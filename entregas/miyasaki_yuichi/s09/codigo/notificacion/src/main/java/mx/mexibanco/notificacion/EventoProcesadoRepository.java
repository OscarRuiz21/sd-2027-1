package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

	/**
	 * Marca el evento como procesado en UNA sola sentencia. Regresa 1 si es nuevo y 0 si ya estaba.
	 * No es "consultar y luego insertar" (otra entrega se colaria en medio) ni atrapa la violacion
	 * de llave (en Postgres eso deja la transaccion abortada): ON CONFLICT DO NOTHING no falla.
	 */
	@Modifying
	@Query(value = "INSERT INTO evento_procesado (evento_id, tipo, transferencia_id, procesado) "
		+ "VALUES (:eventoId, :tipo, :transferenciaId, now()) ON CONFLICT (evento_id) DO NOTHING", nativeQuery = true)
	int marcarSiEsNuevo(@Param("eventoId") UUID eventoId, @Param("tipo") String tipo,
						@Param("transferenciaId") Long transferenciaId);
}
