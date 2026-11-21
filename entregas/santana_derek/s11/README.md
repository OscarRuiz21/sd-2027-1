# S11 · Persistencia políglota y caché

Preparación asistida de Derek Santana con Codex, iniciada el 3 de noviembre de
2026. Base docente: `OscarRuiz21/mexi-banco`, rama `06-poliglota-base`, commit
`9d10c2cdfce2d8d6d171d655bc680b6d2a1e23be`. La saga, Outbox, gateway y directorio
son de esa base y no se atribuyen a esta entrega. El repaso personal está pendiente.

## Cambios

- Caché `cuentas` compartida en Redis, JSON de CuentaVista y TTL de 30 segundos.
- `ConsultaDeSaldo` cachea únicamente la vista; autorizar cargos sigue leyendo SQL.
- Cargos y abonos invalidan después del commit mediante caché transaccional.
- MongoDB recibe un documento por destinatario con `_id=eventoId:clabe`, dinero
  Decimal128, fecha del evento y campos específicos SMS/CORREO/PUSH.
- Duplicados se ignoran individualmente: si cayó después del primer destinatario,
  la reentrega todavía puede guardar al segundo. No hay escritura duplicada a SQL.
- Índice `clabe_fecha` y consulta por CLABE de los diez avisos más recientes.
- Pruebas del consumidor adaptadas para verificar guardar en Mongo antes del ack,
  sin usar la bandeja SQL, y pruebas de formas, dinero exacto y duplicado parcial.

## Reproducir

Clonar la base indicada, copiar encima `codigo/cuenta`, `codigo/notificacion`
y `codigo/docker-compose.yml`. El compose usa proyecto/red `derek-s11`, gateway
18080, Eureka 18761, RabbitMQ 15673/panel 15674, Redis 16379 y MongoDB 27018.
Los cambios de nombres/puertos aíslan el ejercicio de laboratorios anteriores.
Las JVM tienen un límite de heap de 192 MB para este equipo. Los Dockerfiles de
los dos servicios modificados ejecutan Maven verify con pruebas.

```bash
docker compose -p derek-s11 up --build -d --wait
```

`probar_s11.py` recibe una fase y `--directorio <clon>`. Guarda comandos, códigos,
cuerpos HTTP, cabeceras y tiempos en `evidencias/`. Ejecutarlo sobre cuentas de
laboratorio: la fase `viejo` modifica deliberadamente el saldo SQL y lo restaura
en un bloque finally. No es un script para datos reales.

Fases: base, cache, medidas, mongo, viejo. Para comparar sin caché, recrear cuenta
con `CACHE_TIPO=none docker compose up -d cuenta`, esperar health y descubrimiento,
y ejecutar medidas con `MEDICION_CACHE=none`. Luego volver a la configuración normal.
La fase base puede ejecutarse con caché desactivada para reproducir el punto inicial.
Las horas UTC de los registros no se presentan como horas de participación en clase.

El reto opcional de Redis caído no se presenta como implementado: con la solución
mínima un fallo al borrar caché después del commit puede escapar y producir un
resultado ambiguo. No basta suponer que CacheErrorHandler atrapa el callback diferido.

Fuente: [enunciado oficial S11](https://github.com/OscarRuiz21/sd-2027-1/blob/main/labs/s11-poliglota/README.md).

## Validación del 7 de noviembre

Maven verify ejecutado con Java 21 en el host: **10 pruebas de cuenta y 19 de
notificación**, sin fallos ni omitidas. Los registros completos están en
`evidencias/cuenta-pruebas-local.log` y `notificacion-pruebas-local.log`.
Se compararon byte a byte las clases compiladas con las imágenes Docker ya
construidas: 24/24 clases de cuenta y 26/26 de notificación idénticas. Los
listados están en `evidencias/comparacion-imagen-*.txt`. Se reutilizan esas
imágenes para la integración; no se presenta el build interrumpido como exitoso.

Los healthchecks conservan sus consultas reales y tienen intervalos de 30 s y
hasta 120 s por comprobación en este equipo, para no matar el cliente de diagnóstico
mientras arranca. Esto hace más lenta la detección de fallas; es un ajuste del
entorno de laboratorio, no una recomendación de producción.

### Incidencias conservadas

- La primera consulta de una réplica falló al inicializar Redis con el timeout
  docente de 500 ms; las otras respondieron. Se conserva `cache-fallo-arranque.json`.
- La siguiente corrida confirmó el cargo de un peso, pero Transferencia agotó su
  timeout de 2 s y lo marcó FALLIDA sin abonar. Es un caso de resultado remoto
  ambiguo heredado de la saga base; no significa que no hubiera cargo.
  Se conserva `cache-fallo-timeout.json`. En los datos de laboratorio se devolvió
  ese peso mediante el endpoint de abono con llave idempotente y se verificó SQL:
  Ana 1000, Beto 500, Carla 300 (`conciliacion-arranque.json`).
- El compose amplía la espera de lectura de Transferencia a 10 s para este equipo.
  Esto reduce falsos timeouts; **no soluciona en general** la conciliación de cargos
  ambiguos ni el fallo de invalidación posterior al commit. No se borraron los
  registros fallidos ni se contaron como experimentos aprobados.

## Cierre local del 8 de noviembre

[Bitácora PDF](bitacora-s11.pdf), código y evidencias completos. Las seis fases
(base, caché, medidas Redis, Mongo, dato viejo y medidas sin caché) terminaron
con resultado OK; el registro integral confirma la restauración de Redis.
Las mediciones se ejecutaron el 7 de noviembre en horario de México, sin
suspensión detectada. Las capturas son del visor de esos registros reales.

- Caché compartida: seis consultas, tres réplicas y una lectura SQL; invalidación comprobada.
- Hit ratio: A 99%; B 83.33%, incluyendo consultas internas de Transferencia.
- Tres corridas de cien GET: 1 lectura SQL con Redis frente a 300 sin caché.
  Media conjunta 65.46 ms frente a 62.66 ms: no hubo mejora de latencia media.
- Mongo: tres formas, últimos diez avisos, índice y reentrega sin duplicados.
- Saldo desactualizado visible: el cargo se rechazó usando SQL.

El PDF tiene 15 páginas y fue revisado visualmente. Esto es un cierre local,
no una confirmación de subida ni de entrega en la plataforma.
