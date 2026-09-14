const axios = require('axios');
const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const fs = require('fs');
const { rejects } = require('assert');

const SERVER_HOST = process.env.SERVER_HOST || 'localhost';
const REST_URL = `http://${SERVER_HOST}:8080`;
const GRPC_URL = `${SERVER_HOST}:50051`;

const PROTO_PATH = fs.existsSync('./api.proto') ? './api.proto' : '../api.proto';
const packageDefinition = protoLoader.loadSync(PROTO_PATH,{
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true
});
const apiProto =grpc.loadPackageDefinition(packageDefinition).api;

const grpcClient = new apiProto.Buscar(GRPC_URL, grpc.credentials.createInsecure());

async function llamarRest(id){
    console.log(`\n[REST] Buscando ID ${id}`);
    try{
        const respuesta = await axios.get(`${REST_URL}/api/data/${id}`);
        console.log("[REST] Respuesta recibida:", respuesta.data);
    } catch (error){
        console.error("[REST] Error en la petición:", error.message);
    }
}


function llamarGrpc(id){
   console.log(`\n[gRPC] Buscando ID ${id}`);
   return new Promise((resolve, reject) => {
   grpcClient.Busqueda({ id: id }, (error, respuesta) => {
            if (error) {
                console.error("[gRPC] Error en la petición:", error);
                reject(error);
            } else {
                console.log("[gRPC] Respuesta recibida:", respuesta);
                resolve(respuesta);
            }
        });
    });
}


async function pruebas(){
    console.log(`Iniciando cliente ${SERVER_HOST}`);
    console.log("==== ID existente =====");
    await llamarRest("1");
    await llamarGrpc("1");

    console.log("==== ID NO existente =====");
    await llamarRest("99");
    await llamarGrpc("99");
}


pruebas();