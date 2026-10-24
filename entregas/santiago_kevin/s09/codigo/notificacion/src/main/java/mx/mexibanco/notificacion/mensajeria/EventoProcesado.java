package mx.mexibanco.notificacion.mensajeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** La clave primaria impone unicidad en PostgreSQL, incluso entre consumidores concurrentes. */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {
    @Id
    @Column(name = "evento_id", length = 36, nullable = false)
    private String eventoId;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {}
}
