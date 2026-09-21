#!/usr/bin/env bash

set -euo pipefail

BASE_URL="http://localhost:8080"

if [ -t 1 ]; then
  GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; RESET='\033[0m'
else
  GREEN=''; RED=''; CYAN=''; RESET=''
fi

TMPDIR_SELF="$(mktemp -d)"
trap 'rm -rf "$TMPDIR_SELF"' EXIT

wait_for_server() {
  echo -e "${CYAN}Verificando conexión con ${BASE_URL}...${RESET}"
  local attempts=0
  local max=20
  until curl -s --connect-timeout 2 --max-time 3 -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/api/cobro" \
        -H "Content-Type: application/json" -d '{}' 2>/dev/null | grep -qE "^(200|400|409)$"; do
    attempts=$((attempts + 1))
    if [ $attempts -ge $max ]; then
      echo -e "${RED}ERROR: El servidor no responde en ${BASE_URL} tras ${max} intentos.${RESET}"
      exit 1
    fi
    sleep 2
  done
  echo -e "${GREEN}✓ Servidor listo${RESET}\n"
}

curl_bg() {
  local url="$1"
  local key="$2"
  local outfile="$3"
  curl -s --connect-timeout 5 -w "\n%{http_code}" -X POST "$url" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $key" \
    -d '{"monto": 500}' > "$outfile"
}

wait_for_server

# --- Prueba 1: Endpoint ingenuo (Map en memoria) ---
KEY1="key-ingenua-$$-$(date +%s%N)"
OUT1_A="${TMPDIR_SELF}/p1_a.txt"
OUT1_B="${TMPDIR_SELF}/p1_b.txt"

curl_bg "${BASE_URL}/api/cobro-ingenuo" "${KEY1}" "${OUT1_A}" &
PID1_A=$!
curl_bg "${BASE_URL}/api/cobro-ingenuo" "${KEY1}" "${OUT1_B}" &
PID1_B=$!

wait $PID1_A $PID1_B

CODE1_A=$(tail -n 1 "${OUT1_A}")
CODE1_B=$(tail -n 1 "${OUT1_B}")

echo "[Prueba 1] POST /api/cobro-ingenuo (concurrente)"
echo "  Respuestas: HTTP ${CODE1_A} / HTTP ${CODE1_B}"
if [ "$CODE1_A" -eq 200 ] && [ "$CODE1_B" -eq 200 ]; then
  echo -e "  Veredicto:  ${GREEN}PASS (TOCTOU detectado: doble cobro ejecutado)${RESET}\n"
else
  echo -e "  Veredicto:  ${RED}FAIL (No se reprodujo la condición de carrera)${RESET}\n"
fi

# --- Prueba 2: Endpoint robusto (UNIQUE en BD) ---
KEY2="key-robusta-$$-$(date +%s%N)"
OUT2_A="${TMPDIR_SELF}/p2_a.txt"
OUT2_B="${TMPDIR_SELF}/p2_b.txt"

curl_bg "${BASE_URL}/api/cobro" "${KEY2}" "${OUT2_A}" &
PID2_A=$!
curl_bg "${BASE_URL}/api/cobro" "${KEY2}" "${OUT2_B}" &
PID2_B=$!

wait $PID2_A $PID2_B

CODE2_A=$(tail -n 1 "${OUT2_A}")
CODE2_B=$(tail -n 1 "${OUT2_B}")

echo "[Prueba 2] POST /api/cobro (concurrente)"
echo "  Respuestas: HTTP ${CODE2_A} / HTTP ${CODE2_B}"
if { [ "$CODE2_A" -eq 200 ] && [ "$CODE2_B" -eq 409 ]; } || { [ "$CODE2_A" -eq 409 ] && [ "$CODE2_B" -eq 200 ]; }; then
  echo -e "  Veredicto:  ${GREEN}PASS (Atomicidad en BD: 1 procesado 200 OK, 1 conflicto 409)${RESET}\n"
else
  echo -e "  Veredicto:  ${RED}FAIL (No se obtuvo combinación esperada 200/409)${RESET}\n"
fi

# --- Prueba 3: Reintento sobre la misma llave de Prueba 2 ---
OUT3="${TMPDIR_SELF}/p3.txt"
curl_bg "${BASE_URL}/api/cobro" "${KEY2}" "${OUT3}"
CODE3=$(tail -n 1 "${OUT3}")
BODY3=$(head -n -1 "${OUT3}")

echo "[Prueba 3] POST /api/cobro (reintento)"
echo "  Respuesta:  HTTP ${CODE3}"
if [ "$CODE3" -eq 200 ] && echo "$BODY3" | grep -q "ya_procesado"; then
  echo -e "  Veredicto:  ${GREEN}PASS (Reintento seguro: respuesta previa retornada sin reejecutar)${RESET}"
else
  echo -e "  Veredicto:  ${RED}FAIL (No se retornó el resultado previo)${RESET}"
fi
