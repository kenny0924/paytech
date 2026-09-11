# Risk Operations Gateway Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make API Gateway accept and enforce exactly the nine approved Operations Risk routes without exposing Risk as a public target.

**Architecture:** Gateway continues routing only to `operations-console-bff`; two narrow downstream roots are added to that target’s approved registry. Dynamic Snapshot validation remains generic, and route fixtures drive JWT, header/query/body, guard, rewrite, timeout and last-valid behavior.

**Tech Stack:** Java 25, Maven Wrapper 3.9.16, Spring Boot 4.0.8, Spring Cloud Gateway WebFlux, Reactor Netty, Redis-backed guards, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md`

## Global Constraints

- Keep `operations-console-bff` as the only Target and `risk-service` absent from Gateway target configuration.
- Add only `/decision-rule-sets/` and `/decision-releases/` as approved downstream roots; reject broad `/risk/` and every near-match.
- Raise the generic Snapshot body ceiling from 16,384 to 65,536; do not hard-code a Risk route exception in production code.
- Route data comes from Platform Snapshot and is identified there only by the nine exact route codes; persistent Platform route IDs do not cross the public Snapshot boundary.
- Keep five GET limits at zero, Save at 65,536, and Freeze/Create/Validate at 16,384.
- Verify both declared `Content-Length` and chunked streams; buffer at most route limit plus one byte and preserve the existing request-body owner/release lifecycle.
- Preserve filter ordering: authentication and guards before request-body buffering; body limit before upstream timeout and forwarding.
- Return system RFC 9457 code `GATEWAY_PAYLOAD_TOO_LARGE` for 413 and make all rejected requests produce zero BFF calls.
- Preserve last-valid Snapshot atomically when any new route, root, limit or child row is invalid.
- Do not change existing 16 KiB route fixtures or loosen path canonicalization, encoded-separator, dot-segment, cookie, header or query protections.
- The current `api-gateway/README.md` has unrelated uncommitted edits; merge a narrow Risk section without overwriting them.

---

### Task 1: Raise the Generic Snapshot Body Ceiling

**Files:**
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotCompilerAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/builder/GatewaySnapshotFixture.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewayRoutePolicyValidator.java`

**Interfaces:**
- Consumes: `GatewayRouteSnapshot.bodyLimitBytes()` from Platform’s public Snapshot.
- Produces: validation of the exact inclusive interval `0..65_536` for every route.

- [ ] **Step 1: Define the new inclusive boundary in the compiler test**

Add this public test-fixture helper, then use it for one accepted and two rejected cases:

```java
public static GatewayPublicSnapshot operationsSnapshotWithBodyLimit(long bodyLimitBytes) {
    GatewayPublicSnapshot source = operationsSnapshot();
    GatewayRouteSnapshot route = source.routes().getFirst();
    GatewayRouteSnapshot changed = new GatewayRouteSnapshot(route.routeCode(), route.channel(), route.httpMethod(),
        route.externalPathTemplate(), route.downstreamPathTemplate(), route.targetServiceCode(),
        route.securityProfileCode(), route.authorizationMode(), route.permissionCode(), route.requiredScopeCode(),
        route.requestCookieMode(), route.responseCookieMode(), route.corsRequestHeaderNames(),
        route.forwardedRequestHeaderNames(), route.queryParameterNames(), bodyLimitBytes,
        route.bodyReceiveTimeoutMillis(), route.responseTimeoutMillis(), route.guardRules());
    return new GatewayPublicSnapshot(source.schemaVersion(), source.targets(), List.of(changed));
}

assertDoesNotThrow(() -> compiler.compile(GatewaySnapshotFixture.operationsSnapshotWithBodyLimit(65_536L)));
assertThrows(InvalidGatewaySnapshotException.class,
    () -> compiler.compile(GatewaySnapshotFixture.operationsSnapshotWithBodyLimit(65_537L)));
assertThrows(InvalidGatewaySnapshotException.class,
    () -> compiler.compile(GatewaySnapshotFixture.operationsSnapshotWithBodyLimit(-1L)));
```

- [ ] **Step 2: Run RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=GatewaySnapshotCompilerAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because 65,536 is above the existing 16,384 validator ceiling.

- [ ] **Step 3: Change only the production ceiling**

Use:

```java
private static final long MAX_BODY_BYTES = 65_536L;
```

Keep the existing lower-bound and timeout validations unchanged.

