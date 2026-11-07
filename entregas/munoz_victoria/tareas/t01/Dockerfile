FROM python:3.11-slim

WORKDIR /app

# Copiar e instalar dependencias
COPY requerimientos.txt .
RUN pip install --no-cache-dir -r requerimientos.txt

# Copiar el resto del código del servicio
COPY . .

# Exponer los puertos de REST (8000) y gRPC (50051)
EXPOSE 8000 50051

# Ejecutar el servidor unificado
CMD ["python", "server.py"]