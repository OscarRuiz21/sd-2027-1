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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Las mismas tres pruebas del monolito, ahora con dobles de los CLIENTES HTTP en lugar de los
 * servicios de otros modulos. La cuarta es nueva y documenta lo que se rompio al partir.
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


 @Test void siElAbonoFallaDevuelveElCargo() {
  when(transferencias.save(any())).thenAnswer(i -> i.getArgument(0));
  when(cuentas.cargar(ORIGEN,MONTO)).thenReturn(new CuentaRemota(ORIGEN,"Ana",new BigDecimal("800")));
  when(cuentas.abonar(DESTINO,MONTO)).thenThrow(new org.springframework.web.client.ResourceAccessException("sin conexión",new java.net.ConnectException()));
  when(cuentas.abonar(ORIGEN,MONTO)).thenReturn(new CuentaRemota(ORIGEN,"Ana",new BigDecimal("1000")));
  assertThat(servicio.transferir(ORIGEN,DESTINO,MONTO).getEstado()).isEqualTo("COMPENSADA");
  verify(cuentas,times(1)).abonar(ORIGEN,MONTO);
  verifyNoInteractions(notificaciones);
 }
 @Test void siLaDevolucionFallaQuedaPendienteSinRepetirla() {
  when(transferencias.save(any())).thenAnswer(i -> i.getArgument(0));
  when(cuentas.cargar(ORIGEN,MONTO)).thenReturn(new CuentaRemota(ORIGEN,"Ana",new BigDecimal("800")));
  when(cuentas.abonar(any(),eq(MONTO))).thenThrow(new org.springframework.web.client.ResourceAccessException("apagada",new java.net.ConnectException()));
  assertThat(servicio.transferir(ORIGEN,DESTINO,MONTO).getEstado()).isEqualTo("COMPENSACION_PENDIENTE");
  verify(cuentas,times(1)).abonar(ORIGEN,MONTO);
 }
 @Test void sinInstanciaNoSeEnvioHttpYLaDevolucionQuedaPendiente() {
  when(transferencias.save(any())).thenAnswer(i -> i.getArgument(0));
  when(cuentas.cargar(ORIGEN,MONTO)).thenReturn(new CuentaRemota(ORIGEN,"Ana",new BigDecimal("800")));
  when(cuentas.abonar(any(),eq(MONTO))).thenThrow(new IllegalStateException("No instances available for cuenta"));
  assertThat(servicio.transferir(ORIGEN,DESTINO,MONTO).getEstado()).isEqualTo("COMPENSACION_PENDIENTE");
  verify(cuentas,times(1)).abonar(ORIGEN,MONTO);
 }
 @Test void unTimeoutNoPermiteDevolverSinConciliarElAbono() {
  when(transferencias.save(any())).thenAnswer(i -> i.getArgument(0));
  when(cuentas.cargar(ORIGEN,MONTO)).thenReturn(new CuentaRemota(ORIGEN,"Ana",new BigDecimal("800")));
  when(cuentas.abonar(DESTINO,MONTO)).thenThrow(new org.springframework.web.client.ResourceAccessException("respuesta perdida",new java.net.SocketTimeoutException()));
  assertThat(servicio.transferir(ORIGEN,DESTINO,MONTO).getEstado()).isEqualTo("ABONO_INCIERTO");
  verify(cuentas,never()).abonar(ORIGEN,MONTO);
 }
 @Test void unCincoCientosGenericoTampocoPruebaQueNoSeAbono() {
  when(transferencias.save(any())).thenAnswer(i -> i.getArgument(0));
  when(cuentas.cargar(ORIGEN,MONTO)).thenReturn(new CuentaRemota(ORIGEN,"Ana",new BigDecimal("800")));
  when(cuentas.abonar(DESTINO,MONTO)).thenThrow(new ErrorRemotoException("cuenta",500,"Error interno"));
  assertThat(servicio.transferir(ORIGEN,DESTINO,MONTO).getEstado()).isEqualTo("ABONO_INCIERTO");
  verify(cuentas,never()).abonar(ORIGEN,MONTO);
 }
}
