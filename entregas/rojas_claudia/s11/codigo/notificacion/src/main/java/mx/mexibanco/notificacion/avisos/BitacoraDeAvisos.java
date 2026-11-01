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

/**
 * v11: la bitacora de avisos enviados, en la coleccion "avisos" de MongoDB. Un documento por aviso.
 *
 * Por que documentos: cada aviso tiene una forma distinta segun el canal (SMS con telefono, CORREO con
 * direccion y asunto, PUSH con token), se escribe una vez, no se actualiza y se lee entero por CLABE.
 * No hay joins ni invariantes entre avisos. Es lo contrario del saldo, que se queda en Postgres.
 *
 * Que va aqui (lab S11, Parte 2):
 *  - documento(): arma el aviso con su _id y los campos de su canal;
 *  - registrar(): inserta cada aviso y, si el _id ya existe, NO lo cuenta (un evento repetido no
 *    duplica el aviso; es la idempotencia de la S09 sin tabla aparte);
 *  - crearIndice(): el indice por clabe y fecha que usa ultimos();
 *  - ultimos(): los 10 avisos mas recientes de una CLABE.
 *
 * Ya esta resuelto: a quien se avisa por cada tipo de evento (destinatarios) y por que canal
 * (DirectorioDeContactos).
 */
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

	/** Un aviso por hacer: a que CLABE, de que tipo y con que saldo resultante. */
	public record Destinatario(String clabe, String tipo, BigDecimal saldo) {
	}

	/**
	 * Las mismas reglas de la S09: TransferenciaRealizada avisa a origen y a destino; TransferenciaRevertida
	 * solo a origen, porque su dinero regreso.
	 */
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

	/** El texto de siempre: tipo, monto y saldo resultante. */
	public static String texto(Destinatario destinatario, EventoTransferencia evento) {
		return destinatario.tipo() + " por " + evento.monto().abs() + ", saldo resultante " + destinatario.saldo();
	}

	/**
	 * Guarda los avisos de un evento y regresa cuantos eran NUEVOS (0 si el evento ya se habia procesado).
	 */
	public int registrar(EventoTransferencia evento) {
		// S11: uno por uno (un insert de varios se detiene en el primer _id repetido). Si el _id ya existe,
		// ese aviso ya estaba: no es error y no se cuenta.
		int nuevos = 0;
		for (Destinatario destinatario : destinatarios(evento)) {
			Document aviso = documento(evento, destinatario, contactos.de(destinatario.clabe()));
			try {
				mongo.insert(aviso, COLECCION);
				nuevos++;
			} catch (DuplicateKeyException yaEstaba) {
				log.info("[avisos] {} ya estaba: evento repetido, no se duplica", aviso.get("_id"));
			}
		}
		return nuevos;
	}

	/**
	 * El aviso como documento de MongoDB. Los campos comunes van siempre; los del canal, segun el canal.
	 */
	public static Document documento(EventoTransferencia evento, Destinatario destinatario, Contacto contacto) {
		// S11: _id = eventoId:clabe. El mismo evento con la misma CLABE da siempre el mismo _id.
		Document aviso = new Document("_id", evento.eventoId() + ":" + destinatario.clabe())
			.append("eventoId", evento.eventoId().toString())
			.append("transferenciaId", evento.transferenciaId())
			.append("clabe", destinatario.clabe())
			.append("tipo", destinatario.tipo())
			.append("monto", new Decimal128(evento.monto()))         // dinero: Decimal128, nunca double
			.append("saldo", new Decimal128(destinatario.saldo()))
			.append("fecha", Date.from(evento.fecha()));             // la fecha del evento, no la de hoy
		String texto = texto(destinatario, evento);
		// Contacto es sellada: si falta un canal, este switch no compila.
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

	/** Asunto del correo y titulo del push, segun el tipo de aviso. */
	private static String asunto(String tipo) {
		return switch (tipo) {
			case "TRANSFERENCIA_ENVIADA" -> "Enviaste una transferencia";
			case "TRANSFERENCIA_RECIBIDA" -> "Recibiste una transferencia";
			case "DEVOLUCION_TRANSFERENCIA" -> "Tu transferencia no se realizo";
			default -> tipo;
		};
	}

	/** Se corre una vez al arrancar. Crear un indice que ya existe igual no hace nada. */
	@EventListener(ApplicationReadyEvent.class)
	public void crearIndice() {
		// S11: el indice sigue la forma de ultimos(): primero clabe, luego fecha descendente.
		String nombre = mongo.indexOps(COLECCION).ensureIndex(
			new Index().on("clabe", Sort.Direction.ASC).on("fecha", Sort.Direction.DESC).named("clabe_fecha"));
		log.info("[avisos] indice {} listo en {}", nombre, COLECCION);
	}

	/** Los ULTIMOS avisos de una CLABE, del mas reciente al mas viejo, tal como estan guardados. */
	public List<Document> ultimos(String clabe) {
		// S11: WHERE clabe = ? ORDER BY fecha DESC LIMIT 10
		Query consulta = Query.query(Criteria.where("clabe").is(clabe))
			.with(Sort.by(Sort.Direction.DESC, "fecha"))
			.limit(ULTIMOS);
		return mongo.find(consulta, Document.class, COLECCION);
	}
}
