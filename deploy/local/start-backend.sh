#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_DIR="$PROJECT_DIR/log"
PID_FILE="$LOG_DIR/backend.pid"
LOG_FILE="$LOG_DIR/backend.log"
BUILD_LOG="$LOG_DIR/backend-build.log"
HEALTH_URL="http://127.0.0.1:8790/actuator/health"

mkdir -p "$LOG_DIR"

if [ -f "$PID_FILE" ]; then
  PID="$(cat "$PID_FILE")"
  if kill -0 "$PID" 2>/dev/null; then
    echo "后端服务已在运行 (PID: $PID)"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

if lsof -nP -iTCP:8790 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "端口 8790 已被其他进程占用，请先运行 ./deploy/local/status.sh"
  exit 1
fi

JAR="$(find "$PROJECT_DIR/workcove-boot/target" -maxdepth 1 -type f \
  -name 'workcove-boot-*.jar' ! -name '*.original' 2>/dev/null | head -n 1)"

if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
  echo "未找到后端 JAR，正在构建..."
  (
    cd "$PROJECT_DIR"
    mvn -q -DskipTests package
  ) >>"$BUILD_LOG" 2>&1
  JAR="$(find "$PROJECT_DIR/workcove-boot/target" -maxdepth 1 -type f \
    -name 'workcove-boot-*.jar' ! -name '*.original' | head -n 1)"
fi

if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
  echo "后端 JAR 不存在，构建日志: $BUILD_LOG"
  exit 1
fi

{
  printf '\n[%s] starting backend\n' "$(date '+%Y-%m-%d %H:%M:%S')"
  echo "jar: $JAR"
} >>"$LOG_FILE"

nohup java -jar "$JAR" >>"$LOG_FILE" 2>&1 &
PID=$!
echo "$PID" >"$PID_FILE"

for _ in {1..30}; do
  if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
    echo "后端服务启动成功 (PID: $PID)"
    echo "日志: $LOG_FILE"
    exit 0
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "后端服务启动失败，最近日志:"
    tail -n 30 "$LOG_FILE"
    rm -f "$PID_FILE"
    exit 1
  fi
  sleep 1
done

echo "后端服务未在 30 秒内就绪，请查看日志: $LOG_FILE"
exit 1
