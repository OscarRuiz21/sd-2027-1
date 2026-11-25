package mx.mexibanco.transferencia.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.mexibanco.transferencia.Transferencia;
import mx.mexibanco.transferencia.TransferenciaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CierreTransferencia {

	private final TransferenciaRepository transferencias;
	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;

	public CierreTransferencia(TransferenciaRepository transferencias, JdbcTemplate jdbc, ObjectMapper mapper) {
		this.transferencias = transferencias;
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	@Transactional
	public Transferencia completar(Transferencia t, BigDecimal saldoDestino) {
		t.marcarCompletada();
		return guardar(t, "transferencia.realizada", EventoTransferencia.realizada(t, saldoDestino));
	}

	@Transactional
	public Transferencia compensar(Transferencia t, String motivo, BigDecimal saldoDevuelto) {
		t.marcarCompensada(motivo);
		return guardar(t, "transferencia.revertida", EventoTransferencia.revertida(t, saldoDevuelto));
	}

	private Transferencia guardar(Transferencia t, String clave, EventoTransferencia evento) {
		String cuerpo;
		try {
			cuerpo = mapper.writeValueAsString(evento);
		} catch (JsonProcessingException error) {
			throw new IllegalStateException("No se pudo guardar el evento de la transferencia " + t.getId(), error);
		}
		Transferencia cerrada = transferencias.save(t);
		jdbc.update("""
			INSERT INTO outbox (evento_id, clave, cuerpo)
			VALUES (?, ?, ?)
			ON CONFLICT (evento_id) DO NOTHING
			""", evento.eventoId(), clave, cuerpo);
		return cerrada;
	}
}
