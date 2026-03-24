## ADDED Requirements

### Requirement: 实况播放对话框左右分栏布局
对于球机/遥控类设备（PTZType 1/4/5），实况播放对话框 SHALL 采用左右分栏布局：左侧为视频区域（约 60% 宽），右侧为 PTZ 控制面板（约 40% 宽）。非可控设备保持原有全宽视频布局。

#### Scenario: 球机播放时显示控制面板
- **WHEN** 用户点击 PTZType 为 1/4/5 的设备"播放"按钮
- **THEN** 对话框宽度为 1100px，左侧显示视频，右侧显示控制面板（含云台/预置位/巡航三个 tab）

#### Scenario: 非球机播放时不显示控制面板
- **WHEN** 用户点击 PTZType 不在 1/4/5 的设备"播放"按钮
- **THEN** 对话框宽度为 720px，仅显示视频区域

### Requirement: 云台控制面板
控制面板 SHALL 提供云台方向控制（8 方向 + 停止）及变倍/变焦/光圈调节按钮。

#### Scenario: 按住方向键发送持续控制命令
- **WHEN** 用户按下某个方向按钮（mousedown）
- **THEN** 前端立即调用 `POST /ptz/control` 发送对应方向命令；用户松开（mouseup/mouseleave）时发送停止命令

#### Scenario: 变倍控制
- **WHEN** 用户点击"放大"或"缩小"按钮
- **THEN** 前端调用 `POST /ptz/control` 发送变倍命令

### Requirement: 预置位管理面板
控制面板 SHALL 提供预置位 tab，显示当前设备的预置位列表，并支持调用、设置、删除操作。

#### Scenario: 打开预置位 tab 自动加载
- **WHEN** 用户切换到预置位 tab
- **THEN** 前端调用 `GET /ptz/preset` 加载预置位列表并展示

#### Scenario: 调用预置位
- **WHEN** 用户点击列表中某预置位的"调用"按钮
- **THEN** 前端调用 `POST /ptz/preset/call`，成功后 ElMessage 提示"调用成功"

#### Scenario: 设置预置位
- **WHEN** 用户填写预置位编号和名称后点击"设置当前位置"
- **THEN** 前端调用 `POST /ptz/preset/set`，成功后刷新列表

#### Scenario: 删除预置位
- **WHEN** 用户点击某预置位的"删除"按钮并确认
- **THEN** 前端调用 `POST /ptz/preset/delete`，成功后从列表移除

### Requirement: 巡航轨迹面板
控制面板 SHALL 提供巡航轨迹 tab，显示轨迹列表，支持查看详情及启动/停止巡航。

#### Scenario: 打开巡航 tab 自动加载轨迹列表
- **WHEN** 用户切换到巡航轨迹 tab
- **THEN** 前端调用 `GET /ptz/cruise` 加载轨迹列表

#### Scenario: 查看轨迹详情
- **WHEN** 用户点击某轨迹"详情"
- **THEN** 前端调用 `GET /ptz/cruise/{number}`，以弹窗或展开行显示详情

#### Scenario: 启动巡航
- **WHEN** 用户选择轨迹并点击"启动巡航"
- **THEN** 前端调用 `POST /ptz/cruise/start`，返回成功后按钮切换为"停止巡航"

#### Scenario: 停止巡航
- **WHEN** 用户点击"停止巡航"
- **THEN** 前端调用 `POST /ptz/cruise/stop`
