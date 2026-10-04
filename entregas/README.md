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
        ├── t01/
        └── t02/
```

Todo se entrega con **push a tu rama** `entregas_apellido_nombre` — sin pull request hasta
el final del curso (la rutina completa está en el
[`GIT-CHEATSHEET.md`](../GIT-CHEATSHEET.md); los tiempos, en
[`ENTREGAS.md`](../ENTREGAS.md)). Cada quien toca solo su carpeta.

## Las tareas

Aquí se van apilando las **tareas**, la más reciente hasta arriba. Las **prácticas de
laboratorio** no van aquí: cada una tiene su guía en [`labs/`](../labs/).

### T03 · Una implementación real de sharding

**Asignada en la S7 (3-oct) · entrega el sábado 10 de octubre, antes de las 07:00**

En la clase vimos el sharding en abstracto: la llave de partición, por rango o por hash, el
ruteo y el shard caliente. Esta tarea es para ver **cómo lo resuelve una tecnología de verdad**.

**Qué haces.** Eliges **una tecnología que haga sharding** y escribes, con tus palabras:

1. **Qué es** y para qué se usa.
2. **Cómo reparte los datos**: ¿por rango, por hash o de otra forma? ¿Quién elige la llave?
3. **Cómo encuentra el shard correcto** cuando llega una consulta: ¿quién guarda el mapa de qué
   dato vive dónde?
4. **Qué pasa cuando agregas un shard**: ¿se mueven los datos? ¿cuántos?
5. **Qué hace con un shard caliente**, si hace algo.

Al final, las fuentes que usaste. Te puedes apoyar en IA, pero lo que escribas tienes que poder
explicarlo en clase.

Algunas para elegir (no es obligatorio escoger de aquí): MongoDB, Cassandra, Vitess (MySQL),
Citus (PostgreSQL), CockroachDB, Elasticsearch, Redis Cluster, DynamoDB.

**Opcional, suma puntos:** levántala con Docker, crea una tabla o colección repartida en dos o
más shards y muestra en qué shard quedó cada dato. Pon los comandos y la salida en tu README.

**Dónde va:**

```
entregas/apellido_nombre/tareas/t03/README.md
```

```bash
git checkout entregas_apellido_nombre
git pull origin main
mkdir -p entregas/apellido_nombre/tareas/t03
# ... tu README.md ...
git add entregas/apellido_nombre/tareas/t03
git commit -m "T03: una implementación de sharding"
git push
```

**No abras pull request**: el push ES la entrega. Tarde no baja puntos, pero queda registrado
en tu tendencia (ver [`ENTREGAS.md`](../ENTREGAS.md)).

### T02 · Dos algoritmos de consenso que no son Raft

**Asignada en la S5 (19-sep) · entrega el domingo 27 de septiembre, 23:59**

En la clase vimos cómo Raft pone de acuerdo a varios nodos con un líder, latidos y mayoría. Hay
otros algoritmos que resuelven el mismo problema de otra forma.

**Qué haces.** Eliges **dos algoritmos o protocolos de consenso que no sean Raft ni Paxos** y
escribes, para cada uno, **un resumen de cómo funciona**, con tus palabras. Al final, las fuentes
que usaste.

Algunos para elegir (no es obligatorio escoger de aquí): Zab (ZooKeeper), Viewstamped
Replication, PBFT, Tendermint, HotStuff, Proof of Work (Bitcoin), Proof of Stake (Ethereum).

Si Raft se te quedó a medias, empieza por este video: https://youtu.be/IujMVjKvWP4

**Dónde va:**

```
entregas/apellido_nombre/tareas/t02/README.md
```

```bash
git checkout entregas_apellido_nombre
git pull origin main
mkdir -p entregas/apellido_nombre/tareas/t02
# ... tu README.md ...
git add entregas/apellido_nombre/tareas/t02
git commit -m "T02: dos algoritmos de consenso"
git push
```

**No abras pull request**: el push ES la entrega. Tarde no baja puntos, pero queda registrado
en tu tendencia (ver [`ENTREGAS.md`](../ENTREGAS.md)).

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
