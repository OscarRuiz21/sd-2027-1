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
	private final String clabeDestinoFalloDemo;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
								@Value("${mexibanco.demo.clabe-destino-fallo-abono:}") String clabeDestinoFalloDemo) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.clabeDestinoFalloDemo = clabeDestinoFalloDemo;
	}

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
    if (claveOrigen.equals(claveDestino)) {
        throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
    }

    cuentas.consultar(claveOrigen);
    cuentas.consultar(claveDestino);

    Transferencia transferencia = transferencias.save(
        new Transferencia(claveOrigen, claveDestino, monto)
    );

    CuentaRemota origen = cuentas.cargar(claveOrigen, monto);
    transferencia.setEstado(EstadoTransferencia.CARGADA);
    transferencias.save(transferencia);

    pausaDeDemo();

    CuentaRemota destino;
    try {
        simularFalloAbono(claveDestino);
        destino = cuentas.abonar(claveDestino, monto);
    } catch (Exception errorAbono) {
        transferencia.setEstado(EstadoTransferencia.COMPENSACION_PENDIENTE);
        transferencias.save(transferencia);

        try {
            CuentaRemota devuelta = cuentas.abonar(claveOrigen, monto);
            transferencia.setEstado(EstadoTransferencia.COMPENSADA);
            transferencias.save(transferencia);

            try {
                movimientos.registrar(
                    devuelta.clabe(),
                    TipoMovimiento.COMPENSACION_TRANSFERENCIA,
                    monto,
                    devuelta.saldo(),
                    "Compensacion de transferencia " + transferencia.getId()
                );
            } catch (Exception errorMovimiento) {
                throw new IllegalStateException(
                    "Dinero devuelto, pero fallo el registro del movimiento. Transferencia "
                    + transferencia.getId(), errorMovimiento
                );
            }

            throw new ReglaDeNegocioException(
                "Transferencia " + transferencia.getId()
                + " compensada: dinero devuelto a origen"
            );
        } catch (ReglaDeNegocioException resultado) {
            throw resultado;
        } catch (IllegalStateException errorRegistro) {
            throw errorRegistro;
        } catch (Exception errorCompensacion) {
            throw new IllegalStateException(
                "Transferencia " + transferencia.getId()
                + ": compensacion pendiente de revision", errorCompensacion
            );
        }
    }

    transferencia.setEstado(EstadoTransferencia.ABONADA);
    transferencias.save(transferencia);

    MovimientoRemoto cargo = movimientos.registrar(
        origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
        monto.negate(), origen.saldo(), "a " + destino.clabe()
    );

    MovimientoRemoto abono = movimientos.registrar(
        destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
        monto, destino.saldo(), "de " + origen.clabe()
    );

    transferencia.setEstado(EstadoTransferencia.COMPLETADA);
    transferencias.save(transferencia);

    try {
        notificaciones.notificar(cargo);
        notificaciones.notificar(abono);
    } catch (Exception errorNotificacion) {
        System.err.println("Transferencia completada, pero fallo una notificacion: "
                + errorNotificacion.getMessage());
    }

    return transferencia;
}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	private void simularFalloAbono(String claveDestino) {
		if (!clabeDestinoFalloDemo.isBlank()
				&& clabeDestinoFalloDemo.equals(claveDestino)) {
			throw new IllegalStateException(
				"Falla simulada en el abono a la cuenta destino"
			);
		}
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
