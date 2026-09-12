# Caso de estudio · El mismo servicio, por REST y por gRPC

**Sistemas Distribuidos · Clave 1959 · Grupo 2 · Semestre 2027-1**
Asignada en la S4 (12 de septiembre) · **entrega: domingo 20 de septiembre, 23:59**

---

## De qué se trata

En la clase comparamos REST y gRPC en el pizarrón: contrato, formato, transporte, streaming,
interoperabilidad. Esta tarea es para que esa comparación **deje de ser teórica**.

Vas a escribir **un solo servicio** y exponerlo **dos veces**: una por REST y otra por gRPC.
La regla que hace que el ejercicio sirva es esta:

> **La lógica de negocio es la misma para los dos. Lo único que cambia es el controlador y el
> mecanismo de comunicación.**

Si terminas con dos programas distintos, el ejercicio no se hizo. La gracia es ver que la
misma función se puede publicar de dos formas, y sentir en las manos qué cuesta cada una.

## Qué tiene que hacer el servicio

Algo deliberadamente simple: **recibe un ID y devuelve la información asociada**. Si el ID no
existe, responde que no hay datos.

Puedes simular la "base de datos" con un diccionario, un mapa o una lista en memoria. **No
inviertas tiempo en la lógica**: la tarea no evalúa qué tan interesante es tu servicio, sino
que la misma lógica quede detrás de dos interfaces.

## Lo que tienes que entregar

| | Qué |
|---|---|
| **1** | Un **servidor** con la lógica de negocio, expuesta por REST **y** por gRPC |
| **2** | Un **cliente** que sepa llamar a las dos |
| **3** | El **contrato**: tu archivo `.proto` para gRPC (y, si lo tienes, la definición REST) |
| **4** | **Dockerfile** para el cliente y para el servidor |
| **5** | Una **red de Docker** que los conecte, o un `docker-compose.yml` que levante todo |
| **6** | **Evidencia** de que corre: salidas, capturas o lo que uses para depurar |
| **7** | Un **`README.md`** explicando tu diseño y cómo se levanta |

### El framework es tuyo

Usa el que conozcas: **Spring**, **Django** o cualquier framework de Python, algo de **PHP**,
Node, Go, lo que domines. No hay puntos por usar uno en particular; sí los hay por que
funcione y por que entiendas lo que escribiste.

En Spring, por ejemplo, el controlador REST recibe la petición, serializa y deserializa el
JSON y llama a la capa de servicio. El controlador gRPC se apoya en el `.proto` y en el
código generado, y llama **a esa misma capa**.

## Qué se califica

- **Que la lógica sea compartida.** Es lo central. Un solo módulo de negocio, dos controladores.
- **Que las dos interfaces funcionen**, con su evidencia.
- **Que estén contenerizados y se comuniquen** por una red de Docker.
- **Que el `README` explique tu diseño con tus palabras**: dónde quedó la lógica, qué cambia
  entre un controlador y el otro, con qué comandos se levanta.

## Punto extra · mide los bytes

**Opcional.** Compara cuántos bytes viajan en una misma llamada por REST y por gRPC, y
repórtalo en tu `README`. Puedes usar el inspector del navegador, `tcpdump`, `wireshark`, los
logs del framework o lo que se te ocurra — **lo que importa es que digas cómo lo mediste**.

En clase vimos el ejemplo didáctico de 180 bits contra 60. Tu medición real probablemente no
dé esa proporción: eso también es un hallazgo, y explicarlo cuenta.

## Dónde se entrega

En tu rama, como todo:

```
entregas/apellido_nombre/s04/rest-vs-grpc/
```

```bash
git checkout entregas_apellido_nombre
git pull origin main
mkdir -p entregas/apellido_nombre/s04/rest-vs-grpc
# ... tu trabajo ...
git status
git add entregas/apellido_nombre/s04/rest-vs-grpc
git commit -m "Caso de estudio: REST y gRPC sobre la misma logica"
git push
```

**No abras pull request**: el push ES la entrega.

## Tiempos

**En tiempo hasta el domingo 20 de septiembre, 23:59.** Tienes dos fines de semana para
trabajarla. Tarde no baja puntos, pero queda registrado en tu tendencia de entregas (ver
[`ENTREGAS.md`](../ENTREGAS.md)).

## Si te atoras

Abre un **Issue** en el repositorio o pregunta en las Discussions. Atorarse con el `.proto` o
con la generación de código es normal la primera vez: pregunta temprano, no el domingo 20 a
las once de la noche.
