# Platform Support Capability Module Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the currently implemented Platform Support Access Control and Audit/Security code from `platform-support-web` into two non-deployable capability JARs without changing HTTP, database, lock, transaction, audit, or runtime behavior.

**Architecture:** `platform-support-web` remains the only Spring Boot application and composition root. It depends on `platform-support-access-control` and `platform-support-audit-security`; Access Control has one approved dependency on Audit/Security's four-method audit fact sink, while Audit/Security never depends on Access Control. All modules continue to share the same Platform MySQL database, primary DataSource and transaction manager; the existing same-database audit writer pool remains the only specialized connection pool.

**Tech Stack:** Java 25, Maven Reactor, Spring Boot 4.0.8, Spring MVC, MyBatis, MySQL 8.4, Foundation `@ResourceLock`, JUnit 5, JaCoCo.

**Spec:** `docs/superpowers/specs/2026-09-06-platform-support-modular-code-design.md`

## Global Constraints

- Runtime changes are limited to `platform-support-service`; Gateway, BFF, core-system and Vault repositories are not modified.
- The first migration creates only `platform-support-access-control` and `platform-support-audit-security`; it does not pre-create empty File/Document or Delivery modules.
- Preserve every public HTTP path, request/response body, error Code/status, Gateway Snapshot/JWKS contract and Operations behavior.
- Preserve the existing 22-table schema and initialization DML; do not add, remove, rename or migrate database objects or data.
- Keep one Spring Boot process, one Nacos service identity, one primary DataSource and one primary transaction manager.
- Keep the existing same-database, one-connection audit write pool and independent audit transaction; do not add a per-module DataSource.
- Keep Controller, Processor, `@ResourceLock`, `TransactionTemplate`, audit Aspect/Context/Dispatcher and protocol mapping in Web.
- Keep `t_command_idempotency` Mapper, Repository and implementation in Web service-level reliability infrastructure.
- Move each Access Control and Audit/Security DataObject, Mapper, XML and Repository to exactly one owning capability JAR.
- Use capability-root packages `com.paytech.platform.support.accesscontrol...` and `com.paytech.platform.support.auditsecurity...` so capability JARs do not create split packages.
- `platform-support-access-control` may depend on Audit/Security only through `OperationsAuditService` and immutable audit fact types; Audit/Security must not depend on Access Control.
- `platform-support-test` continues to depend directly only on `platform-support-web`; ordinary HTTP/business assertions remain unchanged.
- Test edits are limited to package imports, Maven/source-path expectations and new module ownership/DAG gates.
- Do not create a branch, commit, push, deploy, reset the local database or change another repository unless the user explicitly requests it.

---

### Task 1: Freeze the Target Reactor and Ownership Boundary

**Files:**
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportDependencyBoundaryAutoTest.java`
- Create: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/architecture/PlatformCapabilityModuleBoundaryAutoTest.java`

**Interfaces:**
- Consumes: the confirmed five-module first-stage Reactor `api`, `audit-security`, `access-control`, `web`, `test`.
- Produces: executable tests that reject missing capability POMs, wrong Maven edges, split capability packages, misplaced Owner classes and Web access to capability DAL.

- [x] **Step 1: Add a failing Reactor/DAG test**

  Extend `PlatformSupportDependencyBoundaryAutoTest` to assert the exact root module set and direct dependency sets. The approved production edges are:

  ```text
  platform-support-access-control -> platform-support-api
  platform-support-access-control -> platform-support-audit-security
  platform-support-web -> platform-support-api
  platform-support-web -> platform-support-access-control
  platform-support-web -> platform-support-audit-security
  platform-support-test -> platform-support-web
  ```

  Audit/Security has no Platform module dependency. Test must also assert that neither capability POM contains Boot repackage configuration.

- [x] **Step 2: Add a failing physical Owner test**

  Add `PlatformCapabilityModuleBoundaryAutoTest` that resolves the service root and asserts representative paths and package declarations:

  ```text
  platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/core/dal/mapper/OperationsIdentityMapper.java
  platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/OperationsAuthenticationService.java
  platform-support-audit-security/src/main/java/com/paytech/platform/support/auditsecurity/core/dal/mapper/OperationsAuditMapper.java
  platform-support-audit-security/src/main/java/com/paytech/platform/support/auditsecurity/domainservice/service/OperationsAuditService.java
  platform-support-web/src/main/java/com/paytech/platform/support/core/reliability/mapper/CommandIdempotencyMapper.java
  ```

  It must reject capability source packages under the old shared roots, capability imports of Web/Processor/Controller packages, Audit/Security imports of Access Control, and Access Control imports of Audit/Security DAL, Repository, `*Impl`, writer or query implementation packages.

