FROM python:3.10-slim

# Directorio de trabajo
WORKDIR /app

# Instalacion de dependencias
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Código del cliente
COPY . .

# Pruebas con el cliente
CMD ["python", "client.py"]
