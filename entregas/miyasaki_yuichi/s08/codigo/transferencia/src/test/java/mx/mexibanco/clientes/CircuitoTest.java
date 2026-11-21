package mx.mexibanco.clientes;

import mx.mexibanco.compartido.CircuitoAbiertoException;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los tres estados del circuit breaker, con un reloj falso para no esperar de verdad. */
class CircuitoTest {

	private final AtomicLong ahora = new AtomicLong(0);
	private final Circuito circuito = new Circuito("cuenta", 3, 10_000, ahora::get);
	private final AtomicInteger llamadas = new AtomicInteger();

	private String cuentaCaida() {
		llamadas.incrementAndGet();
		throw new ResourceAccessException("Connection refused");
	}

	private String cuentaArriba() {
		llamadas.incrementAndGet();
		return "ok";
	}

	private void fallar(int veces) {
		for (int i = 0; i < veces; i++) {
			assertThatThrownBy(() -> circuito.ejecutar(this::cuentaCaida)).isInstanceOf(ResourceAccessException.class);
		}
	}

	@Test
	void seAbreAlLlegarAlUmbralYYaNoLlamaAlServicio() {
		fallar(2);
		assertThat(circuito.getEstado()).isEqualTo(Circuito.Estado.CERRADO);
		fallar(1);
		assertThat(circuito.getEstado()).isEqualTo(Circuito.Estado.ABIERTO);

		assertThatThrownBy(() -> circuito.ejecutar(this::cuentaArriba)).isInstanceOf(CircuitoAbiertoException.class);
		assertThat(llamadas).hasValue(3); // la cuarta no llego al servicio
	}

	@Test
	void pasadaLaEsperaPruebaUnaVezYSiSaleBienSeCierra() {
		fallar(3);
		ahora.set(10_000);

		assertThat(circuito.ejecutar(this::cuentaArriba)).isEqualTo("ok");
		assertThat(circuito.getEstado()).isEqualTo(Circuito.Estado.CERRADO);
		assertThat(circuito.getFallasSeguidas()).isZero();
	}

	@Test
	void siLaPruebaFallaSeVuelveAAbrir() {
		fallar(3);
		ahora.set(10_000);
		fallar(1);

		assertThat(circuito.getEstado()).isEqualTo(Circuito.Estado.ABIERTO);
		ahora.set(15_000); // la espera vuelve a contar desde la prueba fallida
		assertThatThrownBy(() -> circuito.ejecutar(this::cuentaArriba)).isInstanceOf(CircuitoAbiertoException.class);
	}

	@Test
	void losErroresDeNegocioNoAbrenElCircuito() {
		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> circuito.ejecutar(() -> {
				throw new ErrorRemotoException("cuenta", 404, "No existe la CLABE");
			})).isInstanceOf(ErrorRemotoException.class);
		}
		assertThat(circuito.getEstado()).isEqualTo(Circuito.Estado.CERRADO);
	}

	@Test
	void unExitoReiniciaElConteoDeFallas() {
		fallar(2);
		circuito.ejecutar(this::cuentaArriba);
		fallar(2);
		assertThat(circuito.getEstado()).isEqualTo(Circuito.Estado.CERRADO);
	}
}
