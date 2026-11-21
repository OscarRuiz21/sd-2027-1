package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import java.util.UUID;

public interface EventoProcesadoRepository extends CrudRepository<EventoProcesado, UUID> {
    
    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO evento_procesado (evento_id, tipo, transferencia_id) VALUES (:eventoId, :tipo, :transferenciaId) ON CONFLICT (evento_id) DO NOTHING")
    int marcarSiEsNuevo(UUID eventoId, String tipo, Long transferenciaId);
}