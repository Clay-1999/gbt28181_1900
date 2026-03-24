# GB/T 28181 网关平台 REST API 文档

Base URL: `http://<host>:<port>`

所有请求/响应均为 `application/json`，字符集 UTF-8。

---

## 一、本端 SIP 配置 `/api/local-config`

### GET /api/local-config

获取本端 SIP 配置及当前 SIP 栈状态。

**响应 200**

```json
{
  "deviceId": "34020000001320000001",
  "domain": "3402000000",
  "sipIp": "192.168.1.100",
  "sipPort": 5060,
  "transport": "UDP",
  "password": "***",
  "expires": 3600,
  "status": "RUNNING",
  "errorMsg": null
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceId | string | 本端国标设备 ID（20 位） |
| domain | string | 本端 SIP 域 |
| sipIp | string | 本端监听 IP |
| sipPort | integer | 本端监听端口 |
| transport | string | 传输协议：`UDP` / `TCP` |
| password | string | 固定返回 `***` |
| expires | integer | 注册有效期（秒） |
| status | string | SIP 栈状态：`STOPPED` / `STARTING` / `RUNNING` / `RELOADING` / `ERROR` |
| errorMsg | string | 错误信息，无错误时为 `null` |

---

### PUT /api/local-config

更新本端 SIP 配置，触发 SIP 栈异步重载。

**请求体**

```json
{
  "deviceId": "34020000001320000001",
  "domain": "3402000000",
  "sipIp": "192.168.1.100",
  "sipPort": 5060,
  "transport": "UDP",
  "password": "your_password",
  "expires": 3600
}
```

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| deviceId | string | 是 | 20 位国标编码 |
| domain | string | 是 | 非空 |
| sipIp | string | 是 | 非空 |
| sipPort | integer | 是 | 1–65535 |
| transport | string | 是 | `UDP` 或 `TCP` |
| password | string | 是 | 非空 |
| expires | integer | 是 | ≥ 60 |

**响应**

| 状态码 | 说明 |
|--------|------|
| 202 | 已接受，SIP 栈正在重载 |
| 400 | 请求参数校验失败 |
| 409 | SIP 栈正在重载中，请稍后重试 |

409 响应体：
```json
{ "message": "SIP Stack 正在重载，请稍后再试" }
```

---

### GET /api/local-config/status

获取当前 SIP 栈运行状态。

**响应 200**

```json
{
  "status": "RUNNING",
  "errorMsg": ""
}
```

---

## 二、互联平台配置 `/api/interconnects`

### GET /api/interconnects

获取所有互联平台配置列表。

**响应 200**

```json
[
  {
    "id": 1,
    "name": "上级平台A",
    "remoteSipId": "34020000002000000001",
    "remoteIp": "10.0.0.1",
    "remotePort": 5060,
    "remoteDomain": "3402000000",
    "password": "***",
    "enabled": true,
    "upLinkEnabled": true,
    "upLinkStatus": "ONLINE",
    "downLinkStatus": "ONLINE",
    "lastHeartbeatAt": "2026-03-18T10:00:00Z",
    "createdAt": "2026-01-01T00:00:00"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | integer | 主键 |
| name | string | 平台名称 |
| remoteSipId | string | 对端 SIP ID |
| remoteIp | string | 对端 IP |
| remotePort | integer | 对端 SIP 端口 |
| remoteDomain | string | 对端 SIP 域 |
| password | string | 固定返回 `***` |
| enabled | boolean | 是否启用 |
| upLinkEnabled | boolean | 是否启用上联注册 |
| upLinkStatus | string | 上联状态：`ONLINE` / `OFFLINE` |
| downLinkStatus | string | 下联状态：`ONLINE` / `OFFLINE` |
| lastHeartbeatAt | string | 最后心跳时间（ISO 8601） |
| createdAt | string | 创建时间 |

---

### GET /api/interconnects/{id}

获取单个互联平台配置。

**路径参数**：`id` — 平台 ID

**响应**：200 同上单条对象；404 未找到

---

### POST /api/interconnects

新建互联平台配置。

**请求体**

```json
{
  "name": "上级平台A",
  "remoteSipId": "34020000002000000001",
  "remoteIp": "10.0.0.1",
  "remotePort": 5060,
  "remoteDomain": "3402000000",
  "password": "your_password",
  "enabled": true,
  "upLinkEnabled": false
}
```

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| name | string | 是 | 非空 |
| remoteSipId | string | 是 | 非空 |
| remoteIp | string | 是 | 非空 |
| remotePort | integer | 是 | 1–65535 |
| remoteDomain | string | 是 | 非空 |
| password | string | 是 | 非空 |
| enabled | boolean | 是 | — |
| upLinkEnabled | boolean | 否 | 默认 false |

**响应**：201 返回创建的对象；400 参数校验失败

---

### PUT /api/interconnects/{id}

更新互联平台配置。请求体同 POST。

**响应**：200 返回更新后对象；400 参数校验失败；404 未找到

---

### DELETE /api/interconnects/{id}

删除互联平台配置。

**响应**：204 成功；404 未找到

---

## 三、设备管理 `/api/devices`

### GET /api/devices/local

获取本端设备（IVS1900 相机映射）列表。

**响应 200**

```json
[
  {
    "id": 1,
    "gbDeviceId": "34020000001310000001",
    "name": "前门摄像头",
    "status": "ONLINE",
    "syncedAt": "2026-03-18T09:30:00"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | integer | 主键 |
| gbDeviceId | string | 国标设备 ID |
| name | string | 相机名称 |
| status | string | `ONLINE` / `OFFLINE` |
| syncedAt | string | 最后同步时间 |

---

### GET /api/devices/remote

获取外域设备（通过互联平台同步的设备）列表。

**响应 200**

```json
[
  {
    "deviceId": "34020000001310000099",
    "name": "外域相机01",
    "status": "ON",
    "interconnectName": "上级平台A",
    "syncedAt": "2026-03-18T09:00:00"
  }
]
```

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceId | string | 国标设备 ID |
| name | string | 设备名称 |
| status | string | `ON` / `OFF` |
| interconnectName | string | 所属互联平台名称 |
| syncedAt | string | 最后同步时间 |

---

## 四、本端设备配置 `/api/devices/local/{gbDeviceId}/config`

本端配置通过 IVS1900 HTTP 接口读写，采用读-改-写模式（PUT 时先 GET 当前值再合并 patch 下发）。

### GET /api/devices/local/{gbDeviceId}/config/video-param

查询视频参数配置。

**响应 200**

```json
{
  "streamInfoList": [
    {
      "streamType": 1,
      "encodeType": 1,
      "resolution": "1920x1080",
      "frameRate": 25,
      "bitRate": 2048
    },
    {
      "streamType": 2,
      "encodeType": 1,
      "resolution": "640x480",
      "frameRate": 15,
      "bitRate": 512
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| streamType | integer | 码流类型：1=主码流，2=辅码流 |
| encodeType | integer | 编码格式：0=H.265，1=H.264，2=MJPEG |
| resolution | string | 分辨率，如 `1920x1080` |
| frameRate | integer | 帧率（fps） |
| bitRate | integer | 码率（kbps） |

**响应**：200 成功；404 设备不存在；200 空对象 `{}` 当 IVS1900 不可达

---

### PUT /api/devices/local/{gbDeviceId}/config/video-param

修改视频参数配置。

**请求体**：同 GET 响应结构，仅需包含要修改的字段。

**响应 200**

```json
{ "success": true }
```

---

### GET /api/devices/local/{gbDeviceId}/config/osd

查询 OSD 叠加配置。

**响应 200**

```json
{
  "enableOSD": 1,
  "osdTime": {
    "enableOSDTime": 1
  },
  "osdFontSize": 3
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| enableOSD | integer | OSD 总开关：0=关，1=开 |
| osdTime.enableOSDTime | integer | 时间显示：0=关，1=开 |
| osdFontSize | integer | 字体大小（1–10） |

---

### PUT /api/devices/local/{gbDeviceId}/config/osd

修改 OSD 配置。请求体同 GET 响应结构。

**响应 200**：`{ "success": true }`

---

### GET /api/devices/local/{gbDeviceId}/config/picture-mask

查询视频遮挡配置。

**响应 200**

```json
{ "enableVideoMask": 0 }
```

| 字段 | 类型 | 说明 |
|------|------|------|
| enableVideoMask | integer | 遮挡开关：0=关，1=开 |

---

### PUT /api/devices/local/{gbDeviceId}/config/picture-mask

修改视频遮挡配置。

**请求体**：`{ "enableVideoMask": 1 }`

**响应 200**：`{ "success": true }`

---

### GET /api/devices/local/{gbDeviceId}/config/frame-mirror

查询镜像翻转配置。

**响应 200**

```json
{
  "streamInfoList": [
    { "streamType": 1, "frameMirrorMode": 0 }
  ]
}
```

| frameMirrorMode | 说明 |
|-----------------|------|
| 0 | 不翻转 |
| 1 | 水平翻转 |
| 2 | 垂直翻转 |
| 3 | 水平+垂直翻转 |

---

### PUT /api/devices/local/{gbDeviceId}/config/frame-mirror

修改镜像翻转配置。

**请求体**：`{ "frameMirrorMode": 1 }`

**响应 200**：`{ "success": true }`

---

## 五、外域设备配置 `/api/devices/remote/{deviceId}/config`

外域配置通过 GB/T 28181 SIP MESSAGE 协议与对端平台交互（ConfigDownload 查询 / DeviceConfig 下发），同步等待响应，超时 10 秒返回 504。

### GET /api/devices/remote/{deviceId}/config/video-param

查询外域设备视频参数配置（发送 ConfigDownload SIP MESSAGE）。

**响应 200**：结构与本端相同，字段由对端平台返回的 XML 决定。

**响应**

| 状态码 | 说明 |
|--------|------|
| 200 | 成功，返回配置 Map |
| 404 | 设备不存在 |
| 504 | SIP 响应超时（10s） |

504 响应体：`{ "error": "SIP 响应超时" }`

---

### PUT /api/devices/remote/{deviceId}/config/video-param

下发外域设备视频参数配置（发送 DeviceConfig SIP MESSAGE）。

**请求体**：包含要修改的字段，如 `{ "streamInfoList": [...] }`

**响应**

| 状态码 | 说明 |
|--------|------|
| 200 | `{ "success": true/false }` |
| 504 | SIP 响应超时 |

---

### GET /api/devices/remote/{deviceId}/config/osd

查询外域设备 OSD 配置。响应/错误码同上。

---

### PUT /api/devices/remote/{deviceId}/config/osd

下发外域设备 OSD 配置。请求/响应同上。

---

### GET /api/devices/remote/{deviceId}/config/picture-mask

查询外域设备视频遮挡配置。

---

### PUT /api/devices/remote/{deviceId}/config/picture-mask

下发外域设备视频遮挡配置。

---

### GET /api/devices/remote/{deviceId}/config/frame-mirror

查询外域设备镜像翻转配置。

---

### PUT /api/devices/remote/{deviceId}/config/frame-mirror

下发外域设备镜像翻转配置。

---

## 通用错误响应

| 状态码 | 场景 |
|--------|------|
| 400 | 请求参数校验失败 |
| 404 | 资源不存在 |
| 409 | 操作冲突（如 SIP 栈重载中） |
| 504 | 外域 SIP 响应超时 |

404 响应体示例：`{ "error": "Device not found: 34020000001310000001" }`
