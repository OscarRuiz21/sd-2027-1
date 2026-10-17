package mx.mexibanco.transferencia;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/diagnostico")
public class DiagnosticoController {

    private final CircuitBreakerCuenta breaker;

    public DiagnosticoController(CircuitBreakerCuenta breaker) {
        this.breaker = breaker;
    }

    @GetMapping("/circuit-breaker")
    public Map<String, Object> circuitBreaker() {
        return Map.of(
                "estado", breaker.getEstado().name(),
                "fallasConsecutivas", breaker.getFallasConsecutivas(),
                "umbral", breaker.getUmbral(),
                "esperaSegundos", breaker.getEsperaSegundos(),
                "segundosRestantes", breaker.getSegundosRestantes()
        );
    }
}
