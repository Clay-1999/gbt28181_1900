#!/usr/bin/env bash
# build-package.sh — 构建 EulerOS v2r13 aarch64 发行包
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DIST_DIR="${SCRIPT_DIR}/dist"

# 从 pom.xml 中提取版本号
VERSION=$(grep -m1 '<version>' "${PROJECT_ROOT}/pom.xml" | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | tr -d '[:space:]')
PKG_NAME="gbt28181-${VERSION}-euleros-aarch64"

echo "=== gbt28181 打包脚本 ==="
echo "版本: ${VERSION}"
echo "输出目录: ${DIST_DIR}"
echo ""

# ── 1. 构建前端（输出到 src/main/resources/static）────────────
echo "[1/3] 构建前端..."
cd "${PROJECT_ROOT}/frontend"
npm install --silent
npm run build
echo "  前端构建完成 → src/main/resources/static"

# ── 2. 构建后端 JAR（含前端静态文件）─────────────────────────
echo "[2/3] 构建后端 JAR..."
cd "${PROJECT_ROOT}"
mvn package -DskipTests -q
JAR_FILE="${PROJECT_ROOT}/target/gbt28181-${VERSION}.jar"
if [ ! -f "${JAR_FILE}" ]; then
    # SNAPSHOT 版本名称兼容
    JAR_FILE=$(ls "${PROJECT_ROOT}"/target/gbt28181-*.jar 2>/dev/null | head -1)
fi
if [ ! -f "${JAR_FILE}" ]; then
    echo "[ERROR] 未找到构建产物 target/gbt28181-*.jar"
    exit 1
fi
echo "  后端 JAR: $(basename "${JAR_FILE}")"

# ── 3. 组装目录结构 ────────────────────────────────────────────
echo "[3/3] 组装包结构..."
STAGE_DIR="/tmp/${PKG_NAME}"
rm -rf "${STAGE_DIR}"
mkdir -p "${STAGE_DIR}"/{lib,zlm,conf,logs,run}

# 后端 JAR
cp "${JAR_FILE}" "${STAGE_DIR}/lib/gbt28181.jar"

# ZLMediaKit 由目标机器 /usr/data/MediaServer 提供，无需打包

# ZLM 配置模板
cat > "${STAGE_DIR}/zlm/config.ini.template" << 'ZLM_CONF'
[general]
listen_ip=0.0.0.0
enableVhost=0

[http]
port=__ZLM_HTTP_PORT__
sslport=18081
sendBufSize=65536

[rtsp]
port=8000

[rtmp]
port=1935

[rtp_proxy]
port=10001
timeoutSec=60
port_range=30000-35000
h264_pt=98
h265_pt=99
ps_pt=96

[hook]
timeoutSec=10
alive_interval=10.0
ZLM_CONF

# 替换模板中的 secret 占位符（安装时由 install.sh 填入）
echo "secret=__ZLM_SECRET__" >> "${STAGE_DIR}/zlm/config.ini.template"

# 后端配置模板
cat > "${STAGE_DIR}/conf/application.yml.template" << 'APP_CONF'
spring:
  application:
    name: gbt28181
  datasource:
    url: jdbc:h2:file:__INSTALL_DIR__/data/gbt28181
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

server:
  port: __APP_PORT__

app:
  secret-key: __APP_SECRET_KEY__

sip:
  default-port: __SIP_PORT__
  default-transport: UDP

zlm:
  base-url: http://127.0.0.1:__ZLM_HTTP_PORT__
  rtp-ip: __LOCAL_IP__
  http-port: __ZLM_HTTP_PORT__
  secret: __ZLM_SECRET__

ivs1900:
  base-url: __IVS1900_BASE_URL__
  username: __IVS1900_USERNAME__
  password: __IVS1900_PASSWORD__
  sync-interval: 60000
  keepalive-interval: 60000

logging:
  level:
    com.example.gbt28181: INFO
    IVS1900-HTTP: INFO
  file:
    name: __INSTALL_DIR__/logs/gbt28181.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 7
APP_CONF

# 复制 install.sh 和 start.sh
cp "${SCRIPT_DIR}/install.sh" "${STAGE_DIR}/install.sh"
cp "${SCRIPT_DIR}/start.sh" "${STAGE_DIR}/start.sh"
chmod +x "${STAGE_DIR}/install.sh" "${STAGE_DIR}/start.sh"

# ── 4. 打包 ───────────────────────────────────────────────────
echo "打包..."
mkdir -p "${DIST_DIR}"
cd /tmp
tar -czf "${DIST_DIR}/${PKG_NAME}.tar.gz" "${PKG_NAME}"
cp "${SCRIPT_DIR}/install.sh" "${DIST_DIR}/install.sh"
cp "${SCRIPT_DIR}/start.sh" "${DIST_DIR}/start.sh"
rm -rf "${STAGE_DIR}"

echo ""
echo "=== 打包完成 ==="
echo "产物目录: ${DIST_DIR}/"
echo "  ├── ${PKG_NAME}.tar.gz"
echo "  ├── install.sh"
echo "  └── start.sh"
echo ""
echo "部署方式：将三个文件上传到目标机器，执行 bash install.sh"
