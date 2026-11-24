LIBROS = {
    "L01": {"id": "L01", "titulo": "Pedro Páramo", "anio": 1955},
    "L02": {"id": "L02", "titulo": "La tregua", "anio": 1960},
}


def buscar_libro(identificador):
    return LIBROS.get(identificador)
