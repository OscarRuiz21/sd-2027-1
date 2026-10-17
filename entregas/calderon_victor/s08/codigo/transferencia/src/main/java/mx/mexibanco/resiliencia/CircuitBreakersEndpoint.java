package mx.mexibanco.resiliencia;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lab S08: GET /actuator/circuitbreakers, el mismo nombre que usa Resilience4j. Dice en que estado
 * esta cada circuito, cuantas fallas lleva y sus ultimos cambios de estado con la hora.
 * Dentro del contenedor: wget -qO- http://localhost:8080/actuator/circuitbreakers
 */
@Component
@Endpoint(id = "circuitbreakers")
public class CircuitBreakersEndpoint {

	private final List<CircuitBreaker> circuitos;

	public CircuitBreakersEndpoint(List<CircuitBreaker> circuitos) {
		this.circuitos = circuitos;
	}

	@ReadOperation
	public Map<String, Object> circuitos() {
		Map<String, Object> todos = new LinkedHashMap<>();
		for (CircuitBreaker circuito : circuitos) {
			todos.put(circuito.getNombre(), circuito.foto());
		}
		return todos;
	}
}
