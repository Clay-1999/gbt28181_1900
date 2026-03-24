package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.InterconnectConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报警订阅客户端。
 *
 * GB/T 28181 A.2.4.6：报警订阅通过 SIP MESSAGE 发送 Query/CmdType=Alarm 的 XML，
 * 与目录订阅（CmdType=Catalog）机制相同，不使用 RFC 3265 SIP SUBSCRIBE 方法。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlarmSubscribeService {

    private final SipMessageSender sipMessageSender;

    /** 已订阅的 configKey 集合（内存维护，重启清零） */
    private final Set<String> subscribedKeys = ConcurrentHashMap.newKeySet();

    /**
     * 向指定目标发起报警订阅（SIP MESSAGE，CmdType=Alarm）。
     * 发送成功后记录订阅状态。
     */
    public boolean subscribeAlarm(String key, String targetSipId, String targetIp,
                                  int targetPort, String targetDomain) {
        try {
            int sn = (int) (System.currentTimeMillis() / 1000 % 100000);
            String body = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\n"
                    + "<Query>\n"
                    + "<CmdType>Alarm</CmdType>\n"
                    + "<SN>" + sn + "</SN>\n"
                    + "<DeviceID>" + targetSipId + "</DeviceID>\n"
                    + "</Query>";

            InterconnectConfig target = new InterconnectConfig();
            target.setRemoteSipId(targetSipId);
            target.setRemoteIp(targetIp);
            target.setRemotePort(targetPort);
            target.setRemoteDomain(targetDomain != null ? targetDomain : targetIp);

            sipMessageSender.sendMessage(target, targetSipId, UUID.randomUUID().toString(), body);
            subscribedKeys.add(key);
            log.info("发送报警订阅 MESSAGE → {}:{} key={}", targetIp, targetPort, key);
            return true;
        } catch (Exception e) {
            log.warn("发送报警订阅 MESSAGE 失败 key={}: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 取消报警订阅（清除本地状态，GB/T 28181 无标准取消命令，订阅自然过期）。
     */
    public void unsubscribeAlarm(String key) {
        subscribedKeys.remove(key);
        log.info("取消报警订阅 key={}", key);
    }

    public boolean isSubscribed(String key) {
        return subscribedKeys.contains(key);
    }
}

