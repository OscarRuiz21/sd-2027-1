package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static mx.mexibanco.clientes.CircuitBreakerCuenta.Estado.*;

class CircuitBreakerCuentaTest {
    @Test void abreRechazaSinHttpYSeRecuperaConUnaPrueba() {
        AtomicLong reloj = new AtomicLong();
        CircuitBreakerCuenta c = new CircuitBreakerCuenta(3, Duration.ofSeconds(15), reloj::get);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> c.ejecutar("consulta", () -> {
                throw new ErrorRemotoException("cuenta", 503, "caído");
            })).isInstanceOf(ErrorRemotoException.class);
        }
        assertThat(c.getEstado()).isEqualTo(ABIERTO);
        AtomicInteger llamadas = new AtomicInteger();
        assertThatThrownBy(() -> c.ejecutar("consulta", llamadas::incrementAndGet))
                .isInstanceOf(CircuitoAbiertoException.class);
        assertThat(llamadas.get()).isZero();
        reloj.addAndGet(Duration.ofSeconds(15).toNanos());
        c.ejecutar("prueba", () -> {
            assertThat(c.getEstado()).isEqualTo(SEMIABIERTO);
            assertThatThrownBy(() -> c.ejecutar("segunda", llamadas::incrementAndGet))
                    .isInstanceOf(CircuitoAbiertoException.class);
            return "ok";
        });
        assertThat(c.getEstado()).isEqualTo(CERRADO);
    }

    @Test void pruebaFallidaVuelveAAbrir() {
        AtomicLong reloj = new AtomicLong();
        CircuitBreakerCuenta c = new CircuitBreakerCuenta(1, Duration.ofSeconds(15), reloj::get);
        assertThatThrownBy(() -> c.ejecutar("consulta", () -> {
            throw new ErrorRemotoException("cuenta", 503, "caído");
        })).isInstanceOf(ErrorRemotoException.class);
        reloj.addAndGet(Duration.ofSeconds(15).toNanos());
        assertThatThrownBy(() -> c.ejecutar("prueba", () -> {
            throw new ErrorRemotoException("cuenta", 503, "caído");
        })).isInstanceOf(ErrorRemotoException.class);
        assertThat(c.getEstado()).isEqualTo(ABIERTO);
    }

    @Test void erroresDeNegocioNoAbrenCircuito() {
        CircuitBreakerCuenta c = new CircuitBreakerCuenta();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> c.ejecutar("consulta", () -> {
                throw new ErrorRemotoException("cuenta", 404, "no existe");
            })).isInstanceOf(ErrorRemotoException.class);
        }
        assertThat(c.getEstado()).isEqualTo(CERRADO);
    }

    @Test void ausenciaDeInstanciasAbreYLaPruebaFallidaReabre() {
        AtomicLong reloj = new AtomicLong();
        CircuitBreakerCuenta c = new CircuitBreakerCuenta(3, Duration.ofSeconds(15), reloj::get);
        AtomicInteger llamadas = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> c.ejecutar("consulta", () -> {
                llamadas.incrementAndGet();
                throw new IllegalStateException("No instances available for cuenta");
            })).isInstanceOf(IllegalStateException.class);
        }
        assertThat(c.getEstado()).isEqualTo(ABIERTO);
        assertThatThrownBy(() -> c.ejecutar("rechazada", llamadas::incrementAndGet))
                .isInstanceOf(CircuitoAbiertoException.class);
        assertThat(llamadas.get()).isEqualTo(3);
        reloj.addAndGet(Duration.ofSeconds(15).toNanos());
        assertThatThrownBy(() -> c.ejecutar("prueba", () -> {
            assertThat(c.getEstado()).isEqualTo(SEMIABIERTO);
            throw new IllegalStateException("No instances available for cuenta");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(c.getEstado()).isEqualTo(ABIERTO);
        reloj.addAndGet(Duration.ofSeconds(15).toNanos());
        assertThat(c.ejecutar("recuperada", () -> "ok")).isEqualTo("ok");
        assertThat(c.getEstado()).isEqualTo(CERRADO);
    }
}
