/**
 * Controlador gRPC (HTTP/2 / Protobuf)
 */

const path = require('path');
const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const { consultarPorId } = require('./logic');

const PROTO_PATH = path.join(__dirname, '../proto/servicio.proto');

// Cargar archivo .proto
const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true
});

const servicioProto = grpc.loadPackageDefinition(packageDefinition).servicio;

/**
 * Implementación del procedimiento RPC Consultar
 */
function consultar(call, callback) {
  const { id } = call.request;
  const resultado = consultarPorId(id);
  // Retorna respuesta sin error en transporte gRPC (status OK)
  callback(null, resultado);
}

/**
 * Inicia el servidor gRPC en el puerto especificado.
 * @param {number} puerto 
 */
function iniciarServidorGrpc(puerto = 50051) {
  const server = new grpc.Server();
  server.addService(servicioProto.BuscadorService.service, {
    Consultar: consultar
  });

  const direccion = `0.0.0.0:${puerto}`;
  server.bindAsync(direccion, grpc.ServerCredentials.createInsecure(), (err, port) => {
    if (err) {
      console.error(`[gRPC] Error al iniciar servidor: ${err.message}`);
      return;
    }
    console.log(`[gRPC] Servidor gRPC escuchando en el puerto ${port}`);
  });

  return server;
}

module.exports = {
  iniciarServidorGrpc,
  PROTO_PATH
};
