## Why

当前平台在告警处理上存在合规缺口：`AlarmNotifyHandler` 已能接收并持久化下级设备推送的 `NOTIFY(Event: Alarm)`，`AlarmController` 已提供 `GET /api/alarms` 查询接口，但平台从未主动向下级设备或外域互联平台发送告警订阅报文，完全依赖下级自愿推送。

GB/T 28181-2022 §9.7 规定：联网系统平台应能主动向下级平台或设备发送报警订阅（`Query/CmdType=Alarm`），以触发对端持续上报告警事件。不主动订阅意味着部分合规实现的设备在未收到订阅请求时不会主动 NOTIFY，造成告警数据丢失。

## What Changes

- 新增 `AlarmSubscribeService`：向下级 IVS1900 设备或外域互联平台主动发送报警订阅 SIP MESSAGE（`CmdType=Alarm`），维护订阅状态，支持取消订阅
- 新增 REST 触发接口：在 `Ivs1900InterconnectController` 和互联配置控制器中分别增加 `POST /{id}/alarm-subscribe`（订阅/切换）和 `GET /{id}/alarm-subscribe`（查询状态）端点

## Capabilities

### New Capabilities

- `alarm-subscribe-client`：主动报警订阅逻辑，发送 `Query/CmdType=Alarm` SIP MESSAGE，维护已订阅 key 集合，提供订阅/取消/状态查询接口

### Modified Capabilities

- `alarm-api`：`AlarmController` 相关的报警查询 REST 接口（`GET /api/alarms`）不变；`Ivs1900InterconnectController` 新增 alarm-subscribe 端点；互联配置控制器同样新增对应端点

## Impact

- 后端：新增 `AlarmSubscribeService`（Bean），注入 `SipMessageSender`
- 后端：`Ivs1900InterconnectController` 新增 `POST/GET /{id}/alarm-subscribe` 端点
- 后端：互联配置控制器新增 `POST/GET /api/interconnects/{id}/alarm-subscribe` 端点
- 无前端变更（订阅操作可通过 REST 手动触发或后续集成到 UI）
- 无数据库变更（订阅状态仅内存维护，重启清零）
