FROM python:3.9-slim
WORKDIR /app
COPY . .
RUN pip install flask grpcio grpcio-tools requests
RUN python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. servicio.proto