/* S04 · escenas 06–11: modos de llamada, el trade-off y las seis piezas. */
const TCOLS = [230, 300, 300];

DECK.scenes.push({
  tag: '07 / Los cuatro modos', title: 'No siempre.', sub: 'Es pregunta y respuesta.',
  items: [
    { id: 'un', x: 600, y: 150, w: 430, t: 'Unario', m: 'una petición → una respuesta' },
    { id: 'ss', x: 1070, y: 150, w: 430, t: 'Streaming del servidor', m: 'una petición → muchas respuestas' },
    { id: 'cs', x: 600, y: 290, w: 430, t: 'Streaming del cliente', m: 'muchas peticiones → una respuesta' },
    { id: 'bd', x: 1070, y: 290, w: 430, t: 'Bidireccional', m: 'los dos hablan a la vez' },
    { id: 'ej', type: 'list', x: 600, y: 440, w: 900, head: 'Para qué sirve cada uno', rows: [['Unario', 'el noventa por ciento de las llamadas'], ['Del servidor', 'la posición del conductor, cada segundo'], ['Del cliente', 'subir un archivo por partes'], ['Bidireccional', 'chat, telemetría, juegos']] },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'Cuatro modos', v: 'HTTP/2 abre streams, así que la llamada deja de ser *solo pregunta y respuesta*.' }
  ],
  steps: [
    { show: ['un'], set: { un: 'ok' }, say: 'El unario es el clásico: *una petición, una respuesta*.' },
    { show: ['ss'], say: 'Con streaming del servidor, pides una vez y *te van llegando* respuestas.' },
    { show: ['cs'], say: 'Con streaming del cliente, tú mandas muchas y *él contesta al final*.' },
    { show: ['bd'], say: 'Y bidireccional: los dos hablan *al mismo tiempo*, por el mismo stream.' },
    { show: ['ej', 'tr'], say: 'Esto es lo que HTTP/1.1 no daba, y por eso *venía con REST limitado*.' }
  ],
  notes: [
    'Del diálogo: "HTTP/2 permite cuatro modos: unario, streaming del servidor, del cliente y bidireccional".',
    'Aterrizarlo en el sistema del curso: transferencias → cuentas es unario; la telemetría hacia observabilidad sería streaming.'
  ]
});

DECK.scenes.push({
  tag: '08 / Comprobación', title: '¿Cuál es cuál?', sub: 'Cuatro escenarios, cuatro modos.',
  items: [
    { id: 'sA', x: 600, y: 140, w: 440, t: 'A · El saldo de una cuenta', m: 'una consulta puntual' },
    { id: 'sB', x: 600, y: 270, w: 440, t: 'B · La ubicación del conductor', m: 'verla todo el tiempo, en vivo' },
    { id: 'sC', x: 600, y: 400, w: 440, t: 'C · Subir muchos fragmentos', m: 'y obtener un resultado al final' },
    { id: 'sD', x: 600, y: 530, w: 440, t: 'D · Un chat en tiempo real', m: 'los dos escriben cuando quieren' },
    { id: 'rA', type: 'chip', x: 1090, y: 170, w: 420, tone: 'green', text: 'unario' },
    { id: 'rB', type: 'chip', x: 1090, y: 300, w: 420, tone: 'green', text: 'streaming del servidor' },
    { id: 'rC', type: 'chip', x: 1090, y: 430, w: 420, tone: 'green', text: 'streaming del cliente' },
    { id: 'rD', type: 'chip', x: 1090, y: 560, w: 420, tone: 'green', text: 'bidireccional' },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'Cómo se decide', v: 'Cuenta los mensajes de cada lado: *uno o muchos*, y quién los manda.' }
  ],
  steps: [
    { show: ['sA', 'sB', 'sC', 'sD'], say: 'Cuatro escenarios. Digan en el chat *qué modo* usarían en cada uno.' },
    { show: ['rA'], set: { sA: 'ok' }, say: 'A, el saldo: una pregunta, una respuesta. Es *unario*.' },
    { show: ['rB'], set: { sB: 'ok' }, say: 'B, la ubicación: pides una vez y *te van llegando*. Streaming del servidor.' },
    { show: ['rC'], set: { sC: 'ok' }, say: 'C, los fragmentos: mandas muchos y llega *un resultado*. Streaming del cliente.' },
    { show: ['rD'], set: { sD: 'ok' }, say: 'D, el chat: los dos hablan cuando quieren. *Bidireccional*.' },
    { show: ['tr'], say: 'La regla para decidir: cuenta *cuántos mensajes* manda cada lado.' }
  ],
  notes: [
    'Cómo correrla: mostrar los cuatro escenarios, un minuto de votación en el chat y revelar uno por uno. Si alguien duda entre B y D, la pregunta que desempata es si el otro lado también manda mensajes.',
    'Aterrizaje en el sistema del curso: transferencias → cuentas es unario; la telemetría hacia observabilidad sería streaming del servidor; una carga de archivo por partes al almacén de objetos, streaming del cliente; el chat de soporte, bidireccional.',
    'Ojo con el reflejo de usar streaming para todo: el unario cubre el noventa por ciento de las llamadas y es el más fácil de reintentar y de depurar.'
  ]
});

