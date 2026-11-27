package mx.mexibanco.transferencia.mensajeria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<EventoPendiente, String> {

	/**
	 * Los pendientes mas viejos primero, BLOQUEADOS para esta transaccion. SKIP LOCKED: si hay otra
	 * replica de transferencia corriendo el relay, se salta las filas que la otra ya tomo en lugar de
	 * esperarla; asi dos replicas nunca publican la misma fila a la vez.
	 */
	@Query(value = "SELECT * FROM outbox WHERE publicado IS NULL ORDER BY creado LIMIT :limite FOR UPDATE SKIP LOCKED",
		nativeQuery = true)
	List<EventoPendiente> tomarPendientes(@Param("limite") int limite);

	long countByPublicadoIsNull();

	List<EventoPendiente> findTop50ByOrderByCreadoDesc();
}
