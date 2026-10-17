package mx.mexibanco.transferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import mx.mexibanco.resiliencia.CircuitBreaker.CircuitoAbiertoException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

	private final TransferenciaService transferencias;

	public TransferenciaController(TransferenciaService transferencias) {
		this.transferencias = transferencias;
	}

	public record SolicitudTransferencia(@NotBlank String claveOrigen, @NotBlank String claveDestino, @NotNull @Positive BigDecimal monto) {
	}
	/**
	 * COMPLETADA --> 201
	 * COMPENSADA -> 409
	 * PENDIENTE -> 502
	 * 
	 * Se agrego un remapro de los casos mostradospara el cliente:
	 */
	@PostMapping
	public ResponseEntity<Transferencia> transferir(@Valid @RequestBody SolicitudTransferencia solicitud) {
		Transferencia transferencia = transferencias.transferir(solicitud.claveOrigen(), solicitud.claveDestino(), solicitud.monto());
		HttpStatus estado = switch (transferencia.getEstado()) {
			case COMPLETADA -> HttpStatus.CREATED;
			case COMPENSADA -> HttpStatus.CONFLICT;
			default -> HttpStatus.BAD_GATEWAY;
		};
		return ResponseEntity.status(estado).body(transferencia);
	}

	@GetMapping("/{id}")
	public Transferencia consultar(@PathVariable Long id) {
		return transferencias.consultar(id);
	}


	@GetMapping("/circuito")
	public Map<String, Object> circuito() {
		return transferencias.estadoDelCircuito();
	}

	/** Circuito abierto: cuenta esta caida y la llamada ni se intento. Es un 503, no un 500. */
	@ExceptionHandler(CircuitoAbiertoException.class)
	public ResponseEntity<Map<String, Object>> circuitoAbierto(CircuitoAbiertoException excepcion) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
			"status", 503,
			"error", "Service Unavailable",
			"mensaje", excepcion.getMessage()));
	}
}
