# servidor/db.py

GENIUS = {
    "1": {"name": "Ada Lovelace"},
    "2": {"name": "Alan Turing"},
    "3": {"name": "Grace Hopper"}
}

def get_data_by_id(requested_id):
   
    # Si el ID existe, retorna los datos y un error vacío.
    if requested_id in GENIUS:
        return {
            "success": True,
            "id": requested_id,
            "name": GENIUS[requested_id]["name"],
            "error": ""
        }
    
    # Si el ID no existe, retorna un  mensaje de error.
    else:
        return {
            "success": False,
            "id": "",
            "name": "",
            "error": "Data not found"
        }

