FROM node:22-slim
WORKDIR /app
COPY client/package.json .
RUN npm install --omit=dev
COPY proto/ proto/
COPY client/client.js .
ENTRYPOINT ["node", "client.js"]
