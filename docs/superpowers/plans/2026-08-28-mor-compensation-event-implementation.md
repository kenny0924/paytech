# MoR Compensation Event Implementation Plan

> **Execution note:** This plan has no agent-skill dependency. Checkboxes remain as the original verification and
> publication gates; they are not retroactively marked complete without current command evidence.

**Goal:** Build, test and locally publish a hardened `com.paytech:mor-compensation-event:1.0.0-SNAPSHOT` Foundation
JAR by selectively porting the useful Code-PMX compensation-event ideas into the approved Code-MoR contract.

**Architecture:** One Spring Boot 4 auto-configured JAR registers immutable work inside the caller transaction,
wakes only after commit, atomically claims local rows with Lease/fencing, executes bounded handlers, and optionally
publishes RabbitMQ events through an exclusive per-attempt physical Connection/Channel with mandatory persistent
confirms. Each service owns its table and XXL-Job entry point.

**Tech Stack:** Java 25, Maven 3.9.16 Wrapper, Spring Boot 4.0.8, MyBatis Starter 4.0.1, Spring Transaction,
Spring AMQP, Jackson 3, Micrometer, MySQL 8.4.11 Testcontainers, RabbitMQ 4.2.9 Testcontainers, JUnit 5 and Awaitility.

**Spec:** `docs/superpowers/specs/2026-08-28-mor-compensation-event-design.md`

## Global constraints

- Preserve all unrelated and concurrent workspace changes; never clean or revert the dirty tree.
- Do not modify `/Users/kenny/Desktop/Workspace/Code-PMX/compensation-event`.
- Do not modify Risk Settlement model, manifest, specialist documents or plan files.
- Do not create a branch, worktree, commit, push or external deployment; project rules override generic skill commit
  instructions.
- Use `apply_patch` for source/document edits.
- Follow strict RED-GREEN-REFACTOR. No target production Java is added before a test that requires it has failed for
  the expected reason.
- Tests use real MySQL/RabbitMQ Testcontainers and stateful test components, never Mockito behavior stubs,
  `@MockBean`, `@MockitoBean`, spies, shared local middleware or fixed sleeps.
- Do not run a formatter; the user will format separately. Correctness and automated verification remain required.
- The current user instruction delegates unit/integration execution to the user; implementation work runs only static
  and compile/test-compile checks unless that instruction changes.
- Do not add a Foundation-owned production migration or automatic schema initialization.
- Shared `README.md`, `AGENTS.md`, `agent-docs/PROJECT_CONTEXT.md`, `agent-docs/TECHNOLOGY_STACK.md`,
  `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md` and requirements content stay frozen until publication succeeds and
  concurrent tasks coordinate their update.

## Task 1: Add the Maven/test harness and prove the initial RED

**Files:**

- Modify: `mor-foundation/pom.xml`
- Modify: `mor-build/mor-dependencies/pom.xml`
- Modify: `scripts/verify-java-foundation.mjs`
- Create: `mor-foundation/mor-compensation-event/pom.xml`
- Create: `mor-foundation/mor-compensation-event/src/test/resources/schema/compensation-event.sql`
- Create: `mor-foundation/mor-compensation-event/src/test/java/com/paytech/foundation/compensation/test/**`
- Create: `mor-foundation/mor-compensation-event/src/test/java/com/paytech/foundation/compensation/auto/CompensationEventRegistrationAutoTest.java`

- [ ] Extend the Foundation reactor, BOM coordinates and structural verifier for `mor-compensation-event` without
  declaring versions in the child POM.
- [ ] Add only the dependencies needed by the approved module and tests. RabbitMQ integration remains conditional for
  consumers but available to this module's integration tests.
- [ ] Add a MySQL contract DDL fixture containing the approved columns and indexes. It is test infrastructure, not a
  production migration.
- [ ] Add the first real MySQL test for “business transaction rolls back, so neither business marker nor compensation
  row survives.” The test must call the public registrar contract that does not exist yet.
