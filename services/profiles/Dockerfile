FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY tinder-contracts/pom.xml tinder-contracts/pom.xml
COPY tinder-contracts/src tinder-contracts/src
COPY profiles/pom.xml profiles/pom.xml
# Install the local contracts jar in this same layer as go-offline. It only
# exists in the cache mount, so a cached install layer with an empty mount
# would otherwise make Maven look for com.tinder:tinder-contracts on Central.
RUN --mount=type=cache,id=m2-profiles,sharing=locked,target=/root/.m2 \
    mvn -B -e -ntp -Dmaven.repo.local=/root/.m2/repository -f tinder-contracts/pom.xml install -DskipTests && \
    find /root/.m2/repository/com/tinder \( -name "*.lastUpdated" -o -name "resolver-status.properties" \) -delete && \
    test -f /root/.m2/repository/com/tinder/tinder-contracts/1.0.0/tinder-contracts-1.0.0.jar && \
    mvn -B -e -ntp -Dmaven.repo.local=/root/.m2/repository -f profiles/pom.xml dependency:go-offline

COPY profiles/src profiles/src
RUN --mount=type=cache,id=m2-profiles,sharing=locked,target=/root/.m2 \
    mvn -B -e -ntp -Dmaven.repo.local=/root/.m2/repository -f tinder-contracts/pom.xml install -DskipTests && \
    find /root/.m2/repository/com/tinder \( -name "*.lastUpdated" -o -name "resolver-status.properties" \) -delete && \
    mvn -B -e -ntp -Dmaven.repo.local=/root/.m2/repository -f profiles/pom.xml package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget

WORKDIR /app
COPY --from=build /workspace/profiles/target/*-SNAPSHOT.jar /app/app.jar

ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:+UseZGC -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Dsun.net.inetaddr.ttl=60 -Djava.io.tmpdir=/tmp" \
    SPRING_PROFILES_ACTIVE=prod


USER 1000:1000
EXPOSE 8010


ENTRYPOINT ["java","-jar","/app/app.jar"]
