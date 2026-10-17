# Cambios para el grupo

Lo que cambia para el alumno, con fecha. Lo más nuevo arriba.

## 2026-10-10

- **Decks publicados**: S06 (*Copias, acuerdo y orquestación*, HTML y PDF), S07 (*Una puerta, un directorio y el reparto*, HTML y PDF) y S08 (*Transacciones: del COMMIT local a la Saga*, PDF), en `material/` y en el índice.
- **Lab S07, plazo ampliado otra vez**: la bitácora se entrega hasta el **sábado 17 de octubre, antes de las 07:00**, porque construir el gateway colgaba algunas máquinas.
- **Lab S08 · saga orquestada y circuit breaker**: el enunciado está en `labs/s08-saga-circuit-breaker/README.md`. Sin guía paso a paso. Base: la rama `03-gateway-discovery-balanceo` de mexi-banco, que ya es pública (la solución del lab S07). Se trabaja en equipo, pero cada quien entrega su código y su `bitacora-s08.pdf` en `entregas/apellido_nombre/s08/`, hasta el **sábado 17 de octubre, antes de las 07:00**.
- **Proyecto · repo del equipo con su monolito**: un repo por equipo (`sd-2027-1-nombre-del-equipo`) con `main` (README y `docs/PRD.md`) y la rama `01-monolito`; OscarRuiz21 como colaborador y la liga en la Discussion #44. Hasta el **sábado 17 de octubre, antes de las 07:00**. Detalle en `proyecto/README.md`.
- **Lectura 7** para la S09: Kreps, *The Log* (partes 1 y 2) y Richardson, *Transactional outbox*, en la [Discussion #49](https://github.com/OscarRuiz21/sd-2027-1/discussions/49); en tiempo hasta el sábado 17 a las 06:59.
- **S09 (17 de octubre)**: mensajería. Brokers, el patrón Outbox (que no alcanzó el sábado) y la saga por coreografía, ya con mensajes.

## 2026-09-19

- **Lab S05, más simple**: queda solo la guía `labs/s05-mexi-banco/Lab-S05-Mexi-Banco-Postman.html` con los pasos de la clase, y se entrega un reporte corto en PDF, hecho con cualquier procesador de textos, con observaciones y capturas. Ya no hay enunciado en Markdown ni rúbrica.
- **Labs ordenados**: cada lab vive ahora en su carpeta, en orden de sesión (`labs/s00-instala-docker/`, `labs/s02-docker-dia-1/`, `labs/s04-docker-dia-2/`, `labs/s05-mexi-banco/`). Las ligas viejas a `labs/<archivo>` ya no funcionan: entra desde [`labs/README.md`](labs/README.md) o desde el índice.
- **S05, material final**: el deck `material/S05/` (HTML y PDF) es ahora el de 52 láminas que se usó en clase. Kubernetes (láminas 18 a 26) y el cierre de Raft (término, reparación del log y etcd) no alcanzaron y se ven en la S06.
- **Lab S05**: Mexi Banco en la etiqueta `v05.1`, con una guía visual en Postman (`labs/s05-mexi-banco/Lab-S05-Mexi-Banco-Postman.html`) y su colección. **La entrega se mueve al domingo 27 de septiembre, 23:59**, porque en clase el lab se hizo como demostración.
- **T02 · dos algoritmos de consenso que no sean Raft ni Paxos**: un resumen de cómo funciona cada uno; el enunciado está en `entregas/README.md`; va en `entregas/apellido_nombre/tareas/t02/`, en tiempo hasta el domingo 27 de septiembre, 23:59.
- **Lectura 5** para la S06: Brewer, *CAP Twelve Years Later* (2012), en la [Discussion #40](https://github.com/OscarRuiz21/sd-2027-1/discussions/40); en tiempo hasta el sábado 26 a las 06:59.
- **Video para repasar Raft**: https://youtu.be/IujMVjKvWP4
- **S06 (26 de septiembre)**: abre con Kubernetes y el cierre de Raft, sigue con CAP, y en el lab Mexi Banco se parte en tres servicios. Los modelos de consistencia (linealizable, causal y eventual) pasan a la S09.

## 2026-09-15

- **S05 (sábado 19 de septiembre)**: deck `material/S05/` (HTML y PDF) y laboratorio `labs/s05-mexi-banco/Lab-S05-Mexi-Banco-Compose.md` con su rúbrica. Tema: de Compose a orquestación, con una introducción a consenso (latido, elección, quórum). Kubernetes se ve como demo del profesor; el lab es Docker Compose sobre Mexi Banco.
- **Mexi Banco ya es código**: repositorio público https://github.com/OscarRuiz21/mexi-banco, etiqueta `v05`. Antes del sábado: `git clone --branch v05 https://github.com/OscarRuiz21/mexi-banco.git` y `docker pull postgres:17-alpine eclipse-temurin:21-jdk-alpine eclipse-temurin:21-jre-alpine` (una imagen por comando).
- **Entrega del lab S05**: push a su rama `entregas_apellido_nombre`, carpeta `entregas/apellido_nombre/s05/`, sin PR. En tiempo hasta el domingo 20 de septiembre a las 23:59; tarde sin penalización hasta antes de la S06.
- **S06 (26 de septiembre)**: Raft a fondo, consistencia y CAP en la teoría; en el lab, Mexi Banco se parte en tres servicios y aparecen gateway y discovery. Lectura 5: Brewer, *CAP Twelve Years Later* (2012); la Discussion se abre el jueves.
- **Lectura 4 (Raft §1 a §5)** sigue vigente para la S05: 2 preguntas y 1 hallazgo en la Discussion, en tiempo hasta el sábado 06:59.

