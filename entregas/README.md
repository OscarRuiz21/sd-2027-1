# Entregas

Una carpeta por persona, con tu **primer apellido y tu primer nombre, en minúsculas y sin
acentos, unidos por un guion bajo**:

```
entregas/
└── ramirez_ana/
    ├── p00/
    ├── s02/
    ├── s03/ … s12/     ← los labs de cada sesion
    └── tareas/         ← las tareas van aparte, aqui
        └── t01/
```

Todo se entrega con **push a tu rama** `entregas_apellido_nombre` — sin pull request hasta
el final del curso (la rutina completa está en el
[`GIT-CHEATSHEET.md`](../GIT-CHEATSHEET.md); los tiempos, en
[`ENTREGAS.md`](../ENTREGAS.md)). Cada quien toca solo su carpeta.

## Las tareas

Aquí se van apilando las **tareas**, la más reciente hasta arriba. Las **prácticas de
laboratorio** no van aquí: cada una tiene su guía en [`labs/`](../labs/).

### T01 · El mismo servicio, por REST y por gRPC

**Asignada en la S4 (12-sep) · entrega el domingo 20 de septiembre, 23:59**

En la clase comparamos REST y gRPC en el pizarrón. Esta tarea es para que esa comparación
deje de ser teórica: escribes **un** servicio y lo expones **dos veces**.

> **La lógica de negocio es la misma para las dos. Lo único que cambia es el controlador y el
> mecanismo de comunicación.**

Si terminas con dos programas distintos, el ejercicio no se hizo.

**Qué hace el servicio.** Algo deliberadamente simple: recibe un ID y devuelve la información
asociada; si el ID no existe, responde que no hay datos. La "base de datos" puede ser un
diccionario en memoria. No inviertas tiempo en la lógica: no se evalúa qué tan interesante
sea, sino que quede **una sola** detrás de dos interfaces.

**Qué entregas:**

| | Qué |
|---|---|
| 1 | Un **servidor** con la lógica, expuesta por REST **y** por gRPC |
| 2 | Un **cliente** que sepa llamar a las dos |
| 3 | Tu **`.proto`** (el contrato de gRPC) |
| 4 | **Dockerfile** del cliente y del servidor |
| 5 | Una **red de Docker** que los conecte, o un `docker-compose.yml` |
| 6 | **Evidencia** de que corre: salidas, capturas, lo que uses para depurar |
| 7 | Un **`README.md`** con tu diseño y cómo se levanta |

**El framework es tuyo**: Spring, Django, PHP, Node, Go, lo que domines. No hay puntos por
usar uno en particular; sí los hay por que funcione y por que entiendas lo que escribiste.

**Qué se califica.** Que la lógica sea compartida — es lo central —, que las dos interfaces
funcionen con su evidencia, que estén contenerizadas y se comuniquen, y que el `README`
explique con tus palabras dónde quedó la lógica y qué cambia entre un controlador y el otro.

**Punto extra.** Mide cuántos bytes viajan en la misma llamada por REST y por gRPC y repórtalo
en tu `README`. Con el inspector del navegador, `tcpdump`, los logs del framework, como
quieras: lo que importa es que digas **cómo lo mediste**. En clase vimos el ejemplo didáctico
de 180 bits contra 60; tu medición real probablemente dé otra cosa, y explicar por qué también
cuenta.

**Dónde va:**

```
entregas/apellido_nombre/tareas/t01/
```

```bash
git checkout entregas_apellido_nombre
git pull origin main
mkdir -p entregas/apellido_nombre/tareas/t01
# ... tu trabajo ...
git status
git add entregas/apellido_nombre/tareas/t01
git commit -m "T01: el mismo servicio por REST y por gRPC"
git push
```

**No abras pull request**: el push ES la entrega. Tarde no baja puntos, pero queda registrado
en tu tendencia (ver [`ENTREGAS.md`](../ENTREGAS.md)).

Si te atoras con el `.proto` o con la generación de código —lo más común la primera vez—
abre un **Issue** y pregunta temprano, no el domingo 20 a las once de la noche.

## Tareas opcionales

Las **opcionales** no son obligatorias y no afectan tu calificación: cuentan para tu tendencia
de entregas y para los **puntos extra** del final del curso. Viven en
**[`OPCIONALES.md`](OPCIONALES.md)**. Está publicada la primera: **implementar bien el patrón
de idempotency key** (S03).
