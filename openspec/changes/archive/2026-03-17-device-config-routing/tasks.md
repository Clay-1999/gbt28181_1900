## 1. 命令路由核心

- [x] 1.1 新建 `sip/DeviceCommandRouter.java`：注入 `Ivs1900CameraRepository`、`RemoteDeviceRepository`，`route(RequestEvent event)` 方法按 DeviceID 三路分发（本端 / 外域 / 404）
- [x] 1.2 修改 `sip/GbtSipListener.java`：在 `processRequest()` 的 MESSAGE 分支中，识别 CmdType 为 ConfigDownload / DeviceConfig 时转交 `DeviceCommandRouter.route()`

## 2. 本端设备：ConfigDownload 处理

- [x] 2.1 新建 `sip/ConfigDownloadHandler.java`：`handle(RequestEvent event, Ivs1900Camera camera)` 方法，解析 ConfigType 字段（支持 `/` 分隔的多 ConfigType，逐一处理合并到同一 Response）
- [x] 2.2 实现 **BasicParam** 查询：调用 `GET /device/deviceconfig/1`，映射设备名称、心跳周期等字段到 `<BasicParam>`
- [x] 2.3 实现 **VideoParamAttribute** 查询：调用 `GET /device/deviceconfig/5`（CameraStreamConfig），按 streamInfoList 下标映射 StreamNumber，将 encodeType/resolution/frameRate/bitRateType/bitRate 映射到 `<VideoParamAttribute>` 各 `<Item>`
- [x] 2.4 实现 **PictureMask** 查询：调用 `GET /device/deviceconfig/11`（VideoMask），映射隐私遮挡区域到 `<PictureMask>`
- [x] 2.5 实现 **FrameMirror** 查询：调用 `GET /device/deviceconfig/5`，取 `frameMirrorMode` 字段映射到 `<FrameMirror>`
- [x] 2.6 实现 **OSDConfig** 查询：调用 `GET /device/deviceconfig/8`，映射 OSD 文字、时间、字体大小等字段到 `<OSDConfig>`
- [x] 2.7 实现无接口的 ConfigType 查询（SVACEncodeConfig / SVACDecodeConfig / VideoRecordPlan / VideoAlarmRecord / SnapShotConfig）：返回对应空结构体，Result=OK，记录 debug 日志
- [x] 2.8 实现 **VideoParamOpt** 查询：调用 `POST /device/sdc-capability`（capabilityType=1），将返回的 H264/H265/MJPEG 各编码格式支持的分辨率列表、帧率范围、码率范围映射到 `<VideoParamOpt>` 的 `Resolution` 字段（支持的分辨率以 `/` 分隔）
- [x] 2.9 实现 **AlarmReport** 查询：从内存中读取本系统维护的报警上报配置，组装 `<AlarmReport>` 返回
- [x] 2.8 构造 ConfigDownload Response MESSAGE 并通过 SipProvider 发送；多 ConfigType 合并到同一 Response

## 3. 本端设备：DeviceConfig 处理

- [x] 3.1 新建 `sip/DeviceConfigHandler.java`：`handle(RequestEvent event, Ivs1900Camera camera)` 方法，解析子命令类型
- [x] 3.2 实现 **BasicParam** 配置：调用 `POST /device/setdeviceconfig/1`（通用配置）或 `PUT /device/camera/name/v1.0`（Name 专用）；成功回复 Result=OK，失败回复 Result=Error
- [x] 3.3 实现 **VideoParamAttribute** 配置：调用 `POST /device/setdeviceconfig/5`，下发各码流的编码格式、分辨率、帧率、码率参数
- [x] 3.4 实现 **PictureMask** 配置：调用 `POST /device/setdeviceconfig/11`，下发隐私遮挡区域
- [x] 3.5 实现 **FrameMirror** 配置：调用 `POST /device/setdeviceconfig/5`，仅更新 `frameMirrorMode` 字段
- [x] 3.6 实现 **OSDConfig** 配置：调用 `POST /device/setdeviceconfig/8`，下发 OSD 参数
- [x] 3.7 实现无接口或只读的子命令（VideoParamOpt 只读不支持写 / SVACEncodeConfig / SVACDecodeConfig / VideoRecordPlan / VideoAlarmRecord / SnapShotConfig）：记录 debug 日志，回复 Result=OK（静默忽略）
- [x] 3.8 实现 **AlarmReport** 配置：将配置内容写入内存（本系统自行管理），回复 Result=OK
- [x] 3.8 构造 DeviceConfig Response MESSAGE 并通过 SipProvider 发送

