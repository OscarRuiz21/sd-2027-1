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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static mx.mexibanco.transferencia.EstadoTransferencia.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * S08: la saga orquestada. Los clientes HTTP son dobles; el repositorio tambien, y se anota cada
 * estado que se guarda para comprobar que la fila pasa por cada paso.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferenciaServiceTest {

	private static final String ORIGEN = "002180000000000001";
	private static final String DESTINO = "002180000000000002";
	private static final String DESTINO_QUE_FALLA = "002180000000000666";
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

	/** Los estados en el orden en que se guardaron. */
	List<EstadoTransferencia> estadosGuardados = new ArrayList<>();

	@BeforeEach
	void armar() {
		servicio = new TransferenciaService(transferencias, cuentas, movimientos, notificaciones, 0,
			DESTINO_QUE_FALLA, 2000, 60000, 3);
		when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> {
			Transferencia t = i.getArgument(0);
			if (t.getId() == null) {
				ReflectionTestUtils.setField(t, "id", 7L);
			}
			estadosGuardados.add(t.getEstado());
			return t;
		});
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));
		when(cuentas.cargar(eq(ORIGEN), eq(MONTO), any())).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
	}

	@Test
	void transferirPasaPorTodosLosEstadosYTerminaCompletada() {
		when(cuentas.abonar(eq(DESTINO), eq(MONTO), any())).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(COMPLETADA);
		assertThat(estadosGuardados).containsSubsequence(INICIADA, CARGADA, ABONADA, COMPLETADA);
		verify(cuentas).cargar(ORIGEN, MONTO, "transferencia-7-cargo");
		verify(cuentas).abonar(DESTINO, MONTO, "transferencia-7-abono");
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA), eq(MONTO.negate()), eq(new BigDecimal("800")), any());
		verify(movimientos).registrar(eq(DESTINO), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA), eq(MONTO), eq(new BigDecimal("700")), any());
		verify(notificaciones, times(2)).notificar(any());
	}

	@Test
	void transferirAUnaCuentaQueNoExisteNoCobraNiCreaLaFila() {
		when(cuentas.consultar(DESTINO)).thenThrow(new ErrorRemotoException("cuenta", 404, "No existe la CLABE " + DESTINO));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(ErrorRemotoException.class)
			.extracting("estado").isEqualTo(404);
		verify(cuentas, never()).cargar(any(), any(), any());
		verify(transferencias, never()).save(any());
	}

	@Test
	void transferirALaMismaCuentaSeRechaza() {
		assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO)).isInstanceOf(ReglaDeNegocioException.class);
		verifyNoInteractions(cuentas);
	}

	@Test
	void saldoInsuficienteQuedaRechazadaYSePropagaEl422() {
		when(cuentas.cargar(eq(ORIGEN), eq(MONTO), any())).thenThrow(new ErrorRemotoException("cuenta", 422, "Saldo insuficiente"));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(ErrorRemotoException.class).extracting("estado").isEqualTo(422);
		assertThat(estadosGuardados).containsExactly(INICIADA, RECHAZADA);
		verify(cuentas, never()).abonar(any(), any(), any());
	}

	/** Lo que se arreglo: si el abono falla, el cargo se devuelve a origen y queda escrito. */
	@Test
	void siElAbonoFallaSeDevuelveElCargoYQuedaCompensada() {
		when(cuentas.abonar(eq(DESTINO), eq(MONTO), any())).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
		when(cuentas.operacionAplicada("transferencia-7-abono")).thenReturn(false);
		when(cuentas.operacionAplicada("transferencia-7-cargo")).thenReturn(true);
		when(cuentas.abonar(eq(ORIGEN), eq(MONTO), eq("transferencia-7-devolucion")))
			.thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOfSatisfying(TransferenciaNoCompletadaException.class, e -> {
				assertThat(e.getTransferencia().getEstado()).isEqualTo(COMPENSADA);
				assertThat(e.getCodigoHttp()).isEqualTo(503);
				assertThat(e.getMessage()).contains("Se devolvieron 200");
			});
		assertThat(estadosGuardados).containsSubsequence(INICIADA, CARGADA, COMPENSADA);
		verify(cuentas).abonar(ORIGEN, MONTO, "transferencia-7-devolucion");
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA), eq(MONTO), eq(new BigDecimal("1000")), any());
	}

	@Test
	void laFallaSimuladaPorClabeCompensaSinLlamarAlAbono() {
		when(cuentas.operacionAplicada("transferencia-7-abono")).thenReturn(false);
		when(cuentas.operacionAplicada("transferencia-7-cargo")).thenReturn(true);
		when(cuentas.abonar(eq(ORIGEN), eq(MONTO), any())).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO_QUE_FALLA, MONTO))
			.isInstanceOfSatisfying(TransferenciaNoCompletadaException.class,
				e -> assertThat(e.getTransferencia().getEstado()).isEqualTo(COMPENSADA));
		verify(cuentas, never()).abonar(eq(DESTINO_QUE_FALLA), any(), any());
	}

	/** Parte 0 con la saga: cuenta apagada durante la pausa. Ni el abono ni la devolucion pasan. */
	@Test
	void siTambienFallaLaDevolucionQuedaPendienteConReintentoProgramado() {
		when(cuentas.abonar(any(), any(), any())).thenThrow(new ResourceAccessException("Connection refused"));
		when(cuentas.operacionAplicada(any())).thenThrow(new ResourceAccessException("Connection refused"));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOfSatisfying(TransferenciaNoCompletadaException.class, e -> {
				assertThat(e.getTransferencia().getEstado()).isEqualTo(COMPENSACION_PENDIENTE);
				assertThat(e.getTransferencia().getIntentosCompensacion()).isEqualTo(1);
				assertThat(e.getTransferencia().getProximoIntento()).isNotNull();
				assertThat(e.getCodigoHttp()).isEqualTo(503);
			});
	}

	/** El recuperador reintenta cuando cuenta vuelve; la llave es la misma en cada intento. */
	@Test
	void unaCompensacionPendienteSeTerminaAlReintentar() {
		Transferencia t = pendiente();
		when(cuentas.operacionAplicada("transferencia-7-abono")).thenReturn(false);
		when(cuentas.operacionAplicada("transferencia-7-cargo")).thenReturn(true);
		when(cuentas.abonar(ORIGEN, MONTO, "transferencia-7-devolucion")).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

		Transferencia resultado = servicio.compensar(t);

		assertThat(resultado.getEstado()).isEqualTo(COMPENSADA);
		verify(cuentas).abonar(ORIGEN, MONTO, "transferencia-7-devolucion");
	}

	@Test
	void trasAgotarLosIntentosQuedaParaRevisionManual() {
		Transferencia t = pendiente();
		when(cuentas.operacionAplicada(any())).thenThrow(new ErrorRemotoException("cuenta", 503, "sigue caida"));

		servicio.compensar(t); // intento 2
		servicio.compensar(t); // intento 3 = maximo en este test

		assertThat(t.getEstado()).isEqualTo(COMPENSACION_FALLIDA);
		assertThat(t.getIntentosCompensacion()).isEqualTo(3);
	}

	/** Un timeout no significa que el abono no ocurrio: si cuenta dice que si se aplico, NO se devuelve. */
	@Test
	void siElAbonoSiSeAplicoPeseAlTimeoutSeCompletaEnLugarDeDevolver() {
		when(cuentas.abonar(eq(DESTINO), eq(MONTO), any())).thenThrow(new ResourceAccessException("Read timed out"));
		when(cuentas.operacionAplicada("transferencia-7-abono")).thenReturn(true);
		when(cuentas.consultar(DESTINO)).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(COMPLETADA);
		verify(cuentas, never()).abonar(eq(ORIGEN), any(), any());
	}

	@Test
	void siElCargoNuncaSeAplicoNoSeDevuelveNada() {
		when(cuentas.cargar(eq(ORIGEN), eq(MONTO), any())).thenThrow(new ResourceAccessException("Read timed out"));
		when(cuentas.operacionAplicada(any())).thenReturn(false);

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOfSatisfying(TransferenciaNoCompletadaException.class,
				e -> assertThat(e.getTransferencia().getEstado()).isEqualTo(RECHAZADA));
		verify(cuentas, never()).abonar(any(), any(), any());
	}

	private Transferencia pendiente() {
		Transferencia t = new Transferencia(ORIGEN, DESTINO, MONTO);
		ReflectionTestUtils.setField(t, "id", 7L);
		t.marcarAsientoCargo();
		t.compensacionFallo("cuenta apagada", java.time.Duration.ofSeconds(2), java.time.Duration.ofSeconds(60), 3);
		return t;
	}
}
