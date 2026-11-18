#!/usr/bin/env bash
# Demo S08 de Mexi Banco: saga orquestada + circuit breaker. Genera la evidencia de la bitacora.
#
# Uso (con todo arriba y healthy: docker compose up --build -d ; docker compose ps):
#   ./demo-s08.sh parte0         cuenta se apaga entre el cargo y el abono (pausa de 20 s).
#                                Con el codigo de la S07: el dinero se pierde.
#                                Con la saga: COMPENSACION_PENDIENTE -> (cuenta vuelve) -> COMPENSADA.
#   ./demo-s08.sh compensacion   el abono falla a proposito (FALLA_ABONO_CLABE) con cuenta ARRIBA:
#                                la transferencia termina COMPENSADA y el dinero regresa a origen.
#   ./demo-s08.sh circuito       apaga cuenta, manda transferencias y muestra CERRADO -> ABIERTO ->
#                                SEMIABIERTO -> CERRADO.
#   ./demo-s08.sh normal         deja transferencia sin pausa ni falla simulada (estado normal).
#
# Las CLABEs cambian en cada corrida. Todo va por el gateway (http://localhost:8080).

set -u
cd "$(dirname "$0")"
MODO="${1:-}"

PUERTA=http://localhost:8080/api
SELLO=$(date +%s | cut -c3-10)
ANA="00218000${SELLO}01"
BETO="00218000${SELLO}02"

NEGRITA=$'\033[1m'; AZUL=$'\033[34m'; NORMAL=$'\033[0m'
paso() { printf '\n%s=== %s ===%s\n' "$NEGRITA" "$1" "$NORMAL"; }
nota() { printf '%s-> %s%s\n' "$AZUL" "$1" "$NORMAL"; }
hora() { date +%H:%M:%S; }

mostrar() {
	if command -v jq >/dev/null 2>&1 && [ -n "$1" ]; then printf '%s\n' "$1" | jq . 2>/dev/null || printf '%s\n' "$1"
	else printf '%s\n' "$1"; fi
}

# pedir METODO URL [CUERPO]: muestra la peticion, deja $CUERPO, $CODIGO y $SEGUNDOS
pedir() {
	local metodo=$1 url=$2 datos=${3:-} salida
	printf '$ curl -X %s %s %s\n' "$metodo" "$url" "${datos:+-d '$datos'}"
	if [ -n "$datos" ]; then
		salida=$(curl -s -w '\n%{http_code} %{time_total}' -X "$metodo" "$url" -H 'Content-Type: application/json' -d "$datos")
	else
		salida=$(curl -s -w '\n%{http_code} %{time_total}' -X "$metodo" "$url")
	fi
	local ultima=${salida##*$'\n'}
	CUERPO=${salida%$'\n'*}
	CODIGO=${ultima%% *}
	SEGUNDOS=${ultima##* }
	mostrar "$CUERPO"
	printf '[HTTP %s en %s s]\n' "$CODIGO" "$SEGUNDOS"
}

# Saca un campo de un JSON (plano o con sangrias de jq) sin depender de jq: campo saldo
campo() { printf '%s' "$CUERPO" | tr -d '\n' | sed -n "s/.*\"$1\": *\"\{0,1\}\([^\",}]*\).*/\1/p" | head -1; }

saldo_de() { CUERPO=$(curl -s "$PUERTA/cuentas/$1"); campo saldo; }

saldos() {
	nota "$(hora)  saldo Ana ($ANA) = $(saldo_de "$ANA")   saldo Beto ($BETO) = $(saldo_de "$BETO")"
}

estado_saga() { CUERPO=$(curl -s "$PUERTA/transferencias/$1"); campo estado; }

# Espera a que un servicio de compose este healthy en todas sus replicas.
esperar_sano() {
	local servicio=$1 limite=${2:-240} t=0
	printf 'Esperando a que %s este healthy' "$servicio"
	while [ $t -lt "$limite" ]; do
		local ids total sanos
		ids=$(docker compose ps -q "$servicio" 2>/dev/null)
		total=$(printf '%s\n' "$ids" | grep -c . || true)
		sanos=0
		for id in $ids; do
			[ "$(docker inspect -f '{{.State.Health.Status}}' "$id" 2>/dev/null)" = healthy ] && sanos=$((sanos + 1))
		done
		if [ "$total" -gt 0 ] && [ "$sanos" -eq "$total" ]; then printf ' listo (%s s)\n' "$t"; return 0; fi
		printf '.'; sleep 3; t=$((t + 3))
	done
	printf ' NO llego a healthy en %s s\n' "$limite"; return 1
}

# Espera a que el gateway ya enrute a una URL (Eureka + caches tardan unos segundos).
esperar_ruta() {
	local url=$1 limite=${2:-120} t=0
	printf 'Esperando a que el gateway llegue a %s' "$url"
	while [ $t -lt "$limite" ]; do
		case $(curl -s -o /dev/null -w '%{http_code}' "$url") in
			200|404) printf ' listo (%s s)\n' "$t"; return 0 ;;
		esac
		printf '.'; sleep 2; t=$((t + 2))
	done
	printf ' no respondio en %s s\n' "$limite"; return 1
}

# Recrea transferencia con las variables indicadas (el resto queda con su valor por omision).
reiniciar_transferencia() {
	paso "Reiniciando transferencia con: ${*:-(valores normales)}"
	env "$@" docker compose up -d transferencia
	esperar_sano transferencia
	esperar_ruta "$PUERTA/transferencias/0"
}

abrir_cuentas() {
	paso "Alta de dos cuentas: Ana con 1000 y Beto con 500"
	pedir POST "$PUERTA/cuentas" "{\"clabe\":\"$ANA\",\"titular\":\"Ana\",\"saldoInicial\":1000}"
	pedir POST "$PUERTA/cuentas" "{\"clabe\":\"$BETO\",\"titular\":\"Beto\",\"saldoInicial\":500}"
	saldos
}

transferir() { pedir POST "$PUERTA/transferencias" "{\"claveOrigen\":\"$ANA\",\"claveDestino\":\"$BETO\",\"monto\":${1:-200}}"; }

movimientos_de() {
	paso "Estado de cuenta de $1"
	pedir GET "$PUERTA/movimientos?clabe=$1"
}

case "$MODO" in
parte0)
	reiniciar_transferencia PAUSA_ENTRE_CARGO_Y_ABONO_MS=20000
	abrir_cuentas
	paso "Transferencia de 200 con pausa de 20 s entre cargo y abono; a los 5 s se apaga cuenta"
	transferir 200 > /tmp/demo-s08-transferencia.txt 2>&1 &
	PID=$!
	sleep 5
	nota "$(hora)  docker compose stop cuenta"
	docker compose stop cuenta
	wait $PID
	cat /tmp/demo-s08-transferencia.txt
	CUERPO=$(sed -n '2,$p' /tmp/demo-s08-transferencia.txt)
	ID=$(campo transferenciaId); [ -z "$ID" ] && ID=$(campo id)
	paso "Se vuelve a encender cuenta"
	nota "$(hora)  docker compose start cuenta"
	docker compose start cuenta
	esperar_sano cuenta
	esperar_ruta "$PUERTA/cuentas/$ANA"
	saldos
	if [ -n "$ID" ]; then
		paso "Que dice la base de transferencia (#$ID) mientras el recuperador reintenta"
		for _ in $(seq 1 60); do
			E=$(estado_saga "$ID")
			nota "$(hora)  transferencia #$ID: $E"
			case $E in COMPENSADA|COMPLETADA|RECHAZADA|COMPENSACION_FALLIDA) break ;; esac
			sleep 5
		done
		pedir GET "$PUERTA/transferencias/$ID"
		saldos
		movimientos_de "$ANA"
	else
		nota "Sin id de transferencia: es el codigo de la S07 (sin saga). El dinero salio de Ana y no llego a Beto."
	fi
	reiniciar_transferencia
	;;
