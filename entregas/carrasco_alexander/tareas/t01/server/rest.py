#Importamos FastAPI que nos permitirá crear nuestra aplicación REST 
from fastapi import FastAPI
#Traemos la función buscar_por_id 
from server.service import buscar_por_id

app = FastAPI() # Es como nuestro servidor REST 


@app.get("/personas/{id}") #Cuando alguien haga una petición HTTP GET a /personas/<id>, ejecutará la función de abajo 
def obtener_persona(id: int):
    persona = buscar_por_id(id)

    if persona is None:
        return {"mensaje": "No hay datos"}

    return persona