#!/usr/bin/env bash
#
# deploy/uninstall.sh
# Uninstall the ics205 systemd service and application from a remote Linux server via SSH.
#
# Usage:
#   ./deploy/uninstall.sh [options] [user@]hostname
#
# Examples:
#   ./deploy/uninstall.sh ics-server.local
#   ./deploy/uninstall.sh ubuntu@192.168.1.100 -p 2222
#   ./deploy/uninstall.sh --purge admin@production.server
#

set -euo pipefail

# Default settings
SERVICE_NAME="ics205.service"
REMOTE_USER="ics205"
REMOTE_GROUP="ics205"
REMOTE_BASE_DIR="/home/${REMOTE_USER}"
REMOTE_APP_DIR="${REMOTE_BASE_DIR}/app"
REMOTE_CONFIG_DIR="${REMOTE_BASE_DIR}/config"
REMOTE_DATA_DIR="${REMOTE_BASE_DIR}/data"
REMOTE_INSTALL_DIR="${REMOTE_BASE_DIR}/install"

# CLI Arguments
TARGET_HOST=""
SSH_PORT=""
SSH_KEY=""
PURGE_DATA=false

print_usage() {
  cat << EOF
Usage: $(basename "$0") [options] [user@]hostname

Uninstall the ics205 application and systemd service on a remote Linux server.

Normal uninstall stops and disables the systemd service, removes the service unit,
and removes the application JAR. Persistent application data (/home/ics205/data)
and configuration (/home/ics205/config) are preserved by default.

Arguments:
  [user@]hostname         Remote target SSH host (e.g. ubuntu@192.168.1.50)

Options:
  --purge                 Completely delete data directory and user account (DESTRUCTIVE)
  -p, --port <port>       SSH port on the remote host (default: 22)
  -i, --identity <file>   SSH private key identity file
  -h, --help              Display this help message and exit

EOF
}

# Parse options
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      print_usage
      exit 0
      ;;
    --purge)
      PURGE_DATA=true
      shift
      ;;
    -p|--port)
      SSH_PORT="$2"
      shift 2
      ;;
    -i|--identity)
      SSH_KEY="$2"
      shift 2
      ;;
    -*)
      echo "Error: Unknown option $1" >&2
      print_usage
      exit 1
      ;;
    *)
      if [[ -z "${TARGET_HOST}" ]]; then
        TARGET_HOST="$1"
      else
        echo "Error: Multiple target hosts specified ('${TARGET_HOST}' and '$1')" >&2
        print_usage
        exit 1
      fi
      shift
      ;;
  esac
done

if [[ -z "${TARGET_HOST}" ]]; then
  echo "Error: Target host is required." >&2
  print_usage
  exit 1
fi

# Configure SSH options
SSH_OPTS=()
if [[ -n "${SSH_PORT}" ]]; then
  SSH_OPTS+=("-p" "${SSH_PORT}")
fi
if [[ -n "${SSH_KEY}" ]]; then
  SSH_OPTS+=("-i" "${SSH_KEY}")
fi

echo "=================================================="
echo "ICS-205 Remote Uninstall"
echo "Target Host:       ${TARGET_HOST}"
echo "Purge Data:        ${PURGE_DATA}"
echo "=================================================="

# 1. Test SSH connectivity
echo "--> Testing SSH connection to ${TARGET_HOST}..."
ssh "${SSH_OPTS[@]}" "${TARGET_HOST}" "uname -s" >/dev/null

LOCAL_REMOTE_SCRIPT=$(mktemp)
cat << 'REMOTESCRIPT' > "${LOCAL_REMOTE_SCRIPT}"
set -euo pipefail

SERVICE_NAME="$1"
REMOTE_USER="$2"
REMOTE_GROUP="$3"
REMOTE_BASE_DIR="$4"
REMOTE_APP_DIR="$5"
REMOTE_CONFIG_DIR="$6"
REMOTE_DATA_DIR="$7"
REMOTE_INSTALL_DIR="$8"
PURGE_DATA="$9"

echo "--> Stopping and disabling ${SERVICE_NAME}..."
if sudo systemctl is-active --quiet "${SERVICE_NAME}" 2>/dev/null; then
  sudo systemctl stop "${SERVICE_NAME}"
fi
if sudo systemctl is-enabled --quiet "${SERVICE_NAME}" 2>/dev/null; then
  sudo systemctl disable "${SERVICE_NAME}"
fi

echo "--> Removing systemd service unit..."
if [ -f "/etc/systemd/system/${SERVICE_NAME}" ]; then
  sudo rm -f "/etc/systemd/system/${SERVICE_NAME}"
fi

echo "--> Reloading systemd daemon..."
sudo systemctl daemon-reload
sudo systemctl reset-failed "${SERVICE_NAME}" 2>/dev/null || true

echo "--> Removing application binaries and install artifacts..."
sudo rm -rf "${REMOTE_APP_DIR}" "${REMOTE_INSTALL_DIR}"

if [ "${PURGE_DATA}" = "true" ]; then
  echo "--> Purging all persistent data, configuration, and home directory (${REMOTE_BASE_DIR})..."
  sudo rm -rf "${REMOTE_BASE_DIR}"

  if id -u "${REMOTE_USER}" >/dev/null 2>&1; then
    echo "--> Removing system user ${REMOTE_USER}..."
    sudo userdel "${REMOTE_USER}" 2>/dev/null || true
  fi
  if getent group "${REMOTE_GROUP}" >/dev/null 2>&1; then
    echo "--> Removing system group ${REMOTE_GROUP}..."
    sudo groupdel "${REMOTE_GROUP}" 2>/dev/null || true
  fi
  echo "Complete uninstall and purge finished."
else
  echo "--> Preserved configuration in ${REMOTE_CONFIG_DIR}"
  echo "--> Preserved persistent data in ${REMOTE_DATA_DIR}"
  echo "Uninstall finished. To remove persistent data, run uninstall with --purge."
fi
REMOTESCRIPT

cleanup() {
  rm -f "${LOCAL_REMOTE_SCRIPT:-}"
}
trap cleanup EXIT

echo "--> Executing uninstall on remote server..."
ssh "${SSH_OPTS[@]}" "${TARGET_HOST}" bash -s -- \
  "${SERVICE_NAME}" \
  "${REMOTE_USER}" \
  "${REMOTE_GROUP}" \
  "${REMOTE_BASE_DIR}" \
  "${REMOTE_APP_DIR}" \
  "${REMOTE_CONFIG_DIR}" \
  "${REMOTE_DATA_DIR}" \
  "${REMOTE_INSTALL_DIR}" \
  "${PURGE_DATA}" < "${LOCAL_REMOTE_SCRIPT}"

echo "=================================================="
echo "Uninstall complete on ${TARGET_HOST}!"
echo "=================================================="
