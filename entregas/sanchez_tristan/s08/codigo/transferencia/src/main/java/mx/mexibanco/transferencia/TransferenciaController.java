package mx.mexibanco.transferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import mx.mexibanco.clientes.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

	private final TransferenciaService transferencias;
	private final CircuitBreaker circuitBreaker;

	public TransferenciaController(TransferenciaService transferencias, CircuitBreaker circuitBreaker) {
		this.transferencias = transferencias;
		this.circuitBreaker = circuitBreaker;
	}

	public record SolicitudTransferencia(
			@NotBlank String claveOrigen,
			@NotBlank String claveDestino,
			@NotNull @Positive BigDecimal monto) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Transferencia transferir(@Valid @RequestBody SolicitudTransferencia solicitud) {
		return transferencias.transferir(solicitud.claveOrigen(), solicitud.claveDestino(), solicitud.monto());
	}

	@GetMapping("/circuit-breaker")
	public Map<String, Object> circuitBreaker() {
		return circuitBreaker.getReporte();
	}

	@GetMapping("/{id}")
	public Transferencia consultar(@PathVariable Long id) {
		return transferencias.consultar(id);
	}
}
