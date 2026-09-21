FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY client.py .
COPY servicio_pb2.py .
COPY servicio_pb2_grpc.py .

ENTRYPOINT ["python", "client.py"]

CMD ["rest", "1"]
