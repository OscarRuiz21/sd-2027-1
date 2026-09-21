FROM python:3.12-slim

WORKDIR /app

COPY client/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Genera el código que necesita el cliente para comunicarse por gRPC.
COPY proto/product.proto /proto/product.proto
RUN python -m grpc_tools.protoc \
    -I/proto \
    --python_out=/app \
    --grpc_python_out=/app \
    /proto/product.proto

COPY client/client.py .

ENTRYPOINT ["python", "client.py"]
CMD ["ambos", "1"]