- [ ] **Step 4: Run GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=GatewaySnapshotCompilerAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS.

### Task 2: Extend the Approved Operations BFF Downstream Roots

**Files:**
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/ApprovedTargetTransportAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotCompilerAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/builder/GatewaySnapshotFixture.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/ApprovedTargetRegistry.java`

**Interfaces:**
- Consumes: existing target code `operations-console-bff`.
- Produces: approval for exactly the two private BFF path roots used by the nine routes.

- [ ] **Step 1: Add positive and adversarial root tests**

At the Registry layer, require the two valid paths and reject only root-boundary counterexamples:

```text
accept /decision-rule-sets/v1/rule-sets
accept /decision-releases/v1/candidates/100/validate
reject /decision-rule-sets-evil/v1/rule-sets
reject /decision-releases-evil/v1/candidates
reject /risk/decision-rule-sets/v1/rule-sets
reject /actuator/health
```

At the compiler/path-template layer, derive otherwise valid Snapshot fixtures whose downstream template is each of the following and require `InvalidGatewaySnapshotException`:

```text
reject /decision-rule-sets/../actuator/health
reject /decision-rule-sets/%2Factuator/health
reject /decision-releases/%2e%2e/actuator/health
```

Do not make `ApprovedTargetRegistry.permitsDownstreamPath` parse or normalize segments; its responsibility remains exact approved-root membership after the compiler’s canonical path checks.

- [ ] **Step 2: Run RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=ApprovedTargetTransportAutoTest,GatewaySnapshotCompilerAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the two valid Risk-configuration downstream paths are rejected by the current registry.

- [ ] **Step 3: Append the two exact roots to the existing target entry**

The `operations-console-bff` entry must contain its existing roots plus:

```java
"/decision-rule-sets/",
"/decision-releases/"
```

Do not create a second target entry or a root equal to `/risk/`.

- [ ] **Step 4: Run GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=ApprovedTargetTransportAutoTest,GatewaySnapshotCompilerAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: Registry root-boundary tests and compiler canonical-path tests pass at their respective layers.

### Task 3: Build an Exact Nine-Route Snapshot Fixture

**Files:**
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/builder/GatewaySnapshotFixture.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotRefreshAutoTest.java`

**Interfaces:**
- Consumes: route codes and public Snapshot paths, permissions, headers, queries and guards from the control-plane plan; persistent Platform IDs remain outside this boundary.
- Produces: `GatewayPublicSnapshot riskDecisionConfigurationSnapshot()` containing all nine routes as one atomic image.

- [ ] **Step 1: Add the complete fixture-contract RED oracle**

In `GatewaySnapshotRefreshAutoTest`, call the not-yet-existing `GatewaySnapshotFixture.riskDecisionConfigurationSnapshot()` and compare all nine rows—not only counts—to this exact ordered oracle:

| Route code | Method | External path | Downstream path | Permission | Body bytes | Query keys |
|---|---|---|---|---|---:|---|
| `operations-risk-rule-authoring-context` | GET | `/api/operations/decision-rule-sets/v1/authoring-context/{pointCode}` | `/decision-rule-sets/v1/authoring-context/{pointCode}` | `RISK_DECISION_CONFIG_READ` | 0 | none |
| `operations-risk-rule-set-detail` | GET | `/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}` | `/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}` | `RISK_DECISION_CONFIG_READ` | 0 | none |
| `operations-risk-rule-set-freeze` | POST | `/api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze` | `/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze` | `RISK_RULE_SET_FREEZE` | 16,384 | none |
| `operations-risk-rule-set-list` | GET | `/api/operations/decision-rule-sets/v1/rule-sets` | `/decision-rule-sets/v1/rule-sets` | `RISK_DECISION_CONFIG_READ` | 0 | `page,pointCode,ruleSetCode,size,status` |
| `operations-risk-rule-set-save` | POST | `/api/operations/decision-rule-sets/v1/rule-sets` | `/decision-rule-sets/v1/rule-sets` | `RISK_RULE_SET_SAVE` | 65,536 | none |
| `operations-risk-test-candidate-create` | POST | `/api/operations/decision-releases/v1/candidates` | `/decision-releases/v1/candidates` | `RISK_TEST_CANDIDATE_CREATE` | 16,384 | none |
| `operations-risk-test-candidate-detail` | GET | `/api/operations/decision-releases/v1/candidates/{releaseId}` | `/decision-releases/v1/candidates/{releaseId}` | `RISK_DECISION_CONFIG_READ` | 0 | none |
| `operations-risk-test-candidate-list` | GET | `/api/operations/decision-releases/v1/candidates` | `/decision-releases/v1/candidates` | `RISK_DECISION_CONFIG_READ` | 0 | `page,pointCode,size,status` |
| `operations-risk-test-candidate-validate` | POST | `/api/operations/decision-releases/v1/candidates/{releaseId}/validate` | `/decision-releases/v1/candidates/{releaseId}/validate` | `RISK_TEST_CANDIDATE_VALIDATE` | 16,384 | none |

