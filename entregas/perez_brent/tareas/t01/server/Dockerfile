FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY proto ./proto

EXPOSE 8000
EXPOSE 50051

CMD ["sh", "-c", "python -m app.grpc_server & uvicorn app.rest:app --host 0.0.0.0 --port 8000"]