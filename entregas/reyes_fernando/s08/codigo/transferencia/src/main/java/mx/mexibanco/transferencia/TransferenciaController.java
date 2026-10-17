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

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

	private final TransferenciaService transferencias;

	public TransferenciaController(TransferenciaService transferencias) {
		this.transferencias = transferencias;
	}

	public record SolicitudTransferencia(@NotBlank String claveOrigen,
			@NotBlank String claveDestino, @NotNull @Positive BigDecimal monto) {
	}

	public record RespuestaTransferencia(Long id, String claveOrigen, String claveDestino,
			BigDecimal monto, Instant fecha, EstadoTransferencia estado, String mensaje) {
	}

	@PostMapping
	public ResponseEntity<RespuestaTransferencia> transferir(@Valid @RequestBody SolicitudTransferencia solicitud) {
		Transferencia t = transferencias.transferir(
			solicitud.claveOrigen(), solicitud.claveDestino(), solicitud.monto());

		HttpStatus codigo = switch (t.getEstado()) {
			case COMPLETADA -> HttpStatus.CREATED;
			case COMPENSADA -> HttpStatus.CONFLICT;
			case FALLIDA -> HttpStatus.UNPROCESSABLE_ENTITY;
			default -> HttpStatus.ACCEPTED;
		};

		String mensaje = switch (t.getEstado()) {
			case COMPLETADA -> "Transferencia completada correctamente.";
			case COMPENSADA -> "Fallo el abono; el dinero fue devuelto al origen.";
			case COMPENSACION_PENDIENTE -> "Fallo el abono y no se pudo confirmar la devolucion. Requiere seguimiento.";
			case REGISTRO_COMPENSACION_PENDIENTE -> "Se devolvio el dinero, pero falta verificar el movimiento de compensacion.";
			case ABONO_INCIERTO -> "El resultado del abono es incierto. Revisar antes de reintentar.";
			case CARGO_INCIERTO -> "El resultado del cargo es incierto. Revisar antes de reintentar.";
			case REGISTRO_ABONO_PENDIENTE -> "Dinero abonado; falta verificar el registro del movimiento.";
			case FALLIDA -> "El cargo fue rechazado; no se realizo la transferencia.";
			default -> "Transferencia en estado " + t.getEstado() + ".";
		};

		return ResponseEntity.status(codigo).body(new RespuestaTransferencia(t.getId(),
			t.getClaveOrigen(), t.getClaveDestino(), t.getMonto(), t.getFecha(), t.getEstado(), mensaje));
	}

	@GetMapping("/{id}")
	public Transferencia consultar(@PathVariable Long id) {
		return transferencias.consultar(id);
	}
}
