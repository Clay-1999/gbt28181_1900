# IVS1900 REST API 接口分析（GB/T 28181 互联依赖）

> 基于 `doc/api/videoDevice.yaml` 和 `doc/api/UserManager.yaml` 的完整分析
>
> 生成时间：2026-03-16

---

## 1. 认证与会话管理

### 1.1 登录接口
**路径**: `POST /loginInfo/login/v1.0`
**来源**: `UserManager.yaml`

**请求体**:
```json
{
  "userName": "admin",      // 5-20字符，字母数字下划线
  "password": "Admin@123"   // 8-16字符，大小写字母+数字+特殊字符至少两种
}
```

**响应**:
```json
{
  "resultCode": 0           // 0=成功
}
```

**认证方式**:
- 登录成功后，服务端返回 `Set-Cookie: JSESSIONID=xxx`
- 后续所有请求必须在 Header 中携带 `Cookie: JSESSIONID=xxx`

---

### 1.2 保活接口
**路径**: `GET /common/keepAlive`
**来源**: `UserManager.yaml`

**请求头**:
```
Cookie: JSESSIONID=<44-80字符>
```

**响应**:
```json
{
  "resultCode": 0
}
```

**说明**: Session 有超时机制，需定期调用保活（建议间隔 5 分钟）

---

### 1.3 登出接口
**路径**: `GET /users/logout`
**来源**: `UserManager.yaml`

**请求头**:
```
Cookie: JSESSIONID=<session-id>
```

**响应**:
```json
{
  "resultCode": 0
}
```

---

## 2. 相机列表查询

### 2.1 获取摄像机子设备列表
**路径**: `GET /device/deviceList/v1.0`
**来源**: `videoDevice.yaml`

**请求参数**（Query）:
| 参数 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| `deviceType` | int32 | ✓ | 2=摄像机, 4=告警设备 | 2 |
| `fromIndex` | int32 | ✓ | 起始索引（从1开始） | 1 |
| `toIndex` | int32 | ✓ | 结束索引 | 1000 |

**响应** (`subListRsp`):
```json
{
  "resultCode": 0,
  "cameraBriefInfos": {
    "cameraBriefInfoList": [
      {
        "code": "08721893516000010101",           // 镜头编码（20位纯数字，以0101结尾）
        "name": "Camera_01",                      // 镜头名称
        "cameraStatus": 1,                        // 在线状态（1=在线，其他=离线）
        "status": 1,                              // 摄像机上线状态
        "type": 2,                                // 摄像机类型
        "domainCode": "c7fdc4e393dfa6d31e0c9bb964c4ae7d",  // NVR域编码（32字节）
        "nvrCode": "c7fdc4e393dfa6d31e0c9bb964c4ae7d",     // NVR域编码
        "deviceIP": "192.168.1.100",              // 摄像机IP
        "deviceModelType": "M6741-10-Z40-E2",     // 型号
        "parentCode": "08721893516000010000",     // 主设备编码
        "connectCode": "08721893516000010101",    // 互联编码
        "enableVoice": 0,                         // 是否启用随路语音（0=否，1=是）
        "isSupportIntelligent": 0,                // 是否支持智能（0=否，1=是）
        "vendorType": "huawei",                   // 厂商类型
        "reserve": ""                             // 扩展字段
      }
    ],
    "indexRange": {
      "fromIndex": 1,
      "toIndex": 16
    },
    "total": 14                                   // 本次查询返回总数
  }
}
```

**关键字段说明**:
- `code`: 即 `cameraCode`，用于后续所有操作（PTZ、参数配置等）
- `domainCode`: NVR 域编码，部分接口需要
- `cameraStatus`: 1=在线，其他值=离线（但建议用 `channelDevInfo` 接口获取更准确的在线状态）

---

### 2.2 查询主设备下的子设备列表
**路径**: `POST /device/subDeviceChannelList`
**来源**: `videoDevice.yaml`

**请求体**:
```json
{
  "devCode": "08721893516000010000",    // 主设备编码（20位）
  "fromIndex": 1,
  "toIndex": 20,
  "allChannel": "false"                 // "false"=仅在线通道，"true"=所有通道
}
```

