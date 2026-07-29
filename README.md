# ReleaseManagement

## Prerequisites

- JDK 25 at `C:\Users\ucs\.jdks\jdk-25`
- Maven 3.9.11
- Docker (for local Postgres via `compose.yaml`)

## Environment variables

```bash
export RM_CREDENTIAL_KEY=$(openssl rand -base64 32)
export RM_CREDENTIAL_KEY_VERSION=1
export RM_JWT_SECRET=$(openssl rand -base64 32)
```

## Run

```bash
export JAVA_HOME="$HOME/.jdks/jdk-25"
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Test

```bash
export JAVA_HOME="$HOME/.jdks/jdk-25"
mvn test
```

## Frontend / local development

The `frontend` Maven profile self-activates once `frontend/package.json` exists — no extra Maven flag is needed once you've run `npm ci` at least once.

```bash
cd frontend
npm ci
npm run dev
```

The dev server runs on `http://localhost:5173` and proxies `/api` and `/actuator` to `http://localhost:8080`, so the SPA and backend are same-origin during local development (cookies and CSRF behave the same as the packaged jar).

```bash
cd frontend
npm run test
```

## Single-jar build

```bash
export JAVA_HOME="$HOME/.jdks/jdk-25"
mvn -Pfrontend package
java -jar target/release-management-0.1.0-SNAPSHOT.jar
```

This builds the SPA and embeds it under `BOOT-INF/classes/static`, so the packaged jar serves both the API and the UI from one process on `http://localhost:8080`.