For every row additionally require `channel=OPERATIONS`, target `operations-console-bff`, security profile `OPERATIONS_JWT`, `authorizationMode=HUMAN_PERMISSION`, null required scope, request/response Cookie modes `DROP/NONE`, and body receive/response timeouts `5000/10000`. Inclusion in this public fixture is the active-runtime fact; `GatewayRouteSnapshot` has no persisted status field. In Platform's binary wire order, GET CORS headers are exactly `[authorization]` and forwarded headers `[origin]`; POST CORS headers are `[authorization,content-type,idempotency-key]` and forwarded headers `[content-type,idempotency-key,origin]`. Every route has exactly these six guards in Platform's `(ruleType.code,dimension.code)` wire order and with these values: `CONCURRENCY/AUTHENTICATED_PRINCIPAL/null/null/null/2`, `CONCURRENCY/CLIENT_IP/null/null/null/3`, `CONCURRENCY/SHARED_ROUTE/null/null/null/50`, `RATE_LIMIT/AUTHENTICATED_PRINCIPAL/10/10/60000/null`, `RATE_LIMIT/CLIENT_IP/10/30/60000/null`, `RATE_LIMIT/SHARED_ROUTE/300/300/60000/null`. Require unique route codes, unique query names, unique header identities and unique `(ruleType,dimension)` guards; only the intentional POST CORS/forward header-name intersection is allowed.

- [ ] **Step 2: Run fixture RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=GatewaySnapshotRefreshAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `riskDecisionConfigurationSnapshot()` does not exist.

- [ ] **Step 3: Add a named fixture method with exact route identities**

Define:

```java
public static GatewayPublicSnapshot riskDecisionConfigurationSnapshot()
```

The method returns the existing active `operations-console-bff` Target and these ordered route codes:

```java
List.of(
    "operations-risk-rule-authoring-context",
    "operations-risk-rule-set-detail",
    "operations-risk-rule-set-freeze",
    "operations-risk-rule-set-list",
    "operations-risk-rule-set-save",
    "operations-risk-test-candidate-create",
    "operations-risk-test-candidate-detail",
    "operations-risk-test-candidate-list",
    "operations-risk-test-candidate-validate"
)
```

This is Platform's canonical binary `routeCode` wire order, deliberately different from numeric-ID and rollout activation order. Use the exact methods/paths/permission codes from the Step 1 oracle, the two list query allowlists, two headers for GET, six headers for POST and six guards per route. Use Snapshot timeouts `5000/10000` and cookie modes `DROP/NONE`.

- [ ] **Step 4: Run the fixture/compiler GREEN tests**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=GatewaySnapshotCompilerAutoTest,GatewaySnapshotRefreshAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS with the valid fixture.

### Task 4: Prove the Public Gateway Boundary End to End

**Files:**
- Create: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/acceptance/RiskDecisionConfigurationGatewayAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/OperationsBffSimulator.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/BffRequestJournal.java`

**Interfaces:**
- Consumes: `riskDecisionConfigurationSnapshot`, the existing loopback BFF probe, JWT test material, Redis guard simulator and RFC 9457 checker.
- Produces: executable proof that the nine browser routes are exact and rejected traffic never reaches BFF.

- [ ] **Step 1: Add only the raw-query fixture consumer**

Create the minimal `RiskDecisionConfigurationGatewayAutoTest` harness, publish `riskDecisionConfigurationSnapshot()` through the existing `PlatformSnapshotSimulator`, refresh once and freeze the same JWT time boundary used by the existing acceptance suite:

```java
@TestBean(name = "operationsJwtClock", methodName = "fixedClock", enforceOverride = true)
private Clock operationsJwtClock;

