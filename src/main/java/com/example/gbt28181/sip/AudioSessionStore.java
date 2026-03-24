package com.example.gbt28181.sip;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频会话内存存储。key = deviceId。
 */
@Component
public class AudioSessionStore {

    private final ConcurrentHashMap<String, AudioSession> sessions = new ConcurrentHashMap<>();

    public void put(AudioSession session) {
        sessions.put(session.deviceId(), session);
    }

    public Optional<AudioSession> get(String deviceId) {
        return Optional.ofNullable(sessions.get(deviceId));
    }

    public boolean has(String deviceId) {
        return sessions.containsKey(deviceId);
    }

    public void remove(String deviceId) {
        sessions.remove(deviceId);
    }

    public Optional<AudioSession> findByCallId(String callId) {
        return sessions.values().stream()
                .filter(s -> s.callId().equals(callId))
                .findFirst();
    }
}
