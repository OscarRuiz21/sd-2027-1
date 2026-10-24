package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, String> {
}