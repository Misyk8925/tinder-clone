FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm install --no-audit --no-fund

COPY . .
RUN npm run build

FROM nginx:1.31.4-alpine-slim
# Alpine publishes security patches to the repo before the base image is rebuilt.
RUN apk --no-cache upgrade

COPY --from=build /app/dist /usr/share/nginx/html
# GATEWAY_URL is substituted at container start, so one image serves every environment.
COPY nginx.conf /etc/nginx/templates/default.conf.template
ENV GATEWAY_URL=http://gateway:8222

RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /run /etc/nginx/conf.d /etc/nginx/templates

USER nginx

EXPOSE 80
