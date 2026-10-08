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
#   ./deploy/update.sh --version 0.0.1 admin@production.server
#

set -euo pipefail

# Determine script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Release settings
GITHUB_REPO="dlieber/ics205"
DEFAULT_SERVICE_PATH="${SCRIPT_DIR}/ics205.service"
DEFAULT_LOG4J_PATH=""
REMOTE_USER="ics205"
REMOTE_GROUP="ics205"
REMOTE_BASE_DIR="/home/${REMOTE_USER}"
REMOTE_APP_DIR="${REMOTE_BASE_DIR}/app"
REMOTE_CONFIG_DIR="${REMOTE_BASE_DIR}/config"
REMOTE_DATA_DIR="${REMOTE_BASE_DIR}/data"
REMOTE_INSTALL_DIR="${REMOTE_BASE_DIR}/install"
SERVICE_NAME="ics205.service"

# CLI Arguments
TARGET_HOST=""
SSH_PORT=""
SSH_KEY=""
VERSION=""
JAR_PATH=""
SERVICE_PATH="${DEFAULT_SERVICE_PATH}"
LOG4J_PATH="${DEFAULT_LOG4J_PATH}"
UPDATE_SERVICE=false
RESTART_SERVICE=true

print_usage() {
  cat << EOF
Usage: $(basename "$0") [options] [user@]hostname

Update the ics205.jar on a remote Linux server and restart the systemd service.

Arguments:
  [user@]hostname         Remote target SSH host (e.g. ubuntu@192.168.1.50)

Options:
  -v, --version <ver>     Install a specific release (e.g. 0.0.1); default: latest
  -s, --sync-service      Also update /etc/systemd/system/ics205.service and daemon-reload
  -l, --log-config <path> Custom log4j2.yaml path (default: web/resources/log4j2.yaml)
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
    -v|--version)
      VERSION="$2"
      shift 2
      ;;
    -s|--sync-service)
      UPDATE_SERVICE=true
      shift
      ;;
    -l|--log-config)
      LOG4J_PATH="$2"
      shift 2
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

# Download the published release JAR and verify its SHA-256 checksum.
command -v curl >/dev/null 2>&1 || {
  echo "Error: curl is required." >&2
  exit 1
}

DOWNLOAD_DIR="$(mktemp -d)"
cleanup_download() {
  rm -rf "${DOWNLOAD_DIR:-}"
}
trap cleanup_download EXIT

