# Operations API Gateway First Slice Implementation Plan

> **2026-09-01 dynamic-control-plane successor:** The [unified public API control-plane design](../specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) now owns public Target/Route/Guard current configuration. This plan remains implementation history for the deployed static Operations Gateway before dynamic Snapshot cutover; its static Catalog constraints remain pre-cutover runtime evidence, not the target control-plane ownership model.

> Superseded in part on 2026-08-30: do not execute this plan's routing, trusted Header/Cookie, request-body, JWKS or
> Guard tasks. Those areas are governed by
> `docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md` and its forthcoming implementation plan.
> This file remains an implementation-history record for the original first slice.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first deployable `api-gateway` slice that exposes exactly three Operations authentication routes, validates Operations access JWTs, enforces non-blocking Redis rate/concurrency protection, and forwards only to `operations-console-bff`.

**Architecture:** Create a two-module Maven reactor: `api-gateway-web` is the only Spring Cloud Gateway Server WebFlux deployable and `api-gateway-test` owns full-chain automatic tests plus stateful BFF/JWKS simulators. Routes remain a code-owned exact catalog; security, browser boundaries, Guard policy, reactive Redis scripts, and observability are focused packages under `com.paytech.gateway`. Platform Support, BFF, frontend, database, migrations, and dynamic route publication stay out of scope.

**Tech Stack:** Java 25, Maven Wrapper 3.9.16, Spring Boot 4.0.8, Spring Cloud 2025.1.3 / Gateway Server WebFlux 5.0.x, Spring Security reactive resource server, Spring Data Redis Reactive + Lettuce, Reactor, Lua, Micrometer, JUnit 5.

**Spec:** `agent-docs/plans/2026-08-29-operations-api-gateway-design.md`

## Global Constraints

- The production base package is `com.paytech.gateway`; configuration prefixes start at `gateway.*` and never use a `mor`/company prefix.
- The Maven parent is `com.paytech:paytech-parent:1.0.0-SNAPSHOT`; child modules do not repeat managed dependency versions.
- The only modules are `api-gateway-web` and `api-gateway-test`; do not create `*-api`, Controller, Repository, Mapper, database, or empty DDD layers.
- Production dependencies must not include `paytech-redis-starter`, `paytech-caffeine-starter`, `paytech-mvc-web-starter`, Redisson, Spring MVC, Testcontainers, Mockito, Nacos, Platform Support clients, or core-service clients.
- Use `org.springframework.cloud:spring-cloud-starter-gateway-server-webflux` and `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server`. Use `org.springframework.boot:spring-boot-starter-data-redis-reactive`; no blocking Redis template, `.block*()`, or fire-and-forget `.subscribe()` call is allowed in production code.
- Expose exactly three production routes: `operations-auth-login`, `operations-auth-refresh`, and `operations-auth-logout`. Explicitly disable discovery locator, Redis route repository, Forwarded/X-Forwarded generation, RequestRateLimiter, and Retry filter factories.
- `OPTIONS` is answered locally, consumes no Guard capacity, and never reaches the BFF. A valid `POST` is rate-counted before concurrency acquisition; a concurrency rejection does not refund the rate token.
- `Burst` is the Token Bucket total capacity. Rate rejection must not deduct another bucket; concurrency acquisition is all-or-nothing across dimensions.
- HMAC secrets are Base64-decoded values of at least 32 bytes. Redis and access-log digests use separate keys and purposes. During rotation, current and previous Redis digest versions are enforced simultaneously for at least the largest 120-second Guard TTL after the whole fleet changes.
- The concurrency release margin is 5 seconds. Lease duration is response timeout plus that margin; the Redis key TTL is twice the lease duration.
- JWKS bounds are HTTPS only, response body at most 64 KiB, at most 16 RSA keys, cache TTL 5 minutes, unknown-`kid` refresh cooldown 5 seconds, and at most 256 remembered unknown `kid` values. Connect timeout is 2 seconds and JWKS response timeout is 3 seconds.
- The BFF connect timeout is 2 seconds. Production route response timeout defaults to 10 seconds and may only be configured lower for tests or emergency hardening.
- The `auto` profile uses lower bounded JWKS/BFF timeouts to keep failure tests fast while retaining `lease duration = route timeout + 5s` and `key TTL = 2 * lease duration`; production-default binding is tested separately.
- Missing or non-exact Origin is `403`. Missing, malformed, or duplicate `__Secure-mor-operations-refresh` on Refresh/Logout is `401`. Login removes every Cookie; Refresh/Logout forward exactly one allowed Cookie.
- Remove every external `X-MoR-*`, `X-Request-Id`, `Forwarded`, and `X-Forwarded-*` header. Generate a UUID request ID and expose it as `X-MoR-Request-Id`; never forward the original Bearer token.
- IP Allow/Deny has no approved bootstrap value and is not implemented. Client IP rate keys use the socket remote address and ignore caller-supplied forwarding headers.
- Management port is `19080`; application port is `18080`. Production ingress TLS, management network ACL, and BFF Gateway-only network ACL remain deployment tasks.
- Tests use the already-running repository Redis at `127.0.0.1:16379`, require exported `MOR_REDIS_PASSWORD`, use a unique `gateway:test:v1:<UUID>` namespace, and clean only with paginated `SCAN` plus exact `UNLINK/DEL`. Never call Docker, `KEYS`, `FLUSHDB`, or port `6379` from tests.
- Do not modify Platform Support, Console, Checkout, Foundation, Build, or `scripts/verify-java-foundation.mjs` paths. Do not commit or create branches unless the user separately asks; each task ends with tests plus `git diff --check`.

## Stable Cross-Task Interfaces

```java
public record RateLimitCommand(String key, long capacityMicrotokens,
        long refillMicrotokens, long refillPeriodMillis, long requestedMicrotokens,
        long ttlMillis) {
}

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
}

public interface ReactiveRateLimiter {
    Mono<RateLimitDecision> evaluate(List<RateLimitCommand> commands);
}

public record ConcurrencyCommand(String key, int limit, long leaseDurationMillis,
        long keyTtlMillis) {
}

public record ConcurrencyLease(String leaseId, List<String> keys) {
}

public record ConcurrencyDecision(boolean allowed, long retryAfterSeconds,
        Optional<ConcurrencyLease> lease) {
}

public interface ReactiveConcurrencyLimiter {
    Mono<ConcurrencyDecision> acquire(List<ConcurrencyCommand> commands);
    Mono<Void> release(ConcurrencyLease lease);
}
```

