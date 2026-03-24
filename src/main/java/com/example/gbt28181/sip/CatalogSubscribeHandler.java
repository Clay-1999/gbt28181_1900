package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.Ivs1900CameraMapping;
import com.example.gbt28181.domain.repository.Ivs1900CameraMappingRepository;
import com.example.gbt28181.domain.repository.LocalSipConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.*;
import javax.sip.address.AddressFactory;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogSubscribeHandler {

    private final Ivs1900CameraMappingRepository mappingRepository;
    private final LocalSipConfigRepository localSipConfigRepository;
    private SipProvider sipProvider;
    private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private MessageFactory messageFactory;

    public void setSipProvider(SipProvider sipProvider) throws PeerUnavailableException {
        this.sipProvider = sipProvider;
        SipFactory factory = SipFactory.getInstance();
        this.addressFactory = factory.createAddressFactory();
        this.headerFactory = factory.createHeaderFactory();
        this.messageFactory = factory.createMessageFactory();
    }

    public void handleSubscribe(RequestEvent event) {
        Request request = event.getRequest();
        ServerTransaction tx = event.getServerTransaction();
        try {
            if (tx == null) {
                tx = sipProvider.getNewServerTransaction(request);
            }

            EventHeader eventHeader = (EventHeader) request.getHeader(EventHeader.NAME);
            if (eventHeader == null || !"Catalog".equalsIgnoreCase(eventHeader.getEventType())) {
                sendResponse(tx, request, Response.BAD_EVENT);
                log.warn("收到未知 Event 类型的 SUBSCRIBE: {}", eventHeader != null ? eventHeader.getEventType() : "null");
                return;
            }

            // 回复 200 OK
            sendResponse(tx, request, Response.OK);

            // 发送 NOTIFY
            sendCatalogNotify(request);

        } catch (Exception e) {
            log.error("处理 SUBSCRIBE 失败", e);
        }
    }

    private void sendCatalogNotify(Request subscribeRequest) {
        try {
            List<Ivs1900CameraMapping> devices = mappingRepository.findAll();
            String localDeviceId = localSipConfigRepository.findAll().stream()
                    .findFirst().map(c -> c.getDeviceId()).orElse("00000000000000000000");
            String xmlBody = buildCatalogXml(localDeviceId, devices);

            // 从 SUBSCRIBE 请求中提取对端地址
            ViaHeader via = (ViaHeader) subscribeRequest.getHeader(ViaHeader.NAME);
            FromHeader from = (FromHeader) subscribeRequest.getHeader(FromHeader.NAME);
            String peerHost = via.getHost();
            int peerPort = via.getPort() > 0 ? via.getPort() : 5060;
            if (via.getReceived() != null && !via.getReceived().isEmpty()) peerHost = via.getReceived();
            if (via.getRPort() > 0) peerPort = via.getRPort();
            String peerSipId = extractUser(from.getAddress().getURI().toString());

            sendNotifyTo(peerSipId, peerHost, peerPort, xmlBody);
            log.info("发送 Catalog NOTIFY → {}:{} 设备数={}", peerHost, peerPort, devices.size());
        } catch (Exception e) {
            log.error("发送 Catalog NOTIFY 失败", e);
        }
    }

    /**
     * 主动向指定上级平台推送目录 NOTIFY（注册成功后调用，不依赖 SUBSCRIBE 上下文）。
     */
    public void pushCatalogToAddress(String targetSipId, String targetIp, int targetPort) {
        try {
            List<Ivs1900CameraMapping> devices = mappingRepository.findAll();
            String localDeviceId = localSipConfigRepository.findAll().stream()
                    .findFirst().map(c -> c.getDeviceId()).orElse("00000000000000000000");
            String xmlBody = buildCatalogXml(localDeviceId, devices);
            sendNotifyTo(targetSipId, targetIp, targetPort, xmlBody);
            log.info("主动推送 Catalog NOTIFY → {}:{} 设备数={}", targetIp, targetPort, devices.size());
        } catch (Exception e) {
            log.warn("主动推送 Catalog NOTIFY 失败: {}", e.getMessage());
        }
    }

    private void sendNotifyTo(String peerSipId, String peerIp, int peerPort, String xmlBody) throws Exception {
        String localSipId = LocalSipConfigHolder.getDeviceId();
        String localIp = LocalSipConfigHolder.getSipIp();
        int localPort = LocalSipConfigHolder.getSipPort();

        javax.sip.address.SipURI requestUri = addressFactory.createSipURI(peerSipId, peerIp + ":" + peerPort);
        javax.sip.address.Address fromAddr = addressFactory.createAddress(
                addressFactory.createSipURI(localSipId, localIp + ":" + localPort));
        javax.sip.address.Address toAddr = addressFactory.createAddress(
                addressFactory.createSipURI(peerSipId, peerIp + ":" + peerPort));
        javax.sip.address.Address contactAddr = addressFactory.createAddress(
                addressFactory.createSipURI(localSipId, localIp + ":" + localPort));

        FromHeader fromHeader = headerFactory.createFromHeader(fromAddr, UUID.randomUUID().toString().substring(0, 8));
        ToHeader toHeader = headerFactory.createToHeader(toAddr, null);
        CallIdHeader callIdHeader = headerFactory.createCallIdHeader(UUID.randomUUID().toString());
        CSeqHeader cseq = headerFactory.createCSeqHeader(1L, Request.NOTIFY);
        MaxForwardsHeader maxFwds = headerFactory.createMaxForwardsHeader(70);
        ContactHeader contact = headerFactory.createContactHeader(contactAddr);
        ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort,
                sipProvider.getListeningPoints()[0].getTransport(), null);

        EventHeader eventHeader = headerFactory.createEventHeader("Catalog");
        SubscriptionStateHeader subState = headerFactory.createSubscriptionStateHeader("active");
        subState.setExpires(3600);
        ContentTypeHeader contentType = headerFactory.createContentTypeHeader("Application", "MANSCDP+xml");
        byte[] bodyBytes = xmlBody.getBytes(StandardCharsets.UTF_8);

        Request notify = messageFactory.createRequest(requestUri, Request.NOTIFY,
                callIdHeader, cseq, fromHeader, toHeader, List.of(viaHeader), maxFwds);
        notify.addHeader(contact);
        notify.addHeader(eventHeader);
        notify.addHeader(subState);
        notify.addHeader(contentType);
        notify.setContent(bodyBytes, contentType);

        sipProvider.getNewClientTransaction(notify).sendRequest();
    }

    private String buildCatalogXml(String localDeviceId, List<Ivs1900CameraMapping> devices) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"GB2312\"?>\n");
        xml.append("<Notify>\n");
        xml.append("  <CmdType>Catalog</CmdType>\n");
        xml.append("  <SN>").append(System.currentTimeMillis() % 100000).append("</SN>\n");
        xml.append("  <DeviceID>").append(localDeviceId).append("</DeviceID>\n");
        xml.append("  <SumNum>").append(devices.size()).append("</SumNum>\n");
        xml.append("  <DeviceList Num=\"").append(devices.size()).append("\">\n");
        for (Ivs1900CameraMapping device : devices) {
            xml.append("    <Item>\n");
            xml.append("      <DeviceID>").append(device.getGbDeviceId()).append("</DeviceID>\n");
            xml.append("      <Name>").append(escapeXml(device.getName())).append("</Name>\n");
            xml.append("      <Manufacturer>IVS1900</Manufacturer>\n");
            xml.append("      <Status>").append("ONLINE".equals(device.getStatus()) ? "ON" : "OFF").append("</Status>\n");
            xml.append("      <ParentID>").append(localDeviceId).append("</ParentID>\n");
            xml.append("    </Item>\n");
        }
        xml.append("  </DeviceList>\n");
        xml.append("</Notify>");
        return xml.toString();
    }

    private void sendResponse(ServerTransaction tx, Request request, int statusCode) {
        try {
            Response response = SipFactory.getInstance().createMessageFactory().createResponse(statusCode, request);
            tx.sendResponse(response);
        } catch (Exception e) {
            log.error("发送 SIP 响应失败: statusCode={}", statusCode, e);
        }
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String extractUser(String uri) {
        if (uri.startsWith("sip:")) uri = uri.substring(4);
        int at = uri.indexOf('@');
        return at > 0 ? uri.substring(0, at) : uri;
    }
}
