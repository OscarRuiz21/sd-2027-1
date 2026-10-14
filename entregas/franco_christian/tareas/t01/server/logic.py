datos = {
    1: {
        "nombre": "Christian",
        "carrera": "Ingeniería en Computación"
    },
    2: {
        "nombre": "Alice",
        "carrera": "Inteligencia Artificial"
    },
    3: {
        "nombre": "UNAM",
        "carrera": "Universidad"
    }
}


def obtener_dato(id):
    return datos.get(id)
