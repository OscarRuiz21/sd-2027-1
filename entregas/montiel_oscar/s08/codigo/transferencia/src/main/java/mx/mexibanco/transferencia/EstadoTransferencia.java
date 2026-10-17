package mx.mexibanco.transferencia;

/** Estado durable de cada paso de la saga. Los estados EN_CURSO advierten una salida incierta tras un reinicio. */
public enum EstadoTransferencia {
	INICIADA,
	CARGO_EN_CURSO,
	CARGADA,
	ABONO_EN_CURSO,
	ABONADA,
	COMPLETADA,
	RECHAZADA,
	CARGO_INCIERTO,
	COMPENSACION_EN_CURSO,
	COMPENSADA,
	COMPENSACION_PENDIENTE,
	REGISTRO_PENDIENTE
}
