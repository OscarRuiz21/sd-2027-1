package mx.mexibanco.mensajeria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EventoOutboxRepository extends JpaRepository<EventoOutbox, Long> {

    /**
     * Toma la siguiente fila pendiente para publicar con FOR UPDATE SKIP LOCKED.
     * Si corren múltiples réplicas de transferencia, cada relay toma filas distintas
     * sin bloquearse entre sí ni duplicar envíos.
     */
    @Query(value = """
        SELECT * FROM outbox
        WHERE publicado = false
        ORDER BY id ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<EventoOutbox> buscarSiguientePendiente();
}
