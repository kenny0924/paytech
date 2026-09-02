# MVC Web Logging and Internal HTTP Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Centralize Spring MVC request/response logging and HTTP exception mapping, remove application-level TLS/X.509 from Platform Support and API Gateway, and preserve the real Client IP supplied by the trusted Nginx edge.

**Architecture:** Foundation owns one Servlet logging Filter and one MVC exception handler. Platform Support becomes a plain internal HTTP service and reuses those Foundation contracts. Nginx terminates public TLS, overwrites one `X-Real-IP`, and the reactive Gateway consumes that value once for Guard and access logging before stripping proxy headers from the BFF request.

**Tech Stack:** Java 25, Spring Boot 4.0.8, Spring MVC, Spring WebFlux/Spring Cloud Gateway, Maven Wrapper 3.9.16, JUnit 5, local MySQL and Redis.

**Spec:** `docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md`

## Global Constraints

- Work only in isolated Git worktrees; the main Foundation, Platform Support and Gateway checkouts contain unrelated user changes.
- Follow TDD: add or change the smallest observable test, run it red, implement the minimum behavior, and run it green.
- Do not add Flyway, Testcontainers, Mockito, an internal-security starter, Service Mesh or certificate tooling.
- Log complete Request and Response bodies at INFO exactly as approved; do not add masking, truncation, sampling, Header/Cookie logging or a configuration switch.
- Nginx must overwrite a single `X-Real-IP`; Gateway rejects missing, repeated, comma-separated or malformed values and never falls back to the Nginx socket IP.
- All consumed `Forwarded`, `X-Forwarded-*` and `X-Real-IP` values remain absent from BFF-bound requests.
- Keep Gateway authentication, authorization, Guard ceilings, Redis runtime state, route behavior and response contracts unchanged except for the approved transport and Client-IP source.
- Platform automated tests continue through HTTP -> Controller -> Processor -> Domain Service -> Repository -> Mapper -> local MySQL.
- Each repository gets its own focused commit; do not publish artifacts or push branches.

---

### Task 1: Foundation service-unavailable exception and MVC error mapping

**Files:**
- Create: `paytech-foundation/paytech-common-core/src/main/java/com/paytech/foundation/common/error/ServiceUnavailableException.java`
- Modify: `paytech-foundation/paytech-common-core/src/test/java/com/paytech/foundation/common/auto/error/BusinessExceptionTest.java`
- Modify: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/error/GlobalExceptionHandler.java`
- Modify: `paytech-foundation/paytech-mvc-web-starter/src/test/java/com/paytech/foundation/web/auto/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `ErrorException(CodeMessageEnum, String, Throwable)` and `ApiErrorResponseFactory.create(CodeMessageEnum, String, String)`.
- Produces: `ServiceUnavailableException(CodeMessageEnum)` and `ServiceUnavailableException(CodeMessageEnum, Throwable)`; MVC mapping to HTTP 503 with the safe enum message.

- [ ] **Step 1: Write failing common-core tests**

Add a test that proves the new type keeps the stable error code and cause while remaining an `ErrorException`:

```java
@Test
void classifiesUnavailableDependencyForHttpAdapters() {
    IllegalStateException cause = new IllegalStateException("database unavailable");
    ServiceUnavailableException exception =
        new ServiceUnavailableException(CommonErrorCode.INTERNAL_ERROR, cause);

    assertInstanceOf(ErrorException.class, exception);
    assertSame(CommonErrorCode.INTERNAL_ERROR, exception.getErrorCode());
    assertEquals(CommonErrorCode.INTERNAL_ERROR.getMessage(), exception.getMessage());
    assertSame(cause, exception.getCause());
}
```

- [ ] **Step 2: Verify the common-core test is red**

Run:

```bash
./mvnw -pl paytech-common-core -Dtest=BusinessExceptionTest test
```

Expected: compilation fails because `ServiceUnavailableException` does not exist.

- [ ] **Step 3: Add the minimum exception type**

Create the concrete classification without retry behavior or Spring dependencies:

```java
public class ServiceUnavailableException extends ErrorException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(CodeMessageEnum errorCode) {
        super(errorCode);
    }

    public ServiceUnavailableException(CodeMessageEnum errorCode, Throwable cause) {
        super(errorCode, null, cause);
    }
}
```

- [ ] **Step 4: Verify common-core is green**

Run the Step 2 command. Expected: `BusinessExceptionTest` passes.

- [ ] **Step 5: Write failing MVC mapping tests**

