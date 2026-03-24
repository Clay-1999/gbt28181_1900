## 1. 后端 Service 层

- [x] 1.1 创建 `LocalSipConfigService`：`getConfig()` 从 DB 读取单行并返回（id=1L），`updateConfig(request)` 写入 DB 后异步调用 `SipStackManager.reload()`
- [x] 1.2 创建 `InterconnectConfigService`：`findAll()`（按 createdAt 倒序）、`findById(id)`（不存在抛 ResourceNotFoundException）、`create(request)`、`update(id, request)`、`delete(id)`

## 2. SipStackManager 完整实现

- [x] 2.1 添加字段：`SipStack sipStack`、`SipProvider sipProvider`，注入 `InterconnectConfigRepository`
- [x] 2.2 实现 `checkPortAvailable(ip, port, transport)`：尝试绑定 `DatagramSocket`（UDP）或 `ServerSocket`（TCP），绑定成功即释放并返回 true，失败返回 false
- [x] 2.3 实现 `doStop()`：若 `sipStack != null`，调用 `sipStack.stop()`，等待 500ms，置 null
- [x] 2.4 实现 `doStart(LocalSipConfig config)`：用 JAIN-SIP API 创建 `SipFactory`、`SipStack`（设置 `javax.sip.IP_ADDRESS`、`javax.sip.STACK_NAME`）、`SipProvider`、`ListeningPoint`（绑定 IP/Port/Transport）
- [x] 2.5 完整实现 `reload(config)` 方法：status→RELOADING → log 注销占位 → `doStop()` → `checkPortAvailable`（失败→ERROR）→ `doStart()`（失败→ERROR）→ log 重连占位 → status→RUNNING；全程 `synchronized`
- [x] 2.6 更新 `LocalSipConfigInitializer`：若 `config.deviceId != null` 则调用 `sipStackManager.reload(config)` 完成启动时初始化

## 3. 控制器填充

- [x] 3.1 填充 `LocalSipConfigController.getConfig()`：调用 `LocalSipConfigService.getConfig()`，将实体映射到 `LocalSipConfigResponse`（password 替换为 `***`，注入 status/errorMsg）
- [x] 3.2 填充 `LocalSipConfigController.updateConfig()`：status==RELOADING 时返回 409；否则调用 `LocalSipConfigService.updateConfig(request)`，返回 202
- [x] 3.3 填充 `InterconnectConfigController` 所有方法：调用 `InterconnectConfigService` 对应方法，实体映射到 `InterconnectConfigResponse`（password 替换为 `***`）

## 4. 前端：本端配置页

- [x] 4.1 `LocalConfigView.vue`：`onMounted` 调用 `GET /api/local-config` 填充表单（`el-form` + `el-form-item`），展示 SIP Stack 状态 Badge（RUNNING=success/RELOADING=warning/ERROR=danger）
- [x] 4.2 实现「保存」按钮：调用 `PUT /api/local-config`，收到 202 后禁用按钮，启动 5 秒间隔轮询 `GET /api/local-config/status`
- [x] 4.3 轮询逻辑：status 变为 RUNNING 或 ERROR 时停止轮询，恢复按钮；ERROR 时用 `el-alert` 展示 `errorMsg`；超过 60 秒（12 次）停止轮询并提示超时
- [x] 4.4 处理 409 响应：展示 `el-message` 提示"SIP Stack 正在重载，请稍后再试"

## 5. 前端：互联管理页

- [x] 5.1 `InterconnectsView.vue`：`onMounted` 调用 `GET /api/interconnects`，用 `el-table` 展示列表（名称、对端 SIP ID、对端 IP:Port、启用状态 Tag、创建时间、操作列）
- [x] 5.2 新增/编辑弹窗：`el-dialog` + `el-form`，字段含 name、remoteSipId、remoteIp、remotePort、remoteDomain、password、enabled，含前端必填校验
- [x] 5.3 新增流程：点击「新增」打开空白弹窗，提交后调用 `POST /api/interconnects`，成功后关闭弹窗并刷新列表，`el-message` 提示"新增成功"
- [x] 5.4 编辑流程：点击「编辑」打开带当前数据（password 字段清空）的弹窗，提交后调用 `PUT /api/interconnects/{id}`，成功后关闭并刷新
- [x] 5.5 删除流程：点击「删除」弹出 `el-message-box` 确认，确认后调用 `DELETE /api/interconnects/{id}`，成功后刷新列表，提示"删除成功"

## 6. 验证

- [x] 6.1 启动应用，访问 `GET /api/local-config` 返回 200，status=ERROR，errorMsg="本端 SIP 参数未配置"
- [x] 6.2 通过 `PUT /api/local-config` 提交合法配置，返回 202，轮询 status 最终变为 RUNNING
- [x] 6.3 `POST /api/interconnects` 新增配置，`GET /api/interconnects` 列表返回该条记录，password 显示 `***`
- [x] 6.4 `DELETE /api/interconnects/{id}` 返回 204，列表中不再出现该记录
- [x] 6.5 端口被占用时 PUT 配置，status 变为 ERROR，errorMsg 含"端口 {port} 不可用"（并发409窗口极短，因reload在毫秒内完成）
