FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci --no-audit --no-fund

COPY . .
ARG NG_CONFIGURATION=production
RUN npm run build -- --configuration ${NG_CONFIGURATION}

FROM nginx:1.27-alpine-slim
COPY --from=build /app/dist/tinder-client/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
