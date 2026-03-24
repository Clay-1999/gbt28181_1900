## Context

系统已实现双向 SIP 注册、心跳保活、IVS1900 相机同步、外域设备目录同步。下一步需要处理对端对具体设备发来的 SIP MESSAGE 控制/查询命令。

当前 `GbtSipListener.processRequest()` 对 MESSAGE 只处理 Keepalive 心跳，未处理 ConfigDownload / DeviceConfig。系统中存在两类设备：
- **本端设备**：存于 `ivs1900_camera_mapping`，需翻译命令为 ivs1900 REST API
- **外域设备**：存于 `remote_device`，需将命令透传给该设备所属的互联对端平台

## Goals / Non-Goals

**Goals:**
- 实现 ConfigDownload（设备参数查询）和 DeviceConfig（设备参数配置）的命令路由
- 本端设备：调用 ivs1900 REST API，将响应翻译为 GB/T 28181 XML 回复
- 外域设备：原样转发 SIP MESSAGE 给对端平台，透传对端应答
- 未知设备：回复 404 Not Found

**Non-Goals:**
- PTZ 控制命令（留待 Phase 7）
- ConfigDownload/DeviceConfig 之外的其他 SIP MESSAGE 类型

## Decisions

### 决策 1：路由逻辑集中在 DeviceCommandRouter

将路由判断封装在独立的 `DeviceCommandRouter` 组件，而非在 `GbtSipListener` 中堆砌 if-else。

**理由**：路由逻辑复用（PTZ、录像控制等后续命令也走同一入口），且便于单元测试。

### 决策 2：外域设备命令透传不解析 XML Body

对外域设备，直接读取原始 SIP MESSAGE body bytes，构造新的 SIP MESSAGE 发往对端，不尝试解析或修改 XML。

**理由**：GB/T 28181 允许厂商扩展字段，强行解析可能丢失信息；透传最简单、兼容性最好。

对端应答（SIP 200 OK + 可能的应答 MESSAGE）需要透传回原始请求方，用 Call-ID 关联两条腿。

### 决策 3：本端设备实现全部 12 种 ConfigType，ivs1900 无对应接口的字段忽略

对 ConfigDownload 和 DeviceConfig 涉及的全部 ConfigType，均实现响应处理。ivs1900 REST API 有对应接口的字段正常映射；无对应接口的字段在 ConfigDownload 应答中省略（不填充），在 DeviceConfig 中忽略并记录 debug 日志，不报错。

**ivs1900 ConfigType → REST API 映射表（基于 videoDevice.yaml 分析）：**

| ConfigType | ConfigDownload（查询）接口 | DeviceConfig（配置）接口 | 备注 |
|---|---|---|---|
| BasicParam | `GET /device/deviceconfig/1` | `POST /device/setdeviceconfig`（configType=4，CameraConfig）；Name 字段可用 `PUT /device/camera/name/v1.0` | ✓ 完全支持 |
| VideoParamOpt | `POST /device/sdc-capability`（capabilityType=1，返回 H264/H265/MJPEG 各编码格式支持的分辨率列表、帧率范围、码率范围） | 不适用（GB 标准中 VideoParamOpt 为只读能力查询，无 DeviceConfig 写操作） | ✓ 支持（能力集查询接口）|
| SVACEncodeConfig | 无接口，返回空 `<SVACEncodeConfig/>` | 无接口，静默忽略返回 OK | ✗ |
| SVACDecodeConfig | 无接口，返回空 `<SVACDecodeConfig/>` | 无接口，静默忽略返回 OK | ✗ |
| VideoParamAttribute | `GET /device/deviceconfig/5` | `POST /device/setdeviceconfig`（configType=5，CameraStreamConfig） | ✓ 完全支持（当前每条码流的编码格式/分辨率/帧率/码率，与 GB 字段一一对应） |
| VideoRecordPlan | 无接口，返回空 `<VideoRecordPlan/>` | 无接口，静默忽略返回 OK | ✗ |
| VideoAlarmRecord | 无接口，返回空 `<VideoAlarmRecord/>` | 无接口，静默忽略返回 OK | ✗（configType=10 是遮挡告警检测，非报警录像配置） |
| PictureMask | `GET /device/deviceconfig/11` | `POST /device/setdeviceconfig`（configType=11，VideoMask） | ✓ 完全支持（隐私遮挡区域） |
| FrameMirror | `GET /device/deviceconfig/5`（`frameMirrorMode` 字段） | `POST /device/setdeviceconfig`（configType=5，`frameMirrorMode` 字段） | ✓ 部分支持（借用 CameraStreamConfig 接口的字段） |
| AlarmReport | 从内存读取本系统维护的报警上报配置，返回 `<AlarmReport>` | 将配置写入内存（本系统自行管理，不调 ivs1900 接口） | 内存管理 |
| OSDConfig | `GET /device/deviceconfig/8` | `POST /device/setdeviceconfig`（configType=8，CameraOSDConfig） | ✓ 完全支持 |
| SnapShotConfig | 无接口，返回空 `<SnapShotConfig/>` | 无接口，静默忽略返回 OK | ✗（`/snapshot/manualsnapshot` 是触发抓拍，不是参数配置接口） |

**理由**：协议一致性优先——对端发来的任何 ConfigType 都应收到合法的 GB/T 28181 响应而非 501；ivs1900 无对应接口的 ConfigType 返回合理的空结构体，不影响对端协议交互。

### 决策 4：外域设备路由依赖 remote_device.interconnect_config_id

从 `remote_device` 表取 `interconnect_config_id`，再从 `interconnect_config` 取对端 SIP 地址（`remote_ip`、`remote_port`、`remote_sip_id`）构造转发目标。

## Risks / Trade-offs

- **外域设备应答透传时序**：转发 MESSAGE 后需等待对端 200 OK 及可能的应答 MESSAGE，若对端无响应会超时（设 5s）→ 超时后回复原始请求方 `408 Request Timeout`
- **ivs1900 字段映射不完整**：多数 ConfigType 在 ivs1900 无对应接口 → ConfigDownload 应答中对应字段省略（空元素），DeviceConfig 写操作静默忽略，记录 debug 日志
- **并发请求**：多个对端同时发来命令，Call-ID 关联透传用 ConcurrentHashMap 维护等待队列，超时后自动清理
