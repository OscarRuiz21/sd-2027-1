# ---- ETAPA 1: CONSTRUCCION ----
FROM eclipse-temurin:21-jdk-alpine AS builder
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:ActiveProcessorCount=1"
ENV MAVEN_OPTS="-Xmx256m -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:ActiveProcessorCount=1"
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
COPY src ./src
RUN --mount=type=cache,id=sd-m2,target=/root/.m2,sharing=locked ./mvnw -B clean verify

# ---- ETAPA 2: EJECUCION ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
