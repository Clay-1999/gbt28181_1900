package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.InterconnectConfig;
import com.example.gbt28181.sip.xml.CatalogQueryXml;
import com.example.gbt28181.sip.xml.GbXmlMapper;
import com.example.gbt28181.util.LogUtils;import lombok.extern.slf4j.Slf4j;
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
/**
 * SIP MESSAGE 发送工具 Bean。
 * 集中封装所有 SIP MESSAGE 的发送逻辑，供各 Handler 复用。
 */
@Component
@Slf4j
public class SipMessageSender {

    private SipProvider sipProvider;
    private AddressFactory addressFactory;
    private HeaderFactory headerFactory;
    private MessageFactory messageFactory;

    /** 由 SipStackManager 在 Stack 重建后调用。 */
    public void setSipProvider(SipProvider sipProvider) throws PeerUnavailableException {
        this.sipProvider = sipProvider;
        SipFactory factory = SipFactory.getInstance();
        this.addressFactory = factory.createAddressFactory();
        this.headerFactory = factory.createHeaderFactory();
        this.messageFactory = factory.createMessageFactory();
    }

    /**
     * 回复入站请求 200 OK。
     */
    public void sendOk(RequestEvent event) {
        try {
            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) {
                tx = sipProvider.getNewServerTransaction(event.getRequest());
            }
            Response rsp = SipFactory.getInstance().createMessageFactory()
                    .createResponse(Response.OK, event.getRequest());
            tx.sendResponse(rsp);
        } catch (Exception e) {
            log.warn("发送 200 OK 失败: {}", e.getMessage());
        }
    }

    /**
     * 向入站请求的发送方回一条新 MESSAGE（异步响应场景）。
     * 目标地址从原始请求的 Via/From Header 中提取。
     */
    public void replyMessage(RequestEvent originalEvent, String xmlBody) {
        try {
            Request origReq = originalEvent.getRequest();
            ViaHeader via = (ViaHeader) origReq.getHeader(ViaHeader.NAME);
            FromHeader from = (FromHeader) origReq.getHeader(FromHeader.NAME);

            String peerHost = via.getHost();
            int peerPort = via.getPort() > 0 ? via.getPort() : 5060;
            if (via.getReceived() != null && !via.getReceived().isEmpty()) peerHost = via.getReceived();
            if (via.getRPort() > 0) peerPort = via.getRPort();

            String peerSipId = extractUser(from.getAddress().getURI().toString());
            String localSipId = LocalSipConfigHolder.getDeviceId();
            String localIp = LocalSipConfigHolder.getSipIp();
            int localPort = LocalSipConfigHolder.getSipPort();

            javax.sip.address.SipURI requestUri = addressFactory.createSipURI(peerSipId, peerHost + ":" + peerPort);
            javax.sip.address.Address fromAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(localSipId, localIp + ":" + localPort));
            javax.sip.address.Address toAddr = from.getAddress();
            javax.sip.address.Address contactAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(localSipId, localIp + ":" + localPort));

            FromHeader newFrom = headerFactory.createFromHeader(fromAddr, UUID.randomUUID().toString().substring(0, 8));
            ToHeader newTo = headerFactory.createToHeader(toAddr, null);
            CallIdHeader callId = headerFactory.createCallIdHeader(UUID.randomUUID().toString());
            CSeqHeader cseq = headerFactory.createCSeqHeader(1L, Request.MESSAGE);
            MaxForwardsHeader maxFwds = headerFactory.createMaxForwardsHeader(70);
            ContactHeader contact = headerFactory.createContactHeader(contactAddr);
            ViaHeader newVia = headerFactory.createViaHeader(localIp, localPort,
                    sipProvider.getListeningPoints()[0].getTransport(), null);
            ContentTypeHeader contentType = headerFactory.createContentTypeHeader("application", "MANSCDP+xml");
            byte[] bodyBytes = xmlBody.getBytes(StandardCharsets.UTF_8);

            Request msg = messageFactory.createRequest(requestUri, Request.MESSAGE,
                    callId, cseq, newFrom, newTo, List.of(newVia), maxFwds);
            msg.addHeader(contact);
            msg.addHeader(contentType);
            msg.setContent(bodyBytes, contentType);

            sipProvider.getNewClientTransaction(msg).sendRequest();
            SipTraceLogger.logOutbound(msg);
            log.debug("<<< SIP MESSAGE (reply) {}", LogUtils.escape(msg));
        } catch (Exception e) {
            log.error("发送响应 MESSAGE 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 向指定对端发送 SUBSCRIBE (Event: Catalog)。
     *
     * @param target  目标互联配置
     * @param expires 订阅有效期（秒），0 表示取消订阅
     * @return 发送成功时的 Call-ID，失败返回 null
     */
    public String sendSubscribe(InterconnectConfig target, int expires) {
        if (sipProvider == null) {
            log.warn("SIP Provider 未就绪，跳过 SUBSCRIBE");
            return null;
        }
        try {
            String localSipId = LocalSipConfigHolder.getDeviceId();
            String localIp    = LocalSipConfigHolder.getSipIp();
            int    localPort  = LocalSipConfigHolder.getSipPort();

            javax.sip.address.SipURI requestUri = addressFactory.createSipURI(
                    target.getRemoteSipId(), target.getRemoteIp() + ":" + target.getRemotePort());
            javax.sip.address.Address fromAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(localSipId, localIp + ":" + localPort));
            javax.sip.address.Address toAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(target.getRemoteSipId(),
                            target.getRemoteIp() + ":" + target.getRemotePort()));
            javax.sip.address.Address contactAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(localSipId, localIp + ":" + localPort));

            String callId = UUID.randomUUID().toString();
            FromHeader    fromHeader    = headerFactory.createFromHeader(fromAddr, UUID.randomUUID().toString().substring(0, 8));
            ToHeader      toHeader      = headerFactory.createToHeader(toAddr, null);
            CallIdHeader  callIdHeader  = headerFactory.createCallIdHeader(callId);
            CSeqHeader    cseqHeader    = headerFactory.createCSeqHeader(1L, Request.SUBSCRIBE);
            MaxForwardsHeader maxFwds   = headerFactory.createMaxForwardsHeader(70);
            ContactHeader contactHeader = headerFactory.createContactHeader(contactAddr);
            ViaHeader     viaHeader     = headerFactory.createViaHeader(localIp, localPort,
                    sipProvider.getListeningPoints()[0].getTransport(), null);
            ExpiresHeader expiresHeader = headerFactory.createExpiresHeader(expires);
            EventHeader   eventHeader   = headerFactory.createEventHeader("Catalog");

            Request subscribe = messageFactory.createRequest(requestUri, Request.SUBSCRIBE,
                    callIdHeader, cseqHeader, fromHeader, toHeader, List.of(viaHeader), maxFwds);
            subscribe.addHeader(contactHeader);
            subscribe.addHeader(expiresHeader);
            subscribe.addHeader(eventHeader);

            // GB/T 28181 A.2.4.3：SUBSCRIBE 须携带 Catalog Query XML body，DeviceID 为下级平台互联编码
            int sn = (int)(System.currentTimeMillis() / 1000 % 100000);
            String xmlBody = GbXmlMapper.toXml(new com.example.gbt28181.sip.xml.CatalogQueryXml(sn, target.getRemoteSipId()));
            byte[] bodyBytes = xmlBody.getBytes(StandardCharsets.UTF_8);
            ContentTypeHeader contentType = headerFactory.createContentTypeHeader("Application", "MANSCDP+xml");
            subscribe.setContent(bodyBytes, contentType);

            sipProvider.getNewClientTransaction(subscribe).sendRequest();
            SipTraceLogger.logOutbound(subscribe);
            log.info("发送 Catalog SUBSCRIBE → {}:{} expires={} callId={}",
                    target.getRemoteIp(), target.getRemotePort(), expires, callId);
            return callId;
        } catch (Exception e) {
            log.error("发送 SUBSCRIBE 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 主动向指定对端发送 MESSAGE（主动发起场景）。
     *
     * @param target   目标互联配置（提供 remoteSipId/remoteIp/remotePort/remoteDomain）
     * @param deviceId 目标设备 ID（仅用于日志）
     * @param callId   Call-ID
     * @param xmlBody  消息体
     */
    public void sendMessage(InterconnectConfig target, String deviceId, String callId, String xmlBody) {
        try {
            String localSipId = LocalSipConfigHolder.getDeviceId();
            String localIp = LocalSipConfigHolder.getSipIp();
            int localPort = LocalSipConfigHolder.getSipPort();

            javax.sip.address.SipURI requestUri = addressFactory.createSipURI(
                    target.getRemoteSipId(), target.getRemoteIp() + ":" + target.getRemotePort());
            javax.sip.address.Address fromAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(localSipId, localIp + ":" + localPort));
            javax.sip.address.Address toAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(target.getRemoteSipId(),
                            target.getRemoteDomain() != null ? target.getRemoteDomain() : target.getRemoteIp()));
            javax.sip.address.Address contactAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(localSipId, localIp + ":" + localPort));

            FromHeader newFrom = headerFactory.createFromHeader(fromAddr, UUID.randomUUID().toString().substring(0, 8));
            ToHeader newTo = headerFactory.createToHeader(toAddr, null);
            CallIdHeader callIdHeader = headerFactory.createCallIdHeader(callId);
            CSeqHeader cseq = headerFactory.createCSeqHeader(1L, Request.MESSAGE);
            MaxForwardsHeader maxFwds = headerFactory.createMaxForwardsHeader(70);
            ContactHeader contact = headerFactory.createContactHeader(contactAddr);
            ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort,
                    sipProvider.getListeningPoints()[0].getTransport(), null);
            ContentTypeHeader contentType = headerFactory.createContentTypeHeader("application", "MANSCDP+xml");
            byte[] bodyBytes = xmlBody.getBytes(StandardCharsets.UTF_8);

            Request msg = messageFactory.createRequest(requestUri, Request.MESSAGE,
                    callIdHeader, cseq, newFrom, newTo, List.of(viaHeader), maxFwds);
            msg.addHeader(contact);
            msg.addHeader(contentType);
            msg.setContent(bodyBytes, contentType);

            sipProvider.getNewClientTransaction(msg).sendRequest();
            SipTraceLogger.logOutbound(msg);
            log.debug("<<< SIP MESSAGE (send) → {}:{} deviceId={}", target.getRemoteIp(), target.getRemotePort(), deviceId);
        } catch (Exception e) {
            log.error("发送 MESSAGE 失败: deviceId={}, error={}", deviceId, e.getMessage(), e);
        }
    }

    /**
     * 从 SIP URI 字符串提取用户部分（sip:user@host → user）。
     */
    public static String extractUser(String uri) {
        if (uri.startsWith("sip:")) uri = uri.substring(4);
        int at = uri.indexOf('@');
        return at > 0 ? uri.substring(0, at) : uri;
    }

    public ClientTransaction sendInvite(InterconnectConfig target, String deviceId,
                                        String callId, String sdpBody) {
        return sendInvite(target, deviceId, callId, sdpBody, null);
    }

    /**
     * 向外域平台发送 SIP INVITE，发起视频流请求。
     *
     * @param target    目标互联配置
     * @param deviceId  目标设备 ID
     * @param callId    Call-ID
     * @param sdpBody   SDP offer 内容
     * @param ssrc      SSRC（用于 Subject 头），可为 null
     * @return ClientTransaction，用于后续 ACK/BYE；失败返回 null
     */
    public ClientTransaction sendInvite(InterconnectConfig target, String deviceId,
                                        String callId, String sdpBody, String ssrc) {
        try {
            String localSipId = LocalSipConfigHolder.getDeviceId();
            String localIp = LocalSipConfigHolder.getSipIp();
            int localPort = LocalSipConfigHolder.getSipPort();

            javax.sip.address.SipURI requestUri = addressFactory.createSipURI(
                    deviceId, target.getRemoteIp() + ":" + target.getRemotePort());
            javax.sip.address.Address fromAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(localSipId, localIp + ":" + localPort));
            javax.sip.address.Address toAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(deviceId,
                            target.getRemoteDomain() != null ? target.getRemoteDomain() : target.getRemoteIp()));
            javax.sip.address.Address contactAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(localSipId, localIp + ":" + localPort));

            String fromTag = UUID.randomUUID().toString().substring(0, 8);
            FromHeader fromHeader = headerFactory.createFromHeader(fromAddr, fromTag);
            ToHeader toHeader = headerFactory.createToHeader(toAddr, null);
            CallIdHeader callIdHeader = headerFactory.createCallIdHeader(callId);
            CSeqHeader cseq = headerFactory.createCSeqHeader(1L, Request.INVITE);
            MaxForwardsHeader maxFwds = headerFactory.createMaxForwardsHeader(70);
            ContactHeader contact = headerFactory.createContactHeader(contactAddr);
            ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort,
                    sipProvider.getListeningPoints()[0].getTransport(), null);
            viaHeader.setRPort();
            ContentTypeHeader contentType = headerFactory.createContentTypeHeader("Application", "sdp");
            byte[] bodyBytes = sdpBody.getBytes(StandardCharsets.UTF_8);

            Request invite = messageFactory.createRequest(requestUri, Request.INVITE,
                    callIdHeader, cseq, fromHeader, toHeader, List.of(viaHeader), maxFwds);
            invite.addHeader(contact);
            invite.addHeader(contentType);
            invite.addHeader(headerFactory.createContentLengthHeader(bodyBytes.length));
            invite.setContent(bodyBytes, contentType);

            // GB/T 28181 要求 Subject 头：deviceId:ssrc,localSipId:0
            if (ssrc != null) {
                Header subjectHeader = headerFactory.createHeader("Subject",
                        deviceId + ":" + ssrc + "," + localSipId + ":0");
                invite.addHeader(subjectHeader);
            }

            ClientTransaction ct = sipProvider.getNewClientTransaction(invite);
            ct.sendRequest();
            SipTraceLogger.logOutbound(invite);
            log.debug("<<< SIP INVITE → {}:{} deviceId={}\n{}", target.getRemoteIp(), target.getRemotePort(), deviceId,
                    invite.toString());
            return ct;
        } catch (Exception e) {
            log.error("发送 INVITE 失败: deviceId={}, error={}", deviceId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 发送 ACK 确认 INVITE 200 OK。
     *
     * @param event    收到的 200 OK ResponseEvent
     * @param fromTag  INVITE 时的 From-tag
     */
    public void sendAck(ResponseEvent event, String fromTag) {
        try {
            Response response = event.getResponse();
            ClientTransaction ct = event.getClientTransaction();
            if (ct == null) {
                log.warn("sendAck: ClientTransaction 为 null，无法发送 ACK");
                return;
            }

            // 从 200 OK 提取 To-tag
            ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            String toTag = toHeader != null ? toHeader.getTag() : null;

            // 构造 ACK Request URI（与 INVITE 相同）
            Request invite = ct.getRequest();
            javax.sip.address.URI requestUri = invite.getRequestURI();

            CallIdHeader callIdHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
            FromHeader fromHeader = (FromHeader) response.getHeader(FromHeader.NAME);
            CSeqHeader cseqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

            String localIp = LocalSipConfigHolder.getSipIp();
            int localPort = LocalSipConfigHolder.getSipPort();

            ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort,
                    sipProvider.getListeningPoints()[0].getTransport(), null);
            CSeqHeader ackCseq = headerFactory.createCSeqHeader(cseqHeader.getSeqNumber(), Request.ACK);
            MaxForwardsHeader maxFwds = headerFactory.createMaxForwardsHeader(70);

            // 重建 To header（含 To-tag）
            ToHeader newTo = headerFactory.createToHeader(toHeader.getAddress(), toTag);

            Request ack = messageFactory.createRequest(requestUri, Request.ACK,
                    callIdHeader, ackCseq, fromHeader, newTo, List.of(viaHeader), maxFwds);

            sipProvider.sendRequest(ack);
            SipTraceLogger.logOutbound(ack);
            log.debug("<<< SIP ACK sent callId={}", callIdHeader.getCallId());
        } catch (Exception e) {
            log.error("发送 ACK 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送 SIP INFO（MANSRTSP 回放控制）。
     *
     * @param config   对端互联配置
     * @param deviceId 目标设备 ID
     * @param session  回放会话（含 callId/fromTag/toTag/cseq）
     * @param body     MANSRTSP 消息体
     */
    public void sendInfo(InterconnectConfig config, String deviceId, PlaybackSession session, String body) {
        try {
            String localSipId = LocalSipConfigHolder.getDeviceId();
            String localIp = LocalSipConfigHolder.getSipIp();
            int localPort = LocalSipConfigHolder.getSipPort();

            javax.sip.address.SipURI requestUri = addressFactory.createSipURI(
                    deviceId, config.getRemoteIp() + ":" + config.getRemotePort());
            javax.sip.address.Address fromAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(localSipId, localIp + ":" + localPort));
            javax.sip.address.Address toAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(deviceId,
                            config.getRemoteDomain() != null ? config.getRemoteDomain() : config.getRemoteIp()));

            FromHeader fromHeader = headerFactory.createFromHeader(fromAddr, session.fromTag());
            ToHeader toHeader = headerFactory.createToHeader(toAddr, session.toTag());
            CallIdHeader callIdHeader = headerFactory.createCallIdHeader(session.callId());
            CSeqHeader cseq = headerFactory.createCSeqHeader(session.cseq() + 1, Request.INFO);
            MaxForwardsHeader maxFwds = headerFactory.createMaxForwardsHeader(70);
            ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort,
                    sipProvider.getListeningPoints()[0].getTransport(), null);
            ContentTypeHeader contentType = headerFactory.createContentTypeHeader("Application", "MANSRTSP");
            byte[] bodyBytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            Request info = messageFactory.createRequest(requestUri, Request.INFO,
                    callIdHeader, cseq, fromHeader, toHeader, List.of(viaHeader), maxFwds);
            info.addHeader(contentType);
            info.addHeader(headerFactory.createContentLengthHeader(bodyBytes.length));
            info.setContent(bodyBytes, contentType);

            sipProvider.getNewClientTransaction(info).sendRequest();
            SipTraceLogger.logOutbound(info);
            log.debug("<<< SIP INFO (MANSRTSP) → {}:{} deviceId={}", config.getRemoteIp(), config.getRemotePort(), deviceId);
        } catch (Exception e) {
            log.error("发送 INFO 失败: deviceId={}, error={}", deviceId, e.getMessage(), e);
        }
    }

    /**
     * 发送 BYE 终止回放会话。
     */
    public void sendPlaybackBye(PlaybackSession session, InterconnectConfig config) {
        try {
            String localSipId = LocalSipConfigHolder.getDeviceId();
            String localIp = LocalSipConfigHolder.getSipIp();
            int localPort = LocalSipConfigHolder.getSipPort();

            javax.sip.address.SipURI requestUri = addressFactory.createSipURI(
                    session.deviceId(), config.getRemoteIp() + ":" + config.getRemotePort());
            javax.sip.address.Address fromAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(localSipId, localIp + ":" + localPort));
            javax.sip.address.Address toAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(session.deviceId(),
                            config.getRemoteDomain() != null ? config.getRemoteDomain() : config.getRemoteIp()));

            FromHeader fromHeader = headerFactory.createFromHeader(fromAddr, session.fromTag());
            ToHeader toHeader = headerFactory.createToHeader(toAddr, session.toTag());
            CallIdHeader callIdHeader = headerFactory.createCallIdHeader(session.callId());
            CSeqHeader cseq = headerFactory.createCSeqHeader(session.cseq() + 1, Request.BYE);
            MaxForwardsHeader maxFwds = headerFactory.createMaxForwardsHeader(70);
            ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort,
                    sipProvider.getListeningPoints()[0].getTransport(), null);

            Request bye = messageFactory.createRequest(requestUri, Request.BYE,
                    callIdHeader, cseq, fromHeader, toHeader, List.of(viaHeader), maxFwds);

            sipProvider.getNewClientTransaction(bye).sendRequest();
            SipTraceLogger.logOutbound(bye);
            log.debug("<<< SIP BYE (playback) sent callId={} deviceId={}", session.callId(), session.deviceId());
        } catch (Exception e) {
            log.error("发送回放 BYE 失败: deviceId={}, error={}", session.deviceId(), e.getMessage(), e);
        }
    }

    /**
     * 发送 BYE 终止视频流会话。
     *
     * @param session  流会话信息
     * @param config   对端互联配置
     */
    public void sendBye(StreamSession session, InterconnectConfig config) {
        try {
            String localSipId = LocalSipConfigHolder.getDeviceId();
            String localIp = LocalSipConfigHolder.getSipIp();
            int localPort = LocalSipConfigHolder.getSipPort();

            javax.sip.address.SipURI requestUri = addressFactory.createSipURI(
                    session.deviceId(), config.getRemoteIp() + ":" + config.getRemotePort());
            javax.sip.address.Address fromAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(localSipId, localIp + ":" + localPort));
            javax.sip.address.Address toAddr = addressFactory.createAddress(
                    addressFactory.createSipURI(session.deviceId(),
                            config.getRemoteDomain() != null ? config.getRemoteDomain() : config.getRemoteIp()));

            FromHeader fromHeader = headerFactory.createFromHeader(fromAddr, session.fromTag());
            ToHeader toHeader = headerFactory.createToHeader(toAddr, session.toTag());
            CallIdHeader callIdHeader = headerFactory.createCallIdHeader(session.callId());
            CSeqHeader cseq = headerFactory.createCSeqHeader(session.cseq() + 1, Request.BYE);
            MaxForwardsHeader maxFwds = headerFactory.createMaxForwardsHeader(70);
            ViaHeader viaHeader = headerFactory.createViaHeader(localIp, localPort,
                    sipProvider.getListeningPoints()[0].getTransport(), null);

            Request bye = messageFactory.createRequest(requestUri, Request.BYE,
                    callIdHeader, cseq, fromHeader, toHeader, List.of(viaHeader), maxFwds);

            sipProvider.getNewClientTransaction(bye).sendRequest();
            SipTraceLogger.logOutbound(bye);
            log.debug("<<< SIP BYE sent callId={} deviceId={}", session.callId(), session.deviceId());
        } catch (Exception e) {
            log.error("发送 BYE 失败: deviceId={}, error={}", session.deviceId(), e.getMessage(), e);
        }
    }
}
