package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.AlarmEvent;
import com.example.gbt28181.domain.repository.AlarmEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.sip.*;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlarmNotifyHandler {

    private final AlarmEventRepository alarmEventRepository;

    public void handle(RequestEvent event) {
        // 1. 先回 200 OK
        try {
            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) tx = ((SipProvider) event.getSource()).getNewServerTransaction(event.getRequest());
            tx.sendResponse(SipFactory.getInstance().createMessageFactory()
                    .createResponse(javax.sip.message.Response.OK, event.getRequest()));
        } catch (Exception e) {
            log.warn("Alarm NOTIFY 回复 200 OK 失败: {}", e.getMessage());
        }

        // 2. 解析消息体
        byte[] rawBody = event.getRequest().getRawContent();
        if (rawBody == null || rawBody.length == 0) {
            log.warn("Alarm NOTIFY 消息体为空，忽略");
            return;
        }

        try {
            // 先用 ISO-8859-1 读出 XML 声明中的 encoding 属性，再用正确编码解码
            String xmlAscii = new String(rawBody, java.nio.charset.StandardCharsets.ISO_8859_1);
            java.nio.charset.Charset charset = java.nio.charset.StandardCharsets.UTF_8;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("encoding=[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(xmlAscii);
            if (m.find()) {
                try { charset = java.nio.charset.Charset.forName(m.group(1)); } catch (Exception ignored) {}
            }
            // 用正确编码解码成字符串，并将 XML 声明中的 encoding 替换为 UTF-8，避免 DocumentBuilder 二次误读
            String xml = new String(rawBody, charset)
                    .replaceFirst("(?i)(encoding=[\"'])[^\"']+([\"'])", "$1UTF-8$2");
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            AlarmEvent alarm = new AlarmEvent();
            alarm.setDeviceId(getText(doc, "DeviceID"));
            alarm.setAlarmPriority(getText(doc, "AlarmPriority"));
            alarm.setAlarmMethod(getText(doc, "AlarmMethod"));
            alarm.setAlarmType(getText(doc, "AlarmType"));
            alarm.setAlarmDescription(getText(doc, "AlarmDescription"));
            alarm.setAlarmTime(getText(doc, "AlarmTime"));
            alarm.setReceivedAt(Instant.now());

            String lonStr = getText(doc, "Longitude");
            String latStr = getText(doc, "Latitude");
            if (lonStr != null) {
                try { alarm.setLongitude(Double.parseDouble(lonStr)); } catch (NumberFormatException ignored) {}
            }
            if (latStr != null) {
                try { alarm.setLatitude(Double.parseDouble(latStr)); } catch (NumberFormatException ignored) {}
            }

            // sourceIp 从 Via 头提取
            ViaHeader via = (ViaHeader) event.getRequest().getHeader(ViaHeader.NAME);
            if (via != null) {
                String host = via.getReceived() != null ? via.getReceived() : via.getHost();
                alarm.setSourceIp(host);
            }

            alarmEventRepository.save(alarm);
            log.info("告警入库 deviceId={} alarmType={} priority={}", alarm.getDeviceId(), alarm.getAlarmType(), alarm.getAlarmPriority());

        } catch (Exception e) {
            log.warn("Alarm NOTIFY XML 解析失败: {}", e.getMessage());
        }
    }

    private String getText(Document doc, String tagName) {
        NodeList nl = doc.getElementsByTagName(tagName);
        if (nl.getLength() > 0) {
            String text = nl.item(0).getTextContent();
            return text != null && !text.isBlank() ? text.trim() : null;
        }
        return null;
    }
}
