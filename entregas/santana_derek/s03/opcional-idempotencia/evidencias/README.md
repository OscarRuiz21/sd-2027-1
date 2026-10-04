# Evidencia real de S03

La verificación automatizada se ejecutó en la Mac Intel de Derek con Docker Desktop.
No son respuestas inventadas ni capturas simuladas. Las pruebas locales y la
construcción/pruebas de la imagen terminaron el **3 de octubre de 2026**, hora de México;
la prueba HTTP del servicio y la recreación terminaron el **4 de octubre de 2026**.
Los logs del contenedor usan UTC, seis horas por delante de la hora local.

## Resultados comprobados

| Comprobación | Resultado real | Archivo |
| --- | --- | --- |
| 13 pruebas locales | `Ran 13 tests in 15.013s`, `OK` | [pruebas-locales.txt](pruebas-locales.txt) |
| Construcción Docker | `Image s03-derek-verificacion-cobros Built` | [docker-build.txt](docker-build.txt) |
| 13 pruebas dentro de Docker | `Ran 13 tests in 19.633s`, `OK` | [pruebas-docker.txt](pruebas-docker.txt) |
| Servicio publicado | Contenedor `Healthy` | [docker-servicio.txt](docker-servicio.txt) |
| Dos solicitudes simultáneas | Ambas `201`, mismo cuerpo; replay `false` y `true` | [http-concurrencia.txt](http-concurrencia.txt) |
| Reintento y datos distintos | Replay exacto `201`; otro monto `409` | [http-concurrencia.txt](http-concurrencia.txt) |
| Conteos e integridad | 1 operación, 1 cobro, WAL, integridad `ok` | [base-antes-recrear.txt](base-antes-recrear.txt) |
| Recrear contenedor | Contenedor recreado y `Healthy` | [docker-recrear.txt](docker-recrear.txt) |
| Replay tras recreación | `201`, replay `true`, mismo UUID y cuerpo | [http-replay-recrear.txt](http-replay-recrear.txt) |
| Conteos tras recreación | Iguales; comparación `cmp` termina con código 0 | [base-despues-recrear.txt](base-despues-recrear.txt) |

La clave de la ejecución HTTP fue `evidencia-s03-20261004`. El UUID real fue
`e166247a-9021-404e-bf32-0532b19f894e`. Dos solicitudes, un reintento y una solicitud con
datos distintos dejaron **una sola fila en cada tabla**. Después de recrear el
contenedor, ese mismo UUID volvió a recibirse y los conteos no cambiaron.

Las pruebas de caída usan `os._exit(77)` en procesos independientes en tres puntos.
Antes del COMMIT se comprobó que quedaban cero reservas y cero cobros; después del
COMMIT quedó una operación completa y el reintento reprodujo el cuerpo guardado.
Los tests concurrentes verificaron también 12 solicitudes HTTP y dos procesos que
comparten el mismo archivo SQLite. La prueba del Map ingenuo produjo dos efectos.

## Cómo se ejecutó

Desde la carpeta de esta actividad se ejecutaron estos comandos, conservando sus
salidas completas en los archivos anteriores:

```bash
python3 tests.py
docker compose -p s03-derek-verificacion build
docker compose -p s03-derek-verificacion run --rm --no-deps cobros python tests.py
docker compose -p s03-derek-verificacion up -d --wait
python3 probar_http.py --clave evidencia-s03-20261004
docker compose -p s03-derek-verificacion up -d --force-recreate --wait
python3 probar_http.py --clave evidencia-s03-20261004 --solo-replay
cmp evidencias/base-antes-recrear.txt evidencias/base-despues-recrear.txt
```

Para los conteos se ejecutó Python dentro del contenedor con `docker compose exec -T
cobros`: una conexión a `/data/cobros.sqlite3` consultó `COUNT(*)` en `operaciones` y
`cobros`, la fila original y `PRAGMA integrity_check`. El archivo de entorno registra
las versiones locales; los archivos de la base registran las versiones del contenedor.
La salida de descarga y su digest están en [docker-pull.txt](docker-pull.txt).

### Ajuste exclusivo del entorno del agente

El sandbox inicialmente bloqueó el socket de Docker y se concedió acceso. Después,
el CLI intentó acceder al llavero y falló con `Keychain Error (-67674)`. Para descargar
la imagen pública y construirla se usó una configuración **temporal** en
`/tmp/s03-derek-docker-config`, con acceso anónimo a Docker Hub y la ubicación de los
plugins instalados. Se seleccionó el socket existente con
`DOCKER_HOST=unix:///Users/derek/.docker/run/docker.sock`; no se editaron credenciales
ni la configuración habitual de Derek. Los comandos de construcción y pruebas en
Docker usaron `DOCKER_CONFIG=/tmp/s03-derek-docker-config` y ese `DOCKER_HOST`.
Este ajuste no es un requisito del proyecto ni hay que copiarlo para la ejecución
normal en Terminal. Las salidas guardadas corresponden a las ejecuciones exitosas.

El servicio de verificación se detuvo con `docker compose -p s03-derek-verificacion
down`, conservando el volumen de evidencia. La copia del alumno usa su propio proyecto
Compose, por lo que el cliente puede generar nuevas claves sin reutilizar esta base.

## Comprobación guiada realizada por el alumno

Derek ejecutó `docker compose up -d --build --wait` desde su checkout habitual y reportó
que el servicio terminó con `Healthy`. Después ejecutó `python3 probar_http.py` y
proporcionó la salida conservada en
[comprobacion-manual-alumno.txt](comprobacion-manual-alumno.txt), sin hora exacta registrada.
Esta evidencia proviene del alumno; el agente no repitió las pruebas para documentarla.

Las dos solicitudes recibieron `201` y el mismo UUID
`3e5a3fcf-7198-4f6b-a16f-8e8525fcbd9e`; la primera reprodujo el resultado (`true`) y
la segunda creó el cobro (`false`). Ese orden es válido: cualquiera puede ganar la
carrera. El reintento reprodujo el cuerpo y otro monto recibió `409`; el script terminó
con `OK`. Quedan pendientes el commit y push mediante GitHub Desktop. No se abrió PR.

No se probó un proveedor de pagos real, apagado eléctrico, fallo de disco ni rendimiento
de producción. El efecto simulado pertenece a SQLite; las limitaciones externas se
explican en el README principal.
