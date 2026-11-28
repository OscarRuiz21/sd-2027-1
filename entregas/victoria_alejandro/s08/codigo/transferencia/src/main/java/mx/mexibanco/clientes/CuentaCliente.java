package mx.mexibanco.clientes;

import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lo que en el monolito era CuentaService.consultar/cargar/abonar, ahora por HTTP a CUENTA_URL
 * (en v06, http://cuenta: el nombre en el directorio, y una replica distinta en cada peticion).
 * Cada metodo es una peticion independiente que se confirma sola en el servicio cuenta:
 * no hay transaccion que envuelva un cargo y el abono que le sigue.
 *
 * S12: todas las llamadas pasan por
 *   - un TIMEOUT explicito (conexion y lectura): si cuenta esta lenta, fallamos en segundos en vez
 *     de quedarnos colgados hasta que el gateway corte con 504;
 *   - un CIRCUIT BREAKER: tras varias fallas seguidas deja de llamar a cuenta por un rato y
 *     contesta al instante con CircuitoAbiertoException (503).
 */
@Component
public class CuentaCliente {

	private final RestClient http;
	private final CircuitBreaker circuito;

	@Autowired
	public CuentaCliente(RestClient.Builder builder,
						 @Value("${mexibanco.servicios.cuenta}") String urlBase,
						 @Value("${mexibanco.cuenta.timeout-conexion-ms:1000}") long timeoutConexionMs,
						 @Value("${mexibanco.cuenta.timeout-lectura-ms:3000}") long timeoutLecturaMs,
						 @Value("${mexibanco.cuenta.circuito.umbral-fallas:3}") int umbralFallas,
						 @Value("${mexibanco.cuenta.circuito.espera-abierto-ms:20000}") long esperaAbiertoMs) {
		// clone(): el builder es compartido (y @LoadBalanced); el timeout solo aplica a cuenta.
		this(builder.clone().requestFactory(conTimeout(timeoutConexionMs, timeoutLecturaMs)),
			urlBase, umbralFallas, esperaAbiertoMs);
	}

	/**
	 * Para ClientesHttpTest: sin timeout propio, porque cambiar el requestFactory desconectaria el
	 * servidor simulado que la prueba amarra al builder.
	 */
	public CuentaCliente(RestClient.Builder builder, String urlBase) {
		this(builder, urlBase, 3, 20000);
	}

	private CuentaCliente(RestClient.Builder builder, String urlBase, int umbralFallas, long esperaAbiertoMs) {
		this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
		this.circuito = new CircuitBreaker("cuenta", umbralFallas, Duration.ofMillis(esperaAbiertoMs),
			CuentaCliente::esFallaDeCuenta);
	}

	private static SimpleClientHttpRequestFactory conTimeout(long conexionMs, long lecturaMs) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(Duration.ofMillis(conexionMs));
		fabrica.setReadTimeout(Duration.ofMillis(lecturaMs));
		return fabrica;
	}

	/**
	 * Que abre el circuito: que cuenta no conteste (caida, sin instancias en el directorio, timeout)
	 * o conteste 5xx. Un 4xx (no existe la CLABE, saldo insuficiente) NO: cuenta funciono bien.
	 */
	static boolean esFallaDeCuenta(Throwable e) {
		return !(e instanceof HttpClientErrorException
			|| e instanceof NoEncontradoException
			|| e instanceof ReglaDeNegocioException);
	}

	public CuentaRemota consultar(String clabe) {
		return protegido(() -> http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota cargar(String clabe, BigDecimal monto) {
		return protegido(() -> http.post().uri("/cuentas/{clabe}/cargos", clabe).body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota abonar(String clabe, BigDecimal monto) {
		return protegido(() -> http.post().uri("/cuentas/{clabe}/abonos", clabe).body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class));
	}

	/**
	 * Abono idempotente: si cuenta ya aplico un abono con esta clave, no lo vuelve a aplicar y solo
	 * devuelve la cuenta. Lo usa la saga para el abono a destino ("abono-{id}") y para la
	 * compensacion ("reverso-{id}"), que puede reintentarse varias veces.
	 */
	public CuentaRemota abonar(String clabe, BigDecimal monto, String claveIdempotencia) {
		return protegido(() -> http.post().uri("/cuentas/{clabe}/abonos", clabe)
			.header("Idempotency-Key", claveIdempotencia)
			.body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class));
	}

	public CircuitBreaker.Instantanea estadoDelCircuito() {
		return circuito.instantanea();
	}

	private <T> T protegido(Supplier<T> llamada) {
		return circuito.ejecutar(llamada);
	}
}
