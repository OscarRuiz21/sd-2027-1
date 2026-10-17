package mx.mexibanco.clientes;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lo que en el monolito era CuentaService.consultar/cargar/abonar, ahora por
 * HTTP a CUENTA_URL.
 * Cada llamada pasa por un circuit breaker: si cuenta falla seguido, el
 * circuito se abre y se
 * responde error de inmediato en vez de seguir golpeando a un servicio caído.
 */
@Component
public class CuentaCliente {

	private final RestClient http;
	private final CircuitBreaker breaker;

	@Autowired
	public CuentaCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.cuenta}") String urlBase,
			CircuitBreakerFactory<?, ?> fabricaDeCircuitos) {
		this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
		this.breaker = fabricaDeCircuitos.create("cuenta");
	}

	/** Sin circuit breaker: lo usan las pruebas de ClientesHttpTest. */
	public CuentaCliente(RestClient.Builder builder, String urlBase) {
		this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
		this.breaker = null;
	}

	public CuentaRemota consultar(String clabe) {
		return conCircuito(() -> http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota cargar(String clabe, BigDecimal monto) {
		return conCircuito(() -> http.post().uri("/cuentas/{clabe}/cargos", clabe).body(Map.of("monto", monto))
				.retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota abonar(String clabe, BigDecimal monto) {
		return conCircuito(() -> http.post().uri("/cuentas/{clabe}/abonos", clabe).body(Map.of("monto", monto))
				.retrieve().body(CuentaRemota.class));
	}

	/**
	 * Ejecuta la llamada dentro del circuit breaker. Sin este "fallback", Spring
	 * envuelve todo
	 * error en NoFallbackAvailableException y un 404 de cuenta dejaría de ser 404.
	 * Aquí se
	 * relanza el error original, y si el circuito está abierto se responde un 503
	 * explicado.
	 */
	private <T> T conCircuito(Supplier<T> llamada) {
		if (breaker == null) {
			return llamada.get();
		}
		return breaker.run(llamada, error -> {
			if (error instanceof CallNotPermittedException) {
				throw new ErrorRemotoException("cuenta", 503,
						"Circuito abierto: el servicio cuenta no responde, intenta más tarde");
			}
			if (error instanceof IllegalStateException
					&& String.valueOf(error.getMessage()).contains("No instances available")) {
				throw new ErrorRemotoException("cuenta", 503,
						"El servicio cuenta no está disponible en este momento");
			}
			if (error instanceof RuntimeException e) {
				throw e;
			}
			throw new IllegalStateException(error);
		});
	}
}