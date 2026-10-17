package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class CuentaCliente {

    private final RestClient http;
    private final CircuitBreakerCuenta circuitBreaker;

    public CuentaCliente(
            RestClient.Builder builder,
            @Value("${mexibanco.servicios.cuenta}") String urlBase,
            CircuitBreakerCuenta circuitBreaker) {

        this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
        this.circuitBreaker = circuitBreaker;
    }

    public CuentaRemota consultar(String clabe) {
        return circuitBreaker.ejecutar(() ->
            http.get()
                .uri("/cuentas/{clabe}", clabe)
                .retrieve()
                .body(CuentaRemota.class)
        );
    }

    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return circuitBreaker.ejecutar(() ->
            http.post()
                .uri("/cuentas/{clabe}/cargos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class)
        );
    }

    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return circuitBreaker.ejecutar(() ->
            http.post()
                .uri("/cuentas/{clabe}/abonos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class)
        );
    }
}