FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY client.py .
COPY service.proto .
COPY service_pb2.py .
COPY service_pb2_grpc.py .

CMD ["python", "client.py"]