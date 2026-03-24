# GB/T 28181-2022 互联系统架构文档

> 本文档描述整个系统的顶层架构，是所有后续实现模块的设计基准。

---

## 1. 系统定位

本系统有两个核心职责：

**① GB/T 28181 互联网关**：实现与对端平台的 GB/T 28181-2022 互联协议，包括双向注册、设备目录同步、设备控制命令转发。

**② ivs1900 协议适配器**：将对端发来的 GB/T 28181 信令翻译为 ivs1900 REST API 调用，使已接入 ivs1900 的本地相机对外呈现为符合国标的设备。

```
对端平台（GB/T 28181）          gbt28181（协议网关）          ivs1900（本地相机管理）
────────────────────────────────────────────────────────────────────────────
SUBSCRIBE/NOTIFY Catalog   ←→   定时同步 + 国标ID映射   ←→   GET /cameras
SIP MESSAGE PTZ            ←→   命令翻译                ←→   POST /cameras/{id}/ptz
SIP MESSAGE 设备参数查询    ←→   命令翻译                ←→   GET  /cameras/{id}/config
SIP MESSAGE 设备参数配置    ←→   命令翻译                ←→   PUT  /cameras/{id}/config
SIP MESSAGE 录像控制        ←→   命令翻译                ←→   POST /cameras/{id}/recording
```

**互联**是两个平台之间的对等关系（Peer-to-Peer）：双方互相注册、双向共享设备目录。这与**级联**（单向上下级）有本质区别：

| 维度 | 级联 | 互联 |
|------|------|------|
| 注册方向 | 下级 → 上级（单向） | 双向对等 |
| 设备目录 | 下级上报给上级 | 双方互相上报 |
| 媒体请求 | 上级向下级请求 | 任意一方发起 |
| 权限关系 | 有层级管控 | 对等，无主从 |

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         浏览器（Vue 3）                          │
│   ┌──────────────┐  ┌──────────────┐  ┌─────────────────────┐  │
│   │  本端配置页   │  │  互联管理页   │  │  设备树 / 预览页    │  │
│   └──────┬───────┘  └──────┬───────┘  └──────────┬──────────┘  │
└──────────┼─────────────────┼───────────────────── ┼────────────┘
           │                 │  REST API (HTTP/JSON) │
           ▼                 ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot 后端                            │
│                                                                  │
│  ┌─────────────────────┐    ┌──────────────────────────────┐    │
│  │    REST API 层       │    │      SIP 信令层               │    │
│  │  LocalSipConfig      │    │  ┌──────────┐ ┌──────────┐  │    │
│  │  InterconnectConfig  │    │  │  SIP      │ │  SIP     │  │    │
│  │  Device              │    │  │  Server   │ │  Client  │  │    │
│  │  Session             │    │  │（接受注册）│ │（发起注册）│  │    │
│  │  Stream              │    │  └────┬─────┘ └────┬─────┘  │    │
│  │  Status              │    │       └──────┬──────┘        │    │
│  └──────────┬──────────┘    │         JAIN-SIP RI           │    │
│             │               └────────────────┬─────────────┘    │
│             │                                │                   │
│  ┌──────────▼──────────────────────────────▼──────────────┐    │
│  │                    业务服务层                             │    │
│  │   SipStackManager  │  DeviceCatalogService  │  MediaProxy│    │
│  │                    │  Ivs1900SyncService    │            │    │
│  └──────────┬────────────────────┬────────────────┬────────┘    │
│             │                    │                │              │
│  ┌──────────▼──────────┐  ┌─────▼──────┐  ┌─────▼──────────┐  │
│  │    持久层（JPA）      │  │   内存注册表│  │  Netty RTP     │  │
│  │  local_sip_config   │  │  （运行时） │  │  端口池 + 代理  │  │
│  │  interconnect_config│  └────────────┘  └────────────────┘  │
│  │  device（对端设备）  │                                        │
│  │  ivs1900_camera_map │                                        │
│  └──────────┬──────────┘                                        │
│             │                                                    │
│         H2 / MySQL          ivs1900 REST API（HTTP）             │
└─────────────────────────────────────┬───────────────────────────┘
           SIP UDP/TCP :5060          │        RTP UDP :10000-20000
                │                    ▼                │
      ┌─────────┴──────────┐  ┌─────────────┐  ┌────┴─────────────┐
      │     对端平台 A      │  │   ivs1900   │  │    对端平台 B    │
      │  (GB/T 28181 兼容) │  │  （本地相机）│  │ (GB/T 28181 兼容)│
      └────────────────────┘  └─────────────┘  └──────────────────┘