compensacion)
	reiniciar_transferencia FALLA_ABONO_CLABE="$BETO"
	abrir_cuentas
	paso "Transferencia de 200 a Beto: el abono a su CLABE falla a proposito (FALLA_ABONO_CLABE=$BETO)"
	transferir 200
	ID=$(campo transferenciaId)
	saldos
	paso "La transferencia quedo escrita"
	pedir GET "$PUERTA/transferencias/$ID"
	movimientos_de "$ANA"
	paso "Una transferencia a otra CLABE sigue funcionando"
	pedir POST "$PUERTA/transferencias" "{\"claveOrigen\":\"$BETO\",\"claveDestino\":\"$ANA\",\"monto\":50}"
	saldos
	reiniciar_transferencia
	;;
circuito)
	reiniciar_transferencia
	abrir_cuentas
	paso "Circuito al inicio"
	pedir GET "$PUERTA/transferencias/circuito"
	paso "Se apaga cuenta y se mandan 6 transferencias seguidas (fijate en el tiempo de cada una)"
	nota "$(hora)  docker compose stop cuenta"
	docker compose stop cuenta
	for i in 1 2 3 4 5 6; do
		nota "$(hora)  transferencia $i"
		transferir 10
	done
	paso "Circuito despues de las fallas"
	pedir GET "$PUERTA/transferencias/circuito"
	paso "Se vuelve a encender cuenta"
	nota "$(hora)  docker compose start cuenta"
	docker compose start cuenta
	esperar_sano cuenta
	paso "Esperando a que el circuito pase a SEMIABIERTO"
	for _ in $(seq 1 30); do
		CUERPO=$(curl -s "$PUERTA/transferencias/circuito"); E=$(campo estado)
		nota "$(hora)  circuito: $E"
		[ "$E" = SEMIABIERTO ] && break
		sleep 2
	done
	esperar_ruta "$PUERTA/cuentas/$ANA"
	sleep 6   # que el balanceador de transferencia tambien vea otra vez las replicas de cuenta
	paso "Una transferencia en SEMIABIERTO: sus llamadas a cuenta son las de prueba"
	transferir 10
	paso "Circuito al final (con el historial de transiciones)"
	pedir GET "$PUERTA/transferencias/circuito"
	saldos
	;;
normal)
	reiniciar_transferencia
	;;
*)
	sed -n '2,15p' "$0"
	exit 1
	;;
esac
