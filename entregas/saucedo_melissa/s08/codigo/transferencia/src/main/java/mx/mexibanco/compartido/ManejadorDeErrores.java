package mx.mexibanco.compartido;

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
 * S08: en transferencia se agrego el manejador de TransferenciaFallidaException.
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

        /** El otro servicio ni siquiera contesto (apagado, nombre que no resuelve, timeout). */
        @ExceptionHandler(ResourceAccessException.class)
        ProblemDetail servicioNoDisponible(ResourceAccessException e) {
                return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                        "No se pudo contactar a otro servicio: " + e.getMessage());
        }

        /** La saga no se completo: se dice que paso con el dinero (estado) y como consultarlo (id). */
        @ExceptionHandler(TransferenciaFallidaException.class)
        ProblemDetail transferenciaFallida(TransferenciaFallidaException e) {
                ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
                problema.setTitle("Transferencia no completada");
                problema.setProperty("transferenciaId", e.getTransferenciaId());
                problema.setProperty("estado", e.getEstado());
                return problema;
        }

        /** El circuit breaker esta abierto: se rechaza al instante sin intentar la llamada. */
        @ExceptionHandler(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class)
        ProblemDetail circuitoAbierto(io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
                ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                        "El servicio cuenta no esta disponible (circuito abierto); intenta de nuevo en unos segundos");
                problema.setProperty("servicio", "cuenta");
                problema.setProperty("circuito", "ABIERTO");
                return problema;
        }

        /** Eureka no tiene ninguna instancia registrada de un servicio (por ejemplo, cuenta apagada). */
        @ExceptionHandler(IllegalStateException.class)
        ProblemDetail sinInstancias(IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("No instances available")) {
                        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                                "No hay instancias disponibles del servicio solicitado");
                        problema.setProperty("servicio", e.getMessage().replace("No instances available for ", ""));
                        return problema;
                }
                return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno");
        }
}
