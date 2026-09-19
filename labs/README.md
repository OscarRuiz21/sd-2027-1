# Laboratorios

Las guías de laboratorio, una carpeta por lab y en orden de sesión.

## Antes del sábado 29 de agosto

**[Instala Docker](https://oscarruiz21.github.io/sd-2027-1/labs/s00-instala-docker/Instala-Docker.html)**:
elige tu sistema operativo arriba y sigue los pasos. Termina con la prueba
`docker run hello-world`; si la ves, ya quedaste. Las instrucciones de Linux están
probadas comando por comando en Ubuntu 24.04, Debian 13 y Fedora 44.

## Docker, día uno · se impartió el sábado 5 de septiembre (S3)

**[Docker, día uno: de cero a tu propia imagen](https://oscarruiz21.github.io/sd-2027-1/labs/s02-docker-dia-1/Lab-S02-Docker-dia-1.html)**:
el flujo de entrega en vivo + Docker desde cero: contenedores, ciclo de vida, puertos,
diagnóstico y tu primera imagen. Cierra con el **reto de cuatro misiones**, que es la entrega.
Cada comando y cada salida esperada están probados tal cual.

*La guía se llama Lab-S02 porque estaba planeada para la S2 (29-ago), pero esa sesión no
alcanzó a llegar al laboratorio: se impartió el 5 de septiembre, al abrir la S3.* **Se entrega
en `entregas/apellido_nombre/s02/`**, conservando el número de la guía.

*Nota (05-sep): el modelo de entrega cambió — todo va con push a tu rama
`entregas_apellido_nombre`, SIN pull request hasta el final del curso; donde la guía diga
"abre tu PR", ignóralo (ver [`GIT-CHEATSHEET.md`](../GIT-CHEATSHEET.md)).*

## Docker, día dos · sábado 12 de septiembre (S4)

**[Docker, día dos: estado, red, configuración y compose](https://oscarruiz21.github.io/sd-2027-1/labs/s04-docker-dia-2/Lab-S04-Docker-dia-2.html)**:
volúmenes (el estado sobrevive al contenedor), redes definidas por el usuario (los
contenedores se encuentran por nombre), variables de entorno y `.env` (la configuración
vive fuera de la imagen) y `docker compose` (el sistema en un archivo). Antes del sábado
baja las imágenes en tu casa: `postgres:17-alpine`, `nginx:alpine`, `redis:8-alpine`,
`rabbitmq:4-management-alpine` y `alpine:3.20`. Cierra con el **reto de cuatro misiones**;
la entrega es un push a tu rama en `entregas/apellido_nombre/s04/`, sin pull request.

## Mexi Banco con Compose · sábado 19 de septiembre (S5)

**[Lab S05: Mexi Banco con Docker Compose](s05-mexi-banco/Lab-S05-Mexi-Banco-Compose.md)** (rúbrica en
[`Lab-S05-rubrica.md`](s05-mexi-banco/Lab-S05-rubrica.md)): el caso hilo conductor del curso ya es código.
`git clone --branch v05.1 https://github.com/OscarRuiz21/mexi-banco.git` y `docker compose up --build`.
Tres hitos: arriba y respondiendo, las propiedades de Compose con tus manos (red, healthcheck,
volumen, matar `app` y ver que no revive sola) e idempotencia del SPEI.

**[Guía visual con Postman](https://oscarruiz21.github.io/sd-2027-1/labs/s05-mexi-banco/Lab-S05-Mexi-Banco-Postman.html)**:
lo mismo que se mostró en clase, petición por petición, con la colección
[`mexi-banco-v05.1.postman_collection.json`](s05-mexi-banco/mexi-banco-v05.1.postman_collection.json) y 34 pruebas
automáticas. Usa Postman de escritorio.

Entrega en `entregas/apellido_nombre/s05/` con push a tu rama, **en tiempo hasta el domingo 27 de
septiembre** (se movió del 20: en clase el lab se hizo como demostración); tarde sin penalización
hasta antes de la S07.

---

Las **tareas** no están aquí: se apilan en el [`README.md` de entregas](../entregas/README.md),
y se entregan en tu carpeta, dentro de `tareas/`.
