# Operations Gateway Hardening Implementation Plan

> **2026-09-01 dynamic-control-plane successor:** The [unified public API control-plane design](../specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) now owns public Target/Route/Guard current configuration. This plan remains implementation history for the deployed static Operations Gateway before dynamic Snapshot cutover and continues to own unaffected security-hardening evidence; the static Catalog is a pre-cutover runtime fact only.

> **Guard 部分已于 2026-08-31 被替代：** 不再执行本文中的 Redis Identity HMAC、`guard:v2`、Policy Revision、状态迁移或旧 Redis Hash Schema 任务。当前批准契约使用原始 Canonical IP / 已验证 JWT 的准确 `sub` 作为 Redis Key 身份、`guard:v3`、无 Policy Revision 或滚动迁移、Rate 字段 `t/lr/rr` 与 Concurrency Metadata `hw/l`；Access Log HMAC 保留。本文其余任务正文仅作为既有实施历史。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the public Operations Gateway while retaining only Login, Refresh and Logout, making the route model method-aware, enforcing exact protocol boundaries, and preserving a simpler but correct Redis Rate/Concurrency Guard.

**Architecture:** One immutable compile-time route catalog drives routing, security, CORS, request-body, cookie, forwarding, timeout and Guard behavior. The request is admitted in two phases: anonymous ingress Route/IP Guard before JWT/body work, then an optional authenticated Subject Guard. Redis Lua owns fleet-wide atomicity and monotonic policy state; WebFlux filters own bounded body replay and exact terminal HTTP contracts.

**Tech Stack:** Java 25, Spring Boot 4.0.8, Spring Cloud Gateway Server WebFlux 2025.1.3, Reactor Netty, Spring Security Resource Server, Lettuce/Reactive Redis, Lua, JUnit 5, real HTTPS simulators and repository Compose Redis 8.2.

**Spec:** `docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md`

## Global Constraints

- Work in the existing `api-gateway` and root worktrees. Preserve unrelated dirty changes and stage only files named by each task.
- Do not add Platform Support, Operations BFF, frontend or business-service production code.
- Do not add Mockito, Testcontainers, Docker startup, YAML-created public routes, retries, blocking Redis calls or a local request-body cache beyond one exchange.
- Gateway automatic tests use the already-running Redis at `127.0.0.1:16379` with a unique namespace and protocol simulators.
- Production names, constants, configuration and positive fixtures must not contain the legacy project acronym. A narrow negative forgery test may construct an old wire name inline.
- Header comparisons are case-insensitive exact-name comparisons. Prefix and wildcard matching are forbidden.
- Run every stated RED command and confirm the expected failure before editing production code. Then implement only enough for GREEN.
- Keep commits scoped and use `git add -- <exact paths>`; never stage unrelated root or nested-repository changes.

---

## Task 1: Make the route catalog the single method-aware contract

**Files:**

- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRouteDefinition.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRouteCatalog.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRouteConfiguration.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRoutingProperties.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsAccessRequirement.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsQueryPolicy.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/RefreshCookieRequirement.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardProfile.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/RateLimitRule.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/ConcurrencyRule.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/OperationsSecurityConfiguration.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/GatewayFallbackSecurityConfiguration.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/OperationsRoutingAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/architecture/GatewayArchitectureAutoTest.java`

- [ ] Add failing tests proving catalog lookup is exact on `HttpMethod + rawPath`, each current route is POST, all current queries are rejected locally, trailing/case/encoded-slash variants never call the BFF, and the runtime exposes exactly three catalog routes.
- [ ] Add startup tests proving BFF URI accepts HTTPS origins and numeric-loopback HTTP only, and rejects path, userinfo, query, fragment, non-loopback HTTP and hostname aliases such as `localhost`.
- [ ] Run RED:

  ```bash
  cd api-gateway
  ./mvnw -pl api-gateway-test -am \
    -Dtest=OperationsRoutingAutoTest,GatewayArchitectureAutoTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
  ```

  Expected: failures because definitions do not own their methods/query/access policies, routing uses `stripPrefix(2)`, and BFF URI validation is too permissive.

- [ ] Expand the immutable definition so every field below is required and validated:

  ```java
  public record OperationsRouteDefinition(
      OperationsRouteCode code,
      HttpMethod method,
      String externalPath,
      String bffPath,
      OperationsQueryPolicy queryPolicy,
      OperationsAccessRequirement accessRequirement,
      RefreshCookieRequirement refreshCookieRequirement,
      Set<String> corsRequestHeaders,
      Set<String> forwardedRequestHeaders,
      int bodyLimitBytes,
      Duration bodyReceiveTimeout,
      GuardProfile ingressGuard,
      GuardProfile authenticatedGuard,
      Duration responseTimeout) { }
  ```

- [ ] Register routes with `predicate.method(route.method())`, exact raw-path/query predicates and explicit `setPath(route.bffPath())`; remove `stripPrefix` and all hard-coded POST assumptions outside the catalog.
- [ ] Generate protected/anonymous security matchers from catalog access requirements and keep fallback Security deny-by-default.
- [ ] Run GREEN with the same focused command and verify the BFF simulator records the exact `/auth/v1/*` path.
- [ ] Commit nested-repository files only:

  ```bash
  git -C api-gateway add -- api-gateway-web/src/main/java/com/paytech/gateway/routing api-gateway-web/src/main/java/com/paytech/gateway/security/OperationsSecurityConfiguration.java api-gateway-web/src/main/java/com/paytech/gateway/security/GatewayFallbackSecurityConfiguration.java api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/OperationsRoutingAutoTest.java api-gateway-test/src/test/java/com/paytech/gateway/auto/architecture/GatewayArchitectureAutoTest.java
  git -C api-gateway commit -m "feat: make Operations routes explicit contracts"
  ```

## Task 2: Replace legacy protocol names and enforce exact ingress/header forwarding

**Files:**

- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayProtocol.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayErrorWriter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/TrustedRequestContextWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/OperationsBoundaryWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/UpstreamRequestGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/AuthenticatedOperationsIdentityGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/TrustedTerminalResponseHeaders.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/TrustedRequestBoundaryAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/OperationsJwtValidationAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsCorsAndBodyLimitAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/acceptance/OperationsGatewayAcceptanceAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/OperationsRoutingAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/OperationsJwksAvailabilityAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/architecture/GatewayArchitectureAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayAccessLogAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayMetricsAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/GatewayErrorContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/probe/GatewayBoundaryTestConfiguration.java`

- [ ] Add failing tests for the new six wire names, case-insensitive exact removal of the enumerated reserved names, survival of near matches such as `X-Gateway-Actor-Subject-Extra` at ingress, and final BFF absence of every non-allowlisted header.
- [ ] Add route-specific CORS and forwarding tests: Login has no Authorization/CSRF/Cookie; Refresh forwards CSRF plus one refresh Cookie; Logout forwards CSRF/Cookie plus trusted identity headers; raw Bearer is never forwarded.
- [ ] Run RED:

  ```bash
  cd api-gateway
  ./mvnw -pl api-gateway-test -am \
    -Dtest=TrustedRequestBoundaryAutoTest,OperationsJwtValidationAutoTest,OperationsCorsAndBodyLimitAutoTest,OperationsGatewayAcceptanceAutoTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
  ```

- [ ] Centralize exact wire names in `GatewayProtocol`, including `X-Request-Id`, the four `X-Gateway-*` identity headers and `__Secure-operations-refresh`. Implement reserved names as a finite lower-cased set; do not call `startsWith`, `regionMatches` with a prefix or wildcard helpers.
- [ ] Make preflight method/header checks derive from the matched route. Rebuild upstream headers from an empty `HttpHeaders`, copy only the route allowlist, and inject only Gateway-generated context.
- [ ] Run GREEN and then run a production/positive-fixture semantic scan:

  ```bash
  rg -n --glob '*.java' --glob '*.yml' --glob '*.yaml' \
    'X-M[oO][Rr]-|__Secure-[mM][oO][rR]|MOR_REDIS_PASSWORD|class .*Mor|static final .*MOR' \
    api-gateway/api-gateway-web/src/main api-gateway/api-gateway-test/src/test || true
  ```

  Expected: no production or reusable positive-protocol hit; only an explicitly documented inline negative-forgery literal may remain.
- [ ] Commit the exact touched files with message `fix: enforce exact Gateway protocol headers`.

## Task 3: Own refresh-cookie and terminal response contracts

**Files:**

- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/OperationsRefreshCookiePolicy.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/OperationsResponseContractGlobalFilter.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/InvalidUpstreamResponseException.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/RefreshCookieResponsePolicy.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRouteDefinition.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRouteCatalog.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayGlobalFilterOrder.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/TrustedTerminalResponseHeaders.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayErrorWriter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayErrorCode.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExceptionWebHandler.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayOutcome.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/TrustedRequestBoundaryAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/GatewayErrorContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/acceptance/OperationsGatewayAcceptanceAutoTest.java`
- Create: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsResponseContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/OperationsRouteDefinitionContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/BffScenarioStore.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/OperationsBffSimulator.java`

- [ ] Add failing response-matrix tests for Login/Refresh/Logout Cookie Max-Age, zero/positive values, duplicate/foreign Cookie, Domain, wrong Path, missing Secure/HttpOnly/SameSite, invalid opaque values and empty values. Every invalid upstream contract must return a local 502 before response commitment.
- [ ] Add failing tests that every auth success/error owns `Cache-Control: no-store`, `Pragma: no-cache`, a single deduplicated `Vary: Origin`, exact CORS response names and only `X-Request-Id, Retry-After` exposure.
- [ ] Run RED with `TrustedRequestBoundaryAutoTest,GatewayErrorContractAutoTest,OperationsGatewayAcceptanceAutoTest`.
- [ ] Parse BFF `Set-Cookie` as an untrusted response contract, accept at most one target cookie, validate route-specific Max-Age, then rebuild exactly:

  ```text
  __Secure-operations-refresh=<opaque>; Path=/api/operations/auth/v1; Max-Age=<validated>; Secure; HttpOnly; SameSite=Strict
  ```

  Never copy `Domain` or unknown attributes. Clear the invalid BFF response/body and write `502 GATEWAY_UPSTREAM_INVALID_RESPONSE`.
- [ ] Replace prefix-based CORS response cleanup with an exact finite owned-header set and normalize `Vary` without duplicates.
- [ ] Run GREEN and commit with message `fix: validate Operations terminal responses`.

## Task 4: Simplify static Guard profiles and single-current HMAC

**Files:**

- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardProfile.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/RateLimitRule.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/ConcurrencyRule.java`
- Delete: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardRule.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardDimension.java`
- Delete: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardTier.java`
- Delete: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardPolicyResolver.java`
- Delete or replace: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/BootstrapGuardPolicy.java`
- Delete or replace: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/ResolvedGuardPolicy.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsGuardGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RateLimitCommand.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RateLimitCommandFactory.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RedisReactiveRateLimiter.java`
- Modify: `api-gateway/api-gateway-web/src/main/resources/com/paytech/gateway/guard/ratelimit/token-bucket.lua`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyCommand.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyKeys.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyCommandFactory.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/GatewayHmacProperties.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/VersionedHmacDigester.java`
- Modify: `api-gateway/api-gateway-web/src/main/resources/application.yml`
- Replace: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/GuardPolicyResolutionAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/VersionedHmacDigesterAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/configuration/GatewayBootstrapPropertiesAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/architecture/GatewayArchitectureAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisConcurrencyLeaseAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisTokenBucketAutoTest.java`

- [ ] Replace precedence/resolver tests with failing catalog-profile tests for the exact approved Login, Refresh and Logout rate/concurrency values and an explicit no-subject profile for Login/Refresh.
- [ ] Add failing tests that Redis HMAC accepts exactly one current version/material, access-log material remains separate, and no `previous` property or dual digest is available.
- [ ] Run RED with `GuardPolicyResolutionAutoTest,VersionedHmacDigesterAutoTest,GatewayBootstrapPropertiesAutoTest`.
- [ ] Model Guard profiles directly on route definitions. Give each rule a positive monotonic `policyRevision`; remove tier, null-route ceiling, resolver and dynamic-policy scaffolding.
- [ ] Make `redisIpDigest` and `redisSubjectDigest` return one `VersionedDigest`, and delete rotation-pair command generation.
- [ ] Run GREEN and scan production sources for `GuardTier`, `GuardPolicyResolver`, `redisPrevious`, `.previous()` and rotation-pair branches; expect no hits.
- [ ] Commit with message `refactor: simplify Operations Guard policies`.

## Task 5: Implement monotonic Redis token-bucket v2

**Files:**

- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RateLimitCommand.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RateLimitCommandFactory.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RedisReactiveRateLimiter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/redis/RedisGuardKeyFactory.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/redis/GuardRedisProperties.java`
- Modify: `api-gateway/api-gateway-web/src/main/resources/com/paytech/gateway/guard/ratelimit/token-bucket.lua`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisTokenBucketAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisRateLimitFailureAutoTest.java`

- [ ] Add failing real-Redis tests for: one `TIME` per decision; multi-bucket all-or-nothing deductions; backward Redis time; higher-revision capacity/period reduction; lower-revision caller; equal-revision metadata mismatch; rejected requests persisting refill/migration without deduction; finite TTL; and `guard:v2` keys only.
- [ ] Run RED:

  ```bash
  cd api-gateway
  ./mvnw -pl api-gateway-test -am \
    -Dtest=RedisTokenBucketAutoTest,RedisRateLimitFailureAutoTest \
    -Dsurefire.failIfNoSpecifiedTests=false test
  ```

- [ ] Extend command arguments with positive policy revision and active rate metadata. Validate all keys/arguments before invoking Redis.
- [ ] Implement Lua in four mutation phases: validate all inputs; load/refill/migrate with `effectiveNow=max(redisNow,last)`; decide all buckets; persist all states and deduct only when every bucket allows. Same revision with different metadata returns the explicit script-contract failure consumed as fail-closed 503.
- [ ] Compute `Retry-After` from the maximum recovery and TTL from effective stored policy using integer microtokens/remainder only.
- [ ] Run GREEN, inspect representative Redis hashes and ensure tests remove only their namespace with SCAN plus exact UNLINK/DEL.
- [ ] Commit with message `fix: make Redis token buckets monotonic`.

## Task 6: Implement concurrency metadata, policy reduction and accurate Retry-After

**Files:**

- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyCommand.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyCommandFactory.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyLease.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/RedisReactiveConcurrencyLimiter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/redis/RedisGuardKeyFactory.java`
- Modify: `api-gateway/api-gateway-web/src/main/resources/com/paytech/gateway/guard/concurrency/concurrency-acquire.lua`
- Modify: `api-gateway/api-gateway-web/src/main/resources/com/paytech/gateway/guard/concurrency/concurrency-release.lua`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisConcurrencyLeaseAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisGuardUnavailableAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`

- [ ] Add failing real-Redis tests for companion metadata Hashes, non-empty ZSET without metadata, clock rollback/high-water behavior, higher/lower/equal revision handling, all-dimension atomic acquire, idempotent release and finite crash-recovery TTL.
- [ ] Add the key policy-reduction test: with `count > reducedLimit`, `Retry-After` is based on the `(count - limit + 1)`-th expiry, capped by lease duration—not the first expiry. Add a test proving rejected acquire does not refresh either key TTL.
- [ ] Run RED with `RedisConcurrencyLeaseAutoTest,RedisGuardUnavailableAutoTest`.
- [ ] Make acquire receive paired ZSET/metadata keys and one Lease ID. Read Redis `TIME` once, persist monotonic high-water before cleanup/scoring, resolve revision, then write to every dimension only if all allow.
- [ ] Keep release idempotent and remove the Lease ID from every phase ZSET without extending TTL. Treat partial/malformed metadata and script return-shape mismatch as dependency unavailable.
- [ ] Run GREEN and commit with message `fix: make Redis concurrency leases monotonic`.

## Task 7: Enforce two-phase Guard admission and non-waiting bulkheads

**Files:**

- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsIngressGuardWebFilter.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsGuardAdmission.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsGuardTimingInvariant.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsBffTimeoutGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsGuardGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RateLimitCommandFactory.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyCommandFactory.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayGlobalFilterOrder.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExchangeAttributes.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayMetrics.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayMetricsAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/configuration/GatewayBootstrapPropertiesAutoTest.java`

- [ ] Add failing end-to-end tests proving ingress Route/IP Guard runs before JWT, JWKS, body and BFF; Logout Subject Guard runs only after successful authentication; Login/Refresh never create subject keys.
- [ ] Add failing tests proving a passed rate decision followed by a full concurrency bucket returns immediate `429 GATEWAY_CONCURRENCY_LIMITED` with integer `Retry-After`, never queues, does not refund the rate token and calls neither body nor BFF.
- [ ] Cover success, error, cancellation, body timeout, subject rejection and BFF timeout: every acquired ingress/subject Lease has one idempotent release attempt; release failure records a bounded metric and never rewrites a committed response.
- [ ] Run RED with `OperationsGuardFlowAutoTest,GatewayMetricsAutoTest`.
- [ ] Split the filter into ingress admission and post-authenticated admission. Store acquired leases as request-scoped state and use `Mono.usingWhen`/`doFinally` semantics with atomic release guards; never call `delay`, `retryWhen`, `block` or a semaphore wait.
- [ ] Add startup invariant calculation for the declared worst post-acquire path and require 30-second lease duration to be strictly greater. Keep key/metadata TTL at 60 seconds.
- [ ] Run GREEN and commit with message `fix: guard Operations requests in two phases`.

## Task 8: Bound and replay request bodies with a total deadline

**Files:**

- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/ReplayableRequestBodyOwner.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/RequestBodyLimitGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExchangeAttributes.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/UpstreamRequestGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayErrorCode.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayOutcome.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsCorsAndBodyLimitAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/probe/GatewayBoundaryTestConfiguration.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/OperationsBffSimulator.java`
- Create: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/BffTransportProbe.java`
- Create: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/ReplayableRequestBodyOwnerAutoTest.java`

- [ ] Add failing tests for missing/false/chunked Content-Length, exact 16 KiB acceptance, 16 KiB + 1 rejection, an aggregate five-second receive deadline that does not reset per chunk, cancellation, no BFF bytes before validation, and release of ingress/subject leases.
- [ ] Add a package-visible lifecycle probe or narrow buffer owner abstraction to prove the temporary byte array is best-effort zeroed after complete/error/cancel without exposing or logging the body.
- [ ] Run RED with `OperationsCorsAndBodyLimitAutoTest,OperationsGuardFlowAutoTest`.
- [ ] Rename `forwardCachedBody` to `forwardWithReplayableBody`. Join at `limit + 1`, copy then release the pooled buffer, decorate a replayable body, set exact Content-Length, remove Transfer-Encoding and zero the array in terminal cleanup.
- [ ] Carry the validated actual body length through a trusted exchange attribute so upstream Header reconstruction preserves only that exact Content-Length and never trusts the caller's Content-Length or Transfer-Encoding.
- [ ] Apply one `timeout(route.bodyReceiveTimeout())` to the entire aggregation publisher. Map it to `408 GATEWAY_REQUEST_TIMEOUT`; retain `413 GATEWAY_PAYLOAD_TOO_LARGE` for actual bytes over limit.
- [ ] Ensure filter ordering is after ingress Guard and authenticated Subject Guard but before upstream reconstruction/BFF routing.
- [ ] Run GREEN and commit with message `fix: bound Operations request body lifetime`.

## Task 9: Bound unknown-kid JWKS refreshes

**Files:**

- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/ReactiveJwksProvider.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/OperationsSecurityProperties.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayMetrics.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/OperationsJwksAvailabilityAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/OperationsJwtValidationAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/architecture/GatewayArchitectureAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayMetricsAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayAccessLogAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsResponseContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/jwks/JwksSimulator.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/jwks/JwksScenarioStore.java`

- [ ] Add failing tests that non-RS256 and `kid` outside `^[A-Za-z0-9._:-]{1,128}$` return 401 with zero JWKS calls.
- [ ] Add sequential and concurrent unknown-kid tests proving one shared in-flight refresh and one global five-second cooldown: successful refresh + still missing => 401; failed refresh and cooldown misses => 503; cached known keys keep working.
- [ ] Add cancellation and exact-boundary tests proving one request cannot cancel or clear the shared refresh, the cooldown suppresses until but not at `retryAt`, and success/failure metrics count actual refreshes rather than joiners.
- [ ] Add failing URI/HTTP tests for HTTPS + host + path only, no redirects, two-second connect, three-second response, 64 KiB body, at most 16 valid RSA signing keys and RSA-2048 minimum.
- [ ] Run RED with `OperationsJwksAvailabilityAutoTest,OperationsJwtValidationAutoTest`.
- [ ] Replace per-kid structures with one immutable cached key map, one atomically shared in-flight `Mono`, and one cooldown result. Do not retain requested kids or tag metrics by kid.
- [ ] Run GREEN, confirm refresh success/failure/suppressed metrics are low-cardinality, and commit with message `fix: bound Gateway JWKS refresh work`.

## Task 10: Enforce the public transport envelope and stable upstream failures

**Files:**

- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/transport/GatewayTransportProperties.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/transport/GatewayNettyServerConfiguration.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/transport/GatewayNettyClientConfiguration.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/transport/GatewayHeaderDeadlineHandlers.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayRequestHeaderLimitsWebFilter.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayUpstreamFailureClassifier.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsBffTimeoutGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExceptionWebHandler.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayUpstreamProtocolException.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayErrorCode.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayGlobalFilterOrder.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayOutcome.java`
- Modify: `api-gateway/api-gateway-web/src/main/resources/application.yml`
- Modify: `api-gateway/api-gateway-test/src/test/resources/application-auto.yml`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/configuration/GatewayBootstrapPropertiesAutoTest.java`
- Create: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/transport/GatewayTransportAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/GatewayErrorContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayAccessLogAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayMetricsAutoTest.java`

- [ ] Add failing startup/unit-integration tests for the exact TLS protocols/suites, 8 KiB request line, 32 KiB aggregate headers, five-second header/read-idle deadlines, 1,000 connections, backlog 512 and 1,000 keep-alive requests. Test loopback HTTP through the same decoder/admission configuration.
- [ ] Add failing request tests for Authorization/Cookie aggregate 8 KiB and Origin/each CORS control header 2 KiB. Oversized parsed headers return `431 GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE` locally without Redis/JWT/BFF; pre-decoder rejection may close the transport without a JSON body.
- [ ] Add slow raw-header, keep-alive and pipelining tests proving one non-resetting five-second total Header deadline starts at the first byte, Header read-idle is bounded, body bytes do not start a new Header deadline, and the next request is independently timed.
- [ ] Add failure-classification tests: BFF TLS/protocol/invalid response => 502; connect/pool acquisition/DNS => 503; complete response budget => 504. Cause walking must use only an explicit finite type set and never expose exception messages.
- [ ] Run RED with `GatewayTransportAutoTest,GatewayBootstrapPropertiesAutoTest,GatewayErrorContractAutoTest`.
- [ ] Configure Reactor Netty server limits before `ServerWebExchange` creation and validate production TLS configuration at startup. Keep certificate/key values as deployment-secret inputs and forbid public cleartext in production.
- [ ] Configure a fixed BFF pool with two-second acquisition, two-second connect and the whole BFF chain at ten seconds including pool/connect; explicitly disable redirects and Reactor Netty retry.
- [ ] Run GREEN and commit with message `fix: bound Gateway transport resources`.

## Task 11: Rename the Gateway Redis secret and synchronize documentation

**Files:**

- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/redis/RepositoryRedisEnvironment.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestRuntime.java`
- Modify: `api-gateway/README.md`
- Modify: `local-environment/.env.example`
- Modify: `local-environment/compose.yaml`
- Modify: `scripts/verify-local-environment.mjs`
- Modify: `agent-docs/plans/2026-08-29-operations-api-gateway-design.md`
- Modify: `agent-docs/plans/2026-08-29-platform-support-operations-authentication-requirements.md`
- Modify: `agent-docs/TECHNOLOGY_STACK.md`
- Modify: `agent-docs/JAVA_PROJECT_STRUCTURE.md`
- Modify: `agent-docs/UNIT_TEST_GUIDE.md`
- Modify: `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`

- [ ] Add/adjust semantic verification first so it fails when the Gateway/local Redis entry uses `MOR_REDIS_PASSWORD` or old Gateway Header/Cookie/HMAC-rotation contracts.
- [ ] Run RED:

  ```bash
  node scripts/verify-local-environment.mjs
  rg -n 'MOR_REDIS_PASSWORD|X-M[oO][Rr]-|__Secure-[mM][oO][rR]-operations-refresh' api-gateway agent-docs local-environment scripts
  ```

- [ ] Rename only the Redis service variable to `PAYTECH_REDIS_PASSWORD` in `.env.example`, Compose Redis environment/command/healthcheck, Gateway tests and active docs. Do not rename unrelated established local-environment variables or the Compose project name.
- [ ] Do not edit untracked `local-environment/.env.local`. Document the one-time copy of the existing secret value under the new variable name and explain that an already-running Redis container need not be recreated.
- [ ] Synchronize route catalog, same-site/`credentials: include`, exact Header/Cookie, Guard v2, no-wait concurrency, body replay/deadline, JWKS, transport and error contracts across the listed authority docs. Remove old dual-HMAC and prefix-filter descriptions.
- [ ] Run GREEN for local verification and a focused semantic scan. Commit `api-gateway` README/test-runtime changes in the nested repository; commit root docs/local-environment verification with exact-path staging only after confirming unrelated dirty files are excluded.

## Task 12: Full verification, independent review and integration

**Files:**

- Modify only if a verification failure identifies a defect in files already owned by Tasks 1-11.

- [ ] Export the existing Redis secret under the new process variable without printing it, confirm `127.0.0.1:16379` is reachable, and do not start/recreate Docker.
- [ ] Run the complete nested-repository gate:

  ```bash
  cd api-gateway
  ./mvnw clean verify
  node scripts/verify-gateway-boundaries.mjs
  node scripts/verify-jacoco.mjs
  ```

- [ ] Run the relevant root gates:

  ```bash
  cd ..
  node scripts/verify-local-environment.mjs
  npm --prefix requirements-site test
  git diff --check
  git -C api-gateway diff --check
  ```

- [ ] Run final semantic scans for forbidden production identifiers, prefix/wildcard Header matching, `stripPrefix`, Guard tiers/resolvers, dual-HMAC code, Testcontainers/Mockito and blocking calls.
- [ ] Request two independent reviews: one spec-compliance/security review and one implementation-quality/test review. Fix every confirmed P0/P1 and rerun the affected focused tests plus the full gate.
- [ ] Inspect `git status --short` in root and nested repository, verify unrelated Compensation/Foundation work remains untouched, and stage only the Gateway hardening files.
- [ ] If the requested integration commit is not already present, commit the exact remaining root and nested changes on `main`, record both hashes, and verify final `main` status contains only pre-existing unrelated work.
