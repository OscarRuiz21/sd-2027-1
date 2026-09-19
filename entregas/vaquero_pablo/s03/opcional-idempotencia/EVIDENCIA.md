# Evidencia de ejecución · Opcional S03

Verificación local del 12 de septiembre de 2026, en macOS, con Python 3.12.7 y
SQLite 3.45.3. Los cobros son registros de prueba en bases temporales.

## Pruebas automatizadas

Comando ejecutado desde esta carpeta:

```bash
python3 -m unittest -v
```

Resultado:

```text
test_changed_payload_conflicts ... ok
test_concurrent_changed_payload_conflicts ... ok
test_concurrent_http_requests_in_one_server ... ok
test_concurrent_http_requests_in_two_processes ... ok
test_crash_after_commit_replays_saved_response ... ok
test_crash_before_commit_rolls_back_key_and_charge ... ok
test_demo_cli ... ok
test_different_keys_are_different_operations ... ok
test_invalid_input_does_not_reserve_key ... ok
test_json_spacing_and_field_order_do_not_change_identity ... ok
test_lock_timeout_returns_retryable_503 ... ok
test_restart_keeps_original_response ... ok

Ran 12 tests in 2.089s

OK
```

## Salida real de la demostración HTTP

La prueba `test_demo_cli` inició el servidor, ejecutó `demo_race.py` y comprobó
directamente en SQLite que había una sola llave y un solo cobro. Esta fue su salida:

```text
Llave compartida: demo-33da1602-49c7-45a7-9ae8-323e90764a3a
Petición 1: HTTP 201, Idempotency-Replayed: false
{"amount":10000,"currency":"MXN","id":"536dedd6-5e4b-4ac1-88cd-9414ec5462d8","status":"succeeded"}
Petición 2: HTTP 201, Idempotency-Replayed: true
{"amount":10000,"currency":"MXN","id":"536dedd6-5e4b-4ac1-88cd-9414ec5462d8","status":"succeeded"}
OK: una creación y una repetición con el mismo ID y cuerpo.
```

El ID y la llave se generan en cada ejecución. También puede cambiar cuál petición
gana la carrera; lo que debe mantenerse es una creación, una repetición y un único
cobro guardado.
