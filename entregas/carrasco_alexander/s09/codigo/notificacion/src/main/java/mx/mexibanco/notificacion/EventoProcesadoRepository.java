package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, Long> {
    boolean existsByEventoId(String eventoId);
}