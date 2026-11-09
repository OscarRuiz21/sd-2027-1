const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');

// llamada REST
async function llamarREST(id) {
    console.log(`\n--- Llamando por REST (ID: ${id}) ---`);
    const inicio = performance.now();
    try {
        const res = await fetch(`http://server:8080/api/medicamentos/${id}`);
        
        // MEDICIÓN PARA EL PUNTO EXTRA
        const textoCrudo = await res.text();
        const bytesREST = Buffer.byteLength(textoCrudo, 'utf8');
        console.log(`Payload REST medido: ${bytesREST} bytes`);
        
        const data = JSON.parse(textoCrudo);
        console.log(`Respuesta REST:`, data);
        console.log(`Tiempo: ${(performance.now() - inicio).toFixed(2)} ms`);
    } catch (error) {
        console.error("Error en REST:", error.message);
    }
}

// Llamada GRPC
const PROTO_PATH = path.join(__dirname, '../proto/servicio.proto');
const packageDefinition = protoLoader.loadSync(PROTO_PATH);
const farmayaProto = grpc.loadPackageDefinition(packageDefinition).farmaya;
const clienteGrpc = new farmayaProto.Catalogo('server:9090', grpc.credentials.createInsecure());

function llamarGRPC(id) {
    console.log(`\n--- Llamando por gRPC (ID: ${id}) ---`);
    const inicio = performance.now();
    clienteGrpc.ObtenerMedicamento({ id: id }, (error, respuesta) => {
        if (!error) {
            console.log(`Respuesta gRPC:`, respuesta);
            console.log(`Tiempo: ${(performance.now() - inicio).toFixed(2)} ms`);
        } else {
            console.error("Error en gRPC:", error.message);
        }
    });
}

// EJECUCIÓN CORREGIDA
async function ejecutar() {
    console.log("Iniciando pruebas de cliente...");
    await llamarREST("1");  // REST - ID Existente
    llamarGRPC("1");        // gRPC - ID Existente (mismo ID)
    
    // Pequeña asincronía visual
    setTimeout(() => {
        llamarGRPC("99");   // gRPC - ID Inexistente
    }, 500);
}

ejecutar();