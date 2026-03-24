package com.example.gbt28181.service;

import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.repository.Ivs1900CameraMappingRepository;
import com.example.gbt28181.domain.repository.Ivs1900InterconnectConfigRepository;
import com.example.gbt28181.sip.SipInviteService;
import com.example.gbt28181.sip.SipRegistrationServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalDeviceStreamService {

    private final Ivs1900CameraMappingRepository cameraMappingRepository;
    private final Ivs1900InterconnectConfigRepository ivs1900Repository;
    private final SipRegistrationServer sipRegistrationServer;
    private final SipInviteService sipInviteService;

    /**
     * 向本端 IVS1900 相机发起视频流请求。
     *
     * @param gbDeviceId 平台分配的国标设备 ID
     * @return HTTP-FLV 播放地址
     */
    public String startStream(String gbDeviceId) {
        Ivs1900CameraMapping camera = cameraMappingRepository.findByGbDeviceId(gbDeviceId)
                .orElseThrow(() -> new IllegalArgumentException("本端设备不存在: " + gbDeviceId));

        Ivs1900InterconnectConfig ivs1900 = ivs1900Repository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("IVS1900 互联配置不存在"));

        // 从注册记录取实际 IP/端口（优先），回退到互联配置中的静态地址
        String targetIp = ivs1900.getIp();
        int targetPort = ivs1900.getPort();

        var contact = sipRegistrationServer.getRegisteredContact(ivs1900.getSipId());
        if (contact.isPresent()) {
            String contactStr = contact.get();
            // contact 格式: sip:sipId@ip:port
            try {
                String hostPart = contactStr.contains("@") ? contactStr.substring(contactStr.indexOf('@') + 1) : "";
                if (hostPart.contains(":")) {
                    targetIp = hostPart.substring(0, hostPart.lastIndexOf(':'));
                    targetPort = Integer.parseInt(hostPart.substring(hostPart.lastIndexOf(':') + 1));
                } else if (!hostPart.isEmpty()) {
                    targetIp = hostPart;
                }
            } catch (Exception e) {
                log.warn("解析注册 contact 地址失败，使用配置地址: {}", contactStr);
            }
        } else {
            log.warn("IVS1900 {} 未注册，使用配置中的静态地址 {}:{}", ivs1900.getSipId(), targetIp, targetPort);
        }

        InterconnectConfig target = buildTarget(ivs1900, targetIp, targetPort);
        String ivsCameraId = camera.getIvsCameraId();
        log.info("本端设备拉流 gbDeviceId={} ivsCameraId={} target={}:{}", gbDeviceId, ivsCameraId, targetIp, targetPort);
        return sipInviteService.startStream(target, ivsCameraId, "local_");
    }

    /**
     * 停止本端 IVS1900 相机视频流。
     */
    public void stopStream(String gbDeviceId) {
        Ivs1900CameraMapping camera = cameraMappingRepository.findByGbDeviceId(gbDeviceId)
                .orElse(null);
        if (camera == null) {
            log.debug("stopStream: 本端设备不存在 gbDeviceId={}", gbDeviceId);
            return;
        }

        Ivs1900InterconnectConfig ivs1900 = ivs1900Repository.findAll().stream().findFirst().orElse(null);
        if (ivs1900 == null) {
            log.debug("stopStream: IVS1900 互联配置不存在");
            return;
        }

        String targetIp = ivs1900.getIp();
        int targetPort = ivs1900.getPort();
        var contact = sipRegistrationServer.getRegisteredContact(ivs1900.getSipId());
        if (contact.isPresent()) {
            String contactStr = contact.get();
            try {
                String hostPart = contactStr.contains("@") ? contactStr.substring(contactStr.indexOf('@') + 1) : "";
                if (hostPart.contains(":")) {
                    targetIp = hostPart.substring(0, hostPart.lastIndexOf(':'));
                    targetPort = Integer.parseInt(hostPart.substring(hostPart.lastIndexOf(':') + 1));
                } else if (!hostPart.isEmpty()) {
                    targetIp = hostPart;
                }
            } catch (Exception e) {
                log.warn("解析注册 contact 地址失败: {}", contactStr);
            }
        }

        InterconnectConfig target = buildTarget(ivs1900, targetIp, targetPort);
        sipInviteService.stopStream(camera.getIvsCameraId(), target);
    }

    private InterconnectConfig buildTarget(Ivs1900InterconnectConfig ivs1900, String ip, int port) {
        InterconnectConfig ic = new InterconnectConfig();
        ic.setId(ivs1900.getId());
        ic.setRemoteSipId(ivs1900.getSipId());
        ic.setRemoteIp(ip);
        ic.setRemotePort(port);
        ic.setRemoteDomain(ivs1900.getDomain());
        ic.setPassword(ivs1900.getPassword());
        ic.setEnabled(true);
        return ic;
    }
}
