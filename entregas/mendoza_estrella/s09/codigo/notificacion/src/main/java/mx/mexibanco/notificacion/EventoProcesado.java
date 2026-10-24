
package mx.mexibanco.notificacion;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

    @Id
    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "transferencia_id", nullable = false)
    private Long transferenciaId;

    protected EventoProcesado() {
    }
}
