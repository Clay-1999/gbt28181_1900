package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.entity.LinkStatus;
import com.example.gbt28181.domain.repository.InterconnectConfigRepository;
import com.example.gbt28181.domain.repository.Ivs1900CameraMappingRepository;
import com.example.gbt28181.domain.repository.Ivs1900InterconnectConfigRepository;
import com.example.gbt28181.domain.repository.RemoteDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class SipRegistrationServer {

    private final InterconnectConfigRepository repository;
    private final Ivs1900InterconnectConfigRepository ivs1900Repository;
    private final RemoteDeviceRepository remoteDeviceRepository;
    private final Ivs1900CameraMappingRepository ivs1900CameraMappingRepository;
    private final CatalogQueryService catalogQueryService;
    private final CatalogSubscribeService catalogSubscribeService;
    private SipProvider sipProvider;

    // nonce → created epoch second
    private final Map<String, Long> nonceMap = new ConcurrentHashMap<>();
    // remoteSipId → RegistrationEntry
    private final Map<String, RegistrationEntry> registrationTable = new ConcurrentHashMap<>();

    private static final String REALM = "gbt28181";
    private static final long NONCE_TTL_SECONDS = 30;
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 180;

    public void setSipProvider(SipProvider sipProvider) {
        this.sipProvider = sipProvider;
    }

    public void handleRegister(RequestEvent event) {
        Request request = event.getRequest();
        ServerTransaction tx = event.getServerTransaction();
        try {
            if (tx == null) {
                tx = sipProvider.getNewServerTransaction(request);
            }

            FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
            String fromUri = from.getAddress().getURI().toString();
            String remoteSipId = extractUserFromUri(fromUri);

            // Check regular interconnect configs first
            Optional<InterconnectConfig> configOpt = repository.findAll().stream()
                    .filter(c -> c.getRemoteSipId().equals(remoteSipId))
                    .findFirst();

            if (configOpt.isPresent()) {
                handleRegularRegister(event, tx, request, remoteSipId, fromUri, configOpt.get());
                return;
            }

            // Check IVS1900 interconnect configs
            Optional<Ivs1900InterconnectConfig> ivs1900Opt = ivs1900Repository.findBySipId(remoteSipId);
            if (ivs1900Opt.isPresent()) {
                handleIvs1900Register(event, tx, request, remoteSipId, fromUri, ivs1900Opt.get());
                return;
            }

            log.warn("收到未知对端 REGISTER: {}", remoteSipId);
            sendResponse(tx, request, Response.FORBIDDEN);

        } catch (Exception e) {
            log.error("处理 REGISTER 请求失败: {}", e.getMessage(), e);
        }
    }

    private void handleRegularRegister(RequestEvent event, ServerTransaction tx, Request request,
                                       String remoteSipId, String fromUri, InterconnectConfig config) throws Exception {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            sendResponse(tx, request, Response.FORBIDDEN);
            log.debug("互联配置已禁用，拒绝 REGISTER: {}", remoteSipId);
            return;
        }

        AuthorizationHeader authHeader = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);

        if (authHeader == null) {
            String nonce = issueChallenge(tx, request, remoteSipId);
            return;
        }

        String nonce = authHeader.getNonce();
        if (!nonceMap.containsKey(nonce)) {
            log.warn("nonce 不存在或已过期: {}", nonce);
            sendResponse(tx, request, Response.FORBIDDEN);
            return;
        }
        nonceMap.remove(nonce);

        String ha1 = DigestAuthUtils.calcHa1(remoteSipId, REALM, config.getPassword());
        String ha2 = DigestAuthUtils.calcHa2("REGISTER", authHeader.getURI().toString());
        String expected = DigestAuthUtils.calcResponse(ha1, nonce, ha2);

        if (!expected.equals(authHeader.getResponse())) {
            log.warn("Digest 认证失败: {}", remoteSipId);
            sendResponse(tx, request, Response.FORBIDDEN);
            return;
        }

        ExpiresHeader expiresHeader = (ExpiresHeader) request.getHeader(ExpiresHeader.NAME);
        int expires = expiresHeader != null ? expiresHeader.getExpires() : 3600;

        if (expires == 0) {
            registrationTable.remove(remoteSipId);
            config.setUpLinkStatus(LinkStatus.OFFLINE);
            config.setDownLinkStatus(LinkStatus.OFFLINE);
            repository.save(config);
            sendResponse(tx, request, Response.OK);
            log.info("对端 {} 已注销", remoteSipId);
        } else {
            ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
            registrationTable.put(remoteSipId, new RegistrationEntry(
                    contact != null ? contact.getAddress().getURI().toString() : fromUri,
                    expires,
                    Instant.now().getEpochSecond(),
                    null,
                    config.getId(),
                    false
            ));
            config.setUpLinkStatus(LinkStatus.ONLINE);
            config.setDownLinkStatus(LinkStatus.ONLINE);
            repository.save(config);
            sendResponse(tx, request, Response.OK);
            log.info("对端 {} 注册成功，expires={}s", remoteSipId, expires);
            String contactIp = null;
            int contactPort = 0;
            if (contact != null && contact.getAddress().getURI() instanceof SipURI contactUri) {
                contactIp = contactUri.getHost();
                contactPort = contactUri.getPort();
            }
            catalogQueryService.queryCatalog(config, contactIp, contactPort);
            catalogSubscribeService.subscribe(config, contactIp, contactPort);
        }
    }

    private void handleIvs1900Register(RequestEvent event, ServerTransaction tx, Request request,
                                       String remoteSipId, String fromUri, Ivs1900InterconnectConfig ivs1900) throws Exception {
        AuthorizationHeader authHeader = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);

        if (authHeader == null) {
            issueChallenge(tx, request, remoteSipId);
            return;
        }

        String nonce = authHeader.getNonce();
        if (!nonceMap.containsKey(nonce)) {
            log.warn("IVS1900 nonce 不存在或已过期: {}", nonce);
            sendResponse(tx, request, Response.FORBIDDEN);
            return;
        }
        nonceMap.remove(nonce);

        String ha1 = DigestAuthUtils.calcHa1(remoteSipId, REALM, ivs1900.getPassword());
        String ha2 = DigestAuthUtils.calcHa2("REGISTER", authHeader.getURI().toString());
        String expected = DigestAuthUtils.calcResponse(ha1, nonce, ha2);

        if (!expected.equals(authHeader.getResponse())) {
            log.warn("IVS1900 Digest 认证失败: {}", remoteSipId);
            sendResponse(tx, request, Response.FORBIDDEN);
            return;
        }

        ExpiresHeader expiresHeader = (ExpiresHeader) request.getHeader(ExpiresHeader.NAME);
        int expires = expiresHeader != null ? expiresHeader.getExpires() : 3600;

        if (expires == 0) {
            registrationTable.remove(remoteSipId);
            ivs1900.setUpLinkStatus(LinkStatus.OFFLINE);
            ivs1900Repository.save(ivs1900);
            sendResponse(tx, request, Response.OK);
            log.info("IVS1900 {} 已注销，upLinkStatus=OFFLINE", remoteSipId);
        } else {
            ContactHeader contact = (ContactHeader) request.getHeader(ContactHeader.NAME);
            registrationTable.put(remoteSipId, new RegistrationEntry(
                    contact != null ? contact.getAddress().getURI().toString() : fromUri,
                    expires,
                    Instant.now().getEpochSecond(),
                    null,
                    ivs1900.getId(),
                    true
            ));
            ivs1900.setUpLinkStatus(LinkStatus.ONLINE);
            ivs1900Repository.save(ivs1900);
            sendResponse(tx, request, Response.OK);
            log.info("IVS1900 {} 注册成功，upLinkStatus=ONLINE，expires={}s", remoteSipId, expires);

            // Trigger catalog subscription to sync cameras
            String contactIp = null;
            int contactPort = 0;
            if (contact != null && contact.getAddress().getURI() instanceof SipURI contactUri) {
                contactIp = contactUri.getHost();
                contactPort = contactUri.getPort();
            }
            InterconnectConfig adapted = toInterconnectConfig(ivs1900, contactIp, contactPort);
            catalogQueryService.queryCatalog(adapted, contactIp, contactPort);
            catalogSubscribeService.subscribe(ivs1900, contactIp, contactPort);
        }
    }

    /** Adapts Ivs1900InterconnectConfig to InterconnectConfig for use with CatalogQueryService. */
    private InterconnectConfig toInterconnectConfig(Ivs1900InterconnectConfig ivs1900, String contactIp, int contactPort) {
        InterconnectConfig ic = new InterconnectConfig();
        ic.setId(ivs1900.getId());
        ic.setRemoteSipId(ivs1900.getSipId());
        ic.setRemoteIp(contactIp != null && !contactIp.isEmpty() ? contactIp : ivs1900.getIp());
        ic.setRemotePort(contactPort > 0 ? contactPort : ivs1900.getPort());
        ic.setRemoteDomain(ivs1900.getDomain());
        ic.setPassword(ivs1900.getPassword());
        ic.setEnabled(true);
        return ic;
    }

    private String issueChallenge(ServerTransaction tx, Request request, String remoteSipId) throws Exception {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        nonceMap.put(nonce, Instant.now().getEpochSecond());
        SipFactory sipFactory = SipFactory.getInstance();
        Response response = sipFactory.createMessageFactory().createResponse(Response.UNAUTHORIZED, request);
        WWWAuthenticateHeader wwwAuth = sipFactory.createHeaderFactory().createWWWAuthenticateHeader("Digest");
        wwwAuth.setRealm(REALM);
        wwwAuth.setNonce(nonce);
        wwwAuth.setAlgorithm("MD5");
        response.addHeader(wwwAuth);
        tx.sendResponse(response);
        log.debug("发送 401 挑战给 {}", remoteSipId);
        return nonce;
    }

    public void handleMessage(RequestEvent event) {
        Request request = event.getRequest();
        ServerTransaction tx = event.getServerTransaction();
        try {
            if (tx == null) {
                tx = sipProvider.getNewServerTransaction(request);
            }

            FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
            String remoteSipId = extractUserFromUri(from.getAddress().getURI().toString());

            RegistrationEntry entry = registrationTable.get(remoteSipId);
            if (entry == null) {
                log.warn("收到未注册对端的 MESSAGE: {}", remoteSipId);
                sendResponse(tx, request, Response.FORBIDDEN);
                return;
            }

            // Parse XML body and verify CmdType=Keepalive
            byte[] rawBody = request.getRawContent();
            if (rawBody == null || !isKeepalive(rawBody)) {
                log.warn("MESSAGE 不是 Keepalive，忽略: remoteSipId={}", remoteSipId);
                sendResponse(tx, request, Response.OK);
                return;
            }

            // Update lastHeartbeatAt in memory
            Instant now = Instant.now();
            registrationTable.put(remoteSipId, new RegistrationEntry(
                    entry.contact(), entry.expires(), entry.registeredAt(), now,
                    entry.interconnectConfigId(), entry.isIvs1900()));

            if (entry.isIvs1900()) {
                ivs1900Repository.findById(entry.interconnectConfigId()).ifPresent(ivs1900 -> {
                    ivs1900.setUpLinkStatus(LinkStatus.ONLINE);
                    ivs1900Repository.save(ivs1900);
                });
            } else {
                repository.findById(entry.interconnectConfigId()).ifPresent(config -> {
                    config.setLastHeartbeatAt(now);
                    config.setDownLinkStatus(LinkStatus.ONLINE);
                    repository.save(config);
                });
            }

            sendResponse(tx, request, Response.OK);
            log.debug("收到 Keepalive，更新 lastHeartbeatAt: remoteSipId={}", remoteSipId);

        } catch (Exception e) {
            log.error("处理 MESSAGE 请求失败: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void cleanupExpiredRegistrations() {
        long now = Instant.now().getEpochSecond();
        // Cleanup expired nonces
        nonceMap.entrySet().removeIf(e -> now - e.getValue() > NONCE_TTL_SECONDS);
        // Dual-scan: registration expiry OR heartbeat timeout
        registrationTable.entrySet().removeIf(entry -> {
            RegistrationEntry reg = entry.getValue();
            boolean registrationExpired = now - reg.registeredAt() > reg.expires();
            boolean heartbeatTimedOut = reg.lastHeartbeatAt() != null
                    && now - reg.lastHeartbeatAt().getEpochSecond() > HEARTBEAT_TIMEOUT_SECONDS;

            if (registrationExpired || heartbeatTimedOut) {
                String reason = registrationExpired ? "注册到期" : "心跳超时";
                log.warn("{}，清除注册 {}", reason, entry.getKey());
                if (reg.isIvs1900()) {
                    ivs1900Repository.findById(reg.interconnectConfigId()).ifPresent(ivs1900 -> {
                        ivs1900.setUpLinkStatus(LinkStatus.OFFLINE);
                        ivs1900Repository.save(ivs1900);
                    });
                    // 互联平台离线，所有本端相机标为 OFF
                    ivs1900CameraMappingRepository.findAll().forEach(cam -> {
                        if (!"OFF".equals(cam.getStatus())) {
                            cam.setStatus("OFF");
                            ivs1900CameraMappingRepository.save(cam);
                        }
                    });
                    log.info("IVS1900 互联离线，已将所有本端相机状态置为 OFF");
                    catalogSubscribeService.cancelSubscription(reg.interconnectConfigId());
                } else {
                    repository.findById(reg.interconnectConfigId()).ifPresent(config -> {
                        config.setUpLinkStatus(LinkStatus.OFFLINE);
                        config.setDownLinkStatus(LinkStatus.OFFLINE);
                        repository.save(config);
                    });
                    // 互联平台离线，该平台下所有外域设备标为 OFF
                    remoteDeviceRepository.findByInterconnectConfigId(reg.interconnectConfigId())
                            .forEach(dev -> {
                                if (!"OFF".equals(dev.getStatus())) {
                                    dev.setStatus("OFF");
                                    remoteDeviceRepository.save(dev);
                                }
                            });
                    log.info("外域互联 {} 离线，已将该平台下所有设备状态置为 OFF", reg.interconnectConfigId());
                    catalogSubscribeService.cancelSubscription(reg.interconnectConfigId());
                }
                return true;
            }
            return false;
        });
    }

    public void deregisterByConfigId(Long configId) {
        registrationTable.entrySet().removeIf(entry -> {
            if (configId.equals(entry.getValue().interconnectConfigId())) {
                log.info("上联已禁用，清除注册 {}", entry.getKey());
                return true;
            }
            return false;
        });
        repository.findById(configId).ifPresent(config -> {
            config.setUpLinkStatus(LinkStatus.OFFLINE);
            repository.save(config);
        });
    }

    public void shutdown() {
        registrationTable.clear();
        nonceMap.clear();
        repository.findAll().forEach(config -> {
            config.setUpLinkStatus(LinkStatus.OFFLINE);
            repository.save(config);
        });
        ivs1900Repository.findAll().forEach(ivs1900 -> {
            ivs1900.setUpLinkStatus(LinkStatus.OFFLINE);
            ivs1900Repository.save(ivs1900);
        });
        log.info("SipRegistrationServer 已关闭，所有上联状态重置为 OFFLINE");
    }

    private boolean isKeepalive(byte[] body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(body));
            NodeList nodes = doc.getElementsByTagName("CmdType");
            return nodes.getLength() > 0 && "Keepalive".equals(nodes.item(0).getTextContent().trim());
        } catch (Exception e) {
            log.debug("MESSAGE XML 解析失败: {}", e.getMessage());
            return false;
        }
    }

    private static final DateTimeFormatter GB_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private void sendResponse(ServerTransaction tx, Request request, int statusCode) throws Exception {
        Response response = SipFactory.getInstance().createMessageFactory().createResponse(statusCode, request);
        if (statusCode == Response.OK) {
            // GB/T 28181 §9.10 校时：Date 头使用 YYYY-MM-DDTHH:MM:SS 格式（非 RFC 2822）
            // 使用 ExtensionHeaderImpl 绕过 JAIN SIP 对 Date 头域的 RFC 2822 格式校验
            gov.nist.javax.sip.header.ExtensionHeaderImpl dateHeader =
                    new gov.nist.javax.sip.header.ExtensionHeaderImpl("Date");
            dateHeader.setValue(GB_DATE_FMT.format(Instant.now()));
            response.addHeader(dateHeader);
        }
        tx.sendResponse(response);
    }

    private String extractUserFromUri(String uri) {
        if (uri.startsWith("sip:")) uri = uri.substring(4);
        int atIdx = uri.indexOf('@');
        return atIdx > 0 ? uri.substring(0, atIdx) : uri;
    }

    record RegistrationEntry(String contact, int expires, long registeredAt, Instant lastHeartbeatAt,
                             Long interconnectConfigId, boolean isIvs1900) {}

    /** 查询已注册设备的 SIP contact URI（用于本端设备拉流获取目标地址）。 */
    public Optional<String> getRegisteredContact(String sipId) {
        return Optional.ofNullable(registrationTable.get(sipId)).map(RegistrationEntry::contact);
    }

    /** 查询已注册设备的注册记录（用于本端设备拉流获取目标地址）。 */
    public Optional<RegistrationEntry> getRegistrationEntry(String sipId) {
        return Optional.ofNullable(registrationTable.get(sipId));
    }
}

