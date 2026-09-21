FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY logic.py .
COPY server.py .
COPY servicio_pb2.py .
COPY servicio_pb2_grpc.py .

EXPOSE 5001
EXPOSE 50051

CMD ["python", "server.py"]
