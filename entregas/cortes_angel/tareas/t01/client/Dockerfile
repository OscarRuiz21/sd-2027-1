FROM python:3.10-slim
WORKDIR /app
RUN pip install grpcio grpcio-tools requests
COPY . .
CMD ["python", "cliente.py"]