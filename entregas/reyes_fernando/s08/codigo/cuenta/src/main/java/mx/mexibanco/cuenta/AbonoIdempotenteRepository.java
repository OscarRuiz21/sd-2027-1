package mx.mexibanco.cuenta;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AbonoIdempotenteRepository extends JpaRepository<AbonoIdempotente, String> {
}
