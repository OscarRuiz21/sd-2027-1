package mx.mexibanco.transferencia;

/**
 * Estados de la saga orquestada de transferencia.
 *
 * Flujo normal:
 *   INICIADA -> CARGADA -> COMPLETADA
 *
 * Flujo con compensacion exitosa:
 *   INICIADA -> CARGADA -> COMPENSANDO -> COMPENSADA
 *
 * Flujo con fallo en compensacion:
 *   INICIADA -> CARGADA -> COMPENSANDO -> COMPENSACION_PENDIENTE
 *
 * Fallo previo a tocar saldo (cuenta no existe, saldo insuficiente, circuito abierto):
 *   INICIADA -> FALLIDA
 */
public enum EstadoTransferencia {
	INICIADA,
	CARGADA,
	COMPLETADA,
	COMPENSANDO,
	COMPENSADA,
	COMPENSACION_PENDIENTE,
	FALLIDA
}
