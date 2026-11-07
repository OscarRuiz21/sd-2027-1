# Examen y repaso pendiente

Actualización: 7 de noviembre de 2026. El examen ya está publicado en main.
Vence el **21 de noviembre de 2026 antes de las 06:59**. Requiere diez preguntas
fijas y quince elegidas de al menos cinco temas, todas escritas a mano, escaneadas
en un solo PDF y enviadas por push. La entrevista individual es ese mismo día.

Se preparó un [borrador de las 25 respuestas](examen-respuestas-borrador.md),
con asistencia de Codex, para revisar y estudiar. No es el manuscrito ni se presenta
como trabajo escrito personalmente por Derek. Falta su revisión, escritura y escaneo.

- 7 de noviembre: T04, S11 y Lectura 10; fecha original antes de las 07:00.
- 14 de noviembre: repaso.
- 21 de noviembre: examen escrito e entrevista técnica.
- 28 de noviembre: proyecto final, pitch, diagrama funcional y código.

Fuente: [enunciado oficial](https://github.com/OscarRuiz21/sd-2027-1/blob/main/examen-escrito/README.md).
El anuncio del 4 de noviembre permite registrar laboratorios y tareas completos
fuera de plazo como «tarde», sin penalización de puntos.

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
