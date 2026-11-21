# S07 · Directorio, gateway y balanceo

Ejecución asistida del 19 de octubre de 2026 sobre la rama de laboratorio
`lab/03-gateway-discovery-balanceo`. Las adiciones de configuración y del builder
balanceado siguen la guía y se cotejaron con `03-gateway-discovery-balanceo` del
profesor. La referencia docente se reconoce como tal; las mediciones de esta
máquina se conservan por separado.

`codigo/` conserva el material base y los cambios indicados. `compose.recursos.json`
limita la JVM para esta laptop y amplía la ventana de arranque; no cambia el
experimento de descubrimiento ni el código de negocio. `probar_s07.py` registra
el antes y las mediciones del gateway, balanceo y caída con la hora de México.

Construir los servicios uno por uno: los Dockerfiles comparten una caché Maven.
Al terminar, usar el compose final y el override de recursos. El proyecto Docker
es `derek-s07`; la red y el volumen están separados de S05.

## Explicación para revisar

Al partir el monolito, una llamada HTTP confirma en otra base y una transacción
local ya no deshace las escrituras remotas. El directorio resuelve nombres y
mantiene instancias; el gateway es la puerta con reglas; el balanceador elige
una copia de la lista que tiene en caché.

Registrar servicios no obliga a los clientes a usar ese registro: en el paso
inicial transferencia todavía usa DNS de Compose. El builder `@LoadBalanced`
y las URLs por nombre cambian ese comportamiento en la tarea final.

Las tres copias de cuenta comparten la misma base: esto demuestra réplicas de
aplicación y reparto de solicitudes, no tres copias independientes de los datos.
La predicción de la caída se guardó antes del experimento; el resultado medido
puede ser distinto del ensayo del profesor y es el que debe describir la bitácora.

Guía: https://oscarruiz21.github.io/sd-2027-1/labs/s07-mexi-banco/Lab-S07-Mexi-Banco.html
