package mx.mexibanco.mensajeria.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<EventoSaliente, Long> {

	/**
	 * Las filas pendientes mas viejas, BLOQUEADAS para esta transaccion. SKIP LOCKED: si corre mas de
	 * una replica de transferencia, cada relay se salta las filas que otro ya tomo, en vez de esperarlas
	 * (y publicarlas dos veces). Hay que llamarlo dentro de una transaccion.
	 */
	@Query(value = """
		SELECT * FROM outbox
		WHERE publicado IS NULL
		ORDER BY id
		LIMIT :limite
		FOR UPDATE SKIP LOCKED
		""", nativeQuery = true)
	List<EventoSaliente> tomarPendientes(@Param("limite") int limite);

	long countByPublicadoIsNull();
}
