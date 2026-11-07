# Escenario B: 100 consultas del saldo de Ana, con una transferencia de 1 peso de Ana a Beto cada diez
for i in $(seq 1 100); do
  curl -s -o /dev/null localhost:8080/api/cuentas/002180000000000001
  if [ $((i % 10)) -eq 0 ]; then curl -s -o /dev/null -X POST localhost:8080/api/transferencias -H 'Content-Type: application/json' -d '{"claveOrigen":"002180000000000001","claveDestino":"002180000000000002","monto":1}'; fi
done
