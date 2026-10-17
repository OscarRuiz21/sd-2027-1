package mx.mexibanco.clientes;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Se lanza sin llamar a cuenta cuando el circuito esta abierto. Responde 503. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class CircuitoAbiertoException extends RuntimeException {
        public CircuitoAbiertoException(String nombre) {
                super("El servicio " + nombre + " no esta disponible (circuito abierto). Intenta de nuevo en unos segundos.");
        }
}
