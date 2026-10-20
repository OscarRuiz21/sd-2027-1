 package mx.mexibanco.cuenta;

import jakarta.persistence.*;
import java.time.Instant;

/** Una fila por operación ya aplicada. La PK única impide aplicarla dos veces. */
@Entity
public class OperacionAplicada {

	@Id
	@Column(length = 100)
	private String clave;

	@Column(nullable = false)
	private Instant fecha = Instant.now();

	protected OperacionAplicada() {
	}

	public OperacionAplicada(String clave) {
		this.clave = clave;
	}
}