package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

    /**
     * Marca el evento como procesado en UNA sola sentencia y regresa cuantas filas inserto:
     * 1 si es nuevo, 0 si ya estaba. Sin "consultar y luego insertar" (dos entregas simultaneas se
     * colarian entre las dos llamadas) y sin atrapar la violacion de llave (en Postgres deja la
     * transaccion abortada). Corre dentro de la transaccion de quien la llama.
     */
    @Modifying
    @Query(value = """
        INSERT INTO evento_procesado (evento_id, tipo, transferencia_id, fecha)
        VALUES (:eventoId, :tipo, :transferenciaId, now())
        ON CONFLICT (evento_id) DO NOTHING
        """, nativeQuery = true)
    int marcarSiEsNuevo(@Param("eventoId") UUID eventoId,
                        @Param("tipo") String tipo,
                        @Param("transferenciaId") Long transferenciaId);
}
