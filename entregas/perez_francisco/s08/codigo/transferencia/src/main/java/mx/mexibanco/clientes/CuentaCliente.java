
package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class CuentaCliente {

    private final RestClient http;

    // Estados del circuit breaker.
    private enum Estado {
        CERRADO, ABIERTO, SEMIABIERTO
    }

    // Comienza cerrado para permitir las peticiones.
    private Estado estado = Estado.CERRADO;

    // Cuenta las fallas consecutivas.
    private int fallas = 0;

    // Momento en que se abrio el circuito.
    private long tiempoApertura = 0;

    // Despues de 3 fallas, se abre durante 10 segundos.
    private static final int LIMITE_FALLAS = 3;
    private static final long ESPERA_MS = 10000;

    public CuentaCliente(
            RestClient.Builder builder,
            @Value("${mexibanco.servicios.cuenta}") String urlBase) {

        this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
    }

    // Controla todas las llamadas hacia el servicio cuenta.
    private synchronized <T> T ejecutar(Supplier<T> operacion) {

        // Si esta abierto, revisamos si ya paso el tiempo.
        if (estado == Estado.ABIERTO) {

            long transcurrido =
                System.currentTimeMillis() - tiempoApertura;

            if (transcurrido < ESPERA_MS) {
                System.out.println(
                    "CIRCUIT BREAKER ABIERTO: solicitud rechazada"
                );

                throw new IllegalStateException(
                    "Servicio cuenta no disponible. Circuit breaker abierto."
                );
            }

            // Despues de 10 segundos permite una prueba.
            estado = Estado.SEMIABIERTO;
            System.out.println("CIRCUIT BREAKER SEMIABIERTO");
        }

        try {

            // Ejecuta la peticion HTTP.
            T resultado = operacion.get();

            // Si funciona, reinicia el contador.
            fallas = 0;

            if (estado != Estado.CERRADO) {
                estado = Estado.CERRADO;
                System.out.println("CIRCUIT BREAKER CERRADO");
            }

            return resultado;

        } catch (Exception error) {

            // Registra las fallas de comunicacion.
            fallas++;
            System.out.println(
                "CIRCUIT BREAKER: falla " + fallas
            );

            // Abre el circuito al llegar al limite.
            if (fallas >= LIMITE_FALLAS
                    || estado == Estado.SEMIABIERTO) {

                estado = Estado.ABIERTO;
                tiempoApertura = System.currentTimeMillis();

                System.out.println("CIRCUIT BREAKER ABIERTO");
            }

            throw error;
        }
    }

    // Consulta una cuenta pasando por el circuit breaker.
    public CuentaRemota consultar(String clabe) {
        return ejecutar(() ->
            http.get()
                .uri("/cuentas/{clabe}", clabe)
                .retrieve()
                .body(CuentaRemota.class)
        );
    }

    // Realiza un cargo pasando por el circuit breaker.
    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return ejecutar(() ->
            http.post()
                .uri("/cuentas/{clabe}/cargos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class)
        );
    }

    // Realiza un abono pasando por el circuit breaker.
    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return ejecutar(() ->
            http.post()
                .uri("/cuentas/{clabe}/abonos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class)
        );
    }
}
