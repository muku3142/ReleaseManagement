# Local PostgreSQL on Docker Desktop

How the local `releasemgmt` database is started, connected to, and inspected on this
machine. Everything here was captured from the actual Phase 01 execution run
(2026-07-28) and re-verified against the running container.

Environment it was verified on: Windows 10 Pro, Docker Desktop 29.1.3
(client/server API 1.52), PostgreSQL 18, Spring Boot 4.1.0, JDK 25.

---

## TL;DR

```bash
# from the project root: D:\IdeaProjects\ReleaseManagement
docker compose up -d
```

That is the whole command. In normal use you do not even need it — Spring Boot's
`spring-boot-docker-compose` module runs it for you (see
[Who starts the container](#who-starts-the-container)).

---

## The compose file

`compose.yaml` at the project root is the single source of truth. It is intentionally
minimal — no named volume, no healthcheck, no custom network:

```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: releasemgmt
      POSTGRES_USER: releasemgmt
      POSTGRES_PASSWORD: releasemgmt
    ports:
      - "5432:5432"
```

Because there is no volume declared, **the data directory is anonymous** — `docker
compose down -v` (or a `docker volume prune`) discards the database, and Flyway
re-creates the schema from scratch on the next start. That is deliberate for local
development: the schema is disposable and fully reproducible from
`src/main/resources/db/migration/`.

Resulting container identity (derived from the directory name `ReleaseManagement`):

| Property | Value |
|----------|-------|
| Container name | `releasemanagement-postgres-1` |
| Compose project | `releasemanagement` |
| Network | `releasemanagement_default` |
| Published port | `0.0.0.0:5432 -> 5432/tcp` |
| Database / user / password | `releasemgmt` / `releasemgmt` / `releasemgmt` |

---

## Who starts the container

There are two independent paths to a running Postgres, and they matter because they
talk to the Docker daemon in different ways.

### 1. Spring Boot Docker Compose support (the default for `mvn spring-boot:run`)

`application.yml` enables Boot's compose integration:

```yaml
spring:
  docker:
    compose:
      enabled: true
      skip:
        in-tests: true
```

So the normal developer loop needs **no manual Docker command at all**:

```bash
export JAVA_HOME="$HOME/.jdks/jdk-25"
export RM_CREDENTIAL_KEY=$(openssl rand -base64 32)
export RM_JWT_SECRET=$(openssl rand -base64 32)
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Boot shells out to `docker compose up`, waits for the service, injects the JDBC
connection details, and shuts the container down when the JVM exits.
`skip.in-tests: true` keeps it out of the way of the test suite.

### 2. Manual `docker compose` (what the Phase 01 verification actually used)

```bash
cd /d/IdeaProjects/ReleaseManagement
docker compose up -d
docker ps --filter "name=releasemanagement"
```

Expected output:

```
CONTAINER ID   IMAGE         STATUS         PORTS                      NAMES
462a77642d16   postgres:18   Up 7 minutes   0.0.0.0:5432->5432/tcp     releasemanagement-postgres-1
```

Use this path when you want the database to outlive the application process — for
repeated `spring-boot:run` cycles, for `psql` poking, or when Boot's compose
integration misbehaves (below).

---

## The Docker API negotiation workaround

During Phase 01's manual end-to-end verification, `mvn spring-boot:run` failed at
startup even though the container itself started fine:

```
Container releasemanagement-postgres-1 Started
Container releasemanagement-postgres-1 Waiting
request returned 500 Internal Server Error for API route and version
http://%2F%2F.%2Fpipe%2FdockerDesktopLinuxEngine/v1.51/containers/462a7764.../json,
check if the server supports the requested API version
    at org.springframework.boot.docker.compose.core.ProcessRunner.run(...)
```

**Diagnosis.** `spring-boot-docker-compose` runs `docker inspect` as a subprocess
during its readiness wait, and that subprocess negotiated API `v1.51` against a
daemon reporting `1.52`. Note two things: the container had already **started
successfully**, and Testcontainers — which uses a completely separate Java Docker
client rather than the `docker` CLI — connects to the same daemon without any
problem during `mvn test`. So this is local CLI/daemon version skew, not a defect
in the application, the compose file, or the pom.

**Workaround used (verification only, no code change).** Start Postgres yourself and
tell Boot not to manage it:

```bash
cd /d/IdeaProjects/ReleaseManagement
docker compose up -d

export JAVA_HOME="$HOME/.jdks/jdk-25"
export RM_CREDENTIAL_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=
export RM_JWT_SECRET=and0LXRlc3Qtc2VjcmV0LTAxMjM0NTY3ODlhYmNkZWY=
mvn spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments=--spring.docker.compose.enabled=false
```

> The two secrets above are throwaway values used only for that local smoke test.
> Generate your own with `openssl rand -base64 32` — never reuse these.

`spring.docker.compose.enabled` deliberately stays `true` in `application.yml`. The
override belongs on the command line, not in committed config, because the root cause
is one machine's Docker CLI/daemon skew rather than anything about this project.

If you hit it too, the durable fix is to bring the Docker Desktop CLI and engine back
into alignment (update Docker Desktop); the flag above is the stopgap.

---

## Connecting and inspecting

### Connection details

| Setting | Value | Env override |
|---------|-------|--------------|
| JDBC URL | `jdbc:postgresql://localhost:5432/releasemgmt` | `RM_DB_URL` |
| User | `releasemgmt` | `RM_DB_USER` |
| Password | `releasemgmt` | `RM_DB_PASSWORD` |

These are the defaults baked into `application.yml`, so an unconfigured checkout
points at the compose container automatically.

### psql inside the container

No local `psql` install needed — exec into the container:

```bash
# interactive shell
docker exec -it releasemanagement-postgres-1 psql -U releasemgmt -d releasemgmt

# one-shot query
docker exec releasemanagement-postgres-1 \
  psql -U releasemgmt -d releasemgmt -c "\dt"
```

Verified output on the current container:

```
                    List of tables
 Schema |         Name          | Type  |    Owner
--------+-----------------------+-------+-------------
 public | flyway_schema_history | table | releasemgmt
 public | jira_credential       | table | releasemgmt
 public | jira_site_config      | table | releasemgmt
(3 rows)
```

### Confirm Flyway applied the baseline

```bash
docker exec releasemanagement-postgres-1 psql -U releasemgmt -d releasemgmt \
  -c "select version, description, success from flyway_schema_history;"
```

```
 version | description | success
---------+-------------+---------
 1       | init schema | t
(1 row)
```

### Confirm the site-config row was seeded

`JiraSiteConfigSeeder` writes a single row on first startup when the table is empty,
so a fresh environment is usable before anyone signs in:

```bash
docker exec releasemanagement-postgres-1 psql -U releasemgmt -d releasemgmt \
  -c "select * from jira_site_config;"
```

```
 id |          base_url          |          updated_at           | updated_by
----+----------------------------+-------------------------------+------------
  1 | https://acme.atlassian.net | 2026-07-28 13:38:57.100118+00 | anonymous
(1 row)
```

`updated_by` is `anonymous` because the seeder runs before any authentication exists;
Phase 01 plan 05 wires Spring Security, after which `PUT /api/settings/jira-site`
records the acting user.

---

## Tests do not use this container

`mvn test` does **not** touch the compose container. The persistence integration
tests (`*PersistenceIT`) start their own throwaway PostgreSQL 18 via Testcontainers
2.0.5, and `spring.docker.compose.skip.in-tests: true` keeps Boot's compose
integration out of the test context.

```bash
export JAVA_HOME="$HOME/.jdks/jdk-25"
mvn test
```

Consequence worth knowing: **Docker Desktop must be running for `mvn test` to pass**,
even though no compose container is involved. Testcontainers needs a reachable daemon.

---

## Lifecycle commands

```bash
docker compose up -d           # start (detached)
docker compose ps              # status of this project's services
docker compose logs -f postgres  # follow Postgres logs
docker compose stop            # stop, keep the container
docker compose start           # restart a stopped container
docker compose down            # stop and remove container + network
docker compose down -v         # ...and discard the data (schema rebuilt by Flyway)
```

---

## Troubleshooting

**`npipe` connection failure / Testcontainers cannot reach Docker.**
Docker Desktop is installed but not started. Launch it and wait for the daemon to
accept connections before retrying:

```powershell
Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
```

Poll until ready with `docker info` rather than guessing at a sleep duration. This
was hit on the first `JiraSiteConfigPersistenceIT` attempt during Phase 01.

**`500 Internal Server Error ... check if the server supports the requested API version`.**
Docker CLI/daemon API skew. See
[The Docker API negotiation workaround](#the-docker-api-negotiation-workaround).

**Port 5432 already in use.**
Another Postgres (a native install, or another project's compose stack) holds the
port. Stop it, or change the host side of the mapping in `compose.yaml` to e.g.
`"5433:5432"` and set `RM_DB_URL=jdbc:postgresql://localhost:5433/releasemgmt`.

**Schema looks stale or Flyway reports a checksum mismatch.**
The database is disposable — reset it rather than repairing by hand:

```bash
docker compose down -v && docker compose up -d
```

**Check which API versions are actually in play.**

```bash
docker version --format '{{.Client.APIVersion}} / {{.Server.APIVersion}}'
```

Currently reports `1.52 / 1.52`.

---

## Sources

Every command and output above is either read from a committed project file
(`compose.yaml`, `application.yml`, `README.md`) or captured from the Phase 01
plan 01-01 execution transcript and re-run against the live container while writing
this document. See
`.planning/phases/01-foundation-authentication/01-01-SUMMARY.md`
(deviations 8 and 9) for the original record of the two Docker environment issues.