private static Clock fixedClock() {
    return Clock.fixed(JwtFixtureBuilder.NOW, ZoneOffset.UTC);
}
```

Add only `forwardsAllowedListQueriesByteForByte`. Rule Set List sends `page=2&size=20&pointCode=CHECKOUT_PREPARE&ruleSetCode=SMOKE_RULE&status=DRAFT`; Candidate List sends `page=3&size=10&pointCode=CHECKOUT_PREPARE&status=VALIDATING`. Each invocation creates `JwtFixtureBuilder.template()`, registers its own `expectJwks(jwt.jwksJson())`, sends `"Bearer " + jwt.build()`, and requires exactly one journal entry whose not-yet-existing `rawQuery()` equals the byte-identical input. Do not edit either simulator class yet.

- [ ] **Step 2: Run the raw-query fixture RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=RiskDecisionConfigurationGatewayAutoTest#forwardsAllowedListQueriesByteForByte \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compilation fails because `BffRequestJournal.Entry.rawQuery()` does not exist. No production Gateway file changes in this RED step.

- [ ] **Step 3: Implement only raw-query journal capture**

Extend the sole `BffRequestJournal.record(...)` signature and `Entry` record with `String rawQuery`. In `OperationsBffSimulator.respond`, keep `path = "/" + request.path()` for scenario matching, derive the raw query only from `request.uri()` by taking the exact substring after the first `?` (null when no `?` is present), and record it separately. Do not decode, sort or rebuild it and do not include it in `BffScenarioStore.consume(method, path)`. Strings and the surrounding immutable Entry snapshot remain defensive; existing assertions may ignore the new accessor.

- [ ] **Step 4: Run the raw-query fixture GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=RiskDecisionConfigurationGatewayAutoTest#forwardsAllowedListQueriesByteForByte \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: both list-route invocations pass and existing simulator consumers still compile.

- [ ] **Step 5: Add all nine rewrite, trusted Header and CORS cases**

Create a nine-row `Stream<Arguments>` containing external path, method, expected downstream path, route code and permission. Every independent authenticated test or parameterized invocation creates `JwtFixtureBuilder jwt = JwtFixtureBuilder.template()`, registers `jwksSimulator().scenarios().expectJwks(jwt.jwksJson())` before its first authenticated request, and sends `"Bearer " + jwt.build()`; no case may rely on a prior test's JWKS scenario/cache. For every row, send a valid Operations JWT and Origin, then assert exactly one `OperationsBffSimulator` journal entry with the rewritten path and Gateway-generated actor/session/route/request-ID headers. Assert browser-supplied trusted headers and cookies are absent upstream.

For each GET, prove `authorization` is accepted for CORS/authentication but absent from the BFF journal, while `origin` is forwarded. For each POST, prove `content-type` and `idempotency-key` are both accepted and forwarded byte-for-byte while `authorization` is consumed. Add nine local OPTIONS cases with the exact requested method/header set; each returns 204 with exact CORS response headers and zero BFF calls. An extra requested header is forbidden.

- [ ] **Step 6: Run the rewrite/Header/CORS characterization GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=RiskDecisionConfigurationGatewayAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the query-journal and all nine rewrite/Header/CORS cases pass using the Task 3 fixture.

- [ ] **Step 7: Add exact query and path rejection cases**

Verify an extra `debug` query returns RFC 9457 400. Use unconfigured `PUT` as the wrong method for every route (GET and POST intentionally share two paths), and require RFC 9457 `404 GATEWAY_ROUTE_NOT_FOUND`; trailing slash, case change, encoded slash, dot segment and `decision-rule-sets-evil` must produce the same Gateway-owned result. These Route/Operations-boundary rejections occur before the authentication filter: send a valid-looking Bearer value but register no JWKS scenario, then require both zero JWKS requests and zero BFF calls so teardown has no unconsumed expectation. Only a request that reaches typed authentication registers `expectJwks`.

- [ ] **Step 8: Run the query/path rejection GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=RiskDecisionConfigurationGatewayAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: exact allowlisted queries forward and every extra query/path/method variant is Gateway-owned with zero JWKS/BFF calls where specified.

- [ ] **Step 9: Add Content-Length and chunked size boundaries**

Create JWT material with the repository's executable pattern:

```java
JwtFixtureBuilder jwt = JwtFixtureBuilder.template();
jwksSimulator().scenarios().expectJwks(jwt.jwksJson());
```

Build Content-Length requests from `GatewayPublicTestClient.operations(port).defaultHeader("X-Real-IP", "203.0.113.17").build()`, then call `post().uri(path).header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.build()).contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", key).bodyValue(new String(bytes, UTF_8))`. Generate every boundary fixture from ASCII-only, semantically valid JSON bytes (therefore also valid UTF-8), and assert `new String(bytes, UTF_8).getBytes(UTF_8)` has exactly the original byte sequence and length before sending. Before each accepted call, register exactly one matching `OperationsBffSimulator` response and capture its journal count.

