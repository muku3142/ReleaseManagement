---
phase: 09-rag-service-foundation-index
plan: 02
subsystem: rag-service
tags: [spring-boot, pgvector, flyway, jdbc, archunit]
requires: []
provides:
  - standalone rag-service Maven module (Boot 4.1.0, JdbcClient, springdoc 3.1.1)
  - Flyway V1 RAG schema (vector(768), no ANN index)
  - domain records, exceptions and six ports for waves 2-4
  - GlobalExceptionHandler (ProblemDetail + reason)
affects: [09-03, 09-04, 09-05]
tech-stack:
  added: [spring-boot-starter-jdbc, springdoc-openapi 3.1.1, pgvector schema]
  patterns: [hexagonal ports, ProblemDetail reason codes, ArchUnit boundaries]
key-files:
  created:
    - rag-service/pom.xml
    - rag-service/src/main/resources/db/migration/V1__rag_schema.sql
    - rag-service/src/main/resources/application.yml
    - rag-service/src/test/resources/application.yml
    - rag-service/src/main/java/com/evision/ragservice/ports/EmbeddingPort.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/GlobalExceptionHandler.java
    - rag-service/src/test/java/com/evision/ragservice/ArchitectureBoundaryTest.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/persistence/SchemaPersistenceIT.java
key-decisions:
  - "Root pom untouched; rag-service is a standalone build"
  - "Model prefixes live under rag.ollama.* only; callers pass EmbeddingPurpose"
requirements-completed: [RAG-01, RAG-02, RAG-03]
duration: 20min
completed: 2026-10-13
---

# Phase 9 Plan 02: rag-service foundation Summary

Standalone rag-service module with pgvector V1 schema (768 dims, exact scan), framework-free domain/port contracts, and ProblemDetail error mapping for every domain exception.

## Tasks
1. Pom, application class, both yml files, V1 schema, SchemaPersistenceIT: 38ffa55
2. Domain records/exceptions, six ports, GlobalExceptionHandler, ArchitectureBoundaryTest: c91f907

## Verification
`mvn -f rag-service/pom.xml clean test -Dtest=ArchitectureBoundaryTest,SchemaPersistenceIT`: 8 tests, 0 failures. Root pom unchanged.

## Deviations from Plan
None - plan executed as written. `HandlerMethodValidationException` detail is a fixed generic string (no per-field list) to avoid echoing values.

## Known Stubs
None.

## Self-Check: PASSED
