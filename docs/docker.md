# Running on Docker Desktop

How to build and run the whole service — app plus database — as containers. Every command
and output below was executed and verified on Docker Desktop 29.1.3 / Windows 10.

Companion documents: [architecture.md](architecture.md),
[database.md](database.md), [local-postgres.md](local-postgres.md) (database-only dev setup).

---

## TL;DR

```bash
cp .env.example .env
sed -i "s|^RM_CREDENTIAL_KEY=.*|RM_CREDENTIAL_KEY=$(openssl rand -base64 32)|" .env
sed -i "s|^RM_JWT_SECRET=.*|RM_JWT_SECRET=$(openssl rand -base64 32)|" .env

docker compose -f compose.app.yaml up --build -d
```

Then open **http://localhost:8080** and sign in with `dev@example.com` / `valid-token`
(the `stub-jira` profile, on by default in `.env.example` — no real Jira tenant needed).

---

## Two compose files, and why

| File | Contains | Used for |
|------|----------|----------|
| `compose.yaml` | Postgres only | Backing service for `mvn spring-boot:run` / `mvn test` |
| `compose.app.yaml` | Postgres **+ the app** | Running the whole service in containers |

**The app service is deliberately not in `compose.yaml`.** Spring Boot's docker-compose
integration reads `compose.yaml` on `mvn spring-boot:run`; an `app` service there would make
the application try to start a container of itself on every local run. Keeping them separate
means neither mode interferes with the other.

The two also differ in data durability. `compose.yaml` declares no volume — its data is
anonymous and disposable, which is what you want for a dev database you reset freely.
`compose.app.yaml` uses a named `pgdata` volume so data survives `down`/`up`.

> `compose.app.yaml` publishes Postgres on `127.0.0.1:5432` only; `compose.yaml` publishes
> `5432`. Both share the compose project name `releasemanagement` and the 5432 host port, so
> run one at a time — `docker compose down` before bringing up the other, or you get a port
> clash. Both use `pgvector/pgvector:0.8.7-pg18-trixie`.

---

## Prerequisites

- Docker Desktop running (the build needs the daemon; so do the Testcontainers tests).
- Nothing else. **Java, Maven, Node and npm are not required on the host** — the builder
  image carries the JDK and Maven, and the `frontend-maven-plugin` downloads its own Node
  into the build. This is the fastest path to a running instance on a clean machine.

---

## Secrets

Two secrets have **no defaults**, by design. Compose refuses to start without them rather
than booting a credential-handling service with a predictable encryption key:

```
error while interpolating services.app.environment.RM_CREDENTIAL_KEY:
required variable RM_CREDENTIAL_KEY is missing a value
```

Create `.env` and fill both in. Substitute rather than append — appending with `>>` leaves
duplicate keys in the file, which works but is confusing to read later:

```bash
cp .env.example .env
sed -i "s|^RM_CREDENTIAL_KEY=.*|RM_CREDENTIAL_KEY=$(openssl rand -base64 32)|" .env
sed -i "s|^RM_JWT_SECRET=.*|RM_JWT_SECRET=$(openssl rand -base64 32)|" .env
```

Compose reads `.env` from the project directory automatically, so no `--env-file` flag is
needed for any command. `.env` is gitignored — never commit it.

Verify the two lengths, since the app validates them at startup and refuses to boot on a
mismatch:

```bash
grep ^RM_CREDENTIAL_KEY= .env | cut -d= -f2- | base64 -d | wc -c   # must be exactly 32
grep ^RM_JWT_SECRET=     .env | cut -d= -f2- | base64 -d | wc -c   # must be >= 32
```

### Applying changed secrets to a running stack

Compose fixes environment values at **container-creation** time, so editing `.env` has no
effect on an already-running container. `--force-recreate` is what applies it:

```bash
docker compose -f compose.app.yaml up -d --force-recreate app
```

Without it the container keeps the old baked-in values, and you will conclude the new secret
"didn't work".

Changing `RM_JWT_SECRET` invalidates active sessions — users sign in again, nothing else.
Changing `RM_CREDENTIAL_KEY` makes every stored Jira token undecryptable; users must sign in
again to re-supply one. That recovery works: `upsert` writes via JPQL specifically so it never
decrypts the row it is replacing. (It previously did a `findById` first, which threw on the
stale ciphertext and locked users out of the very action that would fix it.)