Add a 503 assertion and broaden the invalid-request test to invoke the common handler signature:

```java
@Test
void mapsUnavailableFailureToServiceUnavailableWithoutLeakingCause() {
    ServiceUnavailableException exception = new ServiceUnavailableException(
        CommonErrorCode.INTERNAL_ERROR,
        new IllegalStateException("password=should-not-leak")
    );

    ResponseEntity<ApiErrorResponse> response =
        handler.handleServiceUnavailableException(exception, request("request-10005"));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    assertEquals(CommonErrorCode.INTERNAL_ERROR.getCode(), response.getBody().code());
    assertEquals(CommonErrorCode.INTERNAL_ERROR.getMessage(), response.getBody().message());
    assertEquals("request-10005", response.getBody().requestId());
    assertEquals(RESPONSE_TIME, response.getBody().timestamp());
}
```

The invalid-request handler annotation must cover `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `BindException`, `HttpMessageNotReadableException`, `ServletRequestBindingException` and `MethodArgumentTypeMismatchException` through one stable 400 response.

- [ ] **Step 6: Verify the MVC mapping test is red**

Run:

```bash
./mvnw -pl paytech-mvc-web-starter -am -Dtest=GlobalExceptionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `handleServiceUnavailableException` is absent.

- [ ] **Step 7: Implement the 503 and common invalid-request mappings**

Add the specific handler before the generic `ErrorException` mapping and always expose the enum default message:

```java
@ExceptionHandler(ServiceUnavailableException.class)
public ResponseEntity<ApiErrorResponse> handleServiceUnavailableException(
        ServiceUnavailableException exception, HttpServletRequest request) {
    String requestId = requestId(request);
    LOGGER.error("服务暂时不可用, requestId={}, errorCode={}, exceptionType={}",
        requestId, exception.getCode(), exception.getClass().getName());
    ApiErrorResponse response = responseFactory.create(
        exception.getErrorCode(), exception.getErrorCode().getMessage(), requestId);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
}
```

Use one `handleInvalidRequestException(Exception, HttpServletRequest)` method for the six framework request exceptions; do not expose binding or parse messages.

- [ ] **Step 8: Verify Foundation exception tests are green**

Run both commands from Steps 2 and 6. Expected: all selected tests pass.

---

### Task 2: Foundation request/response/duration logging Filter

**Files:**
- Create: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/logging/HttpRequestResponseLoggingFilter.java`
- Create: `paytech-foundation/paytech-mvc-web-starter/src/test/java/com/paytech/foundation/web/auto/HttpRequestResponseLoggingFilterTest.java`
- Modify: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/autoconfigure/WebAutoConfiguration.java`
- Modify: `paytech-foundation/README.md`

**Interfaces:**
- Consumes: `RequestIdFilter.REQUEST_ATTRIBUTE`, Servlet `FilterChain`, Spring `ContentCachingRequestWrapper` and `ContentCachingResponseWrapper`.
- Produces: auto-configured `HttpRequestResponseLoggingFilter`; one INFO event named `HTTP request completed` per request.

- [ ] **Step 1: Write the real Filter-chain preservation test**

Use `RequestIdFilter`, the new logging Filter, `MockHttpServletRequest`, `MockHttpServletResponse` and a Logback `ListAppender`. The downstream chain must read the JSON request and write JSON response:

```java
request.setMethod("POST");
request.setRequestURI("/payments");
request.setQueryString("expand=fees");
request.setContentType(MediaType.APPLICATION_JSON_VALUE);
request.setContent("{\"cardToken\":\"tok_10001\"}".getBytes(StandardCharsets.UTF_8));

FilterChain endpoint = (servletRequest, servletResponse) -> {
    String body = new String(servletRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals("{\"cardToken\":\"tok_10001\"}", body);
    HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
    httpResponse.setStatus(201);
    httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
    httpResponse.getWriter().write("{\"paymentId\":\"pay_10001\"}");
};
```

Assert the final client body is unchanged, exactly one event exists, and its structured values contain `requestId`, `method`, `uri`, `query`, `requestBody`, `status`, `responseBody` and a non-negative `elapsedMs`.

- [ ] **Step 2: Write the exceptional-chain and MDC test**

Make the downstream chain throw after writing status 500, assert one terminal event, assert the exception propagates, and assert the caller's previous MDC value is restored after the outer `RequestIdFilter` returns.

- [ ] **Step 3: Verify logging tests are red**

Run:

