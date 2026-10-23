package mx.mexibanco.mensajeria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface EventoOutboxRepository extends JpaRepository<EventoOutbox, UUID> {
	@Query(value = "SELECT * FROM outbox_evento WHERE publicado_en IS NULL ORDER BY creado_en, evento_id LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
	Optional<EventoOutbox> siguientePendiente();
}
