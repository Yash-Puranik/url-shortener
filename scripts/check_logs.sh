#!/usr/bin/env bash

set -euo pipefail
PROJECT_DIR="/path/to/your/project"
WINDOW="5m"
err_array=("ERROR" "WARN" "ERR" "Exception" "FATAL" "CRITICAL" "Traceback" "Failed" "Unable" "Cannot" "Refused" "Connection refused" "Connection reset by peer")
FOUND=0
docker_logs=$(docker compose logs --no-color --since="$WINDOW" app db 2>&1 || true)

for err_kw in "${err_array[@]}"; do
    MATCHES=$(printf '%s\n' "$docker_logs" | grep -F -- "$err_kw" || true)
    if [ -n "$MATCHES" ]; then
        FOUND=1
        echo "Found error: $err_kw"
        {
            echo "Creating a log file"
            echo "=== Keyword [$err_kw] at $(date '+%Y-%m-%d %H:%M:%S') ==="
        } >> "$PROJECT_DIR/logs/error_logs_$(date +%Y%m%d_%H%M%S).log"
    fi
done
