# T03 · Sharding con Redis Cluster

Josué Hernández Rubio · Sistemas Distribuidos · Grupo 2

## 1. Qué es y para qué se usa

Redis guarda datos en memoria y se usa, por ejemplo, para cachés, sesiones y contadores. Redis Cluster reparte las claves entre varios nodos primarios. Así se distribuyen también el uso de memoria y el trabajo de atender peticiones.

## 2. Cómo reparte los datos

El clúster tiene 16 384 *slots*, numerados del 0 al 16 383. Redis calcula `CRC16(clave) % 16384` y guarda la clave en el nodo encargado de ese slot. La aplicación elige el nombre de la clave. Por ejemplo, `pedido:1` y `pedido:2` pueden quedar en nodos distintos.

También se puede usar una etiqueta entre llaves. Con `pedido:{17}:estado` y `pedido:{17}:total`, Redis calcula el hash de `17` en ambos casos. Eso pone las dos claves en el mismo slot y permite operarlas juntas.

## 3. Cómo encuentra el nodo correcto

Cada nodo mantiene un mapa de qué slots atiende cada primario. Los nodos comparten esa información entre sí. Un cliente que entiende Redis Cluster puede consultar el mapa con `CLUSTER SHARDS`, guardarlo y mandar cada petición al nodo correspondiente. Si la manda al nodo equivocado, recibe `MOVED` con la dirección a la que debe reenviarla.

## 4. Qué pasa al agregar un shard

Un primario nuevo entra vacío y empieza a recibir claves cuando se le asignan slots. El administrador puede usar `redis-cli --cluster reshard` para mover slots y las claves que contienen. Si se mueve un slot con 20 claves, se mueven esas 20. Si está vacío, cambia el mapa y se mueven cero claves. El número de claves que se transfieren depende de lo que haya en los slots elegidos.

## 5. Qué pasa con un shard caliente

Se pueden repartir algunos slots del nodo saturado entre otros nodos. Si casi todo el tráfico va a una sola clave, mover su slot solo traslada la carga a otro nodo. En un contador, una opción es guardar varios contadores parciales en claves con slots distintos y sumarlos al consultar. Eso reparte las escrituras y hace más costosa la lectura del total.

## Prueba con Docker

Levanté tres primarios de prueba con Redis 8 y cargué cuatro pedidos. Usé la misma red de Docker para que los nodos se encontraran por nombre.

```bash
docker network create t03-redis

nodo() {
  docker run -d --name "$1" --network t03-redis redis:8-alpine \
    redis-server --cluster-enabled yes --cluster-config-file nodes.conf \
    --save "" --appendonly no
  until docker exec "$1" redis-cli PING | grep -qx PONG; do sleep 1; done
}

for n in t03-redis-1 t03-redis-2 t03-redis-3; do nodo "$n"; done

docker exec t03-redis-1 redis-cli --cluster create \
  t03-redis-1:6379 t03-redis-2:6379 t03-redis-3:6379 --cluster-yes

until docker exec t03-redis-1 redis-cli CLUSTER INFO | grep -q 'cluster_state:ok'; do
  sleep 1
done

for i in 1 2 3 7068; do
  docker exec t03-redis-1 redis-cli -c SET "pedido:$i" "folio-$i"
done

mostrar() {
  for n in "$@"; do
    claves="$(docker exec "$n" redis-cli --scan | sort | paste -sd ' ' -)"
    printf '%s: %s\n' "$n" "${claves:-(vacío)}"
  done
}

mostrar t03-redis-1 t03-redis-2 t03-redis-3

for i in 1 2 3 7068; do
  printf 'pedido:%s: ' "$i"
  docker exec t03-redis-1 redis-cli CLUSTER KEYSLOT "pedido:$i"
done

docker exec t03-redis-1 redis-cli GET pedido:1
docker exec t03-redis-1 redis-cli -c GET pedido:1
```

Salida de la prueba:

```text
t03-redis-1: pedido:2 pedido:7068
t03-redis-2: pedido:3
t03-redis-3: pedido:1
pedido:1: 15101
pedido:2: 2718
pedido:3: 6847
pedido:7068: 0
MOVED 15101 172.26.0.4:6379
folio-1
```

El primer GET llegó al nodo 1 y recibió la redirección al nodo 3. Con `-c`, el cliente siguió la redirección y leyó el valor.

Luego agregué un cuarto nodo. `mostrar` devolvió la misma distribución y el cuarto apareció vacío. Después le moví un slot del nodo 1.

```bash
nodo t03-redis-4
docker exec t03-redis-1 redis-cli --cluster add-node \
  t03-redis-4:6379 t03-redis-1:6379
mostrar t03-redis-1 t03-redis-2 t03-redis-3 t03-redis-4

origen="$(docker exec t03-redis-1 redis-cli CLUSTER MYID)"
destino="$(docker exec t03-redis-4 redis-cli CLUSTER MYID)"
docker exec t03-redis-1 redis-cli --cluster reshard t03-redis-1:6379 \
  --cluster-from "$origen" --cluster-to "$destino" --cluster-slots 1 --cluster-yes

mostrar t03-redis-1 t03-redis-2 t03-redis-3 t03-redis-4
docker exec t03-redis-4 redis-cli GET pedido:7068
```

Se movió el slot 0 y con él solo `pedido:7068`. Las otras tres claves quedaron en sus nodos originales.

```text
t03-redis-1: pedido:2
t03-redis-2: pedido:3
t03-redis-3: pedido:1
t03-redis-4: pedido:7068
folio-7068
```

## Fuentes

- Redis. [Redis Cluster specification](https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/).
- Redis. [Scale with Redis Cluster](https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/).
- Redis. [Redis Anti-Patterns: Common Mistakes Every Developer Should Avoid](https://redis.io/tutorials/redis-anti-patterns-every-developer-should-avoid/).
