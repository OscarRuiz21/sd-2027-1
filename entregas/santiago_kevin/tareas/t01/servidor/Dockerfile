FROM python:3.13-slim

WORKDIR /app

COPY servidor/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY proto/ ./proto/
COPY servidor/ .

RUN python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/products.proto

EXPOSE 8000 50051

CMD ["python", "main.py"]