# PRD – Plataforma POS distribuida para cadena de tiendas de conveniencia

Oct 9, 2026 · Equipo Pumaverso Distribuido

## Resumen

Cada sucursal opera como nodo autónomo: vende sin conexión y sincroniza con central al reconectar, sin perder ni duplicar transacciones. La V1 se valida en un piloto de 20 sucursales, con 4 desarrolladores, en 4 meses.

El sistema cubre usuarios, punto de venta (POS), inventario por sucursal, pagos de servicios y recargas, y proveedores. El riesgo central del negocio no es la caída técnica, sino la venta perdida por inventario incorrecto.

## Principios de diseño

1. **Local-first.** El POS escribe siempre primero en el nodo y sincroniza en segundo plano, haya red o no. No existen "modo online" y "modo offline" para el cajero.
2. **Append-only.** El nodo nunca reescribe su historia. Toda corrección es un evento nuevo de ajuste, con motivo y aprobador.
3. **Solo se entrega lo que se puede cumplir localmente.** Sin red, el nodo ejecuta únicamente transacciones que se completan con recursos propios.
4. **Ante la duda, a favor de la venta.** Cuando el sistema sospecha un error de inventario, actúa para no dejar el anaquel vacío; el conteo físico corrige después.
5. **Cada dato tiene un solo dueño de la verdad** y una vigencia máxima declarada.

## Requisitos críticos

Regla única offline: se permite solo lo que se entrega completo en el mostrador con recursos locales. Lo que sería una promesa a cumplir por un tercero requiere confirmación en tiempo real.

| Operación | Offline | Razón |
| --- | --- | --- |
| Venta de productos | Permitida | Producto físico + precio en caché vigente |
| Recepción de proveedor | Permitida | Mercancía física; la deuda la promete la cadena, que controla su sistema |
| Recarga de celular | Bloqueada | Promesa de saldo que depende del operador telefónico |
| Pago de servicios (luz, agua) | Bloqueada | Requiere confirmación del emisor |
| Pago con tarjeta | Bloqueado | Requiere autorización bancaria |

Costo aceptado: las recargas bloqueadas offline reducen tráfico de clientes. Se mide como métrica explícita (ver Métricas).

## Casos de falla y garantías del nodo

El nodo deja de operar una función cuando el dato que la sostiene caduca, no cuando pierde la red.

**Vigencia máxima por tipo de dato (TTL)**

| Dato | Vigencia offline | Al caducar |
| --- | --- | --- |
| Credenciales de usuario | 24 h | El usuario no puede iniciar sesión |
| Precios y promociones | 72 h | Se bloquea la venta del SKU afectado |
| Costos | 30 días | Se marca para revisión, no bloquea venta |
| Alertas sanitarias y ley seca | Sin canal offline en V1 | Proceso manual (ver Alcance) |

Si se vende con precio desactualizado dentro del TTL, la diferencia la absorbe la cadena, nunca el cliente.

**Punto de commit.** La venta existe para el sistema en el instante en que se registra el cobro en el log local, antes de imprimir el ticket. Un apagón antes de ese punto deja la venta inexistente; después, la venta queda completa. Si al reiniciar hay una venta sin cobro confirmado, su resolución genera un evento auditado con el usuario que decidió.

**Reloj.** La identidad de cada transacción no depende del reloj: es `sucursal + caja + consecutivo`. El reloj solo alimenta reportes. Si marca una fecha anterior al último sync, el nodo bloquea ventas hasta corregirlo.

## Consistencia de datos

Cada tipo de dato tiene un solo dueño; los conflictos se resuelven con eventos de ajuste, nunca borrando historia.

| Dato | Dueño de la verdad | Rol del otro lado |
| --- | --- | --- |
| Ventas | Nodo | Central consolida, no puede borrar ni modificar |
| Inventario físico | Nodo | Central propone movimientos y concilia |
| Traspasos entre sucursales | Ambos nodos | Central ordena; salida y entrada las confirma cada sucursal |
| Usuarios y permisos | Central | El nodo aplica con TTL de 24 h |
| Precios y costos | Central | El nodo aplica con TTL por tipo |

**Traspasos.** Un traspaso son dos eventos: salida confirmada por la sucursal origen y entrada confirmada por la destino. Si la origen nunca confirmó la salida, no hay pérdida que ajustar.

**Usuario dado de baja operando offline.** Ventana máxima de exposición: 24 h. Toda transacción de un usuario revocado entra a una cola de auditoría al sincronizar.

## Validación de stock

El POS nunca bloquea una venta por stock: si el producto está en el mostrador, se vende.

