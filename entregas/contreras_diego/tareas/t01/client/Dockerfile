FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY service.proto .
RUN python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. service.proto

COPY client.py ./

CMD ["python", "client.py"]