- [ ] Run:

  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 25)
  export PATH="$JAVA_HOME/bin:$PATH"
  ./mvnw -f mor-foundation/pom.xml -pl mor-compensation-event -am test \
    -Dtest=CompensationEventRegistrationAutoTest -Dsurefire.failIfNoSpecifiedTests=false
  ```

  Expected RED: test compilation fails because the compensation registration API/implementation is absent. Record the
  failure before adding production Java.

## Task 2: Implement immutable registration, canonical envelope and transaction afterCommit

**Files:**

- Create: `.../api/CompensationEventRegistrar.java`
- Create: `.../api/CompensationEventRequest.java`
- Create: `.../api/RegisteredCompensationEvent.java`
- Create: `.../model/CompensationExecutionType.java`
- Create: `.../model/CompensationEventStatus.java`
- Create: `.../model/CompensationEventRecord.java`
- Create: `.../execution/CompensationHandlerRegistry.java`
- Create: `.../execution/CompensationAfterCommitDispatcher.java`
- Create: `.../persistence/CompensationEventMapper.java`
- Create: `.../persistence/MyBatisCompensationEventStore.java`
- Create: `.../persistence/CompensationEventRegistrationService.java`
- Create: `.../serialization/CanonicalEventEnvelopeFactory.java`
- Create: `.../serialization/CompensationPayloadPolicy.java`
- Create: `.../error/CompensationEventConflictException.java`
- Create: `.../error/CompensationConfigurationException.java`
- Create: `src/main/resources/META-INF/mybatis/CompensationEventMapper.xml`
- Extend: `CompensationEventRegistrationAutoTest.java`
- Create: `.../auto/CanonicalEventEnvelopeAutoTest.java`

- [ ] Add pure tests with literal expectations for deterministic normal/hashed event keys, recursively ordered JSON,
  raw database hash versus prefixed envelope hash, 64 KiB enforcement and secret/card-key rejection. Observe RED.
- [ ] Implement the smallest immutable models, validation and canonicalization to make those pure tests GREEN.
- [ ] Add MySQL tests for no-active-transaction rejection, rollback, commit, identical duplicate registration and
  same-key/same-source different-content conflict. Observe RED.
- [ ] Implement Mapper XML and registration against the host transaction. Duplicate rows compare all immutable
  fields. Registration adds a non-throwing afterCommit wake callback.
- [ ] Run the two focused test classes, then refactor only after they pass.

## Task 3: Implement atomic Claim, Lease takeover and fencing

**Files:**

- Extend: `CompensationEventMapper.java`
- Extend: `CompensationEventMapper.xml`
- Extend: `MyBatisCompensationEventStore.java`
- Create: `.../model/ClaimedCompensationEvent.java`
- Create: `.../api/CompensationScanRequest.java`
- Create: `.../api/CompensationScanResult.java`
- Create: `.../api/CompensationEventScanner.java`
- Create: `.../execution/CompensationEventScannerService.java`
- Create: `.../auto/CompensationEventClaimLeaseAutoTest.java`

- [ ] Write real MySQL tests for two concurrent owners claiming one due row, active-Lease exclusion, expired-Lease
  takeover, attempt increment, and old-owner update/delete fencing. Use barriers/latches and bounded Awaitility, not
  sleeps. Observe RED.
- [ ] Implement candidate scanning and conditional Claim. Claim writes PROCESSING, owner, database-time Lease,
  increments attempt and lock version.
- [ ] Implement conditional success delete, retry transition and terminal FAILED transition with the full current
  Lease fencing predicate.
- [ ] Add shard and event-type allowlist tests, then make the scanner submit at most the requested batch.
- [ ] Run `CompensationEventClaimLeaseAutoTest` until GREEN and inspect all affected-row assertions.

## Task 4: Implement Handler registry, bounded dispatch, timeout and retry lifecycle

**Files:**

- Create: `.../api/CompensationExecutionPolicy.java`
- Create: `.../api/CompensationHandlerResult.java`
- Create: `.../api/LocalCompensationEventHandler.java`
- Create: `.../execution/CompensationEventDispatcher.java`
- Create: `.../execution/RetryDelayCalculator.java`
- Create: `.../execution/SafeErrorSummary.java`
- Create: `.../observability/CompensationEventObserver.java`
- Create: `.../observability/MicrometerCompensationEventObserver.java`
- Create: `.../auto/CompensationEventDispatcherAutoTest.java`
- Create: `.../test/handler/ScenarioLocalCompensationEventHandler.java`

- [ ] Write scenario-driven tests for success deletion, retryable ERROR with bounded exponential delay, exhausted
  FAILED, terminal FAILED, thrown exception sanitization, missing Handler, duplicate Handler startup failure and late
  timeout result fencing. Observe RED.
- [ ] Add bounded executor tests using core=1/max=1/queue=1 and blocking scenario handlers. Prove rejection leaves the
  rejected row unclaimed/recoverable and emits an observation. Observe RED.
- [ ] Implement Handler registry and policy validation. Null/IGNORE are not representable.
- [ ] Implement Claim-inside-Coordinator behavior, `AbortPolicy`, pre-Claim Handler-capacity backpressure, an isolated
  bounded platform-thread Handler pool, timed Coordinator wait, interrupt and fenced result completion. A permit is
  released only when the real Handler exits.
- [ ] Implement low-cardinality observation and safe error summaries; never log or tag payload/event source ID.
- [ ] Run the focused dispatcher tests until GREEN, then rerun registration and Lease tests for regression.

## Task 5: Implement mandatory persistent RabbitMQ publication with Confirm

**Files:**

- Create: `.../api/RabbitMqEventRoute.java`
- Create: `.../messaging/RabbitMqCompensationPublisher.java`
- Create: `.../messaging/CompensationRabbitConnectionFactoryProvider.java`
- Create: `.../messaging/ConfirmingRabbitMqCompensationPublisher.java`
- Create: `.../messaging/RabbitMqConfirmClassifier.java`
- Create: `.../autoconfigure/MorCompensationRabbitAutoConfiguration.java`
- Extend: `CompensationHandlerRegistry.java`
- Create: `.../auto/RabbitMqCompensationPublisherAutoTest.java`
- Create: `.../auto/RabbitMqCompensationDispatchAutoTest.java`

- [ ] Start RabbitMQ 4.2.9 through Testcontainers and declare test-owned topic exchange/queue/binding.
- [ ] Write a successful real-broker test that asserts exchange/routing, persistent delivery, JSON UTF-8, message ID,
  exact canonical envelope and deletion only after Confirm. Observe RED.
- [ ] Write an unroutable mandatory-return test that retains the row as ERROR, and a repeated-publish test that emits
  byte-identical envelopes with the same event key/hash. Observe RED.
- [ ] Implement an attempt-scoped mandatory path backed by an exclusive Provider, `CacheMode.CONNECTION`, persistent
  JSON, synchronous Confirm and Return check. Ack without Return is the only success; timeout/interruption/unknown
  outcome aborts the exact attempt physical Connection with a positive channel RPC timeout. Logical resources and
  permits always close, and Confirm waiting preserves the exact validated nanosecond Duration.
- [ ] Require `0 < confirmTimeout + channelRpcTimeout < policy.timeout < leaseDuration`, Provider concurrency equal to
  Handler maximum, no general-purpose exposure of the nested factory, bounded Rabbit callback threads, lifecycle drain
  on close and payload-safe DEBUG/TRACE logging.
- [ ] Keep queue/DLQ declaration and consumer idempotency outside the module.
- [ ] Run the focused RabbitMQ test and the full dispatcher tests until GREEN.

## Task 6: Add audited manual recovery and Spring Boot 4 auto-configuration

**Files:**

- Create: `.../api/CompensationEventRecoveryService.java`
- Create: `.../api/CompensationEventReopenRequest.java`
- Create: `.../api/CompensationRecoveryAudit.java`
- Create: `.../api/CompensationRecoveryAuditRecorder.java`
- Create: `.../persistence/CompensationEventRecoveryServiceImpl.java`
- Create: `.../autoconfigure/MorCompensationEventProperties.java`
- Create: `.../autoconfigure/MorCompensationEventAutoConfiguration.java`
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `.../auto/CompensationEventRecoveryAutoTest.java`
- Create: `.../auto/MorCompensationEventAutoConfigurationTest.java`

- [ ] Write MySQL tests proving reopen requires FAILED + expected version + actor/reason/ticket, rolls back with the
  host audit insert, resets attempts on commit, retains prior error and wakes only after commit. Observe RED.
- [ ] Implement recovery only when an audit recorder bean exists; never provide an unaudited default.
- [ ] Write ApplicationContextRunner tests for valid configuration, disabled mode, missing identity/infrastructure,
  invalid pool/Lease/policy, duplicate/cross-mechanism Handler/Route, optional AMQP loading and attempts to replace the
  production Dispatcher/core Executors. Observe RED.
- [ ] Implement explicit Boot 4 bean configuration with constructor injection, conditional RabbitMQ configuration,
  configuration properties and existing host SqlSessionFactory/transaction manager reuse. Do not add component scan or
  `spring.factories`.
- [ ] Reproduce Payment's positive-lock CHECK in MySQL, initialize `lock_version=1`, and restrict source/schema versions
  to the positive INTEGER range shared by the approved physical models.
- [ ] Run recovery and auto-configuration tests until GREEN.

## Task 7: Verify, document and publish to local Nexus

**Files:**

- Modify: `mor-foundation/README.md`
- Modify only after coordination: `agent-docs/TECHNOLOGY_STACK.md`
- Modify only after coordination: `agent-docs/PROJECT_CONTEXT.md`
- Modify only after coordination: `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`
- Review all files touched by Tasks 1-6.

- [ ] Respect the user's final instruction not to run code formatting; validate compilation, tests and whitespace
  correctness without rewriting style.
- [ ] Run:

  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 25)
  export PATH="$JAVA_HOME/bin:$PATH"
  export MOR_MAVEN_SETTINGS=/Users/kenny/Desktop/Workspace/Settings/3-Maven/settings/local_settings.xml
  node scripts/verify-java-foundation.mjs
  ./mvnw --settings "$MOR_MAVEN_SETTINGS" -f mor-build/pom.xml clean verify
  ./mvnw --settings "$MOR_MAVEN_SETTINGS" -f mor-foundation/pom.xml clean verify
  ./mvnw --settings "$MOR_MAVEN_SETTINGS" -f mor-foundation/pom.xml \
    -pl mor-compensation-event -am dependency:tree
  git diff --check
  ```