**响应** (`subDevRsp`):
```json
{
  "resultCode": 0,
  "devChannelBriefInfos": [
    {
      "channelCode": "08721893516000010101",    // 通道编码（20位）
      "channelName": "Camera_01",               // 通道名称
      "channelType": 1,                         // 1=摄像头, 2=告警输入, 3=告警输出, 4=音频输入, 5=音频输出
      "channelEnable": 1,                       // 0=不可用, 1=可用, 2=不支持
      "reserve": ""
    }
  ],
  "fromIndex": 1,
  "toIndex": 20,
  "totalNum": 9
}
```

---

## 3. 相机在线状态

### 3.1 查询视频通道状态列表
**路径**: `GET /device/channelDevInfo`
**来源**: `videoDevice.yaml`

**请求参数**: 无（获取所有通道状态）

**响应** (`channelInfoRsp`):
```json
{
  "resultCode": 0,
  "maxChannelNum": "8",                         // NVR最大通道数
  "channelDevList": {
    "channelDevInfo": [
      {
        "cameraCode": "08721893516000010101",   // 摄像机编码
        "channel": 1,                           // 视频通道序号（从1开始）
        "isOnline": "true",                     // 是否在线（字符串 "true"/"false"）
        "isEnable": "true"                      // 是否已添加接入（字符串 "true"/"false"）
      }
    ]
  }
}
```

**关键说明**:
- `isOnline` 是**字符串类型**，不是布尔值
- 需要判断 `isOnline === "true"` 而不是 `isOnline === true`
- 这是获取在线状态的**最准确**接口

---

## 4. PTZ 控制

### 4.1 云台控制操作
**路径**: `POST /device/ptzcontrol`
**来源**: `videoDevice.yaml`

**请求体** (`PtzControlReq`):
```json
{
  "cameraCode": "08721893516000010101",    // 镜头编码（20位）
  "controlCode": 1,                        // 控制码（1-40）
  "controlPara1": "1",                     // 控制参数1（字符串）
  "controlPara2": "1"                      // 控制参数2（字符串）
}
```

**controlCode 完整枚举表**:

| controlCode | 说明 | controlPara1 | controlPara2 | GB/T 28181 映射 |
|-------------|------|--------------|--------------|----------------|
| **1** | 云台向上 | 1=点动, 2=连续, 3=停止 | 步长(1-10) | PTZCmd=0x08, 速度=para2 |
| **2** | 云台向下 | 1=点动, 2=连续, 3=停止 | 步长(1-10) | PTZCmd=0x04, 速度=para2 |
| **3** | 云台向左 | 1=点动, 2=连续, 3=停止 | 步长(1-10) | PTZCmd=0x02, 速度=para2 |
| **4** | 云台向右 | 1=点动, 2=连续, 3=停止 | 步长(1-10) | PTZCmd=0x01, 速度=para2 |
| **5** | 云台左上 | 1=点动, 2=连续, 3=停止 | 步长(1-10) | PTZCmd=0x0A |
| **6** | 云台右上 | 1=点动, 2=连续, 3=停止 | 步长(1-10) | PTZCmd=0x09 |
| **7** | 云台左下 | 1=点动, 2=连续, 3=停止 | 步长(1-10) | PTZCmd=0x06 |
| **8** | 云台右下 | 1=点动, 2=连续, 3=停止 | 步长(1-10) | PTZCmd=0x05 |
| **9** | 云台停止 | - | - | PTZCmd=0x00 |
| **11** | 转到预置位 | 预置位序号(1-255) | 速度挡位(0-10, 0=最大速度) | PTZCmd=0x81 + PresetID |
| **12** | 巡航路线 | 巡航路线序号 | - | PTZCmd=0x82 + CruiseID |
| **14** | 模式路线开始 | 模式路线序号 | - | - |
| **15** | 模式路线停止 | 模式路线序号 | - | - |
| **21** | 光圈增大 | - | 速度(1-10) | PTZCmd=0x44 |
| **22** | 光圈减小 | - | 速度(1-10) | PTZCmd=0x48 |
| **23** | 聚焦增（远） | 2=连续, 3=关闭 | 速度(1-10) | PTZCmd=0x42 |
| **24** | 聚焦减（近） | 2=连续, 3=关闭 | 速度(1-10) | PTZCmd=0x41 |
| **25** | 变倍增（放大） | 2=连续, 3=关闭 | 速度(1-10) | PTZCmd=0x10 |
| **26** | 变倍减（缩小） | 2=连续, 3=关闭 | 速度(1-10) | PTZCmd=0x20 |
| **27** | 雨刷控制 | 1=雨刷 | - | - |
| **28** | 灯光控制 | 2=灯光 | - | - |
| **31** | 框选区域 | 左上角坐标(x,y) | 右下角坐标(x,y) | - |
| **36** | 坐标控制 | 坐标(x,y,z) | 坐标(x,y,z) | - |
| **37** | 方向控制 | UP/DOWN/LEFT/RIGHT/... | 速度(vx,vy) | - |

