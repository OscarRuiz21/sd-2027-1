# PRD · FLDSMDFR: Sistema Distribuido de Streaming Musical

**Equipo:** FLDSMDFR · **Integrantes:** Christian Franco Ramírez ([@ChristianFRZ](https://github.com/ChristianFRZ)), *[Integrante 2]* ([@usuario2](https://github.com/)), *[Integrante 3]* ([@usuario3](https://github.com/)), *[Integrante 4]* ([@usuario4](https://github.com/)) · **Fecha:** Octubre 2026  
**Materia:** Sistemas Distribuidos (FI-UNAM, Semestre 2027-1)

---

## 1. Problema y usuarios

### ¿Quién sufre el problema?
Los usuarios que desean escuchar música bajo demanda en plataformas digitales experimentan problemas de degradación de servicio, saturación de ancho de banda o interrupción de reproducción cuando un único servidor centralizado atiende simultáneamente la gestión de cuentas, la búsqueda de canciones en catálogo, la entrega pesada de archivos multimedia (audio) y el registro concurrente de estadísticas de reproducción.

### ¿Cómo se resuelve hoy y por qué no basta un enfoque centralizado?
En arquitecturas monolíticas centralizadas tradicionales, las peticiones transaccionales ligeras (ej. consultar listas de reproducción o iniciar sesión) compiten directamente por I/O de disco, CPU y conexiones abiertas contra las transferencias continuas de streaming de audio y la escritura intensiva de telemetría de reproducciones. Si el servidor se sobrecarga o falla la base de datos central, toda la plataforma queda inutilizable (punto único de falla).

### Usuarios del sistema
1. **Oyente (Usuario Final):** Navega por el catálogo de canciones, crea y organiza playlists personalizadas, y reproduce pistas de audio de forma fluida y continua.
2. **Administrador / Curador de Contenido:** Registra metadatos de nuevas canciones, administra pistas y supervisa métricas globales de reproducciones en el sistema.

---

## 2. Por qué es un sistema distribuido

El proyecto requiere una arquitectura distribuida debido a las siguientes razones inherentes al problema:

1. **Perfiles de carga heterogéneos:**
   - La **gestión de usuarios y playlists** tiene alta frecuencia de lecturas/escrituras transaccionales con payloads pequeños (JSON).
   - El **streaming de canciones** requiere transferencia de datos binarios continuos (chunks de audio), consumo intensivo de ancho de banda e I/O de disco sostenido.
   - El **registro de reproducciones** genera una ráfaga alta de escrituras concurrentes que no debe bloquear ni ralentizar la experiencia de audio del usuario.
2. **Aislamiento de fallos y resiliencia:** Si el subsistema de contadores o registro de historial se satura o cae temporalmente, el usuario debe poder seguir reproduciendo música y consultando su biblioteca.
3. **Escalabilidad horizontal independiente:** El servicio de streaming de audio y el balanceador pueden replicarse en múltiples instancias detrás de un gateway para distribuir el tráfico de red, mientras que los servicios transaccionales operan a menor escala.

---

## 3. Objetivos y lo que no vamos a hacer

### Objetivos del prototipo (Alcance del corazón)
Construir un prototipo académico funcional enfocado en demostrar conceptos clave de sistemas distribuidos:
1. **API Gateway y Enrutamiento Unificado:** Punto único de entrada para clientes con balanceo de carga e invocación a microservicios.
2. **Servicio de Usuarios y Playlists:** Gestión de perfiles, autenticación básica y administración de listas de reproducción.
3. **Servicio de Catálogo y Streaming:** Consulta de metadatos de canciones y transmisión de fragmentos de audio (audio streaming por chunks o HTTP Range requests).
4. **Servicio de Reproducciones y Métricas:** Registro concurrente de eventos de reproducción (`song_played`), incremento de contadores y cálculo de tendencias/popularidad.
5. **Comunicación inter-servicio:** Comunicación síncrona (REST/gRPC) para validación de datos y asíncrona/desacoplada para eventos de telemetría.

### Lo que NO vamos a hacer (Fuera de alcance)
- **Algoritmos complejos de recomendación basados en Machine Learning / IA.**
- **Transcodificación dinámica de audio en tiempo real a múltiples bitrates:** Se emplearán archivos de audio estándar pre-cargados (MP3/WAV/OGG).
- **Pasarelas de pago reales o cobro de suscripciones.**
- **Gestión de licencias DRM (Digital Rights Management).**
- **Aplicaciones móviles nativas:** La interacción se realizará mediante API REST/gRPC documentada, clientes CLI y/o una interfaz web ligera.

---

## 4. El requisito que perseguimos y lo que dejamos atrás

### Requisito primordial: Alta Disponibilidad y Baja Latencia en Streaming
Bajo el **Teorema CAP**, priorizamos un modelo orientado a **AP (Availability / Partition Tolerance)** en la capa de reproducción:
- **Disponibilidad:** El usuario debe poder reproducir su música y consultar canciones incluso si ciertos nodos de métricas no responden o si hay lentitud en la sincronización.
- **Baja latencia:** La inicialización del buffer de audio debe ser inmediata.

### Lo que dejamos atrás: Consistencia Estricta Inmediata
Sacrificamos consistencia inmediata (*strong consistency*) en el conteo de reproducciones e historiales en favor de **consistencia eventual**:
- Si un usuario reproduce una canción, el contador global de reproducciones puede actualizarse de manera diferida o agregada.
- No se bloquea la reproducción esperando un bloqueo transaccional distribuido de base de datos de dos fases (2PC).

---

## 5. Historias de usuario

### Funcionalidad 1: Autenticación y Gestión de Playlists
- **Historia:** *Como oyente*, quiero crear y consultar mis playlists personalizadas, *para* organizar las pistas musicales que me gustan.
- **Criterios de aceptación:**
  1. `POST /api/playlists` permite crear una lista asociada al identificador del usuario.
  2. `POST /api/playlists/{id}/songs` agrega una referencia de canción existente.
  3. `GET /api/playlists/{id}` devuelve las canciones y metadatos en formato JSON en menos de 200 ms.
  4. Si el servicio de catálogo no responde al consultar detalles, la playlist muestra las canciones con metadatos en caché o estado degradado amigable.

### Funcionalidad 2: Búsqueda en Catálogo y Streaming de Audio
- **Historia:** *Como oyente*, quiero buscar canciones y reproducir una pista seleccionada, *para* escuchar música continua desde cualquier cliente.
- **Criterios de aceptación:**
  1. `GET /api/songs` lista las canciones disponibles con título, artista, álbum y duración.
  2. `GET /api/songs/{id}/stream` entrega el flujo binario de audio admitiendo peticiones con cabeceras `Range: bytes=...` para permitir pausa, reanudación y buffering.
  3. El streaming no interrumpe la navegación ni bloquea peticiones de otros usuarios concurrentes.

### Funcionalidad 3: Registro de Reproducciones y Métricas
- **Historia:** *Como administrador o curador*, quiero registrar cada reproducción y ver el top de canciones más escuchadas, *para* identificar la popularidad del catálogo.
- **Criterios de aceptación:**
  1. Al reproducirse al menos 10 segundos de una pista, el cliente o gateway emite un evento `POST /api/playbacks`.
  2. Cada evento incluye un identificador único de idempotencia (`playback_id`) para evitar duplicar conteos ante reintentos de red.
  3. `GET /api/metrics/top` devuelve la lista ordenada de canciones más reproducidas con consistencia eventual.

---

## 6. Del monolito a los servicios

### Módulos del monolito inicial (`01-monolito`)
Toda la lógica reside en una sola aplicación con un único proceso:
- `module_users`: Gestión en memoria / base única de usuarios y sesiones.
- `module_playlists`: Gestión de colecciones y asociaciones usuario-canción.
- `module_catalog`: Metadatos y lectura directa de archivos locales de audio.
- `module_playback`: Incremento sincrónico del contador en la misma base de datos.

### Servicios resultantes (`02-separacion` en adelante)
1. **API Gateway / Proxy:** Punto de entrada HTTP expuesto en el puerto frontal (`8080`), enruta y balancea hacia los servicios internos.
2. **Servicio de Usuarios y Playlists (`users-playlists-service`):** Expone endpoints REST para usuarios y listas de reproducción. Dueño de la base de datos `users_playlists.db`.
3. **Servicio de Catálogo y Streaming (`catalog-streaming-service`):** Expone endpoints de búsqueda y streaming de audio (`/stream`). Dueño de la base `catalog.db` y del volumen de almacenamiento de archivos multimedia (`/media/audio`).
4. **Servicio de Métricas y Reproducciones (`playback-metrics-service`):** Procesa eventos de escucha, gestiona la concurrencia de contadores y almacena el histórico en `playbacks.db`.

### Dueño de cada dato (Single Source of Truth)
| Dato / Entidad | Servicio Dueño | Almacenamiento |
|---|---|---|
| Usuarios y credenciales | `users-playlists-service` | Base de datos relacional aislada |
| Playlists y enlaces usuario-canción | `users-playlists-service` | Base de datos relacional aislada |
| Metadatos de canciones (título, artista, duración) | `catalog-streaming-service` | Base de datos de catálogo |
| Archivos de audio (binarios MP3) | `catalog-streaming-service` | Volumen compartido de archivos multimedia |
| Eventos de reproducción y contadores | `playback-metrics-service` | Base de datos de telemetría y métricas |

### Plan de ramas progresivo
- `01-monolito`: Aplicación única en Python (FastAPI/Flask) con los módulos acoplados y base de datos local.
- `02-separacion`: División del código en 3 servicios independientes en contenedores Docker comunicándose por red interna.
- `03-gateway-balanceo`: Integración del API Gateway centralizado y balanceador de carga con múltiples instancias del servicio de streaming.
- `04-resiliencia-concurrencia`: Implementación de timeouts, reintentos con backoff, claves de idempotencia y degradación elegante ante caídas de nodos.

---

## 7. Dónde vive cada pieza

| Pieza | Dónde vive | Por qué | Qué pasa si falla |
|---|---|---|---|
| **API Gateway** | Contenedor frontal expuesto (`gateway`, puerto `8080`) | Actúa como fachada única para los clientes, oculta la topología interna y enruta tráfico. | Si se cae, el sistema queda inaccesible para clientes externos. Se mitiga mediante reinicio automático (`restart: always` en Docker). |
| **Directorio / DNS** | Red interna de Docker Compose (`networks: sd-net`) con resolución DNS por nombre de servicio | Permite desacoplar IPs y resolver nombres como `http://catalog-service:5001`. | Si falla la resolución de nombres, los servicios no se comunican; Docker Engine administra este DNS embebido. |
| **Balanceo** | Integrado en el API Gateway o proxy inverso (ej. Nginx / Gateway Round-Robin) frente a `catalog-streaming-service` | La carga de streaming es pesada; repartir entre múltiples réplicas (`catalog_1`, `catalog_2`) evita saturación. | Si una réplica falla, el balanceador redirige a réplicas activas (failover transparente). |
| **Datos (tipo de base, réplicas)** | Bases de datos independientes por servicio (SQLite / PostgreSQL) en volúmenes persistentes | Garantiza autonomía de microservicios y evita que consultas analíticas bloqueen transacciones. | Si cae una base de datos específica, solo se afecta el servicio dependiente; el resto continúa operando. |
| **Operaciones entre servicios** | Llamadas REST internas y comunicación asíncrona mediante eventos de reproducción | Desacopla la respuesta inmediata al usuario del procesamiento de telemetría. | Se aplican timeouts de 1.5s y degradación: si falla el registro de reproducción, el streaming no se corta. |
| **Manejo de fallas** | Clientes internos con timeout configurable, circuit breaker simple y claves de idempotencia UUID | Previene bloqueos por cascada (*cascading failures*) y evita conteos duplicados por reintentos de red. | Si un servicio externo está caído, el circuit breaker abre el circuito y responde con fallback inmediato. |

---

## 8. Stack y cómo se levanta

### Tecnologías seleccionadas
- **Lenguaje:** Python 3.11+ (estándar de la materia para servicios distribuidos).
- **Frameworks web y comunicación:**
  - **FastAPI / Flask:** Para las APIs REST del Gateway, Usuarios y Métricas.
  - **gRPC / HTTP Streaming:** Para comunicación de alto rendimiento o streaming binario por chunks.
- **Bases de datos:** SQLite (o PostgreSQL ligero en contenedor) con esquemas separados por servicio.
- **Orquestación y contenedores:** Docker y Docker Compose.

### Cómo se levanta el entorno
Toda la arquitectura se despliega con un solo comando desde la raíz del proyecto:

```bash
docker compose up --build
```

Y para apagar el sistema:
```bash
docker compose down -v
```

---

## 9. Decisiones de arquitectura

| Decisión | Alternativas descartadas | Justificación técnica |
|---|---|---|
| **Database-per-Service:** Bases independientes para cada servicio | Base de datos compartida única | Evita el acoplamiento a nivel de esquema y cuellos de botella de bloqueo en concurrencia masiva. |
| **Streaming por HTTP Range Requests:** Servir archivos de audio con soporte de chunks | Descarga completa previa en memoria | Permite reproducción instantánea sin esperar a descargar canciones completas de 5-10 MB. |
| **Idempotencia en reproducciones mediante UUIDs** | Incremento directo `counter += 1` en URL simple | Evita que reintentos de red generen múltiples incrementos erróneos para la misma sesión de escucha. |
| **Docker Compose con DNS embebido** | Configuración manual de IPs fijas o Consul complejo | Simplifica la operación académica y permite el uso de nombres canónicos estables (`http://users-service:5000`). |
| **Degradación elegante en el Gateway** | Cancelar la petición completa si algún servicio secundario falla | Si el servicio de métricas no está disponible, el usuario puede seguir escuchando su canción sin interrupciones. |

---

## 10. Preguntas abiertas y desacuerdos

1. **Protocolo de comunicación interna para streaming:** ¿Conviene exponer el streaming directamente por HTTP REST con `StreamingResponse` de FastAPI o implementar un canal gRPC con streaming unidireccional? *(Acuerdo inicial: HTTP Range Requests en REST por facilidad de consumo desde navegador/cliente estándar).*
2. **Almacenamiento de archivos binarios:** ¿Montar una carpeta compartida en el host con volúmenes de Docker o levantar un contenedor con almacenamiento de objetos tipo MinIO compatible con S3? *(Acuerdo inicial: volumen local montado en el contenedor de catálogo para simplicidad y menor sobrecarga).*
3. **Manejo de autenticación distribuida:** ¿Pasar tokens JWT sin estado firmados por el Gateway a los servicios, o validar sesiones directamente en `users-service`?
