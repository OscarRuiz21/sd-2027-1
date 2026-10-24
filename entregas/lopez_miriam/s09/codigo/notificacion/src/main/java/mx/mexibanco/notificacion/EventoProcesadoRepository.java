package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * LAB S09: registra cada evento una sola vez.
 *
 * PostgreSQL garantiza que evento_id sea unico.
 * ON CONFLICT DO NOTHING permite ignorar los eventos repetidos.
 */
public interface EventoProcesadoRepository
        extends JpaRepository<EventoProcesado, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO eventos_procesados
                (evento_id, fecha_procesamiento)
            VALUES
                (:eventoId, CURRENT_TIMESTAMP)
            ON CONFLICT (evento_id) DO NOTHING
            """, nativeQuery = true)
    int registrarSiEsNuevo(@Param("eventoId") UUID eventoId);
}