**Stock negativo.** Cada venta que deja un SKU en negativo genera un evento marcado automáticamente, sin pedir motivo al cajero. El supervisor determina la causa después. Más de 2 eventos en 24 h en la misma caja disparan alerta.

**Inventario fantasma** (el sistema reporta stock que no está en el anaquel). Se detecta cuando las ventas reales de un SKU caen muy por debajo de su ritmo esperado en esa sucursal y franja horaria. El umbral es por SKU y sucursal, no un número fijo de días.

Al detectar sospecha de fantasma:

1. El sistema asume stock bajo y lo incluye en la sugerencia de pedido.
2. Notifica "conteo físico requerido" al gerente.
3. Si no hay conteo en el plazo definido, escala al supervisor regional.

En V1 no hay resurtido automático; la detección alimenta el reporte que usa el gerente para pedir.

## Sincronización

Cada nodo mantiene un solo registro de eventos con un solo consecutivo; central lo ingiere de forma idempotente.

**Dos canales independientes, en paralelo:**

- **Bajada (central → nodo):** credenciales, bajas de personal, precios, costos. Prioridad: credenciales primero.
- **Subida (nodo → central):** un único flujo de eventos tipados: venta, cancelación, recepción de proveedor, merma, ajuste, traspaso.

**Identidad e idempotencia.** Cada evento lleva `sucursal + caja + consecutivo + hash de contenido`. Central registra el último consecutivo recibido por caja:

- Consecutivo ya recibido → reintento, se descarta.
- Hueco en la secuencia → se guardan los recibidos y se solicita el faltante.
- Una cancelación es un evento propio; nunca deja un hueco.

**Protección contra avalancha.** Límite de tasa por sucursal, reintentos con backoff exponencial más jitter, y una cola intermedia antes de la base de datos. V1 usa Redis Streams o PostgreSQL; Kafka se evalúa al escalar.

**Recepciones de proveedor** viajan en el mismo flujo y generan la cuenta por pagar en central al ingerirse.

## Arquitectura de microservicios

Los microservicios viven en central; cada sucursal es un monolito modular con una sola base de datos local.

**Por qué el nodo no usa microservicios.** Una venta toca POS, inventario y usuarios en una sola transacción. Separarlos dentro de la tienda rompería la atomicidad del punto de commit y multiplicaría las fallas offline. Dentro del nodo, los dominios son módulos del mismo proceso que escriben al mismo registro de eventos.

**Servicios en central**

| Servicio | Responsabilidad | Datos que posee | Comunicación |
| --- | --- | --- | --- |
| Sincronización | Recibe eventos de los nodos, valida idempotencia y huecos, publica al stream | Último consecutivo por caja | API HTTPS hacia nodos; publica eventos |
| Usuarios | Altas, bajas, permisos, emisión de credenciales con TTL de 24 h | Usuarios y roles | API de bajada hacia nodos |
| Catálogo y precios | Productos, precios, promociones, costos con su vigencia | Catálogo | API de bajada hacia nodos |
| Inventario | Consolida existencias, concilia traspasos, detecta inventario fantasma | Existencias por sucursal | Consume eventos del stream |
| Proveedores | Recepciones y cuentas por pagar | Proveedores y deudas | Consume eventos de recepción |
| Auditoría | Cola de eventos marcados: negativos, usuarios revocados, ventas sin cobro | Casos de auditoría | Consume eventos del stream |
| Pagos y recargas | Integración con operadores y emisores; solo online | Transacciones de pago | Fuera de V1 (ver preguntas abiertas) |

**Reglas de comunicación**

- Entre servicios de central, solo eventos asíncronos por el stream (Redis Streams en V1). Ningún servicio llama a otro de forma síncrona para registrar una venta.
- Cada servicio es dueño de sus datos. En V1 comparten una instancia de PostgreSQL, pero cada uno con su propio esquema.
- El nodo solo habla con dos puertas: Sincronización (subida) y una API de bajada para credenciales y precios.

**Ajuste al equipo.** Con 4 desarrolladores, V1 despliega Sincronización, Usuarios, Catálogo e Inventario. Proveedores y Auditoría pueden arrancar como módulos dentro de Inventario y separarse cuando el volumen lo justifique.

## Lenguajes y tecnologías

V1 usa dos lenguajes, Python y TypeScript, más SQL: el equipo prioriza lenguajes que ya domina para no arriesgar el plazo de 4 meses.

