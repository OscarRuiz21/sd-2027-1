package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cliente HTTP de cuenta, protegido por un circuit breaker. cargar y abonar llevan clave de
 * idempotencia (header Idempotency-Key). Con el circuito abierto no se llama a cuenta: se responde
 * al instante con un 503.
 */
@Component
public class CuentaCliente {

	private final RestClient http;
	private final CircuitBreaker circuito;

	@Autowired
	public CuentaCliente(RestClient.Builder builder,
						 @Value("${mexibanco.servicios.cuenta}") String urlBase,
						 @Value("${mexibanco.circuito.umbral:3}") int umbral,
						 @Value("${mexibanco.circuito.espera-ms:10000}") long esperaMs) {
		this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
		this.circuito = new CircuitBreaker("cuenta", umbral, esperaMs);
	}

	public CuentaCliente(RestClient.Builder builder, String urlBase) {
		this(builder, urlBase, 3, 10_000);
	}

	public CuentaRemota consultar(String clabe) {
		return protegido(() -> http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota cargar(String clabe, BigDecimal monto, String claveIdempotencia) {
		return protegido(() -> http.post().uri("/cuentas/{clabe}/cargos", clabe)
			.header("Idempotency-Key", claveIdempotencia)
			.body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota abonar(String clabe, BigDecimal monto, String claveIdempotencia) {
		return protegido(() -> http.post().uri("/cuentas/{clabe}/abonos", clabe)
			.header("Idempotency-Key", claveIdempotencia)
			.body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class));
	}

	public CircuitBreaker.Estado estadoCircuito() {
		return circuito.estado();
	}

	public boolean circuitoAbierto() {
		return circuito.estaAbierto();
	}

	private <T> T protegido(Supplier<T> llamada) {
		if (!circuito.permitir()) {
			throw new ErrorRemotoException("cuenta", 503, "Circuito abierto: cuenta no esta respondiendo, no se intento la llamada");
		}
		try {
			T resultado = llamada.get();
			circuito.exito();
			return resultado;
		} catch (ErrorRemotoException e) {
			if (e.getEstado() >= 500) {
				circuito.fallo();
			} else {
				circuito.exito(); // 404/422: cuenta contesto, esta sana
			}
			throw e;
		} catch (ResourceAccessException | IllegalStateException e) {
			circuito.fallo();
			throw e;
		}
	}
}