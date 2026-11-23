# syntax=docker/dockerfile:1.7
#
# InvigilatorAssist web image: the React build served by unprivileged nginx, which also proxies /api to the API.
#   docker build --build-arg APP_VERSION=$(git rev-parse --short HEAD) -t invigilator-web frontend

# ---- build ----
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# Empty base URL: the app calls /api on its own origin and nginx forwards it.
ENV VITE_API_BASE_URL=""
RUN npm run build

# ---- runtime ----
FROM nginxinc/nginx-unprivileged:1.27-alpine
ARG APP_VERSION=local
LABEL org.opencontainers.image.title="invigilator-web" \
      org.opencontainers.image.source="https://github.com/kssampath/InvigilatorAssist" \
      org.opencontainers.image.version="${APP_VERSION}"
ENV API_UPSTREAM=invigilator-api:8080 \
    APP_VERSION=${APP_VERSION}
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html
# nginx-unprivileged runs as uid 101 and listens on 8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
