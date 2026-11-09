package mx.mexibanco.transferencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    /** Filas pendientes, bloqueadas para esta transaccion. Las que otra replica ya tomo se saltan. */
    @Query(value = "SELECT * FROM outbox WHERE publicada IS NULL ORDER BY id LIMIT :lote FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<Outbox> pendientes(@Param("lote") int lote);
}