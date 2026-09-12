# Reel "gRPC vs REST: why is the same data smaller on the wire" (krishnachaytanyaa)

Sin audio grabado en Plaud; el contenido está en los subtítulos de los fotogramas
(`api12_frames/`, 23 fotogramas a 2 fps del segundo video, 11.8 s).

Metáfora: dos patios de carga con el mismo dato (100 bytes → 60 bytes de dato real).

- **REST · 180 B:** "REST hauls a name tag across for every field" — cada vagón lleva colgado el nombre del campo, en cada mensaje.
- **Ambos:** "both racks share one numbered manifest" — el contrato numerado (el `.proto`) que las dos partes ya tienen.
- **gRPC · 60 B:** "so gRPC drops the names. 60 bytes, not 180" — al existir el manifiesto, los nombres no viajan: solo número de campo y valor.
- Secuencia de cifras que aparece en pantalla: 180 B (REST + JSON) → 148 B → 88 B → 60 B (gRPC + Protobuf).

El primer fotograma del mismo video es otra publicación: **"12 API Concepts"** (algoinsight), una
retícula con REST, idempotencia, paginación, rate limits, versionado, webhooks, gRPC, GraphQL, auth,
reintentos, timeouts y códigos de estado. Sirve como mapa de la sección B y como banco de temas.
