FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY . .

ENV PYTHONPATH=/app/proto

EXPOSE 5000
EXPOSE 50051

CMD ["python", "server.py"]