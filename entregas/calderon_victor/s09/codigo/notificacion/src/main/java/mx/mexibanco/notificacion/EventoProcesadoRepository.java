package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

	/**
	 * Lab S09, Parte 3: guarda la marca del evento si es la primera vez que llega. Regresa 1 si la
	 * inserto (evento nuevo) y 0 si ya existia (duplicado).
	 *
	 * Es UNA sola sentencia a proposito. "Consultar si existe y luego insertar" deja un hueco entre las
	 * dos llamadas por donde otra entrega del mismo mensaje se puede colar. Y dejar que el INSERT
	 * truene por la llave duplicada y atrapar la excepcion tampoco sirve: en Postgres eso deja la
	 * transaccion abortada. ON CONFLICT DO NOTHING hace que la base decida sola, sin error.
	 */
	@Modifying
	@Query(nativeQuery = true, value = """
		INSERT INTO evento_procesado (evento_id, tipo, transferencia_id, procesado)
		VALUES (:eventoId, :tipo, :transferenciaId, now())
		ON CONFLICT (evento_id) DO NOTHING""")
	int marcarSiEsNuevo(@Param("eventoId") UUID eventoId, @Param("tipo") String tipo,
						@Param("transferenciaId") Long transferenciaId);
}
