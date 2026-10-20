#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/codigo"
for servicio in cuenta movimiento notificacion spei transferencia; do
  docker build -t "derek-s07-$servicio" "./$servicio" > "../evidencias/build-final-$servicio.txt" 2>&1
  echo "Construido: $servicio"
done
