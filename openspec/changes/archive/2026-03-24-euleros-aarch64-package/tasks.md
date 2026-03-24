## 1. 准备构建目录结构

- [x] 1.1 创建 `build/` 目录，添加 `build/bin/.gitkeep` 和 `build/dist/.gitkeep`
- [x] 1.2 在 `build/bin/README.md` 中说明需要放置 EulerOS aarch64 的 MediaServer 二进制

## 2. 前端集成进 JAR

- [x] 2.1 修改 `frontend/vite.config.js`，将构建输出目录指向 `../src/main/resources/static`
- [x] 2.2 修改 `build/build-package.sh` 先执行 `npm run build`，再执行 `mvn package`，确保静态文件打包进 JAR

## 3. 编写构建脚本 build/build-package.sh

- [x] 3.1 ~~检查 `build/bin/MediaServer` 是否存在~~ 已移除：MediaServer 已预置于目标机器 `/usr/data/MediaServer`
- [x] 3.2 执行前端构建（`cd frontend && npm run build`），输出至 `src/main/resources/static`
- [x] 3.3 执行 `mvn package -DskipTests` 构建含前端的后端 JAR
- [x] 3.4 按设计的目录结构组装临时目录：`lib/`、`zlm/`、`conf/`、`logs/`（无 `jdk/` 目录，JDK 已预装）
- [x] 3.5 生成 `conf/application.yml.template`（从 `src/main/resources/application.yml` 提取，敏感值替换为占位符）
- [x] 3.6 生成 `zlm/config.ini.template`（ZLM 配置模板，含端口、secret 占位符）
- [x] 3.7 将 `build/install.sh` 和 `build/start.sh` 复制到临时目录
- [x] 3.8 打包为 `build/dist/gbt28181-<version>-euleros-aarch64.tar.gz`，同时输出 `install.sh` 和 `start.sh` 到 `build/dist/`
- [x] 3.9 ~~下载 Temurin JDK 21 aarch64 tarball~~ 已移除：环境上 jre-21 已安装，无需下载/打包 JDK

## 4. 编写安装脚本 build/install.sh

- [x] 4.1 检查运行环境：验证系统 `java` 命令可用（JDK 21 已预装），检查 `/usr/data/MediaServer` 是否存在
- [x] 4.2 将压缩包解压至 `/opt/gbt28181/`，创建 `run/` 和 `logs/` 目录
- [x] 4.3 若 `/opt/gbt28181/conf/application.yml` 不存在，则从模板生成（替换占位符为默认值）
- [x] 4.4 若 `/opt/gbt28181/zlm/config.ini` 不存在，则从模板生成
- [x] 4.5 打印安装完成提示：访问地址 `http://<本机IP>:8080`，以及如何使用 `start.sh`

## 5. 编写运行脚本 build/start.sh

- [x] 5.1 实现 `start` 子命令：启动 ZLMediaKit（`/usr/data/MediaServer`，后台），PID 写入 `run/zlm.pid`，日志输出到 `logs/zlm.log`
- [x] 5.2 实现 `start` 子命令：启动后端 JAR（使用系统预装 `java`），PID 写入 `run/app.pid`，日志输出到 `logs/app.log`
- [x] 5.3 实现 `stop` 子命令：读取 PID 文件，发送 SIGTERM，等待退出，删除 PID 文件
- [x] 5.4 实现 `status` 子命令：检查 PID 文件和进程是否存在，打印 running/stopped 状态
- [x] 5.5 实现 `restart` 子命令：调用 stop 再调用 start
- [x] 5.6 无参数时打印用法说明

## 6. 验证

- [ ] 6.1 在开发机上执行 `bash build/build-package.sh`，确认三个产物文件生成
- [ ] 6.2 检查压缩包内目录结构符合设计（`tar -tzf`），确认无 `jdk/` 目录，无 `zlm/MediaServer`
- [x] 6.3 检查 `install.sh` 和 `start.sh` 语法正确（`bash -n`）
