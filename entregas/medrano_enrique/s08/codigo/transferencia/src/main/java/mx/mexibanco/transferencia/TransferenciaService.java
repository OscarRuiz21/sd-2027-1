package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Transferencia INTERNA, entre dos cuentas del mismo banco. En el monolito (v05.1) este metodo era
 * UNA transaccion local: cargo, abono, asientos y avisos se confirmaban juntos o ninguno.
 *
 * En v06a cada paso es una peticion HTTP a otro servicio, con su propia base, y cada una se
 * confirma sola en cuanto responde. transferir() ya NO lleva @Transactional: no hay transaccion
 * que pueda abarcar tres procesos y cuatro bases. Si el cargo sale bien y el abono falla, el
 * dinero ya salio de origen y no llego a destino (ver README, "Lo que se rompio al partir").
 * Arreglarlo con una saga es tema de la S12; aqui se deja roto a proposito.
 */
@Service
public class TransferenciaService {

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
	private final String clabeFallaProvocada;

	// --- Circuit Breaker Manual (Puntos Extra) ---
    private enum EstadoCB { CLOSED, OPEN, HALF_OPEN }
    private EstadoCB cbEstado = EstadoCB.CLOSED;
    private int cbFallas = 0;
    private final int MAX_FALLAS = 3;
    private Instant cbProximoIntento = Instant.MIN;
    private final long CB_TIEMPO_ESPERA_MS = 10000; // 10 segundos de apertura


	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
								@Value("${mexibanco.demo.clabe-falla-provocada:}") String clabeFallaProvocada) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.clabeFallaProvocada = clabeFallaProvocada;
	}

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}

		// 1. INICIADA
        Transferencia transferencia = new Transferencia(claveOrigen, claveDestino, monto);
        transferencia.setEstado("INICIADA");
        transferencias.save(transferencia);

        ejecutarConCircuitBreaker(() -> cuentas.consultar(claveOrigen));
        ejecutarConCircuitBreaker(() -> cuentas.consultar(claveDestino));

        // 2. CARGADA
        CuentaRemota origen = ejecutarConCircuitBreaker(() -> cuentas.cargar(claveOrigen, monto));
        transferencia.setEstado("CARGADA");
        transferencias.save(transferencia);

        pausaDeDemo();

        CuentaRemota destino;
        try {
            // Falla provocada por variable de entorno (Punto 1.3)
            if (claveDestino.equals(clabeFallaProvocada)) {
                throw new RuntimeException("Falla simulada hacia la CLABE: " + clabeFallaProvocada);
            }
            destino = ejecutarConCircuitBreaker(() -> cuentas.abonar(claveDestino, monto));
        } catch (Exception fallaAbono) {
            // 3. ABONO FALLIDO -> Inicia Saga de Compensación
            return compensar(transferencia, origen, monto, fallaAbono);
        }

        // 4. ABONADA (Flujo Feliz)
        transferencia.setEstado("ABONADA");
        transferencias.save(transferencia);

		// Primero que existan las dos, luego el saldo: el mismo orden de errores que en el monolito.
		// Ojo: entre esta comprobacion y el cargo pasa tiempo de red; ya no es una foto consistente.
		MovimientoRemoto cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
			monto.negate(), origen.saldo(), "a " + destino.clabe());
		MovimientoRemoto abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
			monto, destino.saldo(), "de " + origen.clabe());

		notificaciones.notificar(cargo);
		notificaciones.notificar(abono);

		// 5. COMPLETADA
        transferencia.setEstado("COMPLETADA");
        transferencias.save(transferencia);

		return transferencia;
	}

	private Transferencia compensar(Transferencia t, CuentaRemota origen, BigDecimal monto, Exception e) {
        t.setEstado("COMPENSACION_PENDIENTE");
        transferencias.save(t);

        try {
            ejecutarConCircuitBreaker(() -> cuentas.abonar(origen.clabe(), monto));
            
            movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA, 
                monto, origen.saldo().add(monto), "Reverso por falla en abono. Error: " + e.getMessage());

            t.setEstado("COMPENSADA");
            transferencias.save(t);

            // Respuesta al cliente
            throw new ReglaDeNegocioException("Falla al abonar al destino. Tu dinero regresó a tu cuenta. Detalle: " + e.getMessage());
        } catch (Exception fallaCompensacion) {
            // Si la compensación también falla, se queda en PENDIENTE
            throw new RuntimeException("CRÍTICO: Falló la compensación. La transferencia quedó en COMPENSACION_PENDIENTE", fallaCompensacion);
        }
    }

    private <T> T ejecutarConCircuitBreaker(Supplier<T> peticionHttp) {
        if (cbEstado == EstadoCB.OPEN) {
            if (Instant.now().isBefore(cbProximoIntento)) {
                throw new RuntimeException("Circuit Breaker ABIERTO. Rechazo rápido.");
            }
            cbEstado = EstadoCB.HALF_OPEN;
            System.out.println("CircuitBreaker: Intentando recuperar...");
        }

        try {
            T resultado = peticionHttp.get();
            if (cbEstado == EstadoCB.HALF_OPEN) {
                cbEstado = EstadoCB.CLOSED;
                cbFallas = 0;
                System.out.println("CircuitBreaker: CLOSED - Recuperado.");
            }
            return resultado;
        } catch (Exception e) {
            cbFallas++;
            System.out.println("CircuitBreaker: FALLA " + cbFallas + "/" + MAX_FALLAS);
            if (cbFallas >= MAX_FALLAS) {
                cbEstado = EstadoCB.OPEN;
                cbProximoIntento = Instant.now().plusMillis(CB_TIEMPO_ESPERA_MS);
                System.out.println("CircuitBreaker: 10s.");
            }
            throw e;
        }
    }

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	/**
	 * Solo para la demo en clase: abre una ventana entre el cargo y el abono para apagar cuenta a
	 * mano y ver el dinero "en el aire". Con PAUSA_ENTRE_CARGO_Y_ABONO_MS=0 (el valor normal) no hace nada.
	 */
	private void pausaDeDemo() {
		if (pausaEntreCargoYAbonoMs <= 0) {
			return;
		}
		try {
			Thread.sleep(pausaEntreCargoYAbonoMs);
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
		}
	}
}
