FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY calculator.proto .
COPY calculator_pb2.py .
COPY calculator_pb2_grpc.py .
COPY server.py .

EXPOSE 50051

CMD ["python", "server.py"]