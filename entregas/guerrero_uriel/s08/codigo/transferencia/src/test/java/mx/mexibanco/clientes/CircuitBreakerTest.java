package mx.mexibanco.clientes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

	long ahora = 0;
	CircuitBreaker cb = new CircuitBreaker("cuenta", 3, 1000, () -> ahora);

	@Test
	void seAbreTrasLasFallasSeguidasDelUmbral() {
		cb.fallo();
		cb.fallo();
		assertThat(cb.estado()).isEqualTo(CircuitBreaker.Estado.CERRADO);
		cb.fallo();
		assertThat(cb.estado()).isEqualTo(CircuitBreaker.Estado.ABIERTO);
		assertThat(cb.permitir()).isFalse();
	}

	@Test
	void unExitoReiniciaLaCuenta() {
		cb.fallo();
		cb.fallo();
		cb.exito();
		cb.fallo();
		cb.fallo();
		assertThat(cb.estado()).isEqualTo(CircuitBreaker.Estado.CERRADO);
	}

	@Test
	void pasadaLaEsperaEsSemiabiertoYUnExitoLoCierra() {
		abrir();
		ahora = 1000;
		assertThat(cb.permitir()).isTrue();
		assertThat(cb.estado()).isEqualTo(CircuitBreaker.Estado.SEMIABIERTO);
		cb.exito();
		assertThat(cb.estado()).isEqualTo(CircuitBreaker.Estado.CERRADO);
	}

	@Test
	void unaSondaQueFallaReabreElCircuito() {
		abrir();
		ahora = 1000;
		cb.permitir();
		cb.fallo();
		assertThat(cb.estado()).isEqualTo(CircuitBreaker.Estado.ABIERTO);
		assertThat(cb.permitir()).isFalse();
	}

	@Test
	void enSemiabiertoSoloPasaUnaSonda() {
		abrir();
		ahora = 1000;
		assertThat(cb.permitir()).isTrue();
		assertThat(cb.permitir()).isFalse();
	}

	private void abrir() {
		cb.fallo();
		cb.fallo();
		cb.fallo();
	}
}