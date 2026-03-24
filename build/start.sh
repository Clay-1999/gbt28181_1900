#!/usr/bin/env bash
# start.sh — gbt28181 服务管理脚本
set -euo pipefail

INSTALL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="${INSTALL_DIR}/run"
LOG_DIR="${INSTALL_DIR}/logs"
ZLM_PID_FILE="${RUN_DIR}/zlm.pid"
APP_PID_FILE="${RUN_DIR}/app.pid"

ZLM_BIN="/usr/data/MediaServer"
ZLM_CONF="${INSTALL_DIR}/zlm/config.ini"
ZLM_LOG="${LOG_DIR}/zlm.log"

APP_JAR="${INSTALL_DIR}/lib/gbt28181.jar"
APP_CONF="${INSTALL_DIR}/conf/application.yml"
APP_LOG="${LOG_DIR}/app.log"

# 使用系统预装的 JDK 21
JAVA_CMD="java"

mkdir -p "${RUN_DIR}" "${LOG_DIR}"

# ── 工具函数 ───────────────────────────────────────────────────

is_running() {
    local pid_file="$1"
    if [ -f "${pid_file}" ]; then
        local pid
        pid=$(cat "${pid_file}")
        if kill -0 "${pid}" 2>/dev/null; then
            return 0
        fi
    fi
    return 1
}

stop_process() {
    local name="$1"
    local pid_file="$2"
    if [ ! -f "${pid_file}" ]; then
        echo "  ${name}: 未运行（无 PID 文件）"
        return
    fi
    local pid
    pid=$(cat "${pid_file}")
    if ! kill -0 "${pid}" 2>/dev/null; then
        echo "  ${name}: 进程已退出，清理 PID 文件"
        rm -f "${pid_file}"
        return
    fi
    echo "  停止 ${name} (PID ${pid})..."
    kill -TERM "${pid}"
    local count=0
    while kill -0 "${pid}" 2>/dev/null && [ ${count} -lt 30 ]; do
        sleep 1
        count=$((count + 1))
    done
    if kill -0 "${pid}" 2>/dev/null; then
        echo "  ${name} 未在 30s 内退出，强制终止"
        kill -KILL "${pid}" 2>/dev/null || true
    fi
    rm -f "${pid_file}"
    echo "  ${name} 已停止"
}

# ── 子命令 ─────────────────────────────────────────────────────

cmd_start() {
    echo "=== 启动 gbt28181 服务 ==="

    # 启动 ZLMediaKit
    if is_running "${ZLM_PID_FILE}"; then
        echo "  ZLMediaKit: 已在运行 (PID $(cat "${ZLM_PID_FILE}"))"
    else
        echo "  启动 ZLMediaKit..."
        cd /usr/data
        nohup "${ZLM_BIN}" -c "${ZLM_CONF}" >> "${ZLM_LOG}" 2>&1 &
        echo $! > "${ZLM_PID_FILE}"
        sleep 2
        if is_running "${ZLM_PID_FILE}"; then
            echo "  ZLMediaKit 已启动 (PID $(cat "${ZLM_PID_FILE}"))"
        else
            echo "  [ERROR] ZLMediaKit 启动失败，查看日志: ${ZLM_LOG}"
            exit 1
        fi
    fi

    # 启动后端 JAR
    if is_running "${APP_PID_FILE}"; then
        echo "  gbt28181: 已在运行 (PID $(cat "${APP_PID_FILE}"))"
    else
        echo "  启动 gbt28181 后端..."
        cd "${INSTALL_DIR}"
        nohup "${JAVA_CMD}" -jar "${APP_JAR}" \
            --spring.config.location="file:${APP_CONF}" \
            >> "${APP_LOG}" 2>&1 &
        echo $! > "${APP_PID_FILE}"
        sleep 5
        if is_running "${APP_PID_FILE}"; then
            echo "  gbt28181 已启动 (PID $(cat "${APP_PID_FILE}"))"
        else
            echo "  [ERROR] gbt28181 启动失败，查看日志: ${APP_LOG}"
            exit 1
        fi
    fi

    echo ""
    echo "服务已启动，日志目录: ${LOG_DIR}/"
    # 读取端口配置
    local port
    port=$(grep '^  port:' "${APP_CONF}" 2>/dev/null | awk '{print $2}' | head -1)
    port="${port:-8080}"
    local local_ip
    local_ip=$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' | head -1)
    local_ip="${local_ip:-localhost}"
    echo "访问地址: http://${local_ip}:${port}"
}

cmd_stop() {
    echo "=== 停止 gbt28181 服务 ==="
    stop_process "gbt28181" "${APP_PID_FILE}"
    stop_process "ZLMediaKit" "${ZLM_PID_FILE}"
    echo "所有服务已停止"
}

cmd_status() {
    echo "=== gbt28181 服务状态 ==="
    if is_running "${ZLM_PID_FILE}"; then
        echo "  ZLMediaKit : running  (PID $(cat "${ZLM_PID_FILE}"))"
    else
        echo "  ZLMediaKit : stopped"
    fi
    if is_running "${APP_PID_FILE}"; then
        echo "  gbt28181   : running  (PID $(cat "${APP_PID_FILE}"))"
    else
        echo "  gbt28181   : stopped"
    fi
}

cmd_restart() {
    cmd_stop
    echo ""
    cmd_start
}

# ── 入口 ───────────────────────────────────────────────────────

case "${1:-}" in
    start)   cmd_start ;;
    stop)    cmd_stop ;;
    status)  cmd_status ;;
    restart) cmd_restart ;;
    *)
        echo "Usage: $(basename "$0") {start|stop|restart|status}"
        exit 1
        ;;
esac
