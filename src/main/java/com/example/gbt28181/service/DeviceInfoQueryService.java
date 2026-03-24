package com.example.gbt28181.service;

import com.example.gbt28181.api.exception.ResourceNotFoundException;
import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.entity.RemoteDevice;
import com.example.gbt28181.domain.repository.*;
import com.example.gbt28181.sip.SipMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceInfoQueryService {

    private final Ivs1900CameraMappingRepository cameraRepo;
    private final Ivs1900InterconnectConfigRepository ivs1900Repo;
    private final RemoteDeviceRepository remoteDeviceRepo;
    private final InterconnectConfigRepository interconnectConfigRepo;
    private final SipMessageSender sipMessageSender;

    /** key = "devinfo:<sn>" */
    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    /**
     * 向设备发送 DeviceInfo 或 DeviceStatus 查询，等待响应（10s 超时）。
     *
     * @param type     "local" 或 "remote"
     * @param deviceId 本端用 gbDeviceId，外域用 deviceId
     * @param cmdType  "DeviceInfo" 或 "DeviceStatus"
     * @return 解析后的 Map，超时返回 null
     */
    public Map<String, Object> query(String type, String deviceId, String cmdType) {
        int sn = newSn();
        String snKey = snKey(sn);
        String targetId = resolveTargetDeviceId(type, deviceId);

        String xml = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n<Query>\n"
                + "<CmdType>" + cmdType + "</CmdType>\n"
                + "<SN>" + sn + "</SN>\n"
                + "<DeviceID>" + targetId + "</DeviceID>\n"
                + "</Query>";

        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(snKey, future);
        try {
            sendMessage(type, deviceId, targetId, xml);
            String responseXml = future.get(10, TimeUnit.SECONDS);
            return parseResponse(responseXml, cmdType);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (TimeoutException e) {
            log.warn("{} 查询超时 10s: type={}, deviceId={}", cmdType, type, deviceId);
            return null;
        } catch (Exception e) {
            log.error("{} 查询异常: deviceId={}, error={}", cmdType, deviceId, e.getMessage(), e);
            return null;
        } finally {
            pending.remove(snKey);
        }
    }

    /** 收到设备响应 MESSAGE 时调用，complete 对应 future。 */
    public boolean onResponse(String sn, String xmlBody) {
        try {
            String key = snKey(Integer.parseInt(sn));
            CompletableFuture<String> future = pending.get(key);
            if (future == null) return false;
            future.complete(xmlBody);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Map<String, Object> parseResponse(String xml, String cmdType) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Map<String, Object> result = new LinkedHashMap<>();
            String[] fields = "DeviceID,Result,DeviceName,Manufacturer,Model,Firmware,Channel,Online,Status,RecordStatus,Alarmstatus".split(",");
            for (String field : fields) {
                NodeList nl = doc.getElementsByTagName(field);
                if (nl.getLength() > 0) result.put(field, nl.item(0).getTextContent());
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 {} 响应失败: {}", cmdType, e.getMessage());
            return Map.of("error", "解析失败");
        }
    }

    private String resolveTargetDeviceId(String type, String deviceId) {
        if ("local".equals(type)) {
            return cameraRepo.findByGbDeviceId(deviceId)
                    .map(Ivs1900CameraMapping::getIvsCameraId)
                    .orElseThrow(() -> new ResourceNotFoundException("Camera not found: " + deviceId));
        }
        return deviceId;
    }

    private void sendMessage(String type, String deviceId, String targetId, String xmlBody) {
        if ("local".equals(type)) {
            Ivs1900InterconnectConfig ivs1900 = ivs1900Repo.findAll().stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("IVS1900 互联配置不存在"));
            InterconnectConfig target = toInterconnectConfig(ivs1900);
            sipMessageSender.sendMessage(target, targetId, UUID.randomUUID().toString(), xmlBody);
        } else {
            RemoteDevice device = remoteDeviceRepo.findByDeviceId(deviceId)
                    .orElseThrow(() -> new ResourceNotFoundException("RemoteDevice not found: " + deviceId));
            InterconnectConfig config = interconnectConfigRepo.findById(device.getInterconnectConfigId())
                    .orElseThrow(() -> new ResourceNotFoundException("InterconnectConfig not found"));
            sipMessageSender.sendMessage(config, deviceId, UUID.randomUUID().toString(), xmlBody);
        }
    }

    private InterconnectConfig toInterconnectConfig(Ivs1900InterconnectConfig ivs1900) {
        InterconnectConfig ic = new InterconnectConfig();
        ic.setId(ivs1900.getId());
        ic.setRemoteSipId(ivs1900.getSipId());
        ic.setRemoteIp(ivs1900.getIp());
        ic.setRemotePort(ivs1900.getPort());
        ic.setRemoteDomain(ivs1900.getDomain());
        ic.setPassword(ivs1900.getPassword());
        ic.setEnabled(true);
        return ic;
    }

    private String snKey(int sn) {
        return "devinfo:" + sn;
    }

    private int newSn() {
        return (int) (System.currentTimeMillis() % 100000);
    }
}
