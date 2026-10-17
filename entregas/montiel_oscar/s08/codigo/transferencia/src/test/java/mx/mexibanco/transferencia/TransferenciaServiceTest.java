package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.CuentaSinInstanciasException;
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
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
	private final List<EstadoTransferencia> historia = new ArrayList<>();
	private TransferenciaService servicio;

	@BeforeEach
	void armar() {
		servicio = new TransferenciaService(transferencias, cuentas, movimientos, notificaciones, 0, "");
		lenient().when(transferencias.saveAndFlush(any(Transferencia.class))).thenAnswer(i -> {
			Transferencia t = i.getArgument(0);
			historia.add(t.getEstado());
			return t;
		});
	}

	@Test
	void exitoPersisteCadaPasoYRegistraDosMovimientos() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPLETADA);
		assertThat(historia).containsExactly(EstadoTransferencia.INICIADA, EstadoTransferencia.CARGO_EN_CURSO,
			EstadoTransferencia.CARGADA, EstadoTransferencia.ABONO_EN_CURSO,
			EstadoTransferencia.ABONADA, EstadoTransferencia.COMPLETADA);
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA),
			eq(MONTO.negate()), eq(new BigDecimal("800")), any());
		verify(movimientos).registrar(eq(DESTINO), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA),
			eq(MONTO), eq(new BigDecimal("700")), any());
	}

	@Test
	void fallaSimuladaDeAbonoDevuelveDineroYRegistraCompensacion() {
		servicio = new TransferenciaService(transferencias, cuentas, movimientos, notificaciones, 0, DESTINO);
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPENSADA);
		assertThat(t.getDetalle()).contains("monto devuelto");
		assertThat(historia).containsSubsequence(EstadoTransferencia.CARGADA,
			EstadoTransferencia.ABONO_EN_CURSO, EstadoTransferencia.COMPENSACION_EN_CURSO,
			EstadoTransferencia.COMPENSADA);
		verify(cuentas, never()).abonar(DESTINO, MONTO);
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA),
			eq(MONTO), eq(new BigDecimal("1000")), any());
	}

	@Test
	void siTampocoSePuedeDevolverQuedaPendiente() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "sin cuenta"));
		when(cuentas.abonar(ORIGEN, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "sin cuenta"));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE);
		assertThat(t.getDetalle()).contains("devolucion NO confirmada");
		verify(movimientos, times(1)).registrar(any(), any(), any(), any(), any());
	}

	@Test
	void reintentaConEsperaCrecienteSoloSiNoHuboEnvio() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenThrow(new CuentaSinInstanciasException());
		when(cuentas.abonar(ORIGEN, MONTO)).thenThrow(new CuentaSinInstanciasException());
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE);
		verify(cuentas, times(3)).abonar(ORIGEN, MONTO);
	}

	@Test
	void timeoutDeLaDevolucionNoSeReintentaPorqueEsAmbiguo() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "caida"));
		when(cuentas.abonar(ORIGEN, MONTO)).thenThrow(new ResourceAccessException("timeout"));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE);
		verify(cuentas, times(1)).abonar(ORIGEN, MONTO);
	}

	@Test
	void destinoInexistenteNoHaceCargoYGuardaRechazo() {
		when(cuentas.consultar(ORIGEN)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		when(cuentas.consultar(DESTINO)).thenThrow(new ErrorRemotoException("cuenta", 404, "No existe"));
		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(ErrorRemotoException.class);
		assertThat(historia).containsExactly(EstadoTransferencia.INICIADA, EstadoTransferencia.RECHAZADA);
		verify(cuentas, never()).cargar(any(), any());
	}

	@Test
	void mismaCuentaSeRechazaAntesDeCrearFila() {
		assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO))
			.isInstanceOf(ReglaDeNegocioException.class);
		assertThat(historia).isEmpty();
		verifyNoInteractions(cuentas, movimientos, notificaciones);
	}

	@Test
	void siFallaAsientoTrasAbonoNoDevuelveDinero() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		when(movimientos.registrar(any(), any(), any(), any(), any()))
			.thenReturn(mock(MovimientoRemoto.class))
			.thenThrow(new ErrorRemotoException("movimiento", 503, "sin ledger"));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.REGISTRO_PENDIENTE);
		verify(cuentas, never()).abonar(ORIGEN, MONTO);
	}
}
