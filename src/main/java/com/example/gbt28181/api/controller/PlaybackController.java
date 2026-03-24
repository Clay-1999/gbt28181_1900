package com.example.gbt28181.api.controller;

import com.example.gbt28181.api.dto.PlaybackControlRequest;
import com.example.gbt28181.api.dto.PlaybackRequest;
import com.example.gbt28181.api.exception.ResourceNotFoundException;
import com.example.gbt28181.service.SipPlaybackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class PlaybackController {

    private final SipPlaybackService sipPlaybackService;

    @PostMapping("/{type}/{deviceId}/playback/start")
    public ResponseEntity<Map<String, Object>> startPlayback(@PathVariable String type,
                                                              @PathVariable String deviceId,
                                                              @RequestBody PlaybackRequest req) {
        try {
            String streamUrl = sipPlaybackService.startPlayback(type, deviceId, req);
            return ResponseEntity.ok(Map.of(
                    "streamUrl", streamUrl,
                    "startTime", req.getStartTime(),
                    "endTime",   req.getEndTime()
            ));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(504).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{type}/{deviceId}/playback/stop")
    public ResponseEntity<Map<String, Object>> stopPlayback(@PathVariable String type,
                                                             @PathVariable String deviceId) {
        sipPlaybackService.stopPlayback(type, deviceId);
        return ResponseEntity.ok(Map.of("stopped", true));
    }

    @PostMapping("/{type}/{deviceId}/playback/control")
    public ResponseEntity<Map<String, Object>> controlPlayback(@PathVariable String type,
                                                                @PathVariable String deviceId,
                                                                @RequestBody PlaybackControlRequest req) {
        try {
            sipPlaybackService.controlPlayback(type, deviceId, req);
            return ResponseEntity.ok(Map.of("sent", true));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
}
