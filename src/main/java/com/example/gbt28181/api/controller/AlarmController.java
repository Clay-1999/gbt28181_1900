package com.example.gbt28181.api.controller;

import com.example.gbt28181.api.dto.AlarmEventResponse;
import com.example.gbt28181.domain.entity.AlarmEvent;
import com.example.gbt28181.domain.repository.AlarmEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmEventRepository alarmEventRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        int effectiveSize = Math.min(size, 100);
        PageRequest pageRequest = PageRequest.of(page, effectiveSize);

        Page<AlarmEvent> result;
        if (deviceId != null && !deviceId.isBlank()) {
            result = alarmEventRepository.findByDeviceIdOrderByReceivedAtDesc(deviceId, pageRequest);
        } else {
            result = alarmEventRepository.findAllByOrderByReceivedAtDesc(pageRequest);
        }

        return ResponseEntity.ok(Map.of(
                "total", result.getTotalElements(),
                "items", result.getContent().stream().map(AlarmEventResponse::from).toList()
        ));
    }
}
