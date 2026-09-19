FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY logic.py .
COPY service.proto .
COPY service_pb2.py .
COPY service_pb2_grpc.py .
COPY server.py .

EXPOSE 5000
EXPOSE 50051

CMD ["python", "server.py"]