const path = require('node:path');
const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');

const protoPath = path.join(__dirname, '..', 'proto', 'productos.proto');
const definition = protoLoader.loadSync(protoPath, {
  keepCase: false,
  longs: Number,
  enums: String,
  defaults: true,
  oneofs: true
});

const productosProto = grpc.loadPackageDefinition(definition).productos;
module.exports = { grpc, productosProto };
