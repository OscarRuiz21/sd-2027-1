USERS_DB = {
    1: {"id": 1, "name": "Diego Contreras", "email": "dieg7074@gmail.com"},
    2: {"id": 2, "name": "Adrian Contreras", "email": "diegocontrras.fi@gmail.com"}
}

def find_user_by_id(user_id: int):
    """Misma lógica consumida por REST y gRPC"""
    return USERS_DB.get(user_id)