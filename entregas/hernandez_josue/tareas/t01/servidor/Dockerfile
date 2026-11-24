FROM python:3.12-slim
WORKDIR /app

COPY requisitos.txt .
RUN pip install --no-cache-dir -r requisitos.txt Flask==3.1.2

COPY proto/catalogo.proto .
RUN python -m grpc_tools.protoc -I . --python_out=. --grpc_python_out=. catalogo.proto

COPY servidor/app.py servidor/catalogo.py ./
EXPOSE 8080 50051
CMD ["python", "-u", "app.py"]
