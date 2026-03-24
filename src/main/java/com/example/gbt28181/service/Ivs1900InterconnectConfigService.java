package com.example.gbt28181.service;

import com.example.gbt28181.api.dto.Ivs1900InterconnectRequest;
import com.example.gbt28181.api.exception.ResourceNotFoundException;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.entity.LinkStatus;
import com.example.gbt28181.domain.repository.Ivs1900InterconnectConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class Ivs1900InterconnectConfigService {

    private final Ivs1900InterconnectConfigRepository repository;

    public List<Ivs1900InterconnectConfig> findAll() {
        return repository.findAll();
    }

    public Ivs1900InterconnectConfig findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IVS1900 互联配置不存在：id=" + id));
    }

    public Ivs1900InterconnectConfig create(Ivs1900InterconnectRequest request) {
        Ivs1900InterconnectConfig entity = new Ivs1900InterconnectConfig();
        entity.setUpLinkStatus(LinkStatus.OFFLINE);
        mapRequest(entity, request);
        return repository.save(entity);
    }

    public Ivs1900InterconnectConfig update(Long id, Ivs1900InterconnectRequest request) {
        Ivs1900InterconnectConfig entity = findById(id);
        mapRequest(entity, request);
        return repository.save(entity);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("IVS1900 互联配置不存在：id=" + id);
        }
        repository.deleteById(id);
    }

    private void mapRequest(Ivs1900InterconnectConfig entity, Ivs1900InterconnectRequest request) {
        entity.setSipId(request.getSipId());
        entity.setIp(request.getIp());
        entity.setPort(request.getPort());
        entity.setDomain(request.getDomain());
        entity.setPassword(request.getPassword());
    }
}
