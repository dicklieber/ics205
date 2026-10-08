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
JAR_PATH="${DEFAULT_JAR_PATH}"
SERVICE_PATH="${DEFAULT_SERVICE_PATH}"
LOG4J_PATH="${DEFAULT_LOG4J_PATH}"
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
  -l, --log-config <path> Custom log4j2.yaml path (default: web/resources/log4j2.yaml)
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
echo "Local Log Config:  ${LOG4J_PATH}"
echo "Remote User:       ${REMOTE_USER}"
echo "Remote Layout:     ${REMOTE_BASE_DIR}"
echo "  JAR:             ${REMOTE_APP_DIR}/${REMOTE_USER}.jar"
echo "  Config:          ${REMOTE_CONFIG_DIR}"
echo "  Log Config:      ${REMOTE_CONFIG_DIR}/log4j2.yaml"
echo "  Data:            ${REMOTE_DATA_DIR}"
echo "  Install Unit:    ${REMOTE_INSTALL_DIR}/${SERVICE_NAME}"
echo "  Active Unit:     /etc/systemd/system/${SERVICE_NAME}"
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
REMOTE_BASE_DIR="$2"
REMOTE_APP_DIR="$3"
REMOTE_CONFIG_DIR="$4"
REMOTE_DATA_DIR="$5"
REMOTE_INSTALL_DIR="$6"
SERVICE_NAME="$7"
REMOTE_USER="$8"
REMOTE_GROUP="$9"
START_SERVICE="${10}"

# Verify JRE is installed
if ! command -v java >/dev/null 2>&1; then
  echo "WARNING: Java runtime ('java') was not found in PATH on the remote server." >&2
  echo "Please ensure OpenJDK 17 or 21+ JRE is installed (e.g., 'sudo apt install openjdk-21-jre-headless')." >&2
fi

# 1. Create dedicated system user/group if not present
if ! getent group "${REMOTE_GROUP}" >/dev/null 2>&1; then
  echo "Creating system group '${REMOTE_GROUP}'..."
  sudo groupadd --system "${REMOTE_GROUP}"
fi

NEW_USER=false
if ! id -u "${REMOTE_USER}" >/dev/null 2>&1; then
  echo "Creating system user '${REMOTE_USER}'..."
  sudo useradd --system --home-dir "${REMOTE_BASE_DIR}" --create-home --gid "${REMOTE_GROUP}" --shell /usr/sbin/nologin "${REMOTE_USER}"
  NEW_USER=true
else
  echo "System user '${REMOTE_USER}' already exists."
fi

# Prompt for password if new user was created
if [ "${NEW_USER}" = "true" ]; then
  echo "Please set a password for system user '${REMOTE_USER}':"
  while true; do
    if sudo passwd "${REMOTE_USER}"; then
      echo "Password for '${REMOTE_USER}' set successfully."
      break
    else
      echo "Password setting failed. Try again? (y/n) "
      read -r retry || retry="n"
      if [[ ! "$retry" =~ ^[Yy]$ ]]; then
        echo "Warning: No password set for '${REMOTE_USER}'." >&2
        break
      fi
    fi
  done
fi

# 2. Create destination layout directories
echo "Creating application directory layout..."
sudo mkdir -p "${REMOTE_BASE_DIR}" "${REMOTE_APP_DIR}" "${REMOTE_CONFIG_DIR}" "${REMOTE_DATA_DIR}" "${REMOTE_INSTALL_DIR}"
sudo chown "${REMOTE_USER}:${REMOTE_GROUP}" "${REMOTE_BASE_DIR}"
sudo chmod 750 "${REMOTE_BASE_DIR}"
sudo find "${REMOTE_BASE_DIR}" -maxdepth 1 -name ".*" -exec chown "${REMOTE_USER}:${REMOTE_GROUP}" {} + 2>/dev/null || true

sudo chown "root:${REMOTE_GROUP}" "${REMOTE_APP_DIR}" "${REMOTE_CONFIG_DIR}" "${REMOTE_INSTALL_DIR}"
sudo chmod 750 "${REMOTE_APP_DIR}" "${REMOTE_CONFIG_DIR}" "${REMOTE_INSTALL_DIR}"
sudo chown -R "${REMOTE_USER}:${REMOTE_GROUP}" "${REMOTE_DATA_DIR}"
sudo chmod 750 "${REMOTE_DATA_DIR}"

# 3. Install default log4j2.yaml if not present in config directory
if [ -f "${REMOTE_TMP}/log4j2.yaml" ] && [ ! -f "${REMOTE_CONFIG_DIR}/log4j2.yaml" ]; then
  echo "Installing default log4j2.yaml to ${REMOTE_CONFIG_DIR}/log4j2.yaml..."
  sudo cp "${REMOTE_TMP}/log4j2.yaml" "${REMOTE_CONFIG_DIR}/log4j2.yaml"
  sudo chown "root:${REMOTE_GROUP}" "${REMOTE_CONFIG_DIR}/log4j2.yaml"
  sudo chmod 640 "${REMOTE_CONFIG_DIR}/log4j2.yaml"
fi

