package com.example.gbt28181.sip;

import com.example.gbt28181.domain.entity.LocalSipConfig;
import com.example.gbt28181.domain.entity.SipStackStatus;
import com.example.gbt28181.domain.repository.InterconnectConfigRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sip.*;
import javax.sip.ObjectInUseException;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.TooManyListenersException;

@Component
@RequiredArgsConstructor
@Slf4j
public class SipStackManager {

    private final InterconnectConfigRepository interconnectConfigRepository;
    private final SipRegistrationServer sipRegistrationServer;
    private final SipRegistrationClient sipRegistrationClient;
    private final CatalogSubscribeHandler catalogSubscribeHandler;
    private final CatalogQueryService catalogQueryService;
    private final CatalogNotifyHandler catalogNotifyHandler;
    private final DeviceCommandRouter deviceCommandRouter;
    private final ConfigDownloadHandler configDownloadHandler;
    private final DeviceConfigHandler deviceConfigHandler;
    private final RemoteDeviceMessageForwarder remoteDeviceMessageForwarder;
    private final SipMessageSender sipMessageSender;
    private final SipInviteService sipInviteService;
    private final SipAudioService sipAudioService;
    private final GbtSipListener gbtSipListener;

    @Getter
    private volatile SipStackStatus status = SipStackStatus.ERROR;

    @Getter
    private volatile String errorMsg = "SIP Stack 未初始化";

    private SipStack sipStack;
    private SipProvider sipProvider;

    public synchronized void reload(LocalSipConfig config) {
        if (status == SipStackStatus.RELOADING) {
            log.warn("SIP Stack 正在热重载中，跳过本次请求");
            return;
        }

        log.info("[1] SIP Stack 开始热重载，新配置: {}:{}/{}", config.getSipIp(), config.getSipPort(), config.getTransport());
        status = SipStackStatus.RELOADING;
        errorMsg = null;

        try {
            // [2] 停止所有 SIP Client 注册任务
            sipRegistrationClient.stopAll();

            // [3] 销毁旧 SipStack
            doStop();

            // [4] 检查新端口可用性
            String transport = config.getTransport() != null ? config.getTransport() : "UDP";
            if (!checkPortAvailable(config.getSipIp(), config.getSipPort(), transport)) {
                status = SipStackStatus.ERROR;
                errorMsg = String.format("端口 %d 不可用（%s）", config.getSipPort(), transport);
                log.error("[4] {}", errorMsg);
                return;
            }
            log.info("[4] 端口 {} 可用", config.getSipPort());

            // [5] 创建新 SipStack
            doStart(config);

            // [6] 启动所有 SIP Client 注册任务
            sipRegistrationClient.startAll();

            // [7] 标记 RUNNING
            status = SipStackStatus.RUNNING;
            errorMsg = null;
            log.info("[7] SIP Stack 热重载完成，监听 {}:{}/{}", config.getSipIp(), config.getSipPort(), transport);

        } catch (Exception e) {
            log.error("SIP Stack 热重载失败: {}", e.getMessage(), e);
            status = SipStackStatus.ERROR;
            errorMsg = e.getMessage();
        }
    }

    private boolean checkPortAvailable(String ip, int port, String transport) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if ("TCP".equalsIgnoreCase(transport)) {
                try (ServerSocket ss = new ServerSocket(port, 1, addr)) {
                    return true;
                }
            } else {
                try (DatagramSocket ds = new DatagramSocket(port, addr)) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
    }

    private void doStop() {
        sipRegistrationServer.shutdown();
        sipRegistrationClient.stopAll();
        if (sipStack != null) {
            try {
                if (sipProvider != null) {
                    sipProvider.removeSipListener(gbtSipListener);
                    sipStack.deleteSipProvider(sipProvider);
                }
            } catch (Exception e) {
                log.warn("删除 SipProvider 时异常（忽略）: {}", e.getMessage());
            }
            try {
                sipStack.stop();
                Thread.sleep(500);
            } catch (Exception e) {
                log.warn("停止 SipStack 时异常（忽略）: {}", e.getMessage());
            }
            sipStack = null;
            sipProvider = null;
            log.info("[3] 旧 SipStack 已销毁");
        }
    }

    private void doStart(LocalSipConfig config) throws PeerUnavailableException, TransportNotSupportedException,
            InvalidArgumentException, TooManyListenersException, ObjectInUseException {
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");

        Properties props = new Properties();
        props.setProperty("javax.sip.STACK_NAME", "gbt28181-" + System.currentTimeMillis());
        props.setProperty("javax.sip.IP_ADDRESS", config.getSipIp());
        props.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");
        props.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT", "false");
        // 关闭自动 dialog 管理，允许收到无对应 SUBSCRIBE 的 NOTIFY（GB/T 28181 Catalog 查询响应）
        props.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");
        props.setProperty("gov.nist.javax.sip.DELIVER_UNSOLICITED_NOTIFY", "true");

        sipStack = sipFactory.createSipStack(props);
        String transport = config.getTransport() != null ? config.getTransport() : "UDP";
        ListeningPoint lp = sipStack.createListeningPoint(config.getSipIp(), config.getSipPort(), transport);
        sipProvider = sipStack.createSipProvider(lp);
        sipProvider.addSipListener(gbtSipListener);
        sipRegistrationServer.setSipProvider(sipProvider);
        sipRegistrationClient.setSipProvider(sipProvider);
        catalogSubscribeHandler.setSipProvider(sipProvider);
        catalogQueryService.setSipProvider(sipProvider);
        catalogNotifyHandler.setSipProvider(sipProvider);
        deviceCommandRouter.setSipProvider(sipProvider);
        configDownloadHandler.setSipProvider(sipProvider);
        deviceConfigHandler.setSipProvider(sipProvider);
        remoteDeviceMessageForwarder.setSipProvider(sipProvider);
        sipMessageSender.setSipProvider(sipProvider);
        sipInviteService.setSipProvider(sipProvider);
        sipAudioService.setSipProvider(sipProvider);
        LocalSipConfigHolder.update(config.getDeviceId(), config.getSipIp(), config.getSipPort());        log.info("[5] SipStack 创建成功：{}:{}/{}", config.getSipIp(), config.getSipPort(), transport);
    }

    public synchronized void setStatusError(String msg) {
        this.status = SipStackStatus.ERROR;
        this.errorMsg = msg;
    }

    public synchronized void stop() {
        doStop();
        status = SipStackStatus.ERROR;
        errorMsg = "SIP Stack 已停止";
    }
}
