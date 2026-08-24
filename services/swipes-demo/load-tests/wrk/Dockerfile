FROM alpine:3.21 AS build

RUN apk add --no-cache build-base git linux-headers openssl-dev perl zlib-dev
RUN git clone --depth 1 https://github.com/wg/wrk.git /src/wrk
WORKDIR /src/wrk
RUN make

FROM alpine:3.21

RUN apk add --no-cache libgcc openssl zlib \
    && addgroup -S wrk \
    && adduser -S -G wrk wrk
COPY --from=build --chown=wrk:wrk /src/wrk/wrk /usr/local/bin/wrk

USER wrk

ENTRYPOINT ["wrk"]
