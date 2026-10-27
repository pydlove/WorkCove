#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
"$SCRIPT_DIR/start-backend.sh"
echo
"$SCRIPT_DIR/start-frontend.sh"
echo
echo "WorkCove 本地服务已启动"
echo "工作台: http://127.0.0.1:32581"
echo "日志目录: $(cd "$SCRIPT_DIR/../.." && pwd)/log"
