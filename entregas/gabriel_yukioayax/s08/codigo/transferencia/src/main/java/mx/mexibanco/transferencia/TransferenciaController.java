package mx.mexibanco.transferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

	private final TransferenciaService transferencias;
	private final TransferenciaRepository repositorio;

	public TransferenciaController(TransferenciaService transferencias, TransferenciaRepository repositorio) {
		this.transferencias = transferencias;
		this.repositorio = repositorio;
	}

	public record SolicitudTransferencia(@NotBlank String claveOrigen, @NotBlank String claveDestino, @NotNull @Positive BigDecimal monto) {
	}

	/**
	 * El codigo HTTP dice como termino la saga:
	 * 201 COMPLETADA · 502 COMPENSADA o COMPENSACION_PENDIENTE (el abono fallo) · 503 FALLIDA (cuenta no disponible).
	 * El cuerpo siempre trae el estado y un "detalle" con lo que paso.
	 */
	@PostMapping
	public ResponseEntity<Transferencia> transferir(@Valid @RequestBody SolicitudTransferencia solicitud) {
		Transferencia t = transferencias.transferir(solicitud.claveOrigen(), solicitud.claveDestino(), solicitud.monto());
		HttpStatus codigo = switch (t.getEstado()) {
			case COMPLETADA -> HttpStatus.CREATED;
			case COMPENSADA, COMPENSACION_PENDIENTE -> HttpStatus.BAD_GATEWAY;
			case FALLIDA -> HttpStatus.SERVICE_UNAVAILABLE;
			default -> HttpStatus.ACCEPTED;
		};
		return ResponseEntity.status(codigo).body(t);
	}

	@GetMapping("/{id}")
	public Transferencia consultar(@PathVariable Long id) {
		return transferencias.consultar(id);
	}

	/** Para revisar la saga: las transferencias en un estado, por ejemplo ?estado=COMPENSADA. */
	@GetMapping
	public List<Transferencia> porEstado(@RequestParam EstadoTransferencia estado) {
		return repositorio.findByEstado(estado);
	}

	/** Para ver el circuit breaker desde afuera. */
	@GetMapping("/circuito")
	public Map<String, Object> circuito() {
		return Map.of("cuenta", transferencias.estadoDelCircuito());
	}
}
