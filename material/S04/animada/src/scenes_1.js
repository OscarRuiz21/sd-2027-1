/* S04 · escenas 00–05: REST a fondo y el puente hacia gRPC.
   Guion guiado por el diálogo del reel de techvault404 y el de gRPC vs HTTP API. */
window.DECK = { title: 'Cómo se hablan los servicios', brand: 'Sistemas Distribuidos · S04', scenes: [] };

DECK.scenes.push({
  tag: '00 / REST · repaso', title: 'Recursos y verbos.', sub: 'Lo que ya vimos en la S3.',
  items: [
    { id: 'cli', x: 600, y: 300, w: 220, t: 'Cliente', m: 'app y web' },
    { id: 'api', x: 1060, y: 300, w: 300, t: 'Servicio de cuentas', m: 'HTTP/1.1 · JSON' },
    { id: 'cod', type: 'list', x: 1060, y: 470, w: 380, head: 'El código es contrato', rows: [['2xx', '· funcionó'], ['4xx', '· tu error'], ['5xx', '· el nuestro']] },
    { id: 'idem', type: 'chip', x: 600, y: 470, w: 400, tone: 'amber', k: 'S3', text: 'misma llave, mismo efecto' },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'REST', v: 'Recursos con nombre y verbos con contrato: *la red se vuelve explícita*.' }
  ],
  edges: [
    { id: 'm1', from: [820, 330], to: [1060, 330], straight: true, arrow: true, label: 'GET /cuentas/42' },
    { id: 'm2', from: [1060, 386], to: [820, 386], straight: true, arrow: true, label: '200 OK · saldo', tone: 'green' }
  ],
  steps: [
    { show: ['cli', 'api', 'm1'], set: { m1: 'flow' }, say: 'REST nombra *recursos* (cuentas, pedidos) y los mueve con verbos.' },
    { show: ['m2'], set: { m2: 'flow' }, say: 'La respuesta trae el estado del recurso y, sobre todo, *un código*.' },
    { show: ['cod'], say: 'Dos equis funcionó, cuatro equis es tu error, cinco equis *es el nuestro*.' },
    { show: ['idem', 'tr'], say: 'Y lo de la S3: repetir la misma petición *no debe cobrar dos veces*.' }
  ],
  notes: [
    'Arranque de la sección B. Enlaza con la S3: timeout, at-least-once, idempotencia. RFC 9110 para semántica de verbos y códigos.',
    'La idea que se quiere dejar: REST es la red hecha explícita. El contrato vive en la documentación, no en el código.'
  ]
});

DECK.scenes.push({
  tag: '01 / REST · las seis bases', title: 'Seis bases.', sub: 'Y una de pilón.',
  items: [
    { id: 'b1', x: 600, y: 140, w: 430, t: '1 · Recursos', m: '/usuarios, /pedidos: sustantivos con dirección' },
    { id: 'b2', x: 1070, y: 140, w: 430, t: '2 · Representaciones', m: 'casi siempre JSON, pero no es obligatorio' },
    { id: 'b3', x: 600, y: 270, w: 430, t: '3 · Métodos predecibles', m: 'GET, POST, PUT, DELETE hacen lo que prometen' },
    { id: 'b4', x: 1070, y: 270, w: 430, t: '4 · Sin estado', m: 'cada petición trae lo necesario para entenderse sola' },
    { id: 'b5', x: 600, y: 400, w: 430, t: '5 · Interfaz uniforme', m: 'las mismas reglas para todos los recursos' },
    { id: 'b6', x: 1070, y: 400, w: 430, t: '6 · Cliente y servidor aparte', m: 'el cliente no sabe cómo guardas los datos' },
    { id: 'mal', type: 'chip', mono: true, x: 600, y: 530, w: 430, tone: 'red', text: 'POST /usuarios/42/borrar' },
    { id: 'bien', type: 'chip', mono: true, x: 1070, y: 530, w: 430, tone: 'green', text: 'DELETE /usuarios/42' },
    { id: 'cache', type: 'chip', x: 600, y: 600, w: 900, tone: 'green', text: 'extra: respuestas cacheables' },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'Para discutir', v: '¿Por qué REST funciona bien en sistemas distribuidos, *más allá* de GET, POST, PUT y DELETE?' }
  ],
  steps: [
    { show: ['b1'], say: 'Uno: *recursos*. Sustantivos con dirección propia: /usuarios, /pedidos.' },
    { show: ['b2'], say: 'Dos: *representaciones*. Casi siempre JSON, pero el recurso no es el JSON.' },
    { show: ['b3', 'mal', 'bien'], say: 'Tres: *métodos predecibles*. La acción va en el verbo, nunca en la URL.' },
    { show: ['b4'], set: { b4: 'ok' }, say: 'Cuatro: *sin estado*. Cada petición trae lo que necesita para entenderse sola.' },
    { show: ['b5'], say: 'Cinco: *interfaz uniforme*. Las mismas reglas para todos los recursos.' },
    { show: ['b6'], say: 'Seis: *cliente y servidor aparte*. El cliente no sabe cómo guardas los datos.' },
    { show: ['cache'], say: 'Y de pilón: *respuestas cacheables*. Quien va en medio puede guardar la copia.' },
    { show: ['tr'], set: { b4: '' }, say: 'Ahora la pregunta del grupo: ¿por qué esto *aguanta* un sistema distribuido?' }
  ],
  notes: [
    'Las seis bases en el orden en que conviene enseñarlas: recursos, representaciones, métodos, sin estado, interfaz uniforme y separación cliente-servidor. El extra es cacheabilidad. Fielding, cap. 5 (2000): son restricciones, no recomendaciones.',
    'Respuestas para la discusión: <b>sin estado</b> = cualquier réplica atiende cualquier petición, así que escalar es agregar instancias (enlaza con el factor VI y con el balanceador). <b>Cacheable</b> = el tráfico no llega al origen. <b>Interfaz uniforme</b> = los intermediarios (proxies, gateways, CDNs) operan sin saber del dominio. <b>Cliente y servidor aparte</b> = las dos partes evolucionan por separado, que es justo lo que un sistema distribuido necesita para no romperse en cada despliegue.',
    'Contraste que deja abierto el paso a gRPC: todo esto se paga con texto en el cable y contrato opcional. Preguntar: ¿qué de estas seis bases conserva gRPC?'
  ]
});

