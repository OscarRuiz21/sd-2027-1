package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.resiliencia.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lo que en el monolito era CuentaService.consultar/cargar/abonar, ahora por HTTP a CUENTA_URL
 * (en v06, http://cuenta: el nombre en el directorio, y una replica distinta en cada peticion).
 * Cada metodo es una peticion independiente que se confirma sola en el servicio cuenta:
 * no hay transaccion que envuelva un cargo y el abono que le sigue.
 *
 * Lab S08: cada llamada pasa por el circuit breaker de cuenta y tiene su propio tiempo maximo
 * (1 s para conectar y 2 s para responder), mas corto que el general de ClientesHttp.
 */
@Component
public class CuentaCliente {

	private final RestClient http;
	private final CircuitBreaker circuito;

	@Autowired
	public CuentaCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.cuenta}") String urlBase,
						 CircuitBreaker circuitoCuenta,
						 @Value("${mexibanco.cuenta.timeout-conexion-ms:1000}") int conexionMs,
						 @Value("${mexibanco.cuenta.timeout-respuesta-ms:2000}") int respuestaMs) {
		this(builder.clone().requestFactory(tiemposMaximos(conexionMs, respuestaMs)), urlBase, circuitoCuenta);
	}

	/** Para las pruebas: usa el builder tal cual (con su servidor falso) y sin cambiarle los tiempos. */
	public CuentaCliente(RestClient.Builder builder, String urlBase, CircuitBreaker circuito) {
		this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
		this.circuito = circuito;
	}

	public CuentaRemota consultar(String clabe) {
		return llamar(() -> http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota cargar(String clabe, BigDecimal monto) {
		return llamar(() -> http.post().uri("/cuentas/{clabe}/cargos", clabe).body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota abonar(String clabe, BigDecimal monto) {
		return llamar(() -> http.post().uri("/cuentas/{clabe}/abonos", clabe).body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class));
	}

	/**
	 * Toda llamada a cuenta pasa por el circuito. Lo que no es una respuesta de cuenta (no contesto,
	 * se paso del tiempo, el directorio no tiene instancias) se vuelve un 503 que dice que paso.
	 */
	private <T> T llamar(Supplier<T> llamada) {
		return circuito.llamar(() -> {
			try {
				return llamada.get();
			} catch (ResourceAccessException noContesto) {
				throw new ErrorRemotoException("cuenta", 503, "cuenta no contesto (" + causa(noContesto) + ")");
			} catch (IllegalStateException e) {
				// El balanceador lanza esto cuando Eureka no tiene ninguna instancia de cuenta
				if (e.getMessage() != null && e.getMessage().startsWith("No instances available")) {
					throw new ErrorRemotoException("cuenta", 503, "el directorio no tiene ninguna instancia de cuenta");
				}
				throw e;
			}
		});
	}

	private static SimpleClientHttpRequestFactory tiemposMaximos(int conexionMs, int respuestaMs) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(conexionMs);
		fabrica.setReadTimeout(respuestaMs);
		return fabrica;
	}

	private static String causa(Throwable e) {
		Throwable raiz = e;
		while (raiz.getCause() != null) {
			raiz = raiz.getCause();
		}
		return raiz.getMessage() != null ? raiz.getMessage() : raiz.getClass().getSimpleName();
	}
}
