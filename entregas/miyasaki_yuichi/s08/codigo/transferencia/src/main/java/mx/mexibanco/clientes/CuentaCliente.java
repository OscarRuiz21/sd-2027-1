package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Lo que en el monolito era CuentaService.consultar/cargar/abonar, ahora por HTTP a CUENTA_URL
 * (en v06, http://cuenta: el nombre en el directorio, y una replica distinta en cada peticion).
 * Cada metodo es una peticion independiente que se confirma sola en el servicio cuenta:
 * no hay transaccion que envuelva un cargo y el abono que le sigue.
 *
 * S08: las tres llamadas pasan por un circuit breaker. Si cuenta esta caida, despues de unas
 * cuantas fallas ya no se le llama y se responde de inmediato.
 */
@Component
public class CuentaCliente {

	private final RestClient http;
	private final Circuito circuito;

	@Autowired
	public CuentaCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.cuenta}") String urlBase,
						 @Value("${mexibanco.circuito.cuenta.umbral:3}") int umbral,
						 @Value("${mexibanco.circuito.cuenta.espera-ms:10000}") long esperaMs) {
		this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
		this.circuito = new Circuito("cuenta", umbral, esperaMs, System::currentTimeMillis);
	}

	/** Para las pruebas que no necesitan configurar el circuito. */
	public CuentaCliente(RestClient.Builder builder, String urlBase) {
		this(builder, urlBase, 3, 10_000);
	}

	public CuentaRemota consultar(String clabe) {
		return circuito.ejecutar(() -> http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota cargar(String clabe, BigDecimal monto) {
		return circuito.ejecutar(() -> http.post().uri("/cuentas/{clabe}/cargos", clabe).body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota abonar(String clabe, BigDecimal monto) {
		return circuito.ejecutar(() -> http.post().uri("/cuentas/{clabe}/abonos", clabe).body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class));
	}

	public Circuito circuito() {
		return circuito;
	}
}
