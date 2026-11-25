package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.FallaCuenta;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

import static mx.mexibanco.transferencia.EstadoTransferencia.*;

@Service
public class TransferenciaService {

	private static final Logger LOG = LoggerFactory.getLogger(TransferenciaService.class);

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
	private final String clabeDePrueba;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
								@Value("${FALLAR_ABONO_A_CLABE:}") String clabeDePrueba) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.clabeDePrueba = clabeDePrueba;
	}

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}
		Transferencia t = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto));
		try {
			cuentas.consultar(claveOrigen);
			cuentas.consultar(claveDestino);
		} catch (ErrorRemotoException | FallaCuenta error) {
			guardar(t, RECHAZADA, "La validación de las cuentas falló");
			throw error;
		}

		guardar(t, CARGO_EN_CURSO, "Se va a solicitar el cargo");
		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto);
		} catch (FallaCuenta error) {
			if (error.esResultadoIncierto()) {
				return guardar(t, CARGO_INCIERTO, "No se confirmó el cargo; requiere conciliación");
			}
			guardar(t, RECHAZADA, "El cargo no se envió");
			throw error;
		} catch (ErrorRemotoException error) {
			guardar(t, RECHAZADA, "Cuenta rechazó el cargo");
			throw error;
		}
		guardar(t, CARGADA, "Cargo confirmado");
		pausaDeDemo();

		if (claveDestino.equals(clabeDePrueba)) {
			return compensar(t, origen, "Falla de prueba antes del abono");
		}
		guardar(t, ABONO_EN_CURSO, "Se va a solicitar el abono");
		CuentaRemota destino;
		try {
			destino = cuentas.abonar(claveDestino, monto);
		} catch (FallaCuenta error) {
			if (error.esResultadoIncierto()) {
				return guardar(t, ABONO_INCIERTO, "No se confirmó el abono; requiere conciliación");
			}
			return compensar(t, origen, "El abono no se envió");
		} catch (ErrorRemotoException error) {
			return compensar(t, origen, "Cuenta rechazó el abono");
		}
		guardar(t, ABONADA, "Cargo y abono confirmados");
		MovimientoRemoto cargo;
		MovimientoRemoto abono;
		try {
			cargo = registrarCargo(t, origen);
			abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
				monto, destino.saldo(), "De " + origen.clabe() + " | Transferencia #" + t.getId());
		} catch (ErrorRemotoException | RestClientException | IllegalStateException error) {
			LOG.warn("Transferencia {}: error al registrar los movimientos", t.getId(), error);
			return guardar(t, ABONADA, "Los saldos ya cambiaron; revisar el registro de movimientos");
		}
		guardar(t, COMPLETADA, "Cargo y abono aplicados");
		avisar(t, cargo);
		avisar(t, abono);
		return t;
	}

	private Transferencia compensar(Transferencia t, CuentaRemota origen, String motivo) {
		guardar(t, REINTEGRO_EN_CURSO, motivo + "; se va a solicitar el reintegro");
		CuentaRemota reintegro;
		try {
			reintegro = cuentas.abonar(origen.clabe(), t.getMonto());
		} catch (FallaCuenta error) {
			if (error.esResultadoIncierto()) {
				return guardar(t, REINTEGRO_INCIERTO, motivo + "; reintegro por conciliar");
			}
			return guardar(t, COMPENSACION_PENDIENTE, motivo + "; el reintegro no se envió");
		} catch (ErrorRemotoException error) {
			return guardar(t, COMPENSACION_PENDIENTE, motivo + "; cuenta rechazó el reintegro");
		}
		guardar(t, REINTEGRO_CONFIRMADO, motivo + "; reintegro aplicado");
		try {
			registrarCargo(t, origen);
			movimientos.registrar(origen.clabe(), TipoMovimiento.DEPOSITO,
				t.getMonto(), reintegro.saldo(), "Reintegro transferencia #" + t.getId());
		} catch (ErrorRemotoException | RestClientException | IllegalStateException error) {
			LOG.warn("Transferencia {}: error al registrar la devolución", t.getId(), error);
			return guardar(t, REINTEGRO_CONFIRMADO, motivo + "; dinero devuelto, movimientos por revisar");
		}
		return guardar(t, COMPENSADA, motivo + "; se devolvió el monto a origen");
	}

	private MovimientoRemoto registrarCargo(Transferencia t, CuentaRemota origen) {
		return movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
			t.getMonto().negate(), origen.saldo(), "A " + t.getClaveDestino() + " | Transferencia #" + t.getId());
	}

	private void avisar(Transferencia t, MovimientoRemoto movimiento) {
		try {
			notificaciones.notificar(movimiento);
		} catch (ErrorRemotoException | RestClientException | IllegalStateException error) {
			LOG.warn("Transferencia {}: falló un aviso", t.getId(), error);
		}
	}

	// El orquestador no abre una transacción: cada save confirma el estado en su propia transacción.
	private Transferencia guardar(Transferencia t, EstadoTransferencia estado, String detalle) {
		t.actualizar(estado, detalle);
		transferencias.save(t);
		return t;
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
