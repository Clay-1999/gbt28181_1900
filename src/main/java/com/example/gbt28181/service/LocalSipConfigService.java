package com.example.gbt28181.service;

import com.example.gbt28181.api.dto.LocalSipConfigRequest;
import com.example.gbt28181.domain.entity.LocalSipConfig;
import com.example.gbt28181.domain.repository.LocalSipConfigRepository;
import com.example.gbt28181.sip.SipStackManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalSipConfigService {

    private final LocalSipConfigRepository localSipConfigRepository;
    private final SipStackManager sipStackManager;

    public LocalSipConfig getConfig() {
        return localSipConfigRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("本端 SIP 配置不存在"));
    }

    public void updateConfig(LocalSipConfigRequest request) {
        LocalSipConfig config = localSipConfigRepository.findById(1L)
                .orElseGet(LocalSipConfig::new);
        config.setDeviceId(request.getDeviceId());
        config.setDomain(request.getDomain());
        config.setSipIp(request.getSipIp());
        config.setSipPort(request.getSipPort());
        config.setTransport(request.getTransport());
        config.setPassword(request.getPassword());
        config.setExpires(request.getExpires());
        localSipConfigRepository.save(config);
        reloadAsync(config);
    }

    @Async
    public void reloadAsync(LocalSipConfig config) {
        sipStackManager.reload(config);
    }
}
