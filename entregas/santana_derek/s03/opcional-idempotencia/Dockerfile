FROM python:3.11-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 HOST=0.0.0.0 DB_PATH=/data/cobros.sqlite3
WORKDIR /app
RUN mkdir /data && chown 10001:10001 /data
COPY app.py tests.py probar_http.py ./
USER 10001:10001
EXPOSE 8080
CMD ["python", "app.py"]
