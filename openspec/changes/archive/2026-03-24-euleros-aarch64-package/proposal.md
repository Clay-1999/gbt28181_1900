## Why

当前项目只能在开发机（macOS arm64）上运行，没有面向生产环境的打包方案。需要将后端、前端、ZLMediaKit 打包为可在 EulerOS v2r13 aarch64 上一键部署的产物，降低运维门槛。

## What Changes

- 新增 `build/` 目录，包含构建打包脚本 `build-package.sh`
- 产物结构：单个 `.tar.gz` 包 + `install.sh` + `start.sh`
- 打包内容：后端 JAR、前端静态文件（Nginx 托管）、ZLMediaKit aarch64 二进制及配置模板
- 安装脚本负责：解压、配置目录结构、写入 systemd/Nginx 配置
- 运行脚本负责：启动 ZLMediaKit、启动后端 JAR，支持 stop/status/restart

## Capabilities

### New Capabilities

- `deployment-package`: 将应用打包为可在 EulerOS v2r13 aarch64 上部署的发行包，含安装脚本和运行脚本

### Modified Capabilities

## Impact

- 新增 `build/` 目录及打包脚本，不影响现有源码
- 前端需执行 `npm run build` 产出静态文件
- 后端需执行 `mvn package` 产出 JAR
- ZLMediaKit 需提供 aarch64 预编译二进制（或在打包脚本中从源码交叉编译）
- 最终产物：`gbt28181-<version>-euleros-aarch64.tar.gz`、`install.sh`、`start.sh`
