package mx.mexibanco.compartido;

import mx.mexibanco.resiliencia.CircuitoAbiertoException;
import mx.mexibanco.transferencia.TransferenciaNoCompletadaException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * Unico lugar donde una excepcion se vuelve respuesta HTTP (RFC 9457, Problem Details).
 * Gracias a esto ningun servicio de negocio importa nada de Spring Web.
 *
 * Esta clase es identica en los cinco servicios: se copio, no se comparte (ver README).
 */
@RestControllerAdvice
public class ManejadorDeErrores {

	@ExceptionHandler(NoEncontradoException.class)
	ProblemDetail noEncontrado(NoEncontradoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(ConflictoException.class)
	ProblemDetail conflicto(ConflictoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(ReglaDeNegocioException.class)
	ProblemDetail reglaDeNegocio(ReglaDeNegocioException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}

	/** Dos escrituras al mismo saldo al mismo tiempo: gana una, la otra se reintenta (@Version en Cuenta). */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail escrituraConcurrente(OptimisticLockingFailureException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Otra operacion modifico el mismo registro; reintenta");
	}

	/** El otro servicio contesto con error: se devuelve el mismo codigo y se dice quien fue. */
	@ExceptionHandler(ErrorRemotoException.class)
	ProblemDetail errorRemoto(ErrorRemotoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.getEstado()), e.getMessage());
		problema.setProperty("servicio", e.getServicio());
		return problema;
	}

	/**
	 * S08: la saga no se completo. La respuesta dice que paso con el dinero (devuelto, pendiente o
	 * nunca cobrado), en que estado quedo y con que id se puede consultar.
	 */
	@ExceptionHandler(TransferenciaNoCompletadaException.class)
	ProblemDetail transferenciaNoCompletada(TransferenciaNoCompletadaException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.getCodigoHttp()), e.getMessage());
		problema.setTitle("Transferencia no completada");
		problema.setProperty("transferenciaId", e.getTransferencia().getId());
		problema.setProperty("estado", e.getTransferencia().getEstado());
		problema.setProperty("monto", e.getTransferencia().getMonto());
		problema.setProperty("motivo", e.getTransferencia().getMotivo());
		problema.setProperty("detalleEstado", e.getTransferencia().getDetalle());
		return problema;
	}

	/** S08: el circuit breaker no dejo pasar la llamada a cuenta. Respuesta inmediata, sin esperar timeouts. */
	@ExceptionHandler(CircuitoAbiertoException.class)
	ProblemDetail circuitoAbierto(CircuitoAbiertoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
		problema.setTitle("Circuito abierto");
		problema.setProperty("circuito", e.getCircuito());
		problema.setProperty("estadoCircuito", e.getEstado());
		problema.setProperty("reintentarEnMs", e.getReintentarEnMs());
		return problema;
	}

	/** El otro servicio ni siquiera contesto (apagado, nombre que no resuelve, timeout). */
	@ExceptionHandler(ResourceAccessException.class)
	ProblemDetail servicioNoDisponible(ResourceAccessException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
			"No se pudo contactar a otro servicio: " + e.getMessage());
	}
}