**响应**:
```json
{
  "resultCode": 0    // 0=成功
}
```

**GB/T 28181 PTZ 命令映射示例**:
```xml
<!-- 国标 PTZ 命令（SIP MESSAGE Body） -->
<?xml version="1.0" encoding="GB2312"?>
<Control>
  <CmdType>DeviceControl</CmdType>
  <SN>123</SN>
  <DeviceID>34020000001320000001</DeviceID>
  <PTZCmd>A50F0001</PTZCmd>  <!-- 向上，速度1 -->
</Control>

<!-- 映射到 IVS1900 -->
POST /device/ptzcontrol
{
  "cameraCode": "08721893516000010101",
  "controlCode": 1,        // 向上
  "controlPara1": "2",     // 连续
  "controlPara2": "1"      // 速度1
}
```

---

### 4.2 查询云台当前信息
**路径**: `GET /ptz/currentInfo/v1.0`
**来源**: `videoDevice.yaml`

**请求参数**（Query）:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `cameraCode` | string | ✓ | 摄像机编码 |
| `domainCode` | string | ✓ | 域编码 |

**响应** (`PtzGetCurrentInfoRsp`):
```json
{
  "resultCode": 0,
  "ptzCurrentInfo": {
    "cameraCode": "08721893516000010101",
    "position": {
      "dX": 353.200012,                  // 水平角度（度）
      "dY": -17.16,                      // 垂直角度（度）
      "dZ": 1.31                         // 变倍数
    },
    "ptzRange": {
      "dXMin": 0.0,                      // 水平角度范围
      "dXMax": 360.0,
      "dYMin": -10.0,                    // 垂直角度范围
      "dYMax": 90.0,
      "dZMin": 1.0,                      // 变倍范围
      "dZMax": 240.0,
      "dVXMin": 1.0,                     // 水平速度范围
      "dVXMax": 10.0,
      "dVYMin": 1.0,                     // 垂直速度范围
      "dVYMax": 10.0,
      "dVZMin": 1.0,                     // 变倍速度范围
      "dVZMax": 10.0
    }
  }
}
```

---

### 4.3 预置位管理

#### 4.3.1 查询预置位列表
**路径**: `GET /device/ptzpresetlist/{cameracode}/{nvrcode}`
**来源**: `videoDevice.yaml`

**路径参数**:
- `cameracode`: 摄像机编码（20位）
- `nvrcode`: NVR编码（当前未使用，可传空字符串）

**响应**:
```json
{
  "resultCode": 0,
  "ptzPresetNum": 3,                     // 预置位总数
  "ptzPresetInfoList": {
    "ptzPresetInfo": [
      {
        "presetIndex": 1,                // 预置位索引（1-255）
        "presetName": "大门"             // 预置位名称
      }
    ]
  }
}
```

#### 4.3.2 添加预置位
**路径**: `POST /ptz/presetposition/v1.0`
**来源**: `videoDevice.yaml`

**请求体**:
```json
{
  "cameraCode": "08721893516000010101",
  "domainCode": "c7fdc4e393dfa6d31e0c9bb964c4ae7d",
  "presetName": "大门"
}
```

**响应**:
```json
{
  "resultCode": 0,
  "presetIndex": 1                       // 新增的预置位索引
}
```

#### 4.3.3 删除预置位
**路径**: `DELETE /ptz/presetposition/{cameracode}/{domaincode}/{presetindex}/v1.0`

**路径参数**:
- `cameracode`: 摄像机编码（20位）
- `domaincode`: 域编码（32字节）
- `presetindex`: 预置位索引（1-255）

