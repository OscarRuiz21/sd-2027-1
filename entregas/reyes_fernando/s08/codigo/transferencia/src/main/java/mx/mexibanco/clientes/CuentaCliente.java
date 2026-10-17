package mx.mexibanco.clientes;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/** Cliente HTTP de cuenta: todas las operaciones pasan por el mismo circuit breaker. */
@Component
public class CuentaCliente {

	private final RestClient http;
	private final CircuitBreaker circuito;

	@Autowired
	public CuentaCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.cuenta}") String urlBase,
			@Qualifier("circuitoCuenta") CircuitBreaker circuito) {
		this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
		this.circuito = circuito;
	}

	/** Conserva la forma de construir el cliente en las pruebas HTTP existentes. */
	public CuentaCliente(RestClient.Builder builder, String urlBase) {
		this(builder, urlBase, CircuitBreaker.ofDefaults("cuenta-test"));
	}

	public CuentaRemota consultar(String clabe) {
		return circuito.executeSupplier(() ->
			http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota cargar(String clabe, BigDecimal monto) {
		return circuito.executeSupplier(() ->
			http.post().uri("/cuentas/{clabe}/cargos", clabe).body(Map.of("monto", monto))
				.retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota abonar(String clabe, BigDecimal monto) {
		return circuito.executeSupplier(() ->
			http.post().uri("/cuentas/{clabe}/abonos", clabe).body(Map.of("monto", monto))
				.retrieve().body(CuentaRemota.class));
	}
}
