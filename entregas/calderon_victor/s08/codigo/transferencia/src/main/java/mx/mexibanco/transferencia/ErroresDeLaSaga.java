package mx.mexibanco.transferencia;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lab S08: la respuesta cuando la saga no se completa. Aparte de ManejadorDeErrores (que es igual en
 * los cinco servicios) porque esto solo existe en transferencia. Ademas del detalle, dice el id de la
 * transferencia y su estado, para que el cliente sepa si el dinero se devolvio o sigue pendiente.
 */
@RestControllerAdvice
public class ErroresDeLaSaga {

	@ExceptionHandler(TransferenciaFallidaException.class)
	ProblemDetail transferenciaFallida(TransferenciaFallidaException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.getCodigoHttp()), e.getMessage());
		problema.setProperty("transferencia", e.getTransferencia());
		problema.setProperty("estado", e.getEstado());
		return problema;
	}
}