```bash
./mvnw -pl paytech-mvc-web-starter -am -Dtest=HttpRequestResponseLoggingFilterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `HttpRequestResponseLoggingFilter` does not exist.

- [ ] **Step 4: Implement the minimum caching Filter**

Implement one final `OncePerRequestFilter`, ordered immediately after `RequestIdFilter`:

```java
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class HttpRequestResponseLoggingFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestResponseLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper cachedRequest =
            new ContentCachingRequestWrapper(request, Integer.MAX_VALUE);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            try {
                log(cachedRequest, cachedResponse, startedAt);
            } finally {
                cachedResponse.copyBodyToResponse();
            }
        }
    }
}
```

Decode body bytes with the declared character encoding or UTF-8, use raw `getRequestURI()` and `getQueryString()`, and emit one SLF4J structured INFO event. Do not read headers or cookies.

- [ ] **Step 5: Auto-configure and verify the Filter**

Add this exact bean boundary:

```java
@Bean
@ConditionalOnMissingBean
public HttpRequestResponseLoggingFilter httpRequestResponseLoggingFilter() {
    return new HttpRequestResponseLoggingFilter();
}
```

Run the Step 3 command. Expected: both logging scenarios pass and response bytes remain unchanged.

- [ ] **Step 6: Run and commit Foundation**

Run:

```bash
./mvnw clean verify
./mvnw clean install -DskipTests
git diff --check
git add paytech-common-core paytech-mvc-web-starter README.md
git commit -m "feat: centralize MVC logging and unavailable errors"
```

Expected: the full Foundation reactor passes and `1.0.0-SNAPSHOT` is available locally for Platform tests.

---

### Task 3: Platform Support plain HTTP and shared error contract

**Files:**
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewaySnapshotHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportHealthAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportConfigurationAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportDependencyBoundaryAutoTest.java`
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/service/processor/gatewayconfiguration/QueryGatewaySnapshotProcessor.java`
- Modify: `platform-support-service/platform-support-web/src/main/resources/application.yml`
- Modify: `platform-support-service/platform-support-test/src/test/resources/application-auto.yml`
- Modify: `platform-support-service/platform-support-web/pom.xml`
- Modify: `platform-support-service/platform-support-test/pom.xml`
- Modify: `platform-support-service/pom.xml`
- Modify: `platform-support-service/README.md`
- Delete: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/ext/rest/PlatformHttpExceptionHandler.java`
- Delete: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/configuration/InternalWorkloadSecurityConfiguration.java`
- Delete: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/integration/security/VerifiedWorkload.java`
- Delete: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/integration/security/WorkloadCapability.java`
- Delete: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/integration/security/WorkloadIdentityVerifier.java`
- Delete: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/integration/security/X509WorkloadIdentityVerifier.java`
- Delete: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/security/GatewaySnapshotMtlsAutoTest.java`
- Delete: `platform-support-service/platform-support-test/src/test/resources/tls/*`

**Interfaces:**
- Consumes: Foundation `ServiceUnavailableException` and auto-configured `GlobalExceptionHandler`.
- Produces: unchanged `GET /platform-support/gateway-config/v1/public-snapshot` over private HTTP; stable common 503 body with `code`, `message`, `requestId` and `timestamp`.

- [ ] **Step 1: Change full-chain HTTP tests first**

Remove the nested Spring Security test configuration and assert the shared 503 shape with a fixed request ID:

```java
EntityExchangeResult<byte[]> result = client.get()
    .uri(SNAPSHOT_PATH)
    .header("X-Request-Id", "platform-test-request-10001")
    .exchange()
    .expectStatus().isEqualTo(503)
    .expectBody()
    .jsonPath("$.code").isEqualTo("PLATFORM_GATEWAY_SNAPSHOT_UNAVAILABLE")
    .jsonPath("$.message").isEqualTo("Gateway Snapshot 暂时不可用")
    .jsonPath("$.requestId").isEqualTo("platform-test-request-10001")
    .jsonPath("$.timestamp").exists()
    .returnResult();
```

Rewrite health tests to call plain `http://localhost:{port}` without certificates and verify health, liveness and readiness. Remove assertions for certificate handshake, SPIFFE SAN and `UserDetailsService`.

- [ ] **Step 2: Add architecture assertions for the simplified boundary**

The dependency/configuration tests must assert:

```java
assertFalse(webDependencies.contains("org.springframework.boot:spring-boot-starter-security"));
assertFalse(testDependencies.contains("org.springframework.security:spring-security-test"));
assertNull(production.getProperty("server.ssl.enabled"));
assertNull(production.getProperty("server.ssl.client-auth"));
```

