package com.example.gbt28181.sip;

import com.example.gbt28181.domain.repository.Ivs1900CameraMappingRepository;
import com.example.gbt28181.service.LocalDeviceStreamService;
import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.repository.InterconnectConfigRepository;
import com.example.gbt28181.domain.repository.RemoteDeviceRepository;
import com.example.gbt28181.ivs1900.ZLMediaKitClient;
import com.example.gbt28181.config.ZlmConfig;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.sip.*;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 外域设备视频流 SIP INVITE 业务服务。
 * 负责发起/终止 GB/T 28181 视频流会话。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SipInviteService {

    private final SipMessageSender sipMessageSender;
    private final StreamSessionStore sessionStore;
    private final ZLMediaKitClient zlmClient;
    private final ZlmConfig zlmConfig;
    private final InterconnectConfigRepository interconnectConfigRepo;
    private final RemoteDeviceRepository remoteDeviceRepo;
    private final Ivs1900CameraMappingRepository cameraMappingRepo;

    @Setter(onMethod_ = {@Autowired, @Lazy})
    private LocalDeviceStreamService localDeviceStreamService;

    /** 等待 INVITE 200 OK 的 Future，key = callId */
    private final ConcurrentHashMap<String, CompletableFuture<ResponseEvent>> pendingInvites =
            new ConcurrentHashMap<>();

    private SipProvider sipProvider;
    private javax.sip.header.HeaderFactory headerFactory;
    private javax.sip.message.MessageFactory messageFactory;

    public void setSipProvider(SipProvider sipProvider) throws PeerUnavailableException {
        this.sipProvider = sipProvider;
        SipFactory factory = SipFactory.getInstance();
        this.headerFactory = factory.createHeaderFactory();
        this.messageFactory = factory.createMessageFactory();
    }

    /**
     * 向外域设备发起视频流请求。
     *
     * @return HTTP-FLV 播放地址
     * @throws IllegalArgumentException 设备或互联配置不存在
     * @throws IllegalStateException    ZLM 不可用或 INVITE 超时
     */
    public String startStream(String deviceId) {
        // 1. 查找设备和互联配置
        var device = remoteDeviceRepo.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在: " + deviceId));
        InterconnectConfig config = interconnectConfigRepo.findById(device.getInterconnectConfigId())
                .orElseThrow(() -> new IllegalArgumentException("互联配置不存在: " + device.getInterconnectConfigId()));
        return startStream(config, deviceId, "gb28181_");
    }

    /**
     * 向指定目标发起视频流请求（本端/外域通用）。
     *
     * @param target    目标互联配置（含 IP、端口、SIP ID）
     * @param deviceId  目标设备 SIP ID（用于 INVITE Request-URI 和会话 key）
     * @param streamPrefix ZLMediaKit stream ID 前缀（外域用 "gb28181_"，本端用 "local_"）
     * @return HTTP-FLV 播放地址
     */
    public String startStream(InterconnectConfig target, String deviceId, String streamPrefix) {
        // 1. 如果已有会话，先停止
        if (sessionStore.has(deviceId)) {
            log.info("设备 {} 已有活跃流会话，先停止旧会话", deviceId);
            stopStream(deviceId);
        }

        // 2. 向 ZLMediaKit 申请 RTP 收流端口
        String streamId = streamPrefix + deviceId.replaceAll("[^a-zA-Z0-9]", "_");
        int rtpPort = zlmClient.openRtpServer(streamId);
        if (rtpPort <= 0) {
            throw new IllegalStateException("ZLMediaKit 创建 RTP 端口失败，请检查 ZLM 服务是否运行");
        }

        // 3. 构造 SDP offer
        // GB/T 28181 实时流 SSRC 首位为 0，后 9 位随机
        String ssrc = String.format("0%09d", (long) (Math.random() * 1_000_000_000L));
        String localSipId = LocalSipConfigHolder.getDeviceId();
        String zlmIp = zlmConfig.getRtpIp();
        String sdp = buildSdpOffer(localSipId, zlmIp, rtpPort, ssrc, deviceId);

        // 4. 发送 INVITE，等待 200 OK
        String callId = UUID.randomUUID().toString();
        CompletableFuture<ResponseEvent> future = new CompletableFuture<>();
        pendingInvites.put(callId, future);

        InterconnectConfig config = target;
        try {
            var ct = sipMessageSender.sendInvite(config, deviceId, callId, sdp, ssrc);
            if (ct == null) {
                throw new IllegalStateException("发送 INVITE 失败");
            }

            // 从 INVITE 请求中提取 From-tag
            javax.sip.header.FromHeader fromHeader =
                    (javax.sip.header.FromHeader) ct.getRequest().getHeader(javax.sip.header.FromHeader.NAME);
            String fromTag = fromHeader != null ? fromHeader.getTag() : "";

            ResponseEvent resp200 = future.get(10, TimeUnit.SECONDS);

            // 6. 发送 ACK
            sipMessageSender.sendAck(resp200, fromTag);

            // 7. 提取 To-tag，存储会话
            javax.sip.header.ToHeader toHeader =
                    (javax.sip.header.ToHeader) resp200.getResponse().getHeader(javax.sip.header.ToHeader.NAME);
            String toTag = toHeader != null ? toHeader.getTag() : "";

            StreamSession session = new StreamSession(deviceId, callId, fromTag, toTag, 1L, streamId, rtpPort);
            sessionStore.put(session);
            log.info("视频流会话建立成功 deviceId={} streamId={} rtpPort={}", deviceId, streamId, rtpPort);

            // 8. 返回 FLV 播放地址
            return zlmClient.buildStreamUrl(streamId);

        } catch (TimeoutException e) {
            log.warn("INVITE 超时 10s deviceId={}", deviceId);
            zlmClient.closeRtpServer(streamId);
            throw new IllegalStateException("INVITE timeout");
        } catch (IllegalArgumentException | IllegalStateException e) {
            zlmClient.closeRtpServer(streamId);
            throw e;
        } catch (Exception e) {
            log.error("startStream 异常 deviceId={}: {}", deviceId, e.getMessage(), e);
            zlmClient.closeRtpServer(streamId);
            throw new IllegalStateException("启动视频流失败: " + e.getMessage());
        } finally {
            pendingInvites.remove(callId);
        }
    }

    /**
     * 处理上级平台发来的 INVITE（拉取本端 IVS1900 相机流）。
     */
    public void onIncomingInvite(RequestEvent event) {
        Request request = event.getRequest();
        try {
            // 解析目标 gbDeviceId（Request-URI user 部分）
            javax.sip.address.SipURI requestUri = (javax.sip.address.SipURI) request.getRequestURI();
            String gbDeviceId = requestUri.getUser();

            if (!cameraMappingRepo.findByGbDeviceId(gbDeviceId).isPresent()) {
                sendInviteResponse(event, Response.NOT_FOUND);
                log.warn("上级 INVITE 目标设备不存在 gbDeviceId={}", gbDeviceId);
                return;
            }

            // 100 Trying
            sendInviteResponse(event, Response.TRYING);

            // 通过 LocalDeviceStreamService 拉起流（ZLM 分配端口 + 向 IVS1900 发 INVITE）
            String streamId = "local_" + gbDeviceId.replaceAll("[^a-zA-Z0-9]", "_");
            try {
                localDeviceStreamService.startStream(gbDeviceId);
            } catch (Exception e) {
                log.warn("上级 INVITE 启动本端流失败 gbDeviceId={}: {}", gbDeviceId, e.getMessage());
                sendInviteResponse(event, Response.SERVER_INTERNAL_ERROR);
                return;
            }

            // 从 SessionStore 取 ZLM 端口
            StreamSession session = sessionStore.get(gbDeviceId).orElse(null);
            if (session == null) {
                log.warn("流会话未建立 gbDeviceId={}", gbDeviceId);
                sendInviteResponse(event, Response.SERVER_INTERNAL_ERROR);
                return;
            }

            // 构造 200 OK + SDP
            String localIp = LocalSipConfigHolder.getSipIp();
            String zlmIp = zlmConfig.getRtpIp() != null ? zlmConfig.getRtpIp() : localIp;
            String sdp = buildSdpAnswer(zlmIp, session.zlmPort(), gbDeviceId);

            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) tx = sipProvider.getNewServerTransaction(request);

            ContentTypeHeader contentType = headerFactory.createContentTypeHeader("Application", "sdp");
            byte[] sdpBytes = sdp.getBytes(StandardCharsets.UTF_8);
            Response ok = messageFactory.createResponse(Response.OK, request);
            ok.setContent(sdpBytes, contentType);

            // 添加 Contact 头
            String localSipId = LocalSipConfigHolder.getDeviceId();
            int localPort = LocalSipConfigHolder.getSipPort();
            javax.sip.address.AddressFactory addrFactory = SipFactory.getInstance().createAddressFactory();
            ContactHeader contact = headerFactory.createContactHeader(
                    addrFactory.createAddress(addrFactory.createSipURI(localSipId, localIp + ":" + localPort)));
            ok.addHeader(contact);

            tx.sendResponse(ok);

            // 记录入站会话（callId → gbDeviceId），复用已有 StreamSession（但标记来自上级）
            CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
            String callId = callIdHeader != null ? callIdHeader.getCallId() : UUID.randomUUID().toString();
            log.info("上级 INVITE 本端相机流已建立 gbDeviceId={} zlmPort={} callId={}", gbDeviceId, session.zlmPort(), callId);

        } catch (Exception e) {
            log.error("处理上级 INVITE 失败: {}", e.getMessage(), e);
            try { sendInviteResponse(event, Response.SERVER_INTERNAL_ERROR); } catch (Exception ignored) {}
        }
    }

    private void sendInviteResponse(RequestEvent event, int statusCode) {
        try {
            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) tx = sipProvider.getNewServerTransaction(event.getRequest());
            tx.sendResponse(messageFactory.createResponse(statusCode, event.getRequest()));
        } catch (Exception e) {
            log.warn("发送 INVITE 响应 {} 失败: {}", statusCode, e.getMessage());
        }
    }

    private String buildSdpAnswer(String mediaIp, int rtpPort, String deviceId) {
        return "v=0\r\n"
                + "o=" + deviceId + " 0 0 IN IP4 " + mediaIp + "\r\n"
                + "s=Play\r\n"
                + "c=IN IP4 " + mediaIp + "\r\n"
                + "t=0 0\r\n"
                + "m=video " + rtpPort + " RTP/AVP 96 98\r\n"
                + "a=sendonly\r\n"
                + "a=rtpmap:96 PS/90000\r\n"
                + "a=rtpmap:98 H264/90000\r\n"
                + "f=\r\n";
    }

    /**
     * 停止外域设备视频流。
     */
    public void stopStream(String deviceId) {
        StreamSession session = sessionStore.get(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("No active stream session for: " + deviceId));

        InterconnectConfig config = remoteDeviceRepo.findByDeviceId(deviceId)
                .flatMap(d -> interconnectConfigRepo.findById(d.getInterconnectConfigId()))
                .orElse(null);

        if (config != null) {
            sipMessageSender.sendBye(session, config);
        }
        zlmClient.closeRtpServer(session.streamId());
        sessionStore.remove(deviceId);
        log.info("视频流会话已停止 deviceId={}", deviceId);
    }

    /**
     * 停止视频流（本端/外域通用，调用方提供目标配置）。
     */
    public void stopStream(String deviceId, InterconnectConfig target) {
        StreamSession session = sessionStore.get(deviceId).orElse(null);
        if (session == null) {
            log.debug("stopStream: 无活跃会话 deviceId={}", deviceId);
            return;
        }
        sipMessageSender.sendBye(session, target);
        zlmClient.closeRtpServer(session.streamId());
        sessionStore.remove(deviceId);
        log.info("视频流会话已停止 deviceId={}", deviceId);
    }

    /**
     * 处理对端主动发来的 BYE（由 GbtSipListener 调用）。
     */
    public void onRemoteBye(String callId) {
        sessionStore.findByCallId(callId).ifPresent(session -> {
            zlmClient.closeRtpServer(session.streamId());
            sessionStore.remove(session.deviceId());
            // 若为本端相机会话（streamId 以 local_ 开头），也停止 LocalDeviceStream
            if (session.streamId().startsWith("local_")) {
                try {
                    // deviceId 此处为 ivsCameraId；通过 cameraMappingRepo 反查 gbDeviceId
                    cameraMappingRepo.findByIvsCameraId(session.deviceId()).ifPresent(m -> {
                        try { localDeviceStreamService.stopStream(m.getGbDeviceId()); } catch (Exception ignored) {}
                    });
                } catch (Exception ignored) {}
            }
            log.info("收到对端 BYE，会话已清理 deviceId={} callId={}", session.deviceId(), callId);
        });
    }

    /**
     * 处理 INVITE 200 OK 响应（由 GbtSipListener 调用）。
     */
    public void onInviteOk(ResponseEvent event) {
        javax.sip.header.CallIdHeader callIdHeader =
                (javax.sip.header.CallIdHeader) event.getResponse().getHeader(javax.sip.header.CallIdHeader.NAME);
        if (callIdHeader == null) return;
        String callId = callIdHeader.getCallId();
        CompletableFuture<ResponseEvent> future = pendingInvites.get(callId);
        if (future != null) {
            future.complete(event);
        }
    }

    // ===== 私有方法 =====

    private String buildSdpOffer(String localSipId, String mediaIp, int rtpPort, String ssrc, String deviceId) {
        return "v=0\r\n"
                + "o=" + localSipId + " 0 0 IN IP4 " + mediaIp + "\r\n"
                + "s=Play\r\n"
                + "c=IN IP4 " + mediaIp + "\r\n"
                + "t=0 0\r\n"
                + "m=video " + rtpPort + " RTP/AVP 96 98\r\n"
                + "a=recvonly\r\n"
                + "a=rtpmap:96 PS/90000\r\n"
                + "a=rtpmap:98 H264/90000\r\n"
                + "y=" + ssrc + "\r\n";
    }
}
