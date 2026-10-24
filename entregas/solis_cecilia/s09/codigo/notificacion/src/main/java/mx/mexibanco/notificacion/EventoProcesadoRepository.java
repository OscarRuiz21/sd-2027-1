package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface EventoProcesadoRepository
        extends JpaRepository<EventoProcesado, String> {

    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            INSERT INTO evento_procesado (evento_id, procesado_en)
            VALUES (:eventoId, CURRENT_TIMESTAMP)
            ON CONFLICT (evento_id) DO NOTHING
            """, nativeQuery = true)
    int registrarSiNuevo(@Param("eventoId") String eventoId);
}