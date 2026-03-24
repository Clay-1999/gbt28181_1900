package com.example.gbt28181.api.controller;

import com.example.gbt28181.sip.SipAudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class AudioController {

    private final SipAudioService sipAudioService;

    @PostMapping("/{type}/{deviceId}/audio/broadcast/start")
    public ResponseEntity<Map<String, Object>> startBroadcast(@PathVariable String type,
                                                               @PathVariable String deviceId) {
        return startAudio(type, deviceId, "broadcast");
    }

    @PostMapping("/{type}/{deviceId}/audio/broadcast/stop")
    public ResponseEntity<Map<String, Object>> stopBroadcast(@PathVariable String type,
                                                              @PathVariable String deviceId) {
        sipAudioService.stopAudio(type, deviceId);
        return ResponseEntity.ok(Map.of("stopped", true));
    }

    @PostMapping("/{type}/{deviceId}/audio/talk/start")
    public ResponseEntity<Map<String, Object>> startTalk(@PathVariable String type,
                                                          @PathVariable String deviceId) {
        return startAudio(type, deviceId, "talk");
    }

    @PostMapping("/{type}/{deviceId}/audio/talk/stop")
    public ResponseEntity<Map<String, Object>> stopTalk(@PathVariable String type,
                                                         @PathVariable String deviceId) {
        sipAudioService.stopAudio(type, deviceId);
        return ResponseEntity.ok(Map.of("stopped", true));
    }

    private ResponseEntity<Map<String, Object>> startAudio(String type, String deviceId, String mode) {
        try {
            sipAudioService.startAudio(type, deviceId, mode);
            return ResponseEntity.ok(Map.of("started", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
