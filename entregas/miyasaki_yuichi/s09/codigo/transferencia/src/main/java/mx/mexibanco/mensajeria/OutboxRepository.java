package mx.mexibanco.mensajeria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<EventoOutbox, UUID> {

	/**
	 * Las filas pendientes mas viejas, BLOQUEADAS hasta que termine la transaccion del relay. SKIP LOCKED:
	 * si corre otra replica de transferencia, se salta las filas que ya tomo la otra en lugar de esperarlas,
	 * asi cada relay publica filas distintas y no las mismas dos veces.
	 */
	@Query(value = "SELECT * FROM outbox WHERE publicado IS NULL ORDER BY creado LIMIT :lote FOR UPDATE SKIP LOCKED",
		nativeQuery = true)
	List<EventoOutbox> tomarPendientes(@Param("lote") int lote);
}
