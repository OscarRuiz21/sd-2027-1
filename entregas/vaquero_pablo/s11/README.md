# Lab S11 · Redis, MongoDB y la verdad en PostgreSQL

Pablo Vaquero · Cuenta 318259624 · Grupo 2 · 6 de noviembre de 2026

Implementación del contrato de S11 sobre `06-poliglota-base`, commit `9d10c2cdfce2d8d6d171d655bc680b6d2a1e23be` de Mexi Banco. La bitácora explica las partes 0 a 4, las preguntas de cierre y las limitaciones. Las capturas provienen del navegador real; las respuestas, contadores y logs completos están en `evidencia/`.

## Levantar la entrega

```sh
git clone --branch 06-poliglota-base https://github.com/OscarRuiz21/mexi-banco.git mexi-banco-s11
cd mexi-banco-s11
git checkout 9d10c2cdfce2d8d6d171d655bc680b6d2a1e23be
# Copiar aqui cuenta/, notificacion/ y docker-compose.yml de codigo/.
docker compose up --build -d --wait
```

El Compose entregado usa proyecto y red `mexibanco-s11-pablo`, volumen propio y gateway en `http://localhost:18081`. Eureka: `http://localhost:8761`; RabbitMQ: `http://localhost:15672`; Redis: `127.0.0.1:6379`; MongoDB: `127.0.0.1:27017`. Si esos puertos están ocupados, usar las variables del Compose. No ejecutar simultáneamente otro Mexi Banco con los mismos puertos.

## Implementación

- `ConfiguracionCache`: activa caché, configura JSON tipado, TTL de 30 segundos y `transactionAware()`.
- `ConsultaDeSaldo`: lectura cacheable para mostrar; seis GET entre tres réplicas comparten `leidaEn` y requieren una sola lectura a PostgreSQL.
- `CuentaService`: invalida al confirmar cargos y abonos. La lectura que autoriza el dinero conserva acceso a PostgreSQL.
- `BitacoraDeAvisos`: documentos SMS, correo y push, dinero Decimal128, `_id=eventoId:clabe`, índice `clabe_fecha` y últimos diez por CLABE.
- `ConsumidorDeTransferencias`: guarda en MongoDB antes del ack. Solo trata una clave duplicada como repetición; las fallas reales se reencolan.

## Resultados reales

| Experimento | Resultado | Evidencia |
| --- | --- | --- |
| Base | Diez GET, diez lecturas a PostgreSQL; Mongo vacío | `01-base.json` |
| Caché compartida | Tres instancias, una lectura, TTL 30; invalidación tras transferir | `02-cache.json` |
| Cien GET | 99 hits, 1 miss: 99 % | `03-hit-ratio-A.json` |
| Cien GET y diez transferencias | 100 hits, 20 misses: 83.33 % | `03-hit-ratio-B.json` |
| Latencia | Tres corridas de cien peticiones por modo, con las 600 muestras | `04-latencia-*.json` |
| Índice Mongo | Diez documentos examinados; recorrido natural: 72 | `05-mongo.json` |
| Evento repetido | Dos avisos después de la primera publicación y dos después de repetir | `06-duplicado.json` |
| Dato viejo | Redis mostró $954; PostgreSQL tenía $5; cargo de $100 rechazado con 422 | `07-dato-viejo.json` |
| Pruebas | 10 de cuenta y 19 de notificación aprobadas | `tests-*.txt` |

En B hay 120 accesos a Redis porque la saga también hace dos consultas HTTP por transferencia. El hit ratio se calcula sobre todos esos accesos, no solo sobre los cien GET externos. La mejora de latencia fue variable; la reducción más clara fue de cien lecturas a PostgreSQL a una por corrida.

El evento con `transferenciaId=99999` es un mensaje sintético publicado únicamente para probar la deduplicación; no representa una transferencia real de dinero. Los contactos y cuentas son los datos ficticios del enunciado.

## Repetir experimentos

Copiar `evidencia/` al directorio del clon. El script usa Python estándar, Docker y los servicios reales. Para comparar el punto de partida, ejecutar `base` **antes** de copiar el código modificado. Usar un entorno nuevo: el script exige crear las tres cuentas y no sobreescribe cuentas existentes.

```sh
export S11_APP="$PWD"
export S11_URL=http://localhost:18081
python3 evidencia/experimentos.py base
# Copiar los servicios modificados de codigo/ y reconstruir:
docker compose up --build -d --wait cuenta notificacion
python3 evidencia/experimentos.py cache
python3 evidencia/experimentos.py con
python3 evidencia/experimentos.py mongo
python3 evidencia/experimentos.py duplicado
CACHE_TIPO=none docker compose up -d --wait cuenta
python3 evidencia/experimentos.py sin
docker compose up -d --wait cuenta
python3 evidencia/experimentos.py viejo
```

El script fija el proyecto `mexibanco-s11-pablo`. Si se cambia el puerto del gateway o del panel de RabbitMQ, hay que actualizar las URLs del script. Repetir `cache` también hace nuevas transferencias de prueba; los saldos y el total acumulado de avisos pueden variar.

`viejo` cambia temporalmente el saldo en PostgreSQL para saltarse la invalidación, comprueba el rechazo del cargo y restaura el saldo en un bloque `finally`. No se debe ejecutar en cuentas reales. Las muestras cada cinco segundos detectaron el refresco entre 26.2 y 31.6 segundos desde el primer control; eso no significa que el TTL de una entrada haya excedido treinta segundos.

## Pruebas automáticas

En el clon con los cambios copiados, con Java 21 instalado:

```sh
cd cuenta
./mvnw test
cd ../notificacion
./mvnw test
```

También se ejecutaron construyendo la etapa `builder` de cada Dockerfile y corriendo `./mvnw test` dentro de esa imagen. No se incluyen carpetas `target/`.

## Límites

No se implementó el reto opcional de Redis caído: una falla de caché puede afectar las peticiones. El TTL no da consistencia fuerte de lectura. Los dos avisos de un evento se insertan por separado; si hay una caída entre ambos, el reintento completa el faltante gracias a sus claves estables. El código conserva el Outbox de la base oficial de S09, no se atribuye como desarrollo de esta entrega.

Fuente: [enunciado S11](https://github.com/OscarRuiz21/sd-2027-1/blob/main/labs/s11-poliglota/README.md). Trabajo preparado con apoyo de Codex; resultados obtenidos en la ejecución local.