For chunked requests, copy the proven `OperationsCorsAndBodyLimitAutoTest.sendChunked` ownership pattern: start from `GatewayPublicTestClient.operationsHttpClient(port)`, set the same trusted IP/Origin/`Bearer " + jwt.build()`/Content-Type/key, remove Content-Length, set `Transfer-Encoding: chunked`, then call `post().uri(path).send(Flux.fromIterable(chunks).map(Unpooled::wrappedBuffer)).responseSingle(...)` with a three-second bound. Use the same round-trip-verified ASCII/UTF-8 byte fixtures, not Java character counts; split accepted and rejected bodies across at least two buffers.

Save must forward Content-Length and chunked totals 65,536 and reject 65,537. Freeze, Candidate Create and Validate must forward 16,384 and reject 16,385. For accepted bodies, assert one journal increment, exact downstream body digest/validated Content-Length, terminal body owner and pooled-buffer release. Every rejection asserts the standard RFC 9457 413/code `GATEWAY_PAYLOAD_TOO_LARGE`, a valid Request ID, zero BFF journal increment, zero BFF transport start and released body-owner probes; do not add route-limit values to the public Problem detail. For each GET, prepare a fresh valid fixed-clock JWT/JWKS scenario, exact Origin and a one-byte ASCII body, then require that same 413/code/request-ID/zero-call/zero-transport/released-owner result so authentication or CORS cannot satisfy the assertion accidentally.

- [ ] **Step 10: Run both body-size suites GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=RiskDecisionConfigurationGatewayAutoTest,OperationsCorsAndBodyLimitAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all declared-length, chunked and GET-zero-byte boundaries pass without regressing the existing 16 KiB fixtures.

- [ ] **Step 11: Prove authentication, route authorization metadata and six guards are active**

For one read and each write route, assert the compiled Snapshot retains `authorizationMode=HUMAN_PERMISSION` and the exact permission code, and assert the trusted Route Code forwarded to the BFF. This Gateway simulator test cannot prove the downstream PLT-I02 call; the BFF browser-boundary suite proves its order and the live smoke proves the combined chain. Gateway itself must not call PLT-I02 or deny an otherwise valid JWT based on current role membership. Prove missing/invalid JWT fails before BFF, then exhaust Shared Route, Client IP and Authenticated Principal rate/concurrency keys independently and assert the expected guard result and zero excess upstream calls. Do not add a TENANT guard.

- [ ] **Step 12: Run final acceptance GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=RiskDecisionConfigurationGatewayAutoTest,OperationsCorsAndBodyLimitAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all nine-route, header/query, limit and guard cases pass; existing route limit tests remain unchanged.

### Task 5: Preserve Last Valid and Close the Gateway Track

**Files:**
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotRefreshAutoTest.java`
- Modify: `api-gateway/scripts/verify-gateway-boundaries.mjs`
- Modify: `api-gateway/README.md`

**Interfaces:**
- Consumes: a valid Risk Snapshot and the existing refresh/pinning mechanism.
- Produces: proof that an invalid subsequent whole Snapshot cannot partially expose or widen any new route.

- [ ] **Step 1: Add five exact invalid-subsequent-Snapshot cases**

Inside `GatewaySnapshotRefreshAutoTest`, add a pure test helper that replaces exactly one route in a copied `riskDecisionConfigurationSnapshot()` while preserving list position and every untouched field. Create five named tests whose sole mutations are: Save `bodyLimitBytes=65_537`; Authoring downstream path `/risk/decision-rule-sets/v1/authoring-context/{pointCode}`; remove only Validate's `CONCURRENCY/AUTHENTICATED_PRINCIPAL` guard; append a second `page` query entry to Rule Set List; and set Candidate Create `authorizationMode=NONE` while retaining its non-null permission. No production mutation API is added.

For each named test, first publish the valid whole Snapshot, refresh successfully and retain the exact `GatewayRuntimeSnapshot` object. Publish the one-field-invalid whole Snapshot, refresh once, then assert the refresh fails, `GatewaySnapshotStore.current()` is the same retained object, `lastRefreshResult=FAILURE`, `lastFailureReason=INVALID_SNAPSHOT`, `usingLastValidSnapshot=true`, and all nine retained routes still resolve. Assert no partial new route/root/guard/query/mode is observable.

- [ ] **Step 2: Run the Last Valid focused GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=GatewaySnapshotRefreshAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the valid nine-route Snapshot activates and every one-field-invalid successor retains the same exact runtime Snapshot object.

- [ ] **Step 3: Extend the static boundary script**

Require `ApprovedTargetRegistry` to contain both narrow roots and forbid a `risk-service` target or `"/risk/"` root. Require `GatewayRoutePolicyValidator` to contain one generic `65_536` ceiling and forbid path comparisons in that validator.

- [ ] **Step 4: Run the static boundary gate**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
node scripts/verify-gateway-boundaries.mjs
```

