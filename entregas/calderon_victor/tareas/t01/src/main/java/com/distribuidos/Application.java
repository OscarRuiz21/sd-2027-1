package com.distribuidos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del servidor Spring Boot.
 * Levanta simultáneamente:
 * - Servidor REST en el puerto 8080 (Spring MVC)
 * - Servidor gRPC en el puerto 50051 (net.devh grpc-spring-boot-starter)
 */
@SpringBootApplication
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        log.info("==================================================================");
        log.info(" Servidor T01 inicializado exitosamente:");
        log.info("   - REST endpoint: http://localhost:8080/books/{id}");
        log.info("   - gRPC service : localhost:50051 (BookService/GetBook)");
        log.info("==================================================================");
    }
}
