## ADDED Requirements

### Requirement: 配置对话框新增 4 个 tab
前端配置对话框 SHALL 新增 VideoRecordPlan、VideoAlarmRecord、AlarmReport、SnapShot 4 个配置 tab，与现有 4 个 tab 并列。

#### Scenario: 打开录像计划 tab
- **WHEN** 用户点击"录像计划"tab
- **THEN** 系统调用 GET `/config/video-record-plan`，加载录像方式和码流类型并填充表单

#### Scenario: 保存报警录像配置
- **WHEN** 用户修改预录时间后点击保存
- **THEN** 系统调用 PUT `/config/video-alarm-record`，返回成功或失败提示

#### Scenario: 打开报警上报 tab
- **WHEN** 用户点击"报警上报"tab
- **THEN** 系统调用 GET `/config/alarm-report`，加载报警方式等字段

#### Scenario: 打开抓图 tab
- **WHEN** 用户点击"抓图"tab
- **THEN** 系统调用 GET `/config/snap-shot`，加载抓图间隔和连拍次数
