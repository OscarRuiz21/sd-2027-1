package mx.mexibanco.clientes;
import java.util.concurrent.atomic.AtomicLong;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CircuitoCuentaTest {
 @Test void abreRechazaSinInvocarYSeRecupera() {
  AtomicLong reloj=new AtomicLong(); CircuitoCuenta c=new CircuitoCuenta(reloj::get);
  for(int i=0;i<3;i++) assertThrows(ErrorRemotoException.class,()->c.ejecutar(()->{throw new ErrorRemotoException("cuenta",503,"caída");}));
  assertEquals("ABIERTO",c.estadoActual());
  assertThrows(ErrorRemotoException.class,()->c.ejecutar(()->{fail("No llamar a cuenta");return 0;}));
  reloj.addAndGet(10_000_000_001L);
  assertEquals(7,c.ejecutar(()->{assertEquals("SEMIABIERTO",c.estadoActual());return 7;}));
  assertEquals("CERRADO",c.estadoActual());
 }
 @Test void negocioNoAbreCircuito() {
  CircuitoCuenta c=new CircuitoCuenta();
  for(int i=0;i<5;i++) assertThrows(ErrorRemotoException.class,()->c.ejecutar(()->{throw new ErrorRemotoException("cuenta",422,"sin saldo");}));
  assertEquals("CERRADO",c.estadoActual());
 }
}
