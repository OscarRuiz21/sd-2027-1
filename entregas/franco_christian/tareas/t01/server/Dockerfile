FROM python:3.12-slim

WORKDIR /app

COPY server/requirements.txt ./server/requirements.txt
RUN pip install --no-cache-dir -r ./server/requirements.txt

COPY server ./server
COPY proto ./proto

EXPOSE 5000
EXPOSE 50051

CMD ["sh", "-c", "PYTHONPATH=/app python server/app.py & exec env PYTHONPATH=/app python server/grpc_server.py"]
