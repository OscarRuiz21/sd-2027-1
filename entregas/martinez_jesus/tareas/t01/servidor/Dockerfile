FROM python:3.10-slim

# Establecemos el directorio de trabajo
WORKDIR /app

# Se copia el archivo requerimientos
COPY requirements.txt .

# Se instalan las librerías necesarias
RUN pip install --no-cache-dir -r requirements.txt

# Se copia el resto de los archivos del servidor
COPY . .

# Puertos para REST (8080) y gRPC (50051)
EXPOSE 8080
EXPOSE 50051

# Comando para ejecutar el servidor
CMD ["python", "main.py"]
