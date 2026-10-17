package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CircuitBreakerManual;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/transferencias/circuito")
public class CircuitoController {

        private final CircuitBreakerManual breaker;

        public CircuitoController(CircuitBreakerManual breaker) {
                this.breaker = breaker;
        }

        @GetMapping
        public Map<String, Object> estado() {
                return breaker.resumen();
        }
}
