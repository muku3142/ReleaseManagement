# syntax=docker/dockerfile:1

# ---- build ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Dependencies first so a source-only change does not re-resolve the whole tree.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline

COPY src ./src

# -DskipTests: the suite needs Docker (Testcontainers), which does not exist inside an image build.
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

# Boot 4.1's `extract` produces a thin runnable jar plus a sibling lib/ directory, so the
# runtime entrypoint is a plain `java -jar` and lib/ must stay next to the jar.
RUN java -Djarmode=tools -jar target/rag-service-*.jar extract --layers --destination extracted

# ---- runtime ----------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# curl is needed by the container healthcheck below.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Never run the service as root.
RUN groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --no-create-home app

COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

# Stable jar name so ENTRYPOINT is not coupled to the project version.
RUN set -eu; \
    jar="$(find /app -maxdepth 1 -name 'rag-service-*.jar' -print -quit)"; \
    test -n "$jar"; \
    mv "$jar" /app/app.jar

USER app
EXPOSE 8081

# Container memory is the authority, not the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Readiness excludes Ollama, so an Ollama outage never marks the container unhealthy.
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
  CMD curl -fsS http://localhost:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
