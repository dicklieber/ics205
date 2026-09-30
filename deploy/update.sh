#!/usr/bin/env bash
#
# deploy/update.sh
# Update the ics205.jar artifact on a remote Linux server via SSH and restart the systemd service.
#
# Usage:
#   ./deploy/update.sh [options] [user@]hostname
#
# Examples:
#   ./deploy/update.sh ics-server.local
#   ./deploy/update.sh ubuntu@192.168.1.100 -p 2222
#   ./deploy/update.sh --build admin@production.server
#

set -euo pipefail

# Determine repository root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Default settings
DEFAULT_JAR_PATH="${REPO_ROOT}/out/web/assembly.dest/out.jar"
DEFAULT_SERVICE_PATH="${SCRIPT_DIR}/ics205.service"
REMOTE_INSTALL_DIR="/opt/ics205"
SERVICE_NAME="ics205.service"
REMOTE_USER="ics205"
REMOTE_GROUP="ics205"

# CLI Arguments
TARGET_HOST=""
SSH_PORT=""
SSH_KEY=""
JAR_PATH="${DEFAULT_JAR_PATH}"
SERVICE_PATH="${DEFAULT_SERVICE_PATH}"
UPDATE_SERVICE=false
DO_BUILD=false
RESTART_SERVICE=true

print_usage() {
  cat << EOF
Usage: $(basename "$0") [options] [user@]hostname

Update the ics205.jar on a remote Linux server and restart the systemd service.

Arguments:
  [user@]hostname         Remote target SSH host (e.g. ubuntu@192.168.1.50)

Options:
  -b, --build             Build the fat JAR (./mill web.assembly) before deploying
  -j, --jar <path>        Custom local JAR path (default: out/web/assembly.dest/out.jar)
  -s, --sync-service      Also update /etc/systemd/system/ics205.service and daemon-reload
  -p, --port <port>       SSH port on the remote host (default: 22)
  -i, --identity <file>   SSH private key identity file
  --no-restart            Do not restart the systemd service after updating
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
    -b|--build)
      DO_BUILD=true
      shift
      ;;
    -j|--jar)
      JAR_PATH="$2"
      shift 2
      ;;
    -s|--sync-service)
      UPDATE_SERVICE=true
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
    --no-restart)
      RESTART_SERVICE=false
      shift
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

# Build fat JAR if requested or if missing and in repo root
if [[ "${DO_BUILD}" == true ]] || [[ ! -f "${JAR_PATH}" ]]; then
  if [[ ! -f "${JAR_PATH}" ]]; then
    echo "Assembly fat JAR not found at: ${JAR_PATH}"
  fi
  echo "Building fat JAR with Mill (./mill web.assembly)..."
  (cd "${REPO_ROOT}" && ./mill web.assembly)
fi

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Error: Assembly fat JAR not found at ${JAR_PATH}" >&2
  echo "Run './mill web.assembly' or pass --build flag." >&2
  exit 1
fi

if [[ "${UPDATE_SERVICE}" == true ]] && [[ ! -f "${SERVICE_PATH}" ]]; then
  echo "Error: Systemd service unit not found at ${SERVICE_PATH}" >&2
  exit 1
fi

# Configure SSH and SCP command options
SSH_OPTS=()
SCP_OPTS=()
if [[ -n "${SSH_PORT}" ]]; then
  SSH_OPTS+=("-p" "${SSH_PORT}")
  SCP_OPTS+=("-P" "${SSH_PORT}")
fi
if [[ -n "${SSH_KEY}" ]]; then
  SSH_OPTS+=("-i" "${SSH_KEY}")
  SCP_OPTS+=("-i" "${SSH_KEY}")
fi

echo "=================================================="
echo "ICS-205 Remote Update"
echo "Target Host:       ${TARGET_HOST}"
echo "Local JAR:         ${JAR_PATH}"
echo "Remote Target:     ${REMOTE_INSTALL_DIR}/ics205.jar"
echo "Sync Service Unit: ${UPDATE_SERVICE}"
echo "Restart Service:   ${RESTART_SERVICE}"
echo "=================================================="

# 1. Test SSH connectivity
echo "--> Testing SSH connection to ${TARGET_HOST}..."
ssh "${SSH_OPTS[@]}" "${TARGET_HOST}" "uname -s" >/dev/null

# 2. Create remote staging directory
echo "--> Creating remote temporary staging directory..."
REMOTE_TMP=$(ssh "${SSH_OPTS[@]}" "${TARGET_HOST}" "mktemp -d /tmp/ics205-update.XXXXXX")

