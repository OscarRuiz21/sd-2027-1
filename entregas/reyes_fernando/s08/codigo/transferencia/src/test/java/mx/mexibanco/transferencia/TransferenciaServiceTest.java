package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.ConnectException;

import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

	private static final String ORIGEN = "002180000000000001";
	private static final String DESTINO = "002180000000000002";
	private static final BigDecimal MONTO = new BigDecimal("200");

	@Mock TransferenciaRepository transferencias;
	@Mock CuentaCliente cuentas;
	@Mock MovimientoCliente movimientos;
	@Mock NotificacionCliente notificaciones;

	TransferenciaService servicio;

	@BeforeEach
	void armar() {
		servicio = new TransferenciaService(transferencias, cuentas, movimientos, notificaciones, 0);
		lenient().when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void transferirCargaAbonaRegistraDosAsientosYNotificaDos() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPLETADA);
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA),
			eq(MONTO.negate()), eq(new BigDecimal("800")), any());
		verify(movimientos).registrar(eq(DESTINO), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA),
			eq(MONTO), eq(new BigDecimal("700")), any());
		verify(notificaciones, times(2)).notificar(any());
	}

	@Test
	void transferirAUnaCuentaQueNoExisteNoCobraNada() {
		doReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")))
			.when(cuentas).consultar(ORIGEN);

		doThrow(new ErrorRemotoException("cuenta", 404, "No existe la CLABE " + DESTINO))
			.when(cuentas).consultar(DESTINO);

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(ErrorRemotoException.class)
			.extracting("estado").isEqualTo(404);
		verify(cuentas, never()).cargar(any(), any());
		verifyNoInteractions(movimientos, transferencias, notificaciones);
	}

	@Test
	void transferirALaMismaCuentaSeRechaza() {
		assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO))
			.isInstanceOf(ReglaDeNegocioException.class);
		verifyNoInteractions(cuentas, movimientos, transferencias, notificaciones);
	}

	@Test
	void siElAbonoSeRechazaLaSagaCompensa() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO))
			.thenThrow(new ErrorRemotoException("cuenta", 422, "Abono rechazado"));
		when(cuentas.abonar(ORIGEN, MONTO))
			.thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPENSADA);
		verify(cuentas).abonar(ORIGEN, MONTO);
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.DEPOSITO),
			eq(MONTO), eq(new BigDecimal("1000")), any());
		verifyNoInteractions(notificaciones);
	}

	@Test
	void siLaCompensacionFallaQuedaPendiente() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO))
			.thenThrow(new ErrorRemotoException("cuenta", 422, "Abono rechazado"));
		when(cuentas.abonar(ORIGEN, MONTO))
			.thenThrow(new ResourceAccessException("cuenta apagada", new ConnectException("refused")));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE);
		verify(cuentas).abonar(ORIGEN, MONTO);
		verify(movimientos, times(1)).registrar(any(), any(), any(), any(), any());
	}

	@Test
	void siAbonoDa503NoSeDevuelveDineroSinSaberSiLlego() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO))
			.thenThrow(new ErrorRemotoException("cuenta", 503, "Error remoto"));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.ABONO_INCIERTO);
		verify(cuentas, never()).abonar(ORIGEN, MONTO);
	}
}
