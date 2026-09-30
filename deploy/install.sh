#!/usr/bin/env bash
#
# deploy/install.sh
# Install the ics205 web application and systemd service on a remote Linux server via SSH.
#
# Usage:
#   ./deploy/install.sh [options] [user@]hostname
#
# Examples:
#   ./deploy/install.sh ics-server.local
#   ./deploy/install.sh ubuntu@192.168.1.100 -p 2222
#   ./deploy/install.sh --build admin@production.server
#

set -euo pipefail

# Determine repository root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Default settings
DEFAULT_JAR_PATH="${REPO_ROOT}/out/web/assembly.dest/out.jar"
DEFAULT_SERVICE_PATH="${SCRIPT_DIR}/ics205.service"
REMOTE_INSTALL_DIR="/opt/ics205"
REMOTE_DATA_DIR="/var/lib/ics205"
REMOTE_CONFIG_DIR="/etc/ics205"
SERVICE_NAME="ics205.service"
REMOTE_USER="ics205"
REMOTE_GROUP="ics205"

# CLI Arguments
TARGET_HOST=""
SSH_PORT=""
SSH_KEY=""
JAR_PATH="${DEFAULT_JAR_PATH}"
SERVICE_PATH="${DEFAULT_SERVICE_PATH}"
DO_BUILD=false
START_SERVICE=true

print_usage() {
  cat << EOF
Usage: $(basename "$0") [options] [user@]hostname

Install the ics205 web application and systemd service on a remote Linux server.

Arguments:
  [user@]hostname         Remote target SSH host (e.g. ubuntu@192.168.1.50)

Options:
  -b, --build             Build the fat JAR (./mill web.assembly) before deploying
  -j, --jar <path>        Custom local JAR path (default: out/web/assembly.dest/out.jar)
  -s, --service <path>    Custom service unit path (default: deploy/ics205.service)
  -p, --port <port>       SSH port on the remote host (default: 22)
  -i, --identity <file>   SSH private key identity file
  --no-start              Do not start or enable the systemd service immediately
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
    -s|--service)
      SERVICE_PATH="$2"
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
    --no-start)
      START_SERVICE=false
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

if [[ ! -f "${SERVICE_PATH}" ]]; then
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
echo "ICS-205 Remote Installation"
echo "Target Host:       ${TARGET_HOST}"
echo "Local JAR:         ${JAR_PATH}"
echo "Local Service:     ${SERVICE_PATH}"
echo "Remote Binary:     ${REMOTE_INSTALL_DIR}/ics205.jar"
echo "Remote Data Dir:   ${REMOTE_DATA_DIR}"
echo "=================================================="

# 1. Test SSH connectivity and remote OS
echo "--> Testing SSH connection to ${TARGET_HOST}..."
ssh "${SSH_OPTS[@]}" "${TARGET_HOST}" "uname -s" >/dev/null

# 2. Create remote staging directory
echo "--> Creating remote temporary staging directory..."
REMOTE_TMP=$(ssh "${SSH_OPTS[@]}" "${TARGET_HOST}" "mktemp -d /tmp/ics205-install.XXXXXX")

LOCAL_REMOTE_SCRIPT=$(mktemp)
cat << 'REMOTESCRIPT' > "${LOCAL_REMOTE_SCRIPT}"
set -euo pipefail

REMOTE_TMP="$1"
REMOTE_INSTALL_DIR="$2"
REMOTE_DATA_DIR="$3"
REMOTE_CONFIG_DIR="$4"
SERVICE_NAME="$5"
REMOTE_USER="$6"
REMOTE_GROUP="$7"
START_SERVICE="$8"

# Verify JRE is installed
if ! command -v java >/dev/null 2>&1; then
  echo "WARNING: Java runtime ('java') was not found in PATH on the remote server." >&2
  echo "Please ensure OpenJDK 17 or 21+ JRE is installed (e.g., 'sudo apt install openjdk-21-jre-headless')." >&2
fi

# 1. Create dedicated system user/group if not present
if ! id -u "${REMOTE_USER}" >/dev/null 2>&1; then
  echo "Creating system user '${REMOTE_USER}'..."
  sudo useradd --system --no-create-home --user-group --shell /usr/sbin/nologin "${REMOTE_USER}"
else
  echo "System user '${REMOTE_USER}' already exists."
fi

# 2. Create destination directories
echo "Creating application directories..."
sudo mkdir -p "${REMOTE_INSTALL_DIR}" "${REMOTE_DATA_DIR}" "${REMOTE_CONFIG_DIR}"

