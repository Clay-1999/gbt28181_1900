package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.LinkStatus;
import com.example.gbt28181.domain.repository.InterconnectConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class SipRegistrationClient {

    private final InterconnectConfigRepository repository;
    private final CatalogQueryService catalogQueryService;
    private final CatalogSubscribeHandler catalogSubscribeHandler;

    private SipProvider sipProvider;
    private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private javax.sip.message.MessageFactory messageFactory;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // Initial register / retry tasks (configId → future)
    private final Map<Long, ScheduledFuture<?>> registerTasks = new ConcurrentHashMap<>();
    // Renewal REGISTER tasks (configId → future)
    private final Map<Long, ScheduledFuture<?>> renewalTasks = new ConcurrentHashMap<>();
    // Heartbeat MESSAGE tasks (configId → future)
    private final Map<Long, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();
    // Consecutive heartbeat failure counts
    private final Map<Long, Integer> heartbeatFailCounts = new ConcurrentHashMap<>();
    // configId → retry count for exponential backoff
    private final Map<Long, Integer> retryCounts = new ConcurrentHashMap<>();
    // callId → configId for REGISTER requests
    private final Map<String, Long> callIdToConfigId = new ConcurrentHashMap<>();
    // callIds belonging to renewal REGISTER requests (to distinguish from initial)
    private final Set<String> renewalCallIds = ConcurrentHashMap.newKeySet();
    // callId → configId for MESSAGE/heartbeat requests
    private final Map<String, Long> heartbeatCallIdToConfigId = new ConcurrentHashMap<>();

    private static final int MAX_RETRIES = 10;
    private static final long INITIAL_RETRY_DELAY_S = 5;
    private static final long MAX_RETRY_DELAY_S = 300;
    private static final long HEARTBEAT_INTERVAL_S = 60;
    private static final int HEARTBEAT_FAIL_THRESHOLD = 3;

    public void setSipProvider(SipProvider sipProvider) throws PeerUnavailableException {
        this.sipProvider = sipProvider;
        SipFactory factory = SipFactory.getInstance();
        this.addressFactory = factory.createAddressFactory();
        this.headerFactory = factory.createHeaderFactory();
        this.messageFactory = factory.createMessageFactory();
    }

    // ---- Lifecycle ----

    public void startAll() {
        repository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()) && Boolean.TRUE.equals(c.getUpLinkEnabled()))
                .forEach(this::startRegistration);
    }

    public void startRegistration(InterconnectConfig config) {
        retryCounts.put(config.getId(), 0);
        scheduleRegister(config, 0);
    }

    public void stopRegistration(Long configId) {
        cancelTask(registerTasks, configId);
        cancelTask(renewalTasks, configId);
        cancelTask(heartbeatTasks, configId);
        heartbeatFailCounts.remove(configId);
        retryCounts.remove(configId);
        repository.findById(configId).ifPresent(config -> {
            config.setDownLinkStatus(LinkStatus.OFFLINE);
            repository.save(config);
        });
    }

    public void stopAll() {
        cancelAll(registerTasks);
        cancelAll(renewalTasks);
        cancelAll(heartbeatTasks);
        retryCounts.clear();
        heartbeatFailCounts.clear();
        callIdToConfigId.clear();
        renewalCallIds.clear();
        heartbeatCallIdToConfigId.clear();
        repository.findAll().forEach(config -> {
            config.setDownLinkStatus(LinkStatus.OFFLINE);
            repository.save(config);
        });
        log.info("所有 SIP Client 注册任务已停止");
    }

    // ---- Initial registration ----

    private void scheduleRegister(InterconnectConfig config, long delaySeconds) {
        cancelTask(registerTasks, config.getId());
        ScheduledFuture<?> future = scheduler.schedule(() -> doRegister(config), delaySeconds, TimeUnit.SECONDS);
        registerTasks.put(config.getId(), future);
    }

    private void doRegister(InterconnectConfig config) {
        try {
            InterconnectConfig fresh = repository.findById(config.getId()).orElse(config);
            if (!Boolean.TRUE.equals(fresh.getEnabled()) || !Boolean.TRUE.equals(fresh.getUpLinkEnabled())) return;

            fresh.setDownLinkStatus(LinkStatus.REGISTERING);
            repository.save(fresh);

            String callId = UUID.randomUUID().toString();
            callIdToConfigId.put(callId, fresh.getId());

            Request request = buildRegisterRequest(fresh, callId, null, null);
            ClientTransaction tx = sipProvider.getNewClientTransaction(request);
            tx.sendRequest();
            log.info("发送 REGISTER → {}:{} (configId={})", fresh.getRemoteIp(), fresh.getRemotePort(), fresh.getId());

        } catch (Exception e) {
            log.error("发送 REGISTER 失败 configId={}: {}", config.getId(), e.getMessage());
            handleFailure(config.getId());
        }
    }

    // ---- Renewal ----

    private void scheduleRenewal(InterconnectConfig config, long delaySeconds) {
        cancelTask(renewalTasks, config.getId());
        ScheduledFuture<?> future = scheduler.schedule(() -> doRenewal(config), delaySeconds, TimeUnit.SECONDS);
        renewalTasks.put(config.getId(), future);
    }

    private void doRenewal(InterconnectConfig config) {
        try {
            InterconnectConfig fresh = repository.findById(config.getId()).orElse(config);
            if (!Boolean.TRUE.equals(fresh.getEnabled()) || !Boolean.TRUE.equals(fresh.getUpLinkEnabled())) return;

            String callId = UUID.randomUUID().toString();
            callIdToConfigId.put(callId, fresh.getId());
            renewalCallIds.add(callId);

            Request request = buildRegisterRequest(fresh, callId, null, null);
            ClientTransaction tx = sipProvider.getNewClientTransaction(request);
            tx.sendRequest();
            log.debug("发送 REGISTER renewal → configId={}", fresh.getId());

        } catch (Exception e) {
            log.error("发送 REGISTER renewal 失败 configId={}: {}", config.getId(), e.getMessage());
            onRenewalFailure(config.getId());
        }
    }

    private void onRenewalFailure(Long configId) {
        log.warn("REGISTER renewal 失败 configId={}，取消心跳定时器，触发重新注册", configId);
        cancelTask(heartbeatTasks, configId);
        heartbeatFailCounts.remove(configId);
        handleFailure(configId);
    }

    // ---- Heartbeat ----

    private void scheduleHeartbeat(InterconnectConfig config) {
        cancelTask(heartbeatTasks, config.getId());
        ScheduledFuture<?> future = scheduler.schedule(() -> doHeartbeat(config), HEARTBEAT_INTERVAL_S, TimeUnit.SECONDS);
        heartbeatTasks.put(config.getId(), future);
    }

    private void doHeartbeat(InterconnectConfig config) {
        try {
            InterconnectConfig fresh = repository.findById(config.getId()).orElse(config);
            if (!Boolean.TRUE.equals(fresh.getEnabled())) return;

            String callId = UUID.randomUUID().toString();
            heartbeatCallIdToConfigId.put(callId, fresh.getId());

            Request request = buildKeepaliveRequest(fresh, callId);
            ClientTransaction tx = sipProvider.getNewClientTransaction(request);
            tx.sendRequest();
            log.debug("发送 MESSAGE Keepalive → configId={}", fresh.getId());

        } catch (Exception e) {
            log.error("发送 MESSAGE Keepalive 失败 configId={}: {}", config.getId(), e.getMessage());
            onHeartbeatFailure(config.getId());
        }
    }

    private void onHeartbeatFailure(Long configId) {
        int fails = heartbeatFailCounts.merge(configId, 1, Integer::sum);
        log.warn("心跳失败 configId={} (连续{}次)", configId, fails);
        if (fails >= HEARTBEAT_FAIL_THRESHOLD) {
            log.warn("心跳连续{}次失败，取消续约定时器，触发重新注册 configId={}", HEARTBEAT_FAIL_THRESHOLD, configId);
            cancelTask(renewalTasks, configId);
            heartbeatFailCounts.remove(configId);
            handleFailure(configId);
        } else {
            // Schedule next heartbeat attempt
            repository.findById(configId).ifPresent(this::scheduleHeartbeat);
        }
    }

    // ---- Response handling ----

    public void handleResponse(ResponseEvent event) {
        Response response = event.getResponse();
        int status = response.getStatusCode();

        CallIdHeader callIdHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
        if (callIdHeader == null) return;
        String callId = callIdHeader.getCallId();

        // Route MESSAGE responses to heartbeat handler
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        if (cseq != null && Request.MESSAGE.equals(cseq.getMethod())) {
            handleMessageResponse(callId, status);
            return;
        }

        // REGISTER response
        Long configId = callIdToConfigId.remove(callId);
        if (configId == null) return;

        boolean isRenewal = renewalCallIds.remove(callId);
        InterconnectConfig config = repository.findById(configId).orElse(null);
        if (config == null) return;

        if (status == Response.UNAUTHORIZED) {
            WWWAuthenticateHeader wwwAuth = (WWWAuthenticateHeader) response.getHeader(WWWAuthenticateHeader.NAME);
            if (wwwAuth == null) {
                if (isRenewal) onRenewalFailure(configId);
                else handleFailure(configId);
                return;
            }
            try {
                String nonce = wwwAuth.getNonce();
                String realm = wwwAuth.getRealm();
                String ha1 = DigestAuthUtils.calcHa1(config.getRemoteSipId(), realm, config.getPassword());
                String ha2 = DigestAuthUtils.calcHa2("REGISTER", "sip:" + config.getRemoteDomain());
                String digestResponse = DigestAuthUtils.calcResponse(ha1, nonce, ha2);

                String newCallId = UUID.randomUUID().toString();
                callIdToConfigId.put(newCallId, configId);
                if (isRenewal) renewalCallIds.add(newCallId);

                Request request = buildRegisterRequest(config, newCallId, realm, nonce);
                AuthorizationHeader auth = headerFactory.createAuthorizationHeader("Digest");
                auth.setUsername(config.getRemoteSipId());
                auth.setRealm(realm);
                auth.setNonce(nonce);
                auth.setURI(addressFactory.createURI("sip:" + config.getRemoteDomain()));
                auth.setAlgorithm("MD5");
                auth.setResponse(digestResponse);
                request.addHeader(auth);

                ClientTransaction tx = sipProvider.getNewClientTransaction(request);
                tx.sendRequest();
                log.debug("发送带 Digest 的 REGISTER → configId={} isRenewal={}", configId, isRenewal);

            } catch (Exception e) {
                log.error("Digest 应答失败 configId={}: {}", configId, e.getMessage());
                if (isRenewal) onRenewalFailure(configId);
                else handleFailure(configId);
            }

        } else if (status == Response.OK) {
            ExpiresHeader expiresHeader = (ExpiresHeader) response.getHeader(ExpiresHeader.NAME);
            int expires = expiresHeader != null ? expiresHeader.getExpires() : 3600;
            long renewIn = (long) (expires * 2.0 / 3.0);

            if (isRenewal) {
                // Renewal succeeded: reschedule renewal timer only, heartbeat continues uninterrupted
                scheduleRenewal(config, renewIn);
                log.debug("REGISTER renewal 成功，{}s 后再次续约 configId={}", renewIn, configId);
            } else {
                // Initial/retry register succeeded: start both timers
                retryCounts.put(configId, 0);
                heartbeatFailCounts.remove(configId);
                config.setDownLinkStatus(LinkStatus.ONLINE);
                repository.save(config);
                log.info("REGISTER 成功，对端 {} (configId={})，启动续约({})和心跳定时器",
                        config.getRemoteSipId(), configId, renewIn);
                scheduleRenewal(config, renewIn);
                scheduleHeartbeat(config);
                catalogQueryService.queryCatalog(config);
                // 注册成功后主动推送本端目录给上级平台
                final InterconnectConfig pushConfig = config;
                CompletableFuture.runAsync(() -> {
                    try {
                        catalogSubscribeHandler.pushCatalogToAddress(
                                pushConfig.getRemoteSipId(), pushConfig.getRemoteIp(), pushConfig.getRemotePort());
                    } catch (Exception ex) {
                        log.warn("注册后推送 Catalog NOTIFY 失败 configId={}: {}", pushConfig.getId(), ex.getMessage());
                    }
                });
            }

        } else if (status >= 400) {
            log.warn("REGISTER 失败 status={} configId={} isRenewal={}", status, configId, isRenewal);
            if (isRenewal) onRenewalFailure(configId);
            else handleFailure(configId);
        }
    }

    private void handleMessageResponse(String callId, int status) {
        Long configId = heartbeatCallIdToConfigId.remove(callId);
        if (configId == null) return;

        if (status == Response.OK) {
            heartbeatFailCounts.remove(configId);
            log.debug("心跳 200 OK configId={}", configId);
            repository.findById(configId).ifPresent(this::scheduleHeartbeat);
        } else {
            log.warn("心跳响应异常 status={} configId={}", status, configId);
            onHeartbeatFailure(configId);
        }
    }

    public void handleRegisterTimeout(String callId) {
        boolean isRenewal = renewalCallIds.remove(callId);
        Long configId = callIdToConfigId.remove(callId);
        if (configId == null) return;
        log.warn("REGISTER 超时 configId={} isRenewal={}", configId, isRenewal);
        if (isRenewal) onRenewalFailure(configId);
        else handleFailure(configId);
    }

    public void handleMessageTimeout(String callId) {
        Long configId = heartbeatCallIdToConfigId.remove(callId);
        if (configId == null) return;
        log.warn("心跳超时 configId={}", configId);
        onHeartbeatFailure(configId);
    }

    // ---- Failure / exponential backoff ----

    private void handleFailure(Long configId) {
        cancelTask(renewalTasks, configId);
        cancelTask(heartbeatTasks, configId);

        InterconnectConfig config = repository.findById(configId).orElse(null);
        if (config == null) return;

        int retries = retryCounts.getOrDefault(configId, 0);
        if (retries >= MAX_RETRIES) {
            config.setDownLinkStatus(LinkStatus.ERROR);
            repository.save(config);
            log.error("REGISTER 重试超过 {} 次，停止 configId={}", MAX_RETRIES, configId);
            return;
        }

        config.setDownLinkStatus(LinkStatus.OFFLINE);
        repository.save(config);

        long delay = Math.min(INITIAL_RETRY_DELAY_S * (1L << retries), MAX_RETRY_DELAY_S);
        retryCounts.put(configId, retries + 1);
        log.info("REGISTER 失败，{}s 后重试 (第{}次) configId={}", delay, retries + 1, configId);
        scheduleRegister(config, delay);
    }

    // ---- Utilities ----

    private void cancelTask(Map<Long, ScheduledFuture<?>> map, Long configId) {
        ScheduledFuture<?> f = map.remove(configId);
        if (f != null) f.cancel(false);
    }

    private void cancelAll(Map<Long, ScheduledFuture<?>> map) {
        map.values().forEach(f -> f.cancel(false));
        map.clear();
    }

    // ---- Request builders ----

    private Request buildKeepaliveRequest(InterconnectConfig config, String callId) throws Exception {
        String localSipId = getLocalSipId();
        String localIp = getLocalIp();
        int localPort = getLocalPort();

        SipURI requestUri = addressFactory.createSipURI(
                config.getRemoteSipId(), config.getRemoteIp() + ":" + config.getRemotePort());
        Address fromAddress = addressFactory.createAddress(
                addressFactory.createSipURI(localSipId, config.getRemoteDomain()));
        Address toAddress = addressFactory.createAddress(
                addressFactory.createSipURI(config.getRemoteSipId(), config.getRemoteDomain()));
        Address contactAddress = addressFactory.createAddress(
                addressFactory.createSipURI(localSipId, localIp + ":" + localPort));

        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress,
                UUID.randomUUID().toString().substring(0, 8));
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);
        CallIdHeader callIdHeader = headerFactory.createCallIdHeader(callId);
        CSeqHeader cseqHeader = headerFactory.createCSeqHeader(1L, Request.MESSAGE);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
        ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort,
                sipProvider.getListeningPoints()[0].getTransport(), null);

        ContentTypeHeader contentType = headerFactory.createContentTypeHeader("application", "MANSCDP+xml");
        int sn = (int) (System.currentTimeMillis() / 1000 % 100000);
        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Notify>\n"
                + "<CmdType>Keepalive</CmdType>\n"
                + "<SN>" + sn + "</SN>\n"
                + "<DeviceID>" + localSipId + "</DeviceID>\n"
                + "<Status>OK</Status>\n"
                + "</Notify>";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

        Request request = messageFactory.createRequest(requestUri, Request.MESSAGE,
                callIdHeader, cseqHeader, fromHeader, toHeader, List.of(viaHeader), maxForwards);
        request.addHeader(contactHeader);
        request.addHeader(contentType);
        request.setContent(bodyBytes, contentType);
        return request;
    }

    private Request buildRegisterRequest(InterconnectConfig config, String callId,
                                         String realm, String nonce) throws Exception {
        String localSipId = getLocalSipId();
        String localIp = getLocalIp();
        int localPort = getLocalPort();

        // Request-URI 必须使用 IP:Port，JAIN-SIP 依赖它路由报文；domain 只用于 From/To
        SipURI requestUri = addressFactory.createSipURI(null,
                config.getRemoteIp() + ":" + config.getRemotePort());
        Address fromAddress = addressFactory.createAddress(
                addressFactory.createSipURI(localSipId, config.getRemoteDomain()));
        Address toAddress = addressFactory.createAddress(
                addressFactory.createSipURI(localSipId, config.getRemoteDomain()));
        Address contactAddress = addressFactory.createAddress(
                addressFactory.createSipURI(localSipId, localIp + ":" + localPort));

        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress,
                UUID.randomUUID().toString().substring(0, 8));
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);
        CallIdHeader callIdHeader = headerFactory.createCallIdHeader(callId);
        CSeqHeader cseqHeader = headerFactory.createCSeqHeader(1L, Request.REGISTER);
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
        ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort,
                sipProvider.getListeningPoints()[0].getTransport(), null);
        ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(3600);

        Request request = messageFactory.createRequest(requestUri, Request.REGISTER,
                callIdHeader, cseqHeader, fromHeader, toHeader, List.of(viaHeader), maxForwards);
        request.addHeader(contactHeader);
        request.addHeader(expiresHeader);
        return request;
    }

    private String getLocalSipId() {
        return LocalSipConfigHolder.getDeviceId();
    }

    private String getLocalIp() {
        return LocalSipConfigHolder.getSipIp();
    }

    private int getLocalPort() {
        return LocalSipConfigHolder.getSipPort();
    }
}
