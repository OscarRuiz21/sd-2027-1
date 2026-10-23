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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
	void eventoNuevoSeInsertaYGeneraAvisos() {
		UUID eventoId = UUID.randomUUID();
		EventoTransferencia evento = new EventoTransferencia(
			eventoId, EventoTransferencia.REALIZADA, 1L,
			"002180000000000001", "002180000000000002",
			new BigDecimal("200"), new BigDecimal("800"), new BigDecimal("700"), Instant.now());

		when(procesados.marcarSiEsNuevo(eventoId, EventoTransferencia.REALIZADA, 1L)).thenReturn(1);
		when(notificaciones.saveAll(any())).thenAnswer(i -> i.getArgument(0));

		List<Notificacion> avisos = servicio.avisarDeTransferencia(evento);

		assertThat(avisos).hasSize(2);
		verify(notificaciones).saveAll(any());
	}

	@Test
	void eventoDuplicadoNoGeneraAvisos() {
		UUID eventoId = UUID.randomUUID();
		EventoTransferencia evento = new EventoTransferencia(
			eventoId, EventoTransferencia.REALIZADA, 1L,
			"002180000000000001", "002180000000000002",
			new BigDecimal("200"), new BigDecimal("800"), new BigDecimal("700"), Instant.now());

		when(procesados.marcarSiEsNuevo(eventoId, EventoTransferencia.REALIZADA, 1L)).thenReturn(0);

		List<Notificacion> avisos = servicio.avisarDeTransferencia(evento);

		assertThat(avisos).isEmpty();
		verify(notificaciones, never()).saveAll(any());
	}
}
