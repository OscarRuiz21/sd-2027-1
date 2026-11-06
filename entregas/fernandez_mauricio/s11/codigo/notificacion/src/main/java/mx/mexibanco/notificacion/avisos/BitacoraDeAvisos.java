
package mx.mexibanco.notificacion.avisos;

import mx.mexibanco.notificacion.EventoTransferencia;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Component
public class BitacoraDeAvisos {

    private static final Logger log = LoggerFactory.getLogger(BitacoraDeAvisos.class);

    public static final String COLECCION = "avisos";
    public static final int ULTIMOS = 10;

    private final MongoTemplate mongo;
    private final DirectorioDeContactos contactos;

    public BitacoraDeAvisos(MongoTemplate mongo, DirectorioDeContactos contactos) {
        this.mongo = mongo;
        this.contactos = contactos;
    }

    public record Destinatario(String clabe, String tipo, BigDecimal saldo) {
    }

    public static List<Destinatario> destinatarios(EventoTransferencia evento) {
        return switch (evento.tipo()) {
            case EventoTransferencia.REALIZADA -> List.of(
                new Destinatario(evento.clabeOrigen(), "TRANSFERENCIA_ENVIADA", evento.saldoOrigen()),
                new Destinatario(evento.clabeDestino(), "TRANSFERENCIA_RECIBIDA", evento.saldoDestino()));
            case EventoTransferencia.REVERTIDA -> List.of(
                new Destinatario(evento.clabeOrigen(), "DEVOLUCION_TRANSFERENCIA", evento.saldoOrigen()));
            default -> throw new IllegalArgumentException("Tipo de evento desconocido: " + evento.tipo());
        };
    }

    public static String texto(Destinatario destinatario, EventoTransferencia evento) {
        return destinatario.tipo() + " por " + evento.monto().abs()
            + ", saldo resultante " + destinatario.saldo();
    }

    public int registrar(EventoTransferencia evento) {
        int nuevos = 0;

        for (Destinatario destinatario : destinatarios(evento)) {
            Document aviso = documento(
                evento, destinatario, contactos.de(destinatario.clabe())
            );

            try {
                mongo.insert(aviso, COLECCION);
                nuevos++;
            } catch (DuplicateKeyException yaEstaba) {
                log.info("[avisos] {} ya estaba: evento repetido, no se duplica",
                    aviso.get("_id"));
            }
        }

        return nuevos;
    }

    public static Document documento(
            EventoTransferencia evento,
            Destinatario destinatario,
            Contacto contacto) {

        Document aviso = new Document(
                "_id", evento.eventoId() + ":" + destinatario.clabe())
            .append("eventoId", evento.eventoId().toString())
            .append("transferenciaId", evento.transferenciaId())
            .append("clabe", destinatario.clabe())
            .append("tipo", destinatario.tipo())
            .append("monto", new Decimal128(evento.monto().abs()))
            .append("saldo", new Decimal128(destinatario.saldo()))
            .append("fecha", Date.from(evento.fecha()));

        String texto = texto(destinatario, evento);

        switch (contacto) {
            case Contacto.Sms sms -> aviso
                .append("canal", "SMS")
                .append("telefono", sms.telefono())
                .append("texto", texto);

            case Contacto.Correo correo -> aviso
                .append("canal", "CORREO")
                .append("correo", correo.correo())
                .append("asunto", asunto(destinatario.tipo()))
                .append("cuerpo", texto);

            case Contacto.Push push -> aviso
                .append("canal", "PUSH")
                .append("token", push.token())
                .append("titulo", asunto(destinatario.tipo()))
                .append("cuerpo", texto);
        }

        return aviso;
    }

    private static String asunto(String tipo) {
        return switch (tipo) {
            case "TRANSFERENCIA_ENVIADA" -> "Enviaste una transferencia";
            case "TRANSFERENCIA_RECIBIDA" -> "Recibiste una transferencia";
            case "DEVOLUCION_TRANSFERENCIA" -> "Tu transferencia no se realizo";
            default -> tipo;
        };
    }

    @EventListener(ApplicationReadyEvent.class)
    public void crearIndice() {
        String nombre = mongo.indexOps(COLECCION).ensureIndex(
            new Index()
                .on("clabe", Sort.Direction.ASC)
                .on("fecha", Sort.Direction.DESC)
                .named("clabe_fecha")
        );

        log.info("[avisos] indice {} listo en {}", nombre, COLECCION);
    }

    public List<Document> ultimos(String clabe) {
        Query consulta = new Query(Criteria.where("clabe").is(clabe))
            .with(Sort.by(Sort.Direction.DESC, "fecha"))
            .limit(ULTIMOS);

        return mongo.find(consulta, Document.class, COLECCION);
    }
}
