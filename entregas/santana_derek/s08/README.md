# S08 · Saga y circuit breaker

Base docente: rama `03-gateway-discovery-balanceo` de
[Mexi Banco](https://github.com/OscarRuiz21/mexi-banco/tree/03-gateway-discovery-balanceo).
Implementación asistida de Derek: estados persistentes, devolución explícita,
registro de cargo y devolución, y circuito manual alrededor de CuentaCliente.

La entrega contiene el servicio `transferencia/` completo, sus pruebas y el compose.
Se copia sobre un clon de la rama base para obtener los demás servicios. No se
incluye `target/`. Los Dockerfiles usan una caché Maven compartida: construir
los servicios **uno por uno**, evitando builds simultáneos que esperen esa caché.

## Estados y decisiones

`INICIADA -> CARGADA -> ABONADA -> COMPLETADA`.
Si el abono falla antes de aplicarse: `CARGADA -> COMPENSANDO -> COMPENSADA`.
Si la devolución también falla: `COMPENSACION_PENDIENTE`.
Si se devolvió el dinero pero falló su asiento: `COMPENSADA_REGISTRO_PENDIENTE`;
no se vuelve a devolver para arreglar un error de registro.

Una respuesta perdida no prueba que un cargo o abono no ocurrió. Para resultados
ambiguos se guarda `CARGO_INCIERTO` o `ABONO_INCIERTO` y se exige conciliación.
No se compensa un abono que pudo haberse aplicado sin comprobarlo primero.

El circuito manual abre tras tres fallas de infraestructura, espera diez segundos
y permite una sola sonda. Un 404/422 no cuenta como caída. Hay pruebas de apertura,
rechazo sin llamar al servicio y recuperación. Los timeouts de conexión/lectura
provienen del cliente base (2 s y 5 s).

La configuración amplía a 40 s la espera del gateway para observar la pausa de
20 s; los timeouts de las llamadas a cuenta conservan sus valores propios. En
el primer ensayo, con la puerta base de 15 s, se observó HTTP 504.

## Fallas reproducibles

- `FALLAR_ABONO_A_CLABE=<destino> docker compose up -d transferencia` provoca
  la falla antes de enviar el abono a esa CLABE.
- `PAUSA_ENTRE_CARGO_Y_ABONO_MS=20000 docker compose up -d transferencia`
  permite apagar cuenta durante la pausa y observar el estado pendiente.
- Apagar cuenta y llamar repetidamente a transferencias muestra el circuito.
  Al volver cuenta, esperar su salud y el registro antes de probar la recuperación.

## Límite de la compensación

El enunciado permite dejar la compensación pendiente y explicar cómo terminarla.
Esta versión **no reintenta devoluciones automáticamente**: cuenta en la base S07
no admite una llave de operación idempotente. Antes de automatizarlo, debe guardar
una clave como `transferencia-N-devolucion` en la misma transacción que el abono,
protegerla con UNIQUE y devolver el resultado previo en un replay. Solo después
se agregaría un trabajador con espera creciente. Un estado persistente no hace
atómica la llamada remota con el guardado local; una caída entre ambas también
requiere conciliación. La saga tampoco ofrece aislamiento entre servicios.

Los experimentos, sus horarios y resultados reales se conservan en `evidencias/`.
La revisión personal de los textos y la explicación oral siguen siendo de Derek.
