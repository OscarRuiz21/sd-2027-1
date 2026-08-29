\# Evidencia de Laboratorio S02 · Docker



\*\*Alumna:\*\* Luisa María Segura Cedeño  

\*\*Fecha:\*\* 29 de agosto de 2026  



\---



\## 1. Comandos Utilizados



```bash

\# Construcción de la imagen v2

docker build -t mi-sitio:v2 .



\# Despliegue de dos contenedores en paralelo (Misión 3)

docker run -d -p 9090:80 --name sitio1 mi-sitio:v2

docker run -d -p 9091:80 --name sitio2 mi-sitio:v2



\# Error intencional por colisión de puertos (Misión 4)

docker run -d -p 9090:80 --name choque mi-sitio:v2

