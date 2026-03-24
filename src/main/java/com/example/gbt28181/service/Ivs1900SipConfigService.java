package com.example.gbt28181.service;

import com.example.gbt28181.api.exception.ResourceNotFoundException;
import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.repository.Ivs1900CameraMappingRepository;
import com.example.gbt28181.domain.repository.Ivs1900InterconnectConfigRepository;
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
 * 通过 GB/T 28181 SIP MESSAGE 向 IVS1900 相机查询和下发设备配置。
 * 复用 RemoteDeviceMessageForwarder 的 pending future 机制（通过 SN 匹配响应）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Ivs1900SipConfigService {

    private final Ivs1900CameraMappingRepository cameraMappingRepository;
    private final Ivs1900InterconnectConfigRepository ivs1900Repository;
    private final SipMessageSender sipMessageSender;

    /** 等待 IVS1900 配置响应 MESSAGE 的 future，key = "sn:<sn>" */
    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

    /** 查询 IVS1900 相机配置，返回解析后的 Map；超时返回 null。 */
    public Map<String, Object> queryConfig(String gbDeviceId, CameraConfigType configType) {
        Ivs1900CameraMapping camera = cameraMappingRepository.findByGbDeviceId(gbDeviceId)
                .orElseThrow(() -> new ResourceNotFoundException("IVS1900 camera not found: " + gbDeviceId));

        Ivs1900InterconnectConfig ivs1900 = ivs1900Repository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("IVS1900 互联配置不存在"));

        InterconnectConfig target = toInterconnectConfig(ivs1900);
        String ivsCameraId = camera.getIvsCameraId();
        int sn = (int) (System.currentTimeMillis() % 100000);
        String snKey = "sn:" + sn;

        String xmlBody = GbXmlMapper.toXml(new ConfigDownloadQuery(sn, ivsCameraId, configType));

        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(snKey, future);
        try {
            sipMessageSender.sendMessage(target, ivsCameraId, UUID.randomUUID().toString(), xmlBody);
            String responseXml = future.get(10, TimeUnit.SECONDS);
            return parseConfigXml(responseXml, configType);
        } catch (TimeoutException e) {
            log.warn("IVS1900 queryConfig 超时: gbDeviceId={}, configType={}", gbDeviceId, configType);
            return null;
        } catch (Exception e) {
            log.error("IVS1900 queryConfig 异常: gbDeviceId={}, error={}", gbDeviceId, e.getMessage(), e);
            return null;
        } finally {
            pending.remove(snKey);
        }
    }

    /** 向 IVS1900 相机下发配置；超时返回 null（区别于 false=对端拒绝）。 */
    public Boolean setConfig(String gbDeviceId, CameraConfigType configType, Map<String, Object> patch) {
        Ivs1900CameraMapping camera = cameraMappingRepository.findByGbDeviceId(gbDeviceId)
                .orElseThrow(() -> new ResourceNotFoundException("IVS1900 camera not found: " + gbDeviceId));

        Ivs1900InterconnectConfig ivs1900 = ivs1900Repository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("IVS1900 互联配置不存在"));

        InterconnectConfig target = toInterconnectConfig(ivs1900);
        String ivsCameraId = camera.getIvsCameraId();
        int sn = (int) (System.currentTimeMillis() % 100000);
        String snKey = "sn:" + sn;

        String xmlBody = buildDeviceConfigXml(ivsCameraId, configType, sn, patch);
        if (xmlBody.isEmpty()) return false;

        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(snKey, future);
        try {
            sipMessageSender.sendMessage(target, ivsCameraId, UUID.randomUUID().toString(), xmlBody);
            String response = future.get(10, TimeUnit.SECONDS);
            return response != null && response.contains("<Result>OK</Result>");
        } catch (TimeoutException e) {
            log.warn("IVS1900 setConfig 超时: gbDeviceId={}, configType={}", gbDeviceId, configType);
            return null;
        } catch (Exception e) {
            log.error("IVS1900 setConfig 异常: gbDeviceId={}, error={}", gbDeviceId, e.getMessage(), e);
            return null;
        } finally {
            pending.remove(snKey);
        }
    }

    /**
     * 当收到 IVS1900 回复的配置响应 MESSAGE 时，通过 SN 完成对应 future。
     *
     * @return true 表示该 MESSAGE 已被处理
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

    // ===== XML 解析 =====

    private Map<String, Object> parseConfigXml(String xml, CameraConfigType configType) {
        try {
            return switch (configType) {
                case VideoParamAttribute -> parseVideoParam(xml);
                case OSDConfig -> parseOsdConfig(xml);
                case PictureMask -> parsePictureMask(xml);
                case FrameMirror -> parseFrameMirror(xml);
                case VideoRecordPlan -> parseVideoRecordPlan(xml);
                case VideoAlarmRecord -> parseVideoAlarmRecord(xml);
                case AlarmReport -> parseAlarmReport(xml);
                case SnapShot -> parseSnapShot(xml);
            };
        } catch (Exception e) {
            log.warn("parseConfigXml 失败: configType={}, error={}", configType, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> parseVideoParam(String xml) {
        VideoParamAttributeResponse rsp = GbXmlMapper.fromXml(xml, VideoParamAttributeResponse.class);
        List<Map<String, Object>> list = new ArrayList<>();
        if (rsp.getItems() != null) {
            for (VideoParamAttributeResponse.StreamItem item : rsp.getItems()) {
                Map<String, Object> stream = new LinkedHashMap<>();
                stream.put("streamType", item.getStreamNumber() + 1);
                stream.put("encodeType", item.getVideoFormat());
                stream.put("resolution", item.getResolution());
                stream.put("frameRate", item.getFrameRate());
                stream.put("bitRateType", item.getBitRateType());
                stream.put("bitRate", item.getVideoBitRate());
                list.add(stream);
            }
        }
        return Map.of("streamInfoList", list);
    }

    private Map<String, Object> parseOsdConfig(String xml) {
        OsdConfigResponse rsp = GbXmlMapper.fromXml(xml, OsdConfigResponse.class);
        Map<String, Object> result = new LinkedHashMap<>();
        OsdConfigResponse.OsdConfig osd = rsp.getOsdConfig();
        if (osd != null) {
            result.put("textEnable", osd.getTextEnable());
            result.put("timeEnable", osd.getTimeEnable());
            result.put("timeType", osd.getTimeType());
            result.put("timeX", osd.getTimeX());
            result.put("timeY", osd.getTimeY());
            result.put("length", osd.getLength());
            result.put("width", osd.getWidth());
            result.put("sumNum", osd.getSumNum());
            if (osd.getItems() != null) {
                result.put("items", osd.getItems().stream().map(item -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("text", item.getText());
                    m.put("x", item.getX());
                    m.put("y", item.getY());
                    return m;
                }).collect(java.util.stream.Collectors.toList()));
            }
        }
        return result;
    }

    private Map<String, Object> parsePictureMask(String xml) {
        PictureMaskResponse rsp = GbXmlMapper.fromXml(xml, PictureMaskResponse.class);
        int on = rsp.getPictureMask() != null ? rsp.getPictureMask().getOn() : 0;
        return Map.of("enableVideoMask", on);
    }

    private Map<String, Object> parseFrameMirror(String xml) {
        FrameMirrorResponse rsp = GbXmlMapper.fromXml(xml, FrameMirrorResponse.class);
        int mode = rsp.getFrameMirror() != null ? rsp.getFrameMirror() : 0;
        return Map.of("frameMirrorMode", mode);
    }

    private Map<String, Object> parseVideoRecordPlan(String xml) {
        VideoRecordPlanResponse rsp = GbXmlMapper.fromXml(xml, VideoRecordPlanResponse.class);
        Map<String, Object> result = new LinkedHashMap<>();
        if (rsp.getVideoRecordPlan() != null) {
            VideoRecordPlanResponse.VideoRecordPlan plan = rsp.getVideoRecordPlan();
            result.put("recordEnable", plan.getRecordEnable());
            result.put("streamNumber", plan.getStreamNumber());
            result.put("recordScheduleSumNum", plan.getRecordScheduleSumNum());
            if (plan.getRecordSchedules() != null) {
                result.put("recordSchedules", plan.getRecordSchedules());
            }
        } else {
            result.put("recordEnable", 0);
            result.put("streamNumber", 0);
        }
        return result;
    }

    private Map<String, Object> parseVideoAlarmRecord(String xml) {
        VideoAlarmRecordResponse rsp = GbXmlMapper.fromXml(xml, VideoAlarmRecordResponse.class);
        Map<String, Object> result = new LinkedHashMap<>();
        if (rsp.getVideoAlarmRecord() != null) {
            VideoAlarmRecordResponse.VideoAlarmRecord rec = rsp.getVideoAlarmRecord();
            result.put("recordEnable", rec.getRecordEnable());
            result.put("streamNumber", rec.getStreamNumber());
            result.put("recordTime", rec.getRecordTime());
            result.put("preRecordTime", rec.getPreRecordTime());
        } else {
            result.put("recordEnable", 0);
            result.put("streamNumber", 0);
        }
        return result;
    }

    private Map<String, Object> parseAlarmReport(String xml) {
        AlarmReportResponse rsp = GbXmlMapper.fromXml(xml, AlarmReportResponse.class);
        Map<String, Object> result = new LinkedHashMap<>();
        if (rsp.getAlarmReport() != null) {
            result.put("motionDetection", rsp.getAlarmReport().getMotionDetection());
            result.put("fieldDetection", rsp.getAlarmReport().getFieldDetection());
        } else {
            result.put("motionDetection", 0);
            result.put("fieldDetection", 0);
        }
        return result;
    }

    private Map<String, Object> parseSnapShot(String xml) {
        // SnapShot 标准中无 ConfigDownload 查询，此处仅解析 DeviceConfig 响应的 Result
        SnapShotResponse rsp = GbXmlMapper.fromXml(xml, SnapShotResponse.class);
        return Map.of("result", rsp.getResult() != null ? rsp.getResult() : "");
    }

    // ===== XML 构建 =====

    @SuppressWarnings("unchecked")
    private String buildDeviceConfigXml(String deviceId, CameraConfigType configType, int sn, Map<String, Object> patch) {
        try {
            return switch (configType) {
                case VideoParamAttribute -> buildVideoParamXml(deviceId, sn,
                        (List<Map<String, Object>>) patch.getOrDefault("streamInfoList", List.of()));
                case OSDConfig -> buildOsdConfigXml(deviceId, sn, patch);
                case PictureMask -> buildPictureMaskXml(deviceId, sn, patch);
                case FrameMirror -> buildFrameMirrorXml(deviceId, sn, patch);
                case VideoRecordPlan -> buildVideoRecordPlanXml(deviceId, sn, patch);
                case VideoAlarmRecord -> buildVideoAlarmRecordXml(deviceId, sn, patch);
                case AlarmReport -> buildAlarmReportXml(deviceId, sn, patch);
                case SnapShot -> buildSnapShotXml(deviceId, sn, patch);
            };
        } catch (Exception e) {
            log.error("buildDeviceConfigXml 失败: configType={}, error={}", configType, e.getMessage(), e);
            return "";
        }
    }

    private String buildVideoParamXml(String deviceId, int sn, List<Map<String, Object>> streamList) {
        VideoParamAttributeControl ctrl = new VideoParamAttributeControl();
        ctrl.setSn(sn);
        ctrl.setDeviceId(deviceId);
        List<VideoParamAttributeControl.StreamItem> items = new ArrayList<>();
        for (Map<String, Object> stream : streamList) {
            VideoParamAttributeControl.StreamItem item = new VideoParamAttributeControl.StreamItem();
            int streamType = toInt(stream.get("streamType"), 1);
            item.setStreamNumber(streamType - 1);
            item.setVideoFormat(toInt(stream.get("encodeType"), 2));
            item.setResolution(str(stream.get("resolution")));
            item.setFrameRate(toInt(stream.get("frameRate"), 25));
            item.setBitRateType(toInt(stream.get("bitRateType"), 2));
            item.setVideoBitRate(toInt(stream.get("bitRate"), 1024));
            items.add(item);
        }
        ctrl.setStreamInfoList(items);
        return GbXmlMapper.toXml(ctrl);
    }

    @SuppressWarnings("unchecked")
    private String buildOsdConfigXml(String deviceId, int sn, Map<String, Object> patch) {
        OsdConfigControl ctrl = new OsdConfigControl();
        ctrl.setSn(sn);
        ctrl.setDeviceId(deviceId);
        OsdConfigControl.OsdConfig osd = new OsdConfigControl.OsdConfig();
        osd.setTextEnable(toInt(patch.get("textEnable"), 0));
        osd.setTimeEnable(toInt(patch.get("timeEnable"), 0));
        if (patch.containsKey("timeType")) osd.setTimeType(toInt(patch.get("timeType"), 0));
        if (patch.containsKey("timeX")) osd.setTimeX(toInt(patch.get("timeX"), 0));
        if (patch.containsKey("timeY")) osd.setTimeY(toInt(patch.get("timeY"), 0));
        if (patch.containsKey("length")) osd.setLength(toInt(patch.get("length"), 0));
        if (patch.containsKey("width")) osd.setWidth(toInt(patch.get("width"), 0));
        if (patch.containsKey("items")) {
            List<Map<String, Object>> rawItems = (List<Map<String, Object>>) patch.get("items");
            osd.setSumNum(rawItems.size());
            osd.setItems(rawItems.stream().map(m -> {
                OsdConfigControl.OsdItem item = new OsdConfigControl.OsdItem();
                item.setText(str(m.get("text")));
                item.setX(toInt(m.get("x"), 0));
                item.setY(toInt(m.get("y"), 0));
                return item;
            }).collect(java.util.stream.Collectors.toList()));
        }
        ctrl.setOsdConfig(osd);
        return GbXmlMapper.toXml(ctrl);
    }

    private String buildPictureMaskXml(String deviceId, int sn, Map<String, Object> patch) {
        PictureMaskControl ctrl = new PictureMaskControl();
        ctrl.setSn(sn);
        ctrl.setDeviceId(deviceId);
        PictureMaskControl.PictureMask mask = new PictureMaskControl.PictureMask();
        mask.setOn(toInt(patch.get("enableVideoMask"), 0));
        ctrl.setPictureMask(mask);
        return GbXmlMapper.toXml(ctrl);
    }

    private String buildFrameMirrorXml(String deviceId, int sn, Map<String, Object> patch) {
        FrameMirrorControl ctrl = new FrameMirrorControl();
        ctrl.setSn(sn);
        ctrl.setDeviceId(deviceId);
        ctrl.setFrameMirror(toInt(patch.get("frameMirrorMode"), 0));
        return GbXmlMapper.toXml(ctrl);
    }

    @SuppressWarnings("unchecked")
    private String buildVideoRecordPlanXml(String deviceId, int sn, Map<String, Object> patch) {
        VideoRecordPlanControl ctrl = new VideoRecordPlanControl();
        ctrl.setSn(sn);
        ctrl.setDeviceId(deviceId);
        VideoRecordPlanControl.VideoRecordPlan plan = new VideoRecordPlanControl.VideoRecordPlan();
        plan.setRecordEnable(toInt(patch.get("recordEnable"), 0));
        plan.setStreamNumber(toInt(patch.get("streamNumber"), 0));
        List<Map<String, Object>> rawSchedules = (List<Map<String, Object>>) patch.getOrDefault("recordSchedules", List.of());
        List<VideoRecordPlanControl.RecordSchedule> schedules = new ArrayList<>();
        for (Map<String, Object> rs : rawSchedules) {
            VideoRecordPlanControl.RecordSchedule s = new VideoRecordPlanControl.RecordSchedule();
            s.setWeekDayNum(toInt(rs.get("weekDayNum"), 1));
            List<Map<String, Object>> rawSegs = (List<Map<String, Object>>) rs.getOrDefault("timeSegments", List.of());
            s.setTimeSegmentSumNum(rawSegs.size());
            List<VideoRecordPlanControl.TimeSegment> segs = new ArrayList<>();
            for (Map<String, Object> seg : rawSegs) {
                VideoRecordPlanControl.TimeSegment ts = new VideoRecordPlanControl.TimeSegment();
                ts.setStartHour(toInt(seg.get("startHour"), 0));
                ts.setStartMin(toInt(seg.get("startMin"), 0));
                ts.setStartSec(toInt(seg.get("startSec"), 0));
                ts.setStopHour(toInt(seg.get("stopHour"), 23));
                ts.setStopMin(toInt(seg.get("stopMin"), 59));
                ts.setStopSec(toInt(seg.get("stopSec"), 59));
                segs.add(ts);
            }
            s.setTimeSegments(segs);
            schedules.add(s);
        }
        plan.setRecordScheduleSumNum(schedules.size());
        plan.setRecordSchedules(schedules);
        ctrl.setVideoRecordPlan(plan);
        return GbXmlMapper.toXml(ctrl);
    }

    private String buildVideoAlarmRecordXml(String deviceId, int sn, Map<String, Object> patch) {
        VideoAlarmRecordControl ctrl = new VideoAlarmRecordControl();
        ctrl.setSn(sn);
        ctrl.setDeviceId(deviceId);
        VideoAlarmRecordControl.VideoAlarmRecord rec = new VideoAlarmRecordControl.VideoAlarmRecord();
        rec.setRecordEnable(toInt(patch.get("recordEnable"), 0));
        rec.setStreamNumber(toInt(patch.get("streamNumber"), 0));
        if (patch.containsKey("recordTime")) rec.setRecordTime(toInt(patch.get("recordTime"), 0));
        if (patch.containsKey("preRecordTime")) rec.setPreRecordTime(toInt(patch.get("preRecordTime"), 0));
        ctrl.setVideoAlarmRecord(rec);
        return GbXmlMapper.toXml(ctrl);
    }

    private String buildAlarmReportXml(String deviceId, int sn, Map<String, Object> patch) {
        AlarmReportControl ctrl = new AlarmReportControl();
        ctrl.setSn(sn);
        ctrl.setDeviceId(deviceId);
        AlarmReportControl.AlarmReport report = new AlarmReportControl.AlarmReport();
        report.setMotionDetection(toInt(patch.get("motionDetection"), 0));
        report.setFieldDetection(toInt(patch.get("fieldDetection"), 0));
        ctrl.setAlarmReport(report);
        return GbXmlMapper.toXml(ctrl);
    }

    private String buildSnapShotXml(String deviceId, int sn, Map<String, Object> patch) {
        SnapShotControl ctrl = new SnapShotControl();
        ctrl.setSn(sn);
        ctrl.setDeviceId(deviceId);
        SnapShotControl.SnapShot snap = new SnapShotControl.SnapShot();
        snap.setSnapNum(toInt(patch.get("snapNum"), 1));
        if (patch.containsKey("interval")) snap.setInterval(toInt(patch.get("interval"), 1));
        snap.setUploadURL(str(patch.get("uploadURL")));
        snap.setSessionID(str(patch.get("sessionID")));
        ctrl.setSnapShot(snap);
        return GbXmlMapper.toXml(ctrl);
    }

    private int toInt(Object val, int defaultVal) {
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val.toString()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private String str(Object val) {
        return val == null ? "" : val.toString();
    }
}
