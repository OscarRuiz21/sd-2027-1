# S09 · Avisos por RabbitMQ

Pablo Vaquero · 6 de noviembre de 2026 · Recuperación fuera del plazo original.

La bitácora contiene capturas reales y las preguntas de cierre. `evidencia/` conserva respuestas HTTP, conteos SQL, estadísticas RabbitMQ, logs y scripts de los experimentos. No se da por recuperado el registro en calificaciones: el corte individual dice que esta entrega ya no se registra.

## Ejecutar

```sh
git clone --branch 05-mensajeria-base https://github.com/OscarRuiz21/mexi-banco.git mexi-banco-s09
cd mexi-banco-s09
git checkout 22c52075f70909182748f28bf9c487632144796b
# Copiar transferencia/, notificacion/ y docker-compose.yml desde codigo/ de esta entrega.
docker compose up --build -d --wait
```

Gateway: http://localhost:18082. Eureka: http://localhost:8761. RabbitMQ: http://localhost:15672, credenciales ficticias del lab `mexibanco` / `mexibanco`. Proyecto y red del Compose entregado: `mexibanco-s09-pablo`. No ejecutar simultáneamente otro Mexi Banco con esos puertos. Para conservar las colas y mensajes usar `docker compose stop` y después `start`: la base oficial no monta volumen para RabbitMQ; `down` borra su contenedor.

## Implementación y resultados

- Exchange topic durable `transferencias`; rutas `transferencia.realizada` y `transferencia.revertida`.
- La saga guarda el estado final antes de publicar. `eventoId` es estable por transferencia y tipo. Se retiró la llamada HTTP a Notificación de la saga interna; SPEI conserva su flujo original.
- Confirmaciones correlacionadas, `mandatory`, devolución de mensajes sin ruta y mensajes persistentes. La confirmación del broker no prueba que el consumidor haya guardado el aviso.
- Consumidor con ack manual después del commit, dos avisos por realizada y uno por revertida. Marca UNIQUE de evento y avisos en una transacción PostgreSQL mediante `INSERT ON CONFLICT DO NOTHING`.
- JSON inválido: nack sin reencolar hacia `transferencias.dlx` y `notificacion.transferencias.dlq`. Falla real de almacenamiento: reencolar.

| Experimento | Resultado real |
| --- | --- |
| Base sin notificación | HTTP 500, transferencia 1 ABONADA, saldos 990 / 510 |
| Consumidor apagado | Transferencias 5, 6 y 7: 201; tres mensajes Ready |
| Consumidor reactivado | Cola cero; bandejas HTTP 200 con avisos |
| Duplicado sin protección | Dos publicaciones del mismo evento: 2 y luego 4 avisos |
| Duplicado con protección | Dos publicaciones: 2 y después 2 avisos |
| Mensaje inválido | Antes: reentregas; después: principal 0, DLQ 1 y transferencia normal 201 |
| Compensación | Transferencia 8 COMPENSADA; un aviso de devolución de $5 |
| Broker apagado | Cliente recibió 504, transferencia 10 COMPLETADA; el aviso llegó después de reactivar el broker mientras seguía viva la petición original |
| Sin binding | Transferencia 11 COMPLETADA, HTTP 500 por mensaje devuelto sin ruta |
| Pruebas Java | 25 de transferencia y 13 de notificación aprobadas |

Los eventos con transferenciaId 99999 son sintéticos para probar avisos; no representan movimientos de dinero. `04-duplicado-ingenuo.json` incluye un GET que respondió 503 mientras Eureka actualizaba el registro: los conteos SQL 2/4 y los logs sí verifican la duplicación; la captura se tomó después de que la bandeja respondió. No se alteró ese resultado.

## Reproducir las fallas

Copiar `evidencia/` dentro del clon, fijar `S09_APP` a su ruta y revisar `experimentos.py`. `base` se ejecuta sobre el código oficial antes de copiar la solución, en una base nueva. Los experimentos posteriores cambian datos ficticios y no deben repetirse sobre datos importantes. `desacople` apaga y reactiva Notificación; `compensada` requiere levantar Transferencia con `FALLAR_ABONO_A_CLABE=002180000000000002` y después restaurarla sin esa variable. `broker` apaga RabbitMQ y lo reactiva al terminar.

Los modos defectuosos se activan solo para mostrar el antes mediante el override `compose-demo.yml`: `SIN_IDEMPOTENCIA=true` o `REENCOLAR_INVALIDOS=true`. En la entrega ambos son **false** de forma predeterminada. Reconstruir sin esos modos para el resultado final. La comparación del mensaje inválido usa DLQ ya declarada: el cambio demostrado es dejar de reencolarlo. `sin-binding.py` retira temporalmente el binding y lo restaura en `finally`, sin borrar colas ni mensajes.

## Alcance y referencias

Se partió de `05-mensajeria-base`. Para recuperar y comprender mensajería se usaron como referencia las clases ya resueltas en la base oficial `06-poliglota-base`, adaptadas a PostgreSQL, con pruebas del publicador/consumidor y modos de demostración. Preparación con apoyo de Codex; no se atribuye el código de referencia como invención propia.

No se implementó el reto opcional Outbox. Hay una ventana entre confirmar la saga y publicar: una caída del proceso ahí puede perder el aviso. La llegada tardía observada en el experimento del broker no demuestra recuperación persistente. Un Outbox debería guardar la intención junto al estado final y reenviarla desde un relay, manteniendo deduplicación en destino.

- [Guía S09](https://github.com/OscarRuiz21/sd-2027-1/blob/main/labs/s09-mensajeria/README.md)
- [Confirmaciones de RabbitMQ](https://www.rabbitmq.com/docs/confirms)
- [Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)