- [x] **Step 3: Verify the new tests fail for the intended reason**

  Run:

  ```bash
  cd platform-support-service
  ./mvnw -q -pl platform-support-test -am \
    -Dtest=PlatformSupportDependencyBoundaryAutoTest,PlatformCapabilityModuleBoundaryAutoTest test
  ```

  Expected result: failure because the two capability POMs and representative Owner paths do not exist. A compilation error is not the expected red state.

---

### Task 2: Separate Web-Owned Command Idempotency Before Moving Access Control

**Files:**
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsPersistenceContractAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsLockContractAutoTest.java`
- Create: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/reliability/mapper/CommandIdempotencyMapper.java`
- Create: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/reliability/repository/CommandIdempotencyRepository.java`
- Create: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/reliability/repository/impl/CommandIdempotencyRepositoryImpl.java`
- Move: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/OperationsCommandIdempotencyService.java` to `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/reliability/service/OperationsCommandIdempotencyService.java`
- Move: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/service/impl/OperationsCommandIdempotencyServiceImpl.java` to `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/reliability/service/impl/OperationsCommandIdempotencyServiceImpl.java`
- Create: `platform-support-service/platform-support-web/src/main/resources/mapper/reliability/CommandIdempotencyMapper.xml`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/dal/mapper/OperationsDirectoryMapper.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/OperationsDirectoryRepository.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/impl/OperationsDirectoryRepositoryImpl.java`
- Modify: `platform-support-service/platform-support-web/src/main/resources/mapper/OperationsDirectoryMapper.xml`

**Interfaces:**
- Produces: Web-owned `OperationsCommandIdempotencyService` and `PreparedCommandIdempotency` with their current signatures; Task 4 replaces only the cross-module prepared type with the Access-owned port.
- Produces: a dedicated Web Mapper/Repository boundary owning SQL, IDs, hashing and duplicate-key translation.

- [x] **Step 1: Add the persistence ownership assertion**

  Extend `OperationsPersistenceContractAutoTest` so it requires `mapper/reliability/CommandIdempotencyMapper.xml` to contain the only two `t_command_idempotency` statements and rejects `t_command_idempotency` in every capability Mapper XML.

- [x] **Step 2: Verify the ownership assertion fails**

  Run:

  ```bash
  ./mvnw -q -pl platform-support-test -am \
    -Dtest=OperationsPersistenceContractAutoTest test
  ```

  Expected result: failure because idempotency SQL still resides in `OperationsDirectoryMapper.xml`.

- [x] **Step 3: Extract Mapper and Repository without changing SQL**

  Move the existing `previousCommand` select and `completeCommand` insert byte-for-byte into `CommandIdempotencyMapper.xml`. Create the dedicated Mapper and Repository interface/implementation with the same parameter order and return values, then remove those methods from `OperationsDirectoryMapper`, `OperationsDirectoryRepository` and their implementation.

- [x] **Step 4: Relocate the service implementation**

  Move the service and its existing prepared record to `core.reliability.service`. Change only the Repository dependency to the new Web reliability Repository and preserve `previous(...)`, `complete(...)`, `uk_command_idempotency` conflict translation and the redacted `toString()` contract. Task 4 performs the separate type-safe port conversion immediately before Access Control moves to another JAR.

- [x] **Step 5: Re-run focused idempotency and persistence tests**

  Run:

  ```bash
  ./mvnw -q -pl platform-support-test -am \
    -Dtest=OperationsPersistenceContractAutoTest,OperationsLockContractAutoTest test
  ```

  Expected result: both classes pass without changing their business expectations.

---

### Task 3: Extract Audit/Security as a One-Way Capability

**Files:**
- Create: `platform-support-service/platform-support-audit-security/pom.xml`
- Move to `platform-support-audit-security/src/main/java/com/paytech/platform/support/auditsecurity/domain/audit/`: `AuditChainClosedException.java`, `AuditModels.java`, `OperationsAuditFact.java`, `OperationsAuditProjector.java`, `OperationsAuditQuery.java`
- Move to `platform-support-audit-security/src/main/java/com/paytech/platform/support/auditsecurity/domain/enums/`: `OperationsAuditChainStatus.java`, `OperationsAuditClassification.java`
- Move to `platform-support-audit-security/src/main/java/com/paytech/platform/support/auditsecurity/core/dal/dataobject/`: `OperationsAuditAttribute.java`, `OperationsAuditChain.java`, `OperationsAuditRecord.java`
- Move to `platform-support-audit-security/src/main/java/com/paytech/platform/support/auditsecurity/core/dal/mapper/`: `OperationsAuditMapper.java`
- Move to `platform-support-audit-security/src/main/java/com/paytech/platform/support/auditsecurity/domainservice/repository/` and `repository/impl/`: both audit Repository interfaces and implementations.
- Move to `platform-support-audit-security/src/main/java/com/paytech/platform/support/auditsecurity/domainservice/service/` and `service/impl/`: `OperationsAuditService.java`, `OperationsAuditQueryService.java`, `OperationsAuditWriter.java`, `OperationsAuditQueryServiceImpl.java`, `OperationsAuditWriterImpl.java`.
- Move: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/configuration/OperationsAuditProperties.java` to `platform-support-audit-security/src/main/java/com/paytech/platform/support/auditsecurity/configuration/OperationsAuditProperties.java`.
- Create: `platform-support-service/platform-support-audit-security/src/main/java/com/paytech/platform/support/auditsecurity/domain/audit/OperationsAuditQueryModels.java`
- Move: `platform-support-service/platform-support-web/src/main/resources/mapper/OperationsAuditMapper.xml` to `platform-support-audit-security/src/main/resources/mapper/audit/OperationsAuditMapper.xml`
- Keep and modify: Web `AuditCompletion`, `AuditContext`, `AuditDispatcher`, `Audit`, `AuditAspect`, `OperationsAuditPersistenceConfiguration`, `OperationsTransactionExecutor`.
- Move implementation only: Web `OperationsAuditServiceImpl` to `platform-support-web/src/main/java/com/paytech/platform/support/core/audit/OperationsAuditServiceImpl.java` while retaining its `AuditContext` behavior.
- Modify: `OperationsManagementModels.java`, `OperationsManagementProcessor.java`, audit test imports and audit Mapper XML namespace/result types.

