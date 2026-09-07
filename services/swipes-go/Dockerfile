FROM golang:1.25.13-alpine AS build

WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/swipes-go ./cmd/swipes-go

FROM alpine:3.23

# Alpine publishes security patches to the repo before the base image is
# rebuilt, so upgrade installed packages at build time.
RUN apk --no-cache upgrade \
    && apk add --no-cache ca-certificates wget \
    && addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /out/swipes-go /usr/local/bin/swipes-go

EXPOSE 8040
ENTRYPOINT ["/usr/local/bin/swipes-go"]
