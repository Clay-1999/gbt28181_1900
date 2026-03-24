## Context

项目由三个组件构成：Spring Boot 后端（JAR）、Vue3 前端（静态文件）、ZLMediaKit（媒体服务器二进制）。目标平台为 EulerOS v2r13 aarch64，该系统基于 openEuler，使用 systemd 管理服务。

当前没有任何打包或部署方案，所有组件需手动启动。

## Goals / Non-Goals

**Goals:**
- 产物精简：一个 `.tar.gz` 包 + `install.sh` + `start.sh`，三个文件即可完成部署
- `install.sh` 完成解压、目录初始化、配置文件生成
- `start.sh` 支持 `start / stop / restart / status` 四个子命令
- ZLMediaKit 使用预编译 aarch64 静态二进制，打包进压缩包
- 前端静态文件打包进 JAR，由 Spring Boot 直接托管，无需 Nginx

**Non-Goals:**
- 不做 Docker/容器化
- 不做自动化 CI/CD 流水线
- 不做 systemd service 文件（用 start.sh 管理进程即可）
- 不做数据库迁移（H2 内嵌，无需额外处理）
- 不依赖 Nginx

## Decisions

**D1：ZLMediaKit 二进制来源**
- 选择：在 EulerOS aarch64 机器上编译后放入 `build/bin/` 目录
- 理由：ZLMediaKit 无官方 EulerOS aarch64 预编译包；打包脚本检查 `build/bin/MediaServer` 是否存在，不存在则报错提示用户手动放置
- 替代方案：在线下载——不可靠，生产环境可能无网络

**D2：前端托管方式**
- 选择：`npm run build` 产出的静态文件放入 `src/main/resources/static/`，打包进 JAR，由 Spring Boot 直接托管
- 理由：无需 Nginx，减少部署依赖；前端和后端同一端口（8080）访问；Spring Boot 对静态资源的支持开箱即用

**D3：包结构**
```
gbt28181-<version>/
├── lib/
│   └── gbt28181.jar          # 后端 JAR（含前端静态文件）
├── zlm/
│   ├── MediaServer            # ZLMediaKit 二进制
│   └── config.ini.template    # ZLM 配置模板
├── conf/
│   └── application.yml.template  # 后端配置模板
└── logs/                      # 日志目录（空）
```

**D4：配置管理**
- `install.sh` 根据用户输入（或默认值）生成 `application.yml` 和 ZLM `config.ini`
- 安装后配置文件位于 `/opt/gbt28181/conf/`，重新安装不覆盖已有配置

## Risks / Trade-offs

- [ZLMediaKit 二进制兼容性] EulerOS v2r13 的 glibc 版本可能与编译环境不同 → 使用静态链接编译（`-DENABLE_STATIC=ON`）
- [JDK 版本] 目标机器可能没有 JDK 17+ → `install.sh` 检查 Java 版本，不满足时给出提示
- [端口冲突] 默认端口 8080（后端）、18080（ZLM）可能被占用 → `start.sh` 启动前检查端口
