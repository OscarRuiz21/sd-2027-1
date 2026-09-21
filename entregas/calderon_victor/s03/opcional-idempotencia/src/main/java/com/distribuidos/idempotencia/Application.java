package com.distribuidos.idempotencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio para demostración del patrón Idempotency Key.
 * Compara una implementación ingenua en memoria (susceptible a TOCTOU)
 * frente a una implementación robusta con restricción UNIQUE en base de datos.
 */
@SpringBootApplication
@RestController
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    private final JdbcTemplate jdbc;
    private final Map<String, String> processedKeys = new ConcurrentHashMap<>();

    public Application(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Endpoint ingenuo con Map en memoria.
     * Simula latencia para evidenciar la condición de carrera TOCTOU.
     */
    @PostMapping("/api/cobro-ingenuo")
    public ResponseEntity<Map<String, Object>> cobroIngenuo(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Header Idempotency-Key es obligatorio"));
        }

        log.warn("[INGENUO] Recibida petición con llave: {}", idempotencyKey);

        if (processedKeys.containsKey(idempotencyKey)) {
            log.info("[INGENUO] Llave ya procesada: {}", idempotencyKey);
            return ResponseEntity.ok(Map.of(
                    "status", "ya_procesado",
                    "mensaje", "Cobro ya registrado (resultado del Map)",
                    "llave", idempotencyKey
            ));
        }

        // Ventana de carrera intencional (TOCTOU) para evidenciar fallo en concurrencia
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String resultado = "cobro:$500:ts=" + Instant.now().toEpochMilli();
        processedKeys.put(idempotencyKey, resultado);

        log.warn("[INGENUO] Cobro ejecutado (posible duplicado): llave={}", idempotencyKey);

        return ResponseEntity.ok(Map.of(
                "status", "cobrado",
                "mensaje", "Cobro ejecutado correctamente",
                "llave", idempotencyKey,
                "transaccion", resultado
        ));
    }

    /**
     * Endpoint robusto con INSERT atómico en base de datos.
     * La restricción PRIMARY KEY / UNIQUE en el motor garantiza la unicidad.
     */
    @PostMapping("/api/cobro")
    public ResponseEntity<Map<String, Object>> cobroRobusto(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Header Idempotency-Key es obligatorio"));
        }

        log.info("[ROBUSTO] Recibida petición con llave: {}", idempotencyKey);

        try {
            jdbc.update(
                    "INSERT INTO idempotency_keys (key_id, status) VALUES (?, 'PENDING')",
                    idempotencyKey
            );

            log.info("[ROBUSTO] Llave reservada (PENDING). Procesando cobro: {}", idempotencyKey);

            // Simulación de procesamiento de cobro externo (latencia de pasarela)
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            String transaccionId = "txn-" + Instant.now().toEpochMilli();
            String responseBody = String.format(
                    "{\"status\":\"cobrado\",\"transaccion\":\"%s\",\"monto\":500,\"llave\":\"%s\"}",
                    transaccionId, idempotencyKey
            );

            jdbc.update(
                    "UPDATE idempotency_keys SET status='COMPLETED', response_body=? WHERE key_id=?",
                    responseBody, idempotencyKey
            );

            log.info("[ROBUSTO] Cobro completado y resultado guardado: {}", idempotencyKey);

            return ResponseEntity.ok(Map.of(
                    "status", "cobrado",
                    "transaccion", transaccionId,
                    "monto", 500,
                    "llave", idempotencyKey
            ));

        } catch (DuplicateKeyException ex) {
            // El motor rechazó el INSERT por duplicado (garantía de unicidad)
            log.warn("[ROBUSTO] DuplicateKeyException para llave: {}", idempotencyKey);

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT status, response_body FROM idempotency_keys WHERE key_id=?",
                    idempotencyKey
            );

            String status = (String) (row.get("STATUS") != null ? row.get("STATUS") : row.get("status"));
            String responseBody = (String) (row.get("RESPONSE_BODY") != null ? row.get("RESPONSE_BODY") : row.get("response_body"));

            if ("PENDING".equals(status)) {
                log.warn("[ROBUSTO] Operación en curso (PENDING): {}", idempotencyKey);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Operación en proceso, reintente más tarde"));
            }

            log.info("[ROBUSTO] Reintento seguro (COMPLETED): {}", idempotencyKey);
            return ResponseEntity.ok(Map.of(
                    "status", "ya_procesado",
                    "mensaje", "Resultado original del primer cobro exitoso",
                    "resultado_original", responseBody != null ? responseBody : "",
                    "llave", idempotencyKey
            ));
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
