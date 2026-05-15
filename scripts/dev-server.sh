#!/usr/bin/env bash
# Paper 開発サーバー（JDWP 5005）。プラグイン JAR を plugins/ に同期して起動する。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="${ROOT}/.dev-server"
PAPER_VERSION="1.21.4"
PAPER_BUILD="${PAPER_BUILD:-232}"
DEBUG_PORT="${DEBUG_PORT:-5005}"
JAVA_OPTS="${JAVA_OPTS:--Xms1G -Xmx2G}"

paper_pids() {
  local pid cwd
  for pid in $(pgrep -f 'paper\.jar --nogui' 2>/dev/null || true); do
    cwd="$(readlink -f "/proc/${pid}/cwd" 2>/dev/null || true)"
    if [[ "${cwd}" == "${RUN_DIR}" ]]; then
      echo "${pid}"
    fi
  done
}

port_in_use() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -tlnH 2>/dev/null | grep -qE ":${port}\b"
  else
    netstat -tln 2>/dev/null | grep -qE ":${port}\b"
  fi
}

cmd_stop() {
  local pids
  pids="$(paper_pids | tr '\n' ' ' | xargs)"
  if [[ -z "${pids// }" ]]; then
    echo "No dev Paper server is running."
    return 0
  fi
  echo "Stopping Paper server (PID: ${pids})..."
  kill ${pids} 2>/dev/null || true
  for _ in $(seq 1 30); do
    [[ -z "$(paper_pids | tr '\n' ' ' | xargs)" ]] && break
    sleep 0.5
  done
  pids="$(paper_pids | tr '\n' ' ' | xargs)"
  if [[ -n "${pids// }" ]]; then
    echo "Force killing: ${pids}"
    kill -9 ${pids} 2>/dev/null || true
  fi
  echo "Stopped."
}

cmd_status() {
  local pids
  pids="$(paper_pids | tr '\n' ' ' | xargs)"
  if [[ -n "${pids// }" ]]; then
    echo "Dev Paper server is running (PID: ${pids})"
    echo "  Game port: 25565"
    echo "  Debug port: ${DEBUG_PORT}"
    echo "  Attach: Run and Debug -> Attach to Paper Server"
  else
    echo "Dev Paper server is not running."
  fi
}

ensure_ports_free() {
  local pids
  pids="$(paper_pids | tr '\n' ' ' | xargs)"
  if [[ -n "${pids// }" ]]; then
    echo "ERROR: Dev Paper server is already running (PID: ${pids})." >&2
    echo "  Attach debugger instead of starting again." >&2
    echo "  Or stop it: bash scripts/dev-server.sh stop" >&2
    exit 1
  fi
  if port_in_use "${DEBUG_PORT}"; then
    echo "ERROR: Debug port ${DEBUG_PORT} is already in use (not this dev server?)." >&2
    echo "  Free the port or set DEBUG_PORT=5006 (and match launch.json)." >&2
    exit 1
  fi
}

cmd_start() {
  mkdir -p "${RUN_DIR}/plugins"
  ensure_ports_free

  if [[ ! -f "${RUN_DIR}/paper.jar" ]]; then
    echo "Downloading Paper ${PAPER_VERSION}-${PAPER_BUILD}..."
    curl -fsSL \
      "https://api.papermc.io/v2/projects/paper/versions/${PAPER_VERSION}/builds/${PAPER_BUILD}/downloads/paper-${PAPER_VERSION}-${PAPER_BUILD}.jar" \
      -o "${RUN_DIR}/paper.jar"
  fi

  if [[ ! -f "${RUN_DIR}/eula.txt" ]]; then
    echo "eula=true" > "${RUN_DIR}/eula.txt"
  fi

  echo "Building plugin..."
  (cd "${ROOT}" && mvn -q package -DskipTests)

  local jar="${ROOT}/target/PeRoCasino-1.0.0.jar"
  if [[ ! -f "${jar}" ]]; then
    echo "Plugin JAR not found: ${jar}" >&2
    exit 1
  fi
  cp -f "${jar}" "${RUN_DIR}/plugins/PeRoCasino.jar"

  local props="${RUN_DIR}/server.properties"
  if [[ ! -f "${props}" ]]; then
    cat > "${props}" <<'EOF'
motd=PeRoCasino Dev Server
gamemode=creative
difficulty=peaceful
pvp=false
spawn-protection=0
online-mode=false
max-players=20
view-distance=10
simulation-distance=8
generate-structures=true
EOF
  fi

  echo "Starting Paper (debug port ${DEBUG_PORT})..."
  echo "  Attach debugger: Run and Debug -> Attach to Paper Server"
  cd "${RUN_DIR}"
  exec java \
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT} \
    ${JAVA_OPTS} \
    -jar paper.jar \
    --nogui
}

case "${1:-start}" in
  start) cmd_start ;;
  stop) cmd_stop ;;
  restart) cmd_stop; cmd_start ;;
  status) cmd_status ;;
  *)
    echo "Usage: $0 {start|stop|restart|status}" >&2
    exit 1
    ;;
esac
