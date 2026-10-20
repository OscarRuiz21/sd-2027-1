package mx.mexibanco.transferencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

	List<Transferencia> findByEstadoAndActualizadaBefore(EstadoTransferencia estado, Instant limite);
}