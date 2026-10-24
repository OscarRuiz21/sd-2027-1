package mx.mexibanco.transferencia;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static mx.mexibanco.transferencia.EstadoTransferencia.*;

/**
 * Una fila por transferencia.
 * Guarda el estado de la saga y los datos de su evento final.
 *
 * Las columnas nuevas permiten null para conservar compatibilidad
 * con los registros anteriores.
 */
@Entity
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 18)
    private String claveOrigen;

    @Column(nullable = false, length = 18)
    private String claveDestino;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private Instant fecha;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EstadoTransferencia estado;

    /** Motivo del rechazo o de la compensación. */
    @Column(length = 500)
    private String motivo;

    @Column(precision = 19, scale = 2)
    private BigDecimal saldoOrigenTrasCargo;

    @Column
    private Integer intentosDeCompensacion;

    @Column
    private Instant actualizada;

    // Datos estables del evento para futuras republicaciones.

    @Column(length = 36)
    private String eventoId;

    @Column(length = 40)
    private String tipoEvento;

    @Column(precision = 19, scale = 2)
    private BigDecimal saldoOrigenEvento;

    @Column(precision = 19, scale = 2)
    private BigDecimal saldoDestinoEvento;

    @Column
    private Instant fechaEvento;

    protected Transferencia() {
    }

    public Transferencia(
            String claveOrigen,
            String claveDestino,
            BigDecimal monto) {

        this.claveOrigen = claveOrigen;
        this.claveDestino = claveDestino;
        this.monto = monto;
        this.fecha = Instant.now();
        this.estado = INICIADA;
        this.intentosDeCompensacion = 0;
        this.actualizada = this.fecha;
    }

    /**
     * Cada paso usa siempre la misma clave de idempotencia.
     */
    public String clave(String paso) {
        return "transferencia-" + id + "-" + paso;
    }

    public void marcarCargada(BigDecimal saldoOrigen) {
        pasarA(CARGADA, INICIADA);
        this.saldoOrigenTrasCargo = saldoOrigen;
    }

    public void marcarFallida(String motivo) {
        pasarA(FALLIDA, INICIADA);
        this.motivo = motivo;
    }

    public void marcarAbonada() {
        pasarA(ABONADA, CARGADA);
    }

    public void marcarCompletada() {
        pasarA(COMPLETADA, ABONADA);
    }

    /**
     * Los reintentos conservan el motivo original cuando reciben null.
     */
    public void marcarCompensacionPendiente(String motivo) {
        pasarA(COMPENSACION_PENDIENTE, CARGADA, COMPENSACION_PENDIENTE);
        contarIntento(motivo);
    }

    public void marcarCompensada(String motivo) {
        pasarA(COMPENSADA, CARGADA, COMPENSACION_PENDIENTE);
        contarIntento(motivo);
    }

    private void contarIntento(String motivo) {
        this.intentosDeCompensacion = getIntentosDeCompensacion() + 1;

        if (motivo != null) {
            this.motivo = motivo;
        }
    }

    private void pasarA(
            EstadoTransferencia nuevo,
            EstadoTransferencia... desde) {

        if (!Set.of(desde).contains(estado)) {
            throw new IllegalStateException(
                    "La transferencia " + id
                            + " no puede pasar de " + estado
                            + " a " + nuevo);
        }

        this.estado = nuevo;
        this.actualizada = Instant.now();
    }

    /**
     * Genera los datos del evento una sola vez.
     * Una republicación debe conservar el mismo ID y contenido.
     */
    public void prepararEvento(
            String tipo,
            BigDecimal saldoOrigen,
            BigDecimal saldoDestino) {

        if (eventoId != null) {
            if (!tipo.equals(tipoEvento)) {
                throw new IllegalStateException(
                        "La transferencia ya tiene otro tipo de evento");
            }
            return;
        }

        this.eventoId = UUID.randomUUID().toString();
        this.tipoEvento = tipo;
        this.saldoOrigenEvento = saldoOrigen;
        this.saldoDestinoEvento = saldoDestino;
        this.fechaEvento = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getClaveOrigen() {
        return claveOrigen;
    }

    public String getClaveDestino() {
        return claveDestino;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public Instant getFecha() {
        return fecha;
    }

    public EstadoTransferencia getEstado() {
        return estado;
    }

    public String getMotivo() {
        return motivo;
    }

    public BigDecimal getSaldoOrigenTrasCargo() {
        return saldoOrigenTrasCargo;
    }

    public int getIntentosDeCompensacion() {
        return intentosDeCompensacion == null
                ? 0
                : intentosDeCompensacion;
    }

    public Instant getActualizada() {
        return actualizada;
    }

    public String getEventoId() {
        return eventoId;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public BigDecimal getSaldoOrigenEvento() {
        return saldoOrigenEvento;
    }

    public BigDecimal getSaldoDestinoEvento() {
        return saldoDestinoEvento;
    }

    public Instant getFechaEvento() {
        return fechaEvento;
    }
}