#!/bin/bash
set -euo pipefail
S05_DIR="$(cd "$(dirname "$0")" && pwd)"
S05_SOURCE="/tmp/mexi-banco-s05-derek"
S05_COMMIT="88203161aa31a371823715684a548d154e1516f8"
# Caché aislada: evita modificar la configuración de Docker del alumno.
export DOCKER_CONFIG="/tmp/s05-docker-config"
export DOCKER_HOST="unix:///Users/derek/.docker/run/docker.sock"
mkdir -p "$DOCKER_CONFIG"
if [ ! -f "$DOCKER_CONFIG/config.json" ]; then
  printf '%s\n' '{"auths":{"https://index.docker.io/v1/":{}}}' > "$DOCKER_CONFIG/config.json"
fi
if [ ! -e "$DOCKER_CONFIG/cli-plugins" ]; then
  ln -s /Applications/Docker.app/Contents/Resources/cli-plugins "$DOCKER_CONFIG/cli-plugins"
fi
if [ ! -d "$S05_SOURCE/.git" ]; then
  git clone --branch v05.1 --depth 1 https://github.com/OscarRuiz21/mexi-banco.git "$S05_SOURCE"
fi
if [ "$(git -C "$S05_SOURCE" rev-parse HEAD)" != "$S05_COMMIT" ]; then
  echo "El código no corresponde a v05.1. No se ejecutó Compose." >&2
  exit 1
fi
if [ -n "$(git -C "$S05_SOURCE" status --porcelain)" ]; then
  echo "El código del profesor tiene cambios locales. Revísalos antes de continuar." >&2
  exit 1
fi
case "${1:-estado}" in
  iniciar) set -- up --build -d --wait --wait-timeout 240 ;;
  estado) set -- ps ;;
  conservar) set -- down ;;
  volver) set -- up -d --wait --wait-timeout 240 ;;
  borrar-solo-s05) set -- down -v ;;
  logs) set -- logs --no-color ;;
  *) echo "Opciones: iniciar | estado | conservar | volver | borrar-solo-s05 | logs"; exit 2 ;;
esac
docker compose -p s05_santana_derek -f "$S05_SOURCE/docker-compose.yml" -f "$S05_DIR/compose.s05.yaml" "$@"