**响应**:
```json
{
  "resultCode": 0
}
```

---

## 5. 实时流地址

**重要说明**: videoDevice.yaml 中**未找到专门的获取 RTSP 流 URL 接口**。

### 5.1 可能的获取方式

#### 方式 A：从主设备信息中提取
**路径**: `GET /device/masterDeviceList/v1.1`

**响应中可能包含**:
```json
{
  "deviceBriefInfos": {
    "deviceBriefInfo": [{
      "deviceBasicInfo": {
        "rtsp_main_uri": "rtsp://192.168.1.100:554/main",
        "rtsp_sub_uri": "rtsp://192.168.1.100:554/sub",
        "rtsp_port": 554,
        "auth_type": "digest",
        "protocol_type": "RTSP"
      }
    }]
  }
}
```

#### 方式 B：手动构造 RTSP URL
根据 IVS1900 的常见规则：
```
主码流: rtsp://[username]:[password]@[camera_ip]:[port]/main
子码流: rtsp://[username]:[password]@[camera_ip]:[port]/sub
```

**需要的信息**:
- `camera_ip`: 从 `deviceList` 接口的 `deviceIP` 字段获取
- `username/password`: 摄像机的登录凭据（非 IVS1900 的凭据）
- `port`: 默认 554

---

## 6. 录像相关

**重要说明**: videoDevice.yaml 中**未找到以下接口**：
- 手动录像开始/停止
- 历史录像查询（时间段）
- 录像回放 URL 获取

### 6.1 仅找到的相关接口

#### 手动抓拍（快照）
**路径**: `POST /snapshot/manualsnapshot`
**来源**: `videoDevice.yaml`

**请求体**:
```json
{
  "cameraCode": "08721893516000010101"
}
```

**响应**:
- 类型: `application/octet-stream`
- 内容: 图片文件流（JPEG）

**说明**: 仅支持 HWSDK 协议接入的摄像机

---

## 7. 设备参数查询/配置

### 7.1 批量获取镜头分辨率和智能属性
**路径**: `POST /device/camera/batchconfig/v1.0`
**来源**: `videoDevice.yaml`

**请求体**:
```json
{
  "cameraCodeList": [
    "08721893516000010101",
    "08721893516000020101"
  ]
}
```

**响应**:
```json
{
  "resultCode": 0,
  "cameraCfgInfoList": [
    {
      "cameraCode": "08721893516000010101",
      "frameRate": 25,                   // 帧率
      "imgType": 0,                      // 智能属性类型（0=普通）
      "isComposedImg": 0,                // 是否支持合成图
      "isWithCarCoord": 0,               // 是否带车辆坐标
      "streamInfoList": [
        {
          "resolution": "2304*1296"      // 分辨率
        }
      ]
    }
  ]
}
```

### 7.2 修改镜头名称
**路径**: `PUT /device/camera/name/v1.0`
**来源**: `videoDevice.yaml`

**请求体**:
```json
{
  "cameraCode": "08721893516000010101",
  "cameraName": "新名称"
}
```

**响应**:
```json
{
  "resultCode": 0
}
```

---

## 8. 关键数据类型说明

| 字段名 | 类型 | 格式 | 说明 |
|--------|------|------|------|
| `cameraCode` | string | 20位纯数字 | 镜头编码，以0101结尾，如 "08721893516000010101" |
| `domainCode` | string | 32字节 | NVR域编码，小写字母+数字，如 "c7fdc4e393dfa6d31e0c9bb964c4ae7d" |
| `deviceCode` | string | 20位纯数字 | 主设备编码，以0000结尾，如 "08721893516000010000" |
| `isOnline` | string | "true"/"false" | **字符串类型**，不是布尔值 |
| `cameraStatus` | int32 | 1/其他 | 1=在线，其他值=离线 |
| `resultCode` | int32 | 0/其他 | 0=成功，其他值=失败 |
| `JSESSIONID` | string | 44-80字符 | Session ID，登录后从 Cookie 中获取 |

---

## 9. 错误码参考

| resultCode | 说明 |
|------------|------|
| 0 | 成功 |
| 1 | 通用失败 |
| 401 | 未授权（Session 过期或无效） |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 10. GB/T 28181 互联所需接口清单

