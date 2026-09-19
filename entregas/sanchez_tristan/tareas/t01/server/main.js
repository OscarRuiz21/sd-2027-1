/**
 * Punto de entrada principal del servidor unificado.
 **/

const { iniciarServidorRest } = require('./rest_api');
const { iniciarServidorGrpc } = require('./grpc_server');

const PUERTO_REST = process.env.REST_PORT || 3000;
const PUERTO_GRPC = process.env.GRPC_PORT || 50051;

console.log("=================================================");
console.log(" Iniciando Servidor Unificado (REST + gRPC)... ");
console.log(" Lógica de negocio compartida en: server/logic.js");
console.log("=================================================");

iniciarServidorRest(PUERTO_REST);
iniciarServidorGrpc(PUERTO_GRPC);
