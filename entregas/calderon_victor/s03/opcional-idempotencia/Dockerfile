# ─────────────────────────────────────────────────────────────────────────────
# Etapa 1 — BUILD: compila el JAR con Maven dentro del contenedor
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copia solo el POM primero para aprovechar el cache de capas de Docker:
# las dependencias se descargan una sola vez mientras el POM no cambie.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copia el código fuente y empaqueta el JAR ejecutable de Spring Boot
COPY src ./src
RUN mvn package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Etapa 2 — RUNTIME: imagen final ligera, solo el JRE y el JAR
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copia únicamente el artefacto construido en la etapa anterior
COPY --from=builder /build/target/idempotency-key-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
