package com.example.gbt28181.sip;

/**
 * 音频广播/对讲 SIP 会话信息。
 */
public record AudioSession(
        String deviceId,
        String callId,
        String fromTag,
        String toTag,
        long cseq,
        String mode,         // "broadcast" 或 "talk"
        String streamId,
        int zlmPort,
        String deviceRtpIp,
        int deviceRtpPort
) {}
