#!/usr/bin/env bash
# Punto extra: bytes de TCP-payload de UNA llamada por REST y por gRPC.
# Uso: ./medir_bytes.sh [id]     (SIN PROBAR en el entorno donde se generó; ajusta si hace falta)
set -euo pipefail
ID="${1:-1}"
docker compose --profile medicion up -d --build servidor sniffer

medir () {  # $1 = protocolo, $2 = puerto
  docker compose exec -T sniffer sh -c "rm -f /tmp/$1.pcap; tcpdump -i eth0 -nn -U -w /tmp/$1.pcap 'tcp port $2' >/dev/null 2>&1 &"
  sleep 1
  docker compose --profile cliente run --rm cliente --id "$ID" --protocolo "$1" >/dev/null
  sleep 1
  docker compose exec -T sniffer pkill tcpdump || true
  sleep 1
  docker compose exec -T sniffer sh -c \
    "tcpdump -nn -r /tmp/$1.pcap 2>/dev/null | grep -o 'length [0-9]*' | awk '{s+=\$2} END {print \"$1: \" s \" bytes de payload\"}'"
}
medir rest 8080
medir grpc 50051
docker compose --profile medicion down
