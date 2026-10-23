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
	void eventoRealizadoGeneraDosAvisosYDuplicadoNinguno() {
		EventoTransferencia evento = evento(EventoTransferencia.REALIZADA);
		when(procesados.marcarSiEsNuevo(evento.eventoId(), evento.tipo(), evento.transferenciaId()))
			.thenReturn(1, 0);
		when(notificaciones.saveAll(any())).thenAnswer(i -> i.getArgument(0));

		List<Notificacion> primera = servicio.avisarDeTransferencia(evento);
		List<Notificacion> repetida = servicio.avisarDeTransferencia(evento);

		assertThat(primera).extracting(Notificacion::getMensaje).containsExactly(
			"TRANSFERENCIA_ENVIADA por 200.00, saldo resultante 800.00",
			"TRANSFERENCIA_RECIBIDA por 200.00, saldo resultante 700.00");
		assertThat(repetida).isEmpty();
		verify(notificaciones).saveAll(any());
	}

	@Test
	void eventoRevertidoSoloAvisaAOrigen() {
		EventoTransferencia evento = evento(EventoTransferencia.REVERTIDA);
		when(procesados.marcarSiEsNuevo(evento.eventoId(), evento.tipo(), evento.transferenciaId())).thenReturn(1);
		when(notificaciones.saveAll(any())).thenAnswer(i -> i.getArgument(0));

		assertThat(servicio.avisarDeTransferencia(evento)).extracting(Notificacion::getMensaje)
			.containsExactly("DEVOLUCION_TRANSFERENCIA por 200.00, saldo resultante 800.00");
	}

	@Test
	void eventoSinIdSeRechazaAntesDeMarcar() {
		EventoTransferencia base = evento(EventoTransferencia.REALIZADA);
		EventoTransferencia invalido = new EventoTransferencia(null, base.tipo(), base.transferenciaId(),
			base.clabeOrigen(), base.clabeDestino(), base.monto(), base.saldoOrigen(), base.saldoDestino(), base.fecha());
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> servicio.avisarDeTransferencia(invalido))
			.isInstanceOf(IllegalArgumentException.class);
		verify(procesados, never()).marcarSiEsNuevo(any(), any(), any());
	}

	private static EventoTransferencia evento(String tipo) {
		return new EventoTransferencia(UUID.randomUUID(), tipo, 12L, "002180000000000001",
			"002180000000000002", new BigDecimal("200.00"), new BigDecimal("800.00"),
			EventoTransferencia.REALIZADA.equals(tipo) ? new BigDecimal("700.00") : null, Instant.now());
	}
}
