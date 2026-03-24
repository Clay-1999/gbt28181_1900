package com.example.gbt28181.sip;

import com.example.gbt28181.config.ZlmConfig;
import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.repository.InterconnectConfigRepository;
import com.example.gbt28181.domain.repository.Ivs1900CameraMappingRepository;
import com.example.gbt28181.domain.repository.Ivs1900InterconnectConfigRepository;
import com.example.gbt28181.domain.repository.RemoteDeviceRepository;
import com.example.gbt28181.ivs1900.ZLMediaKitClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.ResponseEvent;
import javax.sip.SipProvider;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 音频广播/对讲 SIP 会话业务服务。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SipAudioService {

    private final SipMessageSender sipMessageSender;
    private final AudioSessionStore audioSessionStore;
    private final ZLMediaKitClient zlmClient;
    private final ZlmConfig zlmConfig;
    private final Ivs1900InterconnectConfigRepository ivs1900Repo;
    private final Ivs1900CameraMappingRepository cameraMappingRepo;
    private final InterconnectConfigRepository interconnectConfigRepo;
    private final RemoteDeviceRepository remoteDeviceRepo;

    /** 等待 INVITE 200 OK 的 Future，key = callId */
    private final ConcurrentHashMap<String, CompletableFuture<ResponseEvent>> pendingInvites =
            new ConcurrentHashMap<>();

    private SipProvider sipProvider;

    public void setSipProvider(SipProvider sipProvider) {
        this.sipProvider = sipProvider;
    }

    /**
     * 发起音频广播或对讲会话。
     *
     * @param type     "local" 或 "remote"
     * @param deviceId 设备 ID
     * @param mode     "broadcast" 或 "talk"
     */
    public void startAudio(String type, String deviceId, String mode) {
        InterconnectConfig config = resolveConfig(type, deviceId);

        // 若已有活跃会话，先停止
        if (audioSessionStore.has(deviceId)) {
            log.info("设备 {} 已有音频会话，先停止旧会话", deviceId);
            stopAudio(type, deviceId);
        }

        String prefix = "broadcast".equals(mode) ? "audio_broadcast_" : "audio_talk_";
        String streamId = prefix + deviceId.replaceAll("[^a-zA-Z0-9]", "_");

        int zlmPort = zlmClient.openRtpServer(streamId);
        if (zlmPort <= 0) {
            throw new IllegalStateException("ZLMediaKit 创建 RTP 端口失败，请检查 ZLM 服务是否运行");
        }

        String callId = UUID.randomUUID().toString();
        CompletableFuture<ResponseEvent> future = new CompletableFuture<>();
        pendingInvites.put(callId, future);

        try {
            String ssrc = String.format("0%09d", (long) (Math.random() * 1_000_000_000L));
            String zlmIp = zlmConfig.getRtpIp();
            String sdp = buildAudioSdp(zlmIp, zlmPort, ssrc, deviceId, mode);

            var ct = sipMessageSender.sendInvite(config, deviceId, callId, sdp, ssrc);
            if (ct == null) {
                throw new IllegalStateException("发送 INVITE 失败");
            }

            FromHeader fromHeader = (FromHeader) ct.getRequest().getHeader(FromHeader.NAME);
            String fromTag = fromHeader != null ? fromHeader.getTag() : "";

            ResponseEvent resp200 = future.get(10, TimeUnit.SECONDS);

            // 解析 200 OK SDP，提取设备 RTP 地址和端口
            String sdp200 = extractSdpBody(resp200);
            String deviceRtpIp = parseConnectionIp(sdp200);
            int deviceRtpPort = parseAudioPort(sdp200);

            sipMessageSender.sendAck(resp200, fromTag);

            ToHeader toHeader = (ToHeader) resp200.getResponse().getHeader(ToHeader.NAME);
            String toTag = toHeader != null ? toHeader.getTag() : "";

            AudioSession session = new AudioSession(deviceId, callId, fromTag, toTag, 1L,
                    mode, streamId, zlmPort, deviceRtpIp, deviceRtpPort);
            audioSessionStore.put(session);
            log.info("音频{}会话建立成功 deviceId={} streamId={} zlmPort={}", mode, deviceId, streamId, zlmPort);

        } catch (TimeoutException e) {
            log.warn("音频 INVITE 超时 10s deviceId={}", deviceId);
            zlmClient.closeRtpServer(streamId);
            throw new IllegalStateException("INVITE timeout");
        } catch (IllegalStateException e) {
            zlmClient.closeRtpServer(streamId);
            throw e;
        } catch (Exception e) {
            log.error("startAudio 异常 deviceId={}: {}", deviceId, e.getMessage(), e);
            zlmClient.closeRtpServer(streamId);
            throw new IllegalStateException("启动音频流失败: " + e.getMessage());
        } finally {
            pendingInvites.remove(callId);
        }
    }

    /**
     * 停止音频会话（幂等）。
     */
    public void stopAudio(String type, String deviceId) {
        AudioSession session = audioSessionStore.get(deviceId).orElse(null);
        if (session == null) {
            return;
        }
        try {
            InterconnectConfig config = resolveConfigQuietly(type, deviceId);
            if (config != null) {
                sendAudioBye(session, config);
            }
        } catch (Exception e) {
            log.warn("发送音频 BYE 失败 deviceId={}: {}", deviceId, e.getMessage());
        }
        zlmClient.closeRtpServer(session.streamId());
        audioSessionStore.remove(deviceId);
        log.info("音频会话已停止 deviceId={}", deviceId);
    }

    /**
     * 处理 INVITE 200 OK（由 GbtSipListener 调用）。
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

    public boolean hasCallId(String callId) {
        return pendingInvites.containsKey(callId);
    }

    /**
     * 处理对端主动发来的 BYE（由 GbtSipListener 调用）。
     */
    public void onRemoteBye(String callId) {
        audioSessionStore.findByCallId(callId).ifPresent(session -> {
            zlmClient.closeRtpServer(session.streamId());
            audioSessionStore.remove(session.deviceId());
            log.info("收到音频对端 BYE，会话已清理 deviceId={} callId={}", session.deviceId(), callId);
        });
    }

    // ===== 私有方法 =====

    /** 解析互联配置，不存在抛 IllegalArgumentException。 */
    private InterconnectConfig resolveConfig(String type, String deviceId) {
        if ("local".equals(type)) {
            cameraMappingRepo.findByGbDeviceId(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException("本端设备不存在: " + deviceId));
            Ivs1900InterconnectConfig ivs = ivs1900Repo.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("IVS1900 互联配置不存在"));
            return toInterconnectConfig(ivs);
        } else {
            var device = remoteDeviceRepo.findByDeviceId(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException("外域设备不存在: " + deviceId));
            return interconnectConfigRepo.findById(device.getInterconnectConfigId())
                    .orElseThrow(() -> new IllegalArgumentException("互联配置不存在: " + device.getInterconnectConfigId()));
        }
    }

    /** 解析互联配置，失败返回 null（用于 stop 路径）。 */
    private InterconnectConfig resolveConfigQuietly(String type, String deviceId) {
        try {
            return resolveConfig(type, deviceId);
        } catch (Exception e) {
            return null;
        }
    }

    private InterconnectConfig toInterconnectConfig(Ivs1900InterconnectConfig ivs) {
        InterconnectConfig cfg = new InterconnectConfig();
        cfg.setRemoteSipId(ivs.getSipId());
        cfg.setRemoteIp(ivs.getIp());
        cfg.setRemotePort(ivs.getPort());
        cfg.setRemoteDomain(ivs.getDomain());
        return cfg;
    }

    private void sendAudioBye(AudioSession session, InterconnectConfig config) {
        // 复用 StreamSession BYE 逻辑，适配字段
        StreamSession adapted = new StreamSession(session.deviceId(), session.callId(),
                session.fromTag(), session.toTag(), session.cseq(), session.streamId(), session.zlmPort());
        sipMessageSender.sendBye(adapted, config);
    }

    private String buildAudioSdp(String mediaIp, int rtpPort, String ssrc, String deviceId, String mode) {
        String sessionName = "broadcast".equals(mode) ? "Broadcast" : "Talk";
        String direction = "broadcast".equals(mode) ? "sendonly" : "sendrecv";
        return "v=0\r\n"
                + "o=" + deviceId + " 0 0 IN IP4 " + mediaIp + "\r\n"
                + "s=" + sessionName + "\r\n"
                + "c=IN IP4 " + mediaIp + "\r\n"
                + "t=0 0\r\n"
                + "m=audio " + rtpPort + " RTP/AVP 0 8\r\n"
                + "a=rtpmap:0 PCMU/8000\r\n"
                + "a=rtpmap:8 PCMA/8000\r\n"
                + "a=" + direction + "\r\n"
                + "y=" + ssrc + "\r\n";
    }

    private String extractSdpBody(ResponseEvent event) {
        try {
            byte[] raw = event.getResponse().getRawContent();
            return raw != null ? new String(raw, java.nio.charset.StandardCharsets.UTF_8) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String parseConnectionIp(String sdp) {
        for (String line : sdp.split("\r\n|\n")) {
            if (line.startsWith("c=IN IP4 ")) {
                return line.substring("c=IN IP4 ".length()).trim();
            }
        }
        return "";
    }

    private int parseAudioPort(String sdp) {
        for (String line : sdp.split("\r\n|\n")) {
            if (line.startsWith("m=audio ")) {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    try { return Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return 0;
    }
}