DECK.scenes.push({
  tag: '09 / El trade-off', title: 'REST o gRPC.', sub: 'Mismo problema, precios distintos.',
  items: [
    { id: 'hd', type: 'trow', head: true, x: 620, y: 130, h: 46, cols: TCOLS, cells: ['', 'REST', 'gRPC'] },
    { id: 'r1', type: 'trow', x: 620, y: 179, cols: TCOLS, cells: ['Formato en el cable', 'JSON, texto', 'Protobuf, binario'] },
    { id: 'r2', type: 'trow', x: 620, y: 226, alt: true, cols: TCOLS, cells: ['Contrato', 'opcional (OpenAPI)', 'obligatorio (.proto)'] },
    { id: 'r3', type: 'trow', x: 620, y: 273, cols: TCOLS, cells: ['Transporte', 'HTTP/1.1 o superior', 'HTTP/2'] },
    { id: 'r4', type: 'trow', x: 620, y: 320, alt: true, cols: TCOLS, cells: ['Streaming', 'limitado', 'nativo, bidireccional'] },
    { id: 'r5', type: 'trow', x: 620, y: 367, cols: TCOLS, cells: ['Depuración', 'curl y navegador', 'grpcurl'] },
    { id: 'r6', type: 'trow', x: 620, y: 414, alt: true, cols: TCOLS, cells: ['Clientes', 'cualquiera con HTTP', 'código generado'] },
    { id: 'r7', type: 'trow', x: 620, y: 461, cols: TCOLS, cells: ['Uso dominante', 'el borde, APIs públicas', 'interno, servicio a servicio'] },
    { id: 'bR', type: 'box', x: 850, y: 124, w: 306, h: 390, tone: 'green' },
    { id: 'bG', type: 'box', x: 1153, y: 124, w: 306, h: 390, tone: 'blue' },
    { id: 'web', type: 'chip', x: 620, y: 540, w: 480, tone: 'amber', text: 'el navegador no habla gRPC: gRPC-Web' },
    { id: 'mex', type: 'label', mono: true, x: 1130, y: 545, w: 390, tone: 'gray', text: 'mexi banco: gateway → servicios, rest · transferencias → cuentas, candidato a grpc' },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'No es una regla', v: 'gRPC *no reemplaza* a HTTP: corre encima. Es un trade-off, no un ganador.' }
  ],
  steps: [
    { show: ['hd', 'r1', 'r2', 'r3', 'r4', 'r5', 'r6', 'r7'], say: 'Las mismas siete filas, leídas como *precios* y no como méritos.' },
    { show: ['bR'], say: 'La frontera pública favorece REST: *cualquier cliente lo lee* y se depura con curl.' },
    { show: ['bG'], hide: ['bR'], say: 'El tráfico interno favorece gRPC: miles de llamadas, y *el contrato evita divergencias*.' },
    { show: ['web'], hide: ['bG'], say: 'Y hay un límite duro: *el navegador no habla gRPC* nativo; gRPC-Web tiende el puente.' },
    { show: ['mex', 'tr'], say: 'En nuestro sistema: el gateway habla REST; transferencias a cuentas *es el candidato*.' }
  ],
  notes: [
    'Válvula del deck: si a las 08:16 se va tarde, esta escena se cuenta en dos minutos con la pura tabla, sin los recuadros.',
    'Del video: para APIs públicas que consume el navegador, HTTP normal es más simple. gRPC brilla entre servicios de backend con rendimiento, contratos fuertes, streaming y varios lenguajes.'
  ]
});

