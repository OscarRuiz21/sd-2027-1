usuarios = {
    "1": {
        "nombre": "Ana",
        "correo": "ana@email.com"
    },
    "2": {
        "nombre": "Luis",
        "correo": "luis@email.com"
    },
    "3": {
        "nombre": "Maria",
        "correo": "maria@email.com"
    }
}


def buscar_usuario(id):
    return usuarios.get(id)