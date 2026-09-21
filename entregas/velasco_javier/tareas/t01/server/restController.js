// Controlador REST: traduce HTTP+JSON <-> core.buscarBoing.
const http = require("http");
const core = require("./core");

function responder(res, codigo, cuerpo) {
  const datos = Buffer.from(JSON.stringify(cuerpo), "utf-8");
  res.writeHead(codigo, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": datos.length,
  });
  res.end(datos);
}

function crearServidor() {
  return http.createServer((req, res) => {
    console.log(`[REST] ${req.method} ${req.url}`);
    if (req.method !== "GET") return responder(res, 405, { mensaje: "método no permitido" });
    if (req.url === "/health") return responder(res, 200, { status: "ok" });

    const m = req.url.match(/^\/boings\/(\d+)$/);
    if (!m) return responder(res, 404, { encontrado: false, mensaje: "ruta no existe" });

    const boing = core.buscarBoing(Number(m[1])); // <- la lógica compartida
    if (!boing) return responder(res, 404, { encontrado: false, mensaje: "no hay datos" });
    responder(res, 200, { encontrado: true, ...boing });
  });
}

module.exports = { crearServidor };
