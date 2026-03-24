package com.example.gbt28181.sip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.nio.charset.StandardCharsets;

/**
 * SIP 消息收发追踪日志。
 * <p>
 * 所有入站和出站 SIP 消息（心跳除外）写入专用文件 logs/sip-trace.log。
 * 使用静态方法，无需 Spring 注入。
 */
public final class SipTraceLogger {

    private static final Logger TRACE = LoggerFactory.getLogger("sip.trace");

    private SipTraceLogger() {}

    /** 记录收到的 SIP 消息（REGISTER / MESSAGE / NOTIFY / SUBSCRIBE / INVITE / BYE 等）。 */
    public static void logInbound(Message message) {
        if (isKeepalive(message)) return;
        String direction = message instanceof Request ? "REQUEST" : "RESPONSE";
        TRACE.info("<<< RECV {} ---\n{}\n---", direction, message);
    }

    /** 记录发出的 SIP 消息。 */
    public static void logOutbound(Message message) {
        if (isKeepalive(message)) return;
        String direction = message instanceof Request ? "REQUEST" : "RESPONSE";
        TRACE.info(">>> SEND {} ---\n{}\n---", direction, message);
    }

    private static boolean isKeepalive(Message message) {
        byte[] raw = message.getRawContent();
        if (raw == null) return false;
        String body = new String(raw, StandardCharsets.UTF_8);
        return body.contains("<CmdType>Keepalive</CmdType>");
    }
}