```

---

## 3. 模块职责

### 3.1 本端 SIP 配置（`local-sip-config`）

- 持久化存储本端 SIP 参数：设备 ID、SIP 域、监听 IP/端口、传输协议（UDP/TCP）、认证用户名/密码、注册有效期
- 提供 Web UI 修改，保存后触发 SIP Stack 热重载
- 热重载流程：注销所有互联客户端 → 销毁旧 SIP Stack → 检查新端口可用性 → 重建 Stack → 重启所有互联客户端
- 热重载失败时标记 `ERROR` 状态并记录原因，等待用户修正
- Stack 状态：`RUNNING` / `RELOADING` / `ERROR`

### 3.2 互联配置管理（`interconnect-config`）

- 持久化存储每条互联关系：对端平台名称、SIP 地址/端口、对端设备 ID、对端 SIP 域、认证密码、启用开关
- **双向控制开关**：
  - `enabled`：控制**下联**（本端主动向对端 REGISTER），默认 `true`
  - `upLinkEnabled`：控制**上联**（是否接受对端 REGISTER 进来），默认 `false`
- REST API 支持增删改查
- 新增/启用时自动向对端发起 SIP 注册（驱动 SIP Client）
- 删除时发送 `REGISTER (Expires: 0)` 注销
- `upLinkEnabled` 从 `true → false` 时，立即清除该配置对应的上联注册表记录，`upLinkStatus` 置为 `OFFLINE`

### 3.3 SIP Stack 生命周期（`sip-stack-lifecycle`）

- 封装 JAIN-SIP 初始化：`SipStack`、`SipProvider`、`ListeningPoint`
- 统一分发 SIP 消息给 Server / Client 两个角色
- 管理 Digest 认证工具（nonce 生成、Authorization 验证）
- 端口绑定前执行可用性检查

### 3.4 SIP 服务端（`sip-interconnect-server`）

- 监听并处理对端平台的 `REGISTER` 请求；**首先检查对应互联配置的 `upLinkEnabled`**：若为 `false`，直接返回 `403 Forbidden`，不进入认证流程
- Digest 认证：首次挑战 → 验证 Authorization → 返回 200 OK
- 维护内存注册表（`remoteSipId → {contact, expires, registeredAt, lastHeartbeatAt}`）
- 接收对端发来的心跳 `MESSAGE`（`CmdType: Keepalive`）：更新 `lastHeartbeatAt`，回复 200 OK
- `@Scheduled` 每 30s 对注册表双重扫描：注册到期（`now - registeredAt > expires`）**或**心跳超时（`now - lastHeartbeatAt > heartbeatTimeout`），任意一个触发则移除该注册并将 `upLinkStatus` 置为 `OFFLINE`
- 注册成功后主动发送 `SUBSCRIBE Catalog`，拉取对端设备目录
- 处理对端发来的 `NOTIFY`，解析 GB/T 28181 XML，同步设备目录

### 3.5 SIP 客户端（`sip-interconnect-client`）

- 根据互联配置向对端发送 `REGISTER`，完成 Digest 挑战-应答；指数退避重试（最长 60 秒间隔）
- 注册成功后**同时启动两个独立定时器**：
  - **心跳定时器**：每 60s 发送 `MESSAGE Keepalive`；连续 3 次无响应 → 取消心跳定时器、取消续约定时器，立即触发重新注册
  - **续约定时器**：在 `expires × 2/3` 时发送 `REGISTER (refresh)`；失败 → 取消心跳定时器，立即触发重新注册
- 两个定时器任意一个失败，均互相取消对方并共同进入重新注册流程，`downLinkStatus` 置为 `OFFLINE`
- 重新注册成功后（200 OK），重置两个定时器，`downLinkStatus` 恢复 `ONLINE`
- 响应对端 `SUBSCRIBE Catalog`：从 `ivs1900_camera_mapping` 读取本端设备列表，组装 GB/T 28181 XML 发送 `NOTIFY`
- 响应对端 `SIP MESSAGE`（设备控制命令）：解析命令类型，翻译后调用 ivs1900 对应 REST 接口，结果回复 200 OK
- 响应对端 `INVITE`（拉流暂不规划，返回 501 Not Implemented）

### 3.6 设备目录（`device-catalog`）

**对端设备（REMOTE）**：
- JPA 实体 `device`：设备 ID、名称、类型、在线状态、IP/端口、父节点 ID、所属互联 ID、同步时间
- 按互联 ID 批量 upsert（对端 `NOTIFY` 触发）
- REST API：按互联 ID 查询对端设备树、按设备 ID 查询详情

**本端设备（来自 ivs1900）**：
- JPA 实体 `ivs1900_camera_mapping`：ivs1900 原始 ID ↔ 国标 20 位设备 ID 映射，含名称、在线状态、同步时间
- 国标 ID 生成规则：`domainCode前10位 + "132" + id补零7位`，一旦生成不变
- `Ivs1900SyncService`：定时任务（默认 60 秒）同步 ivs1900 相机列表，新相机生成国标 ID，已有相机更新在线状态

### 3.7 设备命令路由（`device-command-router`）

收到对端发来的 `SIP MESSAGE`（PTZ / ConfigDownload / DeviceConfig 等）后，按目标设备 ID 路由：

```
收到 SIP MESSAGE（含目标 DeviceID）
        │
        ├─ DeviceID 在 ivs1900_camera_mapping？
        │       │
        │       └─ 是 → 翻译为 ivs1900 REST API 调用，结果回复 SIP 200 OK + 应答 MESSAGE
        │
        ├─ DeviceID 在 remote_device 表？
        │       │
        │       └─ 是 → 通过 interconnect_id 查找对端平台 SIP 地址，
        │                 原样转发 GB/T 28181 SIP MESSAGE 给对端，
        │                 等待对端应答后透传回原始请求方
        │
        └─ 均不在 → 回复 404 Not Found
