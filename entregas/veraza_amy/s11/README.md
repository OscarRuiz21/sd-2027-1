# Lab S11 · Persistencia políglota y caché

Amy Valentina Veraza García · Sistemas Distribuidos 2027-1 · Grupo 2.

`bitacora-s11.pdf` reúne el diseño, las mediciones, las capturas y las respuestas de cierre. `evidencias/` conserva las respuestas originales, las muestras de cada corrida, los planes de MongoDB y los logs. Las capturas de archivos se identifican como vistas de evidencia; también se incluye una captura directa del endpoint HTTP.

## Reproducir

1. Clonar la rama oficial `06-poliglota-base` de https://github.com/OscarRuiz21/mexi-banco.git en una carpeta nueva.
2. Sustituir `cuenta/`, `notificacion/` y `docker-compose.yml` con las carpetas y el archivo incluidos en `codigo/`.
3. Ejecutar `docker compose up --build -d` y esperar a que todos los servicios están sanos. Mantener las tres réplicas de cuenta para demostrar que comparten Redis.
4. Gateway: http://localhost:18011/api. Panel RabbitMQ: http://localhost:15672, usuario y contraseña `mexibanco`.

El nombre del proyecto Compose es `mexi-banco-s11-amy` para aislar los datos de los laboratorios anteriores. Si hay otros entornos usando los mismos puertos, hay que cambiar las variables `PUERTO_GATEWAY`, `PUERTO_RABBIT`, `PUERTO_RABBIT_PANEL`, `PUERTO_REDIS` y `PUERTO_MONGO` según el compose, o detener el entorno que los ocupa.

## Experimentos

Copiar `experimentos.py` a la raíz del clon completo. Necesita Python y `requests`:

```powershell
python experimentos.py baseline
python experimentos.py cache
python experimentos.py mongo
python experimentos.py viejo
```

Ejecutar una sola vez, en ese orden y sobre un entorno nuevo: crean cuentas y transferencias. La primera fase reinicia cuenta con caché desactivada para medir y luego la vuelve a activar. La fase `viejo` cambia temporalmente el saldo en la base de este laboratorio para demostrar la lectura obsoleta; al finalizar restaura el saldo original y elimina la copia. No debe ejecutarse contra datos de producción.

La comparación inicial es el código implementado con `CACHE_TIPO=none`, no una ejecución de la versión original de notificación. Las latencias usan tres corridas de cien GET por configuración, con muestras completas adjuntas. Los contadores del escenario B incluyen las consultas internas de la saga, además de las solicitudes del cliente.

## Código y límites

- Redis: JSON `CuentaVista`, clave `cuentas::<clabe>`, TTL 30 s y caché compartida entre instancias.
- El cargo se autoriza contra PostgreSQL; cargo y abono invalidan la copia con `@CacheEvict` y configuración `transactionAware()`.
- MongoDB: un documento por `eventoId:clabe`, Decimal128 para dinero, forma por canal, índice `clabe_fecha` y últimos diez por CLABE.
- El consumidor guarda en Mongo antes del ACK. Un destinatario duplicado se omite y no impide guardar al siguiente tras un fallo parcial.
- El reto opcional de tolerancia a Redis caído no está implementado. Un fallo de caché puede producir errores, incluida la invalidación posterior al commit.

Pruebas: 10 de cuenta y 18 de notificación. Con JDK 21: `./mvnw test`. En esta máquina se compiló para Java 21 con JDK 25 y `-Dnet.bytebuddy.experimental=true`; los contenedores usan Java 21. Desarrollo y redacción asistidos por IA; los resultados proceden de ejecuciones reales.

Guía: https://github.com/OscarRuiz21/sd-2027-1/blob/main/labs/s11-poliglota/README.md.
