#!/usr/bin/env bash
set -euo pipefail

SERVICE="${SERVICE:-pubrec-auth}"
JAR_SRC="${JAR_SRC:-/tmp/backend.jar}"
JAR_DEST="${JAR_DEST:-/opt/pubrec/auth/backend.jar}"
APP_USER="${APP_USER:-pubrec}"

if [[ ! -f "$JAR_SRC" ]]; then
  echo "Jar not found: $JAR_SRC" >&2
  exit 1
fi

sudo systemctl stop "$SERVICE"
sudo mv "$JAR_SRC" "$JAR_DEST"
sudo chown "$APP_USER:$APP_USER" "$JAR_DEST"
sudo systemctl start "$SERVICE"
sudo systemctl status "$SERVICE" --no-pager
