package mx.mexibanco.mensajeria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventoSalienteRepository extends JpaRepository<EventoSaliente, Long> {

    /**
     * Las filas pendientes, de la mas vieja a la mas nueva. FOR UPDATE SKIP LOCKED: si hay mas de una
     * replica de transferencia, cada relay se lleva filas distintas en vez de publicar las mismas dos
     * veces. Hay que llamarlo dentro de una transaccion (el candado vive hasta el commit).
     */
    @Query(nativeQuery = true, value = """
        SELECT * FROM outbox
        WHERE publicado_en IS NULL
        ORDER BY id
        LIMIT :limite
        FOR UPDATE SKIP LOCKED
        """)
    List<EventoSaliente> pendientesParaPublicar(@Param("limite") int limite);
}