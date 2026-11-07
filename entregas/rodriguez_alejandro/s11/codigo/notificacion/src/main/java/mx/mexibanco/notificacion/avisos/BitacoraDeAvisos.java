package mx.mexibanco.notificacion.avisos;

import mx.mexibanco.notificacion.EventoTransferencia;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
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
		return destinatario.tipo() + " por " + evento.monto().abs() + ", saldo resultante " + destinatario.saldo();
	}

	public int registrar(EventoTransferencia evento) {
		int nuevos = 0;
		List<Destinatario> dests = destinatarios(evento);
		
		for (Destinatario dest : dests) {
			// Usamos el metodo 'de' de DirectorioDeContactos
			Contacto contacto = contactos.de(dest.clabe()); 
			
			Document doc = documento(evento, dest, contacto);
			try {
				mongo.insert(doc, COLECCION);
				nuevos++;
			} catch (com.mongodb.DuplicateKeyException | DataIntegrityViolationException e) {
				// Capturamos tanto la excepcion nativa de Mongo como la envoltura de Spring
				log.info("[avisos] El aviso para {} ya estaba registrado (evento repetido).", dest.clabe());
			}
		}
		return nuevos;
	}

	public static Document documento(EventoTransferencia evento, Destinatario destinatario, Contacto contacto) {
		String idUnico = evento.eventoId() + ":" + destinatario.clabe();
		
		Document doc = new Document("_id", idUnico)
				.append("eventoId", evento.eventoId())
				.append("transferenciaId", evento.transferenciaId())
				.append("clabe", destinatario.clabe())
				.append("tipo", destinatario.tipo())
				.append("monto", new Decimal128(evento.monto()))
				.append("saldo", new Decimal128(destinatario.saldo()))
				.append("fecha", Date.from(evento.fecha()))
				.append("canal", contacto.getClass().getSimpleName().toUpperCase());

		switch (contacto) {
			case Contacto.Sms sms -> doc.append("telefono", sms.telefono())
										.append("texto", texto(destinatario, evento));
			case Contacto.Correo correo -> doc.append("correo", correo.correo())
											.append("asunto", "Aviso de Mexi Banco")
											.append("cuerpo", texto(destinatario, evento));
			case Contacto.Push push -> doc.append("token", push.token())
										.append("titulo", "Aviso de Mexi Banco")
										.append("cuerpo", texto(destinatario, evento));
			default -> throw new IllegalStateException("Canal de contacto no soportado");
		}
		return doc;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void crearIndice() {
		mongo.indexOps(COLECCION).ensureIndex(
				new Index().on("clabe", Sort.Direction.ASC).on("fecha", Sort.Direction.DESC)
		);
		log.info("[avisos] Índice 'clabe_fecha' asegurado en la colección {}", COLECCION);
	}

	public List<Document> ultimos(String clabe) {
		Query query = new Query(Criteria.where("clabe").is(clabe))
				.with(Sort.by(Sort.Direction.DESC, "fecha"))
				.limit(ULTIMOS);
		
		return mongo.find(query, Document.class, COLECCION);
	}
}