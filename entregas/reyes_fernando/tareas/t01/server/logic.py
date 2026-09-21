USUARIOS = {
    1: {
        "id": 1,
        "nombre": "Luis",
        "materia": "Sistemas Distribuidos",
        "correo": "luis.garcia@correo.com",
        "celular": "5512345678"
    },
    2: {
        "id": 2,
        "nombre": "Gabriela",
        "materia": "Computacion Grafica",
        "correo": "gabriela.lopez@correo.com",
        "celular": "5587654321"
    },
    3: {
        "id": 3,
        "nombre": "Juan",
        "materia": "Criptografia",
        "correo": "juan.martinez@correo.com",
        "celular": "5543218765"
    }
}


def buscar_usuario(usuario_id):
    return USUARIOS.get(usuario_id)
