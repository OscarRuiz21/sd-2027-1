
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/transferencia-0.9.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

