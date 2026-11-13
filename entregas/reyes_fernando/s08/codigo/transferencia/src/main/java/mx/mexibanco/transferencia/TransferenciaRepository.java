package mx.mexibanco.transferencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

	/** Solo compensaciones nuevas, con llave persistida y espera cumplida. */
	List<Transferencia> findByEstadoInAndLlaveCompensacionIsNotNullAndProximoReintentoLessThanEqualOrderByProximoReintentoAsc(
		Collection<EstadoTransferencia> estados, Instant ahora);
}