Construct decisions through validated `allowed(...)`/`rejected(...)` factories. Allowed rate decisions have `retryAfterSeconds == 0`; rejected rate decisions have `retryAfterSeconds >= 1`. Allowed concurrency decisions have `retryAfterSeconds == 0` and one Lease; rejected decisions have `retryAfterSeconds >= 1` and `Optional.empty()`. Lease keys are non-empty, unique, insertion-ordered, and defensively immutable. Command factories reject duplicate keys and derive TTLs from policy/timeout rules rather than accepting arbitrary external TTL configuration.

---

### Task 1: Create the WebFlux Maven Reactor and Dependency Boundary

**Files:**
- Create: `api-gateway/.mvn/wrapper/maven-wrapper.properties`
- Create: `api-gateway/mvnw`
- Create: `api-gateway/mvnw.cmd`
- Create: `api-gateway/pom.xml`
- Create: `api-gateway/api-gateway-web/pom.xml`
- Create: `api-gateway/api-gateway-test/pom.xml`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/ApiGatewayApplication.java`
- Create: `api-gateway/api-gateway-web/src/main/resources/application.yml`
- Create: `api-gateway/api-gateway-test/src/test/resources/application-auto.yml`
- Create: `api-gateway/api-gateway-test/src/test/resources/junit-platform.properties`
- Create: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestRuntime.java`
- Create: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestBase.java`
- Test: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/configuration/GatewayBootstrapPropertiesAutoTest.java`

**Interfaces:**
- Produces: executable `ApiGatewayApplication`, Java 25 Maven reactor, reactive Redis connection factory, random-port test application.
- Consumes: `com.paytech:paytech-parent:1.0.0-SNAPSHOT`.

- [ ] **Step 1: Create the reactor POMs and Wrapper metadata**

Reuse the verified Wrapper scripts from `paytech-foundation` byte-for-byte and create:

```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
```

The root POM inherits `paytech-parent`, has `packaging=pom`, version `1.0.0-SNAPSHOT`, manages `com.paytech:api-gateway-web:${project.version}`, and lists only the two approved modules.

`api-gateway-web` declares `paytech-common-core`, `spring-cloud-starter-gateway-server-webflux`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-data-redis-reactive`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, and optional `spring-boot-configuration-processor`. Bind Spring Boot `repackage` with classifier `exec`, preserving the ordinary JAR for test-module dependency.

Configure `maven-compiler-plugin.annotationProcessorPaths` for `spring-boot-configuration-processor` at inherited `${spring-boot.version}`, matching the existing Foundation pattern so JDK 25 reliably generates metadata.

`api-gateway-test` depends on `api-gateway-web` with compile scope so its later JaCoCo aggregate report includes production classes; the module still contains test source only and is never deployed. It declares the Boot 4 modular test starters `spring-boot-starter-webflux-test`, `spring-boot-starter-security-oauth2-resource-server-test`, and `spring-boot-starter-data-redis-reactive-test`, plus `reactor-test` and `awaitility`; do not redundantly declare `spring-boot-starter-test` or `spring-security-test`. Exclude `mockito-core` and `mockito-junit-jupiter` from every Boot test-starter path that brings them transitively; the final dependency gate must prove neither remains. Nimbus JOSE is already supplied by the Resource Server/Jose production chain, and fixed PKCS#8 test PEM files use JDK/Netty APIs, so add no direct unversioned Nimbus or Bouncy Castle dependency. Set install/deploy skip for the test module. Do not declare Testcontainers or WireMock.

- [ ] **Step 2: Write the failing bootstrap test**

```java
class GatewayBootstrapPropertiesAutoTest extends GatewayAutoTestBase {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void test_reactiveApplicationStartsWithoutServletStack() {
        Assertions.assertEquals(WebApplicationType.REACTIVE,
                WebApplicationType.deduce());
        Assertions.assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.springframework.web.servlet.DispatcherServlet"));
        Assertions.assertNotNull(applicationContext.getBean(ReactiveRedisConnectionFactory.class));
    }
}
```

`GatewayAutoTestBase` owns `@ActiveProfiles("auto")`, random application/management ports, and one `@DynamicPropertySource` delegated to `GatewayAutoTestRuntime`. `application-auto.yml` disables every external configuration/discovery integration and contains only explicit non-production test defaults. `junit-platform.properties` keeps full HTTP classes sequential. Later tasks extend the runtime with BFF/JWKS lifecycle and the shared-Redis precondition; individual tests never rely on class execution order.

- [ ] **Step 3: Run the test and verify the application is missing**

```bash
./mvnw -pl api-gateway-test -am -Dtest=GatewayBootstrapPropertiesAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation/context failure naming the missing application/configuration.

- [ ] **Step 4: Add the minimal reactive application and hardened framework defaults**

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

`application.yml` sets app/management ports, application name, `spring.data.redis.*`, and:

```yaml
spring:
  data:
    redis:
      repositories.enabled: false
  cloud:
    gateway:
      server:
        webflux:
          discovery.locator.enabled: false
          redis-route-definition-repository.enabled: false
          global-filter.local-response-cache.enabled: false
          filter.request-rate-limiter.enabled: false
          filter.retry.enabled: false
          forwarded.enabled: false
          x-forwarded.enabled: false
          metrics.path-tags.enabled: false
          metrics.tags.path.enabled: false
          httpclient.connect-timeout: 2000
```

- [ ] **Step 5: Run the focused test and reactor package**

Run the focused command, then `./mvnw -DskipTests package`. Expected: both pass, the web module has ordinary and `exec` JARs, and test module has no deployable Boot JAR.

- [ ] **Step 6: Review the task boundary**

Run `git -C api-gateway status --short` and `git -C api-gateway diff --check`; confirm two modules and no forbidden dependency.

### Task 2: Add Trusted Request IDs and the Stable Gateway Error Contract

