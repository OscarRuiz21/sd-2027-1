package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

@Component
public class CuentaCliente {

    private final RestClient http;
    private final CircuitoCuenta circuito;

    /**
     * Constructor usado por Spring.
     * Recibe el circuito compartido de la aplicacion.
     */
    @Autowired
    public CuentaCliente(
            RestClient.Builder builder,
            @Value("${mexibanco.servicios.cuenta}") String urlBase,
            CircuitoCuenta circuito) {

        this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
        this.circuito = circuito;
    }

    /**
     * Compatibilidad con los tests que construyen el cliente
     * directamente, sin iniciar Spring.
     * Tambien utiliza un circuito, propio de ese cliente.
     */
    public CuentaCliente(
            RestClient.Builder builder,
            String urlBase) {

        this(builder, urlBase, new CircuitoCuenta());
    }

    public CuentaRemota consultar(String clabe) {
        return circuito.ejecutar(
            "consultar",
            () -> Objects.requireNonNull(
                http.get()
                    .uri("/cuentas/{clabe}", clabe)
                    .retrieve()
                    .body(CuentaRemota.class),
                "Cuenta respondio sin cuerpo"
            )
        );
    }

    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return circuito.ejecutar(
            "cargar",
            () -> Objects.requireNonNull(
                http.post()
                    .uri("/cuentas/{clabe}/cargos", clabe)
                    .body(Map.of("monto", monto))
                    .retrieve()
                    .body(CuentaRemota.class),
                "Cargo respondio sin cuerpo"
            )
        );
    }

    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return circuito.ejecutar(
            "abonar",
            () -> Objects.requireNonNull(
                http.post()
                    .uri("/cuentas/{clabe}/abonos", clabe)
                    .body(Map.of("monto", monto))
                    .retrieve()
                    .body(CuentaRemota.class),
                "Abono respondio sin cuerpo"
            )
        );
    }
}