# Local Foundation Initialization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Initialize a non-destructive local `base` Docker Compose environment and the first reusable Java build/foundation JARs.

**Architecture:** Keep runtime infrastructure under the isolated `mor-local` Compose project and keep Java governance split into a dependency BOM, build parent, and focused foundation modules. The first Java slice contains only framework-independent errors, Snowflake IDs, and the MVC error/request-ID starter; business services, business schemas, Flyway, compensation persistence, scheduler, and observability remain out of scope.

**Tech Stack:** Java 25, Maven 3.9.16 Wrapper, Spring Boot 4.0.8, Spring Cloud 2025.1.3, Spring Cloud Alibaba 2025.1.0.0, MySQL 8.4.11, Nacos 3.1.2, RabbitMQ 4.2.9, Redis 8.2.1, JUnit 5.

**Spec:** `agent-docs/TECHNOLOGY_STACK.md` and `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`

## Global Constraints

- Add files incrementally; never overwrite, delete, reset, clean, commit, or reformat another task's work.
- Use Maven `groupId` `com.paytech` and foundation packages under `com.paytech.foundation.*` for this initial slice.
- Compile, test, and run with JDK 25; do not globally enable virtual threads.
- Compose project name is `mor-local`; every host port binds to `127.0.0.1` and existing Redis `6379` and Nexus `18081` are untouched.
- Base-profile infrastructure is MySQL `8.4.11`, Nacos `3.1.2`, RabbitMQ `4.2.9-management`, and Redis `8.2.1`; never use `latest`.
- Compose creates empty per-system databases and scoped users only. It creates Nacos technical tables from the official `3.1.2` schema and creates no business table or Flyway migration.
- Default Maven tests run only `**/auto/**/*Test.java`; no method-level behavior stubs are allowed.
- Do not create a generic common JAR, business DTOs, service scaffolds, compensation persistence, XXL-Job, or observability in this slice.
- Project guidance forbids commits and branches unless requested, so commit steps are intentionally omitted.

---

### Task 1: Unified Maven BOM and Parent

**Files:**
- Create: `mor-build/pom.xml`
- Create: `mor-build/mor-dependencies/pom.xml`
- Create: `mor-build/mor-parent/pom.xml`
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `mvnw.cmd`

**Interfaces:**
- Produces: `com.paytech:mor-dependencies:1.0.0-SNAPSHOT` dependency management and `com.paytech:mor-parent:1.0.0-SNAPSHOT` plugin/build management.
- Consumes: the exact framework versions in the global constraints.

- [x] **Step 1: Add a BOM model verification script**

Create `scripts/verify-java-foundation.mjs` that parses the effective Maven model and fails when Java is not 25, an approved BOM is absent, a child declares a managed version, or default Surefire execution does not select `auto` tests.

- [x] **Step 2: Run the verifier and observe the expected missing-build failure**

Run: `node scripts/verify-java-foundation.mjs`

Expected: non-zero exit identifying missing `mor-build` and `mor-foundation` files.

- [x] **Step 3: Add the build reactor**

The BOM imports the three approved upstream BOMs and manages MyBatis `4.0.1`, Redisson `4.6.1`, XXL-Job `3.4.2`, and all Code-MoR foundation artifacts. The parent inherits the local BOM, sets `maven.compiler.release=25`, and manages Compiler, Surefire, Failsafe, JaCoCo, Enforcer, and Spring Boot Maven plugins. Enforcer requires JDK `[25,26)` and Maven `[3.9.16,4)`.

- [x] **Step 4: Generate and verify the Maven 3.9.16 Wrapper**

Run with JDK 25: `mvn wrapper:wrapper -Dmaven=3.9.16`

Expected: Wrapper files are created without a root Maven reactor or edits to service directories.

### Task 2: Common Core Error Contract

