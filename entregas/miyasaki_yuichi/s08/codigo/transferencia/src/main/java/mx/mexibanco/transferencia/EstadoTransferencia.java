package mx.mexibanco.transferencia;

/**
 * En que paso de la saga va cada transferencia. Se guarda despues de cada paso: si el servicio
 * se cae a la mitad, la base dice donde se quedo.
 *
 * Camino feliz: INICIADA -> CARGADA -> ABONADA -> COMPLETADA.
 * Si falla el cargo: INICIADA -> FALLIDA (no salio dinero, no hay nada que compensar).
 * Si falla el abono: CARGADA -> COMPENSADA, o CARGADA -> COMPENSACION_PENDIENTE si la devolucion
 * tambien falla.
 */
public enum EstadoTransferencia {
	INICIADA,
	CARGADA,
	ABONADA,
	COMPLETADA,
	FALLIDA,
	COMPENSADA,
	COMPENSACION_PENDIENTE
}
