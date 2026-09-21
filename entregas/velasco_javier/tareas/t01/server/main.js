// Un solo proceso, una sola lógica, dos puertos.
const restController = require("./restController");
const grpcController = require("./grpcController");

const PUERTO_REST = Number(process.env.PUERTO_REST || 8080);
const PUERTO_GRPC = Number(process.env.PUERTO_GRPC || 50051);

grpcController.iniciar(PUERTO_GRPC);
restController.crearServidor().listen(PUERTO_REST, "0.0.0.0", () =>
  console.log(`REST escuchando en :${PUERTO_REST}`)
);