**Files:**
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExchangeAttributes.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayOutcome.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayErrorCode.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayErrorResponse.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayErrorWriter.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/web/TrustedRequestContextWebFilter.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExceptionWebHandler.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/web/GatewayErrorContractAutoTest.java`

**Interfaces:**
- Produces: exchange attributes `REQUEST_ID`, `ROUTE_CODE`, and `OUTCOME`; header `X-MoR-Request-Id`; finite `GatewayOutcome`; and `Mono<Void> GatewayErrorWriter.write(ServerWebExchange, HttpStatus, GatewayErrorCode, Long)`.
- Consumes: WebFlux codecs and `CodeMessageEnum`.

- [ ] **Step 1: Write failing Request ID and error-shape tests**

Send attacker-controlled `X-Request-Id` and `X-MoR-Request-Id`; assert neither survives and a Gateway 404 returns:

```json
{"code":"GATEWAY_ROUTE_NOT_FOUND","message":"请求的网关路由不存在","requestId":"generated","timestamp":"UTC instant"}
```

Assert the header/body request IDs match and canary Authorization/Cookie values never appear in body/log output.

- [ ] **Step 2: Run the focused test and verify failure**

Run the focused class; expect missing writer/filter.

- [ ] **Step 3: Implement the error dictionary and writer**

`GatewayErrorCode implements CodeMessageEnum` with exact values:

```text
GATEWAY_UNAUTHORIZED             未通过身份验证
GATEWAY_FORBIDDEN                请求不符合渠道安全策略
GATEWAY_ROUTE_NOT_FOUND          请求的网关路由不存在
GATEWAY_PAYLOAD_TOO_LARGE        请求内容超过允许大小
GATEWAY_RATE_LIMITED             请求过于频繁
GATEWAY_CONCURRENCY_LIMITED      当前并发请求过多
GATEWAY_DEPENDENCY_UNAVAILABLE   网关依赖暂时不可用
GATEWAY_UPSTREAM_TIMEOUT         上游服务响应超时
```

`GatewayErrorResponse` is a record of code/message/requestId/Instant. The writer sets JSON UTF-8, status, request ID, optional integer `Retry-After`, and never logs request material.

Freeze finite outcomes now: `UPSTREAM_RESPONSE`, `AUTHENTICATION_FAILED`, `AUTHORIZATION_DENIED`, `ROUTE_NOT_FOUND`, `PAYLOAD_TOO_LARGE`, `RATE_LIMITED`, `CONCURRENCY_LIMITED`, `DEPENDENCY_UNAVAILABLE`, `UPSTREAM_TIMEOUT`, and `CLIENT_CANCELLED`. Every local terminal branch added by later tasks sets `OUTCOME` before writing; matched routing sets `ROUTE_CODE`, while unknown paths use the finite `UNMATCHED` route label. A client cancellation has no invented HTTP response code and is logged with status family `NONE`.

- [ ] **Step 4: Implement trusted request context and exception mapping**

Run `TrustedRequestContextWebFilter` at `Ordered.HIGHEST_PRECEDENCE + 10`; remove caller `X-MoR-*`, `X-Request-Id`, `Forwarded`, and `X-Forwarded-*`, generate UUID, store it in the exchange, and set it on the response. Preserve Authorization/Origin/Cookie/Content-Type/X-CSRF-Token for later filters.

Map only uncommitted Gateway transport exceptions: connect/DNS refusal to 503 and response timeout to 504. Never wrap an HTTP response already returned by BFF.

- [ ] **Step 5: Re-run and review**

Expected: contract passes; run `git -C api-gateway diff --check`.

### Task 3: Register Exactly Three Static BFF Routes

**Files:**
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRouteCode.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRouteDefinition.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRouteCatalog.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRoutingProperties.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRouteConfiguration.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/OperationsBffSimulator.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/BffScenarioStore.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/BffRequestJournal.java`
- Modify: `api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestRuntime.java`
- Modify: `api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestBase.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/OperationsRoutingAutoTest.java`

**Interfaces:**
- Produces: immutable `OperationsRouteCatalog.all()`/`find(HttpMethod,String)` and three Routes targeting one configured BFF URI.
- Consumes: `OperationsRoutingProperties(bffUri, consoleOrigin, responseTimeout)`; timeout must be positive and at most 10 seconds.

- [ ] **Step 1: Write failing exact-route tests with a stateful BFF simulator**

For each route, prepare a scenario, POST over real HTTP, and assert one recorded request at `/auth/v1/login`, `/auth/v1/refresh`, or `/auth/v1/logout`. Parameterize GET/PUT/PATCH/DELETE/HEAD, trailing slash, case change, encoded separator, extra segment, `/api/operations/v1/anything`, and unknown path; every negative is JSON 404 with zero calls.

- [ ] **Step 2: Run and verify missing routes**

Run the focused class; expect no route catalog/locator.

- [ ] **Step 3: Implement immutable definitions**

```java
public record OperationsRouteDefinition(OperationsRouteCode code, String externalPath,
        String bffPath, boolean accessTokenRequired, int bodyLimitBytes,
        Duration responseTimeout) {
}
```

Catalog snapshot:

```text
operations-auth-login   POST /api/operations/auth/v1/login   /auth/v1/login   anonymous  16384  10s
operations-auth-refresh POST /api/operations/auth/v1/refresh /auth/v1/refresh anonymous  16384  10s
operations-auth-logout  POST /api/operations/auth/v1/logout  /auth/v1/logout  protected  16384  10s
```

`OperationsRouteCode` implements `CodeEnum`; reject duplicate code/path and expose an unmodifiable list.

- [ ] **Step 4: Build programmatic routes**

Use exact POST and Path predicates, `StripPrefix=2`, and route metadata for body/response timeout. Store the timeout with `RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR` as `responseTimeout.toMillis()`; do not put a `Duration` object in metadata. Do not add Retry, fallback, discovery, wildcard, or Platform policy clients. Target only `gateway.operations.bff-uri`.

- [ ] **Step 5: Make the simulator strict and stateful**

Run Reactor Netty on a loopback random port. Unknown method/path, missing scenario, and surplus call fail explicitly. Journal method/path/headers/body digest/count. Support pass-through status/body/content-type, disconnect, delay, and a request barrier. Reset store/journal before every test.

Start the BFF simulator once from `GatewayAutoTestRuntime` before Spring property binding, inject its URI through the existing dynamic-property method, and reset its scenario store/journal from the base class around every test. Do not create per-test Spring contexts merely to change BFF behavior.

- [ ] **Step 6: Re-run routing/no-retry tests**

Add BFF 422 pass-through and BFF 500/disconnect; assert unchanged business response where present and exactly one call. Catalog size remains three.

### Task 4: Enforce Origin, CORS, Cookie, Header, and Body Boundaries