| Variable | Constraint | Consequence of changing it |
|----------|-----------|---------------------------|
| `RM_CREDENTIAL_KEY` | base64, **exactly 32 bytes** | Stored Jira tokens become undecryptable; every user must re-enter their API token |
| `RM_JWT_SECRET` | base64, **≥32 bytes** | All active sessions invalidated; users just sign in again |
| `RM_CREDENTIAL_KEY_VERSION` | 1–127, default `1` | Metadata only — leave alone unless actually rotating |
| `RM_JIRA_DEFAULT_BASE_URL` | Jira site URL | Only seeds an **empty** database; no effect once seeded — change the site in the UI |
| `RM_PORT` | default `8080` | Host port only; the container always listens on 8080 |
| `RM_DB_PASSWORD` | default `releasemgmt` | Must be set before first start, or the volume initialises with the old value |

Both length constraints are validated at startup, so a bad key fails fast with a clear
message instead of surfacing later as a decryption error.

---

## Build and run

```bash
docker compose -f compose.app.yaml up --build -d
```

Expected:

```
 Container releasemanagement-postgres-1  Healthy
 Container releasemanagement-app-1       Started
```

The app **waits for Postgres to pass a `pg_isready` healthcheck** before starting
(`depends_on: condition: service_healthy`), so the JVM never races a database that is still
initialising.

Watch it come up:

```bash
docker compose -f compose.app.yaml logs -f app
docker inspect --format '{{.State.Health.Status}}' releasemanagement-app-1
```

The app has its own healthcheck against `/actuator/health` with a 45s start period.

### Verified working state

```bash
$ curl -s http://localhost:8080/actuator/health
{"groups":["liveness","readiness"],"status":"UP"}

$ curl -s http://localhost:8080/api/settings/jira-site
{"baseUrl":"https://acme.atlassian.net","updatedAt":"...","updatedBy":"application-config-seed"}

$ curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/settings
200
```

The last one confirms SPA deep links survive a hard refresh —
`SpaForwardingController` forwards non-`/api`, non-`/actuator`, extensionless paths to
`index.html`.

Schema state inside the stack:

```bash
$ docker exec releasemanagement-postgres-1 \
    psql -U releasemgmt -d releasemgmt -c "select version, description, success from flyway_schema_history;"
 version | description | success
---------+-------------+---------
 1       | init schema | t
```

### One response that looks wrong but is correct

```bash
$ curl -X PUT http://localhost:8080/api/settings/jira-site \
    -H "Content-Type: application/json" -d '{"baseUrl":"https://evil.example.com"}'
{"status":403,"title":"Access denied","reason":"FORBIDDEN"}
```

**403, not 401** — because `curl` sent no CSRF token, so `CsrfFilter` rejected the request
*before* the authentication filter ran. This is CSRF protection working, and it is the
behaviour `SecurityFilterChainIT` asserts. A request carrying a valid CSRF token but no
session returns 401 `UNAUTHENTICATED`.

---

## Image design

`Dockerfile` is a two-stage build.

**Build stage** — `maven:3.9-eclipse-temurin-25`:
- `pom.xml` is copied and dependencies resolved *before* sources, so a source-only change
  does not re-resolve the whole tree.
- A BuildKit cache mount on `/root/.m2` persists the Maven repository across builds.
- `-DskipTests` is correct here: the suite needs Docker and a Postgres instance, neither of
  which exists inside an image build. Tests belong in CI and `mvn test`, not as a packaging
  side effect.
- The `frontend` Maven profile self-activates on `frontend/package.json`, so the SPA is built
  and embedded at `BOOT-INF/classes/static/` automatically.

