FROM python:3.12-slim

WORKDIR /app

COPY requirementos.txt .

RUN pip install --no-cache-dir -r requirementos.txt

COPY . .

CMD ["python", "client.py"]