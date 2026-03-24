#!/usr/bin/env bash
# install.sh — gbt28181 安装脚本（EulerOS v2r13 aarch64）
set -euo pipefail

INSTALL_DIR="/opt/gbt28181"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 默认配置（可通过环境变量覆盖）
APP_PORT="${APP_PORT:-8080}"
SIP_PORT="${SIP_PORT:-15060}"
ZLM_HTTP_PORT="${ZLM_HTTP_PORT:-18080}"
ZLM_SECRET="${ZLM_SECRET:-$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | head -c 32 2>/dev/null || echo 'gbt28181-zlm-secret')}"
APP_SECRET_KEY="${APP_SECRET_KEY:-gbt28181-secret}"
IVS1900_BASE_URL="${IVS1900_BASE_URL:-https://192.168.1.1:18531}"
IVS1900_USERNAME="${IVS1900_USERNAME:-admin}"
IVS1900_PASSWORD="${IVS1900_PASSWORD:-admin123}"

echo "=== gbt28181 安装程序 ==="
echo "安装目录: ${INSTALL_DIR}"
echo ""

# ── 1. 检查 Java 版本 ──────────────────────────────────────────
echo "[1/4] 检查运行环境..."
# 环境上已预装 JDK 21，检查 java 命令是否可用
if ! java -version 2>&1 | grep -q '21\|openjdk'; then
    echo "  [WARNING] 未检测到 JDK 21，请确认环境上已安装 jre-21 并配置了环境变量"
else
    echo "  JDK 21 ✓"
fi
# 检查 ZLMediaKit
if [ ! -x "/usr/data/MediaServer" ]; then
    echo "  [WARNING] 未找到 /usr/data/MediaServer，请确认 ZLMediaKit 已部署到目标机器"
else
    echo "  ZLMediaKit /usr/data/MediaServer ✓"
fi

# ── 2. 查找压缩包 ──────────────────────────────────────────────
PKG_FILE=$(ls "${SCRIPT_DIR}"/gbt28181-*-euleros-aarch64.tar.gz 2>/dev/null | head -1)
if [ -z "${PKG_FILE}" ]; then
    echo "[ERROR] 未找到 gbt28181-*-euleros-aarch64.tar.gz"
    echo "  请确保压缩包与 install.sh 在同一目录"
    exit 1
fi
echo "  安装包: $(basename "${PKG_FILE}") ✓"

# ── 3. 解压安装 ────────────────────────────────────────────────
echo "[2/4] 解压安装包..."
mkdir -p "${INSTALL_DIR}"

# 保留已有配置文件
CONF_EXISTS=false
if [ -f "${INSTALL_DIR}/conf/application.yml" ]; then
    CONF_EXISTS=true
    cp "${INSTALL_DIR}/conf/application.yml" /tmp/gbt28181_app_yml.bak
fi
ZLM_CONF_EXISTS=false
if [ -f "${INSTALL_DIR}/zlm/config.ini" ]; then
    ZLM_CONF_EXISTS=true
    cp "${INSTALL_DIR}/zlm/config.ini" /tmp/gbt28181_zlm_ini.bak
fi

# 解压（去掉顶层目录）
tar -xzf "${PKG_FILE}" -C "${INSTALL_DIR}" --strip-components=1
mkdir -p "${INSTALL_DIR}"/{run,logs,data}
cp "${SCRIPT_DIR}/start.sh" "${INSTALL_DIR}/start.sh"
chmod +x "${INSTALL_DIR}/start.sh"

# 恢复已有配置
if [ "${CONF_EXISTS}" = true ]; then
    cp /tmp/gbt28181_app_yml.bak "${INSTALL_DIR}/conf/application.yml"
    echo "  已保留原有 application.yml 配置"
fi
if [ "${ZLM_CONF_EXISTS}" = true ]; then
    cp /tmp/gbt28181_zlm_ini.bak "${INSTALL_DIR}/zlm/config.ini"
    echo "  已保留原有 ZLM config.ini 配置"
fi

# ── 4. 生成配置文件 ────────────────────────────────────────────
echo "[3/4] 生成配置文件..."

# 获取本机 IP
LOCAL_IP=$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -1)
LOCAL_IP="${LOCAL_IP:-127.0.0.1}"

if [ "${CONF_EXISTS}" = false ]; then
    mkdir -p "${INSTALL_DIR}/conf"
    sed \
        -e "s|__INSTALL_DIR__|${INSTALL_DIR}|g" \
        -e "s|__APP_PORT__|${APP_PORT}|g" \
        -e "s|__SIP_PORT__|${SIP_PORT}|g" \
        -e "s|__ZLM_HTTP_PORT__|${ZLM_HTTP_PORT}|g" \
        -e "s|__ZLM_SECRET__|${ZLM_SECRET}|g" \
        -e "s|__LOCAL_IP__|${LOCAL_IP}|g" \
        -e "s|__APP_SECRET_KEY__|${APP_SECRET_KEY}|g" \
        -e "s|__IVS1900_BASE_URL__|${IVS1900_BASE_URL}|g" \
        -e "s|__IVS1900_USERNAME__|${IVS1900_USERNAME}|g" \
        -e "s|__IVS1900_PASSWORD__|${IVS1900_PASSWORD}|g" \
        "${INSTALL_DIR}/conf/application.yml.template" > "${INSTALL_DIR}/conf/application.yml"
    echo "  生成 application.yml（本机 IP: ${LOCAL_IP}）"
fi

if [ "${ZLM_CONF_EXISTS}" = false ]; then
    sed \
        -e "s|__ZLM_HTTP_PORT__|${ZLM_HTTP_PORT}|g" \
        -e "s|__ZLM_SECRET__|${ZLM_SECRET}|g" \
        "${INSTALL_DIR}/zlm/config.ini.template" > "${INSTALL_DIR}/zlm/config.ini"
    echo "  生成 ZLM config.ini"
fi

# ── 5. 安装完成提示 ────────────────────────────────────────────
echo "[4/4] 安装完成"
echo ""
echo "========================================"
echo " gbt28181 安装成功！"
echo "========================================"
echo ""
echo " 安装目录: ${INSTALL_DIR}"
echo " 访问地址: http://${LOCAL_IP}:${APP_PORT}"
echo ""
echo " 启动服务:"
echo "   bash ${INSTALL_DIR}/start.sh start"
echo ""
echo " 管理命令:"
echo "   bash ${INSTALL_DIR}/start.sh {start|stop|restart|status}"
echo ""
echo " 配置文件:"
echo "   ${INSTALL_DIR}/conf/application.yml"
echo "   ${INSTALL_DIR}/zlm/config.ini"
echo ""
echo " 日志目录: ${INSTALL_DIR}/logs/"
echo "========================================"
