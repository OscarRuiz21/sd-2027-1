package mx.mexibanco.mensajeria.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.mexibanco.mensajeria.EventoNoPublicadoException;
import mx.mexibanco.mensajeria.EventoTransferencia;
import mx.mexibanco.mensajeria.PublicadorDeEventos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Reto S09: el relay publica lo pendiente y, si el broker no confirma, deja la fila pendiente. */
@ExtendWith(MockitoExtension.class)
class RelayDeEventosTest {

	@Mock
	OutboxRepository outbox;

	@Mock
	PublicadorDeEventos publicador;

	@Mock
	PlatformTransactionManager transacciones;

	ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();

	RelayDeEventos relay;

	@BeforeEach
	void armar() {
		relay = new RelayDeEventos(outbox, publicador, json, new TransactionTemplate(transacciones));
	}

	private EventoSaliente pendiente(long transferenciaId) throws Exception {
		EventoTransferencia evento = EventoTransferencia.realizada(UUID.randomUUID(), transferenciaId,
			"002180000000000001", "002180000000000002", new BigDecimal("1"), new BigDecimal("99"),
			new BigDecimal("501"), Instant.parse("2026-10-17T10:42:07Z"));
		return new EventoSaliente(evento, json.writeValueAsString(evento));
	}

	@Test
	void publicaLoPendienteConElMismoEventoIdYLoMarca() throws Exception {
		EventoSaliente fila = pendiente(1L);
		when(outbox.tomarPendientes(RelayDeEventos.LOTE)).thenReturn(List.of(fila));

		relay.publicarPendientes();

		ArgumentCaptor<EventoTransferencia> publicado = ArgumentCaptor.forClass(EventoTransferencia.class);
		verify(publicador).publicar(publicado.capture());
		assertThat(publicado.getValue().eventoId()).isEqualTo(fila.getEventoId());
		assertThat(publicado.getValue().fecha()).isEqualTo(Instant.parse("2026-10-17T10:42:07Z"));
		assertThat(fila.getPublicado()).isNotNull();
		assertThat(fila.getIntentos()).isEqualTo(1);
		verify(transacciones).commit(any());
	}

	@Test
	void conElBrokerCaidoLaFilaSiguePendienteYNoSeIntentanLasDemas() throws Exception {
		EventoSaliente primera = pendiente(1L);
		EventoSaliente segunda = pendiente(2L);
		when(outbox.tomarPendientes(RelayDeEventos.LOTE)).thenReturn(List.of(primera, segunda));
		doThrow(new EventoNoPublicadoException("No se pudo mandar el evento al broker: Connection refused"))
			.when(publicador).publicar(any());

		relay.publicarPendientes();

		verify(publicador, times(1)).publicar(any());
		assertThat(primera.getPublicado()).isNull();
		assertThat(primera.getIntentos()).isEqualTo(1);
		assertThat(segunda.getIntentos()).isZero();
	}
}
