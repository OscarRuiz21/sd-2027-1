package mx.mexibanco.clientes;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
class CircuitoCuentaTest {
 @Test void abreRechazaSinLlamarYRecupera() {
  AtomicLong reloj = new AtomicLong();
  CircuitoCuenta c = new CircuitoCuenta(reloj::get, 10);
  for (int i=0;i<3;i++) assertThatThrownBy(() -> c.ejecutar(() -> {throw new IllegalStateException("caida");}));
  assertThat(c.getEstado()).isEqualTo(CircuitoCuenta.Estado.ABIERTO);
  assertThatThrownBy(() -> c.ejecutar(() -> {throw new AssertionError("No debe llamar");}));
  reloj.set(11);
  assertThat(c.ejecutar(() -> {assertThat(c.getEstado()).isEqualTo(CircuitoCuenta.Estado.SEMIABIERTO); return "UP";})).isEqualTo("UP");
  assertThat(c.getEstado()).isEqualTo(CircuitoCuenta.Estado.CERRADO);
 }
}
