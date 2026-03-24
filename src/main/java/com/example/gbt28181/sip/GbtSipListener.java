package com.example.gbt28181.sip;

import com.example.gbt28181.util.LogUtils;
import com.example.gbt28181.service.Ivs1900SipConfigService;
import com.example.gbt28181.service.PtzService;
import com.example.gbt28181.service.RecordQueryService;
import com.example.gbt28181.service.SipPlaybackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.sip.*;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
@Component
@RequiredArgsConstructor
@Slf4j
public class GbtSipListener implements SipListener {

    private final SipRegistrationServer sipRegistrationServer;
    private final SipRegistrationClient sipRegistrationClient;
    private final CatalogSubscribeHandler catalogSubscribeHandler;
    private final CatalogNotifyHandler catalogNotifyHandler;
    private final AlarmNotifyHandler alarmNotifyHandler;
    private final DeviceCommandRouter deviceCommandRouter;
    private final RemoteDeviceMessageForwarder remoteDeviceMessageForwarder;
    private final Ivs1900SipConfigService ivs1900SipConfigService;
    private final PtzService ptzService;
    private final RecordQueryService recordQueryService;
    private final SipInviteService sipInviteService;
    private final SipPlaybackService sipPlaybackService;
    private final PlaybackSessionStore playbackSessionStore;
    private final SipAudioService sipAudioService;
    private final AudioSessionStore audioSessionStore;

    @Override
    public void processRequest(RequestEvent event) {
        String method = event.getRequest().getMethod();
        SipTraceLogger.logInbound(event.getRequest());
        log.debug(">>> SIP REQUEST {}", LogUtils.escape(event.getRequest()));
        if (Request.REGISTER.equals(method)) {
            sipRegistrationServer.handleRegister(event);
        } else if (Request.MESSAGE.equals(method)) {
            // 先检查是否是 REST 主动发起的配置查询/下发的响应 MESSAGE（通过 SN 匹配）
            byte[] rawBody = event.getRequest().getRawContent();
            String sn = extractSn(rawBody);
            if (sn != null && ivs1900SipConfigService.hasPendingSn(sn)) {
                String xmlBody = rawBody != null ? new String(rawBody, java.nio.charset.StandardCharsets.UTF_8) : "";
                ivs1900SipConfigService.onIncomingConfigResponse(sn, xmlBody);
                try {
                    ServerTransaction tx = event.getServerTransaction();
                    if (tx == null) tx = ((SipProvider) event.getSource()).getNewServerTransaction(event.getRequest());
                    tx.sendResponse(javax.sip.SipFactory.getInstance().createMessageFactory()
                            .createResponse(javax.sip.message.Response.OK, event.getRequest()));
                } catch (Exception e) {
                    log.warn("回复 IVS1900 配置响应 200 OK 失败: {}", e.getMessage());
                }
            } else if (sn != null && remoteDeviceMessageForwarder.hasPendingSn(sn)) {
                String xmlBody = rawBody != null ? new String(rawBody, java.nio.charset.StandardCharsets.UTF_8) : "";
                remoteDeviceMessageForwarder.onIncomingConfigResponse(sn, xmlBody);
                try {
                    ServerTransaction tx = event.getServerTransaction();
                    if (tx == null) tx = ((SipProvider) event.getSource()).getNewServerTransaction(event.getRequest());
                    tx.sendResponse(javax.sip.SipFactory.getInstance().createMessageFactory()
                            .createResponse(javax.sip.message.Response.OK, event.getRequest()));
                } catch (Exception e) {
                    log.warn("回复配置响应 200 OK 失败: {}", e.getMessage());
                }
            } else if (sn != null && ptzService.hasPendingSn(sn)) {
                String xmlBody = rawBody != null ? new String(rawBody, java.nio.charset.StandardCharsets.UTF_8) : "";
                ptzService.onResponse(sn, xmlBody);
                try {
                    ServerTransaction tx = event.getServerTransaction();
                    if (tx == null) tx = ((SipProvider) event.getSource()).getNewServerTransaction(event.getRequest());
                    tx.sendResponse(javax.sip.SipFactory.getInstance().createMessageFactory()
                            .createResponse(javax.sip.message.Response.OK, event.getRequest()));
                } catch (Exception e) {
                    log.warn("回复 PTZ 查询响应 200 OK 失败: {}", e.getMessage());
                }
            } else if (sn != null && recordQueryService.hasPendingSn(sn)) {
                String xmlBody = rawBody != null ? new String(rawBody, java.nio.charset.StandardCharsets.UTF_8) : "";
                recordQueryService.onResponse(sn, xmlBody);
                try {
                    ServerTransaction tx = event.getServerTransaction();
                    if (tx == null) tx = ((SipProvider) event.getSource()).getNewServerTransaction(event.getRequest());
                    tx.sendResponse(javax.sip.SipFactory.getInstance().createMessageFactory()
                            .createResponse(javax.sip.message.Response.OK, event.getRequest()));
                } catch (Exception e) {
                    log.warn("回复录像查询响应 200 OK 失败: {}", e.getMessage());
                }
            } else if (!deviceCommandRouter.route(event)) {
                sipRegistrationServer.handleMessage(event);
            }
            MDC.remove("callId");
        } else if (Request.SUBSCRIBE.equals(method)) {
            catalogSubscribeHandler.handleSubscribe(event);
        } else if (Request.NOTIFY.equals(method)) {
            javax.sip.header.EventHeader eventHeader =
                    (javax.sip.header.EventHeader) event.getRequest().getHeader(javax.sip.header.EventHeader.NAME);
            String eventType = eventHeader != null ? eventHeader.getEventType() : "";
            if ("Alarm".equalsIgnoreCase(eventType)) {
                alarmNotifyHandler.handle(event);
            } else if ("Catalog".equalsIgnoreCase(eventType) || "presence".equalsIgnoreCase(eventType) || eventType.isEmpty()) {
                catalogNotifyHandler.handleNotify(event);
            } else {
                log.debug("收到未知 Event 类型 NOTIFY: {}", eventType);
                try {
                    SipProvider provider = (SipProvider) event.getSource();
                    Request req = event.getRequest();
                    javax.sip.message.Response ok = SipFactory.getInstance().createMessageFactory()
                            .createResponse(javax.sip.message.Response.OK, req);
                    ServerTransaction tx = event.getServerTransaction();
                    if (tx == null) {
                        try {
                            tx = provider.getNewServerTransaction(req);
                        } catch (Exception ex) {
                            log.debug("未知 NOTIFY 创建 ServerTransaction 失败，降级无状态响应: {}", ex.getMessage());
                        }
                    }
                    if (tx != null) {
                        tx.sendResponse(ok);
                    } else {
                        provider.sendResponse(ok);
                    }
                } catch (Exception e) {
                    log.warn("回复未知 NOTIFY 200 OK 失败: {}", e.getMessage());
                }
            }
        } else if (Request.BYE.equals(method)) {
            handleBye(event);
        } else if (Request.INVITE.equals(method)) {
            sipInviteService.onIncomingInvite(event);
        } else {
            log.warn("收到未处理的 SIP 请求: {}", method);
        }
    }

