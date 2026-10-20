package mx.mexibanco.transferencia;

public enum EstadoTransferencia {
	INICIADA,
	CARGADA,
	ABONADA,
	COMPLETADA,
	FALLIDA,                 // falló el cargo: no salió dinero
	COMPENSADA,              // falló el abono y se devolvió el monto al origen
	COMPENSACION_PENDIENTE   // falló el abono y también la devolución
}