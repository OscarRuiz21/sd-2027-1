FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY servicio.proto .
RUN python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. servicio.proto
COPY . .
CMD ["python", "client.py"]