### 必需接口（Phase 5 核心）

| 功能 | IVS1900 接口 | 调用频率 |
|------|-------------|---------|
| 登录 | `POST /loginInfo/login/v1.0` | 启动时 + 断线重连 |
| 保活 | `GET /common/keepAlive` | 每 5 分钟 |
| 相机列表 | `GET /device/deviceList/v1.0?deviceType=2` | 每 60 秒 |
| 在线状态 | `GET /device/channelDevInfo` | 每 60 秒 |
| PTZ 控制 | `POST /device/ptzcontrol` | 按需（对端发起） |

### 可选接口（Phase 6 扩展）

| 功能 | IVS1900 接口 | 说明 |
|------|-------------|------|
| 预置位查询 | `GET /device/ptzpresetlist/{cameracode}/{nvrcode}` | 设备目录扩展信息 |
| 分辨率查询 | `POST /device/camera/batchconfig/v1.0` | 设备能力集 |
| 快照 | `POST /snapshot/manualsnapshot` | 图片抓拍 |

### 缺失接口（需其他方案）

| 功能 | 状态 | 替代方案 |
|------|------|---------|
| 实时流 URL | 未找到 | 手动构造 RTSP URL 或查询其他文档 |
| 录像控制 | 未找到 | 可能通过 SIP 协议实现 |
| 历史录像查询 | 未找到 | 可能通过 SIP 协议实现 |

---

## 11. 典型调用流程

### 11.1 启动时初始化
```
1. POST /loginInfo/login/v1.0
   → 获取 JSESSIONID

2. GET /device/deviceList/v1.0?deviceType=2&fromIndex=1&toIndex=1000
   → 获取所有摄像机列表

3. GET /device/channelDevInfo
   → 获取所有通道在线状态

4. 生成国标设备 ID，写入 ivs1900_camera_mapping 表
```

### 11.2 定时同步（每 60 秒）
```
1. GET /common/keepAlive
   → 保持 Session 有效

2. GET /device/deviceList/v1.0?deviceType=2&fromIndex=1&toIndex=1000
   → 更新相机列表

3. GET /device/channelDevInfo
   → 更新在线状态

4. 更新 ivs1900_camera_mapping 表的 status 和 syncedAt 字段
```

### 11.3 PTZ 控制（对端发起）
```
1. 对端发送 SIP MESSAGE（GB/T 28181 PTZ 命令）
   ↓
2. 解析 PTZCmd 字节，映射到 IVS1900 controlCode
   ↓
3. 通过 gb_device_id 查询 ivs1900_camera_mapping，得到 ivs_camera_id
   ↓
4. POST /device/ptzcontrol
   {
     "cameraCode": "<ivs_camera_id>",
     "controlCode": <mapped_code>,
     "controlPara1": "<para1>",
     "controlPara2": "<para2>"
   }
   ↓
5. 返回 200 OK 给对端
```

---

## 12. 注意事项

1. **Session 管理**
   - JSESSIONID 有超时机制，需定时保活
   - 建议保活间隔：5 分钟
   - 登录失败或 Session 过期时，需重新登录

2. **在线状态判断**
   - 优先使用 `GET /device/channelDevInfo` 的 `isOnline` 字段
   - 注意 `isOnline` 是字符串 `"true"`/`"false"`，不是布尔值
   - `deviceList` 接口的 `cameraStatus` 可作为辅助判断

3. **PTZ 控制**
   - 仅支持球机类型摄像机
   - controlCode 9 = 停止，所有方向控制后需发送停止命令
   - 速度参数范围：1-10

4. **错误处理**
   - resultCode != 0 时，检查 Session 是否有效
   - 401 错误：重新登录
   - 404 错误：cameraCode 不存在或已下线

5. **性能优化**
   - 相机列表和在线状态查询可合并到一个定时任务
   - 使用批量接口（如 `batchconfig`）减少请求次数
   - Session 保活可与同步任务合并

---

## 13. 文档来源

- `doc/api/videoDevice.yaml` - 设备管理、PTZ 控制、参数配置
- `doc/api/UserManager.yaml` - 登录、保活、登出

**生成工具**: Claude Code Agent (Explore)
**分析时间**: 2026-03-16