Add Maven Enforcer bans for `org.springframework.boot:spring-boot-starter-security` and `org.springframework.security:*`. Verify removal of `integration/security`, `InternalWorkloadSecurityConfiguration`, the local HTTP handler and TLS resources through the reviewed Git diff and final exact scan; do not add source-text or file-existence change-detector tests.

- [ ] **Step 3: Verify the Platform tests are red**

Run:

```bash
./mvnw -pl platform-support-test -am \
  -Dtest=GatewaySnapshotHttpAutoTest,PlatformSupportHealthAutoTest,PlatformSupportConfigurationAutoTest,PlatformSupportDependencyBoundaryAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: tests fail because Security/TLS and the local two-field error handler still exist.

- [ ] **Step 4: Replace the Processor exception**

Change only the classification, preserving the existing local transaction and fail-closed catch:

```java
} catch (RuntimeException exception) {
    throw new ServiceUnavailableException(
        PlatformErrorCode.PLATFORM_GATEWAY_SNAPSHOT_UNAVAILABLE,
        exception
    );
}
```

- [ ] **Step 5: Remove application security and TLS**

Delete the six security classes, the local exception handler, mTLS test and PEM resources. Remove the two Spring Security dependencies. Remove the entire `server.ssl` block from production and test YAML; retain `server.port`, `/platform-support` context path, datasource, MyBatis and Actuator settings.

- [ ] **Step 6: Verify focused Platform behavior is green**

Run the Step 3 command. Expected: tests pass against the local MySQL schema/data and common Foundation response.

- [ ] **Step 7: Run and commit Platform Support**

Run:

```bash
./mvnw clean verify
git diff --check
git add -A
git commit -m "refactor: use internal HTTP for Platform Support"
```

Expected: full-chain tests and the existing 90% instruction/branch coverage gates pass.

---

### Task 4: Gateway internal HTTP transport and trusted Nginx Client IP

**Files:**
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayProtocol.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExchangeAttributes.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/TrustedRequestContextWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsIngressGuardWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/guard/OperationsGuardGlobalFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/observability/GatewayAccessLogWebFilter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/transport/GatewayNettyServerConfiguration.java`
- Delete: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/transport/GatewayTransportProperties.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/routing/OperationsRoutingProperties.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/OperationsSecurityProperties.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/security/ReactiveJwksProvider.java`
- Modify: `api-gateway/api-gateway-web/src/main/resources/application.yml`
- Modify: `api-gateway/api-gateway-test/src/test/resources/application-auto.yml`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/GatewayAutoTestRuntime.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/jwks/JwksSimulator.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/TrustedRequestBoundaryAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayAccessLogAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/transport/GatewayTransportAutoTest.java`
- Modify: each Gateway auto-test `gatewayClient()` builder so it sends default `X-Real-IP: 203.0.113.17`
- Delete: `api-gateway/api-gateway-test/src/test/resources/tls/jwks-server-cert.pem`
- Delete: `api-gateway/api-gateway-test/src/test/resources/tls/jwks-server-key.pem`
- Modify: `api-gateway/scripts/verify-gateway-boundaries.mjs`
- Modify: `api-gateway/README.md`

**Interfaces:**
- Consumes: exactly one Nginx-owned `X-Real-IP` containing a numeric IPv4 or IPv6 address.
- Produces: `GatewayExchangeAttributes.CLIENT_ADDRESS` containing `InetAddress`; Guard and access logging consume only that attribute.

- [ ] **Step 1: Write trusted Client-IP boundary tests**

Add cases that send a valid IPv4 and IPv6 address and cases that send no value, duplicate values, comma chains, hostnames, addresses with ports and malformed bytes. Valid requests reach the existing route boundary; invalid requests return the existing safe `GATEWAY_FORBIDDEN` 403 shape without BFF calls.

The forwarding assertion must prove all of these are absent downstream:

```java
assertFalse(entry.headers().containsKey("Forwarded"));
assertFalse(entry.headers().containsKey("X-Forwarded-For"));
assertFalse(entry.headers().containsKey("X-Real-IP"));
```

- [ ] **Step 2: Write Guard and access-log source tests**

Send socket-loopback requests with `X-Real-IP: 203.0.113.77`. Assert Redis Guard keys contain `client-ip:203.0.113.77` rather than loopback, and `ip_digest` equals the HMAC digest of `203.0.113.77`. This proves all consumers use the same trusted attribute.

