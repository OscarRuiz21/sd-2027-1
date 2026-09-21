const express = require('express');
const { getProductById } = require('./catalogo');
const { grpc, productosProto } = require('./proto');

const REST_PORT = Number(process.env.REST_PORT || 3000);
const GRPC_PORT = Number(process.env.GRPC_PORT || 50051);

function bytesDeJson(payload) {
  return Buffer.byteLength(JSON.stringify(payload), 'utf8');
}

function registrarBytes(protocolo, id, estado, bytes) {
  console.log(`[medicion] ${protocolo} id=${id} estado=${estado} payload=${bytes} bytes`);
}

function crearAppRest() {
  const app = express();
  app.get('/health', (_req, res) => res.status(200).json({ status: 'ok' }));

  app.get('/productos/:id', (req, res) => {
    const producto = getProductById(req.params.id);
    if (!producto) {
      const error = {
        error: 'PRODUCT_NOT_FOUND',
        message: `No existe el producto con id ${req.params.id}`
      };
      registrarBytes('REST', req.params.id, 404, bytesDeJson(error));
      return res.status(404).json(error);
    }

    registrarBytes('REST', producto.id, 200, bytesDeJson(producto));
    return res.status(200).json(producto);
  });

  return app;
}

function getProductGrpc(call, callback) {
  const producto = getProductById(call.request.id);
  if (!producto) {
    return callback({
      code: grpc.status.NOT_FOUND,
      details: `No existe el producto con id ${call.request.id}`
    });
  }

  const bytes = productosProto.ProductResponse.serialize(producto).length;
  registrarBytes('gRPC', producto.id, 'OK', bytes);
  return callback(null, producto);
}

async function iniciarServidor() {
  const app = crearAppRest();
  const httpServer = app.listen(REST_PORT, '0.0.0.0', () => {
    console.log(`REST escuchando en http://0.0.0.0:${REST_PORT}`);
  });

  const grpcServer = new grpc.Server();
  grpcServer.addService(productosProto.ProductosService.service, { getProduct: getProductGrpc });

  await new Promise((resolve, reject) => {
    grpcServer.bindAsync(`0.0.0.0:${GRPC_PORT}`, grpc.ServerCredentials.createInsecure(), (error, port) => {
      if (error) return reject(error);
      console.log(`gRPC escuchando en 0.0.0.0:${port}`);
      return resolve();
    });
  });

  const detener = () => {
    console.log('Cerrando servidor...');
    httpServer.close();
    grpcServer.tryShutdown(() => process.exit(0));
  };
  process.once('SIGTERM', detener);
  process.once('SIGINT', detener);
}

iniciarServidor().catch((error) => {
  console.error('No se pudo iniciar el servidor:', error);
  process.exit(1);
});
