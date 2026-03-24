package com.example.gbt28181.api.controller;

import com.example.gbt28181.api.dto.DeviceResponse;
import com.example.gbt28181.api.dto.PtzControlRequest;
import com.example.gbt28181.api.dto.RemoteDeviceResponse;
import com.example.gbt28181.api.dto.SnapshotRequest;
import com.example.gbt28181.api.dto.UpgradeRequest;
import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.repository.InterconnectConfigRepository;
import com.example.gbt28181.domain.repository.Ivs1900CameraMappingRepository;
import com.example.gbt28181.domain.repository.RemoteDeviceRepository;
import com.example.gbt28181.api.dto.DeviceControlRequest;
import com.example.gbt28181.api.dto.RecordQueryRequest;
import com.example.gbt28181.service.DeviceConfigService;
import com.example.gbt28181.service.DeviceControlService;
import com.example.gbt28181.service.DeviceInfoQueryService;
import com.example.gbt28181.service.RecordQueryService;
import com.example.gbt28181.service.LocalDeviceStreamService;
import com.example.gbt28181.service.PtzService;
import com.example.gbt28181.service.RemoteDeviceConfigService;
import com.example.gbt28181.sip.SipInviteService;
import com.example.gbt28181.sip.xml.CameraConfigType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final Ivs1900CameraMappingRepository mappingRepository;
    private final RemoteDeviceRepository remoteDeviceRepository;
    private final InterconnectConfigRepository interconnectConfigRepository;
    private final DeviceConfigService deviceConfigService;
    private final RemoteDeviceConfigService remoteDeviceConfigService;
    private final LocalDeviceStreamService localDeviceStreamService;
    private final SipInviteService sipInviteService;
    private final PtzService ptzService;
    private final RecordQueryService recordQueryService;
    private final DeviceInfoQueryService deviceInfoQueryService;
    private final DeviceControlService deviceControlService;

    // ===== 设备列表 =====

    @GetMapping("/local")
    public List<DeviceResponse> listLocalDevices() {
        return mappingRepository.findAll().stream()
                .filter(m -> {
                    String id = m.getIvsCameraId();
                    return id == null || id.length() < 13 || !"137".equals(id.substring(10, 13));
                })
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/remote")
    public List<RemoteDeviceResponse> listRemoteDevices() {
        Map<Long, String> configNames = interconnectConfigRepository.findAll().stream()
                .collect(Collectors.toMap(InterconnectConfig::getId, InterconnectConfig::getName));
        return remoteDeviceRepository.findAll().stream()
                .map(d -> {
                    RemoteDeviceResponse r = new RemoteDeviceResponse();
                    r.setDeviceId(d.getDeviceId());
                    r.setName(d.getName());
                    r.setStatus(d.getStatus());
                    r.setPtzType(d.getPtzType());
                    r.setInterconnectName(configNames.get(d.getInterconnectConfigId()));
                    r.setSyncedAt(d.getSyncedAt());
                    return r;
                })
                .collect(Collectors.toList());
    }

    private DeviceResponse toResponse(com.example.gbt28181.domain.entity.Ivs1900CameraMapping entity) {
        DeviceResponse r = new DeviceResponse();
        r.setId(entity.getId());
        r.setGbDeviceId(entity.getGbDeviceId());
        r.setName(entity.getName());
        r.setStatus(entity.getStatus());
        r.setPtzType(entity.getPtzType());
        r.setSyncedAt(entity.getSyncedAt());
        return r;
    }

    // ===== 本端相机视频流 =====

    @PostMapping("/local/{gbDeviceId}/stream/start")
    public ResponseEntity<Map<String, Object>> startLocalStream(@PathVariable String gbDeviceId) {
        try {
            String streamUrl = localDeviceStreamService.startStream(gbDeviceId);
            return ResponseEntity.ok(Map.of("streamUrl", streamUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/local/{gbDeviceId}/stream/stop")
    public ResponseEntity<Void> stopLocalStream(@PathVariable String gbDeviceId) {
        localDeviceStreamService.stopStream(gbDeviceId);
        return ResponseEntity.ok().build();
    }

    // ===== 本端相机配置 =====

    @GetMapping("/local/{gbDeviceId}/config/{configSegment}")
    public ResponseEntity<Map<String, Object>> getLocalConfig(@PathVariable String gbDeviceId,
                                                               @PathVariable String configSegment) {
        CameraConfigType configType = CameraConfigType.fromUrlSegment(configSegment);
        Map<String, Object> result = deviceConfigService.getLocalConfig(gbDeviceId, configType);
        if (result == null) return ResponseEntity.status(504).body(Map.of("error", "SIP 响应超时"));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/local/{gbDeviceId}/config/{configSegment}")
    public Map<String, Object> setLocalConfig(@PathVariable String gbDeviceId,
                                               @PathVariable String configSegment,
                                               @RequestBody Map<String, Object> patch) {
        CameraConfigType configType = CameraConfigType.fromUrlSegment(configSegment);
        return Map.of("success", deviceConfigService.setLocalConfig(gbDeviceId, configType, patch));
    }

    // ===== 外域设备配置 =====

    @GetMapping("/remote/{deviceId}/config/{configSegment}")
    public ResponseEntity<Map<String, Object>> getRemoteConfig(@PathVariable String deviceId,
                                                                @PathVariable String configSegment) {
        CameraConfigType configType = CameraConfigType.fromUrlSegment(configSegment);
        return remoteConfigResponse(remoteDeviceConfigService.queryConfig(deviceId, configType));
    }

    @PutMapping("/remote/{deviceId}/config/{configSegment}")
    public ResponseEntity<Map<String, Object>> setRemoteConfig(@PathVariable String deviceId,
                                                                @PathVariable String configSegment,
                                                                @RequestBody Map<String, Object> patch) {
        CameraConfigType configType = CameraConfigType.fromUrlSegment(configSegment);
        return remoteSetResponse(remoteDeviceConfigService.setConfig(deviceId, configType, patch));
    }

    // ===== 外域设备视频流（原有，保持不变）=====

    @PostMapping("/remote/{deviceId}/stream/start")
    public ResponseEntity<Map<String, Object>> startRemoteStream(@PathVariable String deviceId) {
        try {
            String streamUrl = sipInviteService.startStream(deviceId);
            return ResponseEntity.ok(Map.of("streamUrl", streamUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/remote/{deviceId}/stream/stop")
    public ResponseEntity<Void> stopRemoteStream(@PathVariable String deviceId) {
        sipInviteService.stopStream(deviceId);
        return ResponseEntity.ok().build();
    }

    // ===== PTZ 云台控制 =====

    @PostMapping("/{type}/{deviceId}/ptz/control")
    public ResponseEntity<Map<String, Object>> ptzControl(@PathVariable String type,
                                                          @PathVariable String deviceId,
                                                          @RequestBody PtzControlRequest req) {
        boolean ok = ptzService.sendPtzControl(type, deviceId, req);
        return ResponseEntity.ok(Map.of("success", ok));
    }

    // ===== PTZ 预置位 =====

    @GetMapping("/{type}/{deviceId}/ptz/preset")
    public ResponseEntity<Object> queryPresets(@PathVariable String type,
                                               @PathVariable String deviceId) {
        var result = ptzService.queryPresets(type, deviceId);
        if (result == null) return ResponseEntity.status(504).body(Map.of("error", "SIP 响应超时"));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{type}/{deviceId}/ptz/preset/call")
    public ResponseEntity<Map<String, Object>> callPreset(@PathVariable String type,
                                                          @PathVariable String deviceId,
                                                          @RequestBody PtzControlRequest req) {
        int idx = req.getPresetIndex() != null ? req.getPresetIndex() : 1;
        return ResponseEntity.ok(Map.of("success", ptzService.callPreset(type, deviceId, idx)));
    }

    @PostMapping("/{type}/{deviceId}/ptz/preset/set")
    public ResponseEntity<Map<String, Object>> setPreset(@PathVariable String type,
                                                         @PathVariable String deviceId,
                                                         @RequestBody PtzControlRequest req) {
        int idx = req.getPresetIndex() != null ? req.getPresetIndex() : 1;
        return ResponseEntity.ok(Map.of("success", ptzService.setPreset(type, deviceId, idx, req.getPresetName())));
    }

    @PostMapping("/{type}/{deviceId}/ptz/preset/delete")
    public ResponseEntity<Map<String, Object>> deletePreset(@PathVariable String type,
                                                            @PathVariable String deviceId,
                                                            @RequestBody PtzControlRequest req) {
        int idx = req.getPresetIndex() != null ? req.getPresetIndex() : 1;
        return ResponseEntity.ok(Map.of("success", ptzService.deletePreset(type, deviceId, idx)));
    }

    // ===== PTZ 巡航轨迹 =====

    @GetMapping("/{type}/{deviceId}/ptz/cruise")
    public ResponseEntity<Object> queryCruiseTracks(@PathVariable String type,
                                                    @PathVariable String deviceId) {
        var result = ptzService.queryCruiseTracks(type, deviceId);
        if (result == null) return ResponseEntity.status(504).body(Map.of("error", "SIP 响应超时"));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{type}/{deviceId}/ptz/cruise/{number}")
    public ResponseEntity<Object> queryCruiseTrack(@PathVariable String type,
                                                   @PathVariable String deviceId,
                                                   @PathVariable int number) {
        var result = ptzService.queryCruiseTrack(type, deviceId, number);
        if (result == null) return ResponseEntity.status(504).body(Map.of("error", "SIP 响应超时"));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{type}/{deviceId}/ptz/cruise/start")
    public ResponseEntity<Map<String, Object>> startCruise(@PathVariable String type,
                                                           @PathVariable String deviceId,
                                                           @RequestBody PtzControlRequest req) {
        return ResponseEntity.ok(Map.of("success", ptzService.startCruise(type, deviceId, req.getTrackName())));
    }

    @PostMapping("/{type}/{deviceId}/ptz/cruise/stop")
    public ResponseEntity<Map<String, Object>> stopCruise(@PathVariable String type,
                                                          @PathVariable String deviceId) {
        return ResponseEntity.ok(Map.of("success", ptzService.stopCruise(type, deviceId)));
    }

    // ===== 录像查询 =====

    @PostMapping("/{type}/{deviceId}/records/query")
    public ResponseEntity<Object> queryRecords(@PathVariable String type,
                                               @PathVariable String deviceId,
                                               @RequestBody RecordQueryRequest req) {
        var result = recordQueryService.queryRecords(type, deviceId, req);
        if (result == null) return ResponseEntity.status(504).body(Map.of("error", "SIP 响应超时"));
        return ResponseEntity.ok(result);
    }

    // ===== 设备信息/状态查询 =====

    @GetMapping("/local/{gbDeviceId}/info")
    public ResponseEntity<Object> getLocalDeviceInfo(@PathVariable String gbDeviceId) {
        var result = deviceInfoQueryService.query("local", gbDeviceId, "DeviceInfo");
        if (result == null) return ResponseEntity.status(504).body(Map.of("error", "设备响应超时"));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/local/{gbDeviceId}/status")
    public ResponseEntity<Object> getLocalDeviceStatus(@PathVariable String gbDeviceId) {
        var result = deviceInfoQueryService.query("local", gbDeviceId, "DeviceStatus");
        if (result == null) return ResponseEntity.status(504).body(Map.of("error", "设备响应超时"));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/remote/{deviceId}/info")
    public ResponseEntity<Object> getRemoteDeviceInfo(@PathVariable String deviceId) {
        var result = deviceInfoQueryService.query("remote", deviceId, "DeviceInfo");
        if (result == null) return ResponseEntity.status(504).body(Map.of("error", "设备响应超时"));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/remote/{deviceId}/status")
    public ResponseEntity<Object> getRemoteDeviceStatus(@PathVariable String deviceId) {
        var result = deviceInfoQueryService.query("remote", deviceId, "DeviceStatus");
        if (result == null) return ResponseEntity.status(504).body(Map.of("error", "设备响应超时"));
        return ResponseEntity.ok(result);
    }

    // ===== 设备控制命令（GuardCmd / RecordCmd / TeleBoot）=====

    @PostMapping("/local/{gbDeviceId}/control/guard")
    public ResponseEntity<Map<String, Object>> localGuard(@PathVariable String gbDeviceId,
                                                          @RequestBody DeviceControlRequest req) {
        if (!"SetGuard".equals(req.getCmd()) && !"ResetGuard".equals(req.getCmd()))
            return ResponseEntity.badRequest().body(Map.of("error", "cmd 必须为 SetGuard 或 ResetGuard"));
        deviceControlService.sendGuardCmd("local", gbDeviceId, req.getCmd());
        return ResponseEntity.ok(Map.of("sent", true));
    }

    @PostMapping("/remote/{deviceId}/control/guard")
    public ResponseEntity<Map<String, Object>> remoteGuard(@PathVariable String deviceId,
                                                           @RequestBody DeviceControlRequest req) {
        if (!"SetGuard".equals(req.getCmd()) && !"ResetGuard".equals(req.getCmd()))
            return ResponseEntity.badRequest().body(Map.of("error", "cmd 必须为 SetGuard 或 ResetGuard"));
        deviceControlService.sendGuardCmd("remote", deviceId, req.getCmd());
        return ResponseEntity.ok(Map.of("sent", true));
    }

    @PostMapping("/local/{gbDeviceId}/control/record")
    public ResponseEntity<Map<String, Object>> localRecord(@PathVariable String gbDeviceId,
                                                           @RequestBody DeviceControlRequest req) {
        if (!"Record".equals(req.getCmd()) && !"StopRecord".equals(req.getCmd()))
            return ResponseEntity.badRequest().body(Map.of("error", "cmd 必须为 Record 或 StopRecord"));
        deviceControlService.sendRecordCmd("local", gbDeviceId, req.getCmd());
        return ResponseEntity.ok(Map.of("sent", true));
    }

    @PostMapping("/remote/{deviceId}/control/record")
    public ResponseEntity<Map<String, Object>> remoteRecord(@PathVariable String deviceId,
                                                            @RequestBody DeviceControlRequest req) {
        if (!"Record".equals(req.getCmd()) && !"StopRecord".equals(req.getCmd()))
            return ResponseEntity.badRequest().body(Map.of("error", "cmd 必须为 Record 或 StopRecord"));
        deviceControlService.sendRecordCmd("remote", deviceId, req.getCmd());
        return ResponseEntity.ok(Map.of("sent", true));
    }

    @PostMapping("/local/{gbDeviceId}/control/reboot")
    public ResponseEntity<Map<String, Object>> localReboot(@PathVariable String gbDeviceId) {
        deviceControlService.sendReboot("local", gbDeviceId);
        return ResponseEntity.ok(Map.of("sent", true));
    }

    @PostMapping("/remote/{deviceId}/control/reboot")
    public ResponseEntity<Map<String, Object>> remoteReboot(@PathVariable String deviceId) {
        deviceControlService.sendReboot("remote", deviceId);
        return ResponseEntity.ok(Map.of("sent", true));
    }

    // ===== 图像抓拍 =====

    @PostMapping("/local/{gbDeviceId}/snapshot")
    public ResponseEntity<Map<String, Object>> localSnapshot(@PathVariable String gbDeviceId,
                                                              @RequestBody SnapshotRequest req) {
        deviceControlService.sendSnapshot("local", gbDeviceId, req);
        return ResponseEntity.ok(Map.of("sent", true));
    }

    @PostMapping("/remote/{deviceId}/snapshot")
    public ResponseEntity<Map<String, Object>> remoteSnapshot(@PathVariable String deviceId,
                                                               @RequestBody SnapshotRequest req) {
        deviceControlService.sendSnapshot("remote", deviceId, req);
        return ResponseEntity.ok(Map.of("sent", true));
    }

    // ===== 软件升级 =====

    @PostMapping("/local/{gbDeviceId}/upgrade")
    public ResponseEntity<Map<String, Object>> localUpgrade(@PathVariable String gbDeviceId,
                                                             @RequestBody UpgradeRequest req) {
        deviceControlService.sendUpgrade("local", gbDeviceId, req);
        return ResponseEntity.ok(Map.of("sent", true));
    }

    @PostMapping("/remote/{deviceId}/upgrade")
    public ResponseEntity<Map<String, Object>> remoteUpgrade(@PathVariable String deviceId,
                                                              @RequestBody UpgradeRequest req) {
        deviceControlService.sendUpgrade("remote", deviceId, req);
        return ResponseEntity.ok(Map.of("sent", true));
    }

    private ResponseEntity<Map<String, Object>> remoteConfigResponse(Map<String, Object> result) {
        if (result == null) return ResponseEntity.status(504).body(Map.of("error", "SIP 响应超时"));
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> remoteSetResponse(Boolean success) {
        if (success == null) return ResponseEntity.status(504).body(Map.of("error", "SIP 响应超时"));
        return ResponseEntity.ok(Map.of("success", success));
    }
}