```

**本端 IVS1900 相机命令映射**：

| GB/T 28181 命令 | ivs1900 REST API |
|-----------------|-----------------|
| PTZ 控制（DeviceControl/PTZCmd） | `POST /device/ptzcontrol` |
| 云台状态查询（DeviceInfo）        | `GET  /ptz/currentInfo/v1.0` |
| 设备参数查询（ConfigDownload）    | `POST /device/camera/batchconfig/v1.0` |
| 设备参数配置（DeviceConfig）      | ivs1900 对应配置接口 |

**外域平台设备命令转发**：保持原始 GB/T 28181 XML 不变，仅替换 SIP 路由目标（To/Request-URI 指向对端平台）

### 3.8 媒体代理（`media-proxy`）

- Netty UDP Channel 实现 RTP/RTCP 双向透传
- 动态端口池（默认 10000–20000）：按需分配、释放
- 300 秒无数据包自动超时清理
- 端口耗尽时拒绝新 INVITE，返回 486 Busy Here
- **范围**：仅代理对端设备的流（REMOTE）；本端 ivs1900 相机拉流暂不规划

### 3.9 Web 前端（`web-dashboard`）

- Vue 3 + Vite + Element Plus
- 页面：仪表盘 / 本端配置 / 互联管理 / 设备树 / 实时预览
- 轮询（10 秒）刷新互联双向注册状态
- hls.js 播放 HLS 视频流

---

## 4. 数据模型

```
local_sip_config（单行）
├── device_id       VARCHAR   本端 20 位国标设备 ID
├── domain          VARCHAR   SIP 域
├── sip_ip          VARCHAR   监听 IP
├── sip_port        INT       监听端口（默认 5060）
├── transport       VARCHAR   UDP / TCP
├── username        VARCHAR   认证用户名
├── password        VARCHAR   认证密码（加密存储）
├── expires         INT       注册有效期（秒，默认 3600）
├── status          VARCHAR   RUNNING / RELOADING / ERROR
└── error_msg       VARCHAR   热重载失败原因（可空）

