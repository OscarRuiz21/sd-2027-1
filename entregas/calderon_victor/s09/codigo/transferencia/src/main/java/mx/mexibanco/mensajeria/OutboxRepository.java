package mx.mexibanco.mensajeria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<EventoPendiente, Long> {

	/**
	 * Lab S09, reto: los eventos pendientes mas viejos, BLOQUEADOS para esta transaccion. Con
	 * SKIP LOCKED, si hay dos replicas de transferencia corriendo el relay a la vez, cada una se lleva
	 * filas distintas en lugar de esperar a la otra o de publicar las mismas dos veces.
	 */
	@Query(nativeQuery = true, value = """
		SELECT * FROM outbox
		WHERE publicado IS NULL
		ORDER BY id
		LIMIT :cuantos
		FOR UPDATE SKIP LOCKED""")
	List<EventoPendiente> tomarPendientes(@Param("cuantos") int cuantos);
}
