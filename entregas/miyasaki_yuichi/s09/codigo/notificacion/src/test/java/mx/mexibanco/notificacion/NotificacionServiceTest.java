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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Nueva en v06a: el mensaje del aviso se arma igual que en el monolito, ahora desde un DTO. */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

	private static final String ORIGEN = "002180000000000001";
	private static final String DESTINO = "002180000000000002";

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

	private static EventoTransferencia realizada(UUID eventoId) {
		return new EventoTransferencia(eventoId, EventoTransferencia.REALIZADA, 12L, ORIGEN, DESTINO,
			new BigDecimal("200"), new BigDecimal("800"), new BigDecimal("700"), Instant.now());
	}

	@Test
	void unEventoNuevoDejaUnAvisoParaCadaLado() {
		UUID eventoId = UUID.randomUUID();
		when(procesados.marcarSiEsNuevo(eventoId, EventoTransferencia.REALIZADA, 12L)).thenReturn(1);
		when(notificaciones.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

		List<Notificacion> avisos = servicio.avisarDeTransferencia(realizada(eventoId));

		assertThat(avisos).extracting(Notificacion::getClabeCuenta).containsExactly(ORIGEN, DESTINO);
		assertThat(avisos).extracting(Notificacion::getMensaje).containsExactly(
			"TRANSFERENCIA_ENVIADA por 200, saldo resultante 800",
			"TRANSFERENCIA_RECIBIDA por 200, saldo resultante 700");
	}

	/** Tarea S09: el broker entrega al menos una vez. La segunda entrega del mismo evento no guarda nada. */
	@Test
	void elMismoEventoDosVecesNoRepiteLosAvisos() {
		UUID eventoId = UUID.randomUUID();
		when(procesados.marcarSiEsNuevo(eventoId, EventoTransferencia.REALIZADA, 12L)).thenReturn(0);

		List<Notificacion> avisos = servicio.avisarDeTransferencia(realizada(eventoId));

		assertThat(avisos).isEmpty();
		verify(notificaciones, never()).saveAll(anyList());
	}

	/** Un evento sin eventoId nunca se va a poder procesar: se rechaza sin tocar la base (el consumidor lo manda a la DLQ). */
	@Test
	void unEventoSinEventoIdSeRechazaSinTocarLaBase() {
		assertThatThrownBy(() -> servicio.avisarDeTransferencia(realizada(null)))
			.isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(procesados, notificaciones);
	}

	@Test
	void unTipoQueNoExisteSeRechazaSinTocarLaBase() {
		EventoTransferencia raro = new EventoTransferencia(UUID.randomUUID(), "TransferenciaInventada", 12L, ORIGEN, DESTINO,
			new BigDecimal("200"), new BigDecimal("800"), null, Instant.now());

		assertThatThrownBy(() -> servicio.avisarDeTransferencia(raro)).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(procesados, notificaciones);
	}
}