## 4. 外域设备：命令透传

- [x] 4.1 新建 `sip/RemoteDeviceMessageForwarder.java`：`forward(RequestEvent event, RemoteDevice device)` 方法
- [x] 4.2 通过 `interconnect_config_id` 查询对端 SIP 地址，构造转发用 SIP MESSAGE（替换 Request-URI / To 头，保留原始 XML body）
- [x] 4.3 用 `ConcurrentHashMap<String, CompletableFuture>` 以 SN 关联等待中的透传请求
- [x] 4.4 在 `DeviceCommandRouter.route()` 中识别透传应答（Response 根节点 + Call-ID/SN 在等待 Map 中），完成 CompletableFuture 并透传回原始请求方
- [x] 4.5 设置 5 秒超时：超时后回复原始请求方 `SIP/2.0 408 Request Timeout`，清理等待 Map

## 5. 验证

- [x] 5.1 编译通过：`mvn compile -q`
- [x] 5.2 发送 ConfigDownload（BasicParam）给本端 IVS1900 相机，确认收到含 `<BasicParam>` 的应答 MESSAGE
- [x] 5.3 发送 ConfigDownload（VideoParamOpt）给本端相机，确认收到含支持分辨率列表的 `<VideoParamOpt>` 应答（数据来自 sdc-capability 接口）
- [x] 5.4 发送 ConfigDownload（VideoParamAttribute）给本端相机，确认收到含各码流 StreamNumber/VideoFormat/Resolution/FrameRate/BitRateType 的 `<VideoParamAttribute>` 应答（数据来自 deviceconfig/5 接口）
- [x] 5.5 发送 ConfigDownload（PictureMask）给本端相机，确认收到含遮挡区域的 `<PictureMask>` 应答
- [x] 5.6 发送 ConfigDownload（FrameMirror）给本端相机，确认收到含 `<FrameMirror>` 的应答（frameMirrorMode 字段）
- [x] 5.7 发送 ConfigDownload（OSDConfig）给本端相机，确认收到含 OSD 参数的 `<OSDConfig>` 应答
- [x] 5.8 发送 ConfigDownload（SVACDecodeConfig）给本端相机，确认收到含 `<SVACDecodeConfig/>` 的应答，Result=OK
- [x] 5.9 发送 ConfigDownload（VideoRecordPlan / AlarmReport / SnapShotConfig 等无接口 ConfigType）各一次，确认均回复合法应答，Result=OK
- [x] 5.9 发送 ConfigDownload（多 ConfigType，如 `BasicParam/OSDConfig`）给本端相机，确认应答合并在同一 Response
- [x] 5.10 发送 DeviceConfig（BasicParam/Name）给本端相机，确认 ivs1900 相机名称变更，回复 Result=OK
- [x] 5.11 发送 DeviceConfig（VideoParamAttribute）给本端相机，确认码流参数下发，回复 Result=OK
- [x] 5.12 发送 DeviceConfig（PictureMask）给本端相机，确认遮挡区域下发，回复 Result=OK
- [x] 5.13 发送 DeviceConfig（OSDConfig）给本端相机，确认 OSD 参数下发，回复 Result=OK
- [x] 5.14 发送 DeviceConfig（SnapShotConfig 等无接口子命令），确认回复 Result=OK（静默忽略）
- [x] 5.15 模拟 ivs1900 接口返回 5xx，确认 DeviceConfig 回复 Result=Error
- [x] 5.16 发送 ConfigDownload 给外域设备 DeviceID，确认命令被透传至对端，应答透传回来
- [x] 5.17 发送 ConfigDownload 给未知 DeviceID，确认回复 404 Not Found
