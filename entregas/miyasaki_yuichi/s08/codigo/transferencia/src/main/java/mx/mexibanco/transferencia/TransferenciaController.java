package mx.mexibanco.transferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import mx.mexibanco.clientes.Circuito;
import mx.mexibanco.clientes.CuentaCliente;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

	private final TransferenciaService transferencias;
	private final CuentaCliente cuentas;

	public TransferenciaController(TransferenciaService transferencias, CuentaCliente cuentas) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
	}

	/**
	 * Estado del circuit breaker hacia cuenta, para verlo cambiar (S08). Por el gateway:
	 * GET /api/transferencias/circuito. Spring prefiere esta ruta fija sobre /{id}.
	 */
	@GetMapping("/circuito")
	public Map<String, Object> circuito() {
		Circuito circuito = cuentas.circuito();
		return Map.of("servicio", circuito.getServicio(), "estado", circuito.getEstado(),
			"fallasSeguidas", circuito.getFallasSeguidas());
	}

	public record SolicitudTransferencia(@NotBlank String claveOrigen, @NotBlank String claveDestino, @NotNull @Positive BigDecimal monto) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Transferencia transferir(@Valid @RequestBody SolicitudTransferencia solicitud) {
		return transferencias.transferir(solicitud.claveOrigen(), solicitud.claveDestino(), solicitud.monto());
	}

	@GetMapping("/{id}")
	public Transferencia consultar(@PathVariable Long id) {
		return transferencias.consultar(id);
	}
}
