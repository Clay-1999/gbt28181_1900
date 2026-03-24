package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import com.example.gbt28181.domain.entity.RemoteDevice;
import com.example.gbt28181.domain.repository.InterconnectConfigRepository;
import com.example.gbt28181.domain.repository.Ivs1900CameraMappingRepository;
import com.example.gbt28181.domain.repository.Ivs1900InterconnectConfigRepository;
import com.example.gbt28181.domain.repository.LocalSipConfigRepository;
import com.example.gbt28181.domain.repository.RemoteDeviceRepository;
import com.example.gbt28181.sip.xml.CatalogItem;
import com.example.gbt28181.sip.xml.CatalogNotifyXml;
import com.example.gbt28181.sip.xml.CatalogResponseXml;
import com.example.gbt28181.sip.xml.GbXmlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.*;
import javax.sip.header.FromHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogNotifyHandler {

    private final RemoteDeviceRepository remoteDeviceRepository;
    private final InterconnectConfigRepository interconnectConfigRepository;
    private final Ivs1900InterconnectConfigRepository ivs1900ConfigRepository;
    private final Ivs1900CameraMappingRepository ivs1900CameraMappingRepository;
    private final LocalSipConfigRepository localSipConfigRepository;

    private SipProvider sipProvider;

    public void setSipProvider(SipProvider sipProvider) {
        this.sipProvider = sipProvider;
    }

    public void handleNotify(RequestEvent event) {
        Request request = event.getRequest();
        ServerTransaction tx = event.getServerTransaction();
        try {
            if (tx == null) {
                try {
                    tx = sipProvider.getNewServerTransaction(request);
                } catch (Exception e) {
                    log.debug("NOTIFY 创建 ServerTransaction 失败（可能缺少 Subscription-State），降级为无状态响应: {}", e.getMessage());
                }
            }

            FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
            String fromUri = from.getAddress().getURI().toString();
            String remoteSipId = extractUserFromUri(fromUri);

            byte[] rawBody = request.getRawContent();

            // 判断是否来自 IVS1900
            boolean isIvs1900 = ivs1900ConfigRepository.findBySipId(remoteSipId).isPresent();
            if (isIvs1900) {
                if (rawBody != null) {
                    parseAndUpsertIvs1900Cameras(rawBody);
                }
                sendResponse(tx, request, Response.OK);
                return;
            }

            // 非 IVS1900：走外域设备逻辑
            Long interconnectConfigId = interconnectConfigRepository.findAll().stream()
                    .filter(c -> c.getRemoteSipId().equals(remoteSipId))
                    .map(c -> c.getId())
                    .findFirst()
                    .orElse(null);

            if (interconnectConfigId == null) {
                log.warn("收到未知对端 NOTIFY: {}", remoteSipId);
                sendResponse(tx, request, Response.FORBIDDEN);
                return;
            }

            if (rawBody != null) {
                parseAndUpsertDevices(rawBody, interconnectConfigId);
            }

            sendResponse(tx, request, Response.OK);

        } catch (Exception e) {
            log.error("处理 NOTIFY 请求失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理目录查询响应 MESSAGE（CmdType=Catalog，根节点 Response）。
     */
    public void handleCatalogResponse(RequestEvent event, byte[] rawBody) {
        Request request = event.getRequest();
        try {
            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) {
                try {
                    tx = sipProvider.getNewServerTransaction(request);
                } catch (Exception e) {
                    log.debug("Catalog 响应创建 ServerTransaction 失败，降级为无状态响应: {}", e.getMessage());
                }
            }

            FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
            String remoteSipId = extractUserFromUri(from.getAddress().getURI().toString());

            // 先检查是否来自 IVS1900
            if (ivs1900ConfigRepository.findBySipId(remoteSipId).isPresent()) {
                if (rawBody != null) {
                    parseAndUpsertIvs1900Cameras(rawBody);
                }
                sendResponse(tx, request, Response.OK);
                return;
            }

            Long interconnectConfigId = interconnectConfigRepository.findAll().stream()
                    .filter(c -> c.getRemoteSipId().equals(remoteSipId))
                    .map(c -> c.getId())
                    .findFirst()
                    .orElse(null);

            if (interconnectConfigId == null) {
                log.warn("收到未知对端 Catalog 响应: {}", remoteSipId);
                sendResponse(tx, request, Response.FORBIDDEN);
                return;
            }

            parseAndUpsertDevices(rawBody, interconnectConfigId);
            sendResponse(tx, request, Response.OK);

        } catch (Exception e) {
            log.error("处理 Catalog 响应失败: {}", e.getMessage(), e);
        }
    }

    /** 解析 IVS1900 Catalog Notify/Response，写入 ivs1900_camera_mapping 表 */
    private void parseAndUpsertIvs1900Cameras(byte[] body) {
        try {
            List<CatalogItem> items = parseCatalogItems(body);
            int newCount = 0, updateCount = 0;
            for (CatalogItem item : items) {
                String deviceId = item.getDeviceId();
                if (deviceId == null || deviceId.isBlank()) continue;
                // 跳过音频采集通道（IVS1900 上报的音频输入设备编码第11-13位为 137）
                if (deviceId.length() >= 13 && "137".equals(deviceId.substring(10, 13))) continue;

                java.util.Optional<Ivs1900CameraMapping> existing =
                        ivs1900CameraMappingRepository.findByIvsCameraId(deviceId);

                if (existing.isEmpty()) {
                    Ivs1900CameraMapping mapping = new Ivs1900CameraMapping();
                    mapping.setIvsCameraId(deviceId);
                    mapping.setName(item.getName());
                    mapping.setStatus(item.getStatus() != null ? item.getStatus() : "OFF");
                    mapping.setPtzType(item.getPtzType());
                    mapping.setSyncedAt(LocalDateTime.now());
                    mapping.setGbDeviceId("TEMP_" + System.currentTimeMillis());
                    mapping = ivs1900CameraMappingRepository.save(mapping);

                    String gbDeviceId = generateGbDeviceId(mapping.getId());
                    mapping.setGbDeviceId(gbDeviceId);
                    ivs1900CameraMappingRepository.save(mapping);
                    newCount++;
                    log.debug("IVS1900 新相机: deviceId={}, gbDeviceId={}", deviceId, gbDeviceId);
                } else {
                    Ivs1900CameraMapping mapping = existing.get();
                    mapping.setName(item.getName());
                    if (item.getStatus() != null) mapping.setStatus(item.getStatus());
                    if (item.getPtzType() != null) mapping.setPtzType(item.getPtzType());
                    mapping.setSyncedAt(LocalDateTime.now());
                    ivs1900CameraMappingRepository.save(mapping);
                    updateCount++;
                    log.info("IVS1900 更新相机: deviceId={} status={} gbDeviceId={}", deviceId, item.getStatus(), mapping.getGbDeviceId());
                }
            }
            log.info("IVS1900 Catalog Notify 同步完成：新增={}, 更新={}", newCount, updateCount);
        } catch (Exception e) {
            log.warn("解析 IVS1900 Catalog Notify XML 失败: {}", e.getMessage());
        }
    }

    private void parseAndUpsertDevices(byte[] body, Long interconnectConfigId) {
        try {
            List<CatalogItem> items = parseCatalogItems(body);
            int count = 0;
            for (CatalogItem item : items) {
                String deviceId = item.getDeviceId();
                if (deviceId == null || deviceId.isBlank()) continue;

                RemoteDevice device = remoteDeviceRepository.findByDeviceId(deviceId)
                        .orElse(new RemoteDevice());
                device.setDeviceId(deviceId);
                device.setName(item.getName());
                device.setStatus(item.getStatus());
                if (item.getPtzType() != null) device.setPtzType(item.getPtzType());
                device.setInterconnectConfigId(interconnectConfigId);
                device.setSyncedAt(LocalDateTime.now());
                remoteDeviceRepository.save(device);
                count++;
            }
            log.info("NOTIFY Catalog 解析完成，upsert {} 个外域设备，interconnectConfigId={}", count, interconnectConfigId);
        } catch (Exception e) {
            log.warn("解析 NOTIFY XML 失败: {}", e.getMessage());
        }
    }

    /**
     * 从字节数组解析 Catalog 目录项列表。
     * 兼容根节点为 &lt;Notify&gt;（SUBSCRIBE 推送）和 &lt;Response&gt;（主动查询响应）两种形式。
     */
    private List<CatalogItem> parseCatalogItems(byte[] body) {
        String xml = new String(body, StandardCharsets.UTF_8);
        try {
            if (xml.contains("<Notify>")) {
                CatalogNotifyXml notify = GbXmlMapper.fromXml(xml, CatalogNotifyXml.class);
                if (notify.getDeviceList() != null && notify.getDeviceList().getItems() != null) {
                    return notify.getDeviceList().getItems();
                }
            } else {
                CatalogResponseXml response = GbXmlMapper.fromXml(xml, CatalogResponseXml.class);
                if (response.getItems() != null) {
                    return response.getItems();
                }
            }
        } catch (Exception e) {
            log.warn("JAXB 解析 Catalog XML 失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private String generateGbDeviceId(Long id) {
        String prefix = localSipConfigRepository.findAll().stream()
                .findFirst()
                .map(config -> extractNumericPrefix(config.getDomain(), 10))
                .orElse("0000000000");
        return prefix + "131" + String.format("%07d", id);
    }

    private String extractNumericPrefix(String input, int length) {
        if (input == null || input.isEmpty()) return "0".repeat(length);
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
                if (sb.length() == length) return sb.toString();
            }
        }
        while (sb.length() < length) sb.append('0');
        return sb.toString();
    }

    private void sendResponse(ServerTransaction tx, Request request, int statusCode) throws Exception {
        Response response = SipFactory.getInstance().createMessageFactory().createResponse(statusCode, request);
        if (tx != null) {
            tx.sendResponse(response);
        } else {
            sipProvider.sendResponse(response);
        }
    }

    private String extractUserFromUri(String uri) {
        return SipMessageSender.extractUser(uri);
    }
}
