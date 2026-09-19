/**
 * Cliente unificado (REST + gRPC)
 * Realiza peticiones a ambas interfaces del servidor, valida la concordancia de la lógica
 * y realiza mediciones de los bytes transferidos (Punto Extra).
 */

const path = require('path');
const http = require('http');
const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const protobuf = require('protobufjs');

const SERVER_HOST = process.env.SERVER_HOST || 'localhost';
const REST_PORT = process.env.REST_PORT || 3000;
const GRPC_PORT = process.env.GRPC_PORT || 50051;

const PROTO_PATH = path.join(__dirname, '../proto/servicio.proto');

// Cargar definición Protobuf para gRPC cliente
const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true
});
const servicioProto = grpc.loadPackageDefinition(packageDefinition).servicio;

// Función para realizar peticiones HTTP REST
function hacerPeticionRest(host, port, idPath) {
    return new Promise((resolve, reject) => {
        const options = {
            hostname: host,
            port: port,
            path: `/api/consulta/${encodeURIComponent(idPath)}`,
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            }
        };

        const req = http.request(options, (res) => {
            let data = '';
            let rawHeaderSize = 0;

            // Calcular tamaño estimado de encabezados HTTP/1.1 recibidos
            const statusLine = `HTTP/${res.httpVersion} ${res.statusCode} ${res.statusMessage}\r\n`;
            rawHeaderSize += Buffer.byteLength(statusLine);
            for (const [key, val] of Object.entries(res.req._header ? {} : res.headers)) {
                rawHeaderSize += Buffer.byteLength(`${key}: ${val}\r\n`);
            }
            rawHeaderSize += 2; // \r\n final

            res.on('data', (chunk) => {
                data += chunk;
            });

            res.on('end', () => {
                try {
                    const jsonBody = JSON.parse(data);
                    const bodyBytes = Buffer.byteLength(data, 'utf8');
                    resolve({
                        statusCode: res.statusCode,
                        body: jsonBody,
                        jsonString: data,
                        bodyBytes: bodyBytes,
                        headerBytesEstimate: rawHeaderSize,
                        totalBytesEstimate: bodyBytes + rawHeaderSize
                    });
                } catch (err) {
                    reject(err);
                }
            });
        });

        req.on('error', (err) => reject(err));
        req.end();
    });
}

// Función para realizar peticiones gRPC
function hacerPeticionGrpc(client, idBusqueda) {
    return new Promise((resolve, reject) => {
        client.Consultar({ id: idBusqueda }, (err, response) => {
            if (err) {
                return reject(err);
            }
            resolve(response);
        });
    });
}

// Medidor de bytes Protobuf
async function medirProtobuf(id, responseObj) {
    const root = await protobuf.load(PROTO_PATH);
    const ConsultaRequest = root.lookupType('servicio.ConsultaRequest');
    const ConsultaResponse = root.lookupType('servicio.ConsultaResponse');

    const reqMsg = ConsultaRequest.create({ id });
    const reqBuf = ConsultaRequest.encode(reqMsg).finish();

    const resMsg = ConsultaResponse.create(responseObj);
    const resBuf = ConsultaResponse.encode(resMsg).finish();

    return {
        requestProtobufBytes: reqBuf.length,
        responseProtobufBytes: resBuf.length,
        grpcFrameHeaderBytes: 5 // 1 byte comprimido + 4 bytes longitud
    };
}

async function ejecutarPruebas() {
    console.log("=================================================");
    console.log("   CLIENTE UNIFICADO DE PRUEBAS (REST vs gRPC)   ");
    console.log("=================================================");
    console.log(`Conectando a Servidor: ${SERVER_HOST}`);
    console.log(`REST: http://${SERVER_HOST}:${REST_PORT}`);
    console.log(`gRPC: ${SERVER_HOST}:${GRPC_PORT}`);
    console.log("-------------------------------------------------\n");

    const grpcClient = new servicioProto.BuscadorService(
        `${SERVER_HOST}:${GRPC_PORT}`,
        grpc.credentials.createInsecure()
    );

    const idsParaProbar = ["101", "103", "999"];

    for (const id of idsParaProbar) {
        console.log(`>>> PROBANDO CONSULTA PARA ID: "${id}" <<<`);

        // 1. Petición REST
        let restRes;
        try {
            restRes = await hacerPeticionRest(SERVER_HOST, REST_PORT, id);
            console.log(`[REST] HTTP Status: ${restRes.statusCode}`);
            console.log(`[REST] Respuesta JSON:`, JSON.stringify(restRes.body));
            console.log(`[REST] Tamaño Payload JSON: ${restRes.bodyBytes} bytes`);
        } catch (e) {
            console.error(`[REST] Error: ${e.message}`);
        }

        // 2. Petición gRPC
        let grpcRes;
        try {
            grpcRes = await hacerPeticionGrpc(grpcClient, id);
            console.log(`[gRPC] Respuesta Obj:`, grpcRes);
            const pbMedicion = await medirProtobuf(id, grpcRes);
            console.log(`[gRPC] Tamaño Payload Protobuf Binario: ${pbMedicion.responseProtobufBytes} bytes`);
        } catch (e) {
            console.error(`[gRPC] Error: ${e.message}`);
        }

        // 3. Comparación de Medición de Bytes (Punto Extra)
        if (restRes && grpcRes) {
            const pbMedicion = await medirProtobuf(id, grpcRes);
            const jsonBytes = restRes.bodyBytes;
            const pbBytes = pbMedicion.responseProtobufBytes;
            const diferencia = jsonBytes - pbBytes;
            const ahorroPorcentaje = ((diferencia / jsonBytes) * 100).toFixed(2);

            console.log("\n  --- COMPARACIÓN DE TAMAÑO DE CARGA ÚTIL (PAYLOAD) ---");
            console.log(`  * REST (JSON):      ${jsonBytes} bytes`);
            console.log(`  * gRPC (Protobuf):  ${pbBytes} bytes (frame gRPC total: ${pbBytes + 5} bytes)`);
            console.log(`  * Diferencia net:   ${diferencia} bytes menor en gRPC (${ahorroPorcentaje}% más eficiente)`);
        }
        console.log("-------------------------------------------------\n");
    }

    grpcClient.close();
}

// Ejecutar cliente
ejecutarPruebas().catch((err) => {
    console.error("Error fatal en el cliente:", err);
    process.exit(1);
});
