package com.example.gbt28181.sip;

/**
 * 外域设备视频流 SIP 会话信息。
 */
public record StreamSession(
        String deviceId,
        String callId,
        String fromTag,
        String toTag,
        long cseq,
        String streamId,
        int zlmPort
) {}
