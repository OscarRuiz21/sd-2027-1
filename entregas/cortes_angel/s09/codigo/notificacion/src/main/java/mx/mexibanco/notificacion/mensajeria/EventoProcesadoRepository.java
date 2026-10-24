package mx.mexibanco.notificacion.mensajeria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {
    
    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO evento_procesado (evento_id, tipo, transferencia_id) VALUES (?1, ?2, ?3) ON CONFLICT (evento_id) DO NOTHING")
    int marcarSiEsNuevo(UUID eventoId, String tipo, Long transferenciaId);
}