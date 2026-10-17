package mx.mexibanco.clientes;

import mx.mexibanco.compartido.CircuitoAbiertoException;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

	private CircuitBreaker cb;

	@BeforeEach
	void setup() {
		// Umbral 3 fallas, tiempo espera 100ms para pruebas rapidas
		cb = new CircuitBreaker(3, 100);
	}

	@Test
	void iniciaEnEstadoCerrado() {
		assertThat(cb.getEstado()).isEqualTo(CircuitBreaker.Estado.CERRADO);
	}

	@Test
	void llamadasExitosasMantienenCircuitoCerrado() {
		String res = cb.ejecutar(() -> "OK");
		assertThat(res).isEqualTo("OK");
		assertThat(cb.getEstado()).isEqualTo(CircuitBreaker.Estado.CERRADO);
	}

	@Test
	void erroresDeNegocioNoAbrenElCircuito() {
		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> cb.ejecutar(() -> {
				throw new NoEncontradoException("No existe");
			})).isInstanceOf(NoEncontradoException.class);
		}
		assertThat(cb.getEstado()).isEqualTo(CircuitBreaker.Estado.CERRADO);
	}

	@Test
	void trasTresFallasDeInfraestructuraSeAbreElCircuito() {
		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> cb.ejecutar(() -> {
				throw new ResourceAccessException("Connection refused");
			})).isInstanceOf(ResourceAccessException.class);
		}

		// Ahora esta ABIERTO
		assertThat(cb.getEstado()).isEqualTo(CircuitBreaker.Estado.ABIERTO);

		// Llamada subsecuente falla de inmediato con CircuitoAbiertoException (fail-fast)
		AtomicInteger llamadasAlServicio = new AtomicInteger(0);
		assertThatThrownBy(() -> cb.ejecutar(() -> {
			llamadasAlServicio.incrementAndGet();
			return "OK";
		})).isInstanceOf(CircuitoAbiertoException.class);

		// Confirma que ni siquiera intento llamar al servicio
		assertThat(llamadasAlServicio.get()).isEqualTo(0);
	}

	@Test
	void transicionaASemiabiertoYVuelveACerradoConPruebaExitosa() throws InterruptedException {
		// Provocar 3 fallas
		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> cb.ejecutar(() -> {
				throw new ErrorRemotoException("cuenta", 500, "Error interno");
			})).isInstanceOf(ErrorRemotoException.class);
		}
		assertThat(cb.getEstado()).isEqualTo(CircuitBreaker.Estado.ABIERTO);

		// Esperar que venza el tiempo de espera (100 ms)
		Thread.sleep(120);

		// Al consultar o ejecutar pasa a SEMIABIERTO
		assertThat(cb.getEstado()).isEqualTo(CircuitBreaker.Estado.SEMIABIERTO);

		// Prueba exitosa en semiabierto
		String res = cb.ejecutar(() -> "RECUPERADO");
		assertThat(res).isEqualTo("RECUPERADO");

		// Regresa a CERRADO
		assertThat(cb.getEstado()).isEqualTo(CircuitBreaker.Estado.CERRADO);
	}

	@Test
	void transicionaASemiabiertoYVuelveAAbiertoSiFallaPrueba() throws InterruptedException {
		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> cb.ejecutar(() -> {
				throw new ResourceAccessException("Fallo");
			})).isInstanceOf(ResourceAccessException.class);
		}
		Thread.sleep(120);
		assertThat(cb.getEstado()).isEqualTo(CircuitBreaker.Estado.SEMIABIERTO);

		// Falla en semiabierto
		assertThatThrownBy(() -> cb.ejecutar(() -> {
			throw new ResourceAccessException("Sigue caido");
		})).isInstanceOf(ResourceAccessException.class);

		// Vuelve a ABIERTO
		assertThat(cb.getEstado()).isEqualTo(CircuitBreaker.Estado.ABIERTO);
	}
}