interconnect_config（多行）
├── id                  BIGINT    主键
├── name                VARCHAR   显示名称
├── remote_sip_id       VARCHAR   对端设备 ID
├── remote_ip           VARCHAR   对端 SIP 地址
├── remote_port         INT       对端 SIP 端口
├── remote_domain       VARCHAR   对端 SIP 域
├── password            VARCHAR   认证密码（加密存储）
├── enabled             BOOLEAN   是否启用（控制下联：本端主动向对端注册）
├── up_link_enabled     BOOLEAN   是否启用上联（控制上联：是否接受对端注册进来，默认 false）
├── up_link_status      VARCHAR   ONLINE / OFFLINE（对端注册进来的状态）
├── down_link_status    VARCHAR   ONLINE / OFFLINE / REGISTERING / ERROR（本端注册出去的状态）
├── last_heartbeat_at   TIMESTAMP 最后收到对端心跳的时间（upLink 方向，可空）
└── created_at          TIMESTAMP 创建时间

device（多行）——仅存储对端平台同步来的设备（REMOTE）
├── device_id       VARCHAR   国标 20 位设备 ID（对端分配）
├── name            VARCHAR   设备名称
├── type            VARCHAR   设备类型
├── status          VARCHAR   ON / OFF
├── ip              VARCHAR   设备 IP
├── port            INT       设备端口
├── parent_id       VARCHAR   父节点 ID（构建树结构）
├── interconnect_id BIGINT    所属互联配置 ID（FK → interconnect_config）
└── synced_at       TIMESTAMP 最后同步时间

ivs1900_camera_mapping（多行）——本端相机的国标 ID 映射表，同时作为命令网关的路由表
├── id              BIGINT    主键（自增，用于生成国标 ID 序号）
├── ivs_camera_id   VARCHAR   ivs1900 原始相机 ID（唯一）
├── gb_device_id    VARCHAR   生成的 20 位国标设备 ID（唯一，一旦生成不变）
│                             生成规则：本端 domain 前10位 + "132" + id补零至7位
├── name            VARCHAR   相机名称
├── status          VARCHAR   ONLINE / OFFLINE（从 ivs1900 同步）
└── synced_at       TIMESTAMP 最后同步时间
```

---

## 5. 关键流程

### 5.1 双向注册流程

```
本平台                                    对端平台
   │                                          │
   │──── REGISTER ──────────────────────────▶│  本端作为客户端，主动注册
   │◀─── 401 Unauthorized (nonce) ───────────│
   │──── REGISTER (Authorization) ──────────▶│
   │◀─── 200 OK ─────────────────────────────│
   │                                          │
   │◀─── REGISTER ───────────────────────────│  对端作为客户端，反向注册
   │──── 401 Unauthorized (nonce) ───────────▶│
   │◀─── REGISTER (Authorization) ───────────│
   │──── 200 OK ─────────────────────────────▶│
   │                                          │
   │──── SUBSCRIBE (Event: Catalog) ─────────▶│  注册成功后拉取设备目录
   │◀─── 200 OK ─────────────────────────────│
   │◀─── NOTIFY (XML 设备列表) ──────────────│
   │──── 200 OK ─────────────────────────────▶│
```

### 5.2 本端设备目录同步流程（ivs1900）

```
定时任务（每 60 秒）
        │
        ▼
调用 ivs1900 REST API 获取相机列表
        │
        ├── 新相机（ivs_camera_id 不在映射表中）
        │     └── 生成国标 ID（domainCode前10位 + "132" + id补零7位）
        │         写入 ivs1900_camera_mapping
        │
        └── 已有相机
              └── 更新 status（ONLINE/OFFLINE）和 synced_at

对端发来 SUBSCRIBE Catalog
        │
        ▼
从 ivs1900_camera_mapping 读取全部相机（含 gb_device_id、name、status）
        │
        ▼