- [ ] **Step 3: Verify Client-IP tests are red**

Run:

```bash
./mvnw -pl api-gateway-test -am \
  -Dtest=TrustedRequestBoundaryAutoTest,GatewayAccessLogAutoTest,OperationsGuardFlowAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the current implementation strips/ignores `X-Real-IP` and uses loopback socket IP.

- [ ] **Step 4: Resolve the trusted address once**

Add `GatewayProtocol.CLIENT_IP = "X-Real-IP"` and `GatewayExchangeAttributes.CLIENT_ADDRESS`. In `TrustedRequestContextWebFilter`, require exactly one Header value, reject commas, parse numeric input with Netty `NetUtil.createByteArrayFromIpAddressString`, convert with `InetAddress.getByAddress`, put it in the exchange attributes, then strip every reserved ingress Header before continuing.

On invalid input, set `GatewayOutcome.AUTHORIZATION_DENIED` and use `GatewayErrorWriter` to return:

```java
return errorWriter.write(
    exchange,
    HttpStatus.FORBIDDEN,
    GatewayErrorCode.GATEWAY_FORBIDDEN,
    null
);
```

- [ ] **Step 5: Make Guard and access logs consume the attribute**

Both Guard filters must use:

```java
InetAddress clientAddress = exchange.getAttribute(GatewayExchangeAttributes.CLIENT_ADDRESS);
if (clientAddress == null) {
    throw new GuardDependencyUnavailableException("Operations Guard 缺少可信 Client IP");
}
```

`GatewayAccessLogWebFilter.ipDigest` reads the same attribute and returns `unavailable` only for a request rejected before a valid address was established. Remove all three socket-address readers.

- [ ] **Step 6: Verify Client-IP behavior is green**

Add `.defaultHeader(GatewayProtocol.CLIENT_IP, "203.0.113.17")` to application-port test clients, but not management-port clients or tests intentionally exercising missing/invalid input. Run the Step 3 command. Expected: all focused tests pass.

- [ ] **Step 7: Write internal HTTP transport tests**

Change transport/configuration tests to assert production has no `server.ssl.*`, `gateway.transport.mode`, `GATEWAY_TLS_CERTIFICATE`, `GATEWAY_TLS_PRIVATE_KEY` or JWKS Trust Anchor. Assert BFF and JWKS accept exact internal HTTP origins and reject HTTPS, user-info, query and fragment forms.

- [ ] **Step 8: Verify transport tests are red**

Run:

```bash
./mvnw -pl api-gateway-test -am \
  -Dtest=GatewayTransportAutoTest,GatewayBootstrapPropertiesAutoTest,GatewayArchitectureAutoTest,OperationsJwksAvailabilityAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: tests fail on the current public TLS mode, HTTPS JWKS and trust-anchor constructor.

- [ ] **Step 9: Simplify Gateway transport and upstream clients**

Delete `GatewayTransportProperties`. Keep `GatewayNettyServerConfiguration` only for connection, backlog, HTTP/1.1 and Header deadline hardening. Remove its TLS and loopback validation dependencies.

Require plain internal HTTP origins:

```java
if (uri == null || !"http".equalsIgnoreCase(uri.getScheme()) || isBlank(uri.getHost())
        || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
    throw new IllegalArgumentException("内部服务 URI 必须是 HTTP URI");
}
```

Remove `trustAnchor` from `OperationsSecurityProperties`; build the existing bounded Reactor `HttpClient` without `.secure(...)`. Convert `JwksSimulator` to HTTP and delete its PEM files.

Remove `server.ssl` and `gateway.transport` from YAML. Require `${GATEWAY_BIND_ADDRESS}` for the application Listener, keep the public Console Origin as HTTPS, and document that deployment networking permits Nginx only.

- [ ] **Step 10: Update boundary script and README**

The script must reject `server.ssl`, `gateway.transport.mode`, Gateway certificate variables, JWKS Trust Anchor and HTTPS internal upstream examples. README must include the exact Nginx contract:

```nginx
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $remote_addr;
```

State that Nginx-to-Gateway and Gateway-to-Java-service traffic is private HTTP and that network policy must prevent direct Gateway access.

- [ ] **Step 11: Run and commit Gateway**

Run:

```bash
./mvnw clean verify
node scripts/verify-gateway-boundaries.mjs
git diff --check
git add -A
git commit -m "refactor: run Gateway behind trusted Nginx"
```

