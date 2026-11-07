package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.CompensacionPendienteException;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.compartido.TransferenciaCompensadaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Las mismas tres pruebas del monolito, ahora con dobles de los CLIENTES HTTP en lugar de los
 * servicios de otros modulos. Las dos ultimas (S08) comprueban la compensacion de la saga.
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

	@Test
	void transferirCargaAbonaRegistraDosAsientosYNotificaDos() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));
		when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getMonto()).isEqualByComparingTo(MONTO);
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA), eq(MONTO.negate()), eq(new BigDecimal("800")), any());
		verify(movimientos).registrar(eq(DESTINO), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA), eq(MONTO), eq(new BigDecimal("700")), any());
		verify(notificaciones, times(2)).notificar(any());
	}

	@Test
	void transferirAUnaCuentaQueNoExisteNoCobraNada() {
		when(cuentas.consultar(ORIGEN)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		when(cuentas.consultar(DESTINO)).thenThrow(new ErrorRemotoException("cuenta", 404, "No existe la CLABE " + DESTINO));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(ErrorRemotoException.class)
			.extracting("estado").isEqualTo(404);
		verify(cuentas, never()).cargar(any(), any());
		verifyNoInteractions(movimientos, transferencias, notificaciones);
	}

	@Test
	void transferirALaMismaCuentaSeRechaza() {
		assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO)).isInstanceOf(ReglaDeNegocioException.class);
		verifyNoInteractions(cuentas, movimientos, transferencias, notificaciones);
	}

	/** La saga (S08): si el abono falla, se le devuelve el cargo al origen y la transferencia queda COMPENSADA. */
	@Test
	void siElAbonoFallaSeDevuelveElCargo() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
		when(cuentas.abonar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(TransferenciaCompensadaException.class);
		verify(cuentas).abonar(ORIGEN, MONTO); // la devolucion
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.DEPOSITO), eq(MONTO), eq(new BigDecimal("1000")), any());
		verify(notificaciones, never()).notificar(any());
	}

	/** Si la devolucion tambien falla, la transferencia no se queda en CARGADA: queda pendiente. */
	@Test
	void siLaDevolucionTambienFallaQuedaPendiente() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(any(), eq(MONTO))).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
		when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(CompensacionPendienteException.class);
		verify(transferencias, atLeastOnce()).save(argThat(t -> t.getEstado() == EstadoTransferencia.COMPENSACION_PENDIENTE));
		verifyNoInteractions(movimientos, notificaciones);
	}
}
