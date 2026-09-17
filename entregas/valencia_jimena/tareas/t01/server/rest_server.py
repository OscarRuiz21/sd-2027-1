from flask import Flask, jsonify
from service import get_item_by_id

app = Flask(__name__)


@app.route("/items/<item_id>", methods=["GET"])
def get_item(item_id):
    result = get_item_by_id(item_id)

    if not result["found"]:
        body = {"error": "sin datos", "id": item_id}
        print(f"[REST] GET /items/{item_id} -> 404")
        return jsonify(body), 404

    print(f"[REST] GET /items/{item_id} -> 200")
    return jsonify(result["item"]), 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080)