# 4. Install JAR file (owned by root:group, mode 640 - readable by app, unmodifiable)
echo "Installing JAR to ${REMOTE_APP_DIR}/${REMOTE_USER}.jar..."
sudo cp "${REMOTE_TMP}/out.jar" "${REMOTE_APP_DIR}/${REMOTE_USER}.jar"
sudo chown "root:${REMOTE_GROUP}" "${REMOTE_APP_DIR}/${REMOTE_USER}.jar"
sudo chmod 640 "${REMOTE_APP_DIR}/${REMOTE_USER}.jar"

# 5. Install systemd service unit copies
echo "Installing distribution service unit to ${REMOTE_INSTALL_DIR}/${SERVICE_NAME}..."
sudo cp "${REMOTE_TMP}/${SERVICE_NAME}" "${REMOTE_INSTALL_DIR}/${SERVICE_NAME}"
sudo chown "root:${REMOTE_GROUP}" "${REMOTE_INSTALL_DIR}/${SERVICE_NAME}"
sudo chmod 640 "${REMOTE_INSTALL_DIR}/${SERVICE_NAME}"

echo "Installing active systemd unit to /etc/systemd/system/${SERVICE_NAME}..."
sudo cp "${REMOTE_TMP}/${SERVICE_NAME}" "/etc/systemd/system/${SERVICE_NAME}"
sudo chown root:root "/etc/systemd/system/${SERVICE_NAME}"
sudo chmod 644 "/etc/systemd/system/${SERVICE_NAME}"

# 6. Create default environment config if none exists
if [ ! -f "${REMOTE_CONFIG_DIR}/${REMOTE_USER}.env" ] && [ ! -f "${REMOTE_CONFIG_DIR}/${REMOTE_USER}.conf" ] && [ ! -f "${REMOTE_CONFIG_DIR}/application.conf" ]; then
  echo "Creating default environment file at ${REMOTE_CONFIG_DIR}/${REMOTE_USER}.env..."
  sudo tee "${REMOTE_CONFIG_DIR}/${REMOTE_USER}.env" > /dev/null << 'EOF'
JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC -Dauth.secureCookie=false
JAR_PATH=/home/ics205/app/ics205.jar
# Remote Java Debugger (listening on port 5005 across all network interfaces):
# JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -Xms256m -Xmx512m -XX:+UseG1GC -Dauth.secureCookie=false
EOF
  sudo chown "${REMOTE_USER}:${REMOTE_GROUP}" "${REMOTE_CONFIG_DIR}/${REMOTE_USER}.env"
  sudo chmod 640 "${REMOTE_CONFIG_DIR}/${REMOTE_USER}.env"
fi

# 7. Reload systemd daemon
echo "Reloading systemd daemon..."
sudo systemctl daemon-reload

# 8. Start and enable service if requested
if [ "${START_SERVICE}" = "true" ]; then
  echo "Enabling and restarting ${SERVICE_NAME}..."
  sudo systemctl enable "${SERVICE_NAME}"
  sudo systemctl restart "${SERVICE_NAME}"
  
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

# 3. Transfer JAR, service unit, and log configuration
echo "--> Uploading artifacts to ${TARGET_HOST}:${REMOTE_TMP}..."
scp "${SCP_OPTS[@]}" "${JAR_PATH}" "${TARGET_HOST}:${REMOTE_TMP}/out.jar"
scp "${SCP_OPTS[@]}" "${SERVICE_PATH}" "${TARGET_HOST}:${REMOTE_TMP}/${SERVICE_NAME}"
if [[ -f "${LOG4J_PATH}" ]]; then
  scp "${SCP_OPTS[@]}" "${LOG4J_PATH}" "${TARGET_HOST}:${REMOTE_TMP}/log4j2.yaml"
fi
scp "${SCP_OPTS[@]}" "${LOCAL_REMOTE_SCRIPT}" "${TARGET_HOST}:${REMOTE_TMP}/provision.sh"

# 4. Execute remote installation steps
echo "--> Provisioning remote server and configuring systemd..."
ssh -t "${SSH_OPTS[@]}" "${TARGET_HOST}" bash "${REMOTE_TMP}/provision.sh" \
  "${REMOTE_TMP}" \
  "${REMOTE_BASE_DIR}" \
  "${REMOTE_APP_DIR}" \
  "${REMOTE_CONFIG_DIR}" \
  "${REMOTE_DATA_DIR}" \
  "${REMOTE_INSTALL_DIR}" \
  "${SERVICE_NAME}" \
  "${REMOTE_USER}" \
  "${REMOTE_GROUP}" \
  "${START_SERVICE}"

SSH_EXTRA_FLAGS=""
if [[ -n "${SSH_PORT}" ]]; then
  SSH_EXTRA_FLAGS="${SSH_EXTRA_FLAGS}-p ${SSH_PORT} "
fi
if [[ -n "${SSH_KEY}" ]]; then
  SSH_EXTRA_FLAGS="${SSH_EXTRA_FLAGS}-i ${SSH_KEY} "
fi

echo "=================================================="
echo "Installation complete on ${TARGET_HOST}!"
echo ""
echo "Next steps:"
echo "1. Access the web interface at http://${TARGET_HOST}:8080/ to create the initial admin user."
echo ""
echo "2. Check service status and live logs:"
echo "   ssh -t ${SSH_EXTRA_FLAGS}${TARGET_HOST} \"sudo systemctl status ${SERVICE_NAME}\""
echo "   ssh -t ${SSH_EXTRA_FLAGS}${TARGET_HOST} \"sudo journalctl -u ${SERVICE_NAME} -f\""
echo "=================================================="
