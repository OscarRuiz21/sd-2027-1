FROM node:22-slim
WORKDIR /app
COPY server/package.json .
RUN npm install --omit=dev
COPY proto/ proto/
COPY server/*.js .
EXPOSE 8080 50051
CMD ["node", "main.js"]
