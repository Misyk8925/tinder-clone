FROM golang:1.24-alpine AS build

WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/swipes-go ./cmd/swipes-go

FROM alpine:3.21

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /out/swipes-go /usr/local/bin/swipes-go

EXPOSE 8040
ENTRYPOINT ["/usr/local/bin/swipes-go"]
