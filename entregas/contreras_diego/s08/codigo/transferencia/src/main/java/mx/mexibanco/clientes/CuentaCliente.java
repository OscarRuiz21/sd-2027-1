package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Llamadas HTTP al servicio cuenta, todas protegidas por un circuit breaker: si cuenta esta caida,
 * tras varias fallas el circuito se abre y las llamadas fallan al instante sin tocar la red.
 */
@Component
public class CuentaCliente {

        private final RestClient http;
        private final CircuitBreakerManual breaker;

        @Autowired
        public CuentaCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.cuenta}") String urlBase,
                             CircuitBreakerManual breaker) {
                this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
                this.breaker = breaker;
        }

        /** Sin proteccion real (umbral inalcanzable): para pruebas que no tratan el circuito. */
        public CuentaCliente(RestClient.Builder builder, String urlBase) {
                this(builder, urlBase, new CircuitBreakerManual("cuenta", Integer.MAX_VALUE,
                        Duration.ofSeconds(1), 1, e -> true));
        }

        public CuentaRemota consultar(String clabe) {
                return breaker.ejecutar(() ->
                        http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class));
        }

        public CuentaRemota cargar(String clabe, BigDecimal monto) {
                return breaker.ejecutar(() ->
                        http.post().uri("/cuentas/{clabe}/cargos", clabe).body(Map.of("monto", monto))
                                .retrieve().body(CuentaRemota.class));
        }

        public CuentaRemota abonar(String clabe, BigDecimal monto) {
                return breaker.ejecutar(() ->
                        http.post().uri("/cuentas/{clabe}/abonos", clabe).body(Map.of("monto", monto))
                                .retrieve().body(CuentaRemota.class));
        }
}
