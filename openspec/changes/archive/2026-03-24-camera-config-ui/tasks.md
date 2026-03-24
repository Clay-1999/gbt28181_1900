## 1. 本端配置 Service

- [x] 1.1 新建 `DeviceConfigService`，注入 `Ivs1900CameraMappingRepository` 和 `Ivs1900DeviceConfigClient`
- [x] 1.2 实现 `getLocalConfig(gbDeviceId, configType)`：查找 camera mapping（404 if not found），按 configType 调用对应 get 方法，将结果序列化为 `Map<String, Object>`
- [x] 1.3 实现 `setLocalConfig(gbDeviceId, configType, patch)`：查找 camera mapping，按 configType 调用对应 set 方法，返回 boolean

configType 映射：
- `VideoParamAttribute` → `getStreamConfig` / `setStreamConfig`
- `OSDConfig` → `getOsdConfig` / `setOsdConfig`
- `PictureMask` → `getVideoMask` / `setVideoMask`
- `FrameMirror` → `getStreamConfig` / `setStreamConfig`（patch 含 `frameMirrorMode`）

## 2. 本端配置 REST 接口

每种配置类型独立 URL，共 8 个 endpoint：

- [x] 2.1 `GET  /api/devices/local/{gbDeviceId}/config/video-param`
- [x] 2.2 `PUT  /api/devices/local/{gbDeviceId}/config/video-param`
- [x] 2.3 `GET  /api/devices/local/{gbDeviceId}/config/osd`
- [x] 2.4 `PUT  /api/devices/local/{gbDeviceId}/config/osd`
- [x] 2.5 `GET  /api/devices/local/{gbDeviceId}/config/picture-mask`
- [x] 2.6 `PUT  /api/devices/local/{gbDeviceId}/config/picture-mask`
- [x] 2.7 `GET  /api/devices/local/{gbDeviceId}/config/frame-mirror`
- [x] 2.8 `PUT  /api/devices/local/{gbDeviceId}/config/frame-mirror`

各 GET 返回 `Map` 或 404；各 PUT body 为 `Map<String, Object>`，返回 `{"success": true/false}`

## 3. 外域配置 SIP 主动发起

- [x] 3.1 在 `RemoteDeviceMessageForwarder` 新增 `queryConfig(RemoteDevice device, String configType)`：构造 ConfigDownload SIP MESSAGE（参考 `CatalogQueryService.queryCatalog` 模式），用 callId 作 pending key，等待 future（10s 超时），返回响应 XML body 或 null
- [x] 3.2 在 `RemoteDeviceMessageForwarder` 新增 `setConfig(RemoteDevice device, String configType, Map<String, Object> patch)`：构造 DeviceConfig SIP MESSAGE XML body，发送并等待响应（10s），返回 Result=OK 则 true
- [x] 3.3 新增 `onIncomingConfigResponse(String callId, String xmlBody)` 方法：查找 pending 中对应 future 并 complete

## 4. 外域配置响应 MESSAGE 路由

- [x] 4.1 在 `GbtSipListener.processRequest` 的 MESSAGE 分支中，解析 body 的 CmdType 和 Call-ID，若 `RemoteDeviceMessageForwarder.hasPending(callId)` 为 true，则调用 `onIncomingConfigResponse` 并回复 200 OK，不走 `deviceCommandRouter.route()`

## 5. 外域配置 REST 接口

每种配置类型独立 URL，共 8 个 endpoint：

- [x] 5.1 `GET  /api/devices/remote/{deviceId}/config/video-param`
- [x] 5.2 `PUT  /api/devices/remote/{deviceId}/config/video-param`
- [x] 5.3 `GET  /api/devices/remote/{deviceId}/config/osd`
- [x] 5.4 `PUT  /api/devices/remote/{deviceId}/config/osd`
- [x] 5.5 `GET  /api/devices/remote/{deviceId}/config/picture-mask`
- [x] 5.6 `PUT  /api/devices/remote/{deviceId}/config/picture-mask`
- [x] 5.7 `GET  /api/devices/remote/{deviceId}/config/frame-mirror`
- [x] 5.8 `PUT  /api/devices/remote/{deviceId}/config/frame-mirror`

各 GET 查找 RemoteDevice（404 if not found），调用 `queryConfig`，解析 XML 为 `Map`，超时返回 504；各 PUT 调用 `setConfig`，返回 `{"success": true/false}`，超时返回 504

## 6. 前端配置对话框

- [x] 6.1 在 `DevicesView.vue` 本端设备表格增加"配置"操作列，绑定 `openConfig(row, 'local')` 方法
- [x] 6.2 在外域设备表格增加"配置"操作列，绑定 `openConfig(row, 'remote')` 方法
- [x] 6.3 新增配置对话框（`el-dialog`），包含 `el-tabs`，tab 项：VideoParamAttribute、OSDConfig、PictureMask、FrameMirror
- [x] 6.4 实现 `openConfig(row, type)`：记录当前设备和类型，打开对话框，触发加载当前 tab 配置
- [x] 6.5 实现 `loadConfig(configType)`：调用对应 GET 接口，填充表单数据，处理加载状态和错误
- [x] 6.6 实现各 tab 表单字段：
  - VideoParamAttribute：码流列表（StreamID、VideoFormat、Resolution、FrameRate、BitRateType、BitRate）
  - OSDConfig：OSDEnabled（switch）、OSDTime（switch）、OSDFontSize（number）
  - PictureMask：MaskEnabled（switch）
  - FrameMirror：MirrorEnabled（select：0=不翻转、1=水平、2=垂直、3=水平+垂直）
- [x] 6.7 实现"保存"按钮：调用对应 PUT 接口，显示成功/失败提示
