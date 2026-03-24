package com.example.gbt28181.service;

import com.example.gbt28181.api.dto.InterconnectConfigRequest;
import com.example.gbt28181.api.exception.ResourceNotFoundException;
import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.LinkStatus;
import com.example.gbt28181.domain.entity.SipStackStatus;
import com.example.gbt28181.domain.repository.InterconnectConfigRepository;
import com.example.gbt28181.sip.SipRegistrationClient;
import com.example.gbt28181.sip.SipRegistrationServer;
import com.example.gbt28181.sip.SipStackManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterconnectConfigService {

    private final InterconnectConfigRepository repository;
    private final SipRegistrationClient sipRegistrationClient;
    private final SipRegistrationServer sipRegistrationServer;
    private final SipStackManager sipStackManager;

    public List<InterconnectConfig> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public InterconnectConfig findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("互联配置不存在：id=" + id));
    }

    public InterconnectConfig create(InterconnectConfigRequest request) {
        InterconnectConfig entity = new InterconnectConfig();
        entity.setUpLinkStatus(LinkStatus.OFFLINE);
        entity.setDownLinkStatus(LinkStatus.OFFLINE);
        mapRequest(entity, request);
        InterconnectConfig saved = repository.save(entity);

        if (Boolean.TRUE.equals(saved.getEnabled()) && Boolean.TRUE.equals(saved.getUpLinkEnabled())
                && sipStackManager.getStatus() == SipStackStatus.RUNNING) {
            sipRegistrationClient.startRegistration(saved);
        }
        return saved;
    }

    public InterconnectConfig update(Long id, InterconnectConfigRequest request) {
        InterconnectConfig entity = findById(id);
        boolean wasEnabled = Boolean.TRUE.equals(entity.getEnabled());
        boolean wasUpLinkEnabled = Boolean.TRUE.equals(entity.getUpLinkEnabled());
        // 检测连接参数是否变更（需要重启注册）
        boolean connectionChanged = wasEnabled && wasUpLinkEnabled
                && (!entity.getRemoteIp().equals(request.getRemoteIp())
                || !entity.getRemotePort().equals(request.getRemotePort())
                || !entity.getRemoteDomain().equals(request.getRemoteDomain())
                || !entity.getRemoteSipId().equals(request.getRemoteSipId()));

        mapRequest(entity, request);
        boolean isEnabled = Boolean.TRUE.equals(entity.getEnabled());
        boolean isUpLinkEnabled = Boolean.TRUE.equals(entity.getUpLinkEnabled());
        InterconnectConfig saved = repository.save(entity);

        if (sipStackManager.getStatus() == SipStackStatus.RUNNING) {
            boolean wasActive = wasEnabled && wasUpLinkEnabled;
            boolean isActive = isEnabled && isUpLinkEnabled;
            if (!wasActive && isActive) {
                sipRegistrationClient.startRegistration(saved);
            } else if (wasActive && !isActive) {
                sipRegistrationClient.stopRegistration(id);
            } else if (isActive && connectionChanged) {
                // 连接参数变更：重启注册任务
                sipRegistrationClient.stopRegistration(id);
                sipRegistrationClient.startRegistration(saved);
            }
        }

        if (wasUpLinkEnabled && !isUpLinkEnabled) {
            sipRegistrationServer.deregisterByConfigId(id);
        }
        return saved;
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("互联配置不存在：id=" + id);
        }
        sipRegistrationClient.stopRegistration(id);
        repository.deleteById(id);
    }

    private void mapRequest(InterconnectConfig entity, InterconnectConfigRequest request) {
        entity.setName(request.getName());
        entity.setRemoteSipId(request.getRemoteSipId());
        entity.setRemoteIp(request.getRemoteIp());
        entity.setRemotePort(request.getRemotePort());
        entity.setRemoteDomain(request.getRemoteDomain());
        entity.setPassword(request.getPassword());
        entity.setEnabled(request.getEnabled());
        entity.setUpLinkEnabled(request.getUpLinkEnabled() != null ? request.getUpLinkEnabled() : false);
    }
}
