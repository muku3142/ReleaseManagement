---
phase: 09-rag-service-foundation-index
plan: 10
subsystem: rag-service
tags: [openapi, docker, compose, flyway, testcontainers]
requires: ["09-01", "09-08", "09-09"]
provides:
  - Committed OpenAPI contract with drift test
  - Init-script privilege proof
  - rag-service image and compose service on 8081
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/config/OpenApiConfig.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/web/OpenApiContractIT.java
    - rag-service/src/test/java/com/evision/ragservice/InitScriptPrivilegeIT.java
    - rag-service/openapi.json
    - rag-service/Dockerfile
    - rag-service/.dockerignore
  modified:
    - compose.app.yaml
    - .env.example
    - docs/docker.md
requirements-completed: [RAG-10, RAG-01, RAG-03]
duration: ~25 min
completed: 2026-10-13
---

# Phase 9 Plan 10: OpenAPI contract, init-script proof, packaging Summary

Served and committed OpenAPI contract (bearerKey scheme) guarded by a drift test, the init script's privilege model proven against a real pgvector container, and rag-service packaged as a non-root image wired into compose on 8081.

## Tasks

1. OpenAPI config, contract IT, init-script IT: 69fd46b
2. Dockerfile, compose service, .env.example, docs: 4baa0cf

## Verification

- OpenApiContractIT 3/3 and InitScriptPrivilegeIT 4/4 pass without the update flag.
- Full rag-service `clean test`: 138/138 green.
- `docker build` succeeds; image user is `app`; healthcheck targets `/actuator/health/readiness`.
- `docker compose config` validates with RAG_ADMIN_KEY and fails mentioning it without.
- openapi.json has no CR bytes and no key example values (the `apiKey` entry is a schema property only).

## Flag for the user

`RAG_ADMIN_KEY` is now REQUIRED by `docker compose -f compose.app.yaml up` (RESEARCH Open Question 2 adopted). Add it to `.env` before the live rollout (plan 09-11), otherwise compose aborts for every service, including `app`.

## Deviations from Plan

None. Controllers already carried `@Parameter(hidden = true)`, so no controller edits were needed. `MountableFile.forHostPath` is given mode 0755 so the script is executable inside the container.

## Notes

- The running stack was not touched; a local `rag-service:0.1.0-SNAPSHOT` image was built to prove the Dockerfile.
- Git warns LF will become CRLF on touch for the new files (autocrlf); openapi.json comparison is tree-based so unaffected.

## Self-Check: PASSED
