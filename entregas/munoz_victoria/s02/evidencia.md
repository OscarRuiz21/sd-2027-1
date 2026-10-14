# Evidencia de Laboratorio · Sesión 02
**Estudiante:** Victoria Muñoz
**Directorio:** entregas/munoz_victoria/s02

---

## 1. Comandos utilizados
- docker run -d -p 8080:80 --name miweb nginx:alpine
- docker exec -it miweb sh
- docker logs miweb
- docker build -t mi-sitio:v2 .
- docker run -d -p 9090:80 --name sitio mi-sitio:v2
- docker run -d -p 9091:80 --name sitio2 mi-sitio:v2
- docker run -d -p 8080:80 --name choque nginx:alpine
- docker rm choque

---

## 2. Salida de docker ps
CONTAINER ID   IMAGE          STATUS          PORTS                  NAMES
4b9e11f0a281   mi-sitio:v2    Up 10 seconds   0.0.0.0:9091->80/tcp   sitio2
f3d7904bc122   mi-sitio:v2    Up 2 minutes    0.0.0.0:9090->80/tcp   sitio
519285cd786e   nginx:alpine   Up 20 minutes   0.0.0.0:8080->80/tcp   miweb

---

## 3. Error de puerto ocupado
docker: Error response from daemon: failed to set up container networking: Bind for 0.0.0.0:8080 failed: port is already allocated

---

## 4. Preguntas de reflexión

### ¿Por qué no cambió la página sin reconstruir?
Porque las imágenes de Docker son inmutables. Al compilar con build, COPY congeló index.html en una capa de solo lectura. El contenedor lee esa capa interna y no el archivo local del host. Para ver cambios se debe construir una nueva imagen (v2) y correr un nuevo contenedor.

### ¿Qué comparten y qué NO comparten dos contenedores de la misma imagen?
- Comparten: La imagen base de solo lectura (mismas capas de archivos, librerías y configuración inicial).
- No comparten: La capa de lectura/escritura (R/W), sus procesos (PIDs), memoria RAM, IDs y los puertos asignados en la máquina host.
