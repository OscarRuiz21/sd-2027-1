package mx.mexibanco.transferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {
    private final TransferenciaService transferencias;

    public TransferenciaController(TransferenciaService transferencias) {
        this.transferencias = transferencias;
    }

    public record SolicitudTransferencia(@NotBlank String claveOrigen,
            @NotBlank String claveDestino, @NotNull @Positive BigDecimal monto) { }

    @PostMapping
    public ResponseEntity<Transferencia> transferir(@Valid @RequestBody SolicitudTransferencia s) {
        Transferencia t = transferencias.transferir(s.claveOrigen(), s.claveDestino(), s.monto());
        HttpStatus codigo = switch (t.getEstado()) {
            case COMPLETADA -> HttpStatus.CREATED;
            case COMPENSADA -> HttpStatus.CONFLICT;
            case FALLIDA -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(codigo).body(t);
    }

    @GetMapping("/{id}")
    public Transferencia consultar(@PathVariable Long id) {
        return transferencias.consultar(id);
    }
}