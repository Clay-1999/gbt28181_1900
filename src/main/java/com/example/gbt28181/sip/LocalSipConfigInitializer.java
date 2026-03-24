package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.LocalSipConfig;
import com.example.gbt28181.domain.entity.SipStackStatus;
import com.example.gbt28181.domain.repository.LocalSipConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocalSipConfigInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final LocalSipConfigRepository localSipConfigRepository;
    private final SipStackManager sipStackManager;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        LocalSipConfig config;
        if (localSipConfigRepository.count() == 0) {
            config = new LocalSipConfig();
            config.setStatus(SipStackStatus.ERROR);
            config.setErrorMsg("本端 SIP 参数未配置");
            localSipConfigRepository.save(config);
            log.info("首次启动：已写入默认本端 SIP 配置，请通过 Web UI 完善参数");
        } else {
            config = localSipConfigRepository.findById(1L).orElseThrow();
            log.info("本端 SIP 配置已存在");
        }

        if (config.getDeviceId() != null) {
            log.info("本端 SIP 参数已配置，启动 SIP Stack ...");
            sipStackManager.reload(config);
        } else {
            sipStackManager.setStatusError("本端 SIP 参数未配置");
        }
    }
}
