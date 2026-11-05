package mx.mexibanco.notificacion.mensajeria;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
@Entity @Table(name="evento_procesado") public class EventoProcesado { @Id private String eventoId; private Instant fecha; protected EventoProcesado() {} public EventoProcesado(String id) { this.eventoId=id; this.fecha=Instant.now(); } }
