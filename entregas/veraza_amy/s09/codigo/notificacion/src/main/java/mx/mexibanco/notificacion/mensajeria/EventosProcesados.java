package mx.mexibanco.notificacion.mensajeria;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
public interface EventosProcesados extends JpaRepository<EventoProcesado, UUID> {
    @Modifying
    @Query(value="INSERT INTO evento_procesado(evento_id,tipo,transferencia_id) VALUES (:id,:tipo,:transferencia) ON CONFLICT (evento_id) DO NOTHING", nativeQuery=true)
    int marcarSiEsNuevo(UUID id, String tipo, Long transferencia);
}
