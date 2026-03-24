package com.example.gbt28181.service;

import com.example.gbt28181.api.dto.PlaybackControlRequest;
import com.example.gbt28181.api.dto.PlaybackRequest;
import com.example.gbt28181.api.exception.ResourceNotFoundException;
import com.example.gbt28181.config.ZlmConfig;
import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.repository.InterconnectConfigRepository;
import com.example.gbt28181.domain.repository.Ivs1900CameraMappingRepository;
import com.example.gbt28181.domain.repository.Ivs1900InterconnectConfigRepository;
import com.example.gbt28181.domain.repository.RemoteDeviceRepository;
import com.example.gbt28181.ivs1900.ZLMediaKitClient;
import com.example.gbt28181.sip.LocalSipConfigHolder;
import com.example.gbt28181.sip.PlaybackSession;
import com.example.gbt28181.sip.PlaybackSessionStore;
import com.example.gbt28181.sip.SipMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sip.ResponseEvent;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 历史回放业务服务。
 * 负责通过 INVITE(s=Playback)/INFO(MANSRTSP)/BYE 实现 GB/T 28181 §9.8 历史回放。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SipPlaybackService {

    private final SipMessageSender sipMessageSender;
    private final PlaybackSessionStore sessionStore;
    private final ZLMediaKitClient zlmClient;
    private final ZlmConfig zlmConfig;
    private final RemoteDeviceRepository remoteDeviceRepo;
    private final InterconnectConfigRepository interconnectConfigRepo;
    private final Ivs1900CameraMappingRepository cameraMappingRepo;
    private final Ivs1900InterconnectConfigRepository ivs1900ConfigRepo;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 等待 INVITE 200 OK 的 future，key = callId */
    private final ConcurrentHashMap<String, CompletableFuture<ResponseEvent>> pendingInvites =
            new ConcurrentHashMap<>();

    /**
     * 发起历史回放。
     *
     * @param type     "local" 或 "remote"
     * @param deviceId 设备 ID（local 为 gbDeviceId，remote 为 deviceId）
     * @param req      回放时间范围
     * @return 流播放 URL
     */
    public String startPlayback(String type, String deviceId, PlaybackRequest req) {
        InterconnectConfig config = resolveConfig(type, deviceId);
        String targetDeviceId = resolveTargetDeviceId(type, deviceId);

        // 如果已有活跃回放会话，先停止
        if (sessionStore.has(deviceId)) {
            log.info("设备 {} 已有活跃回放会话，先停止旧会话", deviceId);
            stopPlayback(type, deviceId);
        }

        // 申请 ZLM RTP 收流端口
        String streamId = "playback_" + deviceId.replaceAll("[^a-zA-Z0-9]", "_");
        int rtpPort = zlmClient.openRtpServer(streamId);
        if (rtpPort <= 0) {
            throw new IllegalStateException("ZLMediaKit 创建 RTP 端口失败，请检查 ZLM 服务是否运行");
        }

        // 构造回放 SDP（s=Playback，SSRC 首位为 1）
        String ssrc = String.format("1%09d", (long) (Math.random() * 1_000_000_000L));
        String zlmIp = zlmConfig.getRtpIp();
        String localSipId = LocalSipConfigHolder.getDeviceId();
        long startTs = parseTimestamp(req.getStartTime());
        long endTs = parseTimestamp(req.getEndTime());
        String sdp = buildPlaybackSdp(localSipId, zlmIp, rtpPort, ssrc, targetDeviceId, startTs, endTs);

        String callId = UUID.randomUUID().toString();
        CompletableFuture<ResponseEvent> future = new CompletableFuture<>();
        pendingInvites.put(callId, future);

        try {
            var ct = sipMessageSender.sendInvite(config, targetDeviceId, callId, sdp, ssrc);
            if (ct == null) {
                throw new IllegalStateException("发送 INVITE 失败");
            }

            FromHeader fromHeader = (FromHeader) ct.getRequest().getHeader(FromHeader.NAME);
            String fromTag = fromHeader != null ? fromHeader.getTag() : "";

            ResponseEvent resp200 = future.get(10, TimeUnit.SECONDS);
            sipMessageSender.sendAck(resp200, fromTag);

            ToHeader toHeader = (ToHeader) resp200.getResponse().getHeader(ToHeader.NAME);
            String toTag = toHeader != null ? toHeader.getTag() : "";

            String streamUrl = zlmClient.buildStreamUrl(streamId);
            PlaybackSession session = new PlaybackSession(
                    deviceId, callId, fromTag, toTag, 1L,
                    req.getStartTime(), req.getEndTime(),
                    streamId, rtpPort, streamUrl);
            sessionStore.put(session);
            log.info("回放会话建立成功 deviceId={} streamId={} rtpPort={}", deviceId, streamId, rtpPort);
            return streamUrl;

        } catch (TimeoutException e) {
            log.warn("回放 INVITE 超时 10s deviceId={}", deviceId);
            zlmClient.closeRtpServer(streamId);
            throw new IllegalStateException("INVITE timeout");
        } catch (IllegalArgumentException | IllegalStateException | ResourceNotFoundException e) {
            zlmClient.closeRtpServer(streamId);
            throw e;
        } catch (Exception e) {
            log.error("startPlayback 异常 deviceId={}: {}", deviceId, e.getMessage(), e);
            zlmClient.closeRtpServer(streamId);
            throw new IllegalStateException("启动回放失败: " + e.getMessage());
        } finally {
            pendingInvites.remove(callId);
        }
    }

    /**
     * 停止历史回放（幂等）。
     */
    public void stopPlayback(String type, String deviceId) {
        PlaybackSession session = sessionStore.get(deviceId).orElse(null);
        if (session == null) {
            log.debug("stopPlayback: 无活跃回放会话 deviceId={}", deviceId);
            return;
        }
        try {
            InterconnectConfig config = resolveConfig(type, deviceId);
            sipMessageSender.sendPlaybackBye(session, config);
        } catch (Exception e) {
            log.warn("发送回放 BYE 失败 deviceId={}: {}", deviceId, e.getMessage());
        }
        zlmClient.closeRtpServer(session.streamId());
        sessionStore.remove(deviceId);
        log.info("回放会话已停止 deviceId={}", deviceId);
    }

    /**
     * 控制回放进度（pause/play/scale/seek）。
     */
    public void controlPlayback(String type, String deviceId, PlaybackControlRequest req) {
        PlaybackSession session = sessionStore.get(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("无活跃回放会话: " + deviceId));
        InterconnectConfig config = resolveConfig(type, deviceId);
        String body = buildMansrtspBody(req, session);
        sipMessageSender.sendInfo(config, resolveTargetDeviceId(type, deviceId), session, body);
    }

    /**
     * 处理 INVITE 200 OK 回调（由 GbtSipListener 调用）。
     */
    public void onInviteOk(ResponseEvent event) {
        CallIdHeader callIdHeader = (CallIdHeader) event.getResponse().getHeader(CallIdHeader.NAME);
        if (callIdHeader == null) return;
        String callId = callIdHeader.getCallId();
        CompletableFuture<ResponseEvent> future = pendingInvites.get(callId);
        if (future != null) {
            future.complete(event);
        }
    }

    /**
     * 处理对端主动 BYE（由 GbtSipListener 调用）。
     */
    public void onRemoteBye(String callId) {
        sessionStore.getByCallId(callId).ifPresent(session -> {
            zlmClient.closeRtpServer(session.streamId());
            sessionStore.remove(session.deviceId());
            log.info("收到对端 BYE，回放会话已清理 deviceId={} callId={}", session.deviceId(), callId);
        });
    }

    // ===== 私有辅助方法 =====

    private InterconnectConfig resolveConfig(String type, String deviceId) {
        if ("local".equals(type)) {
            Ivs1900InterconnectConfig ivs1900 = ivs1900ConfigRepo.findAll().stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("IVS1900 互联配置不存在"));
            return toInterconnectConfig(ivs1900);
        }
        var device = remoteDeviceRepo.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("设备不存在: " + deviceId));
        return interconnectConfigRepo.findById(device.getInterconnectConfigId())
                .orElseThrow(() -> new ResourceNotFoundException("互联配置不存在"));
    }

    private String resolveTargetDeviceId(String type, String deviceId) {
        if ("local".equals(type)) {
            return cameraMappingRepo.findByGbDeviceId(deviceId)
                    .map(m -> m.getIvsCameraId())
                    .orElseThrow(() -> new ResourceNotFoundException("本端相机不存在: " + deviceId));
        }
        return deviceId;
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

    private long parseTimestamp(String timeStr) {
        try {
            return LocalDateTime.parse(timeStr, TIME_FMT).toEpochSecond(ZoneOffset.of("+08:00"));
        } catch (Exception e) {
            log.warn("解析时间失败: {}", timeStr);
            return 0;
        }
    }

    private String buildPlaybackSdp(String localSipId, String mediaIp, int rtpPort,
                                     String ssrc, String deviceId, long startTs, long endTs) {
        return "v=0\r\n"
                + "o=" + deviceId + " 0 0 IN IP4 " + mediaIp + "\r\n"
                + "s=Playback\r\n"
                + "u=" + deviceId + ":0\r\n"
                + "c=IN IP4 " + mediaIp + "\r\n"
        + "t=" + startTs + " " + endTs + "\r\n"
                + "m=video " + rtpPort + " RTP/AVP 96 98\r\n"
                + "a=recvonly\r\n"
                + "a=rtpmap:96 PS/90000\r\n"
                + "a=rtpmap:98 H264/90000\r\n"
                + "y=" + ssrc + "\r\n"
                + "f=\r\n";
    }

    private String buildMansrtspBody(PlaybackControlRequest req, PlaybackSession session) {
        String action = req.getAction();
        long cseqN = session.cseq() + 1;
        if ("pause".equals(action)) {
            return "PAUSE MANSRTSP/1.0\r\nCSeq: " + cseqN + "\r\n";
        } else if ("scale".equals(action)) {
            double scale = req.getScale() != null ? req.getScale() : 1.0;
            return "PLAY MANSRTSP/1.0\r\nCSeq: " + cseqN + "\r\nScale: " + scale + "\r\n";
        } else if ("seek".equals(action)) {
            long seekTs   = parseTimestamp(req.getSeekTime());
            long startTs  = parseTimestamp(session.startTime());
            long nptOffset = Math.max(0, seekTs - startTs);
            return "PLAY MANSRTSP/1.0\r\nCSeq: " + cseqN + "\r\nRange: npt=" + nptOffset + "-\r\n";
        } else {
            // default: play (resume)
            return "PLAY MANSRTSP/1.0\r\nCSeq: " + cseqN + "\r\nScale: 1.0\r\n";
        }
    }
}
