# Evidencia del laboratorio S02: Docker, día uno

## 1. Construcción y ejecución

Construí una imagen con mi Dockerfile y mi página:

```bash
docker build -t derek-s02:v1 entregas/santana_derek/s02
docker run -d -p 9090:80 --name derek-s02-sitio derek-s02:v1
curl -i http://localhost:9090
```

La construcción terminó correctamente. La consulta respondió HTTP 200 OK
y mostró mi página con la frase:
“Esta página la sirve un contenedor que yo construí.”

## 2. Dos contenedores de la misma imagen

Ejecuté una segunda instancia:

```bash
docker run -d -p 9091:80 --name derek-s02-sitio2 derek-s02:v1
curl -i http://localhost:9091
docker ps --filter name=derek-s02
```

Ambos contenedores estaban activos, usaban derek-s02:v1 y respondían
HTTP 200 OK. Cada uno publicaba un puerto distinto: 9090 y 9091.

## 3. Error de puerto ocupado

Intenté usar nuevamente el puerto 9090:

```bash
docker run -d -p 9090:80 --name derek-s02-choque derek-s02:v1
```

Docker rechazó el arranque con este mensaje:

```text
Bind for 0.0.0.0:9090 failed: port is already allocated
```

El puerto ya pertenecía al primer contenedor. Eliminé el contenedor
que no pudo arrancar:

```bash
docker rm derek-s02-choque
```

## 4. Cambio local y reconstrucción

Cambié una frase de index.html por:
“Prueba nueva: cambié este archivo en la Mac.”

git diff confirmó el cambio. Sin reconstruir la imagen, una consulta
al puerto 9090 todavía mostró la frase anterior. El archivo había
sido copiado a la imagen; no estaba montado desde la Mac.

Después ejecuté:

```bash
docker build -t derek-s02:v2 entregas/santana_derek/s02
docker stop derek-s02-sitio
docker rm derek-s02-sitio
docker run -d -p 9090:80 --name derek-s02-sitio derek-s02:v2
curl -s http://localhost:9090
curl -s http://localhost:9091
```

El primer contenedor mostró la frase nueva. El segundo conservó la
anterior porque seguía usando v1.

## 5. Estado final observado

| Contenedor | Imagen | Estado | Puerto de la Mac → contenedor |
|---|---|---|---|
| derek-s02-sitio | derek-s02:v2 | Activo | 9090 → 80 |
| derek-s02-sitio2 | derek-s02:v1 | Activo | 9091 → 80 |

## 6. Conclusión

Dos contenedores creados desde la misma imagen parten del mismo
contenido, pero tienen procesos, estado y puertos independientes.

Cambiar el archivo local no actualiza la imagen ni el contenedor.
Para servir el cambio construí v2 y reemplacé el primer contenedor.
El segundo siguió mostrando el contenido anterior.