**Files:**
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/web/OperationsBoundaryWebFilter.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayGlobalFilterOrder.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/web/RequestBodyLimitGlobalFilter.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/web/UpstreamRequestGlobalFilter.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsCorsAndBodyLimitAutoTest.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/security/TrustedRequestBoundaryAutoTest.java`

**Interfaces:**
- Produces: local preflight response, exact Origin enforcement, cached bounded request body, sanitized upstream headers/Cookies.
- Consumes: route catalog and trusted request ID; Task 5 later supplies authenticated JWT principal.

- [ ] **Step 1: Write the failing browser-boundary matrix**

Cover exact HTTPS Origin success; missing Origin; `null`; HTTP; suffix/prefix lookalikes; wrong port; comma-separated Origins; OPTIONS with non-POST requested method; and requested headers beyond Authorization/Content-Type/X-CSRF-Token. Valid OPTIONS is 204 with exact Origin, credentials true, fixed allowed/exposed values, no Guard, and no BFF.

Cover Login with multiple cookies (BFF sees none), Refresh/Logout with target plus unrelated cookies (only target survives), missing/duplicate target (401), caller trusted/Forwarded/XFF spoofing, and unchanged response `Set-Cookie`. Cover Content-Length and chunked bodies at 16,384 allowed and 16,385 rejected with 413/zero BFF calls.

- [ ] **Step 2: Run both classes and verify failures**

Use both test names with `surefire.failIfNoSpecifiedTests=false`.

- [ ] **Step 3: Implement local CORS/origin behavior**

For exact route paths, POST requires exact configured Origin. OPTIONS requires exact Origin, requested method POST, and case-insensitive allowed-header subset; return local 204. Any other method/path under `/api/operations` gets JSON 404 before route lookup.

- [ ] **Step 4: Implement the byte ceiling**

Reject oversized Content-Length immediately. For chunked/unknown length, aggregate at most `limit+1`, release buffers on overflow/error/cancel, and decorate the allowed request with cached bytes. Never decode/log body. Run before Guard so 413 consumes no capacity.

Freeze project order constants now: body limit first, the Task 8 Guard second, upstream sanitization third, then built-in Netty routing. Add an order test rather than relying on bean discovery order.

- [ ] **Step 5: Implement upstream sanitization**

Remove Authorization/Cookies. Login remains cookie-free. Refresh/Logout require exactly one target Cookie and rebuild one opaque Cookie header without decoding value. Preserve the already validated Origin plus Content-Type/X-CSRF-Token and framework-required transport headers; do not forward caller-supplied Host, forwarding headers, `baggage`, `traceparent`, or `tracestate`. Runtime instrumentation may inject its current validated transport trace context after sanitization, but caller trace headers are never treated as identity or copied manually. Task 5 adds identity headers.

- [ ] **Step 6: Re-run and inspect leak output**

Repeat chunked over-limit calls; assert stable 413, zero BFF calls, and no captured Netty leak warning.

### Task 5: Validate Operations JWTs through a Bounded Reactive JWKS Provider

**Files:**
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/security/OperationsSecurityProperties.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/security/ReactiveJwksProvider.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/security/JwksUnavailableException.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/security/OperationsJwtValidator.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/security/OperationsChannelAuthorizationManager.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/security/OperationsSecurityConfiguration.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/jwks/JwksSimulator.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/jwks/JwksScenarioStore.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/jwks/JwksRequestJournal.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/builder/JwtFixtureBuilder.java`
- Modify: `api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestRuntime.java`
- Modify: `api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestBase.java`
- Create: `api-gateway-test/src/test/resources/tls/jwks-server-cert.pem`
- Create: `api-gateway-test/src/test/resources/tls/jwks-server-key.pem`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/security/OperationsJwtValidationAutoTest.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/security/OperationsJwksAvailabilityAutoTest.java`

**Interfaces:**
- Produces: `ReactiveJwtDecoder`, public/protected `SecurityWebFilterChain`s, and `Flux<JWK> ReactiveJwksProvider.select(SignedJWT)`.
- Consumes: trusted HTTPS JWKS URI/CA, issuer/audience, error writer, Logout metadata.

- [ ] **Step 1: Write the failing JWT matrix**

Use RSA-2048 fixtures. Test valid claims and one negative per missing/empty/wrong iss/sub/aud/token_use/channel/actor_type/sid/jti/iat/nbf/exp. Test none, RS512, missing/unknown kid, wrong key, iat>nbf, nbf>=exp, lifetime 900 accepted/901 rejected, future iat 30 accepted/31 rejected, 30-second nbf/exp skew boundaries, and expiration.

Channel/actor failures are 403; cryptographic/issuer/audience/token-use/identity/time failures are 401. Query/form/Cookie access tokens are ignored. Login/Refresh are anonymous; Logout requires Bearer plus refresh Cookie.

- [ ] **Step 2: Write failing JWKS availability/cache tests**

Assert initial fetch, known cached key during JWKS 503 before TTL expiry, expired cache plus unavailable JWKS gives 503, unknown kid one controlled refresh then no repeat within the 5-second cooldown, refresh after cooldown, rotated key success, successful refresh still missing key gives 401, required key with unreachable/503 JWKS gives 503, invalid/oversized/>16-key JWKS gives 503, and concurrent unknown-key requests coalesce. Inject a Clock/scheduler boundary so expiry and cooldown tests do not sleep.

- [ ] **Step 3: Implement the bounded provider**

Use TLS WebClient with fixed bounds. Parse `JWKSet`; accept at most 16 RSA public keys usable for RS256. Cache immutable set 5 minutes and serve known cached kid offline. Coalesce refresh with one cached in-flight Mono. Keep a synchronized access-order map capped at 256 to refresh a repeated unknown kid at most every 5 seconds. Fetch/parse/TLS/limit errors become `JwksUnavailableException`; successful refresh without kid returns empty selection.

Reject an empty set, blank/duplicate `kid`, private RSA material, a non-signing `use`, or an explicit non-RS256 `alg`. `GatewayAutoTestRuntime` starts the HTTPS simulator once, injects its URI and pinned test CA, and the shared base resets JWKS scenarios/journal around each sequential HTTP test.

```java
NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
        .withJwkSource(reactiveJwksProvider::select)
        .jwsAlgorithm(SignatureAlgorithm.RS256)
        .build();
