# PIE Trader frontend — React 19 + Vite, served by its own Express BFF
# (server.ts). Not Next.js: see docker-compose.yml's frontend service,
# which previously pointed at a non-existent ./frontend directory with
# Next.js-only env vars.

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Builds the static SPA (dist/) and bundles server.ts -> server.js,
# with node_modules kept external (--packages=external) rather than
# inlined — the runtime stage below installs production deps instead.
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY --from=build /app/dist ./dist
COPY --from=build /app/server.js ./server.js

EXPOSE 3000
CMD ["node", "server.js"]
