package mx.mexibanco.resiliencia;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static mx.mexibanco.resiliencia.CircuitBreaker.Estado.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S08: los tres estados del circuito y sus transiciones, con un reloj que se mueve a mano. */
class CircuitBreakerTest {

	/** Reloj de prueba: el tiempo solo avanza cuando la prueba lo pide. */
	static class RelojManual extends Clock {
		Instant ahora = Instant.parse("2026-11-15T12:00:00Z");

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

	static class FallaDeNegocio extends RuntimeException {
	}

	RelojManual reloj = new RelojManual();
	CircuitBreaker circuito = new CircuitBreaker("cuenta", 3, Duration.ofSeconds(10), 2,
		e -> !(e instanceof FallaDeNegocio), reloj);
	AtomicInteger llamadasReales = new AtomicInteger();

	String ok() {
		return circuito.ejecutar(() -> {
			llamadasReales.incrementAndGet();
			return "ok";
		});
	}

	void falla() {
		try {
			circuito.ejecutar(() -> {
				llamadasReales.incrementAndGet();
				throw new IllegalStateException("cuenta caida");
			});
		} catch (IllegalStateException esperada) {
			// la excepcion original se relanza tal cual
		}
	}

	@Test
	void seAbreTrasTresFallasSeguidasYYaNoLlamaAlServicio() {
		falla();
		falla();
		assertThat(circuito.estado()).isEqualTo(CERRADO);
		falla();
		assertThat(circuito.estado()).isEqualTo(ABIERTO);

		assertThatThrownBy(this::ok).isInstanceOf(CircuitoAbiertoException.class);
		assertThat(llamadasReales).hasValue(3); // la cuarta no llego al servicio
	}

	@Test
	void unExitoReiniciaElConteoDeFallas() {
		falla();
		falla();
		ok();
		falla();
		falla();
		assertThat(circuito.estado()).isEqualTo(CERRADO);
	}

	@Test
	void lasRespuestasDeNegocioNoAbrenElCircuito() {
		for (int i = 0; i < 5; i++) {
			try {
				circuito.ejecutar(() -> {
					throw new FallaDeNegocio();
				});
			} catch (FallaDeNegocio esperada) {
				// un 404/422: cuenta esta viva
			}
		}
		assertThat(circuito.estado()).isEqualTo(CERRADO);
	}

	@Test
	void pasaASemiabiertoTrasLaEsperaYSeCierraConDosPruebasExitosas() {
		falla();
		falla();
		falla();
		reloj.avanzar(Duration.ofSeconds(9));
		assertThat(circuito.estado()).isEqualTo(ABIERTO);
		reloj.avanzar(Duration.ofSeconds(1));
		assertThat(circuito.estado()).isEqualTo(SEMIABIERTO);

		ok();
		assertThat(circuito.estado()).isEqualTo(SEMIABIERTO);
		ok();
		assertThat(circuito.estado()).isEqualTo(CERRADO);
		assertThat(circuito.resumen().get("transiciones").toString())
			.contains("CERRADO, a=ABIERTO", "ABIERTO, a=SEMIABIERTO", "SEMIABIERTO, a=CERRADO");
	}

	@Test
	void siLaPruebaFallaSeVuelveAAbrirYEsperaOtraVez() {
		falla();
		falla();
		falla();
		reloj.avanzar(Duration.ofSeconds(10));
		falla(); // la llamada de prueba
		assertThat(circuito.estado()).isEqualTo(ABIERTO);
		reloj.avanzar(Duration.ofSeconds(5));
		assertThatThrownBy(this::ok).isInstanceOf(CircuitoAbiertoException.class);
	}

	@Test
	void enSemiabiertoSoloPasaUnaPruebaALaVez() {
		falla();
		falla();
		falla();
		reloj.avanzar(Duration.ofSeconds(10));
		circuito.ejecutar(() -> {
			// mientras la prueba esta en curso, otra llamada se rechaza de inmediato
			assertThatThrownBy(this::ok).isInstanceOf(CircuitoAbiertoException.class);
			return null;
		});
	}
}