    @Override
    public void processResponse(ResponseEvent event) {
        SipTraceLogger.logInbound(event.getResponse());
        log.debug(">>> SIP RESPONSE {}", LogUtils.escape(event.getResponse()));
        int statusCode = event.getResponse().getStatusCode();
        String method = ((javax.sip.header.CSeqHeader) event.getResponse()
                .getHeader(javax.sip.header.CSeqHeader.NAME)).getMethod();
        if (Request.INVITE.equals(method) && statusCode == javax.sip.message.Response.OK) {
            // 先查是否是回放会话的响应
            CallIdHeader callIdHeader = (CallIdHeader) event.getResponse().getHeader(CallIdHeader.NAME);
            String callId = callIdHeader != null ? callIdHeader.getCallId() : "";
            if (sipAudioService.hasCallId(callId)) {
                sipAudioService.onInviteOk(event);
            } else if (playbackSessionStore.getByCallId(callId).isPresent()
                    || sipPlaybackService != null) {
                // 尝试先交给回放服务处理（回放 future 会 complete，实时 future 不受影响）
                sipPlaybackService.onInviteOk(event);
                sipInviteService.onInviteOk(event);
            } else {
                sipInviteService.onInviteOk(event);
            }
        } else {
            sipRegistrationClient.handleResponse(event);
        }
    }

    @Override
    public void processTimeout(TimeoutEvent event) {
        ClientTransaction tx = event.getClientTransaction();
        if (tx == null) return;

        CallIdHeader callIdHeader = (CallIdHeader) tx.getRequest().getHeader(CallIdHeader.NAME);
        if (callIdHeader == null) return;
        String callId = callIdHeader.getCallId();
        String method = tx.getRequest().getMethod();

        if (Request.REGISTER.equals(method)) {
            sipRegistrationClient.handleRegisterTimeout(callId);
        } else if (Request.MESSAGE.equals(method)) {
            sipRegistrationClient.handleMessageTimeout(callId);
        }
    }

    @Override
    public void processIOException(IOExceptionEvent event) {
        log.error("SIP IO 异常: host={} port={}", event.getHost(), event.getPort());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent event) {}

    @Override
    public void processDialogTerminated(DialogTerminatedEvent event) {}

    private void handleBye(RequestEvent event) {
        try {
            ServerTransaction tx = event.getServerTransaction();
            if (tx == null) {
                tx = ((SipProvider) event.getSource()).getNewServerTransaction(event.getRequest());
            }
            tx.sendResponse(javax.sip.SipFactory.getInstance().createMessageFactory()
                    .createResponse(javax.sip.message.Response.OK, event.getRequest()));
        } catch (Exception e) {
            log.warn("回复 BYE 200 OK 失败: {}", e.getMessage());
        }
        CallIdHeader callIdHeader = (CallIdHeader) event.getRequest().getHeader(CallIdHeader.NAME);
        if (callIdHeader != null) {
            String callId = callIdHeader.getCallId();
            if (audioSessionStore.findByCallId(callId).isPresent()) {
                sipAudioService.onRemoteBye(callId);
            } else {
                sipInviteService.onRemoteBye(callId);
                sipPlaybackService.onRemoteBye(callId);
            }
        }
    }

    private String extractSn(byte[] rawBody) {
        if (rawBody == null) return null;
        try {
            String body = new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
            int start = body.indexOf("<SN>") + 4;
            int end = body.indexOf("</SN>");
            if (start > 3 && end > start) return body.substring(start, end).trim();
        } catch (Exception ignored) {}
        return null;
    }
}
