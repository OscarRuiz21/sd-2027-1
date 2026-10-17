package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CuentaCircuitBreakerTest {
	@Test
	void cerradoAbiertoSemiabiertoCerrado() throws InterruptedException {
		CuentaCircuitBreaker circuito = new CuentaCircuitBreaker(2, 40);
		AtomicInteger llamadas = new AtomicInteger();
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> circuito.ejecutar(() -> {
				llamadas.incrementAndGet();
				throw new ErrorRemotoException("cuenta", 503, "caida");
			})).isInstanceOf(ErrorRemotoException.class);
		}
		assertThat(circuito.estado()).isEqualTo(CuentaCircuitBreaker.Estado.OPEN);
		assertThatThrownBy(() -> circuito.ejecutar(() -> { llamadas.incrementAndGet(); return "nunca"; }))
			.isInstanceOf(CircuitoAbiertoException.class);
		assertThat(llamadas).hasValue(2);
		Thread.sleep(60);
		assertThat(circuito.ejecutar(() -> {
			assertThat(circuito.estado()).isEqualTo(CuentaCircuitBreaker.Estado.HALF_OPEN);
			return "recuperada";
		})).isEqualTo("recuperada");
		assertThat(circuito.estado()).isEqualTo(CuentaCircuitBreaker.Estado.CLOSED);
	}

	@Test
	void errorDeNegocioNoAbreCircuito() {
		CuentaCircuitBreaker circuito = new CuentaCircuitBreaker(1, 1000);
		assertThatThrownBy(() -> circuito.ejecutar(() -> {
			throw new ErrorRemotoException("cuenta", 404, "no existe");
		})).isInstanceOf(ErrorRemotoException.class);
		assertThat(circuito.estado()).isEqualTo(CuentaCircuitBreaker.Estado.CLOSED);
	}

	@Test
	void ausenciaDeInstanciasCuentaComoFallaYSeTraduceA503() {
		CuentaCircuitBreaker circuito = new CuentaCircuitBreaker(1, 1000);
		assertThatThrownBy(() -> circuito.ejecutar(() -> {
			throw new IllegalStateException("No instances available for cuenta");
		})).isInstanceOf(CuentaSinInstanciasException.class)
			.hasMessageContaining("No hay instancias disponibles de cuenta");
		assertThat(circuito.estado()).isEqualTo(CuentaCircuitBreaker.Estado.OPEN);
	}
}
