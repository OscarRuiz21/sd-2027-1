FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY service.proto .
RUN python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. service.proto

COPY service.py rest_app.py grpc_app.py main.py ./

EXPOSE 8000 50051

CMD ["python", "main.py"]
