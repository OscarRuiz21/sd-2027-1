package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.resiliencia.CircuitoCuenta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.Map;

@Component
public class CuentaCliente {
    private final RestClient http;
    private final CircuitoCuenta circuito;
    private final String fallarAbonoClabe;

    public CuentaCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.cuenta}") String url,
            CircuitoCuenta circuito, @Value("${mexibanco.demo.fallar-abono-clabe:}") String fallarAbonoClabe) {
        this.http = ClientesHttp.crear(builder, url, "cuenta");
        this.circuito = circuito;
        this.fallarAbonoClabe = fallarAbonoClabe;
    }

    public CuentaRemota consultar(String clabe) {
        return circuito.llamar(() -> http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class));
    }

    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return circuito.llamar(() -> http.post().uri("/cuentas/{clabe}/cargos", clabe)
            .body(Map.of("monto", monto)).retrieve().body(CuentaRemota.class));
    }

    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return circuito.llamar(() -> {
            if (!fallarAbonoClabe.isBlank() && fallarAbonoClabe.equals(clabe))
                throw new ErrorRemotoException("cuenta", 422, "Falla simulada: abono rechazado ANTES de enviarlo a cuenta");
            return http.post().uri("/cuentas/{clabe}/abonos", clabe)
                .body(Map.of("monto", monto)).retrieve().body(CuentaRemota.class);
        });
    }
}
