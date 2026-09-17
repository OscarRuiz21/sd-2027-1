FROM python:3.11-slim

WORKDIR /app

COPY client/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY proto/ /app/proto/
RUN python -m grpc_tools.protoc -I/app/proto --python_out=/app --grpc_python_out=/app /app/proto/service.proto

COPY client/client.py .

CMD ["python", "client.py"]