DECK.scenes.push({
  tag: '10 / Las seis piezas', title: 'Partir el monolito.', sub: 'Crea seis preguntas.',
  items: [
    { id: 'mono', x: 600, y: 300, w: 260, t: 'Monolito', m: 'un proceso, una base' },
    { id: 'sv1', x: 960, y: 150, w: 240, t: 'Cuentas', m: 'PostgreSQL' },
    { id: 'sv2', x: 960, y: 290, w: 240, t: 'Transferencias', m: 'PostgreSQL' },
    { id: 'sv3', x: 960, y: 430, w: 240, t: 'Notificaciones', m: 'MongoDB' },
    { id: 'q', type: 'list', x: 1270, y: 140, w: 250, tone: 'red', head: 'Preguntas nuevas', rows: ['1 · ¿por dónde entra el cliente?', '2 · ¿cómo se encuentran?', '3 · ¿de dónde sale la config?', '4 · ¿y si el otro está caído?', '5 · ¿mil lecturas iguales?', '6 · ¿dónde falló la operación?'] },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'El costo de partir', v: 'Lo que era una llamada en memoria ahora *cruza la red*, con todo lo que eso trae.' }
  ],
  edges: [
    { id: 'a', from: 'mono', to: 'sv1' }, { id: 'b', from: 'mono', to: 'sv2' }, { id: 'c', from: 'mono', to: 'sv3' },
    { id: 'x1', from: 'sv2', to: 'sv1', fs: 'r', ts: 'r', pts: [[1250, 342], [1250, 202]] },
    { id: 'x2', from: 'sv2', to: 'sv3', fs: 'r', ts: 'r', pts: [[1250, 342], [1250, 482]] }
  ],
  steps: [
    { show: ['mono'], hide: ['x1', 'x2'], say: 'Ayer era un proceso: las llamadas eran *funciones en memoria*.' },
    { show: ['sv1', 'sv2', 'sv3'], set: { 'a,b,c': 'flow' }, say: 'Hoy son tres servicios, cada uno *dueño de su base*.' },
    { show: ['x1', 'x2'], set: { 'x1,x2': 'flow', 'a,b,c': 'dim' }, say: 'Y cada flecha entre ellos *ya cruza la red*: puede tardar, fallar o repetirse.' },
    { show: ['q'], set: { q: 'hot' }, say: 'Eso abre seis preguntas que el monolito nunca tuvo que contestar.' },
    { show: ['tr'], say: 'Cada pieza de hoy existe porque *responde una de esas seis*.' }
  ],
  notes: [
    'Es el mapa del deck (slide 7). Las seis preguntas: entrada, descubrimiento, configuración, acoplamiento de disponibilidad, lecturas repetidas y visibilidad.',
    'Insistir: ninguna pieza se agrega por moda; cada una paga una pregunta concreta.'
  ]
});

