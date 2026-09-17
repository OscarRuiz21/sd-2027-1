from fastapi import FastAPI, HTTPException

from logica import consult

app = FastAPI()


@app.get("/products/{product_id}")
def get_product(product_id: int):
    product = consult(product_id)          # <- la misma lógica del paso 1
    if product is None:
        raise HTTPException(status_code=404, detail="No hay datos para ese ID")
    return product
