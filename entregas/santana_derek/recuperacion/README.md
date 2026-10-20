# Recuperación de actividades · Derek

Trabajo realizado el 19 y 20 de octubre de 2026, con asistencia de Codex.

## Proyecto de la tienda

Repositorio local separado: `/Users/derek/Documents/sd-2027-1-docker-rangers`.
Se conserva el PRD de Pablo en `docs/PRD.md`. Hay ramas locales `main` y
`01-monolito`. El monolito ya pasó la suite de compra, permisos, stock concurrente
e idempotencia. En `docs/PLAN-DEL-EQUIPO.md` están el mensaje para copiar al grupo
y una propuesta de trabajo; en `docs/GUIA-DEREK.md`, la explicación para estudiar.

Falta acordar trabajo con el equipo, publicar el repositorio, agregar colaboradores
y enlazarlo en la Discussion #44. No se ha enviado ningún mensaje al grupo.

## Entregas individuales

- S06: no aparece una guía de laboratorio S06 en el árbol oficial consultado el 20 de octubre. Esto no confirma el estado de lecturas o examen.
- S07: ejecución y PDF terminados; nueve respuestas repartidas entre tres réplicas, caída medida y demo de transferencia/SPEI correctas.
- S08: código, 14 pruebas, ensayos de compensación, pendiente y recuperación del circuito terminados. PDF revisado visualmente. Se conservó el primer intento y el ajuste que motivó.
- S09: código y pruebas (18 de transferencia + 5 de notificación), tres mensajes pendientes, consumo posterior, duplicado antes/después, DLQ y aviso de compensación comprobados; PDF terminado.
- T01: implementación y ejecución en Docker completadas; revisar explicación.
- T02: respuestas Zab/PBFT con fuentes primarias; lectura y explicación personal pendientes.
- T03: respuestas MongoDB con fuentes oficiales; lectura y explicación personal pendientes.

Los borradores y resultados asistidos requieren revisión personal. No se presentan
experimentos como realizados por Derek en clase ni se inventan capturas u horarios.

S09 vence el sábado 24 de octubre antes de las 07:00. S07/S08, tareas y monolito
ya vencieron; confirmar con el profesor cómo regularizarlos.

## Qué falta para que cuente como entrega

Los archivos están guardados localmente; todavía no se han subido los nuevos labs ni las tareas a GitHub. No hay PR, conforme a las reglas del curso. Revisar y practicar las explicaciones, y después subir solo la carpeta de Derek a su rama. Para S07/S08 y tareas vencidas, consultar la regularización con el profesor.

En el proyecto del equipo falta publicar el repositorio e invitar al equipo/profesor, además de acordar aportaciones reales. La base de la tienda es un monolito de API: aún no es el proyecto distribuido final.

Este repaso no confirma la entrega del examen a mano ni los comentarios de lecturas en Discussions.

## Repaso antes de entregar

- T01: señala `logica.consultar` y explica cómo REST y gRPC traducen el mismo resultado o error. La comparación de tamaños solo mide cuerpos, no tráfico total.
- T02: explica por qué Zab supone caídas y PBFT admite réplicas que mienten; calcula el quórum para 3 votantes y las réplicas para tolerar una bizantina.
- T03: recorre una consulta por mongos y explica por qué un producto popular sigue siendo caliente aunque su llave tenga hash.
- S07: distingue directorio, gateway y balanceador. Explica por qué pueden seguir apareciendo errores después de apagar una copia.
- S08: explica cuándo devolver y cuándo conciliar; por qué una saga no ofrece aislamiento global y por qué no reintentamos abonos sin idempotencia.
- S09: dibuja guardar, commit y ack. Explica qué ocurre si el consumidor cae entre commit y ack y qué hueco deja no implementar Outbox.

Fuentes de organización: [guías de labs](https://github.com/OscarRuiz21/sd-2027-1/blob/main/labs/README.md), [tareas](https://github.com/OscarRuiz21/sd-2027-1/blob/main/entregas/README.md) y [política de entregas](https://github.com/OscarRuiz21/sd-2027-1/blob/main/ENTREGAS.md).
