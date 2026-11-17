# Lab S08 · Que la transferencia no deje dinero en el aire

**Sistemas Distribuidos 2027-1 · Grupo 2 · S08 · sábado 10 de octubre**
**Entrega: sábado 17 de octubre de 2026, antes de las 07:00** (antes de la clase de la S09)

En la clase vimos por qué el `@Transactional` del monolito ya no alcanza cuando la operación cruza
servicios, y dos salidas: 2PC y la **saga**. Esta semana la programas. **No hay guía paso a paso**:
aquí está lo que debe pasar al final; cómo lograrlo lo investigas tú, con tu equipo, la lectura de
la semana (Fowler y Richardson) y el deck de la clase.

> En una línea: la transferencia de Mexi Banco se vuelve una **saga orquestada**: si el abono falla
> después del cargo, el sistema **devuelve el dinero** a la cuenta de origen y lo deja escrito. Las
> llamadas a `cuenta` pasan por un **circuit breaker**.

La **coreografía** (con mensajes y un broker) no se programa esta semana: la vemos en la S09.

## El punto de partida

La solución del lab S07 ya está publicada en la rama `03-gateway-discovery-balanceo`:

```bash
git clone --branch 03-gateway-discovery-balanceo https://github.com/OscarRuiz21/mexi-banco.git mexi-banco-s08
cd mexi-banco-s08
docker compose up --build -d
docker compose ps        # espera a que todos digan healthy
```

Si ya tienes el repo clonado, basta con `git fetch origin` y `git switch 03-gateway-discovery-balanceo`.

Abre `transferencia/src/main/java/mx/mexibanco/transferencia/TransferenciaService.java`. El método
`transferir()` hace cuatro saltos por HTTP: consulta las dos cuentas, **carga** al origen, **abona**
al destino, registra los movimientos y avisa. Cada salto se confirma solo. El comentario lo dice:
*si el cargo sale bien y el abono falla, el cargo NO se deshace*. Ese es el problema que vas a
resolver.

## Parte 0 · Ver el problema antes de arreglarlo

Antes de tocar código, reprodúcelo y déjalo en tu bitácora:

1. Consulta el saldo de dos cuentas por el gateway (`http://localhost:8080/api/...`).
2. Levanta `transferencia` con una pausa entre el cargo y el abono:
   `PAUSA_ENTRE_CARGO_Y_ABONO_MS=20000 docker compose up -d transferencia`.
3. Lanza una transferencia y, durante esos 20 segundos, apaga `cuenta` (`docker compose stop cuenta`).
4. Vuelve a encender `cuenta` y consulta los saldos: el dinero salió del origen y no llegó al destino.

## Parte 1 · La saga orquestada (obligatoria)

`TransferenciaService` es el **orquestador**: él decide qué paso sigue y qué hacer si uno falla.

Al terminar, tu versión debe cumplir esto:

1. **Cada transferencia tiene un estado guardado** en su tabla, y se actualiza en cada paso. Por
   ejemplo: `INICIADA` → `CARGADA` → `ABONADA` → `COMPLETADA`, o `CARGADA` → `COMPENSADA` cuando
   algo falla. Los nombres los eliges tú; lo que importa es que, si el servicio se cae a la mitad,
   la base diga en qué paso se quedó.
2. **Si el abono falla después del cargo, se compensa**: se le devuelve el monto a la cuenta de
   origen, queda registrado como movimiento y la transferencia termina en un estado que lo diga.
   La respuesta al cliente explica qué pasó (no un 500 sin más).
3. **Una forma de provocar la falla del abono sin apagar todo**, para probar la compensación con
   `cuenta` arriba. Por ejemplo, una variable de entorno que haga fallar el abono hacia cierta
   CLABE. Documenta cuál usaste.
4. **Qué pasa si la compensación también falla** (repite la Parte 0, con `cuenta` apagada durante
   la pausa): la transferencia no se puede quedar en `CARGADA` para siempre. Como mínimo, debe
   quedar en un estado tipo `COMPENSACION_PENDIENTE`. En tu bitácora explica cómo la terminarías
   (por ejemplo, un reintento programado) y por qué **la compensación debe ser idempotente**.

## Parte 2 · Circuit breaker en las llamadas a cuenta

Si `cuenta` está caída o lenta, `transferencia` no debe seguir golpeándola ni quedarse colgada.
Pon un **circuit breaker** en las llamadas de `transferencia` a `cuenta`:

- Con **Resilience4j** (`spring-cloud-starter-circuitbreaker-resilience4j`; la versión ya la fija
  el BOM de Spring Cloud del `pom.xml`), o
- **A mano**: una clase con los tres estados, un contador de fallas y un tiempo de espera. **Hacerlo
  a mano suma puntos.**

Lo que debe verse en tu bitácora: los **tres estados** (cerrado, abierto y semiabierto) y cómo
cambian. Apagas `cuenta`, mandas varias transferencias, ves que el circuito se abre y que ya
responde rápido sin llamar a `cuenta`; la enciendes y ves que pasa a semiabierto y luego a cerrado.
Los logs o el endpoint `/actuator/circuitbreakers` (si usas Resilience4j) sirven de evidencia.

Opcional, suma puntos: un **timeout** explícito en las llamadas a `cuenta` y un **reintento con
espera creciente** en la compensación. Recuerda la regla de la clase: **reintentar sin
idempotencia cobra dos veces**.

## Cómo se trabaja

Con tu **equipo del proyecto**: se apoyan, se reparten la investigación y se ayudan a depurar.
Pero **cada quien entrega lo suyo**: su código y su bitácora.

## Qué entregas

En tu rama `entregas_apellido_nombre` del repo del grupo:

```
entregas/apellido_nombre/s08/
├── bitacora-s08.pdf
└── codigo/
    ├── transferencia/        ← la carpeta completa del servicio, SIN target/
    └── docker-compose.yml    ← solo si lo cambiaste
```

**La bitácora** (en Word o el procesador que quieras, exportada a PDF), con capturas en cada parte:

1. **El problema**: los saldos antes y después de la Parte 0, y en una frase por qué pasó.
2. **Tu saga**: los pasos y la compensación de cada uno, con un **diagrama de estados** de la
   transferencia (a mano y fotografiado también vale).
3. **El código clave**: los fragmentos que cambiaste, explicados con tus palabras.
4. **La compensación funcionando**: la falla provocada, los saldos que regresan y el estado final
   en la base.
5. **Cuando la compensación también falla**: qué estado queda y cómo lo terminarías.
6. **El circuit breaker**: los tres estados, con evidencia.
7. **Lo que la saga no te da**: un párrafo sobre el aislamiento (¿qué ve alguien que consulta el
   saldo de origen entre el cargo y la compensación?).

```bash
git checkout entregas_apellido_nombre
git pull origin main
mkdir -p entregas/apellido_nombre/s08/codigo
# ... copia transferencia/ (sin target/) y tu bitacora-s08.pdf ...
git add entregas/apellido_nombre/s08
git commit -m "Lab S08: saga orquestada y circuit breaker"
git push
```

**No abras pull request**: el push ES la entrega. Tarde no baja puntos, pero queda registrado en tu
tendencia (ver [`ENTREGAS.md`](../../ENTREGAS.md)).

## Si te atoras

- Construir todas las imágenes tarda: si solo cambias `transferencia`, reconstruye solo ese
  servicio con `docker compose up --build -d transferencia`.
- Si tu máquina no aguanta las tres réplicas de `cuenta`, bájalas a una:
  `docker compose up -d --scale cuenta=1`.
- Dudas o errores: abre un Issue en el repo del grupo con el error completo y el comando que
  corriste.
