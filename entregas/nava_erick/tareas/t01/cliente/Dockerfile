FROM node:alpine

RUN apk update && apk add tcpdump

WORKDIR /app

COPY cliente/package*.json ./
RUN npm install

COPY cliente/ .
COPY api.proto .

CMD ["tail", "-f", "/dev/null"]
