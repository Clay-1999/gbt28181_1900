package com.example.gbt28181.api.controller;

import com.example.gbt28181.api.dto.Ivs1900InterconnectRequest;
import com.example.gbt28181.api.dto.Ivs1900InterconnectResponse;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.service.Ivs1900InterconnectConfigService;
import com.example.gbt28181.sip.AlarmSubscribeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ivs1900/interconnect")
@RequiredArgsConstructor
public class Ivs1900InterconnectController {

    private final Ivs1900InterconnectConfigService service;
    private final AlarmSubscribeService alarmSubscribeService;

    @GetMapping
    public ResponseEntity<List<Ivs1900InterconnectResponse>> list() {
        return ResponseEntity.ok(service.findAll().stream().map(this::toResponse).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Ivs1900InterconnectResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(service.findById(id)));
    }

    @PostMapping
    public ResponseEntity<Ivs1900InterconnectResponse> create(
            @Valid @RequestBody Ivs1900InterconnectRequest request) {
        return ResponseEntity.status(201).body(toResponse(service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ivs1900InterconnectResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody Ivs1900InterconnectRequest request) {
        return ResponseEntity.ok(toResponse(service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/alarm-subscribe")
    public ResponseEntity<Map<String, Object>> alarmSubscribe(@PathVariable Long id) {
        Ivs1900InterconnectConfig cfg = service.findById(id);
        String key = "ivs1900-" + id;
        if (alarmSubscribeService.isSubscribed(key)) {
            alarmSubscribeService.unsubscribeAlarm(key);
            return ResponseEntity.ok(Map.of("subscribed", false, "configId", id));
        }
        boolean ok = alarmSubscribeService.subscribeAlarm(key, cfg.getSipId(), cfg.getIp(), cfg.getPort(), cfg.getDomain());
        return ResponseEntity.ok(Map.of("subscribed", ok, "configId", id));
    }

    @GetMapping("/{id}/alarm-subscribe")
    public ResponseEntity<Map<String, Object>> alarmSubscribeStatus(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("subscribed", alarmSubscribeService.isSubscribed("ivs1900-" + id)));
    }

    private Ivs1900InterconnectResponse toResponse(Ivs1900InterconnectConfig entity) {
        Ivs1900InterconnectResponse r = new Ivs1900InterconnectResponse();
        r.setId(entity.getId());
        r.setSipId(entity.getSipId());
        r.setIp(entity.getIp());
        r.setPort(entity.getPort());
        r.setDomain(entity.getDomain());
        r.setPassword("***");
        r.setUpLinkStatus(entity.getUpLinkStatus());
        r.setCreatedAt(entity.getCreatedAt());
        return r;
    }
}
