# ---- ETAPA 1: CONSTRUCCION ----
# Se compila con la imagen oficial de Maven (no con ./mvnw): asi no importan los finales de linea
# del wrapper cuando el repo se clona en Windows.
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -Dmaven.test.skip=true

# ---- ETAPA 2: EJECUCION ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
