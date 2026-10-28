#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
"$SCRIPT_DIR/stop-all.sh"
echo
sleep 1
"$SCRIPT_DIR/start-all.sh"
