package com.example.gbt28181.api.controller;

import com.example.gbt28181.api.dto.LocalSipConfigRequest;
import com.example.gbt28181.api.dto.LocalSipConfigResponse;
import com.example.gbt28181.domain.entity.LocalSipConfig;
import com.example.gbt28181.domain.entity.SipStackStatus;
import com.example.gbt28181.service.LocalSipConfigService;
import com.example.gbt28181.sip.SipStackManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/local-config")
@RequiredArgsConstructor
public class LocalSipConfigController {

    private final SipStackManager sipStackManager;
    private final LocalSipConfigService localSipConfigService;

    @GetMapping
    public ResponseEntity<LocalSipConfigResponse> getConfig() {
        LocalSipConfig config = localSipConfigService.getConfig();
        return ResponseEntity.ok(toResponse(config));
    }

    @PutMapping
    public ResponseEntity<?> updateConfig(@Valid @RequestBody LocalSipConfigRequest request) {
        if (sipStackManager.getStatus() == SipStackStatus.RELOADING) {
            return ResponseEntity.status(409).body(Map.of("message", "SIP Stack 正在重载，请稍后再试"));
        }
        localSipConfigService.updateConfig(request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "status", sipStackManager.getStatus(),
                "errorMsg", sipStackManager.getErrorMsg() != null ? sipStackManager.getErrorMsg() : ""
        ));
    }

    private LocalSipConfigResponse toResponse(LocalSipConfig config) {
        LocalSipConfigResponse r = new LocalSipConfigResponse();
        r.setDeviceId(config.getDeviceId());
        r.setDomain(config.getDomain());
        r.setSipIp(config.getSipIp());
        r.setSipPort(config.getSipPort());
        r.setTransport(config.getTransport());
        r.setPassword("***");
        r.setExpires(config.getExpires());
        r.setStatus(sipStackManager.getStatus());
        r.setErrorMsg(sipStackManager.getErrorMsg());
        return r;
    }
}
