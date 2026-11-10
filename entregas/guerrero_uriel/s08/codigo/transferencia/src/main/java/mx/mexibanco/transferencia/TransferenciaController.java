package mx.mexibanco.transferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
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

	/** Lo que ve el cliente: el estado final de la saga y una explicación en palabras. */
	public record RespuestaTransferencia(Long id, EstadoTransferencia estado, String mensaje,
										 String claveOrigen, String claveDestino, BigDecimal monto, Instant fecha) {
		static RespuestaTransferencia de(Transferencia t) {
			String mensaje = t.getEstado() == EstadoTransferencia.COMPLETADA
				? "Transferencia completada."
				: t.getDetalle();
			return new RespuestaTransferencia(t.getId(), t.getEstado(), mensaje,
				t.getClaveOrigen(), t.getClaveDestino(), t.getMonto(), t.getFecha());
		}
	}

	@PostMapping
	public ResponseEntity<RespuestaTransferencia> transferir(@Valid @RequestBody SolicitudTransferencia solicitud) {
		Transferencia t = transferencias.transferir(solicitud.claveOrigen(), solicitud.claveDestino(), solicitud.monto());
		HttpStatus status = switch (t.getEstado()) {
			case COMPLETADA -> HttpStatus.CREATED;       // 201
			case COMPENSADA -> HttpStatus.CONFLICT;      // 409: no se hizo, el dinero volvió
			default -> HttpStatus.SERVICE_UNAVAILABLE;   // 503: COMPENSACION_PENDIENTE, se reintenta sola
		};
		return ResponseEntity.status(status).body(RespuestaTransferencia.de(t));
	}

	/** Evidencia para la bitacora: estado actual del circuito hacia cuenta. */
	@GetMapping("/circuito")
	public Map<String, String> circuito() {
		return Map.of("circuito", "cuenta", "estado", transferencias.estadoCircuito());
	}

	@GetMapping("/{id}")
	public RespuestaTransferencia consultar(@PathVariable Long id) {
		return RespuestaTransferencia.de(transferencias.consultar(id));
	}
}