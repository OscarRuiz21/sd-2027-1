USERS_DB = {
    1: {"id": 1, "name": "Usuario Uno", "email": "usuario1@example.com"},
    2: {"id": 2, "name": "Usuario Dos", "email": "usuario2@example.com"}
}

def find_user_by_id(user_id: int):
    """Misma lógica consumida por REST y gRPC"""
    return USERS_DB.get(user_id)
