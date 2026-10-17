package mx.mexibanco.transferencia;

public enum EstadoTransferencia {
        INICIADA,
        CARGADA,
        ABONADA,
        COMPLETADA,
        FALLIDA,
        COMPENSANDO,
        COMPENSADA,
        COMPENSACION_PENDIENTE
}