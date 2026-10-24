package mx.mexibanco.transferencia;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * La operacion completa, una fila por transferencia. Los dos asientos (cargo y abono) viven en
 * movimiento; aqui queda el registro de que la operacion ocurrio, con su propio id y el estado
 * de la saga (S08).
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

        // El default cubre las filas anteriores a la saga (S07), que ya estaban completadas.
        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'COMPLETADA'")
        private EstadoTransferencia estado = EstadoTransferencia.INICIADA;

        @Column(length = 500)
        private String motivo;

        protected Transferencia() {
        }

        public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
                this.claveOrigen = claveOrigen;
                this.claveDestino = claveDestino;
                this.monto = monto;
                this.fecha = Instant.now();
                this.estado = EstadoTransferencia.INICIADA;
        }

        public void cambiarEstado(EstadoTransferencia nuevo, String motivo) {
                this.estado = nuevo;
                if (motivo != null) {
                        this.motivo = motivo.length() > 500 ? motivo.substring(0, 500) : motivo;
                }
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

        public String getMotivo() {
                return motivo;
        }
}
