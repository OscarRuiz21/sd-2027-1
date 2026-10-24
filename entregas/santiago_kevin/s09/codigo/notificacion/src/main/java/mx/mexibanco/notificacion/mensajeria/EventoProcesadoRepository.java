package mx.mexibanco.notificacion.mensajeria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, String> {
    /**
     * Se ejecuta dentro de la transaccion del procesador: 1 = nuevo; 0 = duplicado.
     * ON CONFLICT evita abortar la transaccion por una violacion de unicidad.
     * No se usa exists + save: dos consumidores podrian observar que aun no existe.
     */
    @Modifying
    @Query(value = """
        INSERT INTO evento_procesado (evento_id, procesado_en)
        VALUES (:eventoId, CURRENT_TIMESTAMP)
        ON CONFLICT (evento_id) DO NOTHING
        """, nativeQuery = true)
    int registrarSiNuevo(@Param("eventoId") String eventoId);
}
