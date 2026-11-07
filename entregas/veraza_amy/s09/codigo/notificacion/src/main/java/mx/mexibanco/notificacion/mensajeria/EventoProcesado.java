package mx.mexibanco.notificacion.mensajeria;
import jakarta.persistence.*;
import java.util.UUID;
@Entity
@Table(name="evento_procesado")
public class EventoProcesado {
    @Id @Column(name="evento_id") private UUID eventoId;
    private String tipo;
    @Column(name="transferencia_id") private Long transferenciaId;
    protected EventoProcesado() {}
}
