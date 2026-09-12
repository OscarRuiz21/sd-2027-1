FROM python:3.12-slim
WORKDIR /app
COPY catalog.py rest_server.py ./
USER 10001
EXPOSE 8080
CMD ["python", "-u", "rest_server.py"]
