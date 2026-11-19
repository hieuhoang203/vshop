FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
COPY apps/web/package.json apps/web/package.json
COPY packages/contracts packages/contracts
RUN npm ci
COPY apps/web apps/web
RUN npm run build
ENV NODE_ENV=production
EXPOSE 3100
USER node
CMD ["npm","run","start","--workspace","@vshop/web"]
