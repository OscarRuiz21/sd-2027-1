# Diccionario en memoria compartido por ambas interfaces
FAKE_DB = {
    "1": {"id": "1", "name": "Laptop Pro", "description": "16GB RAM, 512GB SSD", "price": 1200.50},
    "2": {"id": "2", "name": "Teclado Mecánico", "description": "RGB Switch Red", "price": 85.00},
    "3": {"id": "3", "name": "Mouse Inalámbrico", "description": "Ergonómico Bluetooth", "price": 45.30}
}

def get_item_from_db(item_id: str):
    return FAKE_DB.get(item_id)