package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

	@Modifying
	@Query(value = "INSERT INTO evento_procesado (evento_id, tipo, transferencia_id, procesado_en) " +
	               "VALUES (:eventoId, :tipo, :transferenciaId, now()) " +
	               "ON CONFLICT (evento_id) DO NOTHING",
	       nativeQuery = true)
	int marcarSiEsNuevo(@Param("eventoId") UUID eventoId,
	                    @Param("tipo") String tipo,
	                    @Param("transferenciaId") Long transferenciaId);
}
