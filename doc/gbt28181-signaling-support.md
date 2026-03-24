 # GB/T 28181 信令支持清单

> 更新日期：2026-03-23

---

## 一、本端设备（IVS1900）支持的信令

本端设备指本平台南向接入的 IVS1900 NVR，通过 SIP 与平台互联。

| 信令 | SIP 方法 | CmdType | 方向 | 说明 |
|------|----------|---------|------|------|
| 设备注册 | REGISTER | — | IVS1900 → 平台 | Digest 认证；支持注销（Expires=0） |
| 心跳保活 | MESSAGE | Keepalive | IVS1900 → 平台 | 180 s 无心跳则下线 |
| 目录查询 | MESSAGE | Catalog（Query） | 平台 → IVS1900 | 注册成功后自动触发；结果写入 `ivs1900_camera_mapping` |
| 目录订阅 | SUBSCRIBE | Catalog | 平台 → IVS1900 | 注册成功后发起；body 含 `<Query><CmdType>Catalog</CmdType><SN>...</SN><DeviceID>...</DeviceID></Query>`；到期前 60s 续订 |
| 目录订阅通知 | NOTIFY | Catalog | IVS1900 → 平台 | 相机状态变更时推送；解析写入 `ivs1900_camera_mapping` |
| 目录应答 | MESSAGE | Catalog（Response） | IVS1900 → 平台 | 解析设备列表，同步 GbDeviceId、PTZ 类型 |
| 配置下载 | MESSAGE | ConfigDownload | 平台 → IVS1900 | 查询 BasicParam / AlarmReport 等配置 |
| 配置下载应答 | MESSAGE | ConfigDownload（Response） | IVS1900 → 平台 | 返回设备基本参数 |
| 参数配置 | MESSAGE | DeviceConfig | 平台 → IVS1900 | 设置 AlarmReport 等参数 |
| 参数配置应答 | MESSAGE | DeviceConfig（Response） | IVS1900 → 平台 | 返回 Result=OK |
| 视频点播 | INVITE/ACK/BYE | — | 平台 → IVS1900 | SDP offer（recvonly），ZLMediaKit 收流；Subject 含 SSRC |
| 停止点播 | BYE | — | 平台 → IVS1900 | 释放 ZLMediaKit RTP 端口 |
| PTZ 控制 | MESSAGE | DeviceControl | 平台 → IVS1900 | 方向/变倍/变焦/光圈；PTZCmd 16 进制编码 |
| 预置位查询 | MESSAGE | PresetQuery | 平台 → IVS1900 | 返回预置位列表（ID + 名称） |
| 预置位调用 | MESSAGE | DeviceControl | 平台 → IVS1900 | PTZCmd 0x82 + 预置位编号 |
| 预置位设置 | MESSAGE | DeviceControl | 平台 → IVS1900 | PTZCmd 0x81 + 预置位编号 + 名称 |
| 预置位删除 | MESSAGE | DeviceControl | 平台 → IVS1900 | PTZCmd 0x83 + 预置位编号 |
| 巡航轨迹列表查询 | MESSAGE | CruiseTrackListQuery | 平台 → IVS1900 | 返回轨迹编号和名称列表 |
| 巡航轨迹详情查询 | MESSAGE | CruiseTrackQuery | 平台 → IVS1900 | 返回轨迹预置位点、停留时间、速度 |
| 启动巡航 | MESSAGE | DeviceControl | 平台 → IVS1900 | PTZCmd 0x88 + CruiseTrackName |
| 停止巡航 | MESSAGE | DeviceControl | 平台 → IVS1900 | PTZCmd 全停（0x00） |
| 录像查询 | MESSAGE | RecordInfo | 平台 → IVS1900 | 按时间范围/类型查询录像列表（10 s 超时） |
| 录像查询应答 | MESSAGE | RecordInfo（Response） | IVS1900 → 平台 | 返回录像文件列表 |

---

## 二、外域设备（远端平台）支持的信令

外域设备指通过互联配置（InterconnectConfig）注册到本平台的下级/对端 SIP 平台或设备。

