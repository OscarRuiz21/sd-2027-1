package mx.mexibanco.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Un evento que ya procesamos. La llave primaria es el eventoId: la base no deja repetirlo. */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

    @Id
    @Column(name = "evento_id")
    private UUID eventoId;

    @Column(nullable = false)
    private String tipo;

    private Long transferenciaId;

    @Column(nullable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {
    }
}
