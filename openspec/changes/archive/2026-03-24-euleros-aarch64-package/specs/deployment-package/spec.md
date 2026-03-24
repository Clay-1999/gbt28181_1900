## ADDED Requirements

### Requirement: 构建脚本生成发行包
项目根目录下的 `build/build-package.sh` 脚本 SHALL 将前端静态文件编译后嵌入后端 JAR、ZLMediaKit 二进制及配置模板打包为 `gbt28181-<version>-euleros-aarch64.tar.gz`。脚本运行前 SHALL 检查 `build/bin/MediaServer` 是否存在，不存在则打印错误并退出。

#### Scenario: 正常构建
- **WHEN** 执行 `bash build/build-package.sh`，且 `build/bin/MediaServer` 存在
- **THEN** 在 `build/dist/` 目录下生成 `gbt28181-<version>-euleros-aarch64.tar.gz`、`install.sh`、`start.sh` 三个文件

#### Scenario: MediaServer 缺失
- **WHEN** 执行 `bash build/build-package.sh`，但 `build/bin/MediaServer` 不存在
- **THEN** 脚本打印错误信息提示用户放置 aarch64 二进制，并以非零退出码退出

### Requirement: 安装脚本 install.sh
`install.sh` SHALL 完成以下步骤：检查 Java 版本（>=17）、将压缩包解压至 `/opt/gbt28181/`、生成 `application.yml`（若不存在）、生成 ZLM `config.ini`（若不存在）。不依赖 Nginx。

#### Scenario: 首次安装
- **WHEN** 在目标机器上执行 `bash install.sh`，Java >= 17
- **THEN** 应用文件解压至 `/opt/gbt28181/`，配置文件生成，打印安装完成提示及访问地址

#### Scenario: Java 版本不满足
- **WHEN** 执行 `bash install.sh`，但 Java 版本低于 17 或未安装
- **THEN** 打印 Java 版本要求并退出，不继续安装

#### Scenario: 重复安装不覆盖配置
- **WHEN** `/opt/gbt28181/conf/application.yml` 已存在时再次执行 `bash install.sh`
- **THEN** 跳过配置文件生成，保留已有配置，仅更新应用二进制文件

### Requirement: 运行脚本 start.sh
`/opt/gbt28181/start.sh` SHALL 支持 `start`、`stop`、`restart`、`status` 四个子命令，管理 ZLMediaKit 和后端 JAR 两个进程。

#### Scenario: 启动服务
- **WHEN** 执行 `bash start.sh start`
- **THEN** 依次启动 ZLMediaKit 和后端 JAR，将 PID 写入 `/opt/gbt28181/run/*.pid`，打印启动成功信息

#### Scenario: 停止服务
- **WHEN** 执行 `bash start.sh stop`
- **THEN** 读取 PID 文件，向进程发送 SIGTERM，等待进程退出，删除 PID 文件

#### Scenario: 查看状态
- **WHEN** 执行 `bash start.sh status`
- **THEN** 打印 ZLMediaKit 和后端 JAR 各自的运行状态（running/stopped）及 PID

#### Scenario: 无参数调用
- **WHEN** 执行 `bash start.sh`（不带子命令）
- **THEN** 打印用法说明：`Usage: start.sh {start|stop|restart|status}`

### Requirement: 包内目录结构
压缩包解压后 SHALL 包含固定目录结构：`lib/gbt28181.jar`（含前端静态文件）、`zlm/MediaServer`、`zlm/config.ini.template`、`conf/application.yml.template`、`logs/`（空目录）。不含独立的 `web/` 目录。

#### Scenario: 解压校验
- **WHEN** 执行 `tar -tzf gbt28181-<version>-euleros-aarch64.tar.gz`
- **THEN** 输出中包含 `lib/gbt28181.jar`、`zlm/MediaServer`，不包含独立的 `web/` 目录
