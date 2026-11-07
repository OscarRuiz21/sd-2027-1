# Lab S11 · Persistencia políglota y caché

La bitácora está en `bitacora-s11.pdf`. El código completo de los servicios modificados está en `codigo/cuenta/` y `codigo/notificacion/`, sin `target/`.

Se implementó la caché compartida de saldo con TTL de 30 s, la bitácora de avisos en MongoDB y el reto de continuar operando con Redis apagado. La bitácora incluye capturas de terminal, las seis corridas de latencia, ambos hit ratios, los planes de Mongo y las preguntas de cierre.

## Reproducir el entorno

El punto de partida es la rama `06-poliglota-base` de [Mexi Banco](https://github.com/OscarRuiz21/mexi-banco). Sobre una copia de esa rama, reemplazar `cuenta/`, `notificacion/` y `docker-compose.yml` por los de `codigo/`. Compose necesita también los demás servicios y `db/init/` de la rama base: no se ejecuta directamente desde esta carpeta de entrega.

Desde la raíz de esa copia:

```bash
docker compose up --build -d
docker compose ps
docker compose stop spei
# Al terminar, conservar los volúmenes:
docker compose down
```

El Compose entregado usa un nombre de proyecto y red propios (`mexibanco-s11`). MongoDB 8 no arrancó con el kernel local, por lo que se usó `mongo:7.0`. También se amplió a 10 s la espera HTTP de la saga: con Redis detenido, la resolución de su nombre puede tardar más que el límite de conexión configurado en Redis. El escritor tolerante protege el GET/SET y el DEL posterior al commit; las pruebas y la bitácora muestran ese caso.

Se comprobaron 13 pruebas de cuenta y 20 de notificación, y las pruebas de integración del lab. El saldo de Ana se restauró después del experimento del dato viejo y del intento de diagnóstico que produjo un timeout tras el cargo; al final, los saldos y la suma de movimientos coinciden y suman 1800.
