---
phase: 09-rag-service-foundation-index
plan: 07
subsystem: rag-service
tags: [auth, api-key, collections, admin]
requires: ["09-02", "09-03", "09-05"]
provides:
  - ApiKeyFilter.CLIENT_ID_ATTRIBUTE
  - CollectionService.require
  - CollectionController.NAME_PATTERN
key-files:
  created:
    - rag-service/src/main/java/com/evision/ragservice/application/ClientService.java
    - rag-service/src/main/java/com/evision/ragservice/application/CollectionService.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/ApiKeyFilter.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/AdminController.java
    - rag-service/src/main/java/com/evision/ragservice/adapters/web/CollectionController.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/web/AdminAndAuthIT.java
    - rag-service/src/test/java/com/evision/ragservice/adapters/web/CollectionApiIT.java
    - rag-service/src/test/java/com/evision/ragservice/application/CollectionServiceTest.java
decisions:
  - Controllers carry no class-level @Validated; Spring 7 built-in method validation yields HandlerMethodValidationException which the advice maps to 400
metrics:
  tests: "102/102 green (clean test)"
  completed: 2026-10-13
---

# Phase 9 Plan 07: API-key auth, admin clients, collections Summary

Bearer-key filter over `/v1/**` (admin key via `MessageDigest.isEqual`, client keys by SHA-256 lookup), admin client register/rotate/revoke/list, and explicit model-fixed, client-isolated collections with status and re-index.

## Deviations from Plan

**1. [Rule 1 - Bug] Dropped class-level `@Validated` from both controllers.** It switched validation to AOP, so a bad path variable threw an unhandled `ConstraintViolationException` (500) instead of the advice's 400 VALIDATION_FAILED. Built-in Spring 7 method validation handles `@Pattern` on path variables. Commit: task 2.

The short-admin-key check is a unit-level assertion inside `AdminAndAuthIT` (as the plan preferred).

## Commits
- Task 1: feat(09-07) API-key filter and admin client registration
- Task 2: feat(09-07) collection service and controller

## Self-Check: PASSED