decoder.setJwtValidator(operationsJwtValidator);
```

- [ ] **Step 4: Separate authentication from channel authorization**

Validator composes issuer, audience, token use, required `sub`/`sid`/`jti`, `JwtTimestampValidator(Duration.ofSeconds(30))`, explicit time ordering, <=900-second lifetime, and <=30-second future iat. It deliberately leaves `channel` and `actor_type`, including their absence, to the authorization manager, which requires OPERATIONS/PLATFORM_USER and therefore yields 403.

Use three explicitly ordered `SecurityWebFilterChain`s with exact `securityMatcher`s: first Login/Refresh plus their OPTIONS preflights without Resource Server processing, second Logout plus its OPTIONS preflight with the reactive Resource Server and custom authorization manager, and last a permit-all fallback so unmatched requests reach JSON 404. Every chain disables framework CSRF, form login, HTTP Basic, and Spring Security logout; Gateway enforces Origin while Refresh/Logout CSRF business semantics remain BFF-owned. Entry point maps a cause containing `JwksUnavailableException` to 503, otherwise 401.

- [ ] **Step 5: Add trusted identity headers**

For authenticated Logout, upstream receives exactly Request ID, Actor Subject, Session ID, Actor Type PLATFORM_USER, and Channel OPERATIONS. Login/Refresh receive only Request ID. Authorization never reaches BFF.

- [ ] **Step 6: Run both JWT classes**

Expected: complete matrix passes against HTTPS simulator with no test-only route.

### Task 6: Resolve Guard Policy and Build Purpose-Separated HMAC Keys

**Files:**
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardDimension.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardTier.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardRule.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardContext.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/ResolvedGuardPolicy.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/BootstrapGuardPolicy.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/policy/GuardPolicyResolver.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/security/GatewayHmacProperties.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/security/VersionedHmacDigester.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/GuardPolicyResolutionAutoTest.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/security/VersionedHmacDigesterAutoTest.java`

**Interfaces:**
- Produces: `ResolvedGuardPolicy GuardPolicyResolver.resolve(GuardContext)`, current/previous Redis digests, and an independent access-log digest.
- Consumes: stable Route Code, socket remote IP, optional authenticated subject, code-owned bootstrap rules, and Secret-injected HMAC material.

- [ ] **Step 1: Write the failing policy matrix**

Assert that one request accumulates each applicable `SHARED_ROUTE`, `CLIENT_IP`, and `AUTHENTICATED_SUBJECT` dimension, but within one dimension selects only the highest tier in `EMERGENCY > CREDENTIAL > CLIENT > ROUTE > GLOBAL`. Assert deterministic output order and fail construction on duplicate rule identity, non-positive capacity/refill/period/TTL/limit, or an invalid dimension/tier combination.

Freeze the bootstrap values exactly:

```text
Login:   route 300/min burst 300; IP 5/min burst 5; concurrency route 20/IP 2
Refresh: route 600/min burst 600; IP 30/min burst 10; concurrency route 50/IP 3
Logout:  route 300/min burst 300; subject 10/min burst 10; concurrency route 50/subject 2
Ceiling: route 200/s burst 400; subject 20/s burst 40; concurrency route 100/subject 10
```

The ceiling applies only when a later task adds Operations business routes. It neither constrains these three approved auth routes nor creates a fourth Route.

- [ ] **Step 2: Write the failing digest and rotation tests**

Use fixed test secrets and vectors to assert HMAC-SHA-256 determinism, Base64url-without-padding output, separation by purpose (`redis-ip`, `redis-subject`, `access-ip`, `access-subject`), separation by secret version, no raw IP/subject in output, and current-plus-previous results during rotation. Reject missing, undecodable, or decoded-shorter-than-32-byte secrets and duplicate version identifiers.

- [ ] **Step 3: Implement immutable policy resolution**

Use validated records/enums rather than mutable maps in the request path. Resolve one highest-tier rule per dimension and policy type, retain all different dimensions, and sort only by policy type then dimension so policy output is stable. Secret-version expansion belongs to the Redis command factories in Tasks 7/8, not to policy resolution. Current bootstrap policy is code-owned; do not add a configuration publisher, Path/Method selector, or upstream selector. Synthetic higher-tier rules are exercised only at the pure resolver boundary so the future precedence contract is frozen without exposing a runtime policy channel.

- [ ] **Step 4: Implement versioned purpose-separated HMAC**

Decode secrets once at startup, copy input arrays defensively, create a fresh `Mac` per call, and erase temporary byte arrays where practical. Domain-separate input as `purpose || 0x00 || value`; hash socket IPs from their canonical 4/16 address bytes and subjects from their exact UTF-8 claim bytes. Redis-key and access-log secrets are different required property groups. When a previous Redis secret is configured, emit both current and previous digests; log digests use only their own current secret and can never be reused as Redis identifiers.

- [ ] **Step 5: Run both focused classes**

Expected: the precedence, cumulative-dimension, ceiling, validation, separation, and rotation matrix passes without Redis or HTTP.

### Task 7: Implement the Atomic Reactive Redis Token Bucket

**Files:**
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/redis/GuardRedisProperties.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/redis/ReactiveRedisScriptExecutor.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/redis/RedisGuardKeyFactory.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RateLimitCommandFactory.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RateLimitCommand.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RateLimitDecision.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/ReactiveRateLimiter.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/ratelimit/RedisReactiveRateLimiter.java`
- Create: `api-gateway-web/src/main/resources/com/paytech/gateway/guard/ratelimit/token-bucket.lua`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/GuardDependencyUnavailableException.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/redis/RepositoryRedisEnvironment.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/redis/RedisTestNamespace.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/redis/RedisTestCleaner.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/redis/SharedRedisExtension.java`
- Modify: `api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestRuntime.java`
- Modify: `api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestBase.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/redis/RedisEnvironmentPreconditionAutoTest.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisTokenBucketAutoTest.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisRateLimitFailureAutoTest.java`

**Interfaces:**
- Produces: the stable `ReactiveRateLimiter` interface defined above and versioned Redis keys under `gateway:test:v1:<UUID>` in tests.
- Consumes: `ReactiveRedisConnectionFactory`, low-level reactive scripting commands, resolved policy, and HMAC digests.

- [ ] **Step 1: Add a fail-fast shared-Redis precondition**

Read only `MOR_REDIS_PASSWORD`, connect to `127.0.0.1:16379`, authenticate, `PING`, and require the repository-pinned Redis 8.2.1. If the variable, server, version, or authentication is wrong, fail with the exact repository Compose start/export instructions; never skip. `SharedRedisExtension` performs this check before every shared-Redis test class; no test depends on `RedisEnvironmentPreconditionAutoTest` running first. `GatewayAutoTestRuntime` owns one run namespace in the exact form `gateway:test:v1:<UUID>` and injects it through dynamic properties.

