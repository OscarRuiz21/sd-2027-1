import sqlite3
import time
from flask import Flask, request, jsonify

app = Flask(__name__)

def init_db():
    conn = sqlite3.connect('pagos.db')
    c = conn.cursor()
    c.execute('''
        CREATE TABLE IF NOT EXISTS cobros (
            idempotency_key TEXT UNIQUE,
            monto REAL,
            estado TEXT
        )
    ''')
    conn.commit()
    conn.close()

@app.route('/cobrar', methods=['POST'])
def cobrar():
    idem_key = request.headers.get('Idempotency-Key')
    if not idem_key:
        return jsonify({"error": "Falta el header Idempotency-Key"}), 400
    
    datos = request.json or {}
    monto = datos.get('monto', 0)

    conn = sqlite3.connect('pagos.db')
    c = conn.cursor()

    try:
        c.execute("INSERT INTO cobros (idempotency_key, monto, estado) VALUES (?, ?, ?)", 
                  (idem_key, monto, 'procesando'))
        conn.commit()
    except sqlite3.IntegrityError:
        conn.close()
        return jsonify({"error": "Petición duplicada."}), 409

    time.sleep(2) 
    c.execute("UPDATE cobros SET estado = 'completado' WHERE idempotency_key = ?", (idem_key,))
    conn.commit()
    conn.close()

    return jsonify({"mensaje": "Cobro exitoso", "idempotency_key": idem_key, "monto": monto}), 200

if __name__ == '__main__':
    init_db()
    app.run(port=5000)
