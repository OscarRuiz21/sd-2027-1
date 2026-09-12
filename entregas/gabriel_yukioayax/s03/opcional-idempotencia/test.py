import threading
import requests
import uuid

URL = "http://127.0.0.1:5000/cobrar"
LLAVE = str(uuid.uuid4())
PAYLOAD = {"monto": 500.0}
HEADERS = {"Idempotency-Key": LLAVE, "Content-Type": "application/json"}

def hacer_peticion(id_hilo):
    print(f"Hilo {id_hilo}: Enviando petición con llave {LLAVE[:8]}...")
    try:
        respuesta = requests.post(URL, json=PAYLOAD, headers=HEADERS)
        print(f"Hilo {id_hilo}: Código {respuesta.status_code} -> {respuesta.json()}")
    except Exception as e:
        print(f"Hilo {id_hilo}: Error de conexión.")

hilo1 = threading.Thread(target=hacer_peticion, args=(1,))
hilo2 = threading.Thread(target=hacer_peticion, args=(2,))

hilo1.start()
hilo2.start()
hilo1.join()
hilo2.join()