Expected: the full Gateway test suite, coverage and boundary checks pass without TLS test material.

---

### Task 5: Root architecture and requirement consistency

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/DEVELOPMENT_CONVENTIONS.md`
- Modify: `agent-docs/TECHNOLOGY_STACK.md`
- Modify: `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`
- Modify: `agent-docs/APPLICATION_ARCHITECTURE.md`
- Modify: `agent-docs/JAVA_PROJECT_STRUCTURE.md`
- Modify: `agent-docs/UNIT_TEST_GUIDE.md`
- Modify: `agent-docs/VAULT_SERVICE_ARCHITECTURE.md`
- Modify: current Platform/Gateway specs and implementation records that claim application TLS/mTLS is current
- Modify: `requirements-site/content/requirements.ts` only where current requirements still mandate Platform/Vault application mTLS

**Interfaces:**
- Consumes: committed Foundation, Platform Support and Gateway runtime facts from Tasks 1-4.
- Produces: one current architecture rule: public TLS at Nginx, trusted Client IP at Gateway, private HTTP between Java services.
- Produces: one project-wide MVC rule: services reuse the Starter logging/global-error boundary and use the dedicated unavailable subtype only when HTTP status `503` is part of the caller contract.

- [ ] **Step 1: Add documentation regression assertions or exact scans**

Run and record the current matches before editing:

```bash
rg -n "server\.ssl|client-auth|mTLS|X\.509|PUBLIC_TLS|GATEWAY_TLS_|JWKS_TRUST_ANCHOR|HTTPS JWKS" \
  AGENTS.md README.md agent-docs requirements-site/content docs/superpowers/specs docs/superpowers/plans
```

Classify historical records explicitly; current navigation and authority documents must not instruct Java applications to own certificates.

- [ ] **Step 2: Apply semantic documentation updates**

Preserve unrelated database counts, authentication/Tenant/API-control-plane facts and historical decisions. Add the stable current statement:

```text
Nginx terminates public TLS and overwrites one X-Real-IP. API Gateway consumes that value for Guard and access logging, removes proxy headers before BFF forwarding, and is reachable only from the private Nginx network. Gateway, BFFs, Platform Support, Vault and core Java services communicate over private HTTP and do not own X.509 certificates.
```

Do not replace whole shared files; edit only the affected paragraphs.

In `DEVELOPMENT_CONVENTIONS.md`, preserve the two base failure categories while documenting
`ServiceUnavailableException` as the narrow shared `ErrorException` subtype for an explicit safe `503` transport
contract. MVC services must consume the Starter's global exception and Request/Response logging boundary rather than
adding local `@RestControllerAdvice` or duplicate logging Filters.

- [ ] **Step 3: Run root verification**

Run:

```bash
npm --prefix requirements-site run build
node --test requirements-site/tests/*.test.mjs
npm --prefix requirements-site run lint
node scripts/verify-local-environment.mjs
git diff --check
```

Expected: requirements build/tests/lint, local-environment boundary checks and whitespace checks pass.

- [ ] **Step 4: Commit the root repository**

Run:

```bash
git add AGENTS.md README.md agent-docs requirements-site docs/superpowers
git commit -m "docs: standardize Nginx TLS and internal HTTP"
```

Expected: one semantic documentation commit on the isolated root branch; no nested-repository working-tree content is staged in the root commit.

---

### Task 6: Cross-repository final gate and integration handoff

**Files:**
- Verify only; no new production files.

**Interfaces:**
- Consumes: the four repository commits.
- Produces: exact SHAs, changed-file lists, test totals and a semantic merge order that preserves dirty main-checkout work.

- [ ] **Step 1: Re-run clean verification in dependency order**

Run Foundation `clean install`, then Platform `clean verify`, Gateway `clean verify`, and root Requirements/local-environment checks. Expected: all commands exit 0.

- [ ] **Step 2: Scan forbidden runtime remnants**

Run targeted scans in Platform and Gateway for deleted certificate variables, PEM resources, X.509 code, `server.ssl`, `PUBLIC_TLS` and JWKS Trust Anchor. Expected: no current runtime hits; comments or history are not included in production/test resources.

- [ ] **Step 3: Prepare non-destructive integration**

Record:

```bash
git status --short --branch
git log -1 --oneline
git diff HEAD^ --name-status
```

Do not cherry-pick into a dirty main checkout. Report any overlapping dirty files—especially Gateway Guard files—and provide the isolated commit SHA for later semantic integration.