DECK.scenes.push({
  tag: '02 / REST · a fondo', title: 'El contrato opcional.', sub: 'Lo que igual hay que resolver.',
  items: [
    { id: 'l1', type: 'list', x: 600, y: 140, w: 430, head: 'Lo que el cliente necesita', rows: [['Paginación', 'diez mil filas no caben en una respuesta'], ['Versionado', '/v1 y /v2 conviven mientras migran'], ['Rate limit', '429 y Retry-After: cuántas por minuto']] },
    { id: 'l2', type: 'list', x: 1070, y: 140, w: 450, tone: 'amber', head: 'Lo que la red obliga', rows: [['Timeouts', 'soltar el hilo antes de morir esperando'], ['Reintentos', 'espera exponencial con jitter'], ['Idempotencia', 'la misma llave, el mismo efecto']] },
    { id: 'oa', type: 'chip', x: 600, y: 470, w: 430, tone: 'amber', text: 'OpenAPI: el contrato es opcional' },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'La letra chica', v: 'Nada de esto lo impone el estilo: *se acuerda*, se documenta y se vuelve a acordar.' }
  ],
  steps: [
    { show: ['l1'], say: 'REST se ve simple, pero el cliente pide más: paginar, versionar, *aguantar límites*.' },
    { show: ['l2'], say: 'Y la red obliga al resto: timeouts, reintentos con espera y *idempotencia*.' },
    { show: ['oa'], say: 'El contrato existe, pero es *opcional*: se escribe aparte… o no se escribe.' },
    { show: ['tr'], say: 'Funciona muy bien en la frontera pública. Adentro, *pide algo más*.' }
  ],
  notes: [
    'Los seis temas salen del mapa de 12 conceptos de API: REST, idempotencia, paginación, rate limits, versionado, webhooks, gRPC, GraphQL, auth, reintentos, timeouts y códigos de estado.',
    'Puente hacia gRPC: en la frontera pública el costo en bytes se paga una vez; entre servicios, mil veces por segundo.'
  ]
});

