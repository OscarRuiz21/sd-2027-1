FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY proto ./proto
RUN python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/catalog.proto
COPY catalog.py rest_server.py grpc_server.py server.py ./
USER 10001
EXPOSE 8080 50051
CMD ["python", "-u", "server.py"]