**Interfaces:**
- Produces: Audit/Security `OperationsAuditService` with exactly `identify(Supplier<Identity>)`, `target(Supplier<Target>)`, `record(Supplier<? extends OperationsAuditFact>)`, `suppressReplay()`.
- Produces: `OperationsAuditQueryService.auditEvents(int, int, AuditSearch)` and `auditEvent(long)` returning types from `OperationsAuditQueryModels`.
- Consumes: Web provides the `OperationsAuditService` implementation backed only by the current invocation `AuditContext`.

- [x] **Step 1: Add failing audit ownership and dependency tests**

  Extend the capability boundary test to require every audit DO/Mapper/Repository in Audit/Security, to require `AuditContext`, `AuditAspect` and `AuditDispatcher` in Web, and to reject every `com.paytech.platform.support.accesscontrol` reference from Audit/Security.

- [x] **Step 2: Verify the audit boundary test fails**

  Run the two architecture tests and confirm they fail on the current Web-owned audit paths.

- [x] **Step 3: Split authorization from audit query**

  Move `AuditSearch`, `AuditEvent`, `AuditAttribute` and `AuditDetails` from `OperationsManagementModels` to `OperationsAuditQueryModels`. Change the audit query service so it no longer accepts actor/session or injects `OperationsManagementPolicyService`. In `OperationsManagementProcessor`, execute `policy.requirePermission(actor, session, "OPERATIONS_AUDIT_READ")` before calling Audit/Security and pass `policy.parseId(eventId)` for details. Keep both calls inside the existing read transaction.

- [x] **Step 4: Move and rename the audit packages**

  Move the listed production sources and XML, update Java packages/imports and the Mapper XML namespace/result types. Move `OperationsAuditProperties` with the writer budget contract so Audit/Security does not depend on Web; Web still creates the DataSource, transaction manager and SqlSession objects. The Audit/Security POM has exactly the directly used dependencies `paytech-common-core`, `paytech-snowflake-id`, `spring-context`, `spring-tx`, `spring-boot`, `spring-boot-autoconfigure`, `mybatis`, `mybatis-spring`, `jakarta.validation-api`, `lombok` and `jackson-databind`.

