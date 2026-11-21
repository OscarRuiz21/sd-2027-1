package mx.mexibanco.transferencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EventoOutboxRepository extends JpaRepository<EventoOutbox, UUID> {

    @Query(value = "SELECT * FROM evento_outbox WHERE publicado_en IS NULL "
        + "ORDER BY creado_en FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<EventoOutbox> tomarPendientesBloqueando();
}
