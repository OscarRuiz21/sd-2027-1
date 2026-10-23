package mx.mexibanco.transferencia;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class RegistroSaga {

    private final TransferenciaRepository transferencias;

    public RegistroSaga(TransferenciaRepository transferencias) {
        this.transferencias = transferencias;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transferencia iniciar(
            String claveOrigen,
            String claveDestino,
            BigDecimal monto) {

        Transferencia transferencia =
                new Transferencia(claveOrigen, claveDestino, monto);

        return transferencias.saveAndFlush(transferencia);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transferencia actualizar(
            Long id,
            EstadoTransferencia estado,
            String detalle) {

        Transferencia transferencia = transferencias.findById(id)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No existe el registro de la saga " + id));

        transferencia.actualizarEstado(estado, detalle);

        return transferencias.saveAndFlush(transferencia);
    }
}