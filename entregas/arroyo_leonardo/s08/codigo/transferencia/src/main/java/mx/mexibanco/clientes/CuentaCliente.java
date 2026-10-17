package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.Map;

@Component
public class CuentaCliente {
    private final RestClient http;
    private final CircuitBreakerCuenta circuito;

    public CuentaCliente(RestClient.Builder builder,
                         @Value("${mexibanco.servicios.cuenta}") String urlBase,
                         CircuitBreakerCuenta circuito) {
        this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
        this.circuito = circuito;
    }

    public CuentaRemota consultar(String clabe) {
        return circuito.ejecutar("consultar", () -> http.get()
                .uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class));
    }

    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return circuito.ejecutar("cargar", () -> http.post()
                .uri("/cuentas/{clabe}/cargos", clabe).body(Map.of("monto", monto))
                .retrieve().body(CuentaRemota.class));
    }

    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return circuito.ejecutar("abonar", () -> http.post()
                .uri("/cuentas/{clabe}/abonos", clabe).body(Map.of("monto", monto))
                .retrieve().body(CuentaRemota.class));
    }
}