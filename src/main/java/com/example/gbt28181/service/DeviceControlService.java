package com.example.gbt28181.service;

import com.example.gbt28181.api.dto.SnapshotRequest;
import com.example.gbt28181.api.dto.UpgradeRequest;
import com.example.gbt28181.api.exception.ResourceNotFoundException;
import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.entity.RemoteDevice;
import com.example.gbt28181.domain.repository.*;
import com.example.gbt28181.sip.SipMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceControlService {

    private final Ivs1900CameraMappingRepository cameraRepo;
    private final Ivs1900InterconnectConfigRepository ivs1900Repo;
    private final RemoteDeviceRepository remoteDeviceRepo;
    private final InterconnectConfigRepository interconnectConfigRepo;
    private final SipMessageSender sipMessageSender;

    /** 发送 GuardCmd（SetGuard / ResetGuard）。 */
    public void sendGuardCmd(String type, String deviceId, String cmd) {
        String targetId = resolveTargetDeviceId(type, deviceId);
        int sn = newSn();
        String xml = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n<Control>\n"
                + "<CmdType>GuardCmd</CmdType>\n<SN>" + sn + "</SN>\n"
                + "<DeviceID>" + targetId + "</DeviceID>\n"
                + "<GuardCmd>" + cmd + "</GuardCmd>\n</Control>";
        sendMessage(type, deviceId, targetId, xml);
        log.info("GuardCmd {} 已发送: type={}, deviceId={}", cmd, type, deviceId);
    }

    /** 发送 RecordCmd（Record / StopRecord）。 */
    public void sendRecordCmd(String type, String deviceId, String cmd) {
        String targetId = resolveTargetDeviceId(type, deviceId);
        int sn = newSn();
        String xml = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n<Control>\n"
                + "<CmdType>DeviceControl</CmdType>\n<SN>" + sn + "</SN>\n"
                + "<DeviceID>" + targetId + "</DeviceID>\n"
                + "<RecordCmd>" + cmd + "</RecordCmd>\n</Control>";
        sendMessage(type, deviceId, targetId, xml);
        log.info("RecordCmd {} 已发送: type={}, deviceId={}", cmd, type, deviceId);
    }

    /** 发送 TeleBoot（远程重启）。 */
    public void sendReboot(String type, String deviceId) {
        String targetId = resolveTargetDeviceId(type, deviceId);
        int sn = newSn();
        String xml = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n<Control>\n"
                + "<CmdType>DeviceControl</CmdType>\n<SN>" + sn + "</SN>\n"
                + "<DeviceID>" + targetId + "</DeviceID>\n"
                + "<TeleBoot>Boot</TeleBoot>\n</Control>";
        sendMessage(type, deviceId, targetId, xml);
        log.info("TeleBoot 已发送: type={}, deviceId={}", type, deviceId);
    }

    /** 发送 SnapShotConfig（图像抓拍）。 */
    public void sendSnapshot(String type, String deviceId, SnapshotRequest req) {
        String targetId = resolveTargetDeviceId(type, deviceId);
        int sn = newSn();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"GB2312\"?>\n<Control>\n")
                .append("<CmdType>SnapShotConfig</CmdType>\n<SN>").append(sn).append("</SN>\n")
                .append("<DeviceID>").append(targetId).append("</DeviceID>\n")
                .append("<SnapNum>").append(req.getSnapNum()).append("</SnapNum>\n")
                .append("<Interval>").append(req.getInterval()).append("</Interval>\n")
                .append("<UploadAddr>").append(req.getUploadAddr()).append("</UploadAddr>\n");
        if (req.getResolution() != null && !req.getResolution().isEmpty()) {
            xml.append("<Resolution>").append(req.getResolution()).append("</Resolution>\n");
        }
        xml.append("</Control>");
        sendMessage(type, deviceId, targetId, xml.toString());
        log.info("SnapShotConfig 已发送: type={}, deviceId={}, snapNum={}", type, deviceId, req.getSnapNum());
    }

    /** 发送 DeviceUpgrade（软件升级）。 */
    public void sendUpgrade(String type, String deviceId, UpgradeRequest req) {
        String targetId = resolveTargetDeviceId(type, deviceId);
        int sn = newSn();
        String xml = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n<Control>\n"
                + "<CmdType>DeviceUpgrade</CmdType>\n<SN>" + sn + "</SN>\n"
                + "<DeviceID>" + targetId + "</DeviceID>\n"
                + "<FirmwareID>" + req.getFirmwareId() + "</FirmwareID>\n"
                + "<FirmwareAddr>" + req.getFirmwareAddr() + "</FirmwareAddr>\n</Control>";
        sendMessage(type, deviceId, targetId, xml);
        log.info("DeviceUpgrade 已发送: type={}, deviceId={}, firmwareId={}", type, deviceId, req.getFirmwareId());
    }

    private String resolveTargetDeviceId(String type, String deviceId) {
        if ("local".equals(type)) {
            return cameraRepo.findByGbDeviceId(deviceId)
                    .map(Ivs1900CameraMapping::getIvsCameraId)
                    .orElseThrow(() -> new ResourceNotFoundException("Camera not found: " + deviceId));
        }
        return deviceId;
    }

    private void sendMessage(String type, String deviceId, String targetId, String xmlBody) {
        if ("local".equals(type)) {
            Ivs1900InterconnectConfig ivs1900 = ivs1900Repo.findAll().stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("IVS1900 互联配置不存在"));
            InterconnectConfig target = toInterconnectConfig(ivs1900);
            sipMessageSender.sendMessage(target, targetId, UUID.randomUUID().toString(), xmlBody);
        } else {
            RemoteDevice device = remoteDeviceRepo.findByDeviceId(deviceId)
                    .orElseThrow(() -> new ResourceNotFoundException("RemoteDevice not found: " + deviceId));
            InterconnectConfig config = interconnectConfigRepo.findById(device.getInterconnectConfigId())
                    .orElseThrow(() -> new ResourceNotFoundException("InterconnectConfig not found"));
            sipMessageSender.sendMessage(config, deviceId, UUID.randomUUID().toString(), xmlBody);
        }
    }

    private InterconnectConfig toInterconnectConfig(Ivs1900InterconnectConfig ivs1900) {
        InterconnectConfig ic = new InterconnectConfig();
        ic.setId(ivs1900.getId());
        ic.setRemoteSipId(ivs1900.getSipId());
        ic.setRemoteIp(ivs1900.getIp());
        ic.setRemotePort(ivs1900.getPort());
        ic.setRemoteDomain(ivs1900.getDomain());
        ic.setPassword(ivs1900.getPassword());
        ic.setEnabled(true);
        return ic;
    }

    private int newSn() {
        return (int) (System.currentTimeMillis() % 100000);
    }
}
