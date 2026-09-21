usuarios = {
    1: {"id": 1, "nombre": "Bali Yandrei", "carrera": "Ingenieria"},
    2: {"id": 2, "nombre": "Juarez Josue", "carrera": "Arquitectura"},
    3: {"id": 3, "nombre": "Huerta Ximena", "carrera": "Medicina"}
}


def buscar_usuario(id_usuario):
    return usuarios.get(id_usuario)