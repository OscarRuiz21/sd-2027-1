FROM python:3.14-slim

WORKDIR /app

COPY client/requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY client ./client
COPY server ./server

CMD ["python", "client/cliente.py"]