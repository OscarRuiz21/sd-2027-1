FROM python:3.12-slim

WORKDIR /app

COPY server/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Genera product_pb2.py y product_pb2_grpc.py desde el contrato .proto.
COPY proto/product.proto /proto/product.proto
RUN python -m grpc_tools.protoc \
    -I/proto \
    --python_out=/app \
    --grpc_python_out=/app \
    /proto/product.proto

COPY server/*.py .

EXPOSE 8080 50051

CMD ["python", "main.py"]
