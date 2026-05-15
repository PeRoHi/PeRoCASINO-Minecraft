#!/usr/bin/env bash
# Paper 開発サーバー（JDWP 5005）。プラグイン JAR を plugins/ に同期して起動する。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="${ROOT}/.dev-server"
PAPER_VERSION="1.21.4"
PAPER_BUILD="${PAPER_BUILD:-232}"
DEBUG_PORT="${DEBUG_PORT:-5005}"
JAVA_OPTS="${JAVA_OPTS:--Xms1G -Xmx2G}"

mkdir -p "${RUN_DIR}/plugins"

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

JAR="${ROOT}/target/PeRoCasino-1.0.0.jar"
if [[ ! -f "${JAR}" ]]; then
  echo "Plugin JAR not found: ${JAR}" >&2
  exit 1
fi
cp -f "${JAR}" "${RUN_DIR}/plugins/PeRoCasino.jar"

PROPS="${RUN_DIR}/server.properties"
if [[ ! -f "${PROPS}" ]]; then
  cat > "${PROPS}" <<'EOF'
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
