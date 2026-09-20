FROM node:20-alpine
WORKDIR /app

# Instalar tcpdump para el punto extra
RUN apk add --no-cache tcpdump

COPY server/package.json ./server/
RUN cd server && npm install
COPY proto/ ./proto/
COPY server/ ./server/
EXPOSE 8080 9090
CMD ["node", "server/server.js"]