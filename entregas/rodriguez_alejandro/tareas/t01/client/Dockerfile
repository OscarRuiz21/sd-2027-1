FROM node:20-alpine

WORKDIR /app
COPY client/package.json ./client/
RUN cd client && npm install
COPY proto/ ./proto/
COPY client/ ./client/
CMD ["node", "client/client.js"]