package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lo que en el monolito era CuentaService.consultar/cargar/abonar, ahora por HTTP a CUENTA_URL
 * (en v06, http://cuenta: el nombre en el directorio, y una replica distinta en cada peticion).
 * Cada metodo es una peticion independiente que se confirma sola en el servicio cuenta:
 * no hay transaccion que envuelva un cargo y el abono que le sigue.
 */
@Component
public class CuentaCliente {

	private final RestClient http;
	private final CircuitoCuenta circuito;

	public CuentaCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.cuenta}") String urlBase,
						 CircuitoCuenta circuito) {
		this.circuito = circuito;
		this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
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

	private CuentaRemota llamar(Supplier<CuentaRemota> llamada) {
		long permiso = circuito.permitir();
		try {
			CuentaRemota respuesta = llamada.get();
			if (respuesta == null) {
				throw new RestClientException("Respuesta vacía de cuenta");
			}
			circuito.registrar(permiso, true);
			return respuesta;
		} catch (ErrorRemotoException error) {
			boolean rechazo = error.getEstado() >= 400 && error.getEstado() < 500;
			circuito.registrar(permiso, rechazo);
			if (rechazo) {
				throw error;
			}
			throw new FallaCuenta("Cuenta devolvió HTTP " + error.getEstado(), true, error);
		} catch (RestClientException | IllegalStateException error) {
			circuito.registrar(permiso, false);
			boolean sinInstancia = error instanceof IllegalStateException
				&& "No instances available for cuenta".equals(error.getMessage());
			boolean sinConexion = error.getCause() instanceof ConnectException;
			if (sinInstancia || sinConexion) {
				throw new FallaCuenta("Cuenta no está disponible; no se envió la solicitud", false, error);
			}
			throw new FallaCuenta("No se pudo confirmar la respuesta de cuenta", true, error);
		}
	}
}