- [x] **Step 5: Preserve the specialized writer assembly**

  Update `OperationsAuditPersistenceConfiguration` to load exactly `classpath*:mapper/audit/OperationsAuditMapper.xml`. Keep `auditWriteDataSource`, `auditWriteTransactionManager`, `auditWriteSqlSessionFactory` and `auditWriteSqlSessionTemplate` in Web and keep the writer's `REQUIRES_NEW` timeout behavior.

- [x] **Step 6: Run audit tests**

  Run all pure audit tests first:

  ```bash
  ./mvnw -q -pl platform-support-test -am \
    -Dtest=OperationsAuditQueryAutoTest,OperationsAuditProjectorAutoTest,OperationsAuditCompletionAutoTest,OperationsAuditRuntimeAutoTest,PlatformCapabilityModuleBoundaryAutoTest test
  ```

  Then run audit HTTP/connection tests with the repository local-environment credentials when the fixed test database satisfies their documented clean-catalog precondition. Do not reset existing data to make them pass.

---

### Task 4: Extract Access Control and Remove Cross-Module DAL Leakage

**Files:**
- Create: `platform-support-service/platform-support-access-control/pom.xml`
- Create: `platform-support-service/platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domain/operations/CommandIdempotencyContext.java`
- Move all non-audit DataObjects from Web `core/dal/dataobject` to Access Control `core/dal/dataobject`.
- Move `GatewayConfigurationManagementMapper`, `GatewayConfigurationMapper`, `JwtSigningKeyMapper`, `OperationsDirectoryMapper`, `OperationsIdentityMapper` and their XML files to Access Control.
- Move the five corresponding Repository interfaces and implementations to Access Control.
- Move authentication, gateway-configuration and operations domain code to Access Control, except Web-owned audit lifecycle and `GatewaySnapshotAssembler`.
- Move all non-audit domain enums to Access Control.
- Move all non-audit business Service interfaces and implementations to Access Control, excluding Web command idempotency.
- Move signing and Vault port/local-adapter classes to Access Control; keep environment/profile Bean assembly in Web `OperationsAuthenticationConfiguration`.
- Modify all Web Controller/Processor/configuration imports and the 23 affected test source imports; do not change ordinary test bodies or assertions except types renamed by the module boundary.

**Interfaces:**
- Produces: Access Control Services under `com.paytech.platform.support.accesscontrol.domainservice.service`.
- Consumes: Audit/Security's four-method `OperationsAuditService` fact sink only.
- Consumes: Access-owned `CommandIdempotencyContext` implemented by Web reliability; no Access source imports Web classes.

- [x] **Step 1: Add failing no-DAL-leak and one-way-edge assertions**

  Extend `PlatformCapabilityModuleBoundaryAutoTest` to reject Access Control DataObject types in Web fields/method signatures, imports of Access Repository/Mapper/`*Impl` from Web, and every Access-to-Audit import outside the approved audit service/fact/model packages.

- [x] **Step 2: Verify the boundary test fails**

  Expected failures include the existing Web import of `OperationsResource` and the missing Access capability paths.

- [x] **Step 3: Introduce the narrow idempotency context**

  Define:

  ```java
  public interface CommandIdempotencyContext {
      String previous();
      void complete(String responseReference, Instant now);
      String opaqueReference();
  }
  ```

  Replace `PreparedCommandIdempotency` parameters in Account, Role and Gateway commands with this interface. The Web reliability implementation returned by `prepare(...)` implements it. Keep replay detection, conflict handling, audit suppression and response-reference semantics unchanged.

- [x] **Step 4: Replace the navigation DataObject boundary**

  Add an immutable Access domain navigation record containing `id`, `code`, `type`, `parentId`, `displayName`, `frontendRoute`, `componentKey` and `sortOrder`. `OperationsAuthorizationService.navigation(...)` returns this record rather than `OperationsResource`; map the Access DO inside the Service implementation. The Web Processor continues mapping the domain record to the existing API `ConsoleResource`.

