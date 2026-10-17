#!/usr/bin/env bash

set -euo pipefail

NODOS=("cassandra-1" "cassandra-2")
KEYSPACE="test_sharding"
TABLA="cuentas"

# cuenta_id|titular|saldo
CUENTAS=(
  "CTA-101|Ana López|15000.00"
  "CTA-102|Bruno Díaz|3200.50"
  "CTA-103|Carla Méndez|87000.00"
  "CTA-104|Diego Ruiz|450.75"
  "CTA-105|Elena Torres|12500.00"
  "CTA-106|Fernando Gómez|980.00"
  "CTA-107|Gabriela Ortiz|43000.25"
  "CTA-108|Héctor Salas|7600.00"
  "CTA-109|Irene Vargas|21000.00"
  "CTA-110|Jorge Medina|5300.80"
)

if [ -t 1 ]; then
  GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; RESET='\033[0m'
else
  GREEN=''; RED=''; CYAN=''; RESET=''
fi

# cqlsh con reintentos: justo después de un DDL el esquema puede tardar en propagarse al otro nodo
cql() {
  local intento salida
  for intento in 1 2 3 4 5; do
    if salida=$(docker exec cassandra-1 cqlsh -e "$1" 2>&1); then
      [ -n "$salida" ] && echo "$salida"
      return 0
    fi
    sleep 3
  done
  echo -e "${RED}ERROR en CQL: $1${RESET}" >&2
  echo "$salida" >&2
  exit 1
}

esperar_healthy() {
  local nodo="$1"
  local intentos=0
  local max=60
  echo -e "${CYAN}Esperando a que ${nodo} esté healthy...${RESET}"
  until [ "$(docker inspect -f '{{.State.Health.Status}}' "$nodo" 2>/dev/null)" = "healthy" ]; do
    intentos=$((intentos + 1))
    if [ $intentos -ge $max ]; then
      echo -e "${RED}ERROR: ${nodo} no llegó a healthy tras ${max} intentos.${RESET}"
      exit 1
    fi
    sleep 5
  done
  echo -e "${GREEN}✓ ${nodo} healthy${RESET}"
}

for nodo in "${NODOS[@]}"; do
  esperar_healthy "$nodo"
done

# Ambos nodos deben aparecer como UN (Up/Normal) en el anillo antes de crear el esquema
intentos=0
until [ "$(docker exec cassandra-1 nodetool status | grep -c '^UN')" -eq 2 ]; do
  intentos=$((intentos + 1))
  if [ $intentos -ge 30 ]; then
    echo -e "${RED}ERROR: el anillo no tiene 2 nodos UN.${RESET}"
    exit 1
  fi
  sleep 5
done

# Tabla IP -> nombre del nodo, leída de la red de Docker
declare -A NODO_POR_IP
for nodo in "${NODOS[@]}"; do
  ip=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$nodo")
  NODO_POR_IP["$ip"]="$nodo"
done

# --- Esquema: RF=1 para que cada fila viva en un solo nodo ---
echo -e "\n${CYAN}[1] Creación del keyspace y la tabla${RESET}"
cql "CREATE KEYSPACE IF NOT EXISTS ${KEYSPACE} WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"
cql "CREATE TABLE IF NOT EXISTS ${KEYSPACE}.${TABLA} (cuenta_id text PRIMARY KEY, titular text, saldo decimal);"
echo "Keyspace ${KEYSPACE} (RF=1) y tabla ${KEYSPACE}.${TABLA} listos"

echo -e "\n${CYAN}[2] Estado del anillo (nodetool status ${KEYSPACE})${RESET}"
docker exec cassandra-1 nodetool status "$KEYSPACE"

# --- Datos: todos los INSERT en una sola llamada a cqlsh ---
echo -e "${CYAN}[3] Inserción de registros${RESET}"
INSERTS=""
for cuenta in "${CUENTAS[@]}"; do
  IFS='|' read -r id titular saldo <<< "$cuenta"
  INSERTS+="INSERT INTO ${KEYSPACE}.${TABLA} (cuenta_id, titular, saldo) VALUES ('${id}', '${titular}', ${saldo}); "
done
cql "${INSERTS% }"
cql "SELECT cuenta_id, titular, saldo FROM ${KEYSPACE}.${TABLA};"

# --- getendpoints por clave ---
echo -e "\n${CYAN}[4] nodetool getendpoints ${KEYSPACE} ${TABLA} <cuenta_id>${RESET}"
declare -A IP_DE
for cuenta in "${CUENTAS[@]}"; do
  clave="${cuenta%%|*}"
  echo "\$ nodetool getendpoints ${KEYSPACE} ${TABLA} ${clave}"
  ip=$(docker exec cassandra-1 nodetool getendpoints "$KEYSPACE" "$TABLA" "$clave" | tr -d '\r')
  echo "$ip"
  IP_DE["$clave"]="$ip"
done

# --- Resumen ---
declare -A TOKEN_DE
while IFS='|' read -r clave token; do
  TOKEN_DE["$(echo "$clave" | tr -d ' ')"]="$(echo "$token" | tr -d ' \r')"
done < <(cql "SELECT cuenta_id, token(cuenta_id) FROM ${KEYSPACE}.${TABLA};" | grep -E '^ *CTA-')

echo -e "\n${CYAN}[5] Resumen de asignación (Murmur3Partitioner, RF=1)${RESET}"
echo "Clave de partición   |        Token calculado | Shard asignado (nodo)"
echo "---------------------+------------------------+---------------------------"
declare -A FILAS_POR_NODO
for cuenta in "${CUENTAS[@]}"; do
  clave="${cuenta%%|*}"
  ip="${IP_DE[$clave]}"
  nodo="${NODO_POR_IP[$ip]:-desconocido}"
  FILAS_POR_NODO["$nodo"]=$(( ${FILAS_POR_NODO[$nodo]:-0} + 1 ))
  printf "%-20s | %22s | %s (%s)\n" "$clave" "${TOKEN_DE[$clave]}" "$nodo" "$ip"
done

echo
for nodo in "${NODOS[@]}"; do
  echo "${nodo}: ${FILAS_POR_NODO[$nodo]:-0} fila(s)"
done

if [ "${FILAS_POR_NODO[cassandra-1]:-0}" -gt 0 ] && [ "${FILAS_POR_NODO[cassandra-2]:-0}" -gt 0 ]; then
  echo -e "Veredicto: ${GREEN}PASS (las filas quedaron repartidas entre los dos nodos)${RESET}"
else
  echo -e "Veredicto: ${RED}FAIL (todas las filas cayeron en un solo nodo)${RESET}"
fi
