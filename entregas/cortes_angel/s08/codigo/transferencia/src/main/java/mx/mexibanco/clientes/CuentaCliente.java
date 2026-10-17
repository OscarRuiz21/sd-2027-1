package mx.mexibanco.clientes;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class CuentaCliente {

    private final RestClient http;

    public CuentaCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.cuenta}") String urlBase) {
        this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
    }

    @CircuitBreaker(name = "cuentaCB", fallbackMethod = "fallbackConsultar")
    public CuentaRemota consultar(String clabe) {
        return http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class);
    }

    @CircuitBreaker(name = "cuentaCB", fallbackMethod = "fallbackCargar")
    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return http.post().uri("/cuentas/{clabe}/cargos", clabe).body(Map.of("monto", monto))
            .retrieve().body(CuentaRemota.class);
    }

    @CircuitBreaker(name = "cuentaCB", fallbackMethod = "fallbackAbonar")
    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return http.post().uri("/cuentas/{clabe}/abonos", clabe).body(Map.of("monto", monto))
            .retrieve().body(CuentaRemota.class);
    }

    // ==========================================
    // MÉTODOS DE EMERGENCIA (FALLBACKS)
    // ==========================================

    public CuentaRemota fallbackConsultar(String clabe, Throwable t) {
        // Truco: Detectamos si la falla fue porque el fusible ya saltó (CallNotPermittedException)
        if (t.getClass().getSimpleName().equals("CallNotPermittedException")) {
            throw new ReglaDeNegocioException("CORTACIRCUITOS ABIERTO (Fail-Fast): El servicio está caído. Rechazando petición inmediatamente para evitar cascada.");
        }
        // Si no, fue un error normal de conexión (fusible cerrado pero fallando)
        throw new ReglaDeNegocioException("Falla normal de red: Intentando contactar a " + clabe);
    }

    public CuentaRemota fallbackCargar(String clabe, BigDecimal monto, Throwable t) {
        throw new ReglaDeNegocioException("CORTACIRCUITOS ABIERTO: Imposible cargar a " + clabe);
    }

    public CuentaRemota fallbackAbonar(String clabe, BigDecimal monto, Throwable t) {
        throw new ReglaDeNegocioException("CORTACIRCUITOS ABIERTO: Imposible abonar a " + clabe);
    }
}