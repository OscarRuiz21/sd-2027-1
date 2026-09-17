FROM python:3.13-slim

WORKDIR /app

COPY cliente/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt grpcio-tools

COPY proto/ ./proto/
COPY cliente/ .

RUN python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/products.proto

CMD ["python", "cliente.py"]