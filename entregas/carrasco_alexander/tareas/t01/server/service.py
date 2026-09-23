#Creamos un diccionario llamado personas 
personas = {
    1: {"id": 1, "nombre": "Alex"},
    2: {"id": 2, "nombre": "Diego"},
    3: {"id": 3, "nombre": "Carrasco"}
}
#Creamos una función de busqueda 
def buscar_por_id(id):
    if id in personas:
        return personas[id]
    else:
        return None
