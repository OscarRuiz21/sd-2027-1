# ─────────────────────────────────────────────────────────────────────────────
# Etapa 1 — BUILD: compila Lamport.java con el JDK (no necesita Maven)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /build

COPY Lamport.java .
RUN javac -d clases Lamport.java

# ─────────────────────────────────────────────────────────────────────────────
# Etapa 2 — RUNTIME: imagen final ligera, solo el JRE y las clases compiladas
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/clases ./clases

ENTRYPOINT ["java", "-cp", "clases", "Lamport"]
