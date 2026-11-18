package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.resiliencia.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lo que en el monolito era CuentaService.consultar/cargar/abonar, ahora por HTTP a CUENTA_URL
 * (en v06, http://cuenta: el nombre en el directorio, y una replica distinta en cada peticion).
 * Cada metodo es una peticion independiente que se confirma sola en el servicio cuenta:
 * no hay transaccion que envuelva un cargo y el abono que le sigue.
 *
 * S08:
 *  - TODAS las llamadas a cuenta pasan por un circuit breaker (CircuitBreaker, hecho a mano).
 *  - Timeout explicito solo para cuenta (CUENTA_TIMEOUT_CONEXION_MS / CUENTA_TIMEOUT_LECTURA_MS).
 *  - Cargo y abono mandan Idempotency-Key: repetirlos no mueve el saldo dos veces.
 *  - operacionAplicada(llave) pregunta si un cargo/abono con esa llave ya ocurrio.
 */
@Component
public class CuentaCliente {

	private final RestClient http;
	private final CircuitBreaker circuito;

	@Autowired
	public CuentaCliente(RestClient.Builder builder,
						 @Value("${mexibanco.servicios.cuenta}") String urlBase,
						 @Value("${mexibanco.cuenta.timeout-conexion-ms:1000}") int timeoutConexionMs,
						 @Value("${mexibanco.cuenta.timeout-lectura-ms:3000}") int timeoutLecturaMs,
						 @Value("${mexibanco.circuito.cuenta.umbral-fallas:3}") int umbralFallas,
						 @Value("${mexibanco.circuito.cuenta.espera-ms:15000}") long esperaMs,
						 @Value("${mexibanco.circuito.cuenta.pruebas-para-cerrar:2}") int pruebasParaCerrar) {
		RestClient.Builder propio = builder.clone();
		if (timeoutConexionMs > 0 && timeoutLecturaMs > 0) {
			SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
			fabrica.setConnectTimeout(timeoutConexionMs);
			fabrica.setReadTimeout(timeoutLecturaMs);
			propio.requestFactory(fabrica);
		}
		this.http = ClientesHttp.crear(propio, urlBase, "cuenta");
		this.circuito = new CircuitBreaker("cuenta", umbralFallas, Duration.ofMillis(esperaMs), pruebasParaCerrar,
			CuentaCliente::esFalla, Clock.systemUTC());
	}

	/** Para pruebas: usa el builder tal cual (sin cambiar sus tiempos) y un circuito con valores por omision. */
	public CuentaCliente(RestClient.Builder builder, String urlBase) {
		this(builder, urlBase, 0, 0, 3, 15_000, 2);
	}

	/**
	 * Para el circuito, una respuesta 4xx de cuenta NO es falla: cuenta esta viva y contesto
	 * (no existe la CLABE, saldo insuficiente). Si son fallas los 5xx, timeouts y conexiones rechazadas.
	 */
	static boolean esFalla(Throwable e) {
		return !(e instanceof ErrorRemotoException remoto && remoto.getEstado() < 500);
	}

	public CircuitBreaker circuito() {
		return circuito;
	}

	public CuentaRemota consultar(String clabe) {
		return protegido(() -> http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota cargar(String clabe, BigDecimal monto) {
		return cargar(clabe, monto, null);
	}

	public CuentaRemota abonar(String clabe, BigDecimal monto) {
		return abonar(clabe, monto, null);
	}

	public CuentaRemota cargar(String clabe, BigDecimal monto, String llave) {
		return protegido(() -> mover("/cuentas/{clabe}/cargos", clabe, monto, llave));
	}

	public CuentaRemota abonar(String clabe, BigDecimal monto, String llave) {
		return protegido(() -> mover("/cuentas/{clabe}/abonos", clabe, monto, llave));
	}

	/** true si cuenta ya aplico un cargo/abono con esa llave; false si contesta 404. */
	public boolean operacionAplicada(String llave) {
		return protegido(() -> {
			try {
				http.get().uri("/cuentas/operaciones/{llave}", llave).retrieve().toBodilessEntity();
				return true;
			} catch (ErrorRemotoException e) {
				if (e.getEstado() == 404) {
					return false;
				}
				throw e;
			}
		});
	}

	private CuentaRemota mover(String ruta, String clabe, BigDecimal monto, String llave) {
		return http.post().uri(ruta, clabe)
			.headers(cabeceras -> {
				if (llave != null) {
					cabeceras.set("Idempotency-Key", llave);
				}
			})
			.body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class);
	}

	/**
	 * Pasa la llamada por el circuito. Si el balanceador no encuentra instancias de cuenta (todas
	 * apagadas y ya fuera del directorio) lanza IllegalStateException; se traduce a un 503 de cuenta
	 * para que el circuito lo cuente como falla y el cliente reciba algo entendible.
	 */
	private <T> T protegido(Supplier<T> llamada) {
		return circuito.ejecutar(() -> {
			try {
				return llamada.get();
			} catch (IllegalStateException sinInstancias) {
				throw new ErrorRemotoException("cuenta", 503, "No hay instancias de cuenta disponibles: " + sinInstancias.getMessage());
			}
		});
	}
}
