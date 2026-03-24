## 1. 新建 SipMessageSender

- [x] 1.1 新建 `src/main/java/com/example/gbt28181/sip/SipMessageSender.java`，实现 `setSipProvider`、`sendOk`、`replyMessage`、`sendMessage`、`extractUser` 五个方法，逻辑从现有类中提取

## 2. 注册到 SipStackManager

- [x] 2.1 在 `SipStackManager` 中注入 `SipMessageSender`，在 `doStart()` 末尾调用 `sipMessageSender.setSipProvider(sipProvider)`

## 3. 重构 ConfigDownloadHandler

- [x] 3.1 注入 `SipMessageSender`，删除 `sendOk`、`sendResponseMessage`、`extractUser` 三个私有方法，调用处改为 `sipMessageSender.sendOk` / `sipMessageSender.replyMessage`

## 4. 重构 DeviceConfigHandler

- [x] 4.1 注入 `SipMessageSender`，删除 `sendOk`、`sendResponseMessage`、`extractUser` 三个私有方法，调用处改为 `sipMessageSender.sendOk` / `sipMessageSender.replyMessage`

## 5. 重构 RemoteDeviceMessageForwarder

- [x] 5.1 注入 `SipMessageSender`，删除 `sendOk`、`sendRawResponseMessage`、`extractUser` 三个私有方法
- [x] 5.2 `sendInitiatedMessage` 和 `sendForwardedMessage` 改为调用 `sipMessageSender.sendMessage`

## 6. 重构 CatalogQueryService

- [x] 6.1 注入 `SipMessageSender`，`queryCatalog` 中的 MESSAGE 发送部分改为调用 `sipMessageSender.sendMessage`

## 7. 重构 CatalogNotifyHandler

- [x] 7.1 删除 `extractUserFromUri` 私有方法，改为调用 `SipMessageSender.extractUser`（static 调用）

## 8. 验证

- [ ] 8.1 启动后端，验证 SIP 注册、心跳、ConfigDownload、DeviceConfig 流程正常
