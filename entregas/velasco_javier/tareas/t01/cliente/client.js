// Cliente que llama al mismo servicio por REST o por gRPC.
// Uso: node client.js --id 1 [--protocolo rest|grpc|ambos] [--veces N] [--tiempos]
//   --tiempos : en vez de imprimir respuestas, mide la latencia de N llamadas por protocolo
const path = require("path");
const grpc = require("@grpc/grpc-js");
const protoLoader = require("@grpc/proto-loader");

const HOST = process.env.SERVIDOR_HOST || "localhost";
const PUERTO_REST = process.env.PUERTO_REST || "8080";
const PUERTO_GRPC = process.env.PUERTO_GRPC || "50051";
const PROTO_PATH = process.env.PROTO_PATH || path.join(__dirname, "proto", "boings.proto");

function args() {
  const a = { id: null, protocolo: "ambos", veces: null, tiempos: false };
  const v = process.argv.slice(2);
  for (let i = 0; i < v.length; i++) {
    if (v[i] === "--id") a.id = Number(v[++i]);
    else if (v[i] === "--protocolo") a.protocolo = v[++i];
    else if (v[i] === "--veces") a.veces = Number(v[++i]);
    else if (v[i] === "--tiempos") a.tiempos = true;
  }
  if (a.id === null || Number.isNaN(a.id) || !["rest", "grpc", "ambos"].includes(a.protocolo)) {
    console.error("Uso: node client.js --id N [--protocolo rest|grpc|ambos] [--veces N] [--tiempos]");
    process.exit(2);
  }
  if (a.veces === null) a.veces = a.tiempos ? 100 : 1;
  return a;
}

async function porRest(id) {
  const r = await fetch(`http://${HOST}:${PUERTO_REST}/boings/${id}`); // 404 = "no hay datos"
  return r.json();
}

function crearClienteGrpc() {
  const def = protoLoader.loadSync(PROTO_PATH, { keepCase: true, defaults: true });
  const paquete = grpc.loadPackageDefinition(def).boings;
  return new paquete.Boings(`${HOST}:${PUERTO_GRPC}`, grpc.credentials.createInsecure());
}

function porGrpc(cliente, id) {
  return new Promise((resolve, reject) => {
    cliente.ObtenerBoing({ id }, (err, r) => {
      if (err) return reject(err);
      if (!r.encontrado) return resolve({ encontrado: false, mensaje: "no hay datos" });
      resolve({ encontrado: true, id: r.id, nombre: r.nombre, precio: r.precio, stock: r.stock });
    });
  });
}

// ---- medición de tiempo ----
async function cronometrar(fn) {
  const t0 = performance.now();
  await fn();
  return performance.now() - t0;
}

async function medir(fn, veces) {
  const primera = await cronometrar(fn); // incluye abrir conexión / arranque de HTTP/2
  const ms = [];
  for (let i = 0; i < veces; i++) ms.push(await cronometrar(fn)); // conexión ya abierta
  const s = [...ms].sort((x, y) => x - y);
  const q = (p) => s[Math.min(s.length - 1, Math.floor(p * s.length))];
  return { primera, promedio: ms.reduce((x, y) => x + y, 0) / ms.length, mediana: q(0.5), p95: q(0.95), min: s[0] };
}

function fila(nombre, r) {
  const f = (x) => x.toFixed(2);
  return `| ${nombre} | ${f(r.primera)} | ${f(r.promedio)} | ${f(r.mediana)} | ${f(r.p95)} | ${f(r.min)} |`;
}

async function modoTiempos(a, cliente) {
  console.log(`Midiendo id=${a.id}, ${a.veces} llamadas por protocolo (más 1 llamada inicial aparte)\n`);
  console.log("| Protocolo | 1ª llamada (ms) | Promedio (ms) | Mediana (ms) | p95 (ms) | Mínimo (ms) |");
  console.log("|---|---|---|---|---|---|");
  if (a.protocolo !== "grpc") console.log(fila("REST", await medir(() => porRest(a.id), a.veces)));
  if (a.protocolo !== "rest") console.log(fila("gRPC", await medir(() => porGrpc(cliente, a.id), a.veces)));
}

async function main() {
  const a = args();
  const cliente = crearClienteGrpc();
  if (a.tiempos) {
    await modoTiempos(a, cliente);
  } else {
    for (let i = 0; i < a.veces; i++) {
      if (a.protocolo !== "grpc") console.log("REST :", await porRest(a.id));
      if (a.protocolo !== "rest") console.log("gRPC :", await porGrpc(cliente, a.id));
    }
  }
  cliente.close();
}

main().catch((e) => { console.error("Error:", e.message); process.exit(1); });
