package mx.mexibanco.cuenta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompensacionRepository
        extends JpaRepository<Compensacion, Long> {

    Optional<Compensacion> findByClaveOperacion(String claveOperacion);
}