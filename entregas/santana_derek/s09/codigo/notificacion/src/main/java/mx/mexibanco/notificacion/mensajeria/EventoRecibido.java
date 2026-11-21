package mx.mexibanco.notificacion.mensajeria;
import jakarta.persistence.*;
@Entity
@Table(name="evento_recibido")
public class EventoRecibido {
 @Id @Column(name="evento_id",length=36) private String eventoId;
 protected EventoRecibido() {}
}
