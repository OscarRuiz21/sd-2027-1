package mx.mexibanco.clientes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Los tres estados y sus transiciones, con un reloj falso para no esperar de verdad. */
class CircuitBreakerTest {

	static class RelojFalso extends Clock {
		Instant ahora = Instant.parse("2026-01-01T00:00:00Z");

		void avanzar(Duration d) {
			ahora = ahora.plus(d);
		}

		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return ahora; }
	}

	static class ErrorDeNegocio extends RuntimeException {
	}

	private final Supplier<String> cuentaCaida = () -> { throw new IllegalStateException("No instances available for cuenta"); };
	private final Supplier<String> cuentaBien = () -> "ok";

	private RelojFalso reloj;
	private CircuitBreaker circuito;

	@BeforeEach
	void preparar() {
		reloj = new RelojFalso();
		circuito = new CircuitBreaker("cuenta", 3, Duration.ofSeconds(20), e -> !(e instanceof ErrorDeNegocio), reloj);
	}

	private void fallar(int veces) {
		for (int i = 0; i < veces; i++) {
			assertThrows(IllegalStateException.class, () -> circuito.ejecutar(cuentaCaida));
		}
	}

	@Test
	void seAbreAlLlegarAlUmbralDeFallasSeguidas() {
		fallar(2);
		assertEquals(CircuitBreaker.Estado.CERRADO, circuito.getEstado());
		fallar(1);
		assertEquals(CircuitBreaker.Estado.ABIERTO, circuito.getEstado());
	}

	@Test
	void unErrorDeNegocioNoCuentaComoFalla() {
		fallar(2);
		assertThrows(ErrorDeNegocio.class, () -> circuito.ejecutar(() -> { throw new ErrorDeNegocio(); }));
		fallar(2);
		assertEquals(CircuitBreaker.Estado.CERRADO, circuito.getEstado());
	}

	@Test
	void abiertoRechazaSinLlamar() {
		fallar(3);
		AtomicBoolean seLlamo = new AtomicBoolean(false);
		assertThrows(CircuitoAbiertoException.class, () -> circuito.ejecutar(() -> { seLlamo.set(true); return "x"; }));
		assertFalse(seLlamo.get());
	}

	@Test
	void trasLaEsperaPasaASemiabiertoYUnaPruebaBuenaLoCierra() {
		fallar(3);
		reloj.avanzar(Duration.ofSeconds(21));
		assertEquals("ok", circuito.ejecutar(cuentaBien));
		assertEquals(CircuitBreaker.Estado.CERRADO, circuito.getEstado());
	}

	@Test
	void unaPruebaFallidaLoVuelveAAbrirYReiniciaLaEspera() {
		fallar(3);
		reloj.avanzar(Duration.ofSeconds(21));
		fallar(1);
		assertEquals(CircuitBreaker.Estado.ABIERTO, circuito.getEstado());
		reloj.avanzar(Duration.ofSeconds(10));
		assertThrows(CircuitoAbiertoException.class, () -> circuito.ejecutar(cuentaBien));
	}

	@Test
	void enSemiabiertoSoloPasaUnaPruebaALaVez() {
		fallar(3);
		reloj.avanzar(Duration.ofSeconds(21));
		AtomicBoolean segundaRechazada = new AtomicBoolean(false);
		circuito.ejecutar(() -> {
			assertEquals(CircuitBreaker.Estado.SEMIABIERTO, circuito.getEstado());
			try {
				circuito.ejecutar(cuentaBien);
			} catch (CircuitoAbiertoException e) {
				segundaRechazada.set(true);
			}
			return "ok";
		});
		assertTrue(segundaRechazada.get());
		assertEquals(CircuitBreaker.Estado.CERRADO, circuito.getEstado());
	}
}
