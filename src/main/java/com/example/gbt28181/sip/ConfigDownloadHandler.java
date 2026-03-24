package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.sip.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 处理本端 IVS1900 相机的 ConfigDownload（设备参数查询）命令。
 * 返回静态/默认配置，实际配置读取改由 REST API → Ivs1900SipConfigService → IVS1900 SIP MESSAGE。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigDownloadHandler {

    private final AlarmReportStore alarmReportStore;
    private final SipMessageSender sipMessageSender;

    private SipProvider sipProvider;

    public void setSipProvider(SipProvider sipProvider) {
        this.sipProvider = sipProvider;
    }

    /**
     * 处理 ConfigDownload 请求。先回复 200 OK，再异步发送响应 MESSAGE。
     */
    public void handle(RequestEvent event, Ivs1900CameraMapping camera,
                       String deviceId, String sn, String configTypeField) {
        sipMessageSender.sendOk(event);

        Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
        CompletableFuture.runAsync(() -> {
            if (mdcSnapshot != null) MDC.setContextMap(mdcSnapshot);
            try {
                String[] configTypes = configTypeField.split("/");
                StringBuilder xmlParts = new StringBuilder();

                for (String ct : configTypes) {
                    String part = buildConfigTypePart(camera, ct.trim());
                    xmlParts.append(part);
                }

                String xmlBody = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n"
                        + "<Response>\n"
                        + "<CmdType>ConfigDownload</CmdType>\n"
                        + "<SN>" + sn + "</SN>\n"
                        + "<DeviceID>" + deviceId + "</DeviceID>\n"
                        + "<Result>OK</Result>\n"
                        + xmlParts
                        + "</Response>";

                sipMessageSender.replyMessage(event, xmlBody);
                log.debug("ConfigDownload 响应已发送: deviceId={}, configTypes={}", deviceId, configTypeField);
            } catch (Exception e) {
                log.error("ConfigDownload 处理失败: deviceId={}, error={}", deviceId, e.getMessage(), e);
            } finally {
                MDC.clear();
            }
        });
    }

    private String buildConfigTypePart(Ivs1900CameraMapping camera, String configType) {
        return switch (configType) {
            case "BasicParam" -> buildBasicParam(camera);
            case "AlarmReport" -> buildAlarmReport(camera.getGbDeviceId());
            case "VideoParamOpt", "SVACEncodeConfig", "SVACDecodeConfig",
                    "VideoRecordPlan", "VideoAlarmRecord", "SnapShotConfig" ->
                    "<" + configType + "/>\n";
            default -> {
                // VideoParamAttribute, OSDConfig, PictureMask, FrameMirror etc.
                // Return empty element - actual config reads are via REST API → Ivs1900SipConfigService
                log.debug("ConfigDownload: 返回空元素 configType={}, deviceId={}", configType, camera.getGbDeviceId());
                yield "<" + configType + "/>\n";
            }
        };
    }

    private String buildBasicParam(Ivs1900CameraMapping camera) {
        StringBuilder sb = new StringBuilder("<BasicParam>\n");
        appendXml(sb, "Name", camera.getName() != null ? camera.getName() : camera.getGbDeviceId());
        appendXml(sb, "Manufacture", "IVS1900");
        appendXml(sb, "Parental", "0");
        appendXml(sb, "SafetyWay", "0");
        appendXml(sb, "RegisterWay", "1");
        appendXml(sb, "Secrecy", "0");
        sb.append("</BasicParam>\n");
        return sb.toString();
    }

    private String buildAlarmReport(String gbDeviceId) {
        String stored = alarmReportStore.get(gbDeviceId);
        if (stored != null) return stored;
        return "<AlarmReport>\n"
                + "<AlarmMethod>5</AlarmMethod>\n"
                + "<AlarmRecordTime>10</AlarmRecordTime>\n"
                + "<PreRecordTime>5</PreRecordTime>\n"
                + "</AlarmReport>\n";
    }

    private void appendXml(StringBuilder sb, String tag, String value) {
        if (value != null) {
            sb.append("<").append(tag).append(">")
                    .append(value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                    .append("</").append(tag).append(">\n");
        }
    }
}
