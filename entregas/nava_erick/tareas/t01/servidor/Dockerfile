FROM node:alpine

RUN apk update && apk add tcpdump

WORKDIR /app

COPY servidor/package*.json ./
RUN npm install

COPY servidor/ .

COPY api.proto .

EXPOSE 8080 50051

CMD ["node", "servidor.js"]
