# 100 consultas del saldo de Ana: promedio, mediana y p95 en ms, y cuantas leyeron Postgres
desde=$(date -u +%Y-%m-%dT%H:%M:%SZ)
for i in $(seq 1 100); do curl -s -o /dev/null -w '%{time_total}\n' localhost:8080/api/cuentas/002180000000000001; done | sort -n | awk '{t[NR]=$1*1000; s+=$1*1000} END {printf "promedio=%.2f ms mediana=%.2f ms p95=%.2f ms", s/NR, t[int(NR*0.5)], t[int(NR*0.95)]}'
sleep 1
echo "  | lecturas a Postgres: $(docker compose logs --since $desde cuenta | grep -c 'leida de Postgres')"
