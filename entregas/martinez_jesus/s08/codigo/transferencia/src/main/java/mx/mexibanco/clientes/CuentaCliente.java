package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class CuentaCliente {

  private static final Logger log = LoggerFactory.getLogger(CuentaCliente.class);

	//Estado del circuit breaker con cuenta
	public enum EstadoCircuito {
    CERRADO, ABIERTO, SEMIABIERTO
  }
	private static final int UMBRAL_FALLOS = 3;
	private static final long TIEMPO_ESPERA_MS = 20000; // 20 segundos de espera en OPEN

  private final RestClient http;

	private EstadoCircuito estado = EstadoCircuito.CERRADO;
  private int fallasConsecutivas = 0;
  private long momentoApertura = 0;

  public CuentaCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.cuenta}") String urlBase) {
    this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
  }

  public CuentaRemota consultar(String clabe) {
    return ejecutarConCircuitBreaker("consultar(" + clabe + ")", () ->
      http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class)
    );
  }

  public CuentaRemota cargar(String clabe, BigDecimal monto) {
    return ejecutarConCircuitBreaker("cargar(" + clabe + ")", () ->
      http.post().uri("/cuentas/{clabe}/cargos", clabe).body(Map.of("monto", monto))
        .retrieve().body(CuentaRemota.class)
    );
  }

  public CuentaRemota abonar(String clabe, BigDecimal monto) {
    return ejecutarConCircuitBreaker("abonar(" + clabe + ")", () ->
      http.post().uri("/cuentas/{clabe}/abonos", clabe).body(Map.of("monto", monto))
        .retrieve().body(CuentaRemota.class)
    );
  }

  private synchronized <T> T ejecutarConCircuitBreaker(String operacion, Supplier<T> llamada) {
    if (estado == EstadoCircuito.ABIERTO) {
      long transcurrido = System.currentTimeMillis() - momentoApertura;
      if (transcurrido >= TIEMPO_ESPERA_MS) {
        estado = EstadoCircuito.SEMIABIERTO;
        log.warn("[CircuitBreaker] Tiempo de espera cumplido ({} ms). Estado: ABIERTO -> SEMIABIERTO. Probando {}",
          transcurrido, operacion);
      } else {
        log.warn("[CircuitBreaker] Estado: ABIERTO. Rechazo rapido en {} sin llamar a cuenta (faltan {} ms)",
          operacion, (TIEMPO_ESPERA_MS - transcurrido));
        throw new ReglaDeNegocioException(
          "Circuit Breaker ABIERTO: el servicio cuenta no esta disponible temporalmente");
      }
    } else {
      log.info("[CircuitBreaker] Estado: {}. Ejecutando {}", estado, operacion);
    }

    try {
      T resultado = llamada.get();
      if (estado == EstadoCircuito.SEMIABIERTO) {
        log.info("[CircuitBreaker] Llamada de prueba exitosa en {}. Estado: SEMIABIERTO -> CERRADO", operacion);
      }
      estado = EstadoCircuito.CERRADO;
      fallasConsecutivas = 0;
      return resultado;

    } catch (ErrorRemotoException ex) {
      // Errores 4xx de negocio (404 no existe, 422 saldo insuficiente) significan que cuenta SI respondio
      if (ex.getEstado() >= 400 && ex.getEstado() < 500) {
        throw ex;
      }
      registrarFalla(operacion, ex);
      throw ex;

    } catch (Exception ex) {
      registrarFalla(operacion, ex);
      throw ex;
    }
  }

  private void registrarFalla(String operacion, Exception ex) {
    fallasConsecutivas++;
    if (estado == EstadoCircuito.SEMIABIERTO) {
      estado = EstadoCircuito.ABIERTO;
      momentoApertura = System.currentTimeMillis();
      log.error("[CircuitBreaker] Fallo la llamada de prueba en {}. Estado: SEMIABIERTO -> ABIERTO", operacion);
    } else if (fallasConsecutivas >= UMBRAL_FALLOS) {
      estado = EstadoCircuito.ABIERTO;
      momentoApertura = System.currentTimeMillis();
      log.error("[CircuitBreaker] Fallo {}/{} en {}. Umbral alcanzado. Estado: CERRADO -> ABIERTO",
        fallasConsecutivas, UMBRAL_FALLOS, operacion);
    } else {
      log.warn("[CircuitBreaker] Fallo {}/{} en {} (Estado: CERRADO): {}",
        fallasConsecutivas, UMBRAL_FALLOS, operacion, ex.getMessage());
    }
  }
}