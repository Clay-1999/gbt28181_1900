package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.RemoteDevice;
import com.example.gbt28181.domain.repository.InterconnectConfigRepository;
import com.example.gbt28181.sip.xml.CameraConfigType;
import com.example.gbt28181.sip.xml.ConfigDownloadQuery;
import com.example.gbt28181.sip.xml.GbXmlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.*;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 外域设备命令透传处理器。
 * 将对外域设备的 ConfigDownload/DeviceConfig SIP MESSAGE 原样转发至对端平台，并透传对端应答。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RemoteDeviceMessageForwarder {

    private final InterconnectConfigRepository interconnectConfigRepo;
    private final SipMessageSender sipMessageSender;

    /** 等待对端响应 MESSAGE 的 Future，key = "remoteSipId:sn" */
    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    private SipProvider sipProvider;

    public void setSipProvider(SipProvider sipProvider) throws PeerUnavailableException {
        this.sipProvider = sipProvider;
    }

    /**
     * 转发命令至外域设备所属的对端平台。先回复原始请求方 200 OK，再等待对端应答并透传。
     *
     * @param event    原始请求事件
     * @param device   外域设备（含 interconnectConfigId）
     * @param cmdType  命令类型（ConfigDownload / DeviceConfig）
     * @param sn       请求序列号
     */
    public void forward(RequestEvent event, RemoteDevice device, String cmdType, String sn) {
        // 1. 立即回复 200 OK
        sipMessageSender.sendOk(event);

        // 2. 查询对端平台地址
        Optional<InterconnectConfig> configOpt = interconnectConfigRepo.findById(device.getInterconnectConfigId());
        if (configOpt.isEmpty()) {
            log.warn("透传失败：找不到 interconnectConfig id={}", device.getInterconnectConfigId());
            sendErrorResponseMessage(event, cmdType, sn, device.getDeviceId(), "Error");
            return;
        }
        InterconnectConfig config = configOpt.get();
        String remoteSipId = config.getRemoteSipId();
        String key = remoteSipId + ":" + sn;

        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(key, future);

        // 3. 异步：发送转发 MESSAGE，等待应答，超时后回复 408
        CompletableFuture.runAsync(() -> {
            try {
                sendForwardedMessage(event, config, device.getDeviceId());

                String responseBody = future.get(5, TimeUnit.SECONDS);
                // 将对端应答透传回原始请求方
                sipMessageSender.replyMessage(event, responseBody);
                log.debug("透传应答成功: deviceId={}, cmdType={}", device.getDeviceId(), cmdType);

            } catch (TimeoutException e) {
                log.warn("透传超时 5s: deviceId={}, cmdType={}", device.getDeviceId(), cmdType);
                sendErrorResponseMessage(event, cmdType, sn, device.getDeviceId(), "Timeout");
                sendSipResponse(event, Response.REQUEST_TIMEOUT);
            } catch (Exception e) {
                log.error("透传异常: deviceId={}, error={}", device.getDeviceId(), e.getMessage(), e);
                sendErrorResponseMessage(event, cmdType, sn, device.getDeviceId(), "Error");
            } finally {
                pending.remove(key);
            }
        });
    }

    /**
     * 处理可能来自对端的应答 MESSAGE（Response 根元素）。
     * 若 Call-ID 在等待 Map 中，完成 Future 并返回 true。
     *
     * @param event      应答 MESSAGE 请求事件
     * @param fromSipId  发送方 SIP ID
     * @param sn         应答中的 SN
     * @param rawBody    原始 XML 字节
     * @return true 如果该 MESSAGE 是等待的透传应答，已处理
     */
    public boolean handleForwardedResponse(RequestEvent event, String fromSipId, String sn, byte[] rawBody) {
        String key = fromSipId + ":" + sn;
        CompletableFuture<String> future = pending.get(key);
        if (future == null) return false;

        // 回复对端 200 OK
        sipMessageSender.sendOk(event);
        future.complete(new String(rawBody, StandardCharsets.UTF_8));
        return true;
    }

    // ===== REST 主动发起配置查询/下发 =====

    /**
     * 主动向外域设备发送 ConfigDownload 查询，同步等待响应（10s 超时）。
     *
     * @return 响应 XML body，超时或失败返回 null
     */
    public String queryConfig(RemoteDevice device, CameraConfigType configType) {
        Optional<InterconnectConfig> configOpt = interconnectConfigRepo.findById(device.getInterconnectConfigId());
        if (configOpt.isEmpty()) {
            log.warn("queryConfig: 找不到 interconnectConfig id={}", device.getInterconnectConfigId());
            return null;
        }
        InterconnectConfig config = configOpt.get();
        String callId = UUID.randomUUID().toString();
        int sn = (int) (System.currentTimeMillis() % 100000);
        String snKey = "sn:" + sn;

        String xmlBody = GbXmlMapper.toXml(new ConfigDownloadQuery(sn, device.getDeviceId(), configType));

        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(snKey, future);
        try {
            sendInitiatedMessage(config, device.getDeviceId(), callId, xmlBody);
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("queryConfig 超时 10s: deviceId={}, configType={}", device.getDeviceId(), configType);
            return null;
        } catch (Exception e) {
            log.error("queryConfig 异常: deviceId={}, error={}", device.getDeviceId(), e.getMessage(), e);
            return null;
        } finally {
            pending.remove(snKey);
        }
    }

    /**
     * 主动向外域设备发送 DeviceConfig 下发，同步等待响应（10s 超时）。
     *
     * @return true=对端回复 OK，false=对端拒绝，null=超时或异常
     */
    public Boolean setConfig(RemoteDevice device, String configType, String xmlBody) {
        Optional<InterconnectConfig> configOpt = interconnectConfigRepo.findById(device.getInterconnectConfigId());
        if (configOpt.isEmpty()) {
            log.warn("setConfig: 找不到 interconnectConfig id={}", device.getInterconnectConfigId());
            return null;
        }
        InterconnectConfig config = configOpt.get();
        String callId = UUID.randomUUID().toString();
        // 从 xmlBody 中解析 SN，用于匹配对端响应
        String snKey = extractSnKey(xmlBody);

        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(snKey, future);
        try {
            sendInitiatedMessage(config, device.getDeviceId(), callId, xmlBody);
            String response = future.get(10, TimeUnit.SECONDS);
            return response != null && response.contains("<Result>OK</Result>");
        } catch (TimeoutException e) {
            log.warn("setConfig 超时 10s: deviceId={}, configType={}", device.getDeviceId(), configType);
            return null;
        } catch (Exception e) {
            log.error("setConfig 异常: deviceId={}, error={}", device.getDeviceId(), e.getMessage(), e);
            return null;
        } finally {
            pending.remove(snKey);
        }
    }

    /**
     * 当收到外域平台回复的配置响应 MESSAGE 时，通过 SN 完成对应 future。
     *
     * @return true 表示该 MESSAGE 已被处理（是等待中的响应）
     */
    public boolean onIncomingConfigResponse(String sn, String xmlBody) {
        String snKey = "sn:" + sn;
        CompletableFuture<String> future = pending.get(snKey);
        if (future == null) return false;
        future.complete(xmlBody);
        return true;
    }

    /** 检查 SN 是否在等待中（用于 GbtSipListener 路由判断）。 */
    public boolean hasPendingSn(String sn) {
        return pending.containsKey("sn:" + sn);
    }

    private void sendInitiatedMessage(InterconnectConfig target, String deviceId, String callId, String xmlBody) {
        sipMessageSender.sendMessage(target, deviceId, callId, xmlBody);
    }

    // ===== 私有方法 =====

    private void sendForwardedMessage(RequestEvent event, InterconnectConfig target, String deviceId) {
        byte[] originalBody = event.getRequest().getRawContent();
        String xmlBody = originalBody != null ? new String(originalBody, StandardCharsets.UTF_8) : "";
        sipMessageSender.sendMessage(target, deviceId, UUID.randomUUID().toString(), xmlBody);
        log.debug("转发 MESSAGE → {}:{} deviceId={}", target.getRemoteIp(), target.getRemotePort(), deviceId);
    }

    private void sendErrorResponseMessage(RequestEvent event, String cmdType, String sn,
                                          String deviceId, String result) {
        String xmlBody = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n"
                + "<Response>\n"
                + "<CmdType>" + cmdType + "</CmdType>\n"
                + "<SN>" + sn + "</SN>\n"
                + "<DeviceID>" + deviceId + "</DeviceID>\n"
                + "<Result>" + result + "</Result>\n"
                + "</Response>";
        sipMessageSender.replyMessage(event, xmlBody);
    }

    private void sendSipResponse(RequestEvent event, int statusCode) {
        try {
            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) {
                tx = sipProvider.getNewServerTransaction(event.getRequest());
            }
            Response rsp = SipFactory.getInstance().createMessageFactory()
                    .createResponse(statusCode, event.getRequest());
            tx.sendResponse(rsp);
        } catch (Exception e) {
            log.warn("发送 SIP {} 失败: {}", statusCode, e.getMessage());
        }
    }

    /** 从 XML body 中提取 SN，构造 pending key。 */
    private String extractSnKey(String xmlBody) {
        try {
            int start = xmlBody.indexOf("<SN>") + 4;
            int end = xmlBody.indexOf("</SN>");
            if (start > 3 && end > start) {
                return "sn:" + xmlBody.substring(start, end).trim();
            }
        } catch (Exception ignored) {}
        return "sn:" + UUID.randomUUID();
    }
}
