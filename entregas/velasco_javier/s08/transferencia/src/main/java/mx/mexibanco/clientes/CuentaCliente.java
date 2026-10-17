

package mx.mexibanco.clientes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.Callable;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class CuentaCliente {

    private final RestClient http;
    private final CircuitBreakerManual circuitBreaker;

    public CuentaCliente(
            RestClient.Builder builder,
            @Value("${mexibanco.servicios.cuenta}") String urlBase,
            CircuitBreakerManual circuitBreaker) {

        this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
        this.circuitBreaker = circuitBreaker;
    }

    public CuentaRemota consultar(String clabe) {
        return ejecutar(() -> http.get()
                .uri("/cuentas/{clabe}", clabe)
                .retrieve()
                .body(CuentaRemota.class));
    }

    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return ejecutar(() -> http.post()
                .uri("/cuentas/{clabe}/cargos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class));
    }

    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return ejecutar(() -> http.post()
                .uri("/cuentas/{clabe}/abonos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class));
    }

    public CuentaRemota devolver(String clabe, BigDecimal monto) {
        return ejecutar(() -> http.post()
                .uri("/cuentas/{clabe}/devoluciones", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class));
    }

    private CuentaRemota ejecutar(Callable<CuentaRemota> llamada) {
        try {
            return circuitBreaker.ejecutar(
                    llamada,
                    error -> error instanceof ResourceAccessException
                            || error instanceof IllegalStateException
                            || (error instanceof ErrorRemotoException remoto
                            && remoto.getEstado() >= 500));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error al llamar al servicio cuenta", e);
        }
    }
}

