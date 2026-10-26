
package mx.mexibanco.clientes;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Cliente HTTP del servicio de cuentas.
 * Protege las llamadas remotas con un circuit breaker.
 */
@Component
public class CuentaCliente {

    private final RestClient http;

    public CuentaCliente(
            RestClient.Builder builder,
            @Value("${mexibanco.servicios.cuenta}") String urlBase) {
        this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
    }

    @CircuitBreaker(name = "cuenta")
    public CuentaRemota consultar(String clabe) {
        return http.get()
                .uri("/cuentas/{clabe}", clabe)
                .retrieve()
                .body(CuentaRemota.class);
    }

    @CircuitBreaker(name = "cuenta")
    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return http.post()
                .uri("/cuentas/{clabe}/cargos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class);
    }

    @CircuitBreaker(name = "cuenta")
    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return http.post()
                .uri("/cuentas/{clabe}/abonos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class);
    }
}
