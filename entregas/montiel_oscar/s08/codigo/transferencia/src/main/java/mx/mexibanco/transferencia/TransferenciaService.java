package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.CircuitoAbiertoException;
import mx.mexibanco.clientes.CuentaSinInstanciasException;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Saga orquestada: cada cambio de estado se confirma en la base local ANTES de la siguiente
 * llamada HTTP. No hay @Transactional en transferir(): una transaccion local larga no deshace
 * cargos ya confirmados en cuenta y ocultaria el progreso a quien consulte la transferencia.
 */
@Service
public class TransferenciaService {
	private static final Logger LOG = LoggerFactory.getLogger(TransferenciaService.class);

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
	private final String clabeParaFallarAbono;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
			MovimientoCliente movimientos, NotificacionCliente notificaciones,
			@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
			@Value("${mexibanco.demo.fallar-abono-clabe:}") String clabeParaFallarAbono) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.clabeParaFallarAbono = clabeParaFallarAbono;
	}

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}
		Transferencia t = transferencias.saveAndFlush(new Transferencia(claveOrigen, claveDestino, monto));

		try {
			cuentas.consultar(claveOrigen);
			cuentas.consultar(claveDestino);
		} catch (RuntimeException error) {
			avanzar(t, EstadoTransferencia.RECHAZADA, "No se pudo validar las cuentas: " + mensaje(error));
			throw error;
		}

		avanzar(t, EstadoTransferencia.CARGO_EN_CURSO, null);
		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto);
		} catch (RuntimeException error) {
			// Un timeout del POST deja el resultado incierto: NO repetimos un cargo que quiza ocurrio.
			EstadoTransferencia estado = error instanceof mx.mexibanco.compartido.ErrorRemotoException remoto
					&& remoto.getEstado() < 500 ? EstadoTransferencia.RECHAZADA : EstadoTransferencia.CARGO_INCIERTO;
			avanzar(t, estado, "Cargo no confirmado: " + mensaje(error));
			throw error;
		}
		avanzar(t, EstadoTransferencia.CARGADA, "Cargo confirmado en cuenta origen");

		MovimientoRemoto cargo;
		try {
			cargo = movimientos.registrar(claveOrigen, TipoMovimiento.TRANSFERENCIA_ENVIADA,
					monto.negate(), origen.saldo(), "transferencia #" + t.getId() + " a " + claveDestino);
		} catch (RuntimeException error) {
			return compensar(t, "Fallo el asiento del cargo: " + mensaje(error));
		}

		pausaDeDemo();
		avanzar(t, EstadoTransferencia.ABONO_EN_CURSO, null);
		CuentaRemota destino;
		try {
			if (claveDestino.equals(clabeParaFallarAbono)) {
				throw new IllegalStateException("Falla simulada del abono para la CLABE " + claveDestino);
			}
			destino = cuentas.abonar(claveDestino, monto);
		} catch (RuntimeException error) {
			return compensar(t, "Fallo el abono al destino: " + mensaje(error));
		}
		avanzar(t, EstadoTransferencia.ABONADA, "Abono confirmado en cuenta destino");

		try {
			MovimientoRemoto abono = movimientos.registrar(claveDestino, TipoMovimiento.TRANSFERENCIA_RECIBIDA,
					monto, destino.saldo(), "transferencia #" + t.getId() + " de " + claveOrigen);
			avanzar(t, EstadoTransferencia.COMPLETADA, "Cargo y abono confirmados");
			avisarSinAfectarDinero(cargo);
			avisarSinAfectarDinero(abono);
		} catch (RuntimeException error) {
			// El dinero YA llego. Devolverlo ahora duplicaria dinero; solo falta conciliar el asiento.
			avanzar(t, EstadoTransferencia.REGISTRO_PENDIENTE,
					"Abono hecho, pero falta conciliar el movimiento: " + mensaje(error));
		}
		return t;
	}

	private Transferencia compensar(Transferencia t, String motivo) {
		avanzar(t, EstadoTransferencia.COMPENSACION_EN_CURSO, motivo);
		CuentaRemota origenRestaurado;
		try {
			origenRestaurado = devolverConReintentoSeguro(t);
		} catch (RuntimeException error) {
			avanzar(t, EstadoTransferencia.COMPENSACION_PENDIENTE,
					motivo + "; devolucion NO confirmada: " + mensaje(error));
			LOG.warn("Saga {}: compensacion pendiente; requiere conciliacion antes de reintentar", t.getId(), error);
			return t;
		}
		try {
			MovimientoRemoto devolucion = movimientos.registrar(t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
					t.getMonto(), origenRestaurado.saldo(), "COMPENSACION transferencia #" + t.getId());
			avanzar(t, EstadoTransferencia.COMPENSADA, motivo + "; monto devuelto al origen");
			avisarSinAfectarDinero(devolucion);
		} catch (RuntimeException error) {
			avanzar(t, EstadoTransferencia.REGISTRO_PENDIENTE,
					motivo + "; monto devuelto, pero falta asiento de compensacion: " + mensaje(error));
		}
		return t;
	}

	/**
	 * Solo reintenta fallas que demuestran que el POST NO se envio. Un timeout o un 5xx pueden
	 * ocultar un abono ya confirmado; repetirlos sin Idempotency-Key duplicaria la devolucion.
	 */
	private CuentaRemota devolverConReintentoSeguro(Transferencia t) {
		long esperaMs = 250;
		for (int intento = 1; intento <= 3; intento++) {
			try {
				return cuentas.abonar(t.getClaveOrigen(), t.getMonto());
			} catch (CircuitoAbiertoException | CuentaSinInstanciasException sinEnvio) {
				if (intento == 3) throw sinEnvio;
				LOG.warn("Saga {}: devolucion no enviada, reintento {}/3 en {} ms", t.getId(), intento + 1, esperaMs);
				try {
					Thread.sleep(esperaMs);
				} catch (InterruptedException interrumpido) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Reintento de devolucion interrumpido", interrumpido);
				}
				esperaMs *= 2;
			}
		}
		throw new IllegalStateException("Reintentos agotados");
	}

	private void avisarSinAfectarDinero(MovimientoRemoto movimiento) {
		try {
			notificaciones.notificar(movimiento);
		} catch (RuntimeException error) {
			LOG.warn("Aviso no entregado; la transferencia monetaria no se revierte", error);
		}
	}

	private void avanzar(Transferencia t, EstadoTransferencia estado, String detalle) {
		t.avanzar(estado, detalle);
		transferencias.saveAndFlush(t);
		LOG.info("SAGA transferencia={} estado={} detalle={}", t.getId(), estado, detalle);
	}

	private String mensaje(RuntimeException error) {
		String texto = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
		return texto.length() > 160 ? texto.substring(0, 160) : texto;
	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	private void pausaDeDemo() {
		if (pausaEntreCargoYAbonoMs <= 0) return;
		try {
			Thread.sleep(pausaEntreCargoYAbonoMs);
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
		}
	}
}
