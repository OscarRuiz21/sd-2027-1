PRODUCTS = {
    1: {"name": "sneakers", "value": 200, "stock": 4},
    2: {"name": "socks", "value": 20, "stock": 200},
    3: {"name": "tracksuit", "value": 200, "stock": 6}
}


def consult(product_id):
    return PRODUCTS.get(product_id)
