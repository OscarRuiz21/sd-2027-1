# Índice de evidencias

- `00-*`: entorno, commit docente y configuración efectiva de Compose.
- `01-build.txt` y `02-estado.txt`: construcción y salud de app/db.
- `03-newman.txt/json`: 20 peticiones y 34 aserciones automatizadas aprobadas; complementan Postman.
- `04-persistencia.json` y `04-ciclo-compose.txt`: ciclo automatizado anterior, con consultas HTTP; distinto de la ejecución manual.
- `05-*`: Dockerfile y Compose originales.
- `06-integridad.json`: verificación previa de archivos y contenedores anteriores.
- `07-requisitos-verificados.json`: comparación de fuentes y cobertura de los seis pasos.
- `08-revision-pdf.json`: revisión visual y hash del reporte final.
- `09-postman-manual.json`: resultados manuales reales y los dos ciclos de persistencia.
- `postman/`: 21 capturas originales de las 20 peticiones; el historial requiere dos imágenes.
- `persistencia-manual/`: cuatro capturas originales de Terminal/Postman: healthy y saldo 750 conservando el volumen; borrado del volumen, recreación healthy y misma CLABE con HTTP 404.
- `diagnostico-2026-10-06/`: intento de reinicio que inicialmente reportó unhealthy, logs, consulta técnica y comprobación de integridad. El arranque tardó unos 132 segundos y se recuperó sin intervención.
- `intentos-previos/`: trazabilidad de intentos iniciales, no usados como prueba final.

Las imágenes se copiaron sin editar bytes, con procedencia y SHA-256 en los manifests. El reporte distingue las 34 aserciones automáticas de las respuestas manuales: no hay capturas de Test Results y la guía no las exige. El 404 tras borrar el volumen es el resultado esperado para la misma CLABE manual `002180791238795820`.
