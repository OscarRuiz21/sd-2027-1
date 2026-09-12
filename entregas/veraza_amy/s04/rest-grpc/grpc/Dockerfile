FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/catalog.proto
USER 10001
EXPOSE 50051
CMD ["python", "-u", "grpc_server.py"]
