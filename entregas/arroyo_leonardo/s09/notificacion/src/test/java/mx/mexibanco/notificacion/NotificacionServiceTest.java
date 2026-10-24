package mx.mexibanco.notificacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

/** Nueva en v06a: el mensaje del aviso se arma igual que en el monolito, ahora desde un DTO. */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

	@Mock
	NotificacionRepository notificaciones;

	@Mock
	EventoProcesadoRepository procesados;

	@InjectMocks
	NotificacionService servicio;

	@Test
	void elAvisoDiceTipoMontoSinSignoYSaldo() {
		when(notificaciones.save(any(Notificacion.class))).thenAnswer(i -> i.getArgument(0));

		Notificacion aviso = servicio.notificar(new AvisoDeMovimiento("002180000000000001",
			"TRANSFERENCIA_ENVIADA", new BigDecimal("-200"), new BigDecimal("800")));

		assertThat(aviso.getClabeCuenta()).isEqualTo("002180000000000001");
		assertThat(aviso.getMensaje()).isEqualTo("TRANSFERENCIA_ENVIADA por 200, saldo resultante 800");
	}

	@Test
	void unaTransferenciaRealizadaGeneraUnAvisoParaCadaCuenta() {
		when(notificaciones.saveAll(org.mockito.ArgumentMatchers.<List<Notificacion>>any()))
			.thenAnswer(i -> i.getArgument(0));
		EventoTransferencia evento = new EventoTransferencia(UUID.randomUUID(), EventoTransferencia.REALIZADA,
			42L, "002180000000000001", "002180000000000002", new BigDecimal("200"),
			new BigDecimal("800"), new BigDecimal("700"), Instant.now());
		when(procesados.marcarSiEsNuevo(evento.eventoId(), evento.tipo(), evento.transferenciaId())).thenReturn(1);

		List<Notificacion> avisos = servicio.avisarDeTransferencia(evento);

		assertThat(avisos).extracting(Notificacion::getClabeCuenta)
			.containsExactly(evento.clabeOrigen(), evento.clabeDestino());
		assertThat(avisos).extracting(Notificacion::getMensaje).containsExactly(
			"TRANSFERENCIA_ENVIADA por 200, saldo resultante 800",
			"TRANSFERENCIA_RECIBIDA por 200, saldo resultante 700");
	}

	@Test
	void unaTransferenciaRevertidaSoloAvisaAlOrigenSinNecesitarSaldoDestino() {
		when(notificaciones.saveAll(org.mockito.ArgumentMatchers.<List<Notificacion>>any()))
			.thenAnswer(i -> i.getArgument(0));
		EventoTransferencia evento = new EventoTransferencia(UUID.randomUUID(), EventoTransferencia.REVERTIDA,
			42L, "002180000000000001", "002180000000000002", new BigDecimal("200"),
			new BigDecimal("1000"), null, Instant.now());
		when(procesados.marcarSiEsNuevo(evento.eventoId(), evento.tipo(), evento.transferenciaId())).thenReturn(1);

		List<Notificacion> avisos = servicio.avisarDeTransferencia(evento);

		assertThat(avisos).hasSize(1);
		assertThat(avisos.getFirst().getClabeCuenta()).isEqualTo(evento.clabeOrigen());
		assertThat(avisos.getFirst().getMensaje()).contains(
			"DEVOLUCION_TRANSFERENCIA", "te devolvimos el dinero", "saldo resultante 1000");
	}

	@Test
	void unEventoYaProcesadoNoGuardaAvisos() {
		EventoTransferencia evento = new EventoTransferencia(UUID.randomUUID(), EventoTransferencia.REALIZADA,
			42L, "002180000000000001", "002180000000000002", new BigDecimal("200"),
			new BigDecimal("800"), new BigDecimal("700"), Instant.now());
		when(procesados.marcarSiEsNuevo(evento.eventoId(), evento.tipo(), evento.transferenciaId())).thenReturn(0);

		assertThat(servicio.avisarDeTransferencia(evento)).isEmpty();

		verifyNoInteractions(notificaciones);
	}

	@Test
	void dosEntregasDelMismoEventoSoloGuardanUnJuegoDeAvisos() {
		EventoTransferencia evento = new EventoTransferencia(UUID.randomUUID(), EventoTransferencia.REALIZADA,
			42L, "002180000000000001", "002180000000000002", new BigDecimal("200"),
			new BigDecimal("800"), new BigDecimal("700"), Instant.now());
		when(procesados.marcarSiEsNuevo(evento.eventoId(), evento.tipo(), evento.transferenciaId())).thenReturn(1, 0);
		when(notificaciones.saveAll(org.mockito.ArgumentMatchers.<List<Notificacion>>any()))
			.thenAnswer(i -> i.getArgument(0));

		assertThat(servicio.avisarDeTransferencia(evento)).hasSize(2);
		assertThat(servicio.avisarDeTransferencia(evento)).isEmpty();

		verify(notificaciones, times(1)).saveAll(any());
	}
}