`RedisTestNamespace` can only be constructed from a UUID, rejects wildcard/glob characters by construction, and exposes validated Guard prefixes. Cleanup scans only the current run's `<namespace>:guard:*` with a bounded page size; before every `UNLINK`/`DEL`, it revalidates that each exact returned key begins with that prefix. A TTL-bounded sibling Sentinel at `<namespace>:sentinel:<UUID>` proves Guard cleanup did not broaden its scope and is removed by exact key at suite teardown. The static boundary gate separately forbids `KEYS` and `FLUSHDB`.

All final full-chain classes inherit the shared base and extension, and the base cleans only its Guard prefix plus resets both simulator journals before and after each method. Redis-unavailable classes introduced in Task 8 use a different base, do not register this extension, and never connect to or clean `16379`.

- [ ] **Step 2: Write the failing Token Bucket state matrix**

Against the shared Redis, cover first request, exact burst exhaustion, partial refill, full refill cap, fractional rates represented by integer microtokens, maximum integer-second `Retry-After`, TTL >= `max(60s, 2 * empty-to-full)`, all-or-nothing deduction across route/IP/subject, deterministic current/previous rotation keys, concurrent callers never exceeding capacity, and Redis `TIME` independence from application clock.

`RedisGuardKeyFactory` emits only these validated shapes:

```text
{namespace}:guard:v1:{route-code}:rate:shared-route:shared
{namespace}:guard:v1:{route-code}:rate:{client-ip|authenticated-subject}:{secret-version}:{digest}
{namespace}:guard:v1:{route-code}:concurrency:shared-route:shared
{namespace}:guard:v1:{route-code}:concurrency:{client-ip|authenticated-subject}:{secret-version}:{digest}
```

Require an explicit environment-scoped namespace of at most 128 characters made only from lower-case alphanumeric/hyphen colon-delimited segments; reject braces, whitespace, wildcard/glob characters, empty segments, or a namespace that already embeds `guard:v1`. Validate Route Code and secret-version segments before concatenation. `RateLimitCommandFactory` maps resolved rate rules to commands and expands IP/subject rules across current/previous Redis secret versions in stable dimension/version order. It never creates an identity digest for `SHARED_ROUTE`.

- [ ] **Step 3: Implement one low-level reactive script executor**

Load UTF-8 Lua as a classpath resource and execute with `ReactiveRedisConnectionFactory.getReactiveConnection().scriptingCommands().eval(...)` using `ReturnType.MULTI`. The API returns a `Flux`; require its single aggregate result and validate exact reply arity/integer types. Use `Mono.usingWhen` so success/error/cancel all invoke `ReactiveRedisConnection.closeLater()`. Map connection, authentication, timeout, script, and malformed-reply failures to `GuardDependencyUnavailableException`. Do not use repositories, cache abstraction, Redisson, `ReactiveRedisTemplate`, blocking adapters, `.subscribe(`, or `.block*()`.

- [ ] **Step 4: Implement the atomic Lua algorithm**

Validate all argument counts, key uniqueness/types, ranges, and existing Redis value shapes before the first mutation, then call Redis `TIME` once. Store integer `tokens`, `last_refill_millis`, and `refill_remainder` in each bucket hash. Use `elapsed = max(0, now - last)`, cap it at empty-to-full duration, and preserve sub-Microtoken fractions with `numerator = elapsed * refillMicrotokens + remainder`, integer quotient, and remainder; when capacity is reached, reset remainder and last-refill to now. Validate Java/Lua inputs and every multiplication intermediate stay within the IEEE-754 exact-integer range. Derive TTL as `max(60s, 2 * ceil(capacity * period / refill))`, never from free-form configuration.

After refill calculation, validate every bucket before any deduction and deduct exactly one requested token from every bucket only when all allow. On rejection, persist refill state without deducting any bucket and return the maximum ceil-divided recovery seconds. Refresh only involved-key TTLs and arrange the script so no possible runtime error follows its first write. Include a test where a later key has the wrong Redis type and prove no earlier bucket was mutated.

- [ ] **Step 5: Test Fail-Closed without touching shared Redis state**

Allocate then close an ephemeral loopback server socket to obtain an unused port, configure a separate limiter context to it, and assert `evaluate` terminates with `GuardDependencyUnavailableException` without touching the Compose namespace. Task 8 proves the corresponding HTTP 503 and zero-BFF-call behavior. Do not stop, restart, or reconfigure the Compose Redis.

- [ ] **Step 6: Run all Redis rate tests twice**

Keep the full HTTP suite sequential. Add one focused direct-limiter test that runs callers concurrently against two explicit child prefixes under the run namespace and proves isolation while the sibling Sentinel remains unchanged. Expected: stable counts, scoped cleanup, no leaked connections, and every key recorded by either factory/journal begins with the owned run namespace; never scan outside that namespace to make this assertion.

### Task 8: Implement Atomic Concurrency Leases and the Guard Filter

**Files:**
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyCommand.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyLease.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyDecision.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ReactiveConcurrencyLimiter.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/RedisReactiveConcurrencyLimiter.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/concurrency/ConcurrencyCommandFactory.java`
- Create: `api-gateway-web/src/main/resources/com/paytech/gateway/guard/concurrency/concurrency-acquire.lua`
- Create: `api-gateway-web/src/main/resources/com/paytech/gateway/guard/concurrency/concurrency-release.lua`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsGuardGlobalFilter.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayMetrics.java`
- Create: `api-gateway-test/src/test/java/com/paytech/gateway/test/RedisUnavailableGatewayAutoTestBase.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisConcurrencyLeaseAutoTest.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisGuardUnavailableAutoTest.java`

**Interfaces:**
- Produces: the stable `ReactiveConcurrencyLimiter` interface above and pre-forward Guard outcomes `ALLOWED`, `RATE_LIMITED`, `CONCURRENCY_LIMITED`, or `DEPENDENCY_UNAVAILABLE`.
- Consumes: route catalog, authenticated Security Context, remote socket IP, policy resolver, rate limiter, concurrency limiter, error writer, and BFF completion signal.

- [ ] **Step 1: Write failing lease atomicity tests**

Cover acquire below limit, exact limit rejection, one Lease ID written to every applicable ZSET, cleanup of expired entries before counting, all-or-nothing route/IP/subject acquisition, maximum integer-second `Retry-After` across full dimensions, release idempotency, current/previous rotation keys, concurrent contenders never exceeding any limit, key TTL twice the lease duration, and crash-style capacity recovery after expiry.

- [ ] **Step 2: Implement acquire/release scripts**

