FROM python:3.12-slim

WORKDIR /app

COPY client/requirements.txt ./client/requirements.txt
RUN pip install --no-cache-dir -r ./client/requirements.txt

COPY client ./client
COPY proto ./proto

ENV PYTHONPATH=/app

CMD ["python", "client/client.py", "1"]