LOCAL_REMOTE_SCRIPT=$(mktemp)
cat << 'REMOTESCRIPT' > "${LOCAL_REMOTE_SCRIPT}"
set -euo pipefail

REMOTE_TMP="$1"
REMOTE_INSTALL_DIR="$2"
SERVICE_NAME="$3"
REMOTE_USER="$4"
REMOTE_GROUP="$5"
HAS_SERVICE_UNIT="$6"
RESTART_SERVICE="$7"

# Ensure remote install directory exists
sudo mkdir -p "${REMOTE_INSTALL_DIR}"

# Replace JAR atomically
echo "Updating ${REMOTE_INSTALL_DIR}/ics205.jar..."
if command -v install >/dev/null 2>&1; then
  sudo install -m 644 -o "${REMOTE_USER}" -g "${REMOTE_GROUP}" "${REMOTE_TMP}/out.jar" "${REMOTE_INSTALL_DIR}/ics205.jar"
else
  sudo cp "${REMOTE_TMP}/out.jar" "${REMOTE_INSTALL_DIR}/ics205.jar"
  sudo chown "${REMOTE_USER}:${REMOTE_GROUP}" "${REMOTE_INSTALL_DIR}/ics205.jar"
  sudo chmod 644 "${REMOTE_INSTALL_DIR}/ics205.jar"
fi

# Update service unit if transferred
if [ "${HAS_SERVICE_UNIT}" = "true" ]; then
  echo "Updating /etc/systemd/system/${SERVICE_NAME}..."
  sudo cp "${REMOTE_TMP}/${SERVICE_NAME}" "/etc/systemd/system/${SERVICE_NAME}"
  sudo chmod 644 "/etc/systemd/system/${SERVICE_NAME}"
  echo "Reloading systemd daemon..."
  sudo systemctl daemon-reload
fi

# Restart systemd service
if [ "${RESTART_SERVICE}" = "true" ]; then
  echo "Restarting ${SERVICE_NAME}..."
  sudo systemctl restart "${SERVICE_NAME}"
  
  sleep 2
  if sudo systemctl is-active --quiet "${SERVICE_NAME}"; then
    echo "Service ${SERVICE_NAME} restarted and active!"
  else
    echo "WARNING: Service ${SERVICE_NAME} failed to restart or is not active. Check logs with 'journalctl -u ${SERVICE_NAME}'." >&2
  fi
  sudo systemctl status "${SERVICE_NAME}" --no-pager || true
else
  echo "JAR updated. Restart manually with: sudo systemctl restart ${SERVICE_NAME}"
fi
REMOTESCRIPT

cleanup() {
  rm -f "${LOCAL_REMOTE_SCRIPT:-}"
  if [[ -n "${REMOTE_TMP:-}" ]]; then
    ssh "${SSH_OPTS[@]}" "${TARGET_HOST}" "rm -rf '${REMOTE_TMP}'" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# 3. Transfer new JAR (and optional service unit)
echo "--> Uploading new JAR to ${TARGET_HOST}:${REMOTE_TMP}..."
scp "${SCP_OPTS[@]}" "${JAR_PATH}" "${TARGET_HOST}:${REMOTE_TMP}/out.jar"

HAS_SERVICE_UNIT=false
if [[ "${UPDATE_SERVICE}" == true ]] && [[ -f "${SERVICE_PATH}" ]]; then
  echo "--> Uploading service unit to ${TARGET_HOST}:${REMOTE_TMP}..."
  scp "${SCP_OPTS[@]}" "${SERVICE_PATH}" "${TARGET_HOST}:${REMOTE_TMP}/${SERVICE_NAME}"
  HAS_SERVICE_UNIT=true
fi
scp "${SCP_OPTS[@]}" "${LOCAL_REMOTE_SCRIPT}" "${TARGET_HOST}:${REMOTE_TMP}/update.sh"

# 4. Execute remote update steps
echo "--> Applying update on remote server..."
ssh -t "${SSH_OPTS[@]}" "${TARGET_HOST}" bash "${REMOTE_TMP}/update.sh" \
  "${REMOTE_TMP}" \
  "${REMOTE_INSTALL_DIR}" \
  "${SERVICE_NAME}" \
  "${REMOTE_USER}" \
  "${REMOTE_GROUP}" \
  "${HAS_SERVICE_UNIT}" \
  "${RESTART_SERVICE}"

echo "=================================================="
echo "Update complete on ${TARGET_HOST}!"
echo ""
echo "View live logs with:"
echo "   ssh ${TARGET_HOST} \"sudo journalctl -u ${SERVICE_NAME} -f\""
echo "=================================================="