DECK.scenes.push({
  tag: '03 / El puente', title: 'Una acción.', sub: 'Muchas llamadas.',
  items: [
    { id: 'u', type: 'users', x: 640, y: 130, n: 5, g: 1, label: 'pide un viaje' },
    { id: 'trip', x: 880, y: 300, w: 240, t: 'Viajes', m: 'recibe la petición' },
    { id: 's1', x: 1260, y: 140, w: 250, t: 'Conductores', m: 'los cercanos' },
    { id: 's2', x: 1260, y: 270, w: 250, t: 'Precios', m: 'la tarifa' },
    { id: 's3', x: 1260, y: 400, w: 250, t: 'Ubicación', m: 'actualizaciones' },
    { id: 's4', x: 1260, y: 530, w: 250, t: 'Notificaciones', m: 'avisa al conductor' },
    { id: 'm', type: 'metric', x: 600, y: 430, w: 290, head: 'Por cada acción', rows: [['llamadas internas', '1', 'n'], ['conexiones', '1', 'c']] },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'Amplificación', v: 'Una petición del usuario se convierte en *N llamadas* entre servicios.' }
  ],
  edges: [
    { id: 'eu', from: 'u', to: 'trip' },
    { id: 'e1', from: 'trip', to: 's1' }, { id: 'e2', from: 'trip', to: 's2' },
    { id: 'e3', from: 'trip', to: 's3' }, { id: 'e4', from: 'trip', to: 's4' }
  ],
  steps: [
    { show: ['u', 'trip'], set: { eu: 'flow' }, say: 'Un usuario pide un viaje. Para él es *una acción*.' },
    { show: ['s1', 's2', 's3', 's4'], set: { 'e1,e2,e3,e4': 'flow' }, say: 'Adentro, viajes llama a conductores, precios, ubicación y notificaciones.' },
    { show: ['m'], count: { 'm.n': [1, 4], 'm.c': [1, 4] }, say: 'Cuatro llamadas por una. Y en hora pico, *miles por segundo*.' },
    { show: ['tr'], set: { trip: 'hot', 'e1,e2,e3,e4': 'flow slow' }, say: 'Cada flecha abre conexión, saluda con TCP y TLS, y *espera su turno*.' }
  ],
  notes: [
    'Ejemplo tomado del video: backend de una app de viajes. Sirve para justificar HTTP/2 antes de nombrar gRPC.',
    'Dos costos: el handshake de cada conexión nueva y la fila dentro de una misma conexión (head-of-line blocking en HTTP/1.1).'
  ]
});

DECK.scenes.push({
  tag: '04 / HTTP/2', title: 'Una conexión.', sub: 'Muchos streams.',
  items: [
    { id: 'la', type: 'label', mono: true, x: 600, y: 120, w: 400, tone: 'red', text: 'sin http/2' },
    { id: 'c1', type: 'chip', x: 600, y: 160, w: 400, tone: 'red', text: 'conexión 1 · TCP + TLS' },
    { id: 'c2', type: 'chip', x: 600, y: 220, w: 400, tone: 'red', text: 'conexión 2 · TCP + TLS' },
    { id: 'c3', type: 'chip', x: 600, y: 280, w: 400, tone: 'red', text: 'conexión 3 · TCP + TLS' },
    { id: 'fila', type: 'chip', x: 600, y: 360, w: 400, tone: 'amber', text: 'o una sola, y hacen fila' },
    { id: 'lb', type: 'label', mono: true, x: 1060, y: 120, w: 460, tone: 'green', text: 'con http/2' },
    { id: 'conn', x: 1060, y: 160, w: 440, h: 250, t: 'Una conexión', m: 'abierta y reutilizada' },
    { id: 'st1', type: 'chip', x: 1090, y: 240, w: 240, tone: 'green', text: 'stream 1' },
    { id: 'st2', type: 'chip', x: 1090, y: 295, w: 240, tone: 'green', text: 'stream 2' },
    { id: 'st3', type: 'chip', x: 1090, y: 350, w: 240, tone: 'green', text: 'stream 3' },
    { id: 'hdr', type: 'chip', x: 1060, y: 440, w: 440, text: 'encabezados comprimidos' },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'HTTP/2', v: 'Una conexión con varios *streams* a la vez, y encabezados que no se repiten enteros.' }
  ],
  steps: [
    { show: ['la', 'c1', 'c2', 'c3'], say: 'Antes, cada llamada abría *su propia conexión*: TCP, TLS y a empezar de nuevo.' },
    { show: ['fila'], say: 'Y si compartían una, las peticiones *hacían fila*, una detrás de otra.' },
    { show: ['lb', 'conn'], set: { 'c1,c2,c3,fila': 'dim' }, say: 'HTTP/2 deja *una conexión abierta* entre los dos servicios.' },
    { show: ['st1', 'st2', 'st3'], set: { conn: 'ok' }, say: 'Adentro, cada petición viaja en *su propio stream*, al mismo tiempo.' },
    { show: ['hdr', 'tr'], say: 'Y lo que se repite (auth, trazas, metadatos) *se comprime* en vez de reenviarse entero.' }
  ],
  notes: [
    'Orden del video: primero qué da HTTP/2, después qué agrega gRPC encima. gRPC no reemplaza a HTTP: corre sobre él.',
    'Multiplexación: varios streams en una conexión. HPACK comprime encabezados. En HTTP/1.1 la fila es a nivel de petición; en HTTP/2 queda a nivel de TCP.'
  ]
});

