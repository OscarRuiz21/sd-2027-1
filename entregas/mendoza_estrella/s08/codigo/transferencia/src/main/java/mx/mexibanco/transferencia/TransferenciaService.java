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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;


@Service
public class TransferenciaService {

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
	@Value("${FALLAR_ABONO_CLABE:}")
	private String fallarAbonoClabe;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
	}



public Transferencia transferir(
        String claveOrigen,
        String claveDestino,
        BigDecimal monto) {

    if (claveOrigen.equals(claveDestino)) {
        throw new ReglaDeNegocioException(
            "La cuenta origen y la destino son la misma"
        );
    }

    cuentas.consultar(claveOrigen);
    cuentas.consultar(claveDestino);

    Transferencia transferencia = transferencias.saveAndFlush(
        new Transferencia(claveOrigen, claveDestino, monto)
    );

    CuentaRemota origen = cuentas.cargar(claveOrigen, monto);

    transferencia.setEstado(Transferencia.Estado.CARGADA);
    transferencias.saveAndFlush(transferencia);

    pausaDeDemo();

    CuentaRemota destino;

    try {
        if (claveDestino.equals(fallarAbonoClabe)) {
            throw new IllegalStateException(
                "Falla simulada del abono"
            );
        }

        destino = cuentas.abonar(claveDestino, monto);

    } catch (RuntimeException falloAbono) {

        CuentaRemota origenRestituido;

        try {
            origenRestituido = cuentas.abonar(
                claveOrigen, monto
            );

        } catch (RuntimeException falloCompensacion) {

            transferencia.setEstado(
                Transferencia.Estado.COMPENSACION_PENDIENTE
            );
            transferencias.saveAndFlush(transferencia);

            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Transferencia " + transferencia.getId()
                    + ": compensacion pendiente",
                falloCompensacion
            );
        }

        transferencia.setEstado(
            Transferencia.Estado.COMPENSADA
        );
        transferencias.saveAndFlush(transferencia);

        movimientos.registrar(
            claveOrigen,
            TipoMovimiento.DEPOSITO,
            monto,
            origenRestituido.saldo(),
            "Compensacion de transferencia "
                + transferencia.getId()
        );

        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Transferencia " + transferencia.getId()
                + ": el abono fallo y se devolvio el dinero",
            falloAbono
        );
    }

    transferencia.setEstado(Transferencia.Estado.ABONADA);
    transferencias.saveAndFlush(transferencia);

    MovimientoRemoto cargo = movimientos.registrar(
        origen.clabe(),
        TipoMovimiento.TRANSFERENCIA_ENVIADA,
        monto.negate(),
        origen.saldo(),
        "a " + destino.clabe()
    );

    MovimientoRemoto abono = movimientos.registrar(
        destino.clabe(),
        TipoMovimiento.TRANSFERENCIA_RECIBIDA,
        monto,
        destino.saldo(),
        "de " + origen.clabe()
    );

    notificaciones.notificar(cargo);
    notificaciones.notificar(abono);

    transferencia.setEstado(
        Transferencia.Estado.COMPLETADA
    );
    transferencias.saveAndFlush(transferencia);

    return transferencia;
}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

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
