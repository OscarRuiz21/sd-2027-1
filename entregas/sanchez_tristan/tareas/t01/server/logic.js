/**
 * Lógica de negocio unificada para el servicio de búsqueda.
 */


const BASE_DE_DATOS = {
  "101": "Laptop Dell XPS 15 - 16GB RAM, 512GB SSD",
  "102": "Teclado Mecánico Keychron K2 - Switches Brown",
  "103": "Monitor LG UltraGear 27'' 144Hz IPS",
  "104": "Mouse Logitech MX Master 3S - Grafito"
};

/**
 * Consulta la base de datos en memoria por ID.
 * @param {string} id - Identificador a buscar.
 * @returns {{ id: string, informacion: string, encontrado: boolean }}
 */
function consultarPorId(id) {
  const idBuscado = String(id).trim();

  if (Object.prototype.hasOwnProperty.call(BASE_DE_DATOS, idBuscado)) {
    return {
      id: idBuscado,
      informacion: BASE_DE_DATOS[idBuscado],
      encontrado: true
    };
  }

  return {
    id: idBuscado,
    informacion: "No se encontraron datos para el ID especificado",
    encontrado: false
  };
}

module.exports = {
  consultarPorId,
  BASE_DE_DATOS
};
