package com.example.gbt28181.sip;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外域设备视频流会话内存存储。key = deviceId。
 */
@Component
public class StreamSessionStore {

    private final ConcurrentHashMap<String, StreamSession> sessions = new ConcurrentHashMap<>();

    public void put(StreamSession session) {
        sessions.put(session.deviceId(), session);
    }

    public Optional<StreamSession> get(String deviceId) {
        return Optional.ofNullable(sessions.get(deviceId));
    }

    public Optional<StreamSession> findByCallId(String callId) {
        return sessions.values().stream()
                .filter(s -> s.callId().equals(callId))
                .findFirst();
    }

    public void remove(String deviceId) {
        sessions.remove(deviceId);
    }

    public boolean has(String deviceId) {
        return sessions.containsKey(deviceId);
    }

    public Collection<StreamSession> all() {
        return sessions.values();
    }
}
