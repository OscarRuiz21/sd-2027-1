#!/usr/bin/env bash
# Mide con tcpdump los bytes de datos TCP de la llamada con ID 1. uso: bash medir_tcpdump.sh rest|grpc [n]
# Necesita el servidor arriba (docker compose up -d server) y la imagen nicolaka/netshoot.
p=$1; n=${2:-1}; port=5000; [ $p = grpc ] && port=50051
docker run -d --name t01-dump --net container:t01-server-1 nicolaka/netshoot tcpdump -i eth0 -nn -q "tcp port $port" >/dev/null; sleep 2
docker compose run --rm client python una_llamada.py $p $n 2>/dev/null
sleep 1; docker stop t01-dump >/dev/null; docker logs t01-dump 2>/dev/null | awk '{print} {b+=$NF} END {print "TOTAL bytes de datos TCP (sin encabezados IP/TCP): " b}'; docker rm t01-dump >/dev/null
