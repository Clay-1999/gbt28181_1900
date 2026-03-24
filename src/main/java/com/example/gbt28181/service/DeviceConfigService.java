package com.example.gbt28181.service;

import com.example.gbt28181.api.exception.ResourceNotFoundException;
import com.example.gbt28181.domain.repository.Ivs1900CameraMappingRepository;
import com.example.gbt28181.sip.xml.CameraConfigType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceConfigService {

    private final Ivs1900CameraMappingRepository mappingRepository;
    private final Ivs1900SipConfigService ivs1900SipConfigService;

    public Map<String, Object> getLocalConfig(String gbDeviceId, CameraConfigType configType) {
        mappingRepository.findByGbDeviceId(gbDeviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + gbDeviceId));
        return ivs1900SipConfigService.queryConfig(gbDeviceId, configType);
    }

    public boolean setLocalConfig(String gbDeviceId, CameraConfigType configType, Map<String, Object> patch) {
        mappingRepository.findByGbDeviceId(gbDeviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + gbDeviceId));
        Boolean result = ivs1900SipConfigService.setConfig(gbDeviceId, configType, patch);
        return Boolean.TRUE.equals(result);
    }
}
