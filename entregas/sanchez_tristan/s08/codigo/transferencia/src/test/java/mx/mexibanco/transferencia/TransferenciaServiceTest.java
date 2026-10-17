package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.compartido.TransferenciaCompensadaException;
import mx.mexibanco.compartido.TransferenciaFallidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de la Saga Orquestada de Transferencia (S08).
 * Valida el flujo normal, la compensacion automatica y el manejo de fallos en la compensacion.
 */
@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

	private static final String ORIGEN = "002180000000000001";
	private static final String DESTINO = "002180000000000002";
	private static final BigDecimal MONTO = new BigDecimal("200");

	@Mock
	TransferenciaRepository transferencias;

	@Mock
	CuentaCliente cuentas;

	@Mock
	MovimientoCliente movimientos;

	@Mock
	NotificacionCliente notificaciones;

	TransferenciaService servicio;

	@BeforeEach
	void armar() {
		servicio = new TransferenciaService(transferencias, cuentas, movimientos, notificaciones, 0, "");
	}

	private static MovimientoRemoto dummyMovimiento() {
		return new MovimientoRemoto(1L, ORIGEN, TipoMovimiento.TRANSFERENCIA_ENVIADA, MONTO, new BigDecimal("800"), java.time.Instant.now(), "ref");
	}

	@Test
	void transferirExitosoCompletaLaSagaYActualizaEstados() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(dummyMovimiento());
		when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getMonto()).isEqualByComparingTo(MONTO);
		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPLETADA);

		verify(cuentas).cargar(ORIGEN, MONTO);
		verify(cuentas).abonar(DESTINO, MONTO);
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA), eq(MONTO.negate()), eq(new BigDecimal("800")), any());
		verify(movimientos).registrar(eq(DESTINO), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA), eq(MONTO), eq(new BigDecimal("700")), any());
		verify(notificaciones, times(2)).notificar(any());
	}

	@Test
	void transferirAUnaCuentaQueNoExisteRegistraFallidaYNoCobra() {
		when(cuentas.consultar(ORIGEN)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		when(cuentas.consultar(DESTINO)).thenThrow(new ErrorRemotoException("cuenta", 404, "No existe la CLABE " + DESTINO));
		when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(ErrorRemotoException.class)
			.extracting("estado").isEqualTo(404);

		verify(cuentas, never()).cargar(any(), any());
		verify(transferencias, atLeastOnce()).save(argThat(t -> t.getEstado() == EstadoTransferencia.FALLIDA));
		verifyNoInteractions(movimientos, notificaciones);
	}

	@Test
	void transferirALaMismaCuentaSeRechaza() {
		assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO)).isInstanceOf(ReglaDeNegocioException.class);
		verifyNoInteractions(cuentas, movimientos, transferencias, notificaciones);
	}

	@Test
	void siElAbonoFallaLaSagaCompensaDevolviendoElDineroAOrigen() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
		// La compensacion reembolsa a la cuenta origen:
		when(cuentas.abonar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(dummyMovimiento());
		when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(TransferenciaCompensadaException.class)
			.hasMessageContaining("Saga compensada");

		// Verifica que se cargo al origen y se intento abonar al destino
		verify(cuentas).cargar(ORIGEN, MONTO);
		verify(cuentas).abonar(DESTINO, MONTO);
		// Y crucial: la compensacion abono de vuelta a la cuenta origen
		verify(cuentas).abonar(ORIGEN, MONTO);
		// Se registro el movimiento de compensacion en el ledger
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.DEPOSITO), eq(MONTO), eq(new BigDecimal("1000")), any());

		// Se guardo en estado COMPENSADA
		ArgumentCaptor<Transferencia> captor = ArgumentCaptor.forClass(Transferencia.class);
		verify(transferencias, atLeastOnce()).save(captor.capture());
		assertThat(captor.getValue().getEstado()).isEqualTo(EstadoTransferencia.COMPENSADA);
	}

	@Test
	void siLaCompensacionTambienFallaQuedaEnCompensacionPendiente() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
		// La devolucion al origen tambien falla (cuenta sigue caida):
		when(cuentas.abonar(ORIGEN, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta sigue caida"));
		when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(TransferenciaFallidaException.class)
			.hasMessageContaining("COMPENSACION_PENDIENTE");

		// Verifica que intento la compensacion
		verify(cuentas, atLeastOnce()).abonar(ORIGEN, MONTO);

		// Se guardo en estado COMPENSACION_PENDIENTE
		ArgumentCaptor<Transferencia> captor = ArgumentCaptor.forClass(Transferencia.class);
		verify(transferencias, atLeastOnce()).save(captor.capture());
		assertThat(captor.getValue().getEstado()).isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE);
	}
}
