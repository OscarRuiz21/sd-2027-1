# Lab S09 · Amy Veraza

La bitácora contiene el desarrollo y las evidencias reales. `codigo/` incluye transferencia y notificacion completos, sin target.

Para reproducir, clona la rama `05-mensajeria-base` de https://github.com/OscarRuiz21/mexi-banco.git y sustituye sus dos servicios y docker-compose.yml con los de `codigo/`. Ejecuta `docker compose up --build -d --scale cuenta=1` y usa http://localhost:18090/api. Panel RabbitMQ: http://localhost:15672, usuario y contraseña mexibanco.

Los archivos en evidencias contienen respuestas HTTP, logs, estados de colas y capturas del panel. Las etapas anteriores de duplicados y poison pill se ejecutaron con una versión experimental sin deduplicación y con requeue=true; el código entregado es la solución final. No incluye el reto opcional Outbox.

El script auxiliar `experimentos.py` necesita Python, requests y Playwright con Microsoft Edge. Define `MEXIBANCO_PROYECTO` con la ruta al clon completo antes de ejecutarlo. Sus fases modifican datos de prueba y detienen o encienden notificación; `base` se usa sobre la versión original, `antes` sobre una versión experimental sin protecciones y `cola`, `despues` y `revertida` sobre la solución, habilitando FALLAR_ABONO_A_CLABE para la última fase.
