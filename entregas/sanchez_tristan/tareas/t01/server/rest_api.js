/**
 * Controlador REST (HTTP / JSON)
 */

const express = require('express');
const { consultarPorId } = require('./logic');

const app = express();
app.use(express.json());

// Endpoint GET /api/consulta/:id
app.get('/api/consulta/:id', (req, res) => {
  const { id } = req.params;
  const resultado = consultarPorId(id);

  // Establecer encabezados estándar y devolver respuesta JSON
  const status = resultado.encontrado ? 200 : 404;
  res.status(status).json(resultado);
});

// Endpoint alternativo por query param: GET /api/consulta?id=101
app.get('/api/consulta', (req, res) => {
  const id = req.query.id || '';
  const resultado = consultarPorId(id);
  const status = resultado.encontrado ? 200 : 404;
  res.status(status).json(resultado);
});

/**
 * Inicia el servidor REST en el puerto especificado.
 * @param {number} puerto 
 * @returns {import('http').Server}
 */
function iniciarServidorRest(puerto = 3000) {
  const server = app.listen(puerto, () => {
    console.log(`[REST] Servidor HTTP escuchando en el puerto ${puerto}`);
  });
  return server;
}

module.exports = {
  app,
  iniciarServidorRest
};
