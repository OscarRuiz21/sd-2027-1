package mx.mexibanco.transferencia;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * La operacion completa, una fila por transferencia. Los dos asientos (cargo y abono) viven en
 * movimiento; aqui queda el registro de que la operacion ocurrio, con su propio id.
 */
@Entity
public class Transferencia {

    // Definimos los posibles estados de nuestra saga
    public enum Estado {
        INICIADA,
        CARGADA,
        COMPLETADA,
        COMPENSADA,
        COMPENSACION_PENDIENTE
    }

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

    // Nuevo campo para guardar el estado en la base de datos como texto
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado;

    protected Transferencia() {
    }

    public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
        this.claveOrigen = claveOrigen;
        this.claveDestino = claveDestino;
        this.monto = monto;
        this.fecha = Instant.now();
        this.estado = Estado.INICIADA; // Toda transferencia arranca como INICIADA
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

    // Nuevos métodos para el estado
    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }
}