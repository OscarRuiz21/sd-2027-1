package mx.mexibanco.transferencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

	/** Para el reintento programado: las que se quedaron en COMPENSACION_PENDIENTE. */
	List<Transferencia> findByEstado(EstadoTransferencia estado);
}
