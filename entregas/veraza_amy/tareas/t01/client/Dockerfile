FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY proto ./proto
RUN python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/catalog.proto
COPY client.py benchmark.py test_services.py openapi.yaml ./
USER 10001
CMD ["python", "client.py", "1"]
