package mx.mexibanco.transferencia;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una fila por transferencia.
 * Guarda sus datos y el estado alcanzado durante la saga.
 */
@Entity
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 18)
    private String claveOrigen;

    @Column(nullable = false, length = 18)
    private String claveDestino;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private Instant fecha;

    // Los registros anteriores pueden no tener estado ni detalle.
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private EstadoTransferencia estado;

    @Column(length = 1000)
    private String detalle;

    protected Transferencia() {
    }

    public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
        this.claveOrigen = claveOrigen;
        this.claveDestino = claveDestino;
        this.monto = monto;
        this.fecha = Instant.now();
        this.estado = EstadoTransferencia.INICIADA;
        this.detalle = "Transferencia registrada; pendiente de iniciar el cargo";
    }

    public Long getId() {
        return id;
    }

    public String getClaveOrigen() {
        return claveOrigen;
    }

    public String getClaveDestino() {
        return claveDestino;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public Instant getFecha() {
        return fecha;
    }

    public EstadoTransferencia getEstado() {
        return estado;
    }

    public String getDetalle() {
        return detalle;
    }

    public void actualizarEstado(EstadoTransferencia estado, String detalle) {
        this.estado = estado;
        this.detalle = detalle;
    }
}