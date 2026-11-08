package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

    /** Una sola sentencia: regresa 1 si el evento era nuevo y 0 si ya estaba. */
    @Modifying
    @Query(value = "insert into evento_procesado (evento_id, tipo, transferencia_id, fecha) "
            + "values (:id, :tipo, :transferencia, now()) on conflict (evento_id) do nothing", nativeQuery = true)
    int marcarSiEsNuevo(@Param("id") UUID id, @Param("tipo") String tipo, @Param("transferencia") Long transferencia);
}
