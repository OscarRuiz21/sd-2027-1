usuarios = {
    1: {
        "nombre": "Mauricio",
        "edad": 22
    },
    2: {
        "nombre": "Ana",
        "edad": 21
    },
    3: {
        "nombre": "Carlos",
        "edad": 25
    }
}


def obtener_usuario(id_usuario):
    return usuarios.get(id_usuario)