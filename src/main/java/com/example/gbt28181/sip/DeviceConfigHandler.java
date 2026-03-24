package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.sip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 处理本端 IVS1900 相机的 DeviceConfig（设备参数配置）命令。
 * 仅处理 AlarmReport（内存存储），其余配置下发改由 REST API → Ivs1900SipConfigService → IVS1900 SIP MESSAGE。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceConfigHandler {

    private final AlarmReportStore alarmReportStore;
    private final SipMessageSender sipMessageSender;

    private SipProvider sipProvider;

    public void setSipProvider(SipProvider sipProvider) {
        this.sipProvider = sipProvider;
    }

    /**
     * 处理 DeviceConfig 请求。先回复 200 OK，再异步执行配置并发送响应 MESSAGE。
     */
    public void handle(RequestEvent event, Ivs1900CameraMapping camera,
                       String deviceId, String sn, byte[] rawBody) {
        sipMessageSender.sendOk(event);

        Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
        CompletableFuture.runAsync(() -> {
            if (mdcSnapshot != null) MDC.setContextMap(mdcSnapshot);
            try {
                boolean success = applyConfig(camera, rawBody);
                String result = success ? "OK" : "Error";

                String xmlBody = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n"
                        + "<Response>\n"
                        + "<CmdType>DeviceConfig</CmdType>\n"
                        + "<SN>" + sn + "</SN>\n"
                        + "<DeviceID>" + deviceId + "</DeviceID>\n"
                        + "<Result>" + result + "</Result>\n"
                        + "</Response>";

                sipMessageSender.replyMessage(event, xmlBody);
                log.debug("DeviceConfig 响应已发送: deviceId={}, result={}", deviceId, result);
            } catch (Exception e) {
                log.error("DeviceConfig 处理失败: deviceId={}, error={}", deviceId, e.getMessage(), e);
            } finally {
                MDC.clear();
            }
        });
    }

    private boolean applyConfig(Ivs1900CameraMapping camera, byte[] rawBody) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(rawBody));

            // AlarmReport - stored in memory
            if (doc.getElementsByTagName("AlarmReport").getLength() > 0) {
                return applyAlarmReport(doc, camera.getGbDeviceId(), rawBody);
            }

            // BasicParam/Name - log and accept
            String name = getTextContent(doc, "Name");
            if (name != null && !name.isEmpty()) {
                log.info("DeviceConfig BasicParam/Name 收到（未写入，IVS1900 不支持通过 HTTP 更名）: deviceId={}, name={}",
                        camera.getGbDeviceId(), name);
                return true;
            }

            // All other config types: accept silently
            log.debug("DeviceConfig 静默接受: deviceId={}", camera.getGbDeviceId());
            return true;
        } catch (Exception e) {
            log.error("applyConfig 异常: cameraId={}, error={}", camera.getIvsCameraId(), e.getMessage(), e);
            return false;
        }
    }

    private boolean applyAlarmReport(Document doc, String gbDeviceId, byte[] rawBody) {
        try {
            NodeList nodes = doc.getElementsByTagName("AlarmReport");
            if (nodes.getLength() > 0) {
                javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
                javax.xml.transform.Transformer t = tf.newTransformer();
                t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
                java.io.StringWriter sw = new java.io.StringWriter();
                t.transform(new javax.xml.transform.dom.DOMSource(nodes.item(0)),
                        new javax.xml.transform.stream.StreamResult(sw));
                alarmReportStore.put(gbDeviceId, sw.toString() + "\n");
                log.debug("AlarmReport 已写入内存: gbDeviceId={}", gbDeviceId);
            }
            return true;
        } catch (Exception e) {
            log.warn("applyAlarmReport 异常: gbDeviceId={}, error={}", gbDeviceId, e.getMessage());
            return true;
        }
    }

    private String getTextContent(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent().trim() : null;
    }
}
