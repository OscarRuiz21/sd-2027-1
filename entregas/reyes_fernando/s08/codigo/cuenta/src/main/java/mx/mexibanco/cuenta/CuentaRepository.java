package mx.mexibanco.cuenta;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {
	Optional<Cuenta> findByClabe(String clabe);

	boolean existsByClabe(String clabe);

	/** El bloqueo se comparte entre las tres replicas a traves de PostgreSQL. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Cuenta c where c.clabe = :clabe")
	Optional<Cuenta> bloquearPorClabe(@Param("clabe") String clabe);
}
