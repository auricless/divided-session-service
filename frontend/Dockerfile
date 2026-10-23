FROM node:24.21.0-alpine AS build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts
COPY frontend/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.30.6-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/ /usr/share/nginx/html/
USER 101:101
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --retries=12 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
