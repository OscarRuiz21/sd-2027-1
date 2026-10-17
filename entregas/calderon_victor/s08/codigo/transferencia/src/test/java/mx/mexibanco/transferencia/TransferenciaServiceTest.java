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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Las mismas tres pruebas del monolito, ahora con dobles de los CLIENTES HTTP en lugar de los
 * servicios de otros modulos. Lab S08: la cuarta ya no documenta lo que se rompio, sino la
 * compensacion, y las siguientes cubren la devolucion pendiente y la falla provocada.
 */
@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

	private static final String ORIGEN = "002180000000000001";
	private static final String DESTINO = "002180000000000002";
	private static final BigDecimal MONTO = new BigDecimal("200");
	private static final Instant AHORA = Instant.parse("2026-10-17T10:00:00Z");

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
		servicio = saga("");
	}

	private TransferenciaService saga(String fallaAbonoClabe) {
		return new TransferenciaService(transferencias, cuentas, movimientos, notificaciones, 0, fallaAbonoClabe,
			Duration.ofSeconds(2), Duration.ofSeconds(30), Clock.fixed(AHORA, ZoneOffset.UTC));
	}

	private void guardarDevuelveLoMismo() {
		when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void transferirCargaAbonaRegistraDosAsientosYNotificaDos() {
		guardarDevuelveLoMismo();
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getMonto()).isEqualByComparingTo(MONTO);
		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPLETADA);
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA), eq(MONTO.negate()), eq(new BigDecimal("800")), any());
		verify(movimientos).registrar(eq(DESTINO), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA), eq(MONTO), eq(new BigDecimal("700")), any());
		verify(notificaciones, times(2)).notificar(any());
	}

	@Test
	void transferirAUnaCuentaQueNoExisteNoCobraNada() {
		guardarDevuelveLoMismo();
		when(cuentas.consultar(ORIGEN)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		when(cuentas.consultar(DESTINO)).thenThrow(new ErrorRemotoException("cuenta", 404, "No existe la CLABE " + DESTINO));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOfSatisfying(TransferenciaFallidaException.class, e -> {
				assertThat(e.getCodigoHttp()).isEqualTo(404);
				assertThat(e.getEstado()).isEqualTo(EstadoTransferencia.RECHAZADA);
			});
		verify(cuentas, never()).cargar(any(), any());
		verifyNoInteractions(movimientos, notificaciones);
	}

	@Test
	void transferirALaMismaCuentaSeRechaza() {
		assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO)).isInstanceOf(ReglaDeNegocioException.class);
		verifyNoInteractions(cuentas, movimientos, transferencias, notificaciones);
	}

	/** Lo que se rompio en v06a, ya arreglado: si el abono falla, se le devuelve el cargo a origen. */
	@Test
	void siElAbonoFallaSeLeDevuelveElCargoAOrigenYQuedaCompensada() {
		guardarDevuelveLoMismo();
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
		when(cuentas.abonar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOfSatisfying(TransferenciaFallidaException.class, e -> {
				assertThat(e.getCodigoHttp()).isEqualTo(503);
				assertThat(e.getEstado()).isEqualTo(EstadoTransferencia.COMPENSADA);
				assertThat(e.getMessage()).contains("cuenta no contesto").contains("Se devolvieron 200");
			});
		verify(cuentas).abonar(ORIGEN, MONTO);
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA), eq(MONTO), eq(new BigDecimal("1000")),
			contains("devolucion"));
		verifyNoInteractions(notificaciones);
	}

	/** Si la devolucion tambien falla: COMPENSACION_PENDIENTE, y el siguiente intento la termina una sola vez. */
	@Test
	void siLaDevolucionTambienFallaQuedaPendienteYElReintentoLaTerminaUnaSolaVez() {
		guardarDevuelveLoMismo();
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
		when(cuentas.abonar(ORIGEN, MONTO))
			.thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"))
			.thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

		Transferencia[] pendiente = new Transferencia[1];
		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOfSatisfying(TransferenciaFallidaException.class,
				e -> assertThat(e.getEstado()).isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE));
		verify(transferencias, atLeastOnce()).save(argThat(t -> {
			pendiente[0] = t;
			return true;
		}));
		Transferencia t = pendiente[0];
		assertThat(t.getEstado()).isEqualTo(EstadoTransferencia.COMPENSACION_PENDIENTE);
		assertThat(t.getProximoIntento()).isEqualTo(AHORA.plusSeconds(2));

		Transferencia segunda = servicio.compensar(t);   // lo que hace ReintentoDeCompensaciones
		Transferencia tercera = servicio.compensar(segunda); // un reintento de mas no devuelve otra vez

		assertThat(segunda.getEstado()).isEqualTo(EstadoTransferencia.COMPENSADA);
		assertThat(segunda.getIntentosCompensacion()).isEqualTo(2);
		assertThat(tercera.getEstado()).isEqualTo(EstadoTransferencia.COMPENSADA);
		verify(cuentas, times(2)).abonar(ORIGEN, MONTO);
	}

	@Test
	void conFallaAbonoClabeElAbonoFallaSinLlamarACuentaYSeCompensa() {
		servicio = saga(" 002180000000000099 , " + DESTINO);
		guardarDevuelveLoMismo();
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOfSatisfying(TransferenciaFallidaException.class, e -> {
				assertThat(e.getEstado()).isEqualTo(EstadoTransferencia.COMPENSADA);
				assertThat(e.getMessage()).contains("FALLA_ABONO_CLABE");
			});
		verify(cuentas, never()).abonar(eq(DESTINO), any());
	}

	@Test
	void laEsperaEntreIntentosCreceHastaElMaximo() {
		assertThat(servicio.esperaAntesDelIntento(2)).isEqualTo(Duration.ofSeconds(2));
		assertThat(servicio.esperaAntesDelIntento(3)).isEqualTo(Duration.ofSeconds(4));
		assertThat(servicio.esperaAntesDelIntento(4)).isEqualTo(Duration.ofSeconds(8));
		assertThat(servicio.esperaAntesDelIntento(5)).isEqualTo(Duration.ofSeconds(16));
		assertThat(servicio.esperaAntesDelIntento(6)).isEqualTo(Duration.ofSeconds(30));
		assertThat(servicio.esperaAntesDelIntento(40)).isEqualTo(Duration.ofSeconds(30));
	}
}
