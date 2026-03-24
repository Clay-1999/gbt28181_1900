package com.example.gbt28181.service;

import com.example.gbt28181.api.dto.RecordQueryRequest;
import com.example.gbt28181.api.exception.ResourceNotFoundException;
import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.entity.RemoteDevice;
import com.example.gbt28181.domain.repository.*;
import com.example.gbt28181.sip.SipMessageSender;
import com.example.gbt28181.sip.xml.GbXmlMapper;
import com.example.gbt28181.sip.xml.RecordInfoQueryXml;
import com.example.gbt28181.sip.xml.RecordInfoResponseXml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordQueryService {

    private final Ivs1900CameraMappingRepository cameraRepo;
    private final Ivs1900InterconnectConfigRepository ivs1900Repo;
    private final RemoteDeviceRepository remoteDeviceRepo;
    private final InterconnectConfigRepository interconnectConfigRepo;
    private final SipMessageSender sipMessageSender;

    /** 等待 RecordInfo 查询响应 MESSAGE 的 future，key = "record:<sn>" */
    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    /**
     * 发送 RecordInfo 查询并等待响应。
     *
     * @param type     "local" 或 "remote"
     * @param deviceId 本端用 gbDeviceId，外域用 deviceId
     * @return 录像列表结果（含 sumNum 和 items），超时返回 null
     */
    public Map<String, Object> queryRecords(String type, String deviceId, RecordQueryRequest req) {
        int sn = newSn();
        String snKey = recordSnKey(sn);
        String targetId = resolveTargetDeviceId(type, deviceId);
        String startTime = req.getStartTime();
        String endTime = req.getEndTime();
        String recordType = req.getType() != null ? req.getType() : "all";

        String queryXml = GbXmlMapper.toXml(new RecordInfoQueryXml(sn, targetId, startTime, endTime, recordType));
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(snKey, future);
        try {
            sendMessage(type, deviceId, queryXml);
            String responseXml = future.get(10, TimeUnit.SECONDS);
            return parseResponse(responseXml);
        } catch (TimeoutException e) {
            log.warn("RecordInfo 查询超时 10s: type={}, deviceId={}", type, deviceId);
            return null;
        } catch (Exception e) {
            log.error("RecordInfo 查询异常: deviceId={}, error={}", deviceId, e.getMessage(), e);
            return null;
        } finally {
            pending.remove(snKey);
        }
    }

    public boolean hasPendingSn(String sn) {
        try {
            return pending.containsKey(recordSnKey(Integer.parseInt(sn)));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public boolean onResponse(String sn, String xmlBody) {
        try {
            String key = recordSnKey(Integer.parseInt(sn));
            CompletableFuture<String> future = pending.get(key);
            if (future == null) return false;
            future.complete(xmlBody);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Map<String, Object> parseResponse(String xml) {
        try {
            RecordInfoResponseXml rsp = GbXmlMapper.fromXml(xml, RecordInfoResponseXml.class);
            List<Map<String, Object>> items = new ArrayList<>();
            for (RecordInfoResponseXml.RecordItem item : rsp.getItems()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("deviceId", item.getDeviceId());
                m.put("name", item.getName());
                m.put("startTime", item.getStartTime());
                m.put("endTime", item.getEndTime());
                m.put("type", item.getType());
                m.put("secrecy", item.getSecrecy());
                m.put("filePath", item.getFilePath());
                m.put("address", item.getAddress());
                m.put("fileSize", item.getFileSize());
                m.put("streamNumber", item.getStreamNumber());
                items.add(m);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sumNum", rsp.getSumNum());
            result.put("items", items);
            return result;
        } catch (Exception e) {
            log.warn("解析 RecordInfo 响应失败: {}", e.getMessage());
            return Map.of("sumNum", 0, "items", List.of());
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

    private void sendMessage(String type, String deviceId, String xmlBody) {
        if ("local".equals(type)) {
            Ivs1900InterconnectConfig ivs1900 = ivs1900Repo.findAll().stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("IVS1900 互联配置不存在"));
            InterconnectConfig target = toInterconnectConfig(ivs1900);
            String targetId = resolveTargetDeviceId(type, deviceId);
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

    private String recordSnKey(int sn) {
        return "record:" + sn;
    }

    private int newSn() {
        return (int) (System.currentTimeMillis() % 100000);
    }
}
