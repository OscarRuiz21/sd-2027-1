package mx.mexibanco.clientes;

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

    public CuentaRemota consultar(String clabe) {
        return http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class);
    }

    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return cargar(clabe, monto, null);
    }

    public CuentaRemota cargar(String clabe, BigDecimal monto, String operacionId) {
        var spec = http.post().uri("/cuentas/{clabe}/cargos", clabe);
        if (operacionId != null && !operacionId.isBlank()) {
            spec = spec.header("Idempotency-Key", operacionId);
        }
        return spec.body(Map.of("monto", monto)).retrieve().body(CuentaRemota.class);
    }

    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return abonar(clabe, monto, null);
    }

    public CuentaRemota abonar(String clabe, BigDecimal monto, String operacionId) {
        var spec = http.post().uri("/cuentas/{clabe}/abonos", clabe);
        if (operacionId != null && !operacionId.isBlank()) {
            spec = spec.header("Idempotency-Key", operacionId);
        }
        return spec.body(Map.of("monto", monto)).retrieve().body(CuentaRemota.class);
    }
}