**Runtime stage** — `eclipse-temurin:25-jre` (JRE, not JDK):
- Runs as a **non-root** `app` user (uid/gid 1001).
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` so the JVM sizes its heap from the
  *container* limit. Without it the JVM reads the host's memory and gets OOM-killed whenever
  the container limit is lower.
- `SPRING_DOCKER_COMPOSE_ENABLED=false` — Boot's compose integration must stay off inside the
  container; there is no `docker` CLI in the image, and Postgres comes from the compose file.
- `curl` is installed solely for the healthcheck.

### Layering

The jar is exploded with `java -Djarmode=tools -jar ... extract --layers` and copied in four
`COPY` steps, so dependencies (rarely change) and application classes (change every build)
land in **separate image layers** — pushes and pulls then move only what differs.

Worth knowing if you edit the Dockerfile: **Boot 4.1's `extract` produces a thin runnable jar
plus a sibling `lib/` directory**, with dependencies resolved through the manifest
`Class-Path`. It is *not* a fat-jar/`JarLauncher` layout, so the entrypoint is a plain
`java -jar` and `lib/` must stay next to the jar. The jar is renamed to `app.jar` so
`ENTRYPOINT` is not coupled to the project version.

Current image size: **~604 MB**.

### `.dockerignore` matters

`frontend/node_modules/` is excluded, and this is not merely an optimisation: npm installs
**platform-specific binaries** (esbuild, rollup), so shipping a Windows `node_modules` into a
Linux build breaks it. The builder runs `npm ci` inside the image instead. `target/`, `.git/`,
`.planning/` and `.env` are excluded too.

---

## Lifecycle

```bash
docker compose -f compose.app.yaml up --build -d   # build and start
docker compose -f compose.app.yaml ps              # status
docker compose -f compose.app.yaml logs -f app     # follow app logs
docker compose -f compose.app.yaml restart app     # restart just the app
docker compose -f compose.app.yaml stop            # stop, keep data
docker compose -f compose.app.yaml down            # remove containers, KEEP data
docker compose -f compose.app.yaml down -v         # remove containers AND data
```

## Rebuilding after a code change

There is **no push step for local use.** `docker build` writes the image straight into Docker
Desktop's local image store; `docker push` exists only for sending an image to a *remote*
registry. Docker Compose resolves `image:`/`build:` against that same local store.

The one command you normally want:

```bash
docker compose -f compose.app.yaml up --build -d app
```

`--build` is the important part. **`up -d` on its own will not rebuild** — it reuses the
existing image and you will sit there wondering why your change had no effect.

Other forms:

```bash
docker build -t release-management:0.1.0-SNAPSHOT .          # image only, no compose
docker compose -f compose.app.yaml build app                 # image only, via compose
docker compose -f compose.app.yaml up -d --build --force-recreate app   # also replace a container that would otherwise be reused
docker build --no-cache -t release-management:0.1.0-SNAPSHOT .          # ignore the layer cache entirely
```

> **`docker compose build` needs your env vars, even though building doesn't use them.**
> Compose interpolates the whole file before doing anything, so the required-variable guards
> on `RM_CREDENTIAL_KEY`/`RM_JWT_SECRET` will abort `build`, `config`, `ps` and `logs` too:
> ```
> error while interpolating services.app.environment.RM_CREDENTIAL_KEY:
> required variable RM_CREDENTIAL_KEY is missing a value
> ```
> Fix by having a `.env` in the project directory (compose reads it automatically), or pass
> `--env-file <path>`. Plain `docker build` never reads the compose file and so never hits this.

Verify what you have:

```bash
docker images release-management --format '{{.Repository}}:{{.Tag}}  {{.ID}}  {{.CreatedSince}}'
docker compose -f compose.app.yaml ps
```

Note the top-level image ID can change between builds even when every layer was cached —
BuildKit attaches provenance attestations that differ per build. Judge "did my change land"
by container behaviour or `CreatedSince`, not by the ID alone.

## Pushing to a remote registry

Only needed to run the image somewhere other than this machine.

```bash
# Docker Hub
docker tag release-management:0.1.0-SNAPSHOT <user>/release-management:0.1.0-SNAPSHOT
docker login
docker push <user>/release-management:0.1.0-SNAPSHOT

# Azure Container Registry
az acr login --name <registry>
docker tag release-management:0.1.0-SNAPSHOT <registry>.azurecr.io/release-management:0.1.0-SNAPSHOT
docker push <registry>.azurecr.io/release-management:0.1.0-SNAPSHOT
```

The Dockerfile's layer split (dependencies separate from application classes) pays off here:
a code-only change re-pushes just the small application layer, not the ~600 MB base and
dependency layers.

Before pushing anywhere real, read [Not production-ready as-is](#not-production-ready-as-is)
— in particular that this stack runs with `SPRING_PROFILES_ACTIVE=local`, which disables the
`Secure` cookie flag.

---

## rag-service

A separate retrieval service (collections, documents, semantic search) on port **8081**. It
starts with the stack, depends only on Postgres, and does not affect the `app` service.
Swagger UI: `http://localhost:8081/swagger-ui/index.html`; contract at `/v3/api-docs`, committed
at `rag-service/openapi.json`. **`RAG_ADMIN_KEY` is required** (at least 32 characters) by
`docker compose -f compose.app.yaml up`; see `.env.example`.

### First-time setup on an existing volume

Postgres init scripts run only on an empty data directory, so an existing `pgdata` volume
needs the one-off (idempotent, safe to re-run). The postgres container already has
`RAG_DB_PASSWORD` from `.env`, so do not pass it with `-e`; your shell does not have it:

```bash
docker compose -f compose.app.yaml up -d postgres
docker compose -f compose.app.yaml exec postgres bash /docker-entrypoint-initdb.d/10-ragdb.sh
docker compose -f compose.app.yaml up --build -d rag-service
```

A fresh volume runs `10-ragdb.sh` automatically.

### Registering a client

```bash
curl -s -X POST localhost:8081/v1/admin/clients -H "Authorization: Bearer $RAG_ADMIN_KEY" -H 'Content-Type: application/json' -d '{"name":"my-app"}'
```

