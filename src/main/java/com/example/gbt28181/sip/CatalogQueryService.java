package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.sip.xml.CatalogQueryXml;
import com.example.gbt28181.sip.xml.GbXmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.*;
import java.util.UUID;

@Component
@Slf4j
public class CatalogQueryService {

    private final SipMessageSender sipMessageSender;

    public CatalogQueryService(SipMessageSender sipMessageSender) {
        this.sipMessageSender = sipMessageSender;
    }

    private SipProvider sipProvider;

    public void setSipProvider(SipProvider sipProvider) {
        this.sipProvider = sipProvider;
    }

    public void queryCatalog(InterconnectConfig config) {
        queryCatalog(config, null, 0);
    }

    public void queryCatalog(InterconnectConfig config, String contactIp, int contactPort) {
        if (sipProvider == null) {
            log.warn("SIP Provider 未就绪，跳过 Catalog Query configId={}", config.getId());
            return;
        }
        try {
            int sn = (int) (System.currentTimeMillis() / 1000 % 100000);
            String targetIp = (contactIp != null && !contactIp.isEmpty()) ? contactIp : config.getRemoteIp();
            int targetPort = contactPort > 0 ? contactPort : config.getRemotePort();

            // 构造临时 InterconnectConfig 指向实际目标地址
            InterconnectConfig target = new InterconnectConfig();
            target.setRemoteSipId(config.getRemoteSipId());
            target.setRemoteIp(targetIp);
            target.setRemotePort(targetPort);
            target.setRemoteDomain(config.getRemoteDomain());

            String body = GbXmlMapper.toXml(new CatalogQueryXml(sn, config.getRemoteSipId()));

            sipMessageSender.sendMessage(target, config.getRemoteSipId(), UUID.randomUUID().toString(), body);
            log.info("发送 Catalog Query → {}:{} configId={}", targetIp, targetPort, config.getId());

        } catch (Exception e) {
            log.warn("发送 Catalog Query 失败 configId={}: {}", config.getId(), e.getMessage());
        }
    }
}