| Componente | Lenguaje / tecnología | Razón |
| --- | --- | --- |
| Servicio local del nodo | Python | Lenguaje que el equipo ya conoce; se empaqueta como ejecutable con PyInstaller para instalarlo en cada tienda sin instalar Python aparte |
| Base de datos del nodo | SQLite | Transacciones atómicas en disco: garantiza el punto de commit ante apagones; viene incluida en Python y no requiere servidor |
| Interfaz de caja | TypeScript + React, empaquetada con Tauri | Interfaz rápida de construir; Tauri pesa mucho menos que Electron en equipos de tienda |
| Microservicios de central | Python con FastAPI | Mismo lenguaje que el nodo; FastAPI es sencillo y valida automáticamente los datos que llegan |
| Base de datos central | PostgreSQL | Un esquema por servicio en V1 |
| Cola de eventos | Redis Streams | Suficiente para 20 sucursales; Kafka se evalúa al escalar |
| Detección de inventario fantasma | SQL programado + Python | En V1 basta con consultas sobre ventas históricas; si se requieren modelos estadísticos, Python ya está en el stack (pandas) |

**Contrato de eventos.** Los tipos de evento (venta, cancelación, recepción, merma, ajuste, traspaso) se definen una sola vez como modelos de Pydantic en un paquete compartido por el nodo y central. De esos modelos se genera un JSON Schema para obtener los tipos de TypeScript. Así el nodo y central nunca interpretan un evento distinto.

**Alternativa considerada.** Go ofrece mejor rendimiento y un binario único más fácil de distribuir. Se descartó porque el equipo no lo domina y aprenderlo pondría en riesgo el plazo. El volumen del piloto, 20 sucursales, está muy por debajo de los límites de Python con FastAPI.

## Métricas de éxito

La métrica principal es la venta perdida por inventario fantasma: un sistema técnicamente perfecto que deja anaqueles vacíos fracasa.

| Métrica | Para quién | Cómo se mide | Meta piloto |
| --- | --- | --- | --- |
| Venta perdida estimada por fantasma | Dirección | Venta esperada menos venta real en ventanas de sospecha | Por definir con línea base |
| Tickets con ajuste manual | Operaciones | Por sucursal por semana, en absoluto | Por definir |
| Tiempo por transacción | Cajero | Del primer escaneo al cobro registrado | Igual con o sin red |
| Minutos de POS inoperable | Cliente | Por sucursal por semana | Por definir |
| Ingreso no captado por recargas bloqueadas | Dirección | Recargas promedio por hora × horas offline | Solo observación |
| Ventana real de revocación de usuarios | Seguridad | Tiempo entre baja en central y bloqueo efectivo en nodo | ≤ 24 h |
| Eventos duplicados o perdidos en sync | Ingeniería | Conciliación de consecutivos por caja | 0 |

## Alcance V1

V1 prueba una sola cosa: que 20 sucursales vendan sin conexión y sincronicen sin perder ni duplicar eventos.

**Incluye**

- POS local-first con registro de eventos, consecutivo e idempotencia.
- Recepción de mercancía de proveedor como evento de inventario.
- Sync de subida y bajada con cola, límite de tasa, backoff y jitter.
- Credenciales con TTL de 24 h y cola de auditoría para eventos marcados.
- Reporte de ventas y sospechas de inventario fantasma para el gerente.

**No-objetivos**

| Fuera de V1 | Riesgo aceptado | Mitigación |
| --- | --- | --- |
| Recargas, servicios y tarjeta offline | Pérdida de tráfico en zonas con caídas frecuentes | Medir ingreso no captado |
| Canal secundario para alertas sanitarias y ley seca | Multa por vender producto restringido | Aviso manual al gerente por WhatsApp |
| Resurtido automático | Pedidos dependen del criterio del gerente | Reporte de sospechas de fantasma |
| Auditoría en tiempo real | Fraude detectado con retraso | Cola de auditoría al sincronizar |
| App de gerente | Gestión desde la caja | Ninguna en V1 |
| Multi-caja por sucursal | Solo aplica a tiendas de una caja | Elegir sucursales piloto acordes |

## Preguntas abiertas

- [ ] ¿Recargas, pagos de servicios y tarjeta funcionan **online** en V1, o quedan fuera por completo? Si la tarjeta usa una terminal bancaria independiente, debe declararse.
- [ ] ¿De dónde sale la estimación de 20% de tráfico perdido por recargas? Es hipótesis a validar en el piloto.
- [ ] ¿Cuántas sucursales candidatas al piloto tienen una sola caja?
- [ ] Plazo para conteo físico antes de escalar una sospecha de fantasma.
- [ ] Línea base de ventas por SKU para calcular el ritmo esperado: ¿cuántas semanas de historia hay disponibles?
- [ ] ¿Quién aprueba los eventos de ajuste: gerente de tienda o supervisor regional?
