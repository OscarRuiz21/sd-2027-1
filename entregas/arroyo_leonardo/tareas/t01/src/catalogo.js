/**
 * Esta es la única capa que conoce los datos del catálogo. Los controladores
 * REST y gRPC solo traducen su protocolo hacia y desde esta función.
 */
const productos = new Map([
  [1, { id: 1, nombre: 'Teclado mecanico', precio: 1499.0, disponible: true }],
  [2, { id: 2, nombre: 'Mouse inalambrico', precio: 699.5, disponible: true }],
  [3, { id: 3, nombre: 'Monitor 24 pulgadas', precio: 3299.0, disponible: false }]
]);

function getProductById(id) {
  const idNumerico = Number(id);
  if (!Number.isInteger(idNumerico)) return null;

  const producto = productos.get(idNumerico);
  return producto ? { ...producto } : null;
}

module.exports = { getProductById };
