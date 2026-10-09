# S07 · Directorio, gateway y balanceo

**Alumno:** Pablo Vaquero · Sistemas Distribuidos 2027-1 · Grupo 2  
**Ejecución:** 9 de octubre de 2026 · Ciudad de México  
**Entrega principal:** [bitacora-s07.pdf](bitacora-s07.pdf) · [Word editable](bitacora-s07.docx)

La bitácora documenta los cinco pasos del laboratorio, las respuestas a «Piensa», el cierre y la tarea de balanceo interno de transferencia. Incluye horarios, capturas del tablero de Eureka y vistas de los resultados reales guardados durante la ejecución. Se preparó con apoyo de Codex en el equipo local, como se declara en el documento.

Se siguió la [guía oficial S07](https://github.com/OscarRuiz21/sd-2027-1/blob/736d00d65758cc17be7649ee60fec5bf6e0dfa55/labs/s07-mexi-banco/Lab-S07-Mexi-Banco.html), revisada en el `main` actualizado del curso. El código parte de [Mexi Banco, commit 4ce58c4](https://github.com/OscarRuiz21/mexi-banco/tree/4ce58c41489f5e1f6267066a23b3660b23e67b88), rama `lab/03-gateway-discovery-balanceo`.

## Resultados

- Cinco servicios registrados en Eureka; CUENTA se amplió después a tres réplicas.
- Nueve consultas por el gateway respondieron 200 y alternaron entre las tres instancias.
- Al apagar una réplica se registraron 7 respuestas 500 y 53 respuestas 200. La baja apareció en la API del directorio a los 28.73 segundos; el último error terminó a los 32.80 segundos.
- La tarea usa `RestClient.Builder` con `@LoadBalanced` y direcciones por nombre en transferencia. Los registros muestran el cargo y el abono atendidos por réplicas distintas.
- Transferencia de 200: saldos 800 y 700. SPEI de 50 y reintento con la misma llave: saldos 750 y 700 y el mismo ID de SPEI.
- Los errores esperados 404 y 422 conservaron los saldos finales.
- Las 13 pruebas automatizadas pasaron sin fallos ni omisiones: 1 en discovery, 4 en gateway y 8 en transferencia. Las dos primeras comprobaciones se completaron después de exportar la bitácora; sus salidas están en esta carpeta.

Los tiempos son mediciones de esta ejecución, no garantías generales de recuperación. La pausa entre las sesiones de la madrugada y la mañana no se contó como tiempo de detección.

## Archivos

- `bitacora-s07.pdf`: documento final de nueve páginas, revisado visualmente.
- `bitacora-s07.docx`: fuente editable en Word.
- `cambios-s07.patch`: cambios de implementación y habilitación de las pruebas de la plantilla.
- `evidencia/01-*` a `06-*`: estado inicial, registro, gateway, réplicas, predicción, caída y recuperación. Los JSON y TXT conservan las respuestas completas; las imágenes muestran vistas de esos registros o el tablero real.
- `evidencia/07-demo-completa.txt`: salida íntegra de la demo oficial; las otras evidencias `07-*` contienen sus extractos y registros de CUENTA.
- `evidencia/08-tests-*.txt`: resultados de las pruebas automatizadas.
- `evidencia/09-estado-final.json`: los diez contenedores sanos al terminar las pruebas. `09-config-final.yml` conserva la configuración resuelta.
- `evidencia/10-cierre.txt`: apagado de S07 sin borrar el volumen y restauración de la aplicación S05 que ocupaba el puerto 8080.

La captura complementaria `02-eureka-cinco-tabla.jpg` se tomó a las 08:02:38, repitiendo el estado de cinco servicios con una réplica y sin gateway. El primer registro original de esa etapa está en `02-directorio.json` a las 00:15:41. Esta diferencia también se explica en la bitácora.

## Reproducción

Requiere Docker Compose y los puertos 8080 y 8761 disponibles. Clonar el repositorio, fijar la revisión utilizada y aplicar el parche de esta entrega desde su ruta local:

```sh
git clone --branch lab/03-gateway-discovery-balanceo https://github.com/OscarRuiz21/mexi-banco.git mexi-banco-s07
cd mexi-banco-s07
git checkout 4ce58c41489f5e1f6267066a23b3660b23e67b88
git apply /ruta/a/la/entrega/s07/cambios-s07.patch
docker compose up --build -d --wait
docker compose ps
./demo-v06.sh
```

Consultar el tablero en `http://localhost:8761` y la API del banco por `http://localhost:8080/api`. Las réplicas de CUENTA comparten su base lógica; el laboratorio no replica la base de datos ni agrega una transacción distribuida.

Para ejecutar las pruebas usando Java dentro de Docker:

```sh
for servicio in discovery gateway transferencia; do
  docker build --target builder -t "s07-${servicio}-tests" "$servicio"
  docker run --rm "s07-${servicio}-tests" ./mvnw test -B
done
```

Para cerrar el laboratorio conservando sus datos:

```sh
docker compose down
```

Los identificadores de contenedor, las CLABEs generadas por la demo y los tiempos de recuperación pueden variar al repetir el experimento. Los archivos entregados conservan los resultados originales del 9 de octubre.
