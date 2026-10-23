package mx.mexibanco.transferencia;

public enum EstadoTransferencia {
    INICIADA,
    CARGADA,
    ABONADA,
    COMPLETADA,
    COMPENSACION_PENDIENTE,
    COMPENSADA,
    FALLIDA,
    REVISION_PENDIENTE
}