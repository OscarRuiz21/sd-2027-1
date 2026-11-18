package mx.mexibanco.transferencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

	/** S08: las devoluciones pendientes a las que ya les toco reintento. */
	List<Transferencia> findByEstadoAndProximoIntentoLessThanEqual(EstadoTransferencia estado, Instant ahora);

	/** S08: sagas a medias que nadie ha tocado en un rato (el orquestador se cayo a la mitad). */
	List<Transferencia> findByEstadoInAndActualizadaBefore(Collection<EstadoTransferencia> estados, Instant limite);

	List<Transferencia> findByEstadoOrderByIdDesc(EstadoTransferencia estado);

	List<Transferencia> findTop50ByOrderByIdDesc();
}