- [x] **Step 5: Move Access Control production sources and resources**

  Move the listed sources to the capability-root package, update package declarations/imports and update all five Mapper XML namespaces/result types. Keep Controller, Processor, `@ResourceLock`, `OperationsTransactionExecutor`, `GatewaySnapshotAssembler`, health/configuration and API mapping in Web. The Access Control POM has exactly the directly used dependencies `platform-support-api`, `platform-support-audit-security`, `paytech-common-core`, `paytech-snowflake-id`, `jackson-annotations`, `spring-context`, `spring-tx`, `spring-boot-autoconfigure`, `mybatis`, `spring-security-crypto`, `bcprov-jdk18on`, `lombok` and `jackson-databind`.

- [x] **Step 6: Update Web composition and persistence scanning**

  Change the root POM and Web POM to include the two capability artifacts. Configure main Mapper scanning for:

  ```text
  com.paytech.platform.support.accesscontrol.core.dal.mapper
  com.paytech.platform.support.auditsecurity.core.dal.mapper
  com.paytech.platform.support.core.reliability.mapper
  ```

  Change main MyBatis resources to `classpath*:mapper/**/*Mapper.xml`. Keep the audit write factory's exact audit-only resource from Task 3.

- [x] **Step 7: Compile the full Reactor and fix only ownership-related imports**

  Run:

  ```bash
  ./mvnw -q -DskipTests compile
  ```

  Resolve only missing imports, package declarations, Mapper namespace/result types and actual direct module dependencies. Do not change business branches, SQL predicates, constants, timeouts, transaction settings or API DTOs.

- [x] **Step 8: Run structural and pure behavior tests**

  Run:

  ```bash
  ./mvnw -q -pl platform-support-test -am \
    -Dtest=PlatformSupportDependencyBoundaryAutoTest,PlatformCapabilityModuleBoundaryAutoTest,PlatformArchitectureAutoTest,GatewayEnumOwnershipAutoTest,GatewayConfigurationCompilerAutoTest,OperationsAuditQueryAutoTest,OperationsAuditProjectorAutoTest,OperationsAuditCompletionAutoTest,OperationsPasswordPolicyAutoTest,OperationsSessionPolicyAutoTest,OperationsLocalCryptographyAutoTest,OperationsLockContractAutoTest,OperationsPersistenceContractAutoTest test
  ```

  Expected result: all selected tests pass without MySQL state changes.

---

### Task 5: Restore Coverage, Packaging and Current-State Documentation

**Files:**
- Modify: `platform-support-service/platform-support-test/pom.xml`
- Modify: `platform-support-service/README.md`
- Modify: `agent-docs/CURRENT_IMPLEMENTATION.md` only in the Platform capability-module status paragraph.

**Interfaces:**
- Consumes: compiled classes and source roots of API, Access Control, Audit/Security and Web.
- Produces: Phase A coverage that measures all four production JARs and an executable Boot JAR containing both capabilities.

- [x] **Step 1: Add both capability class/source roots to JaCoCo**

  Add `target/classes` and `src/main/java` filesets for `platform-support-access-control` and `platform-support-audit-security` to the custom Phase A JaCoCo report. Keep the 0.90 instruction and branch thresholds unchanged.

- [x] **Step 2: Verify the executable artifact contains capability classes**

  Run `./mvnw -q -DskipTests package`, then inspect the Web `*-exec.jar` and require both:

  ```text
  BOOT-INF/lib/platform-support-access-control-1.0.0-SNAPSHOT.jar
  BOOT-INF/lib/platform-support-audit-security-1.0.0-SNAPSHOT.jar
  ```

- [x] **Step 3: Update documentation without changing architecture authorities**

  Update the service README module tree, package locations, single-DataSource statement and test commands. Update only the current implementation ledger to state that the first-stage two-capability migration exists in the working tree and list actual verification evidence; do not claim File/Document, Delivery, 63 tables, deployment or production rollout.

- [x] **Step 4: Run proportionate verification**

  Run:

  ```bash
  ./mvnw -q clean verify
  git diff --check
  ```

  Use repository-owned local MySQL/Redis credentials without printing them. If the fixed `mor_platform` database still contains non-catalog data, preserve it and report the exact full-suite safety-gate failure; also run the pure/structural suite from Task 4 and `./mvnw -q -DskipTests package` as non-destructive evidence.

- [x] **Step 5: Review the scope before handoff**

  Confirm `git diff --name-only` contains only `platform-support-service` implementation/test/documentation files plus this plan and the narrow current-state ledger paragraph. Confirm no Gateway, BFF, core-system, Vault, database SQL/PDManer, local-environment or deployment files changed. Do not commit until requested by the user.
