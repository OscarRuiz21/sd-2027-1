
package mx.mexibanco.clientes;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerCuentaTest {

    @Test
    void probarEstadosDelCircuitBreaker() throws InterruptedException {
        CircuitBreakerCuenta breaker = new CircuitBreakerCuenta();

        assertEquals(CircuitBreakerCuenta.Estado.CERRADO, breaker.getEstado());
        System.out.println("ESTADO INICIAL: CERRADO");

        AtomicInteger intentos = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            assertThrows(ResourceAccessException.class, () ->
                breaker.ejecutar(() -> {
                    intentos.incrementAndGet();
                    throw new ResourceAccessException("Servicio cuenta no disponible");
                })
            );
        }

        assertEquals(CircuitBreakerCuenta.Estado.ABIERTO, breaker.getEstado());
        assertEquals(3, intentos.get());
        System.out.println("DESPUES DE 3 FALLAS: ABIERTO");

        assertThrows(IllegalStateException.class, () ->
            breaker.ejecutar(() -> {
                intentos.incrementAndGet();
                return "No debe ejecutarse";
            })
        );

        assertEquals(3, intentos.get());
        System.out.println("CIRCUITO ABIERTO: solicitud rechazada sin llamar a cuenta");

        Thread.sleep(10500);

        String resultado = breaker.ejecutar(() -> {
            assertEquals(CircuitBreakerCuenta.Estado.SEMIABIERTO, breaker.getEstado());
            System.out.println("DESPUES DE ESPERAR: SEMIABIERTO");
            return "Servicio recuperado";
        });

        assertEquals("Servicio recuperado", resultado);
        assertEquals(CircuitBreakerCuenta.Estado.CERRADO, breaker.getEstado());
        assertEquals(0, breaker.getFallas());
        System.out.println("RECUPERACION EXITOSA: CERRADO");
    }
}
  