- [ ] Run a separate code-quality and spec review. Fix every confirmed correctness or test-integrity issue and rerun
  the affected tests.
- [ ] Update `mor-foundation/README.md` with public usage, table ownership, XXL scan seam and test commands.
- [ ] Before any non-Risk host adopts the JAR, coordinate its host-owned PDManer comments, canonical key/envelope/hash
  fixtures, initial `lock_version=1`, indexes, migrations and Java mappings; do not silently treat legacy scenario
  fixtures as runtime-compatible.
- [ ] Coordinate the shared-document window. Update shared status documents only after all tests pass and the parallel
  task confirms those files are free.
- [ ] Validate Maven Settings without exposing credentials, then explicitly deploy the build and Foundation reactor
  to the existing local Nexus.
- [ ] Resolve `mor-compensation-event:1.0.0-SNAPSHOT` from `maven-public` using a fresh temporary Maven repository.
- [ ] Report exact test counts, Nexus verification and any deferred per-event SLA/configuration work.

## Current implementation snapshot

As of 2026-08-28, production sources and the corresponding automated test sources are implemented. The latest
post-hardening command executed by this task is Java 25 Maven `-DskipTests test-compile`, which compiled 48 main and 14
test source files successfully. No unit/integration/full `verify` run has been performed after the exclusive RabbitMQ
Connection redesign because the user owns test execution. Local Nexus deployment and fresh-repository resolution also
remain intentionally pending; this snapshot must not be read as runtime-test or publication evidence.