组装 GB/T 28181 XML 设备列表，发送 NOTIFY
```

### 5.3 设备控制命令流程（ivs1900 网关）

```
对端平台                    gbt28181                      ivs1900
   │                           │                             │
   │── SIP MESSAGE ────────────▶│                             │
   │   (PTZ/配置/录像命令)       │                             │
   │                           │ 1. 解析命令类型               │
   │                           │ 2. 通过 gb_device_id         │
   │                           │    查 ivs1900_camera_mapping │
   │                           │    得到 ivs_camera_id        │
   │                           │── REST API ─────────────────▶│
   │                           │◀─ 响应 ──────────────────────│
   │                           │ 3. 翻译响应为 GB/T 28181 格式 │
   │◀─ SIP 200 OK ──────────────│                             │
```

### 5.4 媒体请求流程（仅对端设备）

```
前端 / 对端平台         本平台               目标设备
      │                   │                     │
      │── INVITE (SDP) ──▶│                     │
      │                   │── INVITE (SDP) ────▶│
      │                   │◀─ 200 OK (SDP) ─────│
      │◀─ 200 OK (SDP) ───│                     │
      │── ACK ────────────▶│── ACK ─────────────▶│
      │                   │                     │
      │◀══════════ RTP 流（经媒体代理透传）══════│
      │                   │                     │
      │── BYE ────────────▶│── BYE ─────────────▶│
      │◀─ 200 OK ──────────│◀─ 200 OK ───────────│
```

### 5.5 心跳保活流程

注册成功后，心跳消息与刷新注册**同时运行**，任意一个失败都触发重新注册。

#### downLink 方向（本端作为 Client）

```
REGISTER 200 OK
        │
        ├──────────────────────────────────────────────┐
        │                                              │
        ▼                                              ▼
  [心跳定时器]                                   [续约定时器]
  每 60s 触发                                   expires × 2/3 触发
        │                                              │
        ▼                                              ▼
  MESSAGE Keepalive ──▶ 对端              REGISTER (refresh) ──▶ 对端
        │                                              │
        ├─ 200 OK ──▶ 重置失败计数                     ├─ 200 OK ──▶ 重置续约定时器
        │             继续等下轮心跳                    │
        │                                              │
        └─ 超时/无响应                                  └─ 超时 / 4xx
               │                                              │
          failure_count++                                     │
               │                                              │
          < 3次 ──▶ 继续等下轮心跳                            │
               │                                              │
          ≥ 3次                                               │
               │                                              │
               ▼                                              ▼
          取消心跳定时器                              取消心跳定时器
          取消续约定时器 ◀────────────────────────── 取消续约定时器
               │
               ▼
   downLinkStatus = OFFLINE
   指数退避重新 REGISTER（5s / 10s / 20s / … / 300s）
               │
        REGISTER 200 OK
               │
               ▼
   downLinkStatus = ONLINE
   重启心跳定时器 + 重启续约定时器
```

#### upLink 方向（本端作为 Server）

```
对端 REGISTER 200 OK
        │
        ├─ 记录 registeredAt、expires、lastHeartbeatAt 到内存注册表
        │
        ▼
对端每 60s 发送 MESSAGE Keepalive
        │
        ├─ 收到 ──▶ 更新 lastHeartbeatAt，回 200 OK
        └─ 未收到 ──▶ lastHeartbeatAt 停止更新

@Scheduled 每 30s 扫描注册表（双重检查）：
        │
        ├─ now - registeredAt > expires          （注册到期）
        │         OR
        └─ now - lastHeartbeatAt > heartbeatTimeout  （心跳超时，默认 180s）
                  │
                  ▼  任意一个为真
          移除注册表记录
          upLinkStatus = OFFLINE
          （等待对端重新 REGISTER）
```

#### 关键参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `heartbeatInterval` | 60s | Client 心跳发送周期 |
| `heartbeatFailThreshold` | 3 | 连续无响应次数，触发重注册 |
| `heartbeatTimeout` | 180s | Server 侧无心跳判定超时（= interval × 3） |
| `registerExpires` | 3600s | 注册有效期 |
| 续约提前量 | expires × 2/3 ≈ 2400s | 续约定时器触发时机 |

XML 心跳报文格式（GB/T 28181-2022）：

```xml
<?xml version="1.0" encoding="GB2312"?>
<Notify>
  <CmdType>Keepalive</CmdType>
  <SN>序列号</SN>
  <DeviceID>发送方设备ID</DeviceID>
  <Status>OK</Status>
