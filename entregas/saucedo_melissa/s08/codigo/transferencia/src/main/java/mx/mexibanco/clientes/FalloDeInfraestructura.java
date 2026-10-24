package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Predicate;

/**
 * Que excepciones cuentan como fallo para el circuit breaker de cuenta: solo las que indican que
 * el servicio esta caído o inalcanzable. Un 404 o un 422 (saldo insuficiente) es una respuesta
 * normal de negocio y NO debe abrir el circuito.
 */
public class FalloDeInfraestructura implements Predicate<Throwable> {

        @Override
        public boolean test(Throwable e) {
                if (e instanceof ResourceAccessException) {
                        return true; // no hubo conexion o se acabo el tiempo
                }
                if (e instanceof ErrorRemotoException remoto) {
                        return remoto.getEstado() >= 500;
                }
                return e instanceof IllegalStateException
                        && e.getMessage() != null
                        && e.getMessage().startsWith("No instances available"); // Eureka no tiene instancias de cuenta
        }
}