Expected: the two narrow roots and one generic ceiling pass, and no Risk Target or path-specific validator branch is present.

- [ ] **Step 5: Run focused Gateway verification**

Run:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw -pl api-gateway-test -am \
  -Dtest=GatewaySnapshotCompilerAutoTest,ApprovedTargetTransportAutoTest,GatewaySnapshotRefreshAutoTest,RiskDecisionConfigurationGatewayAutoTest,OperationsCorsAndBodyLimitAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: focused tests pass.

- [ ] **Step 6: Run the full Maven gate**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw clean verify
```

If this exits nonzero, report every failure by exact class/method and do not call the suite green; baseline attribution belongs only to the umbrella plan's pre-edit evidence and is made only when that evidence actually exists.

- [ ] **Step 7: Run the coverage gate**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
node scripts/verify-jacoco.mjs
```

- [ ] **Step 8: Check the Gateway diff**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
git diff --check
```

Expected: no whitespace error.

- [ ] **Step 9: Update README without overwriting its existing dirty change**

Append a narrow section describing the two roots, nine dynamic routes, exact size enforcement, last-valid behavior and test evidence. State explicitly that Gateway never calls Risk and that route rows are not deployed merely because tests pass.

- [ ] **Step 10: Inspect the Gateway worktree and dirty README before staging**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
git status --short
git diff --name-only
git diff -- README.md
```

Expected: all non-README paths belong to the Step 11 allowlist. Identify the pre-existing README hunk separately from the new Risk section; if they cannot be separated safely, stop instead of staging either hunk.

- [ ] **Step 11: Stage the exact clean-path Gateway allowlist only when commit authorization is active**

Run exactly; do not add README yet:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
git add -- \
  api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotCompilerAutoTest.java \
  api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/ApprovedTargetTransportAutoTest.java \
  api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/GatewaySnapshotRefreshAutoTest.java \
  api-gateway-test/src/test/java/com/paytech/gateway/auto/acceptance/RiskDecisionConfigurationGatewayAutoTest.java \
  api-gateway-test/src/test/java/com/paytech/gateway/test/builder/GatewaySnapshotFixture.java \
  api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/OperationsBffSimulator.java \
  api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/BffRequestJournal.java \
  api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/GatewayRoutePolicyValidator.java \
  api-gateway-web/src/main/java/com/paytech/gateway/routing/snapshot/ApprovedTargetRegistry.java \
  scripts/verify-gateway-boundaries.mjs
```

- [ ] **Step 12: Stage only the new README hunk**

Run interactively and reject every pre-existing unrelated hunk:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
git add -p -- README.md
```

Use `s` to split a combined patch when possible. If the unrelated and Risk edits cannot be isolated, stop this track before validation or commit and leave README unstaged; do not stage the whole file.

- [ ] **Step 13: Validate the exact staged Gateway diff**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
git diff --cached --check
git diff --cached --name-only
git diff --cached
```

Expected: `--name-only` contains exactly the ten clean paths in Step 11 plus `README.md` (11 paths total). The full staged diff contains no pre-existing README hunk and no file outside that final allowlist. Unstage and stop on any mismatch; a ten-path result with README omitted is not commit-ready.

- [ ] **Step 14: Commit the approved staged Gateway diff**

Only while the active execution still has explicit commit authorization, run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
git commit -m "feat(api-gateway): enforce Operations risk rule routes"
```
