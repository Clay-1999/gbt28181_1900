package com.example.gbt28181.sip;

/**
 * 历史回放 SIP 会话信息。
 */
public record PlaybackSession(
        String deviceId,
        String callId,
        String fromTag,
        String toTag,
        long cseq,
        String startTime,
        String endTime,
        String streamId,
        int zlmPort,
        String streamUrl
) {}
