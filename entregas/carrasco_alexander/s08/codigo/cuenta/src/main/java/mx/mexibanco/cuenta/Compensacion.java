package mx.mexibanco.cuenta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class Compensacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String claveOperacion;

    @Column(nullable = false, length = 18)
    private String clabe;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private Instant fecha;

    protected Compensacion() {
    }

    public Compensacion(String claveOperacion, String clabe, BigDecimal monto) {
        this.claveOperacion = claveOperacion;
        this.clabe = clabe;
        this.monto = monto;
        this.fecha = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getClaveOperacion() {
        return claveOperacion;
    }

    public String getClabe() {
        return clabe;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public Instant getFecha() {
        return fecha;
    }
}