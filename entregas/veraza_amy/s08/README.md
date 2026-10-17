# Lab S08 - Amy Valentina Veraza Garcia

Entrega: `bitacora-s08.pdf`, servicio completo en `codigo/transferencia/` (sin `target/`) y evidencia real en `evidencias/`.

## Resultados comprobados en Docker

- Version original: origen 1000 -> 800; destino 500 -> 500 tras apagar cuenta durante la pausa.
- Saga con rechazo confirmado: origen vuelve a 1000, destino conserva 500; estado COMPENSADA y asientos -200/+200 registrados.
- Cuenta apagada al devolver: COMPENSACION_PENDIENTE persistida. Durante la pausa se observa saldo 800.
- Circuito manual: CERRADO -> ABIERTO -> SEMIABIERTO -> CERRADO. Rechazos en abierto con HTTP 503, aproximadamente 0.03 s.
- Limitacion observada: las primeras dos consultas sin instancias disponibles devuelven 500 antes de abrir el circuito. Se conserva la evidencia y se explica en el PDF. No se reintentan mutaciones monetarias sin idempotencia.

El log de 11 pruebas Maven pertenece a la verificacion local previa; las pruebas Docker de esta entrega estan separadas. Las imagenes del PDF son visualizaciones de respuestas y logs reales; se incluyen sus archivos originales.

## Reconstruir desde las fuentes

Se necesita Docker y Git. Los servicios no modificados se obtienen de la base oficial:

```powershell
git clone --branch 03-gateway-discovery-balanceo https://github.com/OscarRuiz21/mexi-banco.git mexi-banco-s08-repro
# Desde esta carpeta s08/:
Get-ChildItem -LiteralPath .\codigo\transferencia -Force | Copy-Item -Destination .\mexi-banco-s08-repro\transferencia -Recurse -Force
Copy-Item .\codigo\docker-compose.yml .\mexi-banco-s08-repro\docker-compose.yml
Set-Location .\mexi-banco-s08-repro
docker compose up --build -d --scale cuenta=1
```

El compose entregado cambia el nombre del proyecto/red para aislar el laboratorio y agrega FALLA_ABONO_CLABE. Si el puerto 8080 esta ocupado, cambiar el puerto publicado del gateway a 18080. Para la pausa de 20 s, fijar en gateway `SPRING_CLOUD_GATEWAY_HTTPCLIENT_RESPONSE_TIMEOUT: 45s`, mayor que la pausa.

Para las pruebas unitarias, con JDK 21, entrar a transferencia y ejecutar `.\mvnw.cmd test`.

## Repetir los escenarios

Crear cuentas ficticias con CLABE de 18 digitos por `POST /api/cuentas`, con `saldoInicial` 1000 y 500. Usar CLABEs nuevas por escenario; los volumenes conservan los datos.

El cuerpo de transferencia es `{"claveOrigen":"...","claveDestino":"...","monto":200}` para `POST /api/transferencias`.

1. **Compensada**: establecer `$env:FALLA_ABONO_CLABE` a la CLABE destino, `$env:PAUSA_ENTRE_CARGO_Y_ABONO_MS='0'`, recrear transferencia con `docker compose up -d transferencia` y enviar. Leer el estado de la respuesta y consultar ambos saldos.
2. **Pendiente**: usar otro par de cuentas, establecer la falla al destino y la pausa a `'20000'`. Recrear transferencia, enviar en otra terminal y ejecutar `docker compose stop cuenta` durante la pausa. Consultar el registro en PostgreSQL: debe quedar COMPENSACION_PENDIENTE.
3. **Circuito**: con cuenta apagada, enviar varias solicitudes; tras tres fallas de infraestructura se abre. Ejecutar `docker compose start cuenta`, esperar su registro y al menos diez segundos; una consulta con origen existente y destino inexistente permite sondear sin nuevo cargo. Ver los logs con `docker compose logs transferencia`.
4. **Original**: en un clon sin modificar de la rama oficial, usar pausa de 20 segundos y apagar cuenta durante la pausa. Consultar ambos saldos despues de recuperarla; no usar la saga para representar este escenario.

SQL de comprobacion:

```powershell
docker compose exec -T db psql -U mexibanco -d transferencia -c "select id,estado,detalle from transferencia order by id;"
docker compose exec -T db psql -U mexibanco -d movimiento -c "select * from movimiento order by id;"
```

`codigo/compose-evidencias.yml` y `codigo/capturar-ejecucion.py` documentan la captura con imagenes locales reutilizadas. Ese override usa etiquetas locales (`mexi-banco-v06-*` y `mexi-banco-s08-saga`), no descargables. Para reproducir en otra maquina, reconstruir desde fuentes como arriba; no asumir que esas imagenes existen. El script contiene CLABEs usadas en esta captura, que deben cambiarse antes de repetir.

Al terminar las pruebas, restablecer `$env:FALLA_ABONO_CLABE=''`, `$env:PAUSA_ENTRE_CARGO_Y_ABONO_MS='0'` y recrear transferencia. `docker compose down` detiene sin borrar los volumenes.

Fuente: https://github.com/OscarRuiz21/sd-2027-1/blob/main/labs/s08-saga-circuit-breaker/README.md
