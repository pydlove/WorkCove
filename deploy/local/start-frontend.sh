#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
WEB_DIR="$PROJECT_DIR/workcove-web"
LOG_DIR="$PROJECT_DIR/log"
PID_FILE="$LOG_DIR/frontend.pid"
LOG_FILE="$LOG_DIR/frontend.log"
INSTALL_LOG="$LOG_DIR/frontend-install.log"
APP_URL="http://127.0.0.1:32581/login"

mkdir -p "$LOG_DIR"

if [ -f "$PID_FILE" ]; then
  PID="$(cat "$PID_FILE")"
  if kill -0 "$PID" 2>/dev/null; then
    echo "前端服务已在运行 (PID: $PID)"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

if lsof -nP -iTCP:32581 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "端口 32581 已被其他进程占用，请先运行 ./deploy/local/status.sh"
  exit 1
fi

if [ ! -x "$WEB_DIR/node_modules/.bin/vite" ]; then
  echo "未安装前端依赖，正在执行 pnpm install..."
  (
    cd "$WEB_DIR"
    pnpm install
  ) >>"$INSTALL_LOG" 2>&1
fi

{
  printf '\n[%s] starting frontend\n' "$(date '+%Y-%m-%d %H:%M:%S')"
  echo "port: 32581"
} >>"$LOG_FILE"

(
  cd "$WEB_DIR"
  nohup ./node_modules/.bin/vite >>"$LOG_FILE" 2>&1 &
  echo $! >"$PID_FILE"
)
PID="$(cat "$PID_FILE")"

for _ in {1..20}; do
  if curl -fsS "$APP_URL" >/dev/null 2>&1; then
    echo "前端服务启动成功 (PID: $PID)"
    echo "日志: $LOG_FILE"
    exit 0
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "前端服务启动失败，最近日志:"
    tail -n 30 "$LOG_FILE"
    rm -f "$PID_FILE"
    exit 1
  fi
  sleep 1
done

echo "前端服务未在 20 秒内就绪，请查看日志: $LOG_FILE"
exit 1
