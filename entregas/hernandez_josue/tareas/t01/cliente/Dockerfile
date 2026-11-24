FROM python:3.12-slim
WORKDIR /app

COPY requisitos.txt .
RUN pip install --no-cache-dir --timeout 90 -r requisitos.txt

COPY proto/catalogo.proto .
RUN python -m grpc_tools.protoc -I . --python_out=. --grpc_python_out=. catalogo.proto

COPY cliente/cliente.py .
ENTRYPOINT ["python", "-u", "cliente.py"]
