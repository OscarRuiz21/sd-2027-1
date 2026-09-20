const express = require('express');
const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');

// base de datos
const baseDeDatos = {
    "1": { nombre: "Paracetamol", precio: 35.50 },
    "2": { nombre: "Loratadina", precio: 45.00 },
    "3": { nombre: "Escitalopram", precio: 25.00 }
};

//  función que busca el medicamento
function buscarMedicamento(id) {
    console.log(`[Lógica] Búsqueda de medicamento con ID: ${id}`);
    if (baseDeDatos[id]) {
        return { 
            id: id, 
            nombre: baseDeDatos[id].nombre, 
            precio: baseDeDatos[id].precio, 
            encontrado: true 
        };
    }
    return { id: id, nombre: "", precio: 0.0, encontrado: false };
}

// REST
const app = express();
app.get('/api/medicamentos/:id', (req, res) => {
    const id = req.params.id;
    console.log(`\n[REST] Petición recibida GET /api/medicamentos/${id}`);
    const resultado = buscarMedicamento(id); 
    if (resultado.encontrado) {
        res.status(200).json(resultado);
    } else {
        res.status(404).json({ error: "Medicamento no encontrado" });
    }
});

app.listen(8080, '0.0.0.0', () => {
    console.log('Servidor REST escuchando en http://0.0.0.0:8080');
});

// GRPC
const PROTO_PATH = path.join(__dirname, '../proto/servicio.proto');
const packageDefinition = protoLoader.loadSync(PROTO_PATH);
const farmayaProto = grpc.loadPackageDefinition(packageDefinition).farmaya;
const server = new grpc.Server();
server.addService(farmayaProto.Catalogo.service, {
    ObtenerMedicamento: (llamada, callback) => {
        const id = llamada.request.id;
        console.log(`\n[gRPC] Petición RPC ObtenerMedicamento (ID: ${id})`);
        const resultado = buscarMedicamento(id); 
        callback(null, resultado);
    }
});

server.bindAsync('0.0.0.0:9090', grpc.ServerCredentials.createInsecure(), () => {
    console.log('Servidor gRPC escuchando en 0.0.0.0:9090');
});