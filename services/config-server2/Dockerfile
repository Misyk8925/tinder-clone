FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,id=m2-config-server2,sharing=locked,target=/root/.m2 \
    mvn -B -e -ntp -Dmaven.repo.local=/root/.m2/repository dependency:go-offline

COPY src ./src
RUN --mount=type=cache,id=m2-config-server2,sharing=locked,target=/root/.m2 \
    mvn -B -e -ntp -Dmaven.repo.local=/root/.m2/repository package -DskipTests && \
    find target -maxdepth 1 -name "*.jar" ! -name "*-plain.jar" -exec mv {} target/app.jar \;

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar

ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:+UseZGC -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Dsun.net.inetaddr.ttl=60 -Djava.io.tmpdir=/tmp" \
    SPRING_PROFILES_ACTIVE=prod

USER appuser
EXPOSE 8888

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
