package com.example.gbt28181.sip;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 历史回放会话仓储（in-memory），key = deviceId。
 */
@Component
public class PlaybackSessionStore {

    private final ConcurrentHashMap<String, PlaybackSession> byDeviceId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> callIdToDeviceId = new ConcurrentHashMap<>();

    public void put(PlaybackSession session) {
        byDeviceId.put(session.deviceId(), session);
        callIdToDeviceId.put(session.callId(), session.deviceId());
    }

    public Optional<PlaybackSession> get(String deviceId) {
        return Optional.ofNullable(byDeviceId.get(deviceId));
    }

    public Optional<PlaybackSession> getByCallId(String callId) {
        String deviceId = callIdToDeviceId.get(callId);
        return deviceId != null ? Optional.ofNullable(byDeviceId.get(deviceId)) : Optional.empty();
    }

    public void remove(String deviceId) {
        PlaybackSession session = byDeviceId.remove(deviceId);
        if (session != null) callIdToDeviceId.remove(session.callId());
    }

    public boolean has(String deviceId) {
        return byDeviceId.containsKey(deviceId);
    }
}
