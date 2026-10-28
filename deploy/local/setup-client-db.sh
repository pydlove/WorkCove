#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SQL_FILE="$PROJECT_DIR/deploy/mysql/setup-client-db.sql"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
ADMIN_USER="${MYSQL_ADMIN_USER:-root}"

if ! command -v "$MYSQL_BIN" >/dev/null 2>&1; then
  echo "未找到 mysql 客户端，请先安装 MySQL 客户端"
  exit 1
fi

if [ -z "${MYSQL_ADMIN_PASSWORD:-}" ]; then
  if [ ! -t 0 ]; then
    echo "请在终端中运行此脚本，或通过 MYSQL_ADMIN_PASSWORD 提供管理员密码"
    exit 1
  fi
  read -r -s -p "MySQL 管理员密码（${ADMIN_USER}）: " MYSQL_ADMIN_PASSWORD
  echo
fi

MYSQL_PWD="$MYSQL_ADMIN_PASSWORD" \
  "$MYSQL_BIN" \
  --host=127.0.0.1 \
  --port=3306 \
  --user="$ADMIN_USER" \
  --default-character-set=utf8mb4 \
  <"$SQL_FILE"

unset MYSQL_ADMIN_PASSWORD
echo "已创建 workcove_client 并授予 workcove_app 独立权限"