Acquire and Release first validate all argument counts/ranges, unique keys, and every key type (`none`/`zset`) before their first mutation; after that point neither script performs an operation that can predictably fail. Add wrong-type-on-a-later-key tests proving no partial `ZADD`, expired-entry cleanup, or `ZREM` occurred.

Acquire then calls Redis `TIME`, removes scores `<= now`, validates all ZSET cardinalities, and only then adds the same random UUID Lease ID with `now + leaseDuration` to every key. Set each key TTL to twice lease duration. On rejection, add nothing and return the latest earliest-expiring lease among all full dimensions as ceiling-seconds `Retry-After`. Release removes the Lease ID from every recorded key in one script and never deletes unrelated leases.

`ConcurrencyCommandFactory` uses the exact Task 7 key grammar and version expansion, with lease duration `route response timeout + 5s` and key TTL `2 * lease duration`.

- [ ] **Step 3: Write the failing full HTTP Guard matrix**

Use BFF barriers to hold real requests open. Assert Login route/IP, Refresh route/IP, and Logout route/subject rate and concurrency values; different dimensions accumulate; OPTIONS, 403, 401, 404, and 413 consume no Guard state; a rate-allowed/concurrency-rejected request keeps its rate deduction; 429 includes the maximum integer `Retry-After`; and pre-forward Redis failures return 503 with zero BFF calls.

Assert leases release after BFF success, BFF business error, disconnect/error, timeout, and client cancellation. The cancellation test first waits at the BFF barrier, disposes the real Reactor client subscription, and then awaits an empty ZSET. For release-failure behavior, replace only the exact recorded current-namespace Lease ZSET with a wrong-type value carrying an explicit PX TTL: the client response remains the already-determined upstream result and the real `GatewayMetrics` release-failure counter/log is emitted. Crash-style expiry recovery remains a separate lease test rather than being inferred from wrong-type injection.

- [ ] **Step 4: Implement Guard ordering and termination semantics**

Implement this boundary as an ordered Spring Cloud Gateway `GlobalFilter`, after exact route/origin/body/security checks and before upstream sanitization/Netty routing. Build rate commands through `RateLimitCommandFactory`, evaluate once, then build concurrency commands through `ConcurrencyCommandFactory` and acquire once. Return 429 Rate or Concurrency error without routing. Wrap downstream with `Mono.usingWhen`; its complete, error, and cancel cleanup functions all call one idempotent `safeRelease` whose release-error path records the bounded signal and resumes empty. A pre-forward dependency error maps to 503; cleanup never rewrites the response.

Do not call `subscribe()` in production cleanup. The body-limit, Guard, and upstream-sanitization GlobalFilters use the explicit Task 4 order constants in that sequence; an ordering test asserts body overflow terminates before either Redis limiter and Guard terminates before Netty routing.

`RedisUnavailableGatewayAutoTestBase` starts a separate strict BFF simulator and random-port `SpringApplication` context whose Redis connection is bound from startup to a closed loopback port. It does not inherit the shared base/extension or cleaner. `RedisGuardUnavailableAutoTest` proves JSON 503 and zero calls in that private BFF journal without changing the healthy shared context.

- [ ] **Step 5: Re-run concurrency and full-chain tests**

Expected: no capacity leaks, no over-admission under contention, correct ordering, and zero forbidden BFF calls.

### Task 9: Add Low-Cardinality Observability and Readiness Boundaries

**Files:**
- Modify: `api-gateway-web/src/main/resources/application.yml`
- Modify: `api-gateway-web/src/main/java/com/paytech/gateway/security/ReactiveJwksProvider.java`
- Modify: `api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayMetrics.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayAccessLogWebFilter.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayBootstrapHealthIndicator.java`
- Create: `api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayRedisReactiveHealthIndicator.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayAccessLogAutoTest.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayMetricsAutoTest.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayHealthAutoTest.java`
- Test: `api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayUnavailableHealthAutoTest.java`

**Interfaces:**
- Produces: one terminal access event, bounded Micrometer meters, `/actuator/health/liveness`, `/actuator/health/readiness`, and Prometheus output on the management port.
- Consumes: Route Code, method, status, duration, outcome, request ID, current trace ID when present, and separate access-log IP/subject digests.

- [ ] **Step 1: Write failing redaction and cardinality tests**

Send canaries in query, body, password JSON, Authorization, Cookie, CSRF header, IP, subject, JTI, SID, request ID, and an unknown raw path. Capture only the dedicated structured Gateway access/security loggers plus meter IDs. Assert one terminal access event contains only Route Code, method, status family/status, duration, outcome, trusted request ID, optional trace ID, and access-only IP/subject digests. No canary, Redis key, internal URI, exception stack, raw path, or raw identity may appear as a custom log value or metric tag.

For custom `gateway.*` meters only, assert tag keys are limited to `route`, `outcome`, `status_family`, `limiter`, and `dimension`, and tag values come from finite enums/catalog entries; JVM/Netty/Actuator meters may retain their own bounded tags. Cover allowed, 401, 403, 404, 413, both 429 outcomes, 503, 504, and release failure. Separately prove Spring Cloud Gateway raw-path tags remain disabled.

- [ ] **Step 2: Add neutral bounded meters and trace correlation**

Record a request timer plus Guard/JWKS/release counters through one `GatewayMetrics` facade and the existing Micrometer registry. Keep code on Micrometer Observation/SLF4J APIs: do not add an application OpenTelemetry bridge, SDK, exporter, or second metrics registry because the approved runtime uses the OpenTelemetry Java Agent. The structured logger inherits the validated current trace context from the logging context when instrumentation is present and safely omits it otherwise; it never parses a caller trace header itself. Do not enable raw-path Gateway metric tags.

- [ ] **Step 3: Implement one terminal structured access event**

Emit exactly once from a highest-level reactive filter for complete/error/cancel. Resolve outcome from exchange attributes written by routing/security/body/Guard/transport handlers. Hash only the normalized socket IP and authenticated subject using access-log secrets. Never stringify request headers, query, body, Security Context, throwable, or Redis command.

- [ ] **Step 4: Configure health groups and management isolation**

Use Boot 4 `org.springframework.boot.health.contributor.*` APIs. Expose health, info, prometheus, and readiness/liveness probes only on management port `19080`, disable endpoint discovery and all JMX exposure, and freeze groups as `liveness=livenessState` and `readiness=readinessState,gatewayBootstrap,gatewayRedis`. Disable Boot's default Redis health contributor so only the custom short-timeout reactive `PING` runs. Readiness excludes BFF and JWKS and returns sanitized detail without host, password, namespace, or exception message.