# 3. Install JAR file
echo "Installing JAR to ${REMOTE_INSTALL_DIR}/ics205.jar..."
sudo cp "${REMOTE_TMP}/out.jar" "${REMOTE_INSTALL_DIR}/ics205.jar"
sudo chown "${REMOTE_USER}:${REMOTE_GROUP}" "${REMOTE_INSTALL_DIR}/ics205.jar"
sudo chmod 644 "${REMOTE_INSTALL_DIR}/ics205.jar"

# 4. Set permissions on working and state directories
sudo chown -R "${REMOTE_USER}:${REMOTE_GROUP}" "${REMOTE_INSTALL_DIR}" "${REMOTE_DATA_DIR}"
sudo chmod 750 "${REMOTE_DATA_DIR}"

# 5. Create default environment config if none exists
if [ ! -f "${REMOTE_CONFIG_DIR}/ics205.env" ] && [ ! -f /etc/default/ics205 ]; then
  echo "Creating default environment file at ${REMOTE_CONFIG_DIR}/ics205.env..."
  sudo tee "${REMOTE_CONFIG_DIR}/ics205.env" > /dev/null << 'EOF'
JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC -Dauth.secureCookie=true
JAR_PATH=/opt/ics205/ics205.jar
EOF
  sudo chown root:root "${REMOTE_CONFIG_DIR}/ics205.env"
  sudo chmod 600 "${REMOTE_CONFIG_DIR}/ics205.env"
fi

# 6. Install systemd service unit
echo "Installing systemd unit to /etc/systemd/system/${SERVICE_NAME}..."
sudo cp "${REMOTE_TMP}/${SERVICE_NAME}" "/etc/systemd/system/${SERVICE_NAME}"
sudo chmod 644 "/etc/systemd/system/${SERVICE_NAME}"

# 7. Reload systemd daemon
echo "Reloading systemd daemon..."
sudo systemctl daemon-reload

# 8. Start and enable service if requested
if [ "${START_SERVICE}" = "true" ]; then
  echo "Enabling and starting ${SERVICE_NAME}..."
  sudo systemctl enable --now "${SERVICE_NAME}"
  
  sleep 2
  if sudo systemctl is-active --quiet "${SERVICE_NAME}"; then
    echo "Service ${SERVICE_NAME} is active and running!"
  else
    echo "WARNING: Service ${SERVICE_NAME} is not active. Check logs with 'journalctl -u ${SERVICE_NAME}'." >&2
  fi
  sudo systemctl status "${SERVICE_NAME}" --no-pager || true
else
  echo "Service installed. Start manually with: sudo systemctl enable --now ${SERVICE_NAME}"
fi
REMOTESCRIPT

cleanup() {
  rm -f "${LOCAL_REMOTE_SCRIPT:-}"
  if [[ -n "${REMOTE_TMP:-}" ]]; then
    ssh "${SSH_OPTS[@]}" "${TARGET_HOST}" "rm -rf '${REMOTE_TMP}'" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# 3. Transfer JAR and service unit
echo "--> Uploading artifacts to ${TARGET_HOST}:${REMOTE_TMP}..."
scp "${SCP_OPTS[@]}" "${JAR_PATH}" "${TARGET_HOST}:${REMOTE_TMP}/out.jar"
scp "${SCP_OPTS[@]}" "${SERVICE_PATH}" "${TARGET_HOST}:${REMOTE_TMP}/${SERVICE_NAME}"
scp "${SCP_OPTS[@]}" "${LOCAL_REMOTE_SCRIPT}" "${TARGET_HOST}:${REMOTE_TMP}/provision.sh"

# 4. Execute remote installation steps
echo "--> Provisioning remote server and configuring systemd..."
ssh -t "${SSH_OPTS[@]}" "${TARGET_HOST}" bash "${REMOTE_TMP}/provision.sh" \
  "${REMOTE_TMP}" \
  "${REMOTE_INSTALL_DIR}" \
  "${REMOTE_DATA_DIR}" \
  "${REMOTE_CONFIG_DIR}" \
  "${SERVICE_NAME}" \
  "${REMOTE_USER}" \
  "${REMOTE_GROUP}" \
  "${START_SERVICE}"

echo "=================================================="
echo "Installation complete on ${TARGET_HOST}!"
echo ""
echo "Next steps:"
echo "1. Access the web interface at http://${TARGET_HOST}:8080/ to create the initial admin user."
echo ""
echo "2. Check service status and live logs:"
echo "   ssh ${TARGET_HOST} \"sudo systemctl status ${SERVICE_NAME}\""
echo "   ssh ${TARGET_HOST} \"sudo journalctl -u ${SERVICE_NAME} -f\""
echo "=================================================="
