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