DECK.scenes.push({
  tag: '11 / Piezas 1 a 3', title: 'Entrar, encontrar.', sub: 'Y saber con qué valores.',
  items: [
    { id: 'cli', x: 600, y: 150, w: 200, t: 'Clientes', m: 'app y web' },
    { id: 'gw', x: 880, y: 150, w: 250, t: 'API Gateway', m: 'una sola puerta' },
    { id: 'sv1', x: 1230, y: 130, w: 250, t: 'Cuentas', m: '10.2.4.7 → cambia' },
    { id: 'sv2', x: 1230, y: 260, w: 250, t: 'Transferencias', m: '10.2.4.9 → cambia' },
    { id: 'sv3', x: 1230, y: 390, w: 250, t: 'Notificaciones', m: '10.2.5.1 → cambia' },
    { id: 'disc', x: 880, y: 300, w: 250, t: 'Discovery', m: 'Eureka · nombre → dirección' },
    { id: 'cfg', x: 880, y: 450, w: 250, t: 'Config server', m: 'el gafete, no el uniforme' },
    { id: 'c1', type: 'chip', x: 600, y: 300, w: 240, tone: 'green', text: 'TLS y límites, una vez' },
    { id: 'c2', type: 'chip', x: 600, y: 360, w: 240, text: 'late cada 30 s' },
    { id: 'c3', type: 'chip', x: 600, y: 420, w: 240, tone: 'amber', text: 'factor III' },
    { id: 'tr', type: 'term', x: 80, y: 500, k: 'Las piezas de entrada', v: 'Quién entra, a quién llama y *con qué valores* corre cada instancia.' }
  ],
  edges: [
    { id: 'e0', from: 'cli', to: 'gw' },
    { id: 'e1', from: 'gw', to: 'sv1' }, { id: 'e2', from: 'gw', to: 'sv2' }, { id: 'e3', from: 'gw', to: 'sv3' },
    { id: 'd1', from: 'disc', to: 'sv2', tone: 'violet' },
    { id: 'f1', from: 'cfg', to: 'sv3', tone: 'amber' }
  ],
  steps: [
    { show: ['cli', 'sv1', 'sv2', 'sv3'], hide: ['e1', 'e2', 'e3'], say: 'Sin gateway, cada servicio expone su puerto y *el cliente se acopla* a la topología.' },
    { show: ['gw', 'c1'], set: { 'e0,e1,e2,e3': 'flow' }, say: 'El gateway pone una sola puerta: TLS, identidad y límites *se programan una vez*.' },
    { show: ['disc', 'c2'], set: { d1: 'flow' }, say: 'En contenedores la dirección cambia en cada arranque: el *nombre* se separa de la IP.' },
    { show: ['cfg', 'c3'], set: { f1: 'flow' }, say: 'Y la misma imagen corre en dev y en prod: la configuración *va por fuera*.' },
    { show: ['tr'], say: 'Tres piezas, tres preguntas: por dónde entro, a quién llamo, *con qué valores corro*.' }
  ],
  notes: [
    'Datos duros del deck: Spring Cloud Gateway 5 (nov-2025); Gateway API GA desde el 31-oct-2023. Eureka late cada 30 s y expulsa a los 90 s; la JVM cachea DNS 30 s. Factor III: config es todo lo que varía entre despliegues.',
    'Incidentes: Cloudflare 18-nov-2025 (el proxy central devolvió 5xx de 11:28 a 17:06 UTC). Roblox 28–31-oct-2021 (Consul degenerado, 73 horas). Fastly 8-jun-2021 (un cambio de configuración válido, 85 % de la red con errores en 49 minutos).'
  ]
});

