# FLDSMDFR · Sistema Distribuido de Streaming Musical

Repositorio de trabajo del proyecto semestral para la materia de **Sistemas Distribuidos** (Semestre 2027-1, FI-UNAM).

---

## 👥 Equipo
- **Nombre del equipo:** `FLDSMDFR`
- **Integrantes:**
    -Franco Ramírez Christian (@ChristianFRZ)
    -Lopéz Hernández Miriam Amisadai (@AmisadaiLopez )
    -Nava Santiago Erick (@Saiko-E )
    -Pérez Paitán Brent Armando (@zmd-brnt )


---

## 📄 Entrega actual: PRD (Product Requirements Document)
- Fecha límite: **Sábado 10 de octubre de 2026, antes de las 07:00 AM**.
- Documento oficial generado: [PRD.md](file:///home/christian/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/entregas/proyecto/FLDSMDFR/docs/PRD.md)
- Copia en ruta canónica según especificación del curso: [`proyecto/fldsmdfr/PRD.md`](file:///home/christian/GodsDocs2/UNIVERSIDAD/9noSemestre/SD/sd-2027-1/proyecto/fldsmdfr/PRD.md).

---

## 🎯 Resumen del Proyecto

El sistema es una plataforma de **streaming musical** distribuida orientada a soportar concurrencia, alta disponibilidad y tolerancia a fallos, desacoplando los perfiles de carga del sistema:

1. **API Gateway:** Punto único de acceso con enrutamiento y balanceo.
2. **Servicio de Usuarios y Playlists (`users-playlists-service`):** Gestión relacional de perfiles y listas de reproducción.
3. **Servicio de Catálogo y Streaming (`catalog-streaming-service`):** Consulta de metadatos de canciones y entrega de fragmentos de audio (HTTP Range Requests).
4. **Servicio de Métricas y Reproducciones (`playback-metrics-service`):** Registro de escuchas con claves de idempotencia y contadores con consistencia eventual.

---

## 🛠️ Estructura de carpetas prevista

```text
FLDSMDFR/
├── README.md               # Este archivo informativo
├── PRD.md                  # Especificación del PRD
├── docs/
│   └── PRD.md              # Documentación y diagramas
├── docker-compose.yml      # Despliegue de los servicios (fases posteriores)
└── services/               # Código de los microservicios (fases posteriores)
    ├── gateway/
    ├── users-playlists/
    ├── catalog-streaming/
    └── playback-metrics/
```

---

## 🚀 Instrucciones para enviar la entrega en Git

De acuerdo con [`proyecto/README.md`](../../proyecto/README.md):

```bash
# Verificar cambios pendientes
git status

# Agregar el PRD
git add proyecto/fldsmdfr/PRD.md entregas/proyecto/FLDSMDFR/

# Confirmar entrega
git commit -m "Proyecto: PRD del equipo fldsmdfr"

# Enviar entrega a GitHub
git push origin entregas_franco_christian
```

*Nota:* No es necesario abrir Pull Request. Después de hacer el push, responder en la **Discussion #44** del repositorio de la materia con el enlace al archivo en tu rama.
