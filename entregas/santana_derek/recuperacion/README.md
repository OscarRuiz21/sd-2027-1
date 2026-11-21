# Recuperación de actividades · Derek

Trabajo iniciado el 19 de octubre y actualizado el 20 de noviembre de 2026,
con asistencia de Codex. Estado local; la publicación se comprueba por separado.

## Proyecto de la tienda

Repositorio local separado: `/Users/derek/Documents/sd-2027-1-docker-rangers`.
Se conserva el PRD de Pablo en `docs/PRD.md`. Hay ramas locales `main` y
`01-monolito`, además del avance `codex/02-separacion`. El monolito ya pasó la suite de compra, permisos, stock concurrente
e idempotencia. En `docs/PLAN-DEL-EQUIPO.md` están el mensaje para copiar al grupo
y una propuesta de trabajo; en `docs/GUIA-DEREK.md`, la explicación para estudiar.

El avance distribuido pasó su suite completa el 7 de noviembre: reparto de catálogo,
idempotencia concurrente, última unidad, compensación y recuperación tras reinicio.
Está guardado en un commit local; el PRD de Pablo se conserva.

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
- T04: comparación PostgreSQL, MongoDB y RabbitMQ, con fuentes primarias y simulación Lamport Java comprobada.
- S11: código, 29 pruebas unitarias y seis fases integrales aprobadas; PDF de 15 páginas revisado visualmente y evidencias completas. Cierre local el 8 de noviembre.
- Lecturas 2–10: [borradores de preguntas y hallazgos](lecturas/README.md), no publicados en Discussions.
- Examen: [25 respuestas de apoyo](examen-respuestas-borrador.md); falta revisión personal, manuscrito y escaneo.

Los borradores y resultados asistidos requieren revisión personal. No se presentan
experimentos como realizados por Derek en clase ni se inventan capturas u horarios.

T04, S11 y Lectura 10 vencieron el 7 de noviembre antes de las 07:00.
ATENCIÓN: CAMBIOS.md actualizado el 14 de noviembre indica que T04 y S11
cerraron su recepción tardía ese día a las 07:00. Aunque están completos localmente,
no se puede asegurar aceptación posterior; requiere aclaración del profesor. El examen vence el
21 de noviembre antes de las 06:59 y el proyecto final se presenta el 28.

## Qué falta para que cuente como entrega

Los archivos están guardados localmente; todavía no se han subido los nuevos labs ni las tareas a GitHub. No hay PR, conforme a las reglas del curso. Revisar y practicar las explicaciones, y después subir solo la carpeta de Derek a su rama. El estado «tarde» corresponde al momento real del push, no a la fecha del archivo local.

En el proyecto del equipo falta publicar el repositorio e invitar al equipo/profesor, además de acordar aportaciones reales. Además del monolito hay una versión distribuida con Usuarios, Inventario y Pedidos;
la revisión de equipo y defensa personal siguen pendientes.

Este repaso no confirma la entrega del examen a mano ni los comentarios de lecturas en Discussions.

## Repaso antes de entregar

- T01: señala `logica.consultar` y explica cómo REST y gRPC traducen el mismo resultado o error. La comparación de tamaños solo mide cuerpos, no tráfico total.
- T02: explica por qué Zab supone caídas y PBFT admite réplicas que mienten; calcula el quórum para 3 votantes y las réplicas para tolerar una bizantina.
- T03: recorre una consulta por mongos y explica por qué un producto popular sigue siendo caliente aunque su llave tenga hash.
- S07: distingue directorio, gateway y balanceador. Explica por qué pueden seguir apareciendo errores después de apagar una copia.
- S08: explica cuándo devolver y cuándo conciliar; por qué una saga no ofrece aislamiento global y por qué no reintentamos abonos sin idempotencia.
- T04: distingue reloj lógico, reloj físico y posición de un registro.
- S11: distingue fuente de verdad SQL, vista Redis y avisos Mongo; explica la invalidación posterior al commit.
- S09: dibuja guardar, commit y ack. Explica qué ocurre si el consumidor cae entre commit y ack y qué hueco deja no implementar Outbox.

Fuentes de organización: [guías de labs](https://github.com/OscarRuiz21/sd-2027-1/blob/main/labs/README.md), [tareas](https://github.com/OscarRuiz21/sd-2027-1/blob/main/entregas/README.md) y [política de entregas](https://github.com/OscarRuiz21/sd-2027-1/blob/main/ENTREGAS.md).

## Prioridades verificadas el 20 de noviembre

Se consultó origin/main en 0af68ee. No hay nuevos labs obligatorios; S12 y S13
son material de estudio. OpenTelemetry/Jaeger es una práctica opcional.

1. Examen: revisar las 25 respuestas, escribirlas a mano, escanearlas en un solo
   PDF y subirlo antes del 21 de noviembre a las 06:59. Destino:
   `entregas/santana_derek/examen/examen-escrito.pdf`. El borrador no sustituye esto.
2. Entrevista del 21: cuatro preguntas, posiblemente reformuladas, cámara
   encendida y salas por equipo. Practicar razones, alternativas y fallas.
3. Publicar las entregas locales cuando haya acceso; no confundir commit con push.
   T04/S11 ya tienen cierre explícito; consultar su aceptación sin asumir puntos.
4. Proyecto: antes del 28 a las 07:00, código y `docs/reporte-final.pdf` en repo
   de equipo. PDF: portada, problema, diagramas funcional y de arquitectura,
   tres decisiones con alternativas descartadas, fallas, ejecución, pendientes
   y aportaciones reales. Pitch el 28 con participación de todos.
5. Lecturas: borradores pendientes de revisión personal y publicación autorizada.

Fuente actualizada: [avisos oficiales](https://github.com/OscarRuiz21/sd-2027-1/blob/main/CAMBIOS.md)
y [rúbrica](https://github.com/OscarRuiz21/sd-2027-1/blob/main/proyecto/RUBRICA.md).
