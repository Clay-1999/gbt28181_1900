## 1. 北向目录 NOTIFY 发送

- [x] 1.1 在 `CatalogSubscribeHandler` 中注入 `SipProvider`（已有 setter），实现 `buildNotifyRequest(subscribeRequest, xmlBody)` 方法，构造带 `Event: Catalog`、`Subscription-State: active; expires=3600`、`Content-Type: Application/MANSCDP+xml` 头的 SIP NOTIFY 请求
- [x] 1.2 在 `sendCatalogNotify()` 中调用 `buildNotifyRequest()` 并通过 `sipProvider.sendRequest()` 发送，替换现有 TODO 占位代码
- [x] 1.3 新增 `public void pushCatalogToAddress(String targetSipId, String targetIp, int targetPort)` 方法，供注册成功后主动推送调用，不依赖 SUBSCRIBE 上下文直接构造 out-of-dialog NOTIFY

## 2. 注册成功后主动推送目录

- [x] 2.1 在 `SipRegistrationClient` 中注入 `CatalogSubscribeHandler`（通过构造器注入）
- [x] 2.2 在 `handleRegisterOk(configId)` 方法中（初始注册成功分支，非续约），调用 `catalogSubscribeHandler.pushCatalogToAddress(config.getRemoteSipId(), config.getRemoteIp(), config.getRemotePort())`，使用 `CompletableFuture.runAsync` 异步执行，catch 异常记录 WARN 日志

## 3. 北向 INVITE 接收（本端相机）

- [x] 3.1 在 `GbtSipListener.processRequest()` 中增加 `Request.INVITE` 分支，调用 `sipInviteService.onIncomingInvite(event)`
- [x] 3.2 在 `SipInviteService` 中新增 `onIncomingInvite(RequestEvent event)` 方法：解析 Request-URI user 部分得到目标 gbDeviceId，查询 `Ivs1900CameraMappingRepository`，未找到则回 404
- [x] 3.3 找到本端相机后，回 `100 Trying`，调用 `LocalDeviceStreamService.startStream(gbDeviceId)` 获取流，从 ZLMediaKit 获取对应 RTP 端口
- [x] 3.4 构造 SDP（`m=video <port> RTP/AVP 96`，`a=rtpmap:96 PS/90000`，`c=IN IP4 <localIp>`），回复 `200 OK` 并携带 SDP，建立会话记录（关联 callId）
- [x] 3.5 在 `handleBye()` 中增加入站会话的 BYE 处理：调用 `LocalDeviceStreamService.stopStream(gbDeviceId)` 停止流

## 4. 北向 PTZ 命令接收

- [x] 4.1 在 `DeviceCommandRouter.route()` 中增加对 `CmdType=DeviceControl` 的识别分支（在现有 ConfigDownload/DeviceConfig 判断之前）
- [x] 4.2 提取 XML 中 `PTZCmd` 字段，按 `DeviceID` 路由：本端相机调用 `PtzService.sendRawPtzCmd(gbDeviceId, ptzCmdHex)`，外域设备调用 `RemoteDeviceMessageForwarder.forward()` 原样转发
- [x] 4.3 在 `PtzService` 中新增 `sendRawPtzCmd(String gbDeviceId, String ptzCmdHex)` 方法，解析16进制 PTZCmd（参考 GB/T 28181 附录 F），映射到现有 PTZ 动作并调用 IVS1900 REST 接口；PTZCmd 解析异常时记录 WARN 并返回 false
- [x] 4.4 在 `DeviceCommandRouter.route()` 对 DeviceControl 回复 200 OK
