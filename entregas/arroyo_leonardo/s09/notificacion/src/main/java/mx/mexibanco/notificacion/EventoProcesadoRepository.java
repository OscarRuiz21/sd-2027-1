package mx.mexibanco.notificacion;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO eventos_procesados(evento_id, tipo, transferencia_id, procesado_en)
            VALUES (:id, :tipo, :transferenciaId, CURRENT_TIMESTAMP)
            ON CONFLICT (evento_id) DO NOTHING
            """, nativeQuery = true)
    int marcarSiEsNuevo(@Param("id") UUID id, @Param("tipo") String tipo,
            @Param("transferenciaId") Long transferenciaId);
}
