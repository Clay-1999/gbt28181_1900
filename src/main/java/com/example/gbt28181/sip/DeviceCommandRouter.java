package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import com.example.gbt28181.domain.entity.RemoteDevice;
import com.example.gbt28181.domain.repository.Ivs1900CameraMappingRepository;
import com.example.gbt28181.domain.repository.RemoteDeviceRepository;
import com.example.gbt28181.service.DeviceInfoQueryService;
import com.example.gbt28181.service.PtzService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.slf4j.MDC;

import javax.sip.*;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.Optional;

import com.example.gbt28181.service.RecordQueryService;
import org.springframework.stereotype.Component;

/**
 * SIP MESSAGE 命令路由器。
 * 解析 CmdType（ConfigDownload / DeviceConfig）和 DeviceID，按设备归属路由到对应处理器。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceCommandRouter {

    private final Ivs1900CameraMappingRepository cameraRepo;
    private final RemoteDeviceRepository remoteDeviceRepo;
    private final ConfigDownloadHandler configDownloadHandler;
    private final DeviceConfigHandler deviceConfigHandler;
    private final RemoteDeviceMessageForwarder forwarder;
    private final CatalogNotifyHandler catalogNotifyHandler;
    private final RecordQueryService recordQueryService;
    private final PtzService ptzService;
    private final SipMessageSender sipMessageSender;
    private final DeviceInfoQueryService deviceInfoQueryService;

    private SipProvider sipProvider;

    public void setSipProvider(SipProvider sipProvider) {
        this.sipProvider = sipProvider;
    }

    /**
     * 尝试路由 SIP MESSAGE 请求。
     *
     * @return true 如果已处理（ConfigDownload/DeviceConfig 或转发应答），false 如果不是受支持的命令类型（如 Keepalive）
     */
    public boolean route(RequestEvent event) {
        byte[] rawBody = event.getRequest().getRawContent();
        if (rawBody == null || rawBody.length == 0) return false;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(rawBody));

            String rootTag = doc.getDocumentElement().getTagName();
            String cmdType = getTextContent(doc, "CmdType");
            String sn = getTextContent(doc, "SN");
            String deviceId = getTextContent(doc, "DeviceID");

            // 检测是否为透传应答（Response 根节点 + ConfigDownload/DeviceConfig cmdType）
            if ("Response".equals(rootTag)
                    && ("ConfigDownload".equals(cmdType) || "DeviceConfig".equals(cmdType))) {
                String fromSipId = extractUser(
                        ((FromHeader) event.getRequest().getHeader(FromHeader.NAME)).getAddress().getURI().toString());
                return forwarder.handleForwardedResponse(event, fromSipId, sn, rawBody);
            }

            // 目录查询响应（Response 根节点 + CmdType=Catalog）
            if ("Response".equals(rootTag) && "Catalog".equals(cmdType)) {
                catalogNotifyHandler.handleCatalogResponse(event, rawBody);
                return true;
            }

            // 录像查询响应（Response 根节点 + CmdType=RecordInfo）
            if ("Response".equals(rootTag) && "RecordInfo".equals(cmdType)) {
                // 由 GbtSipListener 中的 hasPendingSn 链优先处理；此处兜底回复 200 OK
                return recordQueryService.hasPendingSn(sn != null ? sn : "");
            }

            // 设备信息/状态查询响应（Response 根节点 + CmdType=DeviceInfo/DeviceStatus）
            if ("Response".equals(rootTag)
                    && ("DeviceInfo".equals(cmdType) || "DeviceStatus".equals(cmdType))) {
                if (sn != null) deviceInfoQueryService.onResponse(sn, new String(rawBody, java.nio.charset.StandardCharsets.UTF_8));
                return true;
            }

            if (!"ConfigDownload".equals(cmdType) && !"DeviceConfig".equals(cmdType)) {
                // 检查是否为 DeviceControl（PTZ 命令）
                if ("DeviceControl".equals(cmdType)) {
                    return handleDeviceControl(event, doc, deviceId);
                }
                // GuardCmd（布/撤防）
                if ("GuardCmd".equals(cmdType)) {
                    return handleGuardCmd(event, doc, deviceId);
                }
                // 北向 PTZ 查询：预置位 / 巡航轨迹
                if ("PresetQuery".equals(cmdType)
                        || "CruiseTrackListQuery".equals(cmdType)
                        || "CruiseTrackQuery".equals(cmdType)) {
                    return handlePtzQuery(event, cmdType, deviceId, doc);
                }
                // 图像抓拍 / 软件升级（回复 200 OK，路由转发）
                if ("SnapShotConfig".equals(cmdType) || "DeviceUpgrade".equals(cmdType)) {
                    return handleSimpleForward(event, cmdType, deviceId, doc);
                }
                return false; // 非目标命令类型，交回上层处理（如 Keepalive）
            }

            CallIdHeader callIdHeader = (CallIdHeader) event.getRequest().getHeader(CallIdHeader.NAME);
            if (callIdHeader != null) {
                MDC.put("callId", callIdHeader.getCallId());
            }

            if (deviceId == null || deviceId.isEmpty()) {
                log.warn("收到 {} 但 DeviceID 为空", cmdType);
                send404(event);
                return true;
            }

            // 路由 1：本端 IVS1900 相机
            Optional<Ivs1900CameraMapping> cameraOpt = cameraRepo.findByGbDeviceId(deviceId);
            if (cameraOpt.isPresent()) {
                if ("ConfigDownload".equals(cmdType)) {
                    String configType = getTextContent(doc, "ConfigType");
                    if (configType == null || configType.isEmpty()) configType = "BasicParam";
                    configDownloadHandler.handle(event, cameraOpt.get(), deviceId, sn, configType);
                } else {
                    deviceConfigHandler.handle(event, cameraOpt.get(), deviceId, sn, rawBody);
                }
                return true;
            }

            // 路由 2：外域设备
            Optional<RemoteDevice> remoteOpt = remoteDeviceRepo.findByDeviceId(deviceId);
            if (remoteOpt.isPresent()) {
                forwarder.forward(event, remoteOpt.get(), cmdType, sn);
                return true;
            }

            // 路由 3：未知设备
            log.warn("{} 目标 DeviceID 未知: {}", cmdType, deviceId);
            send404(event);
            return true;

        } catch (Exception e) {
            log.debug("DeviceCommandRouter 解析 XML 失败（可能是 Keepalive）: {}", e.getMessage());
            return false;
        }
    }

    private boolean handlePtzQuery(RequestEvent event, String cmdType, String deviceId, Document doc) {
        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("{} 但 DeviceID 为空", cmdType);
            send404(event);
            return true;
        }
        // 先回 200 OK
        try {
            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) tx = sipProvider.getNewServerTransaction(event.getRequest());
            tx.sendResponse(SipFactory.getInstance().createMessageFactory()
                    .createResponse(Response.OK, event.getRequest()));
        } catch (Exception e) {
            log.warn("{} 回复 200 OK 失败: {}", cmdType, e.getMessage());
        }
        // 本端相机：代理查询 IVS1900 并回 MESSAGE
        Optional<Ivs1900CameraMapping> cameraOpt = cameraRepo.findByGbDeviceId(deviceId);
        if (cameraOpt.isPresent()) {
            String responseXml;
            if ("PresetQuery".equals(cmdType)) {
                responseXml = ptzService.queryPresetsRaw(deviceId);
            } else if ("CruiseTrackListQuery".equals(cmdType)) {
                responseXml = ptzService.queryCruiseTracksRaw(deviceId);
            } else { // CruiseTrackQuery
                String numberStr = getTextContent(doc, "Number");
                int number = 0;
                try { if (numberStr != null) number = Integer.parseInt(numberStr); } catch (NumberFormatException ignored) {}
                responseXml = ptzService.queryCruiseTrackRaw(deviceId, number);
            }
            if (responseXml != null) {
                sipMessageSender.replyMessage(event, responseXml);
            } else {
                log.warn("{} 查询超时或失败 deviceId={}", cmdType, deviceId);
            }
            return true;
        }
        // 外域设备：透传
        Optional<RemoteDevice> remoteOpt = remoteDeviceRepo.findByDeviceId(deviceId);
        if (remoteOpt.isPresent()) {
            forwarder.forward(event, remoteOpt.get(), cmdType, getTextContent(doc, "SN"));
            return true;
        }
        log.warn("{} 目标 DeviceID 未知: {}", cmdType, deviceId);
        return true;
    }

    private boolean handleDeviceControl(RequestEvent event, Document doc, String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("DeviceControl 但 DeviceID 为空");
            send404(event);
            return true;
        }
        String ptzCmd = getTextContent(doc, "PTZCmd");
        String recordCmd = getTextContent(doc, "RecordCmd");
        String teleBoot = getTextContent(doc, "TeleBoot");
        // 回复 200 OK
        try {
            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) tx = sipProvider.getNewServerTransaction(event.getRequest());
            tx.sendResponse(SipFactory.getInstance().createMessageFactory()
                    .createResponse(javax.sip.message.Response.OK, event.getRequest()));
        } catch (Exception e) {
            log.warn("DeviceControl 回复 200 OK 失败: {}", e.getMessage());
        }
        // 路由到本端或外域
        Optional<Ivs1900CameraMapping> cameraOpt = cameraRepo.findByGbDeviceId(deviceId);
        if (cameraOpt.isPresent()) {
            if (ptzCmd != null && !ptzCmd.isEmpty()) {
                ptzService.sendRawPtzCmd(deviceId, ptzCmd);
            } else if (recordCmd != null) {
                log.info("DeviceControl RecordCmd={} 收到，本端 IVS1900 deviceId={}", recordCmd, deviceId);
            } else if (teleBoot != null) {
                log.info("DeviceControl TeleBoot={} 收到，本端 IVS1900 deviceId={}", teleBoot, deviceId);
            } else {
                log.warn("DeviceControl 未知子命令，deviceId={}", deviceId);
            }
            return true;
        }
        Optional<RemoteDevice> remoteOpt = remoteDeviceRepo.findByDeviceId(deviceId);
        if (remoteOpt.isPresent()) {
            forwarder.forward(event, remoteOpt.get(), "DeviceControl", getTextContent(doc, "SN"));
            return true;
        }
        log.warn("DeviceControl 目标 DeviceID 未知: {}", deviceId);
        return true;
    }

    private boolean handleSimpleForward(RequestEvent event, String cmdType, String deviceId, Document doc) {
        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("{} 但 DeviceID 为空", cmdType);
            send404(event);
            return true;
        }
        // 回复 200 OK
        try {
            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) tx = sipProvider.getNewServerTransaction(event.getRequest());
            tx.sendResponse(SipFactory.getInstance().createMessageFactory()
                    .createResponse(Response.OK, event.getRequest()));
        } catch (Exception e) {
            log.warn("{} 回复 200 OK 失败: {}", cmdType, e.getMessage());
        }
        // 路由到本端或外域
        Optional<Ivs1900CameraMapping> cameraOpt = cameraRepo.findByGbDeviceId(deviceId);
        if (cameraOpt.isPresent()) {
            log.info("{} 收到，本端 IVS1900 deviceId={}", cmdType, deviceId);
            return true;
        }
        Optional<RemoteDevice> remoteOpt = remoteDeviceRepo.findByDeviceId(deviceId);
        if (remoteOpt.isPresent()) {
            forwarder.forward(event, remoteOpt.get(), cmdType, getTextContent(doc, "SN"));
            return true;
        }
        log.warn("{} 目标 DeviceID 未知: {}", cmdType, deviceId);
        return true;
    }

    private boolean handleGuardCmd(RequestEvent event, Document doc, String deviceId) {        if (deviceId == null || deviceId.isEmpty()) {
            log.warn("GuardCmd 但 DeviceID 为空");
            send404(event);
            return true;
        }
        String guardCmd = getTextContent(doc, "GuardCmd");
        // 回复 200 OK
        try {
            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) tx = sipProvider.getNewServerTransaction(event.getRequest());
            tx.sendResponse(SipFactory.getInstance().createMessageFactory()
                    .createResponse(Response.OK, event.getRequest()));
        } catch (Exception e) {
            log.warn("GuardCmd 回复 200 OK 失败: {}", e.getMessage());
        }
        // 路由到本端或外域
        Optional<Ivs1900CameraMapping> cameraOpt = cameraRepo.findByGbDeviceId(deviceId);
        if (cameraOpt.isPresent()) {
            log.info("GuardCmd={} 收到，本端 IVS1900 deviceId={}", guardCmd, deviceId);
            return true;
        }
        Optional<RemoteDevice> remoteOpt = remoteDeviceRepo.findByDeviceId(deviceId);
        if (remoteOpt.isPresent()) {
            forwarder.forward(event, remoteOpt.get(), "GuardCmd", getTextContent(doc, "SN"));
            return true;
        }
        log.warn("GuardCmd 目标 DeviceID 未知: {}", deviceId);
        return true;
    }

    private void send404(RequestEvent event) {
        try {
            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) {
                tx = sipProvider.getNewServerTransaction(event.getRequest());
            }
            Response rsp = SipFactory.getInstance().createMessageFactory()
                    .createResponse(Response.NOT_FOUND, event.getRequest());
            tx.sendResponse(rsp);
        } catch (Exception e) {
            log.warn("发送 404 失败: {}", e.getMessage());
        }
    }

    private String getTextContent(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : null;
    }

    private String extractUser(String uri) {
        if (uri.startsWith("sip:")) uri = uri.substring(4);
        int at = uri.indexOf('@');
        return at > 0 ? uri.substring(0, at) : uri;
    }
}
