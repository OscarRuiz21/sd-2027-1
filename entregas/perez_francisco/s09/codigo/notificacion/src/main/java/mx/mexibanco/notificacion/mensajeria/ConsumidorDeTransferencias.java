
package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import jakarta.annotation.PostConstruct;
import mx.mexibanco.notificacion.AvisoDeMovimiento;
import mx.mexibanco.notificacion.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

/**
 * LAB S09: el consumidor de los eventos de transferencia. Hoy esta vacio a proposito: notificacion
 * todavia recibe los avisos por HTTP (POST /notificaciones, ver NotificacionController) y esta clase
 * no es un bean de Spring, asi que no hace nada.
 *
 * Lo que va aqui (el enunciado del lab tiene los nombres exactos):
 *  - declarar la cola notificacion.transferencias, enlazada al exchange transferencias con el
 *    patron transferencia.*, y su dead letter exchange y dead letter queue;
 *  - un metodo con @RabbitListener que lea cada evento, guarde los avisos y SOLO DESPUES haga ack;
 *  - que recibir dos veces el mismo evento no deje dos juegos de avisos;
 *  - que un mensaje que no se puede leer termine en la DLQ en vez de atorar la cola.
 */

// NUEVO S09: Spring reconoce esta clase como consumidor.
@Component
public class ConsumidorDeTransferencias {

    // LAB S09: tu consumidor va aqui.

    // NUEVO S09: herramientas para recibir y guardar mensajes.
    private static final Logger log =
            LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

    private final NotificacionService notificaciones;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transaccion;

    public ConsumidorDeTransferencias(
            NotificacionService notificaciones,
            JdbcTemplate jdbc,
            ObjectMapper json,
            PlatformTransactionManager administrador) {

        this.notificaciones = notificaciones;
        this.jdbc = jdbc;
        this.json = json;
        this.transaccion = new TransactionTemplate(administrador);
    }

    // NUEVO S09: declarar el exchange de transferencias.
    @Bean
    public TopicExchange exchangeTransferencias() {
        return new TopicExchange("transferencias", true, false);
    }

    // NUEVO S09: declarar el exchange para mensajes incorrectos.
    @Bean
    public DirectExchange exchangeErrores() {
        return new DirectExchange("transferencias.dlx", true, false);
    }

    // NUEVO S09: cola principal con configuracion de DLQ.
    @Bean
    public Queue colaTransferencias() {
        return QueueBuilder.durable("notificacion.transferencias")
                .deadLetterExchange("transferencias.dlx")
                .deadLetterRoutingKey("notificacion.transferencias")
                .build();
    }

    // NUEVO S09: conectar la cola principal al exchange.
    @Bean
    public Binding enlaceTransferencias() {
        return BindingBuilder.bind(colaTransferencias())
                .to(exchangeTransferencias())
                .with("transferencia.*");
    }

    // NUEVO S09: cola para mensajes que no se pueden procesar.
    @Bean
    public Queue colaErrores() {
        return QueueBuilder.durable("notificacion.transferencias.dlq")
                .build();
    }

    // NUEVO S09: conectar la DLQ al exchange de errores.
    @Bean
    public Binding enlaceErrores() {
        return BindingBuilder.bind(colaErrores())
                .to(exchangeErrores())
                .with("notificacion.transferencias");
    }

