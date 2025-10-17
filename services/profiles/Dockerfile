# syntax=docker/dockerfile:1.7

#####################
# 1) Build stage
#####################
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Кэшируем зависимости
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -e -ntp dependency:go-offline

# Копируем исходники и собираем
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -e -ntp package -DskipTests

#####################
# 2) Runtime stage
#####################
# Вариант 1: Debian slim
# FROM eclipse-temurin:21-jre-jammy
# Вариант 2: Distroless (минимальный и безопасный)
FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app
# Копируем готовый fat-jar
COPY --from=build /workspace/target/*-SNAPSHOT.jar /app/app.jar

# Настаиваем var/tmp для Spring, включаем TCP reuse и полезные дефолты
ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:+UseZGC -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Dsun.net.inetaddr.ttl=60 -Djava.io.tmpdir=/tmp" \
    SPRING_PROFILES_ACTIVE=prod

# Distroless уже под nonroot
USER nonroot:nonroot
EXPOSE 8010
# HEALTHCHECK работает не в distroless, поэтому используйте оркестратор (K8s liveness/readiness)
# или возьмите базу не-distroless и добавьте curl

ENTRYPOINT ["java","-jar","/app/app.jar"]