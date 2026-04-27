FROM golang:1.24-alpine AS build

WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/go-swipe-bench .

FROM alpine:3.21

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /out/go-swipe-bench /usr/local/bin/go-swipe-bench
ENTRYPOINT ["/usr/local/bin/go-swipe-bench"]
