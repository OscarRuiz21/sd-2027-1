package mx.mexibanco.transferencia;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
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
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;

/** Orquesta los pasos de una transferencia y su compensacion, sin transaccion distribuida. */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;

	@Value("${FALLAR_ABONO_CLABE:}")
	private String clabeFallaAbono;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
			MovimientoCliente movimientos, NotificacionCliente notificaciones,
			@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
	}

	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}

		// Estas consultas no mueven dinero; si fallan, no iniciamos la saga.
		cuentas.consultar(claveOrigen);
		cuentas.consultar(claveDestino);

		Transferencia t = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto));
		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto);
		} catch (RuntimeException errorCargo) {
			// Una respuesta 4xx conocida rechaza el cargo; con timeout el resultado es incierto.
			guardarEstado(t, rechazoConfirmado(errorCargo)
				? EstadoTransferencia.FALLIDA : EstadoTransferencia.CARGO_INCIERTO);
			log.warn("Transferencia {}: no se confirmo el cargo: {}", t.getId(), errorCargo.toString());
			return t;
		}

		guardarEstado(t, EstadoTransferencia.CARGADA);

		MovimientoRemoto cargo;
		try {
			cargo = movimientos.registrar(origen.clabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
				monto.negate(), origen.saldo(), "a " + claveDestino + " (transferencia " + t.getId() + ")");
		} catch (RuntimeException errorMovimiento) {
			log.warn("Transferencia {}: fallo el registro del cargo; se intenta compensar", t.getId(), errorMovimiento);
			return compensar(t, claveOrigen, monto);
		}

		pausaDeDemo();

		CuentaRemota destino;
		try {
			// El abono se rechaza ANTES de llamar al servicio cuenta: falla de prueba conocida.
			if (claveDestino.equals(clabeFallaAbono)) {
				throw new ErrorRemotoException("cuenta", 422,
					"Falla simulada del abono a la CLABE " + claveDestino);
			}
			destino = cuentas.abonar(claveDestino, monto);
		} catch (RuntimeException errorAbono) {
			if (abonoNoEjecutado(errorAbono)) {
				log.warn("Transferencia {}: abono rechazado, se devuelve el cargo", t.getId());
				return compensar(t, claveOrigen, monto);
			}
			// Un timeout puede ocurrir DESPUES de aplicar el abono: no es seguro devolverlo.
			guardarEstado(t, EstadoTransferencia.ABONO_INCIERTO);
			log.warn("Transferencia {}: resultado del abono incierto; requiere verificacion", t.getId());
			return t;
		}

		guardarEstado(t, EstadoTransferencia.ABONADA);

		MovimientoRemoto abono;
		try {
			abono = movimientos.registrar(destino.clabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
				monto, destino.saldo(), "de " + origen.clabe() + " (transferencia " + t.getId() + ")");
		} catch (RuntimeException errorMovimiento) {
			// El dinero ya llego al destino; NO lo volvemos a mover.
			guardarEstado(t, EstadoTransferencia.REGISTRO_ABONO_PENDIENTE);
			log.warn("Transferencia {}: abono realizado, asiento pendiente de verificar", t.getId(), errorMovimiento);
			return t;
		}

		guardarEstado(t, EstadoTransferencia.COMPLETADA);
		try {
			notificaciones.notificar(cargo);
			notificaciones.notificar(abono);
		} catch (RuntimeException errorAviso) {
			// Un fallo de notificacion no debe volver a mover el dinero.
			log.warn("Transferencia {}: completada, pero hubo un error de notificacion", t.getId(), errorAviso);
		}
		return t;
	}

	private Transferencia compensar(Transferencia t, String claveOrigen, BigDecimal monto) {
		guardarEstado(t, EstadoTransferencia.COMPENSANDO);
		CuentaRemota origenDevuelto;
		try {
			origenDevuelto = cuentas.abonar(claveOrigen, monto);
		} catch (RuntimeException errorDevolucion) {
			// Sin confirmacion de devolucion, nunca afirmar que el dinero regreso.
			guardarEstado(t, EstadoTransferencia.COMPENSACION_PENDIENTE);
			log.warn("Transferencia {}: compensacion pendiente de resolver", t.getId(), errorDevolucion);
			return t;
		}

		try {
			movimientos.registrar(claveOrigen, TipoMovimiento.DEPOSITO, monto,
				origenDevuelto.saldo(), "Compensacion transferencia " + t.getId());
		} catch (RuntimeException errorMovimiento) {
			// Saldo devuelto, pero falta confirmar el asiento del historial.
			guardarEstado(t, EstadoTransferencia.REGISTRO_COMPENSACION_PENDIENTE);
			log.warn("Transferencia {}: dinero devuelto, registro pendiente", t.getId(), errorMovimiento);
			return t;
		}

		guardarEstado(t, EstadoTransferencia.COMPENSADA);
		log.info("Transferencia {} COMPENSADA: se devolvieron {} a {}", t.getId(), monto, claveOrigen);
		return t;
	}

	private void guardarEstado(Transferencia t, EstadoTransferencia estado) {
		t.cambiarEstado(estado);
		transferencias.save(t);
		log.info("Transferencia {} -> {}", t.getId(), estado);
	}

	private boolean rechazoConfirmado(RuntimeException error) {
		return error instanceof ErrorRemotoException remoto
			&& remoto.getEstado() >= 400 && remoto.getEstado() < 500 && remoto.getEstado() != 408;
	}

	private boolean abonoNoEjecutado(RuntimeException error) {
		if (rechazoConfirmado(error) || error instanceof CallNotPermittedException) {
			return true; // Fue rechazado o el circuito evito la llamada.
		}
		if (error instanceof ResourceAccessException) {
			for (Throwable causa = error; causa != null; causa = causa.getCause()) {
				if (causa instanceof ConnectException || causa instanceof NoRouteToHostException
						|| causa instanceof UnknownHostException) {
					return true; // No se pudo establecer la conexion.
				}
			}
		}
		return false;
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
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
