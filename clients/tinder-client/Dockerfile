FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci --no-audit --no-fund

COPY . .
ARG NG_CONFIGURATION=production
RUN npm run build -- --configuration ${NG_CONFIGURATION}

FROM nginx:1.31.4-alpine-slim
# Alpine publishes security patches to the repo before the base image is
# rebuilt, so upgrade installed packages at build time.
RUN apk --no-cache upgrade
COPY --from=build /app/dist/tinder-client/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /run /etc/nginx/conf.d

USER nginx

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
