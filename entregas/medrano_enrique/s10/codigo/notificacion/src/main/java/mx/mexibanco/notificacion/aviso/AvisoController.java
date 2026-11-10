package mx.mexibanco.notificacion.avisos;

import org.bson.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/avisos")
public class AvisoController {

    private final BitacoraDeAvisos bitacora;

    public AvisoController(BitacoraDeAvisos bitacora) {
        this.bitacora = bitacora;
    }

    // Al hacer un GET a /avisos/{clabe}, Spring ejecutará este método
    @GetMapping("/{clabe}")
    public List<Document> obtenerUltimosAvisos(@PathVariable String clabe) {
        // Llama al método que ya programaste en BitacoraDeAvisos.java
        return bitacora.ultimos(clabe);
    }
}