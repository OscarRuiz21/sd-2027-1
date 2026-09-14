//librerias
const express = require('express');
const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const logica = require('./logica');
const fs = require('fs');

const PROTO_PATH = fs.existsSync('./api.proto') ? './api.proto' : '../api.proto';

const app = express();
app.use(express.json());

//rest
app.get('/api/data/:id', (req,res)=>{
    const id = req.params.id;
    const resultado = logica.getData(id);
    res.json(resultado);
})

// grpc
const packageDefinition = protoLoader.loadSync(PROTO_PATH,{
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true
});
const protoDes = grpc.loadPackageDefinition(packageDefinition);
const apiProto = protoDes.api;

function buscarGrpc(call, callback){
    const id = call.request.id;
    const resultado = logica.getData(id);
    callback(null, resultado);
}

const grpcServer = new grpc.Server();
grpcServer.addService(apiProto.Buscar.service, {Busqueda: buscarGrpc});

const REST_PORT = 8080;
const GRPC_PORT = 50051;

app.listen(REST_PORT,() =>{
    console.log(`[REST] Puerto ${REST_PORT}`);
});

grpcServer.bindAsync(`0.0.0.0:${GRPC_PORT}`, grpc.ServerCredentials.createInsecure(), (err,prot) =>{
    if(err){
        console.error(err);
        return;
    }
    console.log(`[gRPC] Puerto ${prot}`);
});