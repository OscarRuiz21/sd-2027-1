package mx.mexibanco.transferencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

	/** Para reintentar las compensaciones que quedaron pendientes. */
	List<Transferencia> findByEstado(EstadoTransferencia estado);
}