DECK.scenes.push({
  tag: '05 / Protobuf', title: 'Los mismos datos.', sub: 'Menos bytes.',
  items: [
    { id: 'j', type: 'chip', mono: true, x: 600, y: 170, w: 470, text: '{"id":42,"nombre":"Ada","activo":true}' },
    { id: 'jm', type: 'label', mono: true, x: 600, y: 232, w: 470, tone: 'gray', text: 'json · el nombre de cada campo viaja, siempre' },
    { id: 'p', type: 'chip', mono: true, x: 600, y: 330, w: 470, tone: 'green', text: '08 2A 12 03 41 64 61 18 01' },
    { id: 'pm', type: 'label', mono: true, x: 600, y: 392, w: 470, tone: 'gray', text: 'protobuf · número de campo y valor' },
    { id: 'm', type: 'metric', x: 1130, y: 170, w: 330, head: 'El mismo dato en el cable', rows: [['REST + JSON', '180 B', 'r'], ['gRPC + Protobuf', '180 B', 'g']] },
    { id: 'nota', type: 'chip', x: 1130, y: 420, w: 380, tone: 'amber', text: 'el nombre lo pone el .proto' },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'Por qué pesa menos', v: 'Si los dos lados ya tienen el contrato, *los nombres no viajan*.' }
  ],
  steps: [
    { show: ['j', 'jm', 'm'], say: 'JSON es texto y se lee de corrido, pero carga *el nombre de cada campo*.' },
    { show: ['p', 'pm'], count: { 'm.g': [180, 60, ' B'] }, say: 'Protobuf es binario: número de campo y valor. *Sesenta bytes* en vez de ciento ochenta.' },
    { set: { m: 'hot' }, say: 'Una llamada no se nota. *Mil por segundo*, sí.' },
    { show: ['nota', 'tr'], set: { m: '' }, say: 'No se perdió nada: el nombre lo pone el contrato, y el contrato *ya lo tienen los dos*.' }
  ],
  notes: [
    'Números del reel de gRPC vs REST: mismo dato, 180 B con REST contra 60 B con gRPC. El ejemplo 38 → 9 bytes del deck actual es el mismo argumento en pequeño.',
    'Protobuf codifica número de campo + tipo (wire type) + valor. Los nombres solo existen en el .proto.'
  ]
});

DECK.scenes.push({
  tag: '06 / El contrato', title: 'Un archivo.', sub: 'Y el código se genera.',
  items: [
    { id: 'proto', type: 'list', mono: true, x: 600, y: 140, w: 440, head: 'conductores.proto', rows: ['service Conductores {', '  rpc BuscarCercanos(Ubicacion)', '      returns (Conductores);', '}', 'message Ubicacion {', '  double lat = 1;', '  double lon = 2;', '}'] },
    { id: 'pc', x: 1120, y: 170, w: 240, t: 'protoc', m: 'genera el código' },
    { id: 'cs', x: 1120, y: 330, w: 240, t: 'Stub cliente', m: 'Go, Java, Python…' },
    { id: 'ss', x: 1120, y: 490, w: 240, t: 'Stub servidor', m: 'el que implementa' },
    { id: 'call', type: 'chip', mono: true, x: 600, y: 620, w: 560, tone: 'green', text: 'conductores.buscarCercanos(ubicacion)' },
    { id: 'tr', type: 'term', x: 80, y: 470, k: 'RPC', v: 'Llamas un método que vive en otro servidor *como si fuera local*.' }
  ],
  edges: [
    { id: 'e1', from: 'proto', to: 'pc', arrow: true },
    { id: 'e2', from: 'pc', to: 'cs', arrow: true }, { id: 'e3', from: 'pc', to: 'ss', arrow: true }
  ],
  steps: [
    { show: ['proto'], say: 'Los dos lados acuerdan *un archivo*: servicios, métodos y mensajes.' },
    { show: ['pc'], set: { e1: 'flow' }, say: 'De ahí, protoc *genera el código* de cliente y servidor.' },
    { show: ['cs', 'ss'], set: { 'e2,e3': 'flow' }, say: 'Cada quien en su lenguaje: Go llamando a Python, Java llamando a Node.' },
    { show: ['call'], say: 'Y en el código se ve así: *una función normal*.' },
    { show: ['tr'], say: 'Debajo, gRPC serializa, manda, espera y devuelve. Eso es *remote procedure call*.' }
  ],
  notes: [
    'Del diálogo: "un contrato, muchos lenguajes". El .proto es contrato obligatorio; en REST, OpenAPI es opcional.',
    'Ojo con la lección de 1984 (Birrell y Nelson) y la Nota sobre computación distribuida (Waldo, 1994): la llamada parece local, pero la red sigue ahí. Por eso gRPC trae deadline en cada llamada.'
  ]
});
