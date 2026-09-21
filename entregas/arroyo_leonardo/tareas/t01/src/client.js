const { grpc, productosProto } = require('./proto');

const REST_BASE_URL = process.env.REST_BASE_URL || 'http://server:3000';
const GRPC_ADDRESS = process.env.GRPC_ADDRESS || 'server:50051';

async function consultarRest(id) {
  const response = await fetch(`${REST_BASE_URL}/productos/${id}`);
  const body = await response.json();
  console.log(`REST id=${id} -> HTTP ${response.status}: ${JSON.stringify(body)}`);
}

function consultarGrpc(cliente, id) {
  return new Promise((resolve) => {
    cliente.getProduct({ id }, (error, respuesta) => {
      if (error) {
        console.log(`gRPC id=${id} -> ${grpc.status[error.code]} (${error.code}): ${error.details}`);
      } else {
        console.log(`gRPC id=${id} -> OK: ${JSON.stringify(respuesta)}`);
      }
      resolve();
    });
  });
}

async function ejecutar() {
  const cliente = new productosProto.ProductosService(GRPC_ADDRESS, grpc.credentials.createInsecure());
  console.log('--- Producto existente ---');
  await consultarRest(1);
  await consultarGrpc(cliente, 1);
  console.log('--- Producto inexistente ---');
  await consultarRest(999);
  await consultarGrpc(cliente, 999);
  cliente.close();
}

ejecutar().catch((error) => {
  console.error('El cliente no pudo completar las consultas:', error);
  process.exit(1);
});
