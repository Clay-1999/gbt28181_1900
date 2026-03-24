package com.example.gbt28181.api.controller;

import com.example.gbt28181.api.dto.InterconnectConfigRequest;
import com.example.gbt28181.api.dto.InterconnectConfigResponse;
import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.service.InterconnectConfigService;
import com.example.gbt28181.sip.AlarmSubscribeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interconnects")
@RequiredArgsConstructor
public class InterconnectConfigController {

    private final InterconnectConfigService interconnectConfigService;
    private final AlarmSubscribeService alarmSubscribeService;

    @GetMapping
    public ResponseEntity<List<InterconnectConfigResponse>> list() {
        List<InterconnectConfigResponse> result = interconnectConfigService.findAll()
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterconnectConfigResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(interconnectConfigService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<InterconnectConfigResponse> create(@Valid @RequestBody InterconnectConfigRequest request) {
        InterconnectConfig created = interconnectConfigService.create(request);
        return ResponseEntity.status(201).body(toResponse(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InterconnectConfigResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody InterconnectConfigRequest request) {
        return ResponseEntity.ok(toResponse(interconnectConfigService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        interconnectConfigService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/alarm-subscribe")
    public ResponseEntity<Map<String, Object>> alarmSubscribe(@PathVariable Long id) {
        InterconnectConfig cfg = interconnectConfigService.findById(id);
        String key = "interconnect-" + id;
        if (alarmSubscribeService.isSubscribed(key)) {
            alarmSubscribeService.unsubscribeAlarm(key);
            return ResponseEntity.ok(Map.of("subscribed", false, "configId", id));
        }
        boolean ok = alarmSubscribeService.subscribeAlarm(key, cfg.getRemoteSipId(), cfg.getRemoteIp(),
                cfg.getRemotePort(), cfg.getRemoteDomain());
        return ResponseEntity.ok(Map.of("subscribed", ok, "configId", id));
    }

    @GetMapping("/{id}/alarm-subscribe")
    public ResponseEntity<Map<String, Object>> alarmSubscribeStatus(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("subscribed", alarmSubscribeService.isSubscribed("interconnect-" + id)));
    }

    private InterconnectConfigResponse toResponse(InterconnectConfig entity) {
        InterconnectConfigResponse r = new InterconnectConfigResponse();
        r.setId(entity.getId());
        r.setName(entity.getName());
        r.setRemoteSipId(entity.getRemoteSipId());
        r.setRemoteIp(entity.getRemoteIp());
        r.setRemotePort(entity.getRemotePort());
        r.setRemoteDomain(entity.getRemoteDomain());
        r.setPassword("***");
        r.setEnabled(entity.getEnabled());
        r.setUpLinkEnabled(entity.getUpLinkEnabled());
        r.setUpLinkStatus(entity.getUpLinkStatus());
        r.setDownLinkStatus(entity.getDownLinkStatus());
        r.setLastHeartbeatAt(entity.getLastHeartbeatAt());
        r.setCreatedAt(entity.getCreatedAt());
        return r;
    }
}
