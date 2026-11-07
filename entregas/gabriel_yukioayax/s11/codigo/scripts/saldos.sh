for c in 002180000000000001 002180000000000002; do docker compose exec -T db psql -U mexibanco -d cuenta -tAc "select clabe, saldo from cuenta where clabe='$c'"; done