    // NUEVO S09: crear tabla con eventoId unico.
    @PostConstruct
    public void prepararTabla() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS evento_procesado (
                    evento_id VARCHAR(36) PRIMARY KEY
                )
                """);
    }

    // NUEVO S09: recibir eventos de RabbitMQ.
    @RabbitListener(queues = "notificacion.transferencias")
    public void recibir(Message mensaje, Channel canal) throws Exception {

        long etiqueta = mensaje.getMessageProperties().getDeliveryTag();

        // NUEVO S09: primero comprobar que el evento sea valido.
        JsonNode evento;

        try {
            evento = json.readTree(mensaje.getBody());

            if (evento == null || !evento.isObject()) {
                throw new IllegalArgumentException("JSON invalido");
            }

            String tipo = texto(evento, "tipo");
            String eventoId = texto(evento, "eventoId");

            java.util.UUID.fromString(eventoId);

            if (!tipo.equals("TransferenciaRealizada") &&
                !tipo.equals("TransferenciaRevertida")) {
                throw new IllegalArgumentException("Tipo no valido");
            }

            texto(evento, "clabeOrigen");
            texto(evento, "clabeDestino");
            texto(evento, "transferenciaId");
            texto(evento, "fecha");
            monto(evento, "monto");

            if (tipo.equals("TransferenciaRealizada")) {
                monto(evento, "saldoOrigen");
                monto(evento, "saldoDestino");
            }

        } catch (JsonProcessingException | IllegalArgumentException e) {

            // NUEVO S09: mensaje incorrecto, enviarlo a la DLQ.
            log.error("NUEVO S09: mensaje invalido: {}", e.getMessage());

            canal.basicNack(etiqueta, false, false);
            return;
        }

        try {

            // NUEVO S09: guardar evento y avisos en una misma transaccion.
            transaccion.execute(estado -> {

                String eventoId = evento.get("eventoId").asText();
                String tipo = evento.get("tipo").asText();

                // NUEVO S09: ON CONFLICT evita procesar duplicados.
                int insertados = jdbc.update(
                        """
                        INSERT INTO evento_procesado (evento_id)
                        VALUES (?)
                        ON CONFLICT (evento_id) DO NOTHING
                        """,
                        eventoId
                );

                if (insertados == 0) {
                    log.info("NUEVO S09: evento duplicado ignorado: {}", eventoId);
                    return null;
                }

                String origen = evento.get("clabeOrigen").asText();
                String destino = evento.get("clabeDestino").asText();
                BigDecimal cantidad = monto(evento, "monto");

                if (tipo.equals("TransferenciaRealizada")) {

                    // NUEVO S09: aviso para la cuenta de origen.
                    notificaciones.notificar(new AvisoDeMovimiento(
                            origen,
                            "TRANSFERENCIA_ENVIADA",
                            cantidad.negate(),
                            monto(evento, "saldoOrigen")
                    ));

                    // NUEVO S09: aviso para la cuenta de destino.
                    notificaciones.notificar(new AvisoDeMovimiento(
                            destino,
                            "TRANSFERENCIA_RECIBIDA",
                            cantidad,
                            monto(evento, "saldoDestino")
                    ));

                } else {

                    // NUEVO S09: aviso cuando se devuelve el dinero.
                    notificaciones.notificar(new AvisoDeMovimiento(
                            origen,
                            "TRANSFERENCIA_REVERTIDA: tu transferencia no se realizo y te devolvimos el dinero",
                            cantidad,
                            BigDecimal.ZERO
                    ));
                }

                log.info("NUEVO S09: evento procesado: {}", eventoId);
                return null;
            });

            // NUEVO S09: confirmar SOLO despues de guardar en PostgreSQL.
            canal.basicAck(etiqueta, false);

        } catch (Exception e) {

            // NUEVO S09: errores temporales se reintentan.
            log.error("NUEVO S09: error al guardar avisos", e);
            canal.basicNack(etiqueta, false, true);
        }
    }

    // NUEVO S09: obtener un campo obligatorio del JSON.
    private String texto(JsonNode evento, String campo) {
        JsonNode valor = evento.path(campo);

        if (valor.isMissingNode() || valor.isNull() ||
                !valor.isValueNode() || valor.asText().isBlank()) {
            throw new IllegalArgumentException("Falta el campo " + campo);
        }

        return valor.asText();
    }

    // NUEVO S09: obtener un monto numerico del JSON.
    private BigDecimal monto(JsonNode evento, String campo) {
        JsonNode valor = evento.path(campo);

        if (!valor.isNumber()) {
            throw new IllegalArgumentException("Monto invalido: " + campo);
        }

        return valor.decimalValue();
    }
}
