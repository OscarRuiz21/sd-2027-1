package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CircuitoAbiertoException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Respuestas que explican que paso, en lugar de un 500. Va con la mayor prioridad para ganarle a un
 * manejador generico de RuntimeException, si lo hay.
 *
 *   COMPENSADA             -> 409 Conflict: no se hizo, y el dinero ya regreso.
 *   COMPENSACION_PENDIENTE -> 503 Service Unavailable: no se hizo, y el dinero regresara.
 *   Circuito abierto       -> 503 Service Unavailable + Retry-After, sin haber llamado a cuenta.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TransferenciaNoCompletadaHandler {

	@ExceptionHandler(TransferenciaNoCompletadaException.class)
	public ProblemDetail manejar(TransferenciaNoCompletadaException e) {
		Transferencia t = e.getTransferencia();
		boolean devuelta = t.getEstado() == EstadoTransferencia.COMPENSADA;

		ProblemDetail problema = ProblemDetail.forStatusAndDetail(
			devuelta ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		problema.setTitle(devuelta ? "Transferencia revertida" : "Transferencia pendiente de devolucion");
		problema.setInstance(URI.create("/transferencias/" + t.getId()));
		problema.setProperty("transferenciaId", t.getId());
		problema.setProperty("estado", t.getEstado());
		problema.setProperty("claveOrigen", t.getClaveOrigen());
		problema.setProperty("claveDestino", t.getClaveDestino());
		problema.setProperty("monto", t.getMonto());
		problema.setProperty("motivo", t.getMotivoFalla());
		if (!devuelta) {
			problema.setProperty("errorCompensacion", t.getErrorCompensacion());
		}
		return problema;
	}

	@ExceptionHandler(CircuitoAbiertoException.class)
	public ResponseEntity<ProblemDetail> circuitoAbierto(CircuitoAbiertoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		problema.setTitle("Servicio no disponible");
		problema.setProperty("servicio", e.getServicio());
		problema.setProperty("circuito", "ABIERTO");
		long segundos = Math.max(1, (e.getReintentarEn().toMillis() + 999) / 1000);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(segundos))
			.body(problema);
	}
}
