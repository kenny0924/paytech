# Platform Support Runtime Simplification Implementation Plan

> **For agentic workers:** Implement task-by-task with test-first red/green checks. Do not introduce an additional framework or abstraction layer.

**Goal:** Replace Flyway/Testcontainers and the Java runtime seed framework with manually maintained SQL and a smaller full-chain Platform Snapshot implementation.

**Architecture:** Operators apply one schema SQL file and one initialization DML file to repository-owned local MySQL. Runtime code only reads and validates current Gateway configuration through the existing Controller-to-Mapper chain; Spring Boot and one small health indicator own readiness.

**Tech Stack:** Java 25, Spring Boot 4.0.8, Spring MVC, MyBatis, MySQL 8.4 local Compose, JUnit 5, Maven.

**Spec:** `docs/superpowers/specs/2026-09-02-manual-infrastructure-and-platform-runtime-simplification.md`

## Global Constraints

- No Flyway, Liquibase, Testcontainers or embedded database dependency.
- No Java schema migration, data repair or seed execution at startup.
- Exactly one Platform schema DDL file and one Platform initialization DML file.
- Preserve the PLT-I20 DTO, URL, mTLS caller boundary and stable `503` failure contract.
- Tests invoke real production entry points and the local MySQL-backed Mapper chain.
- Never commit a password, private key or environment-specific credential.

---

### Task 1: Persist project policy

**Files:**
- Modify: `AGENTS.md`
- Create: `.cursor/rules/manual-infrastructure-and-sql.mdc`
- Modify: canonical technology, local-environment, Java-structure, database and test guidance
- Modify: canonical requirements wording and its semantic tests

- [x] Replace active Flyway/Testcontainers instructions with manual SQL/local middleware rules.
- [x] Keep historical records intact unless their current-status paragraph is misleading.
- [x] Run requirements build, tests and lint.

### Task 2: Make the desired dependency and SQL boundary fail first

**Files:**
- Modify: `platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportDependencyBoundaryAutoTest.java`
- Modify: `platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportConfigurationAutoTest.java`
- Test: the two classes above

- [x] Assert that Flyway/Testcontainers dependencies and `jdbc:tc:` are absent.
- [x] Assert that `docs/database/sql/platform-support-schema.sql` and `platform-support-initial-data.sql` exist.
- [x] Run the focused tests and observe failures caused by the current dependencies/resources.

### Task 3: Replace migration and runtime seed with manual SQL

**Files:**
- Move: `platform-support-web/src/main/resources/db/migration/V1__platform_phase_a.sql` to `docs/database/sql/platform-support-schema.sql`
- Create: `docs/database/sql/platform-support-initial-data.sql`
- Modify: `platform-support-web/pom.xml`, `platform-support-web/src/main/resources/application.yml`
- Delete: Platform runtime seed configuration, domain, Service, Repository, Mapper, integration and XML files

- [x] Preserve all 21 current table definitions in the schema file.
- [x] Express only the currently implemented PLT-I20 non-secret Snapshot rows in one DML file; defer authentication, signing-key and audit initialization until those command lifecycles are implemented.
- [x] Remove Flyway, seed-only Snowflake/Bouncy Castle dependencies and startup initialization.
- [x] Run focused dependency/configuration tests to green.

### Task 4: Simplify Snapshot readiness and shared plumbing

**Files:**
- Modify: `QueryGatewaySnapshotProcessor.java`
- Replace: `PlatformRuntimeHealthIndicator.java` with capability-named Snapshot health code
- Delete: seed readiness and seed-specific metrics
- Delete: redundant `BusinessIdGenerator`/`SnowflakeBusinessIdGenerator`
- Modify: `application.yml`

- [x] First update HTTP and health tests to expect Snapshot availability to come from the database-backed Service rather than mutable seed flags.
- [x] Observe the focused test failure.
- [x] Remove mutable readiness and custom seed metrics while preserving fail-closed HTTP behavior.
- [x] Run focused tests to green.

### Task 5: Replace container/method coverage tests with local full-chain scenarios

**Files:**
- Modify: `platform-support-test/pom.xml`
- Modify: `platform-support-test/src/test/resources/application-auto.yml`
- Create: one small local database contract test
- Consolidate/delete: initialization, broad architecture and coverage-only test classes
- Preserve/simplify: Snapshot HTTP/mTLS and material Gateway compiler scenarios

- [x] Connect tests to `127.0.0.1:13306` using environment-provided credentials.
- [x] Fail fast with the documented local setup command if MySQL or schema data is absent.
- [x] Verify the real HTTP -> Processor -> Service -> Repository -> Mapper -> MySQL path.
- [x] Verify authorized mTLS access, unauthorized denial, valid full Snapshot and invalid-catalog `503`.
- [x] Run Maven verify and inspect JaCoCo from those business scenarios.

### Task 6: Final consistency and commits

**Files:** all changed files in the root and Platform worktrees.

- [x] Scan active docs/build/code for Flyway/Testcontainers/startup seed references.
- [x] Run Platform Maven verify using local MySQL.
- [x] Run root requirements build/tests/lint and relevant repository verification scripts.
- [x] Run `git diff --check` in both repositories and review complete diffs.
- [x] Commit the root and Platform branches separately with scoped messages.
