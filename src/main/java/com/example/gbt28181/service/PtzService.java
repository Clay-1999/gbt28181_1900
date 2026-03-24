package com.example.gbt28181.service;

import com.example.gbt28181.api.dto.PtzControlRequest;
import com.example.gbt28181.api.exception.ResourceNotFoundException;
import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.entity.RemoteDevice;
import com.example.gbt28181.domain.repository.*;
import com.example.gbt28181.sip.PtzCmdEncoder;
import com.example.gbt28181.sip.SipMessageSender;
import com.example.gbt28181.sip.xml.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 云台控制服务。
 * 处理方向/变倍/变焦/光圈控制、预置位查询与操作、巡航轨迹查询与操作。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PtzService {

    private final Ivs1900CameraMappingRepository cameraRepo;
    private final Ivs1900InterconnectConfigRepository ivs1900Repo;
    private final RemoteDeviceRepository remoteDeviceRepo;
    private final InterconnectConfigRepository interconnectConfigRepo;
    private final SipMessageSender sipMessageSender;

    /** 等待 PTZ 查询响应 MESSAGE 的 future，key = "ptz:sn:<sn>" */
    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    // ===== 云台控制 =====

    /**
     * 发送云台控制命令（方向 / 停止 / 变倍 / 变焦 / 光圈）。
     *
     * @param type     设备类型："local" 或 "remote"
     * @param deviceId 设备 ID（local=gbDeviceId，remote=deviceId）
     */
    public boolean sendPtzControl(String type, String deviceId, PtzControlRequest req) {
        String action = req.getAction() != null ? req.getAction() : "stop";
        int speed = req.getSpeed() != null ? req.getSpeed() : 128;
        speed = Math.max(0, Math.min(255, speed));

        String ptzCmd = buildPtzCmd(action, speed);
        String presetName = req.getPresetName();
        String trackName = req.getTrackName();

        int sn = newSn();
        DeviceControlXml xml = new DeviceControlXml();
        xml.setSn(sn);
        xml.setDeviceId(resolveTargetDeviceId(type, deviceId));
        xml.setPtzCmd(ptzCmd);

        if (presetName != null || trackName != null) {
            DeviceControlXml.PtzCmdParams params = new DeviceControlXml.PtzCmdParams();
            params.setPresetName(presetName);
            params.setCruiseTrackName(trackName);
            xml.setPtzCmdParams(params);
        }

        String xmlBody = GbXmlMapper.toXml(xml);
        sendMessage(type, deviceId, xmlBody);
        return true; // 控制命令不等待响应
    }

    // ===== 预置位 =====

    /**
     * 查询预置位列表（PresetQuery），等待设备响应，超时返回 null。
     */
    public List<Map<String, Object>> queryPresets(String type, String deviceId) {
        int sn = newSn();
        String snKey = ptzSnKey(sn);
        String targetId = resolveTargetDeviceId(type, deviceId);

        String queryXml = GbXmlMapper.toXml(new PresetQueryXml(sn, targetId));
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(snKey, future);
        try {
            sendMessage(type, deviceId, queryXml);
            String responseXml = future.get(10, TimeUnit.SECONDS);
            return parsePresetList(responseXml);
        } catch (TimeoutException e) {
            log.warn("PresetQuery 超时 10s: type={}, deviceId={}", type, deviceId);
            return null;
        } catch (Exception e) {
            log.error("PresetQuery 异常: deviceId={}, error={}", deviceId, e.getMessage(), e);
            return null;
        } finally {
            pending.remove(snKey);
        }
    }

    /** 调用预置位（PTZCmd 0x82）。 */
    public boolean callPreset(String type, String deviceId, int presetIndex) {
        return sendPresetCmd(type, deviceId, "call", presetIndex, null);
    }

    /** 设置预置位（PTZCmd 0x81）。 */
    public boolean setPreset(String type, String deviceId, int presetIndex, String presetName) {
        return sendPresetCmd(type, deviceId, "set", presetIndex, presetName);
    }

    /** 删除预置位（PTZCmd 0x83）。 */
    public boolean deletePreset(String type, String deviceId, int presetIndex) {
        return sendPresetCmd(type, deviceId, "delete", presetIndex, null);
    }

    // ===== 巡航轨迹 =====

    /**
     * 查询巡航轨迹列表（CruiseTrackListQuery），超时返回 null。
     */
    public List<Map<String, Object>> queryCruiseTracks(String type, String deviceId) {
        int sn = newSn();
        String snKey = ptzSnKey(sn);
        String targetId = resolveTargetDeviceId(type, deviceId);

        String queryXml = GbXmlMapper.toXml(new CruiseTrackListQueryXml(sn, targetId));
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(snKey, future);
        try {
            sendMessage(type, deviceId, queryXml);
            String responseXml = future.get(10, TimeUnit.SECONDS);
            return parseCruiseTrackList(responseXml);
        } catch (TimeoutException e) {
            log.warn("CruiseTrackListQuery 超时 10s: type={}, deviceId={}", type, deviceId);
            return null;
        } catch (Exception e) {
            log.error("CruiseTrackListQuery 异常: deviceId={}, error={}", deviceId, e.getMessage(), e);
            return null;
        } finally {
            pending.remove(snKey);
        }
    }

    /**
     * 查询指定巡航轨迹详情（CruiseTrackQuery），超时返回 null。
     *
     * @param number 轨迹编号（0=第一条）
     */
    public Map<String, Object> queryCruiseTrack(String type, String deviceId, int number) {
        int sn = newSn();
        String snKey = ptzSnKey(sn);
        String targetId = resolveTargetDeviceId(type, deviceId);

        String queryXml = GbXmlMapper.toXml(new CruiseTrackQueryXml(sn, targetId, number));
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(snKey, future);
        try {
            sendMessage(type, deviceId, queryXml);
            String responseXml = future.get(10, TimeUnit.SECONDS);
            return parseCruiseTrackDetail(responseXml);
        } catch (TimeoutException e) {
            log.warn("CruiseTrackQuery 超时 10s: type={}, deviceId={}, number={}", type, deviceId, number);
            return null;
        } catch (Exception e) {
            log.error("CruiseTrackQuery 异常: deviceId={}, error={}", deviceId, e.getMessage(), e);
            return null;
        } finally {
            pending.remove(snKey);
        }
    }

    /** 启动巡航（PTZCmd 0x88，字节5=组号1，附带 CruiseTrackName）。 */
    public boolean startCruise(String type, String deviceId, String trackName) {
        int sn = newSn();
        DeviceControlXml xml = new DeviceControlXml();
        xml.setSn(sn);
        xml.setDeviceId(resolveTargetDeviceId(type, deviceId));
        xml.setPtzCmd(PtzCmdEncoder.encodeCruiseStart(1));
        if (trackName != null && !trackName.isEmpty()) {
            DeviceControlXml.PtzCmdParams params = new DeviceControlXml.PtzCmdParams();
            params.setCruiseTrackName(trackName);
            xml.setPtzCmdParams(params);
        }
        sendMessage(type, deviceId, GbXmlMapper.toXml(xml));
        return true;
    }

    /** 停止巡航（发送全停 PTZCmd 0x00）。 */
    public boolean stopCruise(String type, String deviceId) {
        int sn = newSn();
        DeviceControlXml xml = new DeviceControlXml();
        xml.setSn(sn);
        xml.setDeviceId(resolveTargetDeviceId(type, deviceId));
        xml.setPtzCmd(PtzCmdEncoder.encodeStop());
        sendMessage(type, deviceId, GbXmlMapper.toXml(xml));
        return true;
    }

    // ===== 北向查询代理（返回原始 XML，由 DeviceCommandRouter 用于转发上级平台）=====

    /**
     * 代理上级平台的预置位查询：向本端 IVS1900 发 PresetQuery，等待响应后返回原始 XML。
     * 超时或出错返回 null。
     */
    public String queryPresetsRaw(String gbDeviceId) {
        int sn = newSn();
        String targetId = resolveTargetDeviceId("local", gbDeviceId);
        return queryRaw("local", gbDeviceId, sn, GbXmlMapper.toXml(new PresetQueryXml(sn, targetId)));
    }

    /**
     * 代理上级平台的巡航轨迹列表查询：向本端 IVS1900 发 CruiseTrackListQuery，等待响应后返回原始 XML。
     */
    public String queryCruiseTracksRaw(String gbDeviceId) {
        int sn = newSn();
        String targetId = resolveTargetDeviceId("local", gbDeviceId);
        return queryRaw("local", gbDeviceId, sn, GbXmlMapper.toXml(new CruiseTrackListQueryXml(sn, targetId)));
    }

    /**
     * 代理上级平台的巡航轨迹详情查询：向本端 IVS1900 发 CruiseTrackQuery，等待响应后返回原始 XML。
     */
    public String queryCruiseTrackRaw(String gbDeviceId, int number) {
        int sn = newSn();
        String targetId = resolveTargetDeviceId("local", gbDeviceId);
        return queryRaw("local", gbDeviceId, sn, GbXmlMapper.toXml(new CruiseTrackQueryXml(sn, targetId, number)));
    }

    private String queryRaw(String type, String deviceId, int sn, String queryXml) {
        String snKey = ptzSnKey(sn);
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(snKey, future);
        try {
            sendMessage(type, deviceId, queryXml);
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("PTZ 查询超时 10s: type={}, deviceId={}", type, deviceId);
            return null;
        } catch (Exception e) {
            log.error("PTZ 查询异常: deviceId={}, error={}", deviceId, e.getMessage(), e);
            return null;
        } finally {
            pending.remove(snKey);
        }
    }

    // ===== SIP 响应回调（由 GbtSipListener 调用）=====

    /** 检查 SN 是否是等待中的 PTZ 查询（用于 GbtSipListener 路由）。 */
    public boolean hasPendingSn(String sn) {
        try {
            return pending.containsKey(ptzSnKey(Integer.parseInt(sn)));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** PTZ 查询响应到达时，完成对应 future。 */
    public boolean onResponse(String sn, String xmlBody) {
        try {
            String key = ptzSnKey(Integer.parseInt(sn));
            CompletableFuture<String> future = pending.get(key);
            if (future == null) return false;
            future.complete(xmlBody);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ===== 私有工具 =====

    private boolean sendPresetCmd(String type, String deviceId, String operation, int presetIndex, String presetName) {
        int sn = newSn();
        DeviceControlXml xml = new DeviceControlXml();
        xml.setSn(sn);
        xml.setDeviceId(resolveTargetDeviceId(type, deviceId));
        xml.setPtzCmd(PtzCmdEncoder.encodePreset(operation, presetIndex));
        if (presetName != null && !presetName.isEmpty()) {
            DeviceControlXml.PtzCmdParams params = new DeviceControlXml.PtzCmdParams();
            params.setPresetName(presetName);
            xml.setPtzCmdParams(params);
        }
        sendMessage(type, deviceId, GbXmlMapper.toXml(xml));
        return true;
    }

    /**
     * 直接转发上级平台原始 PTZCmd 到本端相机（北向 DeviceControl 透传）。
     *
     * @param gbDeviceId  本端相机 gbDeviceId
     * @param ptzCmdHex   原始 PTZCmd 十六进制字符串（如 "A50F000000000000B4"）
     * @return true 发送成功，false 设备不存在或解析失败
     */
    public boolean sendRawPtzCmd(String gbDeviceId, String ptzCmdHex) {
        if (ptzCmdHex == null || ptzCmdHex.trim().isEmpty()) {
            log.warn("sendRawPtzCmd: PTZCmd 为空 gbDeviceId={}", gbDeviceId);
            return false;
        }
        // 验证十六进制格式
        if (!ptzCmdHex.matches("[0-9A-Fa-f]+")) {
            log.warn("sendRawPtzCmd: PTZCmd 格式非法 gbDeviceId={} ptzCmd={}", gbDeviceId, ptzCmdHex);
            return false;
        }
        try {
            Ivs1900CameraMapping camera = cameraRepo.findByGbDeviceId(gbDeviceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Camera not found: " + gbDeviceId));

            int sn = newSn();
            DeviceControlXml xml = new DeviceControlXml();
            xml.setSn(sn);
            xml.setDeviceId(camera.getIvsCameraId());
            xml.setPtzCmd(ptzCmdHex.toUpperCase());

            String xmlBody = GbXmlMapper.toXml(xml);
            sendMessage("local", gbDeviceId, xmlBody);
            log.debug("北向 PTZ 透传 gbDeviceId={} ptzCmd={}", gbDeviceId, ptzCmdHex);
            return true;
        } catch (Exception e) {
            log.warn("sendRawPtzCmd 失败 gbDeviceId={}: {}", gbDeviceId, e.getMessage());
            return false;
        }
    }

    private String buildPtzCmd(String action, int speed) {
        return switch (action) {
            case "zoom_in"   -> PtzCmdEncoder.encodeZoom(true, Math.min(speed >> 4, 15));
            case "zoom_out"  -> PtzCmdEncoder.encodeZoom(false, Math.min(speed >> 4, 15));
            case "focus_in", "focus_out", "iris_in", "iris_out", "fi_stop"
                             -> PtzCmdEncoder.encodeFi(action, speed);
            default          -> PtzCmdEncoder.encodePtz(action, speed);
        };
    }

    /**
     * 解析目标设备真实 SIP 设备 ID（本端 IVS 用 ivsCameraId，外域直接用 deviceId）。
     */
    private String resolveTargetDeviceId(String type, String deviceId) {
        if ("local".equals(type)) {
            return cameraRepo.findByGbDeviceId(deviceId)
                    .map(Ivs1900CameraMapping::getIvsCameraId)
                    .orElseThrow(() -> new ResourceNotFoundException("Camera not found: " + deviceId));
        }
        return deviceId;
    }

    /**
     * 发送 SIP MESSAGE。本端用 IVS1900 互联配置，外域用 RemoteDevice 所属互联配置。
     */
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

    private String ptzSnKey(int sn) {
        return "ptz:" + sn;
    }

    private int newSn() {
        return (int) (System.currentTimeMillis() % 100000);
    }

    // ===== 响应解析 =====

    private List<Map<String, Object>> parsePresetList(String xml) {
        try {
            PresetListResponse rsp = GbXmlMapper.fromXml(xml, PresetListResponse.class);
            List<Map<String, Object>> list = new ArrayList<>();
            for (PresetListResponse.PresetItem item : rsp.getItems()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("presetId", item.getPresetId());
                m.put("presetName", item.getPresetName());
                list.add(m);
            }
            return list;
        } catch (Exception e) {
            log.warn("parsePresetList 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseCruiseTrackList(String xml) {
        try {
            CruiseTrackListResponse rsp = GbXmlMapper.fromXml(xml, CruiseTrackListResponse.class);
            List<Map<String, Object>> list = new ArrayList<>();
            for (CruiseTrackListResponse.CruiseTrackItem t : rsp.getTracks()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("number", t.getNumber());
                m.put("name", t.getName());
                list.add(m);
            }
            return list;
        } catch (Exception e) {
            log.warn("parseCruiseTrackList 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> parseCruiseTrackDetail(String xml) {
        try {
            CruiseTrackDetailResponse rsp = GbXmlMapper.fromXml(xml, CruiseTrackDetailResponse.class);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("number", rsp.getNumber());
            result.put("name", rsp.getName());
            result.put("sumNum", rsp.getSumNum());
            List<Map<String, Object>> points = new ArrayList<>();
            for (CruiseTrackDetailResponse.CruisePoint p : rsp.getPoints()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("presetIndex", p.getPresetIndex());
                pm.put("stayTime", p.getStayTime());
                pm.put("speed", p.getSpeed());
                points.add(pm);
            }
            result.put("points", points);
            return result;
        } catch (Exception e) {
            log.warn("parseCruiseTrackDetail 失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
