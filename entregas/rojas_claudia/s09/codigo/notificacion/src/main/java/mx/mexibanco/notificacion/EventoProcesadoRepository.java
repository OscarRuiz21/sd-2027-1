package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

	/**
	 * Marca el evento como procesado en UNA sola sentencia y regresa cuantas filas inserto: 1 si es
	 * nuevo, 0 si ya estaba. No es "consultar y luego insertar" (entre las dos llamadas otra entrega
	 * del mismo mensaje podria colarse) ni "insertar y atrapar la violacion de llave" (en Postgres eso
	 * deja la transaccion abortada): ON CONFLICT DO NOTHING lo resuelve dentro de Postgres. Si dos
	 * entregas llegan a la vez, la segunda espera el commit de la primera y luego inserta 0.
	 */
	@Modifying
	@Query(value = """
		INSERT INTO evento_procesado (evento_id, tipo, transferencia_id, procesado)
		VALUES (CAST(:eventoId AS uuid), :tipo, :transferenciaId, now())
		ON CONFLICT (evento_id) DO NOTHING
		""", nativeQuery = true)
	int marcarSiEsNuevo(@Param("eventoId") UUID eventoId, @Param("tipo") String tipo,
						@Param("transferenciaId") Long transferenciaId);
}
