
package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class CuentaCliente {

    private final RestClient http;

    // Estados posibles del circuito.
    private enum Estado {
        CERRADO,
        ABIERTO,
        SEMIABIERTO
    }

    private Estado estado = Estado.CERRADO;

    // Abrir el circuito después de tres fallas.
    private final int maximoFallos = 3;

    // Esperar diez segundos antes de probar otra vez.
    private final long esperaMs = 10_000;

    private int fallos = 0;
    private long instanteApertura = 0;
    private boolean pruebaEnCurso = false;

    public CuentaCliente(
            RestClient.Builder builder,
            @Value("${mexibanco.servicios.cuenta}") String urlBase) {

        this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
    }

    // Decide si se permite llamar al servicio remoto.
    private synchronized void permitirLlamada() {

        if (estado == Estado.ABIERTO) {
            long tiempoTranscurrido =
                (System.nanoTime() - instanteApertura) / 1_000_000;

            if (tiempoTranscurrido >= esperaMs) {
                estado = Estado.SEMIABIERTO;
                pruebaEnCurso = false;
                System.out.println(
                    "[CIRCUITO] ABIERTO -> SEMIABIERTO");
            } else {
                System.out.println(
                    "[CIRCUITO] Llamada rechazada: circuito ABIERTO");

                throw new ErrorRemotoException(
                    "cuenta",
                    503,
                    "Servicio cuenta temporalmente no disponible: circuito abierto");
            }
        }

        if (estado == Estado.SEMIABIERTO) {
            if (pruebaEnCurso) {
                throw new ErrorRemotoException(
                    "cuenta",
                    503,
                    "Servicio cuenta en prueba de recuperacion");
            }

            pruebaEnCurso = true;
            System.out.println(
                "[CIRCUITO] Enviando llamada de prueba");
        }
    }

    // Se ejecuta cuando la llamada demuestra que el servicio responde.
    private synchronized void registrarExito() {

        if (estado == Estado.SEMIABIERTO) {
            estado = Estado.CERRADO;
            fallos = 0;
            pruebaEnCurso = false;

            System.out.println(
                "[CIRCUITO] SEMIABIERTO -> CERRADO");
        } else if (estado == Estado.CERRADO) {
            fallos = 0;
        }
    }

    // Se ejecuta cuando hay una falla de comunicacion o un error 5xx.
    private synchronized void registrarFallo() {

        fallos++;

        System.out.println(
            "[CIRCUITO] Falla consecutiva: " + fallos);

        if (estado == Estado.SEMIABIERTO
                || fallos >= maximoFallos) {

            estado = Estado.ABIERTO;
            instanteApertura = System.nanoTime();
            pruebaEnCurso = false;

            System.out.println(
                "[CIRCUITO] Circuito ABIERTO");
        }
    }

    // Ejecuta una llamada protegida por el circuito.
    private <T> T ejecutar(java.util.function.Supplier<T> llamada) {

        permitirLlamada();

        try {
            T resultado = llamada.get();
            registrarExito();
            return resultado;

        } catch (ErrorRemotoException error) {

            // Un 4xx normalmente es un error de negocio.
            // Un 5xx puede indicar un problema del servicio.
            if (error.getEstado() >= 500) {
                registrarFallo();
            } else {
                registrarExito();
            }

            throw error;

        } catch (RestClientException error) {

            // Incluye fallas de conexion y lectura, como timeouts.
            registrarFallo();
            throw error;

		} catch (IllegalStateException error) {
            registrarFallo();
            throw error;
        }
    }

    public CuentaRemota consultar(String clabe) {
        return ejecutar(() ->
            http.get()
                .uri("/cuentas/{clabe}", clabe)
                .retrieve()
                .body(CuentaRemota.class)
        );
    }

    public CuentaRemota cargar(String clabe, BigDecimal monto) {
        return ejecutar(() ->
            http.post()
                .uri("/cuentas/{clabe}/cargos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class)
        );
    }

    public CuentaRemota abonar(String clabe, BigDecimal monto) {
        return ejecutar(() ->
            http.post()
                .uri("/cuentas/{clabe}/abonos", clabe)
                .body(Map.of("monto", monto))
                .retrieve()
                .body(CuentaRemota.class)
        );
    }

    public CuentaRemota compensar(
            String clabe,
            BigDecimal monto,
            String claveOperacion) {

        return ejecutar(() ->
            http.post()
                .uri("/cuentas/{clabe}/compensaciones", clabe)
                .body(Map.of(
                    "monto", monto,
                    "claveOperacion", claveOperacion))
                .retrieve()
                .body(CuentaRemota.class)
        );
    }
}
