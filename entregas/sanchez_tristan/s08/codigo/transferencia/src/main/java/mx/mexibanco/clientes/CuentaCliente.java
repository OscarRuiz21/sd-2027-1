package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cliente HTTP hacia el servicio cuenta con proteccion de Circuit Breaker.
 * Cada llamada pasa por el CircuitBreaker para evitar colapsar si cuenta esta caida.
 */
@Component
public class CuentaCliente {

	private final RestClient http;
	private final CircuitBreaker circuitBreaker;

	@Autowired
	public CuentaCliente(RestClient.Builder builder,
						 @Value("${mexibanco.servicios.cuenta}") String urlBase,
						 @Autowired(required = false) CircuitBreaker circuitBreaker) {
		this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
		this.circuitBreaker = (circuitBreaker != null) ? circuitBreaker : new CircuitBreaker(3, 10_000);
	}

	public CuentaCliente(RestClient.Builder builder, String urlBase) {
		this(builder, urlBase, null);
	}

	public CuentaRemota consultar(String clabe) {
		return circuitBreaker.ejecutar(() ->
			http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class)
		);
	}

	public CuentaRemota cargar(String clabe, BigDecimal monto) {
		return circuitBreaker.ejecutar(() ->
			http.post().uri("/cuentas/{clabe}/cargos", clabe).body(Map.of("monto", monto))
				.retrieve().body(CuentaRemota.class)
		);
	}

	public CuentaRemota abonar(String clabe, BigDecimal monto) {
		return circuitBreaker.ejecutar(() ->
			http.post().uri("/cuentas/{clabe}/abonos", clabe).body(Map.of("monto", monto))
				.retrieve().body(CuentaRemota.class)
		);
	}

	public CircuitBreaker getCircuitBreaker() {
		return circuitBreaker;
	}
}
