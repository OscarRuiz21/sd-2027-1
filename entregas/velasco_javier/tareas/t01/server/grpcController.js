// Controlador gRPC: traduce protobuf <-> core.buscarBoing.
const path = require("path");
const grpc = require("@grpc/grpc-js");
const protoLoader = require("@grpc/proto-loader");
const core = require("./core");

const PROTO_PATH = process.env.PROTO_PATH || path.join(__dirname, "proto", "boings.proto");
const definicion = protoLoader.loadSync(PROTO_PATH, { keepCase: true, defaults: true });
const paquete = grpc.loadPackageDefinition(definicion).boings;

function obtenerBoing(call, callback) {
  const id = call.request.id;
  console.log(`[gRPC] ObtenerBoing(id=${id})`);
  const boing = core.buscarBoing(id); // <- la MISMA lógica
  if (!boing) return callback(null, { encontrado: false });
  callback(null, { encontrado: true, ...boing });
}

function iniciar(puerto) {
  const servidor = new grpc.Server();
  servidor.addService(paquete.Boings.service, { ObtenerBoing: obtenerBoing });
  servidor.bindAsync(`0.0.0.0:${puerto}`, grpc.ServerCredentials.createInsecure(), (err) => {
    if (err) throw err;
    console.log(`gRPC escuchando en :${puerto}`);
  });
}

module.exports = { iniciar };
