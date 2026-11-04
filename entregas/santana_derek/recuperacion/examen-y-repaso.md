# Examen y repaso pendiente

Consulta: 3 de noviembre de 2026, main `d4313102df9f26a14bd668f8f67a0f550e4fa4d1`.
El anuncio de CAMBIOS.md del 31 de octubre dice que el examen escrito en casa y
a mano se publicaría esa semana. No se encontró enunciado ni fecha de entrega
en el árbol de main, las coincidencias de los Markdown, la lista pública de
Discussions ni la búsqueda de Issues por «examen». No se afirma que no exista
en otro canal. El README conserva fechas antiguas; se usa el anuncio más reciente.

- 7 de noviembre: blockchain/telemetría; antes de las 07:00, T04, S11 y Lectura 10.
- 14 de noviembre: repaso.
- 21 de noviembre: entrevista técnica.
- 28 de noviembre: proyecto final, pitch, diagrama funcional y código.

No hay examen manuscrito producido ni entregado por este trabajo. Lo siguiente
es una guía de repaso asistida para el 4 de noviembre, no respuestas a un examen
cuyo enunciado no está disponible.

## Recorrido de 60 minutos

1. 10 min: T01–T04. Localiza la lógica compartida, compara Zab/PBFT, dibuja
   mongos→shard y distingue tiempo físico, lógico y posiciones de secuencia.
2. 15 min: S07–S09. Dibuja gateway, directorio y balanceador. Sigue el cargo,
   el abono y la compensación. Explica guardar→commit→ack y la ventana sin Outbox.
3. 15 min: S11. Identifica la fuente de verdad del saldo, cache-aside y TTL.
   Explica por qué una caché vieja nunca debe autorizar un cargo, y cómo el `_id`
   evento:clabe impide duplicados aunque el consumidor caiga a la mitad.
4. 15 min: tienda. Sigue una compra, encuentra la llave de idempotencia y explica
   qué pasa si Inventario confirma pero Pedidos no recibe respuesta.
5. 5 min: escribe dos dudas propias para la entrevista y revisa los borradores
   de lecturas; no publiques un texto que no puedas explicar.

## Preguntas de comprobación

- ¿Un timeout demuestra que no se aplicó la operación? No: pudo perderse la respuesta.
- ¿Un gateway aporta atomicidad entre servicios? No: enruta solicitudes.
- ¿La saga ofrece aislamiento global? No: expone estados intermedios y compensa.
- ¿Ack después del commit elimina toda repetición? No: permite redelivery segura con idempotencia.
- ¿Outbox hace exactamente una publicación? No: conserva la intención; el relevo puede repetir.
- ¿Un reloj Lamport menor demuestra causalidad? No: solo vale la implicación en sentido causal→reloj.
- ¿Dos réplicas de aplicación son dos réplicas de base? No: pueden compartir un único motor.
- ¿Una consulta rápida demuestra más capacidad? No: medir latencia y carga sobre la base por separado.

Fuentes de fechas: [CAMBIOS.md](https://github.com/OscarRuiz21/sd-2027-1/blob/main/CAMBIOS.md).
