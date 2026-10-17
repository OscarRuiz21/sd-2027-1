package mx.mexibanco.resiliencia;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lab S08: los tres estados del circuito y como cambian, con un reloj que se mueve a mano. */
class CircuitBreakerTest {

	/** Un reloj que solo avanza cuando la prueba lo dice. */
	static class RelojDePrueba extends Clock {
		Instant ahora = Instant.parse("2026-10-17T10:00:00Z");

		void avanzar(Duration d) {
			ahora = ahora.plus(d);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return ahora;
		}
	}

	private final RelojDePrueba reloj = new RelojDePrueba();
	private final CircuitBreaker circuito = new CircuitBreaker("cuenta", 3, Duration.ofSeconds(15),
		CircuitBreaker::esFallaDelServicio, reloj);
	private final AtomicInteger llamadas = new AtomicInteger();

	private String falla() {
		llamadas.incrementAndGet();
		throw new ErrorRemotoException("cuenta", 503, "cuenta no contesto");
	}

	private String bien() {
		llamadas.incrementAndGet();
		return "ok";
	}

	@Test
	void tresFallasSeguidasLoAbrenYAbiertoYaNoLlama() {
		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> circuito.llamar(this::falla)).isInstanceOf(ErrorRemotoException.class);
		}
		assertThat(circuito.getEstado()).isEqualTo(CircuitBreaker.Estado.ABIERTO);

		assertThatThrownBy(() -> circuito.llamar(this::bien)).isInstanceOf(CircuitoAbiertoException.class);
		assertThat(llamadas.get()).as("abierto: la cuarta ni siquiera se intenta").isEqualTo(3);
	}

	@Test
	void pasadaLaEsperaDejaPasarUnaPruebaYSiSaleBienSeCierra() {
		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> circuito.llamar(this::falla));
		}
		reloj.avanzar(Duration.ofSeconds(15));

		assertThat(circuito.llamar(this::bien)).isEqualTo("ok");
		assertThat(circuito.getEstado()).isEqualTo(CircuitBreaker.Estado.CERRADO);
		assertThat(circuito.foto().get("cambios").toString())
			.contains("CERRADO -> ABIERTO", "ABIERTO -> SEMIABIERTO", "SEMIABIERTO -> CERRADO");
	}

	@Test
	void siLaPruebaFallaVuelveAAbrirseConLaEsperaDesdeCero() {
		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> circuito.llamar(this::falla));
		}
		reloj.avanzar(Duration.ofSeconds(15));
		assertThatThrownBy(() -> circuito.llamar(this::falla)).isNotInstanceOf(CircuitoAbiertoException.class);
		assertThat(circuito.getEstado()).isEqualTo(CircuitBreaker.Estado.ABIERTO);

		reloj.avanzar(Duration.ofSeconds(10));
		assertThatThrownBy(() -> circuito.llamar(this::bien)).isInstanceOf(CircuitoAbiertoException.class);
	}

	@Test
	void unErrorDeNegocioNoCuentaComoFalla() {
		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> circuito.llamar(() -> {
				throw new ErrorRemotoException("cuenta", 422, "Saldo insuficiente");
			}));
		}
		assertThat(circuito.getEstado()).isEqualTo(CircuitBreaker.Estado.CERRADO);
	}

	@Test
	void unaRespuestaBuenaReiniciaLaCuentaDeFallas() {
		assertThatThrownBy(() -> circuito.llamar(this::falla));
		assertThatThrownBy(() -> circuito.llamar(this::falla));
		circuito.llamar(this::bien);
		assertThatThrownBy(() -> circuito.llamar(this::falla));
		assertThat(circuito.getEstado()).isEqualTo(CircuitBreaker.Estado.CERRADO);
	}
}
