package mx.mexibanco.clientes;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Circuit breaker obligatorio para las llamadas al servicio cuenta. */
@Configuration
public class CircuitoCuentaConfig {

	private static final Logger log = LoggerFactory.getLogger(CircuitoCuentaConfig.class);

	@Bean
	CircuitBreakerRegistry registroCircuitos() {
		CircuitBreakerConfig configuracion = CircuitBreakerConfig.custom()
			.slidingWindowSize(2)
			.minimumNumberOfCalls(2)
			.failureRateThreshold(50)
			.waitDurationInOpenState(Duration.ofSeconds(12))
			.permittedNumberOfCallsInHalfOpenState(1)
			.ignoreException(error -> error instanceof ErrorRemotoException remoto
				&& remoto.getEstado() >= 400 && remoto.getEstado() < 500)
			.build();
		return CircuitBreakerRegistry.of(configuracion);
	}

	@Bean("circuitoCuenta")
	CircuitBreaker circuitoCuenta(CircuitBreakerRegistry registroCircuitos) {
		CircuitBreaker circuito = registroCircuitos.circuitBreaker("cuenta");
		circuito.getEventPublisher().onStateTransition(evento ->
			log.warn("CIRCUITO CUENTA: {}", evento.getStateTransition()));
		return circuito;
	}
}