DECK.scenes.push({
  tag: '12 / Piezas 4 a 6', title: 'Aguantar, no repetir.', sub: 'Y poder contar la historia.',
  items: [
    { id: 'sv2', x: 600, y: 150, w: 250, t: 'Transferencias', m: 'llama y espera' },
    { id: 'sv3', x: 600, y: 420, w: 250, t: 'Notificaciones', m: 'a veces se cae' },
    { id: 'brk', x: 950, y: 285, w: 250, t: 'Broker', m: 'RabbitMQ · al menos una vez' },
    { id: 'db', x: 1290, y: 150, w: 230, t: 'PostgreSQL', m: 'cara y compartida' },
    { id: 'cache', x: 1290, y: 290, w: 230, t: 'Redis', m: 'copia con TTL' },
    { id: 'obs', x: 950, y: 470, w: 570, t: 'Observabilidad', m: 'logs, métricas y trazas · trace id en cada llamada' },
    { id: 'c1', type: 'chip', x: 600, y: 300, w: 250, tone: 'green', text: 'guarda para después' },
    { id: 'c2', type: 'chip', x: 1290, y: 400, w: 230, tone: 'red', text: '99 % → 98 %: el doble' },
    { id: 'c3', type: 'chip', x: 600, y: 600, w: 250, text: 'traceparent' },
    { id: 'tr', type: 'term', x: 80, y: 500, k: 'Las piezas de resiliencia', v: 'Desacoplar la disponibilidad, no repetir trabajo caro y *poder reconstruir* una operación.' }
  ],
  edges: [
    { id: 'e1', from: 'sv2', to: 'sv3', tone: 'red' },
    { id: 'e2', from: 'sv2', to: 'brk', tone: 'violet' }, { id: 'e3', from: 'brk', to: 'sv3', tone: 'violet' },
    { id: 'e4', from: 'sv2', to: 'db' }, { id: 'e5', from: 'sv2', to: 'cache', tone: 'green' }
  ],
  steps: [
    { show: ['sv2', 'sv3'], hide: ['e2', 'e3', 'e4', 'e5'], set: { e1: 'flow', sv3: 'down' }, say: 'Llamada directa: si notificaciones está caído, *la transferencia falla con él*.' },
    { show: ['brk', 'c1'], hide: ['e1'], set: { 'e2,e3': 'flow', sv3: '' }, say: 'El broker guarda el mensaje: *entrega al menos una vez*, cuando el otro vuelva.' },
    { show: ['db'], set: { e4: 'flow fast' }, say: 'La base es el recurso caro y compartido, y las mismas lecturas *se repiten mil veces*.' },
    { show: ['cache', 'c2'], set: { e5: 'flow', e4: 'dim' }, say: 'La caché es una copia que se permite estar vieja: *si está, hit*; si no, miss y TTL.' },
    { show: ['obs', 'c3'], say: 'Y cuando la operación cruza cuatro servicios, sin *trace id* hay veinte bitácoras y ninguna historia.' },
    { show: ['tr'], say: 'Tres piezas más: aguantar que el otro falle, no repetir trabajo y *ver el recorrido*.' }
  ],
  notes: [
    'Datos duros: at-least-once con ack tras persistir y dead-letter queue; RabbitMQ 4.0 (sep-2024), Kafka 4.0 sin ZooKeeper (mar-2025). De 99 % a 98 % de aciertos se duplica el tráfico a la base. W3C traceparent: versión, trace id, parent id, flags; OpenTelemetry con las tres señales estables.',
    'Incidentes: AWS Kinesis 25-nov-2020 (Cognito escribía al broker de forma bloqueante y el servicio "asíncrono" cayó también). Slack 22-feb-2022 (Memcached vacío en el pico; la base hizo timeout y la caché no se podía rellenar).'
  ]
});

DECK.scenes.push({
  tag: '13 / Cierre', title: 'Cuatro factores.', sub: 'Ya los vieron hoy.',
  items: [
    { id: 'f3', x: 600, y: 150, w: 430, t: 'III · Config', m: 'config server; en el lab, el .env' },
    { id: 'f6', x: 1070, y: 150, w: 430, t: 'VI · Procesos sin estado', m: 'discovery y N réplicas iguales' },
    { id: 'f4', x: 600, y: 290, w: 430, t: 'IV · Backing services', m: 'broker, caché y base, por URL' },
    { id: 'f11', x: 1070, y: 290, w: 430, t: 'XI · Logs', m: 'flujos a stdout; otro los junta' },
    { id: 'rec', type: 'list', x: 600, y: 440, w: 900, tone: 'green', head: 'Las seis piezas, una línea cada una', rows: [['Gateway', 'una puerta'], ['Discovery', 'nombre estable'], ['Config', 'fuera de la imagen'], ['Broker', 'al menos una vez'], ['Caché', 'copia con TTL'], ['Observabilidad', 'trace id']] },
    { id: 'tr', type: 'term', x: 80, y: 500, k: 'La regla', v: 'Ninguna pieza se agrega por moda: cada una *paga una pregunta* que abrió partir el monolito.' }
  ],
  steps: [
    { show: ['f3'], set: { f3: 'ok' }, say: 'La configuración fuera de la imagen: eso es *el factor tres*.' },
    { show: ['f6'], set: { f6: 'ok' }, say: 'Cualquier instancia atiende porque *el estado no vive en ella*: factor seis.' },
    { show: ['f4'], set: { f4: 'ok' }, say: 'Broker, caché y base son adjuntos por URL: *factor cuatro*.' },
    { show: ['f11'], set: { f11: 'ok' }, say: 'Y los logs son flujos que alguien más junta: *factor once*.' },
    { show: ['rec'], say: 'Seis piezas, seis preguntas, y cuatro factores que ya *quedaron ejercidos*.' },
    { show: ['tr'], say: 'Lo que sigue el sábado: el sistema corriendo, y *la decisión de FarmaYa*.' }
  ],
  notes: [
    '12factor.net: Wiggins, Heroku, 2011. Los otros ocho factores salen en el lab de hoy y en la S5.',
    'Válvula del deck: esta escena se puede contar en una frase si el reloj aprieta. La decisión de FarmaYa a las 09:44 nunca se recorta.'
  ]
});

