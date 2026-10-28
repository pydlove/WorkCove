#!/bin/bash
set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)/log"
BACKEND_LABEL="com.workcove.local.backend"
LAUNCHD_DOMAIN="gui/$(id -u)"

stop_service() {
  local name="$1"
  local pid_file="$2"

  if [ ! -f "$pid_file" ]; then
    echo "$name 未运行"
    return
  fi

  local pid
  pid="$(cat "$pid_file")"
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "$name 已停止"
    rm -f "$pid_file"
    return
  fi

  echo "停止 $name (PID: $pid)..."
  kill "$pid" 2>/dev/null || true
  for _ in {1..10}; do
    if ! kill -0 "$pid" 2>/dev/null; then
      rm -f "$pid_file"
      echo "$name 已停止"
      return
    fi
    sleep 1
  done
  echo "$name 未在 10 秒内退出，请检查进程 $pid"
}

stop_service "前端服务" "$LOG_DIR/frontend.pid"

if command -v launchctl >/dev/null 2>&1 \
  && launchctl print "$LAUNCHD_DOMAIN/$BACKEND_LABEL" >/dev/null 2>&1; then
  echo "停止后端服务 (launchd)..."
  launchctl remove "$BACKEND_LABEL"
  rm -f "$LOG_DIR/backend.pid"
  echo "后端服务 已停止"
else
  stop_service "后端服务" "$LOG_DIR/backend.pid"
fi
