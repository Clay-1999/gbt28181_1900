## ADDED Requirements

### Requirement: 互联管理页面显示订阅告警按钮
互联管理界面 SHALL 在每条互联配置条目的操作区显示「订阅告警」按钮，点击后发起告警订阅请求并更新按钮状态。

#### Scenario: 未订阅状态显示
- **WHEN** 页面加载，调用 `GET /api/interconnects/{id}/alarm-subscribe` 返回 `subscribed: false`
- **THEN** 按钮显示「订阅告警」（主色调），可点击

#### Scenario: 已订阅状态显示
- **WHEN** 查询接口返回 `subscribed: true`
- **THEN** 按钮显示「已订阅」（灰色/成功色），点击可重新订阅

#### Scenario: 点击订阅
- **WHEN** 用户点击「订阅告警」按钮
- **THEN** 调用 `POST /api/interconnects/{id}/alarm-subscribe`，成功后按钮变为「已订阅」，显示 Element Plus success 消息；失败时显示 error 消息

---

### Requirement: IVS1900 互联管理页面显示订阅告警按钮
IVS1900 互联配置界面 SHALL 在每条配置条目的操作区显示「订阅告警」按钮，交互逻辑与互联管理页面一致。

#### Scenario: 点击订阅 IVS1900
- **WHEN** 用户点击「订阅告警」按钮
- **THEN** 调用 `POST /api/ivs1900/interconnect/{id}/alarm-subscribe`，成功后更新状态

---

### Requirement: 告警管理页面（AlarmView.vue）
系统 SHALL 提供 `/alarms` 路由页面，展示告警列表，支持按设备 ID 过滤和刷新。

#### Scenario: 页面加载展示告警列表
- **WHEN** 用户导航到 `/alarms`
- **THEN** 调用 `GET /api/alarms`，以 Element Plus Table 展示：DeviceID、告警类型、告警描述、优先级（1-4 映射为文字）、发生时间、来源 IP；按 receivedAt 倒序

#### Scenario: 按设备 ID 过滤
- **WHEN** 用户在过滤输入框输入设备 ID 并点击查询
- **THEN** 调用 `GET /api/alarms?deviceId=...`，表格更新

#### Scenario: 刷新告警
- **WHEN** 用户点击刷新按钮
- **THEN** 重新调用查询接口，更新表格数据

---

### Requirement: 导航菜单增加告警管理入口
应用 SHALL 在导航菜单中增加「告警管理」菜单项，链接到 `/alarms`。

#### Scenario: 菜单项显示
- **WHEN** 用户访问应用任意页面
- **THEN** 导航栏中可见「告警管理」入口，点击跳转到 `/alarms`
