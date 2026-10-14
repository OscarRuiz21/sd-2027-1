
package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class CuentaCliente {

    private final RestClient http;
    private final CircuitBreakerManual circuito =
            new CircuitBreakerManual(3, 10_000);

    public CuentaCliente(RestClient.Builder builder,
                         @Value("${mexibanco.servicios.cuenta}") String urlBase) {
        this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
    }

    public CuentaRemota consultar(String clabe) {
        return circuito.ejecutar(() -> http.get()
                .uri("/cuentas/{clabe}", clabe)
                .retrieve()
                .body(CuentaRemota.class));
    }

    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return circuito.ejecutar(() -> http.post()
                .uri("/cuentas/{clabe}/cargos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class));
    }

    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return circuito.ejecutar(() -> http.post()
                .uri("/cuentas/{clabe}/abonos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class));
    }

    public CuentaRemota compensar(
            String clabe, BigDecimal monto, String claveOperacion) {
        return circuito.ejecutar(() -> http.post()
                .uri("/cuentas/{clabe}/compensaciones", clabe)
                .body(Map.of(
                        "monto", monto,
                        "claveOperacion", claveOperacion
                ))
                .retrieve()
                .body(CuentaRemota.class));
    }
}
