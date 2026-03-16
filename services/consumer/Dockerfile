FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -e -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -e -ntp package -DskipTests && \
    find target -maxdepth 1 -name "*.jar" ! -name "*-plain.jar" -exec mv {} target/app.jar \;

FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar

ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:+UseZGC -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Dsun.net.inetaddr.ttl=60 -Djava.io.tmpdir=/tmp" \
    SPRING_PROFILES_ACTIVE=prod

USER nonroot:nonroot
EXPOSE 8050 8051

ENTRYPOINT ["java","-jar","/app/app.jar"]
