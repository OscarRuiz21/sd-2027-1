
package mx.mexibanco.clientes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CuentaCliente {

    private final RestClient http;
    private final CircuitBreaker circuito;

    public CuentaCliente(
            RestClient.Builder builder,
            @Value("${mexibanco.servicios.cuenta}") String urlBase,
            CircuitBreakerFactory<?, ?> factory) {

        this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
        this.circuito = factory.create("cuenta");
    }

    // Protege las llamadas HTTP con el mismo Circuit Breaker.
    private <T> T ejecutarProtegido(Supplier<T> operacion) {
        return circuito.run(operacion, error -> {

            // El circuito esta abierto y no permite nuevas llamadas.
            if (error instanceof CallNotPermittedException) {
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Circuit Breaker ABIERTO: Cuenta no disponible",
                    error
                );
            }

            // Conservar el error original para que la Saga lo maneje.
            if (error instanceof RuntimeException excepcion) {
                throw excepcion;
            }

            throw new IllegalStateException(
                "Error al comunicarse con Cuenta", error
            );
        });
    }

    public CuentaRemota consultar(String clabe) {
        return ejecutarProtegido(() ->
            http.get()
                .uri("/cuentas/{clabe}", clabe)
                .retrieve()
                .body(CuentaRemota.class)
        );
    }

    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return ejecutarProtegido(() ->
            http.post()
                .uri("/cuentas/{clabe}/cargos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class)
        );
    }

    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return ejecutarProtegido(() ->
            http.post()
                .uri("/cuentas/{clabe}/abonos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class)
        );
    }
}
