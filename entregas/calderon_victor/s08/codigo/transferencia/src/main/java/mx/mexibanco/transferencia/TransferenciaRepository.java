package mx.mexibanco.transferencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

	/** Lab S08: las devoluciones pendientes a las que ya les toca su siguiente intento. */
	List<Transferencia> findByEstadoAndProximoIntentoLessThanEqualOrderByIdAsc(EstadoTransferencia estado, Instant ahora);
}
