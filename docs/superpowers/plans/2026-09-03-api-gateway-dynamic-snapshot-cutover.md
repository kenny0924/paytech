# API Gateway Dynamic Snapshot Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Gateway's static Operations runtime catalog with an atomically refreshed, fully validated Platform Support PLT-I20 snapshot while preserving the three existing Operations routes' behavior.

**Architecture:** The Gateway consumes the shared `platform-support-api` DTOs through a bounded reactive HTTP client. A compiler converts each full DTO into an immutable runtime catalog only after validating the schema, approved targets and static safety limits; a single-flight refresher atomically swaps the current catalog and drives readiness. Every public request pins one compiled route for its entire lifecycle.

**Tech Stack:** Java 25, Spring Boot 4.0.8, Spring Cloud Gateway Server WebFlux, Spring HTTP Interface/WebClient, Reactor, JUnit 5, JaCoCo.

**Spec:** `docs/superpowers/specs/2026-09-03-api-gateway-platform-support-requirements.md`

## Global Constraints

- Consume `com.paytech:platform-support-api:1.0.0-SNAPSHOT`; do not duplicate Platform production DTOs.
- PLT-I20 is deployment-private HTTP with no mTLS, caller header, browser token or API credential.
- Every success is `200` with a full typed snapshot; no ETag, `If-None-Match` or `304` behavior.
- A valid `routes=[]` replaces the catalog with an empty catalog and keeps readiness Up.
- After cutover there is no static/dynamic merge and no static runtime fallback.
- Connection failure, timeout, `5xx`, invalid body and unknown schema retain Last Valid; before first success readiness stays Down and liveness stays Up.
- Keep Approved Target Registry, Security Profiles, protocol rules and absolute limits compiled into Gateway code.
- Preserve exact Operations login/refresh/logout Header, Cookie, Guard and timeout behavior.
- Do not modify any Platform Support tracked source or its user-owned dirty test.
- Production Java identifiers and constants must not contain the project codename.

---

### Task 1: Shared Contract Dependency and Immutable Runtime Model

**Files:**
- Modify: `api-gateway/pom.xml`
- Modify: `api-gateway/api-gateway-web/pom.xml`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/runtime/GatewayRuntimeSnapshot.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/runtime/GatewayRuntimeRoute.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/runtime/GatewayRuntimeCatalog.java`
- Test: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewayRuntimeCatalogAutoTest.java`

**Interfaces:**
- Consumes: Platform `GatewayPublicSnapshot`, `GatewayTargetSnapshot`, `GatewayRouteSnapshot`, `GatewayGuardRuleSnapshot`.
- Produces: immutable compiled snapshot and exact `HttpMethod + path` lookup used by all filters.

- [ ] Write a failing test proving an empty immutable catalog returns no route and exact GET/POST methods are distinct.
- [ ] Run the focused test and confirm it fails because the runtime catalog does not exist.
- [ ] Add the shared API dependency and minimum immutable runtime records/catalog.
- [ ] Run the focused test and confirm it passes.

### Task 2: Snapshot Validation and Compilation

**Files:**
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/ApprovedTargetRegistry.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotCompiler.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/InvalidGatewaySnapshotException.java`
- Test: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotCompilerAutoTest.java`

**Interfaces:**
- Consumes: one full `GatewayPublicSnapshot` and static approved target/safety policies.
- Produces: `GatewayRuntimeSnapshot compile(GatewayPublicSnapshot source)` or rejects the whole candidate.

- [ ] Write failing tests for the current Operations equivalent snapshot, valid empty routes, unknown schema, unapproved target, ambiguous Method/Path, invalid Header/Query/Cookie/Profile/Guard and exceeded limits.
- [ ] Run the focused tests and confirm each missing behavior fails for the expected reason.
- [ ] Implement schema-1 full validation and compilation without partial results.
- [ ] Run the focused tests and confirm they pass.

### Task 3: Non-blocking PLT-I20 Client and Single-flight Refresh

**Files:**
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/configuration/PlatformSnapshotProperties.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/ReactiveGatewaySnapshotClient.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/PlatformGatewaySnapshotClient.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotStore.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotRefresher.java`
- Modify: `api-gateway/api-gateway-web/src/main/resources/application.yml`
- Test: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotRefreshAutoTest.java`

**Interfaces:**
- Consumes: bounded reactive PLT-I20 `200` response.
- Produces: immediate startup refresh, approximately five-second single-flight refresh, atomic Last Valid storage and refresh status.

- [ ] Write failing tests for first success, concurrent refresh suppression, timeout/connection/5xx/invalid candidate retention and valid empty replacement.
- [ ] Run the focused tests and confirm failures describe the missing client/store/refresher.
- [ ] Implement the bounded WebClient/HTTP Interface adapter, atomic store and single-flight scheduler.
- [ ] Run the focused tests and confirm they pass.

### Task 4: Readiness and Request-level Snapshot Pinning

**Files:**
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayBootstrapHealthIndicator.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExchangeAttributes.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/DynamicRouteWebFilter.java`
- Test: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewaySnapshotHealthAutoTest.java`
- Test: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotPinningAutoTest.java`

**Interfaces:**
- Consumes: current optional Last Valid snapshot.
- Produces: readiness Down until first success and one exchange-bound immutable route used downstream.

- [ ] Write failing tests for cold-start readiness, liveness independence, empty-ready state, local 404 and in-flight route pinning across a refresh.
- [ ] Run focused tests and verify expected failures.
- [ ] Implement health state and the earliest public request route-selection filter.
- [ ] Run focused tests and verify they pass.

### Task 5: Replace Static Operations Runtime Reads

**Files:**
- Modify: relevant classes under `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/{routing,web,guard,security}` that currently inject or query `OperationsRouteCatalog`.
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRouteConfiguration.java`
- Test: existing routing, security, web, guard, observability and acceptance auto tests.

**Interfaces:**
- Consumes: the exchange-pinned compiled route from Task 4.
- Produces: dynamic Header/Cookie/Guard/Body/Timeout/Target behavior with no static fallback.

- [ ] Update an acceptance test fixture to serve a Platform snapshot and prove login/refresh/logout preserve their exact behavior.
- [ ] Run the focused acceptance test and confirm it fails while production still reads the static catalog.
- [ ] Migrate all runtime filters to the exchange-pinned route and remove static catalog beans/fallbacks.
- [ ] Add a GET route test and prove method-exact forwarding and query/header enforcement.
- [ ] Run all Gateway auto tests and confirm they pass.

### Task 6: Verification, Documentation and Commit

**Files:**
- Modify: `api-gateway/README.md`
- Modify: Gateway boundary/coverage scripts only if their assertions must reflect the approved dynamic architecture.

**Interfaces:**
- Produces: reviewed and committed Gateway cutover with reproducible verification evidence.

- [ ] Document PLT-I20 configuration, readiness, Last Valid, empty-catalog and no-static-fallback behavior.
- [ ] Run `./mvnw clean verify` with the repository-managed Redis environment.
- [ ] Run `node scripts/verify-gateway-boundaries.mjs` and `node scripts/verify-jacoco.mjs`.
- [ ] Run `git diff --check`, inspect the complete diff and confirm Platform tracked sources are unchanged.
- [ ] Commit only Gateway changes with a focused message and report both repository HEADs and changed files.
