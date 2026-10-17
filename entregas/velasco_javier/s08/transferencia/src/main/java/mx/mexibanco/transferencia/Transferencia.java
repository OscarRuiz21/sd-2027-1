
package mx.mexibanco.transferencia;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

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

        @Enumerated(EnumType.STRING)
        @Column(length = 32)
        private EstadoTransferencia estado = EstadoTransferencia.INICIADA;

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

        public void setEstado(EstadoTransferencia estado) {
                this.estado = estado;
        }

        public void setDetalle(String detalle) {
                if (detalle != null && detalle.length() > 1000) {
                        detalle = detalle.substring(0, 1000);
                }
                this.detalle = detalle;
        }
}
