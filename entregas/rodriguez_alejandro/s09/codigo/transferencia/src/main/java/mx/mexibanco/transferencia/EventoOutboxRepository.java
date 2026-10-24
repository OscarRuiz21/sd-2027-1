package mx.mexibanco.transferencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EventoOutboxRepository extends JpaRepository<EventoOutbox, String> {
    @Query(value = "SELECT * FROM evento_outbox WHERE procesado = false FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<EventoOutbox> findPendientesParaProcesar();
}