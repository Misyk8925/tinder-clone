FROM golang:1.24-alpine AS build

WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/location ./cmd/location

FROM alpine:3.21

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /out/location /usr/local/bin/location

EXPOSE 8065
ENTRYPOINT ["/usr/local/bin/location"]