| 信令 | SIP 方法 | CmdType | 方向 | 说明 |
|------|----------|---------|------|------|
| 设备注册 | REGISTER | — | 外域平台 → 平台 | Digest 认证；设备注册信息写入 `interconnect_config` |
| 心跳保活 | MESSAGE | Keepalive | 外域平台 → 平台 | 更新 `lastHeartbeatAt`；超时下线 |
| 目录查询 | MESSAGE | Catalog（Query） | 平台 → 外域平台 | 注册成功后自动触发；结果写入 `remote_device` |
| 目录订阅 | SUBSCRIBE | Catalog | 平台 → 外域平台 | 注册成功后发起；body 含 `<Query><CmdType>Catalog</CmdType><SN>...</SN><DeviceID>...</DeviceID></Query>`；到期前 60s 续订 |
| 目录订阅通知 | NOTIFY | Catalog | 外域平台 → 平台 | 设备状态变更时推送；解析写入 `remote_device` |
| 目录应答 | MESSAGE | Catalog（Response/Notify） | 外域平台 → 平台 | 解析外域设备列表，upsert `remote_device` |
| 配置下载查询 | MESSAGE | ConfigDownload | 平台 → 外域平台 | 透传 REST 发起的查询；10 s 等待应答 |
| 配置下载应答 | MESSAGE | ConfigDownload（Response） | 外域平台 → 平台 | SN 匹配后返回 REST 调用方 |
| 参数配置 | MESSAGE | DeviceConfig | 平台 → 外域平台 | 透传 REST 发起的配置；10 s 等待结果 |
| 参数配置应答 | MESSAGE | DeviceConfig（Response） | 外域平台 → 平台 | SN 匹配后返回 REST 调用方 |
| 配置查询（上级转发） | MESSAGE | ConfigDownload | 上级 → 平台 → 外域 | 收到上级查询后，转发至外域平台并等待响应回传 |
| 配置下发（上级转发） | MESSAGE | DeviceConfig | 上级 → 平台 → 外域 | 同上 |
| 视频点播 | INVITE/ACK/BYE | — | 平台 → 外域平台 | SDP offer（recvonly），ZLMediaKit 收流 |
| 停止点播 | BYE | — | 平台 → 外域平台 | 释放 ZLMediaKit RTP 端口 |
| 对端挂断 | BYE | — | 外域平台 → 平台 | 清理 StreamSession |
| PTZ 控制 | MESSAGE | DeviceControl | 平台 → 外域平台 | 方向/变倍/变焦/光圈 |
| PTZ 控制（转发） | MESSAGE | DeviceControl | 上级 → 平台 → 外域 | 上级 PTZ 命令原样转发至外域平台 |
| 预置位查询 | MESSAGE | PresetQuery | 平台 → 外域平台 | REST 触发，返回预置位列表 |
| 预置位查询（转发） | MESSAGE | PresetQuery | 上级 → 平台 → 外域 | 上级查询透传至外域 |
| 预置位调用/设置/删除 | MESSAGE | DeviceControl | 平台 → 外域平台 | PTZCmd 编码操作 |
| 巡航轨迹列表查询 | MESSAGE | CruiseTrackListQuery | 平台 → 外域平台 | REST 触发 |
| 巡航轨迹列表查询（转发） | MESSAGE | CruiseTrackListQuery | 上级 → 平台 → 外域 | 上级查询透传 |
| 巡航轨迹详情查询 | MESSAGE | CruiseTrackQuery | 平台 → 外域平台 | REST 触发 |
| 巡航轨迹详情查询（转发） | MESSAGE | CruiseTrackQuery | 上级 → 平台 → 外域 | 上级查询透传 |
| 启动/停止巡航 | MESSAGE | DeviceControl | 平台 → 外域平台 | PTZCmd 编码 |
| 录像查询 | MESSAGE | RecordInfo | 平台 → 外域平台 | 按时间范围查询，10 s 超时 |
| 录像查询应答 | MESSAGE | RecordInfo（Response） | 外域平台 → 平台 | SN 匹配后返回 REST 调用方 |

---

## 三、上级平台下发的信令（北向）

上级平台指通过 SIP 注册到本平台（本平台作为下级）或直接发送命令的上级 SIP 平台。

| 信令 | SIP 方法 | CmdType | 平台响应 | 说明 |
|------|----------|---------|---------|------|
| 注册（本平台向上级注册） | REGISTER | — | 200 OK / 401 Digest | 本平台主动向上级注册；含 2/3 续约、指数退避、心跳 |
| 心跳（本平台 → 上级） | MESSAGE | Keepalive | — | 60 s 周期；3 次失败触发重新注册 |
| 目录订阅 | SUBSCRIBE | Catalog | 200 OK + NOTIFY | 立即回 NOTIFY，包含所有本端 IVS1900 相机列表 |
| 目录主动推送 | NOTIFY | Catalog | — | 注册成功后主动推送；无需等待 SUBSCRIBE |
| 配置下载查询 | MESSAGE | ConfigDownload | 200 OK + 应答 MESSAGE | 路由至本端 IVS1900 或外域设备（透传） |
| 参数配置下发 | MESSAGE | DeviceConfig | 200 OK + 应答 MESSAGE | 路由至本端 IVS1900（AlarmReport 持久化）或外域（透传） |
| 视频点播（拉本端相机） | INVITE | — | 100 Trying + 200 OK + SDP | ZLMediaKit 收 IVS1900 流后返回 SDP；支持 PS/H264 |
| 停止视频点播 | BYE | — | 200 OK | 停止 ZLMediaKit 流，释放 RTP 端口 |
| PTZ 控制 | MESSAGE | DeviceControl | 200 OK | 路由至本端球机（sendRawPtzCmd）或外域（透传） |
| 预置位查询 | MESSAGE | PresetQuery | 200 OK + 应答 MESSAGE | 查询 IVS1900 预置位列表后回 MESSAGE 给上级 |
| 巡航轨迹列表查询 | MESSAGE | CruiseTrackListQuery | 200 OK + 应答 MESSAGE | 查询 IVS1900 轨迹列表后回 MESSAGE 给上级 |
| 巡航轨迹详情查询 | MESSAGE | CruiseTrackQuery | 200 OK + 应答 MESSAGE | 查询 IVS1900 指定轨迹详情后回 MESSAGE 给上级 |

---

## 四、未支持的信令（已知缺口）

| 信令 | CmdType / 方法 | 原因 |
|------|---------------|------|
| 录像回放 | INVITE（回放 SDP） | 无回放专用 SDP 构造（无 `t=` 时间范围字段） |
| 报警事件通知 | MESSAGE / Alarm | 无入站告警处理器 |
| 语音对讲 | INVITE（双向音频） | 仅支持视频单向推流 |
| 固件升级 / 文件下载 | DownloadFile / UpdateFile | 未实现 |
| 录像文件查询 | FileQuery | 未实现 |
| 预置位查询（外域北向） | PresetQuery | 上级查询外域设备的预置位，当前仅支持本端 IVS1900 |
