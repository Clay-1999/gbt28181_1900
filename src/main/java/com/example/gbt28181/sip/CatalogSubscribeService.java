package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.domain.entity.Ivs1900InterconnectConfig;
import com.example.gbt28181.domain.entity.LinkStatus;
import com.example.gbt28181.domain.repository.InterconnectConfigRepository;
import com.example.gbt28181.domain.repository.Ivs1900InterconnectConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理本平台向下级（IVS1900 或外域互联平台）发起的 Catalog SUBSCRIBE 订阅生命周期。
 * <p>
 * 流程：
 * 1. 下级注册成功后，调用 subscribe() 发起订阅
 * 2. 每 30s 检查一次订阅到期时间，剩余 < 60s 时主动续订
 * 3. 下级离线（注册清除）时调用 {@link #cancelSubscription(Long)} 移除订阅记录
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogSubscribeService {

    /** 订阅有效期（秒） */
    private static final int SUBSCRIBE_EXPIRES = 3600;
    /** 提前续订阈值（秒）：剩余时间少于此值时发起续订 */
    private static final int RENEW_BEFORE_SECS = 60;

    private final SipMessageSender sipMessageSender;
    private final Ivs1900InterconnectConfigRepository ivs1900Repository;
    private final InterconnectConfigRepository interconnectConfigRepository;

    /** key = configId（IVS1900 或 InterconnectConfig） */
    private final Map<Long, SubscriptionEntry> subscriptions = new ConcurrentHashMap<>();

    private record SubscriptionEntry(
            Long configId,
            InterconnectConfig target,
            Instant subscribedAt,
            int expires,
            boolean isIvs1900
    ) {
        Instant expiresAt() {
            return subscribedAt.plusSeconds(expires);
        }

        boolean shouldRenew() {
            return Instant.now().isAfter(expiresAt().minusSeconds(RENEW_BEFORE_SECS));
        }
    }

    /**
     * IVS1900 注册成功后调用，发起 Catalog SUBSCRIBE。
     */
    public void subscribe(Ivs1900InterconnectConfig ivs1900, String contactIp, int contactPort) {
        InterconnectConfig target = toInterconnectConfig(ivs1900, contactIp, contactPort);
        doSubscribe(ivs1900.getId(), target, true, contactIp, contactPort);
    }

    /**
     * 外域互联平台（下联）注册成功后调用，发起 Catalog SUBSCRIBE。
     */
    public void subscribe(InterconnectConfig config, String contactIp, int contactPort) {
        InterconnectConfig target = new InterconnectConfig();
        target.setId(config.getId());
        target.setRemoteSipId(config.getRemoteSipId());
        target.setRemoteIp(contactIp != null && !contactIp.isEmpty() ? contactIp : config.getRemoteIp());
        target.setRemotePort(contactPort > 0 ? contactPort : config.getRemotePort());
        target.setRemoteDomain(config.getRemoteDomain());
        target.setPassword(config.getPassword());
        target.setEnabled(true);
        doSubscribe(config.getId(), target, false, contactIp, contactPort);
    }

    private void doSubscribe(Long configId, InterconnectConfig target, boolean isIvs1900,
                              String contactIp, int contactPort) {
        String callId = sipMessageSender.sendSubscribe(target, SUBSCRIBE_EXPIRES);
        if (callId != null) {
            subscriptions.put(configId,
                    new SubscriptionEntry(configId, target, Instant.now(), SUBSCRIBE_EXPIRES, isIvs1900));
            log.info("Catalog SUBSCRIBE 已发起 configId={} isIvs1900={} target={}:{}",
                    configId, isIvs1900, contactIp, contactPort);
        }
    }

    /**
     * 下级离线时调用，移除订阅记录（不发 expires=0，因为对端已无法响应）。
     */
    public void cancelSubscription(Long configId) {
        if (subscriptions.remove(configId) != null) {
            log.info("Catalog 订阅记录已清除 configId={}", configId);
        }
    }

    /**
     * 每 30s 检查所有活跃订阅，到期前 60s 发起续订。
     */
    @Scheduled(fixedDelay = 30_000)
    public void renewSubscriptions() {
        for (SubscriptionEntry entry : subscriptions.values()) {
            if (!entry.shouldRenew()) continue;

            // 检查下级仍在线
            boolean online;
            if (entry.isIvs1900()) {
                online = ivs1900Repository.findById(entry.configId())
                        .map(c -> c.getUpLinkStatus() == LinkStatus.ONLINE)
                        .orElse(false);
            } else {
                online = interconnectConfigRepository.findById(entry.configId())
                        .map(c -> c.getDownLinkStatus() == LinkStatus.ONLINE)
                        .orElse(false);
            }

            if (!online) {
                subscriptions.remove(entry.configId());
                log.info("configId={} 已离线，取消 Catalog 订阅续订", entry.configId());
                continue;
            }

            String callId = sipMessageSender.sendSubscribe(entry.target(), SUBSCRIBE_EXPIRES);
            if (callId != null) {
                subscriptions.put(entry.configId(),
                        new SubscriptionEntry(entry.configId(), entry.target(), Instant.now(),
                                SUBSCRIBE_EXPIRES, entry.isIvs1900()));
                log.info("Catalog SUBSCRIBE 续订成功 configId={}", entry.configId());
            } else {
                log.warn("Catalog SUBSCRIBE 续订失败 configId={}", entry.configId());
            }
        }
    }

    private InterconnectConfig toInterconnectConfig(Ivs1900InterconnectConfig ivs1900,
                                                     String ip, int port) {
        InterconnectConfig ic = new InterconnectConfig();
        ic.setId(ivs1900.getId());
        ic.setRemoteSipId(ivs1900.getSipId());
        ic.setRemoteIp(ip != null && !ip.isEmpty() ? ip : ivs1900.getIp());
        ic.setRemotePort(port > 0 ? port : ivs1900.getPort());
        ic.setRemoteDomain(ivs1900.getDomain());
        ic.setEnabled(true);
        return ic;
    }
}