The client key is shown once. Rotate with `POST /v1/admin/clients/{id}/rotate`, revoke with
`POST /v1/admin/clients/{id}/revoke`.

### Isolation

One-way: the `rag` role cannot connect to `releasemgmt`, but `releasemgmt` is the Postgres
superuser and can read `ragdb`. Moving `ragdb` to its own server is the real separation
(deferred).

### Ollama

Embeddings come from the host's Ollama at `RAG_OLLAMA_BASE_URL` (default
`http://host.docker.internal:11434`); there is no Ollama container. Run
`ollama pull nomic-embed-text` first. On Docker Desktop `host.docker.internal` reaches an Ollama
bound to loopback. On native Linux, Ollama must listen on the Docker bridge (`OLLAMA_HOST=0.0.0.0`
or the bridge IP) and be firewalled, because Ollama has no authentication. With Ollama down,
pushes are accepted and stay PENDING (check `GET /v1/status`); indexing resumes on its own.

The container healthcheck uses `/actuator/health/readiness`, which excludes Ollama.

---

## Troubleshooting

**`required variable RM_CREDENTIAL_KEY is missing a value`**
No `.env`, or you ran `docker compose` without it. Either create `.env` (compose reads it
automatically from the project directory) or pass `--env-file <path>` explicitly. This guard
is intentional.

**Postgres exits 1 with a message about `pg_ctlcluster` and major-version directories**
The volume is mounted at the wrong path. `postgres:18+` stores data in a major-version
subdirectory, so the mount must be at **`/var/lib/postgresql`**, not
`/var/lib/postgresql/data`. If you hit this after editing the compose file, discard the
half-initialised volume: `docker compose -f compose.app.yaml down -v`.
See [docker-library/postgres#1259](https://github.com/docker-library/postgres/pull/1259).

**`Could not find or load main class org.springframework.boot.loader.launch.JarLauncher`**
The entrypoint assumes a fat-jar layout the extraction does not produce. See
[Layering](#layering) — use `java -jar /app/app.jar`.

**Port 5432 or 8080 already in use**
Another stack is up. `docker compose down` (the Postgres-only one) first, or set `RM_PORT`
for the app. Both compose files share the project name `releasemanagement`.

**App unhealthy, logs show a database connection failure**
Check `docker compose -f compose.app.yaml ps` — if `postgres` is not `healthy`, read its
logs. Inside the stack the app reaches the database at hostname `postgres`, not `localhost`.

**Changed `RM_JIRA_DEFAULT_BASE_URL` but the app still shows the old site**
Expected, and not an env-loading problem. The seeder writes **only when `jira_site_config` is
empty**, so once a row exists the variable is ignored no matter how many times you recreate
the container. Confirm the distinction before chasing it:

```bash
docker exec releasemanagement-app-1 printenv RM_JIRA_DEFAULT_BASE_URL   # what the app was given
docker exec releasemanagement-postgres-1 \
  psql -U releasemgmt -d releasemgmt -tAc "select base_url from jira_site_config;"   # what it serves
```

If those differ, the env var arrived fine and the database simply already has a value. Three
ways to change it:

1. **Settings screen in the UI** — the intended path, and it records who changed it.
2. **API**: `PUT /api/settings/jira-site` with `{"baseUrl":"https://<site>.atlassian.net"}`
   (needs a session and a CSRF token).
3. **Reset**: `docker compose -f compose.app.yaml down -v && docker compose -f compose.app.yaml up -d`
   — discards the database, so the seeder runs again.

The URL must be exactly `https://<site>.atlassian.net` with **no trailing slash**. The REST
endpoint returns 400 `VALIDATION_FAILED` for anything else, and the seeder now fails startup
rather than persisting a value that would build paths like `.../atlassian.net//rest/api/3/...`.

**Build is slow the first time**
It resolves the full Maven tree and downloads Node. Subsequent builds reuse the BuildKit
`/root/.m2` cache mount and the dependency layer.

---

## Not production-ready as-is

This stack is for local and demo use. Before deploying anywhere real:

- **`SPRING_PROFILES_ACTIVE=local` sets `cookie-secure=false`**, because this stack serves
  plain HTTP on localhost where `Secure` cookies are unreliable. `AuthCookieWriter` refuses
  to start with `cookie-secure=false` outside the `local` profile — a guard that exists
  precisely so this cannot ship by accident. Production must terminate TLS and drop the
  `local` profile.
- **Secrets come from `.env`.** Use a real secret manager instead.
- **The Postgres password defaults to `releasemgmt`** and the database is exposed on the host.
- **No resource limits** are set. Add them; `MaxRAMPercentage` only pays off once a container
  memory limit exists.
- **No backup** for the `pgdata` volume.