DECK.scenes.push({
  tag: '14 / El mapa completo', title: 'Todas juntas.', sub: 'Seis piezas, un sistema.',
  items: [
    { id: 'cli', x: 590, y: 330, w: 180, t: 'Clientes', m: 'app y web' },
    { id: 'gw', x: 830, y: 330, w: 200, t: 'API Gateway', m: 'una sola puerta' },
    { id: 'sv1', x: 1090, y: 170, w: 200, t: 'Cuentas', m: 'saldos' },
    { id: 'sv2', x: 1090, y: 330, w: 200, t: 'Transferencias', m: 'el caso de hoy' },
    { id: 'sv3', x: 1090, y: 490, w: 200, t: 'Notificaciones', m: 'avisos' },
    { id: 'db1', x: 1340, y: 170, w: 180, t: 'PostgreSQL', m: 'relacional' },
    { id: 'db2', x: 1340, y: 330, w: 180, t: 'PostgreSQL', m: 'relacional' },
    { id: 'db3', x: 1340, y: 490, w: 180, t: 'MongoDB', m: 'documental' },
    { id: 'tl', type: 'label', mono: true, x: 590, y: 580, w: 930, tone: 'gray', text: 'transversales: hablan con todos' },
    { id: 'c1', type: 'chip', x: 590, y: 616, w: 140, tone: 'green', text: 'Redis' },
    { id: 'c2', type: 'chip', x: 740, y: 616, w: 200, text: 'Discovery' },
    { id: 'c3', type: 'chip', x: 950, y: 616, w: 180, tone: 'amber', text: 'Config' },
    { id: 'c4', type: 'chip', x: 1140, y: 616, w: 160, tone: 'violet', text: 'Broker' },
    { id: 'c5', type: 'chip', x: 1310, y: 616, w: 210, text: 'Observabilidad' },
    { id: 'rec', type: 'label', mono: true, x: 590, y: 676, w: 930, tone: 'green', text: 'gateway: una puerta · discovery: nombre estable · config: fuera de la imagen · broker: al menos una vez · caché: copia con ttl · observabilidad: trace id' },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'El mapa completo', v: 'Cada pieza sigue ahí porque *responde una pregunta* que abrió partir el monolito.' }
  ],
  edges: [
    { id: 'e0', from: 'cli', to: 'gw' },
    { id: 'e1', from: 'gw', to: 'sv1' }, { id: 'e2', from: 'gw', to: 'sv2' }, { id: 'e3', from: 'gw', to: 'sv3' },
    { id: 'd1', from: 'sv1', to: 'db1' }, { id: 'd2', from: 'sv2', to: 'db2' }, { id: 'd3', from: 'sv3', to: 'db3' }
  ],
  steps: [
    { show: ['cli', 'gw'], set: { e0: 'flow' }, say: 'El mapa completo: los clientes entran *por una sola puerta*.' },
    { show: ['sv1', 'sv2', 'sv3'], set: { 'e1,e2,e3': 'flow' }, say: 'Detrás, tres servicios, y cada flecha entre ellos *cruza la red*.' },
    { show: ['db1', 'db2', 'db3'], set: { 'd1,d2,d3': 'flow' }, say: 'Cada uno *dueño de su base*: nadie entra a la del vecino.' },
    { show: ['tl', 'c1', 'c2', 'c3', 'c4', 'c5'], say: 'Y las transversales, que hablan con todos: caché, discovery, config, broker y trazas.' },
    { show: ['rec'], say: 'Seis piezas, seis preguntas, *una línea cada una*.' },
    { show: ['tr'], say: 'Quita cualquiera y la pregunta que resolvía *vuelve a aparecer*.' }
  ],
  notes: [
    'Es la lámina de recapitulación del deck (slide 17). Sirve para cerrar la sección C y para volver a ella en la S5, cuando el sistema corra con compose.',
    'Si el reloj aprieta, esta escena se cuenta en treinta segundos: señalar la puerta, los tres dueños de su base y la banda de transversales.'
  ]
});