</Notify>
```

### 5.6 SIP Stack 热重载流程

```
用户保存新本端配置
        │
        ▼
[1] 向所有已注册对端发送 REGISTER (Expires: 0)，等待 200 OK 或超时（5s）
        │
        ▼
[2] 销毁当前 SipStack，释放端口绑定
        │
        ▼
[3] 检查新端口是否可用
        ├── 不可用 → 标记 ERROR，返回错误信息，流程终止
        │
        ▼
[4] 用新参数重建 SipStack，绑定新端口
        ├── 失败 → 标记 ERROR，记录原因，流程终止
        │
        ▼
[5] 遍历 enabled=true 的互联配置，逐一重启 SIP Client
        │
        ▼
标记 RUNNING
```

---

## 6. 技术栈

| 层次 | 技术 | 说明 |
|------|------|------|
| 后端框架 | Spring Boot 3.x | REST API、DI、生命周期管理 |
| SIP 协议栈 | JAIN-SIP（jain-sip-ri） | RFC 3261，GB/T 28181 主流参考实现 |
| RTP 传输 | Netty 4.x | NIO UDP，高性能媒体代理 |
| 持久化 | Spring Data JPA + H2 / MySQL | 开发用 H2，生产可切 MySQL |
| ivs1900 集成 | Spring RestTemplate / WebClient | 调用 ivs1900 REST API：同步相机列表、转发设备控制命令 |
| 前端框架 | Vue 3 + Vite | Composition API |
| UI 组件库 | Element Plus | 中文文档，组件完整 |
| 视频播放 | hls.js | HLS 协议播放 |
| 构建 | Maven + frontend-maven-plugin | 前后端一体打包 |
| 部署 | Fat JAR + jlink 裁剪 JRE + tar.gz | Linux 无需预装 Java |

---

## 7. 实现阶段规划

```
Phase 1：framework-scaffold（当前）
  ├── Maven 项目骨架（pom.xml）+ Spring Boot 主类 + 四层包结构
  ├── JPA 实体：local_sip_config、interconnect_config、ivs1900_camera_mapping
  ├── AES 加密工具类 + JPA AttributeConverter
  ├── SipStackManager 骨架（状态机，不含实际 SIP 操作）
  ├── REST API 骨架：控制器端点定义 + DTO + 统一异常处理
  └── Vue 3 前端脚手架：路由、状态管理、Element Plus、开发代理

Phase 2：local-sip-config（基于 Phase 1）
  ├── 本端 SIP 参数持久化（local_sip_config 表读写）
  ├── SipStackManager 完整实现（JAIN-SIP 初始化、热重载、端口检查）
  ├── 互联配置 CRUD 完整实现（interconnect_config 表读写）
  ├── REST API 业务逻辑填充（LocalSipConfigController、InterconnectConfigController）
  └── Vue 3 前端页面：本端配置页（含热重载状态轮询）+ 互联管理页

Phase 3：sip-registration（基于 Phase 2）✅ 已完成
  ├── SIP Server：接受对端平台 REGISTER，Digest 认证，维护内存注册表
  ├── SIP Client：向对端发起 REGISTER，Digest 挑战-应答，指数退避重试，注册续约
  └── 注册状态同步：双向注册状态写入 interconnect_config，前端可见

Phase 4：heartbeat-keepalive（基于 Phase 3）
  ├── Client 双定时器：注册成功后同时启动心跳定时器（60s）和续约定时器（expires×2/3）
  ├── 心跳失败处理：连续 3 次无响应 → 取消两个定时器 → 指数退避重新注册
  ├── 续约失败处理：REGISTER refresh 失败 → 取消心跳定时器 → 指数退避重新注册
  ├── Server 双重检查：@Scheduled 每 30s 扫描，注册到期或心跳超时（180s）任意一个触发则置 upLinkStatus=OFFLINE
  └── 前端：互联列表增加最后心跳时间列，在线状态基于心跳+注册双重判定

