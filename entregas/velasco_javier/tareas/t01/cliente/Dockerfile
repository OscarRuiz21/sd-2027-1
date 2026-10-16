FROM node:22-slim
WORKDIR /app
COPY cliente/package.json .
RUN npm install --omit=dev
COPY proto/ proto/
COPY cliente/client.js .
ENTRYPOINT ["node", "client.js"]