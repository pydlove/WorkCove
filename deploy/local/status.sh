#!/bin/bash
set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_DIR="$PROJECT_DIR/log"

check_service() {
  local name="$1"
  local pid_file="$2"
  local url="$3"

  if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
    local health="不可用"
    if curl -fsS "$url" >/dev/null 2>&1; then
      health="正常"
    fi
    echo "$name: 运行中 (PID: $(cat "$pid_file"), 健康检查: $health)"
  else
    echo "$name: 已停止"
  fi
}

check_service "后端服务" "$LOG_DIR/backend.pid" "http://127.0.0.1:8790/actuator/health"
check_service "前端服务" "$LOG_DIR/frontend.pid" "http://127.0.0.1:32581/login"
echo "日志目录: $LOG_DIR"