if [[ -n "${VERSION}" ]]; then
  VERSION="${VERSION#v}"
  if [[ ! "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: --version must be X.Y.Z (for example 0.0.1)." >&2
    exit 1
  fi
  RELEASE_TAG="v${VERSION}"
else
  echo "--> Resolving latest GitHub release..."
  LATEST_URL="$(curl -fsSL -o /dev/null -w '%{url_effective}' "https://github.com/${GITHUB_REPO}/releases/latest")"
  RELEASE_TAG="${LATEST_URL##*/}"
  if [[ ! "${RELEASE_TAG}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Error: Could not determine latest release tag from ${LATEST_URL}" >&2
    exit 1
  fi
  VERSION="${RELEASE_TAG#v}"
fi

ASSET_NAME="ics205-${VERSION}.jar"
CHECKSUM_NAME="${ASSET_NAME}.sha256"
RELEASE_BASE="https://github.com/${GITHUB_REPO}/releases/download/${RELEASE_TAG}"
JAR_PATH="${DOWNLOAD_DIR}/${ASSET_NAME}"

echo "--> Downloading ${ASSET_NAME} from GitHub Release ${RELEASE_TAG}..."
curl -fL --retry 3 -o "${JAR_PATH}" "${RELEASE_BASE}/${ASSET_NAME}"
curl -fL --retry 3 -o "${DOWNLOAD_DIR}/${CHECKSUM_NAME}" "${RELEASE_BASE}/${CHECKSUM_NAME}"

echo "--> Verifying SHA-256 checksum..."
(
  cd "${DOWNLOAD_DIR}"
  shasum -a 256 -c "${CHECKSUM_NAME}"
)

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
echo "Release:           ${RELEASE_TAG}\necho "Release JAR:       ${ASSET_NAME}"
echo "Release:           ${RELEASE_TAG}"\necho "Release JAR:       ${ASSET_NAME}"\necho "Remote Target:     ${REMOTE_APP_DIR}/${REMOTE_USER}.jar"
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
REMOTE_BASE_DIR="$2"
REMOTE_APP_DIR="$3"
REMOTE_CONFIG_DIR="$4"
REMOTE_DATA_DIR="$5"
REMOTE_INSTALL_DIR="$6"
SERVICE_NAME="$7"
REMOTE_USER="$8"
REMOTE_GROUP="$9"
HAS_SERVICE_UNIT="${10}"
RESTART_SERVICE="${11}"

# Ensure layout directories exist
sudo mkdir -p "${REMOTE_BASE_DIR}" "${REMOTE_APP_DIR}" "${REMOTE_CONFIG_DIR}" "${REMOTE_DATA_DIR}" "${REMOTE_INSTALL_DIR}"
sudo chown "${REMOTE_USER}:${REMOTE_GROUP}" "${REMOTE_BASE_DIR}"
sudo chmod 750 "${REMOTE_BASE_DIR}"
sudo find "${REMOTE_BASE_DIR}" -maxdepth 1 -name ".*" -exec chown "${REMOTE_USER}:${REMOTE_GROUP}" {} + 2>/dev/null || true

sudo chown "root:${REMOTE_GROUP}" "${REMOTE_APP_DIR}" "${REMOTE_CONFIG_DIR}" "${REMOTE_INSTALL_DIR}"
sudo chmod 750 "${REMOTE_APP_DIR}" "${REMOTE_CONFIG_DIR}" "${REMOTE_INSTALL_DIR}"
sudo chown -R "${REMOTE_USER}:${REMOTE_GROUP}" "${REMOTE_DATA_DIR}"
sudo chmod 750 "${REMOTE_DATA_DIR}"

# Ensure .env file is owned by application user if present
if [ -f "${REMOTE_CONFIG_DIR}/${REMOTE_USER}.env" ]; then
  sudo chown "${REMOTE_USER}:${REMOTE_GROUP}" "${REMOTE_CONFIG_DIR}/${REMOTE_USER}.env"
  sudo chmod 640 "${REMOTE_CONFIG_DIR}/${REMOTE_USER}.env"
fi

# Ensure default log4j2.yaml exists in config directory
if [ -f "${REMOTE_TMP}/log4j2.yaml" ] && [ ! -f "${REMOTE_CONFIG_DIR}/log4j2.yaml" ]; then
  echo "Installing default log4j2.yaml to ${REMOTE_CONFIG_DIR}/log4j2.yaml..."
  sudo cp "${REMOTE_TMP}/log4j2.yaml" "${REMOTE_CONFIG_DIR}/log4j2.yaml"
  sudo chown "root:${REMOTE_GROUP}" "${REMOTE_CONFIG_DIR}/log4j2.yaml"
  sudo chmod 640 "${REMOTE_CONFIG_DIR}/log4j2.yaml"
elif [ -f "${REMOTE_CONFIG_DIR}/log4j2.yaml" ]; then
  sudo chown "root:${REMOTE_GROUP}" "${REMOTE_CONFIG_DIR}/log4j2.yaml"
  sudo chmod 640 "${REMOTE_CONFIG_DIR}/log4j2.yaml"
fi

# Replace JAR atomically (mode 640, root:group)
echo "Updating ${REMOTE_APP_DIR}/${REMOTE_USER}.jar..."
if command -v install >/dev/null 2>&1; then
  sudo install -m 640 -o root -g "${REMOTE_GROUP}" "${REMOTE_TMP}/out.jar" "${REMOTE_APP_DIR}/${REMOTE_USER}.jar"
else
  sudo cp "${REMOTE_TMP}/out.jar" "${REMOTE_APP_DIR}/${REMOTE_USER}.jar"
  sudo chown "root:${REMOTE_GROUP}" "${REMOTE_APP_DIR}/${REMOTE_USER}.jar"
  sudo chmod 640 "${REMOTE_APP_DIR}/${REMOTE_USER}.jar"
fi

# Update service units if transferred
if [ "${HAS_SERVICE_UNIT}" = "true" ]; then
  echo "Updating ${REMOTE_INSTALL_DIR}/${SERVICE_NAME} and /etc/systemd/system/${SERVICE_NAME}..."
  sudo cp "${REMOTE_TMP}/${SERVICE_NAME}" "${REMOTE_INSTALL_DIR}/${SERVICE_NAME}"
  sudo chown "root:${REMOTE_GROUP}" "${REMOTE_INSTALL_DIR}/${SERVICE_NAME}"
  sudo chmod 640 "${REMOTE_INSTALL_DIR}/${SERVICE_NAME}"

  sudo cp "${REMOTE_TMP}/${SERVICE_NAME}" "/etc/systemd/system/${SERVICE_NAME}"
  sudo chown root:root "/etc/systemd/system/${SERVICE_NAME}"
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
  cleanup_download
  rm -f "${LOCAL_REMOTE_SCRIPT:-}"
  if [[ -n "${REMOTE_TMP:-}" ]]; then
    ssh "${SSH_OPTS[@]}" "${TARGET_HOST}" "rm -rf '${REMOTE_TMP}'" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# 3. Transfer new JAR (and optional service unit, log config)
echo "--> Uploading new JAR to ${TARGET_HOST}:${REMOTE_TMP}..."
scp "${SCP_OPTS[@]}" "${JAR_PATH}" "${TARGET_HOST}:${REMOTE_TMP}/out.jar"

if [[ -f "${LOG4J_PATH}" ]]; then
  echo "--> Uploading logging configuration template..."
  scp "${SCP_OPTS[@]}" "${LOG4J_PATH}" "${TARGET_HOST}:${REMOTE_TMP}/log4j2.yaml"
fi

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
  "${REMOTE_BASE_DIR}" \
  "${REMOTE_APP_DIR}" \
  "${REMOTE_CONFIG_DIR}" \
  "${REMOTE_DATA_DIR}" \
  "${REMOTE_INSTALL_DIR}" \
  "${SERVICE_NAME}" \
  "${REMOTE_USER}" \
  "${REMOTE_GROUP}" \
  "${HAS_SERVICE_UNIT}" \
  "${RESTART_SERVICE}"

SSH_EXTRA_FLAGS=""
if [[ -n "${SSH_PORT}" ]]; then
  SSH_EXTRA_FLAGS="${SSH_EXTRA_FLAGS}-p ${SSH_PORT} "
fi
if [[ -n "${SSH_KEY}" ]]; then
  SSH_EXTRA_FLAGS="${SSH_EXTRA_FLAGS}-i ${SSH_KEY} "
fi

echo "=================================================="
echo "Update complete on ${TARGET_HOST}!"
echo ""
echo "View live logs with:"
echo "   ssh -t ${SSH_EXTRA_FLAGS}${TARGET_HOST} \"sudo journalctl -u ${SERVICE_NAME} -f\""
echo "=================================================="
