// LA lógica de negocio. Única, compartida por REST y por gRPC.
// No importa http ni grpc: solo sabe qué es un boing.

// "Base de datos" en memoria
const CATALOGO = new Map([
  [1, { id: 1, nombre: "Boing de manzana", precio: 18.5, stock: 40 }],
  [2, { id: 2, nombre: "Boing de mango", precio: 17.0, stock: 25 }],
  [3, { id: 3, nombre: "Boing de guayaba", precio: 17.0, stock: 0 }],
  [4, { id: 4, nombre: "Boing de tamarindo", precio: 19.0, stock: 12 }],
]);

// Devuelve el boing o null si el ID no existe.
function buscarBoing(id) {
  return CATALOGO.get(id) ?? null;
}

module.exports = { buscarBoing };