DECK.scenes.push({
  tag: '15 / La tarea', title: 'El mismo servicio.', sub: 'Dos veces.',
  items: [
    { id: 'l1', type: 'list', x: 600, y: 130, w: 900, head: 'Qué implementar', rows: [['Un caso de uso', 'uno solo, y el mismo en las dos versiones'], ['Versión REST', 'JSON sobre HTTP, con sus verbos y sus códigos'], ['Versión gRPC', 'un .proto, el código generado y una llamada con grpcurl'], ['Lenguaje', 'el que prefieran: Go, Java, Python, Node…']] },
    { id: 'l2', type: 'list', x: 600, y: 380, w: 900, tone: 'green', head: 'Qué se entrega', rows: [['Docker', 'un Dockerfile por servicio y un compose que levante los dos'], ['Pruebas', 'curl para REST, grpcurl para gRPC, en el README'], ['Comparación', 'bytes en el cable, tiempo de respuesta y líneas de contrato']] },
    { id: 'dst', type: 'chip', mono: true, x: 600, y: 600, w: 520, text: 'entregas/apellido_nombre/s04-api/' },
    { id: 'fec', type: 'chip', x: 1150, y: 600, w: 350, tone: 'amber', text: 'push antes del sábado 19, 07:00' },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'Lo que se evalúa', v: 'Que las dos versiones hagan *lo mismo*, y que sepan decir qué cambió al cambiar de estilo.' }
  ],
  steps: [
    { show: ['l1'], say: 'La tarea: *el mismo servicio*, implementado dos veces. REST y gRPC.' },
    { show: ['l2'], say: 'Las dos dockerizadas, y un compose que las levante *juntas*.' },
    { show: ['dst', 'fec'], say: 'Va por push, como siempre, *antes del sábado que entra*.' },
    { show: ['tr'], say: 'No se evalúa que sea grande: se evalúa que puedan *comparar* las dos.' }
  ],
  notes: [
    'Sugerencia de caso de uso, para que la comparación sea justa: un único método, por ejemplo consultar el saldo de una cuenta o buscar conductores cercanos. Lo interesante no es el tamaño, sino las dos formas de exponerlo.',
    'Qué mirar al calificar: que el .proto exista y sea el contrato real (no documentación aparte), que el compose levante ambos servicios, y que la tabla de comparación tenga números medidos, no copiados de una lámina.',
    'La fecha propuesta es el sábado 19 antes de las 07:00, para que llegue antes de la S5. Ajustarla si se prefiere el domingo 20, como en los labs.',
    'La lectura de la semana queda pendiente de definir; no se anuncia en pantalla. Candidatas: Burns y Oppenheimer, patrones de contenedores (HotCloud 2016), que acompaña al lab de compose; ZooKeeper (USENIX ATC 2010), que explica por dentro discovery y config; o Raft (2014) si se abre consenso.'
  ]
});
