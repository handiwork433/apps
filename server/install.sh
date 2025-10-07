#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "[ERROR] Run this script as root. Example: sudo bash server/install.sh" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR"
ENV_EXAMPLE_FILE="$APP_DIR/.env.example"
ENV_FILE="$APP_DIR/.env"
SERVICE_NAME="telegram-text-app"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
APP_USER="${SUDO_USER:-root}"
APP_GROUP="$(id -gn "$APP_USER")"

log() {
  echo "[install] $*"
}

ensure_packages() {
  log "Updating apt cache and installing prerequisites..."
  apt-get update -y
  apt-get install -y ca-certificates curl gnupg lsb-release git
}

install_node() {
  if command -v node >/dev/null 2>&1; then
    local major
    major="$(node -p "process.versions.node.split('.')[0]")"
    if [[ "$major" -ge 18 ]]; then
      log "Node.js $(node -v) already installed."
      return
    fi
    log "Existing Node.js version is too old; upgrading to 18.x."
  else
    log "Node.js not found; installing 18.x LTS."
  fi

  local keyring="/usr/share/keyrings/nodesource.gpg"
  curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o "$keyring"
  local distro
  distro="$(lsb_release -cs)"
  echo "deb [signed-by=$keyring] https://deb.nodesource.com/node_18.x $distro main" >/etc/apt/sources.list.d/nodesource.list
  echo "deb-src [signed-by=$keyring] https://deb.nodesource.com/node_18.x $distro main" >>/etc/apt/sources.list.d/nodesource.list
  apt-get update -y
  apt-get install -y nodejs
}

install_pm2() {
  if command -v pm2 >/dev/null 2>&1; then
    log "PM2 already installed."
    return
  fi
  log "Installing PM2 globally..."
  npm install -g pm2
}

install_dependencies() {
  log "Installing Node.js dependencies for the server..."
  sudo -u "$APP_USER" --preserve-env=PATH env NODE_ENV=production npm --prefix "$APP_DIR" install --omit=dev
}

prepare_env_file() {
  if [[ -f "$ENV_FILE" ]]; then
    log ".env already exists; skipping copy."
    return
  fi

  if [[ ! -f "$ENV_EXAMPLE_FILE" ]]; then
    echo "[ERROR] Missing $ENV_EXAMPLE_FILE. Cannot create .env automatically." >&2
    exit 1
  fi

  log "Creating .env from template..."
  cp "$ENV_EXAMPLE_FILE" "$ENV_FILE"
  chown "$APP_USER":"$APP_GROUP" "$ENV_FILE"
}

create_systemd_service() {
  local node_bin
  node_bin="$(command -v node)"

  log "Creating systemd service at $SERVICE_FILE..."
  cat <<SERVICE >"$SERVICE_FILE"
[Unit]
Description=Telegram text subscription backend
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$APP_USER
Group=$APP_GROUP
WorkingDirectory=$APP_DIR
EnvironmentFile=$ENV_FILE
ExecStart=$node_bin $APP_DIR/index.js
Restart=on-failure
RestartSec=5s

[Install]
WantedBy=multi-user.target
SERVICE

  chmod 0644 "$SERVICE_FILE"
  systemctl daemon-reload
}

start_service_if_ready() {
  if [[ ! -f "$ENV_FILE" ]]; then
    log "No .env found; skipping service start."
    return
  fi

  if grep -q "__PASTE_TELEGRAM_BOT_TOKEN__" "$ENV_FILE"; then
    log "Detected placeholder Telegram bot token. Edit $ENV_FILE before starting the service."
    return
  fi

  log "Enabling and starting $SERVICE_NAME service..."
  systemctl enable "$SERVICE_NAME"
  systemctl restart "$SERVICE_NAME"
}

adjust_permissions() {
  log "Ensuring $APP_DIR belongs to $APP_USER:$APP_GROUP..."
  chown -R "$APP_USER":"$APP_GROUP" "$APP_DIR"
}

main() {
  ensure_packages
  install_node
  install_pm2
  prepare_env_file
  adjust_permissions
  install_dependencies
  create_systemd_service
  start_service_if_ready

  log "Setup complete."
  log "Edit $ENV_FILE to configure tokens and settings, then run:"
  log "  sudo systemctl restart $SERVICE_NAME"
  log "Check status with:"
  log "  sudo systemctl status $SERVICE_NAME"
}

main "$@"