Phase 5：ivs1900-catalog-sync（基于 Phase 4）
  ├── IVS1900 Session 管理：登录、保活、401 自动重连
  ├── IVS1900 相机列表定时同步（60s）：调用 /device/deviceList + /device/channelDevInfo
  ├── 国标设备 ID 生成：ivs1900_camera_mapping 表维护 ivsCameraId ↔ gbDeviceId 映射
  ├── 响应对端 SUBSCRIBE Catalog：从映射表读取设备列表，组装 GB/T 28181 XML 发送 NOTIFY
  └── 前端：设备树页展示本端 IVS1900 相机列表（名称、在线状态、国标 ID）

Phase 6：device-config-routing（基于 Phase 5）
  ├── 命令路由入口：收到 SIP MESSAGE 后，按 DeviceID 判断设备归属
  │     ├── 在 ivs1900_camera_mapping → 走 IVS1900 REST API 路径
  │     ├── 在 remote_device 表      → 转发 GB/T 28181 SIP MESSAGE 给对端平台
  │     └── 均不在                   → 回复 404 Not Found
  ├── 本端设备：ConfigDownload 查询 → 调用 ivs1900 POST /device/camera/batchconfig/v1.0，
  │     组装 ConfigDownload Response 回复（支持全部 12 种 ConfigType）
  ├── 本端设备：DeviceConfig 配置 → 调用 ivs1900 对应配置接口，
  │     组装 DeviceConfig Response 回复（支持 BasicParam 等 11 种子命令）
  └── 外域设备：ConfigDownload / DeviceConfig → 通过 interconnect_id 查对端 SIP 地址，
        转发原始 GB/T 28181 MESSAGE，透传对端应答

Phase 7：ivs1900-ptz-gateway（基于 Phase 6）
  ├── PTZ 控制：接收 SIP MESSAGE（CmdType=DeviceControl，含 PTZCmd），翻译并调用 ivs1900 POST /device/ptzcontrol
  └── 云台状态查询：接收 SIP MESSAGE（CmdType=DeviceInfo），调用 ivs1900 GET /ptz/currentInfo/v1.0，结果回复

Phase 8：media-proxy（基于 Phase 7）
  ├── Netty UDP Channel：RTP/RTCP 双向透传
  ├── 动态端口池（默认 10000–20000）：按需分配与释放
  ├── 接受对端 INVITE（SDP 协商），分配 RTP 端口，代理回 200 OK
  ├── 300 秒无数据包自动超时清理
  ├── 端口耗尽时返回 486 Busy Here
  └── BYE 处理：释放端口，清理会话

Phase 9：frontend-complete（基于 Phase 8）
  ├── 仪表盘页：双向注册状态总览、在线设备数、活跃媒体会话数
  ├── 设备树页：本端 ivs1900 相机 + 对端同步设备，树形展示
  ├── PTZ 控制界面：方向盘 + 变焦，调用 REST API 发送控制命令
  └── 实时预览页：hls.js 播放，按设备 ID 发起/停止拉流

Phase 10：packaging（基于 Phase 9）
  ├── frontend-maven-plugin 前后端一体打包
  ├── jlink 裁剪 JRE，生成最小运行环境
  └── 自包含 tar.gz 分发包（含启动脚本、默认配置），Linux 无需预装 Java

Phase 11（Not In Scope，暂不规划）
  ├── 本端 ivs1900 相机拉流（INVITE → ivs1900 推流接口）
  ├── 录像检索与历史回放
  ├── WebRTC 低延迟预览
  ├── NAT 穿透
  └── GB35114 国标加密
```

---

## 8. 非功能约束

- **网络**：SIP 端口默认 5060（UDP/TCP），RTP 端口范围 10000–20000，需在防火墙开放；ivs1900 REST API 需本机可达
- **并发**：MVP 阶段单节点，不支持集群
- **NAT**：MVP 要求双方在同一局域网，NAT 穿透留后续
- **认证**：支持 Digest Auth（RFC 3261），暂不支持 GB35114 加密
- **延迟**：HLS 预览延迟 3–10 秒，可接受；WebRTC 低延迟留后续
- **GB/T 28181 XML 兼容性**：增加宽松解析模式，记录解析失败的原始报文，兼容不同厂商实现差异
- **ivs1900 可用性**：若 ivs1900 不可达，本端设备目录同步暂停，已有映射数据保留，不影响对端设备功能
