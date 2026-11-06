# Reloj de Lamport en Java

Simulación de **3 servidores** (A, B y C) que se mandan **5 mensajes**. Cada servidor lleva su propio reloj lógico en un `AtomicLong`.

## Reglas del reloj

| Evento | Qué hace el reloj |
|---|---|
| Evento local | `reloj = reloj + 1` |
| Envío | `reloj = reloj + 1`, y el mensaje lleva ese valor |
| Recepción | `reloj = max(reloj local, reloj recibido) + 1` |

## Cómo ejecutarlo

```bash
javac Lamport.java
java Lamport | tee salida.txt
```

## Salida

```text
[C] reloj=1 | evento local: arranca
[A] reloj=1 | evento local: inicia
[A] reloj=2 | ENVIA 'm1' a B
[B] reloj=3 | RECIBE 'm1' de A (traia 2)
[B] reloj=4 | evento local: procesa m1
[B] reloj=5 | ENVIA 'm2' a C
[C] reloj=6 | RECIBE 'm2' de B (traia 5)
[C] reloj=7 | ENVIA 'm3' a A
[A] reloj=8 | RECIBE 'm3' de C (traia 7)
[A] reloj=9 | ENVIA 'm4' a B
[B] reloj=10 | RECIBE 'm4' de A (traia 9)
[B] reloj=11 | ENVIA 'm5' a C
[C] reloj=12 | RECIBE 'm5' de B (traia 11)
```

## Por qué la salida respeta la causalidad

Si un evento pudo influir en otro (*happened-before*), su reloj es menor. Aquí se ve por dos razones:

1. **Dentro de un servidor el reloj solo crece.** Por ejemplo, B pasa por 3, 4, 5, 10 y 11, sin retroceder nunca.
2. **Cada recepción tiene un reloj mayor que su envío**, porque se usa `max(local, recibido) + 1`:

| Mensaje | Envío | Recepción |
|---|---|---|
| m1 (A → B) | 2 | 3 |
| m2 (B → C) | 5 | 6 |
| m3 (C → A) | 7 | 8 |
| m4 (A → B) | 9 | 10 |
| m5 (B → C) | 11 | 12 |

Un caso interesante: cuando B recibe m4, su reloj local era 5 pero el mensaje traía 9. Por eso salta a 10: **adopta el reloj más adelantado** y así no puede "recibir en el pasado".

## Lo contrario no se cumple

- `[C] arranca` y `[A] inicia` tienen ambos reloj = 1. Son **eventos concurrentes**: ninguno causó al otro.
- Un reloj menor **no implica** causalidad: el evento 1 de C es menor que el 2 de A, pero no hay relación causal entre ellos.

Para detectar concurrencia hacen falta **relojes vectoriales**.