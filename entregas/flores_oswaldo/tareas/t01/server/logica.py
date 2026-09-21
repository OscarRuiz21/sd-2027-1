basededatos = {
    "1": {"nombre": "Oscar Manuel", "rol": "CEO", "edad": 27},
    "2": {"nombre": "Jimena Valencia", "rol": "Arquitecta de software", "edad": 30},
    "3": {"nombre": "Oswaldo Flores", "rol": "Programador Full stack", "edad": 50},
}


def buscar(id_):
    return basededatos.get(id_)