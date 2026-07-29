# syntax=docker/dockerfile:1

# ---- build ------------------------------------------------------------------
# The `frontend` Maven profile self-activates on frontend/package.json and the
# frontend-maven-plugin downloads its own Node into target/, so this stage needs
# no Node installed.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Dependencies first so a source-only change does not re-resolve the whole tree.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline

COPY src ./src
COPY frontend ./frontend

# -DskipTests is correct here: the suite needs Docker (Testcontainers) and a
# Postgres instance, neither of which exist inside an image build. Tests run in
# CI and locally via `mvn test`, not as a packaging side effect.
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

# Explode the jar so the runtime stage can layer it — dependencies (rarely
# changed) and application classes (changed every build) land in separate image
# layers, so pushes/pulls only move what differs.
#
# Boot 4.1's `extract` produces a THIN RUNNABLE jar plus a sibling lib/ directory
# (dependencies resolved via the manifest Class-Path) — not a JarLauncher/fat-jar
# layout. So the runtime entrypoint is a plain `java -jar`, and lib/ must stay
# next to the jar.
RUN java -Djarmode=tools -jar target/release-management-*.jar extract --layers --destination extracted

# ---- runtime ----------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# curl is needed by the container healthcheck below.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Never run the app as root.
RUN groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --no-create-home app

COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

# Give the jar a stable name so ENTRYPOINT is not coupled to the project version.
# Safe to rename: dependencies resolve through the manifest Class-Path (lib/),
# not through the jar's own filename.
RUN set -eu; \
    jar="$(find /app -maxdepth 1 -name 'release-management-*.jar' -print -quit)"; \
    test -n "$jar"; \
    mv "$jar" /app/app.jar

USER app
EXPOSE 8080

# Container memory is the authority, not the host's — without this the JVM sizes
# its heap from the host and gets OOM-killed when the container limit is lower.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Boot's docker-compose integration must stay off inside the container: it would
# try to shell out to a `docker` CLI that is not present, and Postgres is supplied
# by the compose file instead.
ENV SPRING_DOCKER_COMPOSE_ENABLED=false

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
