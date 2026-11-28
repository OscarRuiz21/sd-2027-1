package mx.mexibanco.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Un evento que notificacion ya proceso. La llave primaria es el eventoId: la misma entrega repetida
 * (at-least-once) choca con esta fila y no vuelve a generar avisos.
 *
 * Esta clase existe para que ddl-auto cree la tabla evento_procesado. Las filas se insertan con
 * EventoProcesadoRepository.marcarSiEsNuevo (INSERT ... ON CONFLICT DO NOTHING), no con save().
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

    @Id
    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(name = "transferencia_id")
    private Long transferenciaId;

    @Column(nullable = false)
    private Instant fecha;

    protected EventoProcesado() {
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public String getTipo() {
        return tipo;
    }

    public Long getTransferenciaId() {
        return transferenciaId;
    }

    public Instant getFecha() {
        return fecha;
    }
}
