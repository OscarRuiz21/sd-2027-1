package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reto S09: el relay publica lo pendiente y solo marca lo que el broker confirmo. */
@ExtendWith(MockitoExtension.class)
class RelayDeOutboxTest {

	private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

	@Mock
	OutboxRepository outbox;

	@Mock
	PublicadorDeEventos eventos;

	private RelayDeOutbox relay() {
		return new RelayDeOutbox(outbox, eventos, SinTransaccion.plantilla(), json, 20);
	}

	private EventoOutbox pendiente(long transferenciaId) throws Exception {
		EventoTransferencia evento = EventoTransferencia.realizada(UUID.randomUUID(), transferenciaId,
			"002180000000000001", "002180000000000002", new BigDecimal("1"), new BigDecimal("800"),
			new BigDecimal("700"), Instant.now());
		return new EventoOutbox(evento, json.writeValueAsString(evento));
	}

	@Test
	void publicaLasPendientesYLasMarca() throws Exception {
		EventoOutbox primera = pendiente(30);
		EventoOutbox segunda = pendiente(31);
		when(outbox.tomarPendientes(20)).thenReturn(List.of(primera, segunda));

		relay().publicarPendientes();

		verify(eventos).publicar(argThat(e -> e.eventoId().equals(primera.getEventoId())));
		verify(eventos).publicar(argThat(e -> e.eventoId().equals(segunda.getEventoId())));
		assertThat(primera.getPublicado()).isNotNull();
		assertThat(segunda.getPublicado()).isNotNull();
	}

	/** Con RabbitMQ caido: la fila sigue pendiente (un intento mas) y no se prueban las demas en esta vuelta. */
	@Test
	void siElBrokerNoConfirmaLaFilaSiguePendiente() throws Exception {
		EventoOutbox primera = pendiente(30);
		EventoOutbox segunda = pendiente(31);
		when(outbox.tomarPendientes(20)).thenReturn(List.of(primera, segunda));
		doThrow(new EventoNoPublicadoException("Connection refused")).when(eventos).publicar(any());

		relay().publicarPendientes();

		verify(eventos, times(1)).publicar(any());
		assertThat(primera.getPublicado()).isNull();
		assertThat(primera.getIntentos()).isEqualTo(1);
		assertThat(segunda.getPublicado()).isNull();
		assertThat(segunda.getIntentos()).isZero();
	}
}
