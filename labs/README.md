# Laboratorios

Las guías de laboratorio de cada sesión.

> **Tarea del caso de estudio:** el ejercicio de **REST contra gRPC** asignado en la S4 vive
> aparte, en [`tareas/T01-REST-vs-gRPC.md`](../tareas/T01-REST-vs-gRPC.md). Vence el
> **domingo 20 de septiembre**.

## Antes del sábado 29 de agosto

**[Instala Docker](https://oscarruiz21.github.io/sd-2027-1/labs/Instala-Docker.html)**:
elige tu sistema operativo arriba y sigue los pasos. Termina con la prueba
`docker run hello-world`; si la ves, ya quedaste. Las instrucciones de Linux están
probadas comando por comando en Ubuntu 24.04, Debian 13 y Fedora 44.

## Lab de la S2 · sábado 29 de agosto

**[Docker, día uno: de cero a tu propia imagen](https://oscarruiz21.github.io/sd-2027-1/labs/Lab-S02-Docker-dia-1.html)**:
el flujo de entrega en vivo + Docker desde cero. *Nota (05-sep): el modelo de entrega
cambió — todo va con push a tu rama `entregas_apellido_nombre`, SIN pull request hasta el
final del curso; donde la guía diga "abre tu PR", ignóralo (ver `GIT-CHEATSHEET.md`).*
Contenido Docker:
contenedores, ciclo de vida, puertos, diagnóstico y tu primera imagen. Cierra con el
**reto de cuatro misiones**, que es la entrega de la sesión. Cada comando y cada salida
esperada están probados tal cual.

## Lab de la S4 · sábado 12 de septiembre

**[Docker, día dos: estado, red, configuración y compose](https://oscarruiz21.github.io/sd-2027-1/labs/Lab-S04-Docker-dia-2.html)**:
volúmenes (el estado sobrevive al contenedor), redes definidas por el usuario (los
contenedores se encuentran por nombre), variables de entorno y `.env` (la configuración
vive fuera de la imagen) y `docker compose` (el sistema en un archivo). Antes del sábado
baja las imágenes en tu casa: `postgres:17-alpine`, `nginx:alpine`, `redis:8-alpine`,
`rabbitmq:4-management-alpine` y `alpine:3.20`. Cierra con el **reto de cuatro misiones**;
la entrega es un push a tu rama en `entregas/apellido_nombre/s04/`, sin pull request.
