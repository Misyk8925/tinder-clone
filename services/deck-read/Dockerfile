FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY tinder-contracts/pom.xml tinder-contracts/pom.xml
COPY tinder-contracts/src tinder-contracts/src
RUN --mount=type=cache,id=m2-deck-read,sharing=locked,target=/root/.m2 \
    mvn -B -e -ntp -Dmaven.repo.local=/root/.m2/repository -f tinder-contracts/pom.xml install -DskipTests && \
    find /root/.m2/repository/com/tinder \( -name "*.lastUpdated" -o -name "resolver-status.properties" \) -delete && test -f /root/.m2/repository/com/tinder/tinder-contracts/1.0.0/tinder-contracts-1.0.0.jar

COPY deck-read/pom.xml deck-read/pom.xml
RUN --mount=type=cache,id=m2-deck-read,sharing=locked,target=/root/.m2 \
    mvn -B -e -ntp -U -Dmaven.repo.local=/root/.m2/repository -f deck-read/pom.xml dependency:go-offline

COPY deck-read/src deck-read/src
RUN --mount=type=cache,id=m2-deck-read,sharing=locked,target=/root/.m2 \
    mvn -B -e -ntp -U -Dmaven.repo.local=/root/.m2/repository -f deck-read/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
# Quarkus fast-jar layout: lib/, app/, quarkus/, quarkus-run.jar
COPY --from=build /workspace/deck-read/target/quarkus-app/ /app/

ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:+UseZGC -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Dsun.net.inetaddr.ttl=60 -Djava.io.tmpdir=/tmp"

USER appuser
EXPOSE 8040

ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