- [ ] **Step 5: Verify health behavior**

Assert healthy Redis yields readiness UP; reuse the separate startup-bound unavailable context to prove readiness DOWN; an unreachable BFF leaves readiness UP; and none of the Actuator paths are reachable from the resolved random application port or through `/api/operations/**`. A configuration-binding test, not a fixed-port server, separately proves production defaults `18080/19080`.

- [ ] **Step 6: Run the observability suite**

Expected: redaction, bounded tags, one-event semantics, management isolation, and health grouping all pass.

### Task 10: Freeze Architecture, Coverage, Documentation, and End-to-End Acceptance

**Files:**
- Create: `api-gateway/README.md`
- Create: `api-gateway/scripts/verify-gateway-boundaries.mjs`
- Create: `api-gateway/scripts/verify-jacoco.mjs`
- Modify: `api-gateway/pom.xml`
- Modify: `api-gateway/api-gateway-test/pom.xml`
- Create: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/architecture/GatewayArchitectureAutoTest.java`
- Create: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/acceptance/OperationsGatewayAcceptanceAutoTest.java`
- Modify: `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`
- Modify if Gateway implementation facts change: `agent-docs/TECHNOLOGY_STACK.md`
- Modify if Gateway implementation facts change: `agent-docs/JAVA_PROJECT_STRUCTURE.md`

**Interfaces:**
- Produces: executable developer instructions, static boundary gate, aggregate JaCoCo XML/HTML, and one end-to-end proof of the approved first slice.
- Consumes: all prior tasks plus the approved Gateway design and the already synchronized, verified Foundation cache semantics.

- [ ] **Step 1: Write failing architecture and acceptance gates**

Architecture test limits package scanning to `com.paytech.gateway` and asserts exactly three catalog plus running `RouteLocator` definitions, one WebFlux application, no Controller/Repository/Mapper/database packages, and no dynamic route publisher. Maven Enforcer `bannedDependencies(searchTransitive=true)` rejects MVC, the legacy `spring-cloud-starter-gateway`, Foundation Redis/Caffeine Starters, Redisson, Testcontainers, Mockito, Nacos, and Platform/core clients.

The Node verifier scans only production Java/resources for blocking Redis/MVC types, `ReactiveRedisTemplate`, `ReactiveStringRedisTemplate`, `.block(`/`.blockFirst(`/`.blockLast(`/`.subscribe(`, actual Java or `redis.call`/`redis.pcall` invocations of Redis `KEYS`/`FLUSHDB`, and wildcard production Operations paths. It parses YAML semantically and requires Retry, discovery locator, Redis route repository, local response cache, Forwarded/X-Forwarded, and raw-path metric tags to exist with value `false`; the disabled word itself is not a failure. It separately scans tests for Mockito APIs/annotations, Docker process calls, and actual forbidden Redis commands. Verifier source, dependency exclusions, negative assertions, Lua's `KEYS[n]` argument array, SCAN patterns, and README prose do not create false positives.

The end-to-end test uses the base-managed real random-port Gateway, HTTPS JWKS simulator, stateful BFF simulator, and healthy shared Redis run namespace. Before and after every scenario it cleans only that Guard prefix and resets both simulator journals. It proves Login, Refresh, and Logout happy paths; exact strip-prefix; trusted headers; BFF `Set-Cookie` pass-through; one representative failure for 401/403/404/413/rate 429/concurrency 429/BFF 503/BFF 504; no retry; and zero unexpected BFF/JWKS calls. Construct concurrency 429 with the real BFF barrier. The separate `RedisGuardUnavailableAutoTest` supplies the Redis 503 acceptance proof.

- [ ] **Step 2: Configure aggregate coverage**

Keep the Task 1 compile-scope dependency from `api-gateway-test` to `api-gateway-web` solely so JaCoCo `report-aggregate` can include web classes. Bind `prepare-agent` in the test module before Surefire and `report-aggregate` at `verify`. `verify-jacoco.mjs` reads the aggregate XML, excludes only generated configuration metadata and `ApiGatewayApplication`, and fails when the report/XML or web bundle is missing, either counter total is zero, the XML is older than test execution/class files, or instruction/branch coverage for all remaining `api-gateway-web` classes is below 90%. Do not weaken branch thresholds to make the first run green. The complete coverage gate is explicitly `./mvnw clean verify` followed by this Node verifier; Maven `verify` alone does not claim the 90% result.

- [ ] **Step 3: Write the local runbook**

Document prerequisites, the existing Compose Redis start command, `MOR_REDIS_PASSWORD` export, Maven commands, app port `18080`, management port `19080`, required BFF/JWKS/origin/HMAC properties, safe secret examples that are explicitly non-production, and the three cURL shapes without real credentials. State that tests never invoke Docker and that Redis failure tests use an unused loopback port. Freeze HMAC rotation as a two-stage release: deploy current+previous to the whole fleet, confirm rollout completion, wait at least 120 seconds, and only then remove previous.

- [ ] **Step 4: Synchronize only implemented documentation facts**

Update local environment, technology stack, and Java structure with the implemented two-module Gateway, exact Boot 4/Gateway artifacts, direct Reactive Lettuce/Lua boundary, ports, tests, and no-Testcontainers exception. Do not edit Platform Support requirements again. Preserve the synchronized cache facts: Redis is the `@Primary` default Manager, Caffeine requires explicit per-method Manager selection, the two are not L1/L2, and `spring.cache.type` is not the selection mechanism. Do not edit Foundation-owned files.

- [ ] **Step 5: Run the complete verification matrix from a clean test namespace**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw clean verify
node scripts/verify-gateway-boundaries.mjs
node scripts/verify-jacoco.mjs
git diff --check
```

Then, from the workspace root:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
npm --prefix requirements-site test
git diff --check
```

Expected: every automatic test and boundary gate passes, both JaCoCo thresholds are at least 90%, requirements remain 102/102 or higher, and packaging produces the ordinary plus `exec` Gateway JAR without a deployable test JAR.

- [ ] **Step 6: Perform final secret and scope review**

Search all changed files and reports for the Redis password, private JWKS key outside test resources, real HMAC material, JWT/Cookie canaries, internal URI credentials, Foundation-owned changes, Platform Support Java, BFF Java, and frontend code. Confirm none is present, list any unrelated pre-existing dirty files separately, and hand off without committing.
