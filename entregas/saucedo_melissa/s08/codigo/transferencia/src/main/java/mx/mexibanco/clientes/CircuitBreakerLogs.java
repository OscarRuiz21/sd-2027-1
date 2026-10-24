package mx.mexibanco.clientes;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/** Deja en el log cada cambio de estado del breaker de cuenta (evidencia de la bitacora S08). */
@Configuration
public class CircuitBreakerLogs {

        private static final Logger log = LoggerFactory.getLogger(CircuitBreakerLogs.class);

        public CircuitBreakerLogs(CircuitBreakerRegistry registro) {
                registro.circuitBreaker("cuenta").getEventPublisher()
                        .onStateTransition(e -> log.warn("CIRCUIT BREAKER '{}': {}",
                                e.getCircuitBreakerName(), e.getStateTransition()))
                        .onCallNotPermitted(e -> log.warn("CIRCUIT BREAKER '{}': llamada rechazada, circuito abierto",
                                e.getCircuitBreakerName()));
        }
}
