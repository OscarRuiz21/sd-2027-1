package mx.mexibanco.compartido;

/**
 * La saga no pudo completarse. Lleva el id de la transferencia y el estado en que quedo
 * (COMPENSADA, COMPENSACION_PENDIENTE o FALLIDA) para que el cliente sepa que paso con su dinero.
 */
public class TransferenciaFallidaException extends RuntimeException {

        private final Long transferenciaId;
        private final String estado;

        public TransferenciaFallidaException(Long transferenciaId, String estado, String mensaje, Throwable causa) {
                super(mensaje, causa);
                this.transferenciaId = transferenciaId;
                this.estado = estado;
        }

        public Long getTransferenciaId() {
                return transferenciaId;
        }

        public String getEstado() {
                return estado;
        }
}
