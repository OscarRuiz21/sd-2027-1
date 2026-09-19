from fastapi import FastAPI, HTTPException
from service import find_user_by_id

app = FastAPI()

@app.get("/users/{user_id}")
def get_user_rest(user_id: int):
    user = find_user_by_id(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")
    return user