**Files:**
- Create: `mor-foundation/pom.xml`
- Create: `mor-foundation/mor-common-core/pom.xml`
- Test: `mor-foundation/mor-common-core/src/test/java/com/paytech/foundation/common/auto/error/BusinessExceptionTest.java`
- Create: `mor-foundation/mor-common-core/src/main/java/com/paytech/foundation/common/error/ErrorCode.java`
- Create: `mor-foundation/mor-common-core/src/main/java/com/paytech/foundation/common/error/CommonErrorCode.java`
- Create: `mor-foundation/mor-common-core/src/main/java/com/paytech/foundation/common/error/BusinessException.java`
- Create: `mor-foundation/mor-common-core/src/main/java/com/paytech/foundation/common/error/BizCheckFailException.java`

**Interfaces:**
- Produces: `ErrorCode#code()`, `ErrorCode#defaultMessage()`, `BusinessException#errorCode()`, and `BizCheckFailException(String)`.
- Consumes: no Spring, servlet, persistence, messaging, or Redis API.

- [x] **Step 1: Write failing error-contract tests**

Tests assert that explicit Chinese business context is preserved, blank messages use the stable default, causes are retained, and a null `ErrorCode` fails immediately.

- [x] **Step 2: Run RED**

Run: `./mvnw -f mor-foundation/pom.xml -pl mor-common-core test`

Expected: compilation fails because the production error types do not exist.

- [x] **Step 3: Implement the minimal framework-independent contract**

Use stable codes `MOR-COMMON-400001`, `MOR-COMMON-400002`, and `MOR-COMMON-500001`; do not attach HTTP status types to the core JAR.

- [x] **Step 4: Run GREEN**

Run the same Maven command and expect all common-core tests to pass.

### Task 3: Snowflake ID JAR

**Files:**
- Create: `mor-foundation/mor-id-snowflake/pom.xml`
- Test: `mor-foundation/mor-id-snowflake/src/test/java/com/paytech/foundation/id/auto/SnowflakeIdGeneratorTest.java`
- Create: `mor-foundation/mor-id-snowflake/src/main/java/com/paytech/foundation/id/IdGenerator.java`
- Create: `mor-foundation/mor-id-snowflake/src/main/java/com/paytech/foundation/id/IdGenerationException.java`
- Create: `mor-foundation/mor-id-snowflake/src/main/java/com/paytech/foundation/id/SnowflakeIdGenerator.java`

**Interfaces:**
- Produces: `long IdGenerator#nextId()` and constructors accepting explicit Region/Worker identifiers, Clock, epoch, and rollback tolerance.
- Consumes: only the Java standard library and `mor-common-core` for stable exceptions if needed.

- [x] **Step 1: Write failing Snowflake behavior tests**

Tests assert increasing unique IDs in one millisecond, Region/Worker range rejection, tolerated small rollback without duplicate IDs, excessive rollback fail-closed behavior, and pre-epoch rejection.

- [x] **Step 2: Run RED**

Run: `./mvnw -f mor-foundation/pom.xml -pl mor-id-snowflake -am test`

Expected: compilation fails because the generator is absent.

- [x] **Step 3: Implement a synchronized 41/5/5/12-bit generator**

Use a UTC `2025-01-01T00:00:00Z` epoch, explicit Region/Worker `0..31`, a bounded rollback tolerance, and fail closed when uniqueness cannot be guaranteed. Do not create a static singleton or random node assignment.

- [x] **Step 4: Run GREEN**

Run the same Maven command and expect all common-core and ID tests to pass.

### Task 4: MVC Web Starter

