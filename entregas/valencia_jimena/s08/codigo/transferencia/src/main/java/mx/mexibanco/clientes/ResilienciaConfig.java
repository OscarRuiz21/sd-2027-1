package mx.mexibanco.clientes;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienciaConfig {

        private static final Logger log = LoggerFactory.getLogger(ResilienciaConfig.class);

        @Bean
        Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerDeCuenta() {
                return factory -> {
                        factory.configure(builder -> builder
                                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                        .slidingWindowSize(4)                  // mira las últimas 4 llamadas
                                        .minimumNumberOfCalls(4)               // no decide antes de tener 4
                                        .failureRateThreshold(50)              // si fallan 50% o más, se abre
                                        .waitDurationInOpenState(Duration.ofSeconds(15)) // tiempo abierto
                                        .permittedNumberOfCallsInHalfOpenState(2)        // pruebas en semiabierto
                                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                                        .build())
                                .timeLimiterConfig(TimeLimiterConfig.custom()
                                        .timeoutDuration(Duration.ofSeconds(8))
                                        .build()),
                                "cuenta");

                        // Deja en el log cada cambio de estado: es tu evidencia de los tres estados.
                        factory.addCircuitBreakerCustomizer(cb -> cb.getEventPublisher()
                                .onStateTransition(evento ->
                                        log.warn("CIRCUIT BREAKER 'cuenta': {}", evento.getStateTransition())),
                                "cuenta");
                };
        }
}