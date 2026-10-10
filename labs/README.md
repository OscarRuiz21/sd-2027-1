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

**[Lab S05: Mexi Banco con Docker Compose](https://oscarruiz21.github.io/sd-2027-1/labs/s05-mexi-banco/Lab-S05-Mexi-Banco-Postman.html)**:
los pasos de la clase en tu máquina. Clonar, levantar con Compose, probar los endpoints con
Postman, bajar con y sin volumen, revisar el código, y estudiar el compose y el Dockerfile.
Entregas un **reporte corto en PDF** (con el procesador de textos que quieras) con tus observaciones
y capturas en `entregas/apellido_nombre/s05/`,
**en tiempo hasta el domingo 27 de septiembre**.

## Mexi Banco: directorio, gateway y balanceo · sábado 3 de octubre (S7)

**[Lab S07: de un monolito a servicios que se encuentran solos](https://oscarruiz21.github.io/sd-2027-1/labs/s07-mexi-banco/Lab-S07-Mexi-Banco.html)**:
se trabaja sobre la rama `lab/03-gateway-discovery-balanceo` de
[mexi-banco](https://github.com/OscarRuiz21/mexi-banco). **Antes de clase**, en tu casa, sigue el
recuadro "Antes de clase" de la guía: clonar y construir tarda de 25 a 35 minutos. En clase se
platica la historia del monolito partido y luego, paso a paso, encienden un directorio (Eureka),
un gateway, tres copias de cuenta, y ven qué pasa cuando una muere. Se trabaja en equipo, pero
**cada quien entrega su propia bitácora en PDF** como `bitacora-s07.pdf` en
`entregas/apellido_nombre/s07/`, **hasta el sábado 10 de octubre, antes de las 07:00** (el plazo se amplió en clase).

---

Las **tareas** no están aquí: se apilan en el [`README.md` de entregas](../entregas/README.md),
y se entregan en tu carpeta, dentro de `tareas/`.
