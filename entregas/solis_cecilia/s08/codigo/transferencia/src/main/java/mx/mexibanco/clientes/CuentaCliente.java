package mx.mexibanco.clientes;

import mx.mexibanco.compartido.AbonoRechazadoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cliente HTTP de cuenta protegido por un circuit breaker.
 */
@Component
public class CuentaCliente {

    private final RestClient http;
    private final String clabeConFalla;
    private final CircuitoCuenta circuito = new CircuitoCuenta();

    public CuentaCliente(
            RestClient.Builder builder,
            @Value("${mexibanco.servicios.cuenta}") String urlBase,
            @Value("${ABONO_FALLA_CLABE:}") String clabeConFalla) {

        this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
        this.clabeConFalla = clabeConFalla;
    }

    public CuentaRemota consultar(String clabe) {
        return circuito.ejecutar(() ->
                http.get()
                        .uri("/cuentas/{clabe}", clabe)
                        .retrieve()
                        .body(CuentaRemota.class));
    }

    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return circuito.ejecutar(() ->
                http.post()
                        .uri("/cuentas/{clabe}/cargos", clabe)
                        .body(Map.of("monto", monto))
                        .retrieve()
                        .body(CuentaRemota.class));
    }

    public CuentaRemota abonarDestino(String clabe, BigDecimal monto) {
        // La falla simulada no cuenta como caída del servicio.
        if (!clabeConFalla.isBlank() && clabeConFalla.equals(clabe)) {
            throw new AbonoRechazadoException(
                    "Falla de prueba: no se envió el abono a " + clabe);
        }

        return abonar(clabe, monto);
    }

    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return circuito.ejecutar(() ->
                http.post()
                        .uri("/cuentas/{clabe}/abonos", clabe)
                        .body(Map.of("monto", monto))
                        .retrieve()
                        .body(CuentaRemota.class));
    }
}