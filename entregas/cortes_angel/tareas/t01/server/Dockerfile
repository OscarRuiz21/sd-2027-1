FROM python:3.10-slim
WORKDIR /app
RUN pip install grpcio grpcio-tools fastapi uvicorn
COPY . .
EXPOSE 3000 50051
CMD ["python", "main.py"]