# Rúbrica · Lab 05 · Mexi Banco con Compose

Lab de operar y observar, no de escribir código: no hay `base/`, `tests/` ni `solucion/` como en
un lab de implementación: la evidencia es la captura y la explicación, no un repo que compile.

| Criterio | Puntos | Completo | Parcial | Cero |
|---|---|---|---|---|
| Sistema levantado y respondiendo | 20 | Captura de `docker compose ps` con ambos servicios `healthy` | Solo `running`, o solo un servicio | No hay captura o no levantó |
| Transferencia interna correcta | 15 | Dos cuentas, saldos cuadran tras la transferencia | Transferencia hecha pero saldos no verificados | No la hizo |
| Comprobó "lo que Compose no hace" | 20 | Mató `app`, confirmó que no revivió sola, la volvió a levantar sin perder datos | Mató `app` pero no verificó la persistencia al revivirla | No lo intentó |
| Idempotencia del SPEI | 20 | Mandó la misma `Idempotency-Key` dos veces y muestra que no se duplicó el cobro | Lo intentó pero la evidencia no muestra claramente el resultado | No lo intentó |
| Explica la decisión (preguntas de cierre) | 25 | Las tres respuestas muestran que entendió el porqué, no solo el qué pasó | Respuestas correctas pero superficiales | No entregó las preguntas |

Aprobado con 70/100. La pregunta 2 de cierre (qué seguiría sin resolver aunque agreguen
`restart: always`) es la que más separa a quien entendió del que solo copió el comando.
