FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY servicio.proto .
RUN python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. servicio.proto
COPY . .
EXPOSE 8000 50051
CMD ["python", "main.py"]
