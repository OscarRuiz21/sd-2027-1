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

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
	}

	/**
     * Coordina la transferencia como una saga orquestada.
     * Cada cambio de estado se guarda para conocer el avance de la operacion.
     * Si falla el abono, intenta devolver el dinero a la cuenta de origen.
     */
    public Transferencia transferir(
            String claveOrigen,
            String claveDestino,
            BigDecimal monto) {

        if (claveOrigen.equals(claveDestino)) {
            throw new ReglaDeNegocioException(
                "La cuenta origen y la destino son la misma");
        }

        cuentas.consultar(claveOrigen);
        cuentas.consultar(claveDestino);

        // Guardar la transferencia antes de mover dinero.
        Transferencia transferencia = transferencias.save(
            new Transferencia(claveOrigen, claveDestino, monto));

        // Primer paso: retirar el dinero de la cuenta origen.
        CuentaRemota origen = cuentas.cargar(claveOrigen, monto);

        transferencia.cambiarEstado(EstadoTransferencia.CARGADA);
        transferencias.save(transferencia);

        pausaDeDemo();

        // Segundo paso: intentar depositar el dinero en destino.
        CuentaRemota destino;

        try {
            destino = cuentas.abonar(claveDestino, monto);
        } catch (RuntimeException errorAbono) {

            // Si falla el abono, intentar devolver el dinero al origen.
            try {
                CuentaRemota origenCompensado = cuentas.compensar(
                    claveOrigen,
                    monto,
                    "transferencia-" + transferencia.getId() + "-compensacion");

                movimientos.registrar(
                    origenCompensado.clabe(),
                    TipoMovimiento.COMPENSACION,
                    monto,
                    origenCompensado.saldo(),
                    "Compensacion de transferencia " + transferencia.getId());

                transferencia.cambiarEstado(EstadoTransferencia.COMPENSADA);

            } catch (RuntimeException errorCompensacion) {

                // Se necesita revisar y reintentar esta compensacion.
                transferencia.cambiarEstado(
                    EstadoTransferencia.COMPENSACION_PENDIENTE);
            }

            return transferencias.save(transferencia);
        }

        // Tercer paso: el abono se completó.
        transferencia.cambiarEstado(EstadoTransferencia.ABONADA);
        transferencias.save(transferencia);

        MovimientoRemoto cargo = movimientos.registrar(
            origen.clabe(),
            TipoMovimiento.TRANSFERENCIA_ENVIADA,
            monto.negate(),
            origen.saldo(),
            "a " + destino.clabe());

        MovimientoRemoto abono = movimientos.registrar(
            destino.clabe(),
            TipoMovimiento.TRANSFERENCIA_RECIBIDA,
            monto,
            destino.saldo(),
            "de " + origen.clabe());

        notificaciones.notificar(cargo);
        notificaciones.notificar(abono);

        // La saga termina correctamente.
        transferencia.cambiarEstado(EstadoTransferencia.COMPLETADA);

        return transferencias.save(transferencia);
    }

		/**
 * Reintenta devolver el dinero cuando una compensacion quedo pendiente.
 * Se utiliza la misma clave de operacion para evitar devolver dos veces.
 */
public Transferencia reintentarCompensacion(Long id) {
    Transferencia transferencia = transferencias.findById(id)
        .orElseThrow(() ->
            new NoEncontradoException(
                "No existe la transferencia " + id));

    if (transferencia.getEstado()
            != EstadoTransferencia.COMPENSACION_PENDIENTE) {
        throw new ReglaDeNegocioException(
            "La transferencia " + id
            + " no tiene una compensacion pendiente");
    }

    try {
        CuentaRemota origenCompensado = cuentas.compensar(
            transferencia.getClaveOrigen(),
            transferencia.getMonto(),
            "transferencia-" + transferencia.getId()
                + "-compensacion");

        movimientos.registrar(
            origenCompensado.clabe(),
            TipoMovimiento.COMPENSACION,
            transferencia.getMonto(),
            origenCompensado.saldo(),
            "Compensacion de transferencia "
                + transferencia.getId());

        transferencia.cambiarEstado(EstadoTransferencia.COMPENSADA);

    } catch (RuntimeException errorCompensacion) {
        // Si el intento falla, se conserva el estado pendiente.
        transferencia.cambiarEstado(
            EstadoTransferencia.COMPENSACION_PENDIENTE);
    }

    return transferencias.save(transferencia);
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