**Files:**
- Create: `mor-foundation/mor-web-starter/pom.xml`
- Test: `mor-foundation/mor-web-starter/src/test/java/com/paytech/foundation/web/auto/RequestIdFilterTest.java`
- Test: `mor-foundation/mor-web-starter/src/test/java/com/paytech/foundation/web/auto/GlobalExceptionHandlerTest.java`
- Create: `mor-foundation/mor-web-starter/src/main/java/com/paytech/foundation/web/error/ApiErrorResponse.java`
- Create: `mor-foundation/mor-web-starter/src/main/java/com/paytech/foundation/web/error/ApiErrorResponseFactory.java`
- Create: `mor-foundation/mor-web-starter/src/main/java/com/paytech/foundation/web/error/GlobalExceptionHandler.java`
- Create: `mor-foundation/mor-web-starter/src/main/java/com/paytech/foundation/web/request/RequestIdFilter.java`
- Create: `mor-foundation/mor-web-starter/src/main/java/com/paytech/foundation/web/autoconfigure/MorWebAutoConfiguration.java`
- Create: `mor-foundation/mor-web-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Interfaces:**
- Produces: JSON error fields `code`, `message`, `requestId`, and UTC `timestamp`; request header/response header `X-Request-Id`; MVC exception mappings for business, validation, and unexpected exceptions.
- Consumes: `mor-common-core`, Spring MVC, Jakarta Servlet, SLF4J/MDC, and Spring Boot autoconfiguration.

- [x] **Step 1: Write failing request-ID and exception mapping tests**

Tests exercise real servlet request/response objects, verify accepted and generated request IDs, MDC cleanup, deterministic UTC timestamps, business error mapping to 400, and unexpected error mapping to 500 without leaking exception details.

- [x] **Step 2: Run RED**

Run: `./mvnw -f mor-foundation/pom.xml -pl mor-web-starter -am test`

Expected: compilation fails because the web types are absent.

- [x] **Step 3: Implement the minimal starter**

Accept only request IDs matching `[A-Za-z0-9._:-]{1,128}`; otherwise generate one. Store it in a request attribute and MDC for the request duration, return it in the response header, and always clear MDC in `finally`. Map known messages safely and return a generic Chinese message for unexpected failures.

- [x] **Step 4: Run GREEN**

Run the same Maven command and expect all foundation tests to pass.

### Task 5: Local Base Docker Compose

**Files:**
- Create: `local-environment/compose.yaml`
- Create: `local-environment/.env.example`
- Create: `local-environment/README.md`
- Create: `local-environment/mysql/init/01-create-databases-and-users.sh`
- Create: `local-environment/mysql/init/02-nacos-schema.sql`
- Modify: `.gitignore` only to allow `local-environment/.env.example`
- Modify: `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md` only if Nacos 3 console/client ports need correction.

**Interfaces:**
- Produces: Compose Profile `base`, project `mor-local`, empty per-system schemas/scoped users, Nacos technical schema, RabbitMQ vhost/account, and password-protected isolated Redis.
- Consumes: developer-created `local-environment/.env.local`; existing Docker containers and volumes are never referenced.

- [x] **Step 1: Add configuration fixtures and run a failing Compose validation**

Run: `docker compose --env-file local-environment/.env.example -f local-environment/compose.yaml --profile base config`

Expected before files exist: non-zero exit for missing Compose file.

- [x] **Step 2: Add isolated Compose/configuration files**

Bind MySQL to `127.0.0.1:13306`, Redis to `127.0.0.1:16379`, RabbitMQ to `127.0.0.1:5672/15672`, Nacos client endpoints to `127.0.0.1:8848/9848`, and the Nacos 3 console to an unused loopback port. Use named volumes, health checks, bounded container memory, and no container names.

- [x] **Step 3: Validate shell, SQL provenance, resolved Compose, and isolation**

Run `bash -n` on the MySQL initializer, render Compose with `.env.example`, and verify all published host bindings resolve to `127.0.0.1`. Do not run `up`, pull images, delete volumes, or touch the Docker daemon in this task.

### Task 6: Fresh Full Verification

**Files:**
- Modify: `README.md` with links only if no concurrent task has changed the intended section.
- Modify: `agent-docs/TECHNOLOGY_STACK.md` to record the now-selected `com.paytech` namespace and initialized-module boundary.

**Interfaces:**
- Produces: reproducible build and local-infrastructure usage instructions.
- Consumes: all earlier tasks.

- [x] **Step 1: Re-run the foundation verifier and effective Maven model checks**

Run: `node scripts/verify-java-foundation.mjs` and `./mvnw -f mor-foundation/pom.xml help:effective-pom`.

- [x] **Step 2: Run the complete foundation reactor tests**

Run: `./mvnw -f mor-foundation/pom.xml clean verify`.

- [x] **Step 3: Re-render Compose and inspect the scoped diff**

Run Compose config, `git diff --check`, targeted `git status --short`, secret/placeholder scans, and confirm no business service, migration, Docker state, branch, or commit was changed.
