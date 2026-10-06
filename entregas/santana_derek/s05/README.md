# S05 - Mexi Banco con Docker Compose

**Derek Santana Cardoso · GitHub DerekMstn114 · rama entregas_santana_derek.**

Reporte final: **reporte-s05.pdf**. Los seis pasos de la guía están documentados: versión docente v05.1, build y salud, 20 peticiones en Postman de escritorio, persistencia con y sin volumen, cuatro capas de los cinco módulos y explicación de Compose/Dockerfile.

## Resultados manuales

Las 21 capturas originales en `evidencias/postman/` muestran las 20 peticiones: origen Ana 1000, destino Beto 500; transferencia de 200 con saldos 800/700; SPEI de 50 con reintento del mismo id y cargo único; origen 750, movimientos y avisos; errores 404/409/400/422/422/400 sin alterar ese saldo.

La misma CLABE `002180791238795820` se consultó en los dos ciclos. Tras `conservar` y `volver`, Terminal mostró app/db healthy y Postman respondió HTTP 200, Ana id 1, saldo 750. Tras `borrar-solo-s05` y `volver`, Terminal mostró el volumen S05 eliminado y recreado, ambos servicios healthy, y Postman respondió HTTP 404 para esa misma CLABE. Es el resultado esperado de borrar los datos. El nombre de la petición sigue diciendo saldo 750; su aserción original puede fallar en esta última consulta porque la cuenta ya no existe.

Las cuatro capturas de esos ciclos están en `evidencias/persistencia-manual/`. Todos los originales se conservaron byte por byte y tienen hashes en sus respectivos `manifest.json`. El anexo incluye las imágenes completas.

Newman ejecutó antes 20 peticiones y 34 aserciones oficiales sin fallas, como comprobación técnica complementaria. No se afirma que esas aserciones estén capturadas en Postman de escritorio. La guía exige revisar respuestas y capturas, pero no pide capturas de Test Results.

## Entorno y comandos

Código docente sin cambios: v05.1, commit `88203161aa31a371823715684a548d154e1516f8`, en `/tmp/mexi-banco-s05-derek`. `s05.sh` valida versión y limpieza antes de usar Compose. El override solo cambia el puerto a `127.0.0.1:8085:8080` y la red a `s05_santana_derek`. Proyecto `s05_santana_derek`, volumen `s05_santana_derek_mexibanco_datos`. Configuración temporal de Docker en `/tmp/s05-docker-config`.

Desde esta carpeta: `bash s05.sh estado` consulta el estado; `iniciar` construye y levanta; `conservar` baja sin borrar datos; `volver` levanta; `borrar-solo-s05` baja y elimina solo el volumen S05; `logs` muestra registros. Las pruebas requeridas ya están completas: no hace falta repetirlas. El arranque lento del primer ciclo manual se conserva en `evidencias/diagnostico-2026-10-06/`; terminó healthy sin intervención.

## Fuentes y entrega

[Guía oficial S05](https://oscarruiz21.github.io/sd-2027-1/labs/s05-mexi-banco/Lab-S05-Mexi-Banco-Postman.html), [aplicación v05.1](https://github.com/OscarRuiz21/mexi-banco/tree/v05.1) y [colección oficial](https://github.com/OscarRuiz21/sd-2027-1/blob/main/labs/s05-mexi-banco/mexi-banco-v05.1.postman_collection.json). La guía y la colección guardadas fueron idénticas al comparar main del 4 y 5 de octubre; hashes y revisión en `evidencias/07-requisitos-verificados.json`. La colección local cambia únicamente baseUrl a localhost:8085.

Fecha en tiempo: 27 de septiembre de 2026, 23:59; la guía indica que tarde queda en la tendencia sin bajar puntos. Los archivos están preparados solo en `entregas/santana_derek/s05/`; S02/S03/S04 se preservaron. No se realizó commit, push ni PR.

En GitHub Desktop revisa repositorio SD y rama `entregas_santana_derek`, comprueba que los cambios sean solo S05, haz el commit y **Push origin**, sin pull request. Mensaje sugerido: `Completa S05 Mexi Banco con reporte y evidencias de Postman`.
