# syntax=docker/dockerfile:1.7
#
# InvigilatorAssist API image.
#   docker build --build-arg APP_VERSION=$(git rev-parse --short HEAD) -t invigilator-api backend
#
# Optimisations:
#   * multi-stage: the JDK and Maven stay in the build stage; the runtime is a ~200 MB JRE on Alpine
#   * dependency layer: pom.xml is copied and resolved before the sources, so code changes reuse it
#   * BuildKit cache mount for ~/.m2, so even a changed pom.xml downloads only what is new
#   * Spring Boot layered jar: dependencies / loader / snapshot deps / application are separate image layers,
#     so a code change pushes only the small application layer to the registry
#   * non-root numeric user (Kubernetes runAsNonRoot can verify it)

# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -ntp dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -ntp package -DskipTests \
 && java -Djarmode=tools -jar target/invigilator-api.jar extract --layers --launcher --destination target/extracted \
 && mkdir -p target/extracted/dependencies target/extracted/spring-boot-loader \
             target/extracted/snapshot-dependencies target/extracted/application

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
ARG APP_VERSION=local
LABEL org.opencontainers.image.title="invigilator-api" \
      org.opencontainers.image.source="https://github.com/kssampath/InvigilatorAssist" \
      org.opencontainers.image.version="${APP_VERSION}"
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=build --chown=app:app /src/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /src/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /src/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /src/target/extracted/application/ ./
USER 10001
EXPOSE 8080
ENV APP_VERSION=${APP_VERSION} \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
