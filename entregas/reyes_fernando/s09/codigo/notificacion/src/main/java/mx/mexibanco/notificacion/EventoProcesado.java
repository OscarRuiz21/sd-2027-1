package mx.mexibanco.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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