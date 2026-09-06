# System-wide RFC 9457-based Problem Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Migrate every currently implemented first-party HTTP producer and consumer to the confirmed RFC 9457-based Code-MoR Problem Profile while preserving a deployment-gated legacy reader for rolling upgrades.

**Architecture:** Introduce a framework-neutral `paytech-http-problem` contract, then upgrade consumers before producers. Spring MVC keeps one shared writer in `paytech-mvc-web-starter`; Gateway keeps a WebFlux writer and validates Owner responses; Vault keeps its Go/zero-leak Java boundaries. Source migration ends with the legacy reader still available because this task does not deploy; deleting it is a separately gated post-rollout task.

**Tech Stack:** Java 25, Maven 3.9.16, Spring Boot 4.0.8 MVC, Spring Cloud Gateway WebFlux 5.0.3, Jackson 3, Go 1.27.1, TypeScript/Vite, OpenAPI 3.1, Nginx test configuration, JUnit 5, Node test runners.

**Spec:** `docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md`

## Global Constraints

- Preserve all existing dirty changes. In particular, do not overwrite Foundation Resource Lock work, Platform concurrency work, Finance balance-test edits, frontend page edits, or test-environment deployment work.
- Do not create branches, commits, publish artifacts, deploy services, start/stop shared infrastructure, or modify a shared database.
- Use RED-GREEN TDD for every behavior change. Tests may use real in-process/Loopback protocol simulators, never method-level behavior stubs.
- The target non-`HEAD` body has required `type`, `title`, `detail`, `code`, `requestId`, and `timestamp`; optional `errors` contains only `field` and `message`; body has no `status`, `instance`, or legacy top-level `message`.
- `type` is exactly `about:blank`; `title` is the standard English reason phrase for the actual HTTP status; `timestamp` is UTC RFC 3339 with exactly six fractional digits.
- Request IDs match `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`; values are case-sensitive; non-`HEAD` response header and body IDs must match exactly.
- `HEAD` errors retain status, `application/problem+json`, request ID, and other allowed headers but have no body.
- Consumers ignore unknown extensions but reject forbidden `status`/`instance`, missing/invalid required fields, mismatched IDs, bad media types, titles, or timestamps.
- The old `{code,message,requestId,timestamp}` reader is available only through an explicit per-target migration option. Its timestamp accepts the actual old Jackson `Instant` wire (valid four-digit UTC date-time, uppercase `Z`, optional 1–9 fractional digits). It is not auto-detected from malformed Problem JSON.
- Producer versions emit only the new shape. No `Accept`-based response negotiation is added.
- Phase 4 legacy deletion is not executed because no deployment is authorized. `CURRENT_IMPLEMENTATION.md` must say exactly what source and tests completed and that rollout/removal remain pending.

## Preflight

- [x] Record `git status --short --branch` for the root and every affected nested repository.
- [x] Confirm the current Foundation focused baseline:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw -pl paytech-http-client-support,paytech-mvc-web-starter -am test
```

Expected plan-time evidence: 232 focused tests pass (100 Common Core, 46 HTTP Client Support, 86 MVC Starter).

- [x] Record existing full-suite failures separately before attributing any failure to this migration. Do not weaken a new assertion to accommodate an unrelated baseline defect.

### Task 1: Add the framework-neutral Problem contract and build governance

**Files:**
- Create: `paytech-foundation/paytech-http-problem/pom.xml`
- Create: `paytech-foundation/paytech-http-problem/src/main/java/com/paytech/foundation/httpproblem/api/ApiProblemResponse.java`
- Create: `paytech-foundation/paytech-http-problem/src/main/java/com/paytech/foundation/httpproblem/api/ProblemFieldError.java`
- Create: `paytech-foundation/paytech-http-problem/src/main/java/com/paytech/foundation/httpproblem/api/ApiProblemProfile.java`
- Create: `paytech-foundation/paytech-http-problem/src/test/java/com/paytech/foundation/httpproblem/auto/ApiProblemResponseJsonAutoTest.java`
- Create: `paytech-foundation/paytech-http-problem/src/test/java/com/paytech/foundation/httpproblem/auto/ApiProblemProfileAutoTest.java`
- Modify: `paytech-foundation/pom.xml`
- Modify: `paytech-build/paytech-dependencies/pom.xml`
- Modify: `scripts/verify-java-foundation.mjs`

**Public contract:**

```java
public record ProblemFieldError(String field, String message) {}

public record ApiProblemResponse(
    String type,
    String title,
    String detail,
    String code,
    String requestId,
    String timestamp,
    List<ProblemFieldError> errors
) {}

public final class ApiProblemProfile {
    public static final String TYPE = "about:blank";
    public static final String CONTENT_TYPE = "application/problem+json";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static boolean isValidRequestId(String value);
    public static String formatTimestamp(Instant value);
    public static boolean isCanonicalTimestamp(String value);
    public static void validate(ApiProblemResponse value, String expectedTitle, String headerRequestId);
}
```

The model may use a minimal Jackson annotation only to omit absent/empty `errors`; it must not depend on MVC, WebFlux, Servlet, Spring business types, or a service error catalog.

- [x] Add module/BOM/architecture assertions first and run them RED because the module does not exist.
- [x] Add JSON/vector tests for exact six fields, optional non-empty `errors`, no legacy/status/instance fields, unknown-member read compatibility, request-ID boundary cases, and fixed timestamp vectors; run RED.
- [x] Implement the smallest model/profile and Maven wiring; run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-build
./mvnw clean install
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw -pl paytech-http-problem -am test
cd /Users/kenny/Desktop/Workspace/Code-MoR
node scripts/verify-java-foundation.mjs
```

### Task 2: Add the strict Problem decoder and explicit legacy compatibility option

**Files:**
- Create: `paytech-foundation/paytech-http-client-support/src/main/java/com/paytech/foundation/httpclient/api/HttpServiceClientOptions.java`
- Create: `paytech-foundation/paytech-http-client-support/src/main/java/com/paytech/foundation/httpclient/core/error/HttpProblemDecoder.java`
- Modify: `paytech-foundation/paytech-http-client-support/pom.xml`
- Modify: `paytech-foundation/paytech-http-client-support/src/main/java/com/paytech/foundation/httpclient/api/HttpServiceClientFactory.java`
- Modify: `paytech-foundation/paytech-http-client-support/src/main/java/com/paytech/foundation/httpclient/core/error/HttpServiceClientInterceptor.java`
- Modify: `paytech-foundation/paytech-http-client-support/src/test/java/com/paytech/foundation/httpclient/auto/HttpServiceClientFactoryTest.java`
- Modify: `paytech-foundation/paytech-http-client-support/src/test/java/com/paytech/foundation/httpclient/test/simulator/HttpServiceSimulator.java`

**API compatibility:** Keep `create(RestClient, CodeMessageEnum...)` as strict Problem-only. Add an overload taking `HttpServiceClientOptions`; expose named factories `strict()` and `migrationWithLegacyErrorResponse()` rather than a positional boolean. The immutable options also expose one Foundation-generic, target-supplied dependency-failure mapper so a caller can distinguish `TIMEOUT` from `UNAVAILABLE` without Foundation importing a caller error catalog; default behavior remains the existing `ServiceUnavailableException` mapping.

- [x] Extend the Loopback simulator and tests first for valid Problem, unknown extension, explicit legacy target, legacy-disabled rejection, malformed/empty/duplicate/trailing JSON, wrong media type, missing field, `status`/`instance`, bad type/title/time, unknown code, and header/body ID mismatch; run RED.
- [x] Add `HEAD` tests proving non-2xx responses validate only status/media type/single valid header ID and never require a body; run RED.
- [x] Implement media-type dispatch and strict decoding. Preserve only explicitly registered safe 4xx codes; map malformed/unknown responses to `UPSTREAM_INVALID_RESPONSE`; preserve current no-retry behavior and causes.
- [x] Add target-mapper tests proving a compliant upstream 504 and local read timeout classify as `TIMEOUT`, other compliant 5xx/connection failures classify as `UNAVAILABLE`, malformed 504 remains `UPSTREAM_INVALID_RESPONSE`, and the default mapping remains backward compatible.
- [x] Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation
./mvnw -pl paytech-http-client-support -am -Dtest=HttpServiceClientFactoryTest test
```

### Task 3: Upgrade Operations Web as a browser consumer before BFF producers change

**Files:**
- Modify: `operations-console-web/src/api/session.ts`
- Modify: `operations-console-web/src/api/session.test.ts`
- Modify: `operations-console-web/src/feedback/errorDetails.ts`
- Modify: `operations-console-web/src/feedback/errorDetails.test.ts`

**Behavior:** Parse new Problem bodies from `application/problem+json`, use `detail`, require header/body ID equality, accept unknown extensions, and keep an explicit temporary legacy `application/json` branch. `ApiError.message` may remain an internal UI property but must receive validated Problem `detail`, not a top-level wire `message`.

- [x] Add new/legacy/invalid response table tests first, including ID mismatch, bad timestamp/title/type, forbidden status/instance, unknown members, and safe fallbacks; run RED.
- [x] Implement the dual reader without changing page-level business branching.
- [x] Run `npm test` and `npm run build` in `operations-console-web`.

### Task 4: Upgrade Operations BFF's Platform consumer

**Files:**
- Modify: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/PlatformClientConfiguration.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/OperationsErrorStatusResolver.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/domain/enums/OperationsBffErrorCode.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/simulator/PlatformOperationsSimulator.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsBrowserBoundaryAutoTest.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsPlatformDiscoveryUnavailableAutoTest.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsHttpTestModeAutoTest.java`

**Behavior:** Configure only the known Platform target with `migrationWithLegacyErrorResponse()`. Business adapters do not parse JSON. BFF preserves registered safe 4xx semantics internally, rejects invalid/unknown upstream 4xx as 502, maps ordinary 5xx/connect/discovery failures to its own `503 DEPENDENCY_UNAVAILABLE`, maps a compliant upstream 504 or local dependency wait timeout to its own `504 DEPENDENCY_TIMEOUT`, and never forwards upstream request IDs or raw details to browsers.

- [x] Add simulator scenarios for valid new, valid old, invalid new, unknown code, 5xx, 504, and ID mismatch; run focused tests RED.
- [x] Wire the explicit compatibility option and target dependency-failure mapper; add the two BFF error codes and 504 status mapping; run focused tests GREEN.
- [x] Defer outbound BFF Problem-shape assertions until Task 7 installs the MVC writer.

### Task 5: Upgrade the Vault Java consumer before the Go producer

**Files:**
- Modify: `vault-service/paytech-vault-client/pom.xml`
- Modify: `vault-service/paytech-vault-client/src/main/java/com/paytech/vault/client/internal/wire/ErrorResponse.java`
- Modify: `vault-service/paytech-vault-client/src/main/java/com/paytech/vault/client/internal/DefaultVaultClient.java`
- Modify: `vault-service/paytech-vault-client/src/main/java/com/paytech/vault/client/internal/VaultResponseErrorHandler.java`
- Modify: `vault-service/paytech-vault-client/src/main/java/com/paytech/vault/client/internal/BoundedResponseBody.java`
- Modify: `vault-service/paytech-vault-client/src/test/java/com/paytech/vault/client/test/VaultSimulator.java`
- Modify: `vault-service/paytech-vault-client/src/test/java/com/paytech/vault/client/auto/VaultClientHttpTest.java`
- Modify: `vault-service/paytech-vault-client/src/test/java/com/paytech/vault/client/auto/VaultClientObservationTest.java`
- Modify: `vault-service/scripts/verify.sh`
- Modify: `vault-service/README.md`

**Behavior:** Use the shared Problem model/rules while retaining bounded UTF-8 parsing and zero-leak exceptions. Accept old Vault JSON only through an explicit migration constant local to the known Vault target; keep `ErrorResponse` solely as the redacted legacy four-field DTO and never duplicate the new shared Problem model. Tighten the Vault client's propagated/generated Request ID to the new Profile without changing the generic legacy Header validator used by Idempotency Keys. Update the Vault prepared-artifact preflight for the new Foundation dependency. Never log or expose response bodies, tokens, raw details, parser failures, or transport causes.

- [x] Add new/legacy/invalid/fixed-time/ID-mismatch/media-type tests and sensitive canary assertions first; run RED.
- [x] Implement strict Problem validation and temporary legacy dispatch; run Java client tests GREEN.

### Task 6: Add Gateway consumer validation in migration mode

**Files:**
- Modify: `api-gateway/api-gateway-web/pom.xml`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/OwnerProblemResponseGlobalFilter.java`
- Create: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/OwnerProblemValidator.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayGlobalFilterOrder.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/TrustedTerminalResponseHeaders.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/clientapi/ClientApiOwnerScenarioStore.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/clientapi/ClientApiOwnerSimulator.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/BffScenarioStore.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/simulator/bff/OperationsBffSimulator.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/transport/ClientApiOwnerSimulatorAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/ClientApiRoutingAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/OperationsRoutingAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsCorsAndBodyLimitAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsResponseContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/architecture/GatewayFilterOrderContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/architecture/GatewayArchitectureAutoTest.java`

**Behavior:** Intercept only Owner 4xx/5xx. Validate Problem status/media type/title/type/time/ID and forbidden members before terminal headers overwrite anything. Buffer only the Gateway-owned 64 KiB maximum error body, preserve valid raw bytes/timestamp/extensions, convert invalid responses through the existing centralized local 502 path, and give `HEAD` a no-body validation path. An immutable known-target migration setting accepts legacy errors only for `operations-console-bff`; strict is default and there is no content guessing. Task 6 proves local 502 ownership and non-disclosure but deliberately leaves the temporary legacy Gateway code/body to Task 9, which owns the one final Writer/code switch.

- [x] Add valid new/old/invalid and raw `HEAD` scenarios first; run RED.
- [x] Implement the response decorator/filter and ordering; run focused routing/architecture tests GREEN.

### Task 7: Switch the shared MVC producer to the new Profile

**Files:**
- Modify: `paytech-foundation/paytech-common-core/src/main/java/com/paytech/foundation/common/error/CommonErrorCode.java`
- Modify: `paytech-foundation/paytech-common-core/src/test/java/com/paytech/foundation/common/auto/enums/CodeEnumTest.java`
- Create: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/error/ApiProblemResponseFactory.java`
- Create: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/error/MvcProblemExceptionMapper.java`
- Create: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/error/MappedProblem.java`
- Create: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/error/ProblemResponseWriter.java`
- Create: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/error/ProblemExceptionFilter.java`
- Create: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/error/RequestBodyFieldErrorProjector.java`
- Modify: `paytech-foundation/paytech-mvc-web-starter/pom.xml`
- Modify: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/error/GlobalExceptionHandler.java`
- Modify: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/request/RequestIdFilter.java`
- Modify: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/autoconfigure/WebAutoConfiguration.java`
- Delete: `paytech-foundation/paytech-mvc-web-starter/src/main/java/com/paytech/foundation/web/error/ApiErrorResponseFactory.java`
- Create: `paytech-foundation/paytech-mvc-web-starter/src/test/java/com/paytech/foundation/web/auto/MvcProblemResponseAutoTest.java`
- Modify: `paytech-foundation/paytech-mvc-web-starter/src/test/java/com/paytech/foundation/web/auto/GlobalExceptionHandlerTest.java`
- Modify: `paytech-foundation/paytech-mvc-web-starter/src/test/java/com/paytech/foundation/web/auto/ApiErrorStatusResolverTest.java`
- Modify: `paytech-foundation/paytech-mvc-web-starter/src/test/java/com/paytech/foundation/web/auto/RequestIdFilterTest.java`
- Modify: `paytech-foundation/paytech-mvc-web-starter/src/test/java/com/paytech/foundation/web/auto/MetadataOnlyRequestLoggingTest.java`

**Status rules:** Valid JSON body-field violations become 422 `VALIDATION_ERROR` with safe field projections. Malformed JSON/header/path/query remains 400 without `errors`. Existing Owner resolver rules remain series-safe. Add normal MVC mappings for 404/405/415 where Spring exposes them to the Advice.

**Producer architecture:** Both Advice and the starter-owned `ProblemExceptionFilter` use one
`MvcProblemExceptionMapper -> ProblemResponseWriter` path. The fallback filter is ordered
`Ordered.HIGHEST_PRECEDENCE + 30`, inside `RequestIdFilter` (`+10`) and
`HttpRequestResponseLoggingFilter` (`+20`), so an exception thrown before Dispatcher is converted before the
logging filter records and copies the final response. It replaces an uncommitted partial response, preserves only
explicitly approved standard response headers, and rethrows committed responses and client-I/O/transport aborts. It
does not duplicate exception classification and does not flush or copy a `ContentCachingResponseWrapper`.

**Common taxonomy:** Add only the cross-application codes `VALIDATION_ERROR`, `NOT_FOUND`,
`METHOD_NOT_ALLOWED`, and `UNSUPPORTED_MEDIA_TYPE`. Known framework mappings are 422/404/405/415 respectively;
malformed or other standard 4xx framework failures safely fall back to `INVALID_REQUEST`, and unmapped standard 5xx
framework failures to `INTERNAL_ERROR`, while retaining the actual standard status/title. Never expose Spring's
generated detail. Owner `FailException` mappings accept only 4xx and Owner `ErrorException` mappings only 5xx;
null, resolver failure, non-standard status, or wrong-series output falls back to the approved default.

**Field safety:** Emit `errors` only for request-body semantic validation. Translate Bean Validation field paths to
external Jackson property names, including safe nested bean and numeric collection indices; omit maps, quoted or
class-qualified paths, controls, delimiter-bearing property names, global errors, ambiguous/unresolvable paths, and
unsafe/custom messages. Deduplicate and sort deterministically. Mixed body and non-body method validation becomes
400 with no `errors`.

**HEAD and headers:** Branch before factory/clock/serialization for `HEAD`; emit zero bytes while retaining status,
exact `application/problem+json`, exactly one valid `X-Request-Id`, and approved standard headers. The application
does not write `Date` or `X-Timestamp`; prove `Date` on a real embedded-server connector test because MockMvc does
not synthesize it. Preserve the public Request-ID constants and supplier constructor, but validate raw header
multiplicity and the generated value against the Profile grammar.

- [x] Rewrite unit expectations and add MockMvc/filter-chain plus a small real-connector contract test before production edits; observe RED.
- [x] Cover business failure, concurrent conflict, validation 422, malformed JSON 400, unavailable 503, unknown 500, not-found 404, method 405, media 415, exact content type, Date/no X-Timestamp, fixed time, and `HEAD` no body.
- [x] Tighten Request ID multiplicity and regex in tests first; then implement while preserving Client API/Operations entry generation ownership outside this generic MVC filter.
- [x] Implement the one mapper/writer/factory path and use it from Advice and the inner fallback filter; no service-local Advice.
- [x] Run the focused MVC suite and Foundation reactor tests.

### Task 8: Align MVC applications and Owner status mappings

**Files:**
- Modify: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/configuration/PlatformApiErrorConfiguration.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsHttpChecker.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/authentication/OperationsJwksHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewayConfigurationManagementHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewaySnapshotHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewayConfigurationLeaseOverlapHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuthenticationHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuthenticationConcurrencyHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuditBestEffortHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuditConnectionFailureHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsDirectoryRefactoringHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsResourceLockFailureAutoTest.java`
- Create: `client-contract-service/client-contract-test/src/test/java/com/paytech/client/contract/auto/ClientContractProblemProfileAutoTest.java`
- Modify: `finance-service/paytech-finance-test/src/test/java/com/paytech/finance/auto/FinanceApplicationAutoTest.java`
- Modify: `finance-service/paytech-finance-test/src/test/java/com/paytech/finance/auto/balance/BalanceQueryHttpAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/test/decision/RiskDecisionHttpClient.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ManualPayoutEligibilityAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ManualPayoutEligibilityFailClosedAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/contract/RiskDecisionConfigurationApiContractAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ext/http/DecisionConfigurationConcurrencyAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ext/http/DecisionReleaseManagementHttpAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ext/http/DecisionReleaseValidationRecoveryAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ext/http/DecisionRuleSetManagementHttpAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/service/DecisionConfigurationWebOrchestrationAutoTest.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsBrowserBoundaryAutoTest.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsHttpTestModeAutoTest.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsPlatformDiscoveryUnavailableAutoTest.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/simulator/PlatformOperationsSimulator.java`

**Owner mapping:** Platform maps authentication/session to 401 and permission denial to 403 while retaining its existing Gateway-config 404. Risk retains its existing Owner 404. Other services inherit the shared writer without new production classes. Audit every application-owned Security entry point, access-denied handler, error controller, filter, and interceptor that may consume an exception or write a 4xx/5xx directly; delegate such paths to the shared writer or prove they throw into it. A catch-only fallback does not normalize a component that already consumed the exception.

- [x] Centralize shared shape assertions in each repository's existing HTTP checker/helper before touching dozens of call sites.
- [x] Run focused tests RED against old installed Foundation, install the new Foundation locally, then run GREEN.
- [x] Run each repository's relevant full `clean verify` only when its documented local prerequisites are available; otherwise report the exact environmental block.

### Task 9: Switch Gateway local errors and strict Owner forwarding

**Files:**
- Modify: `api-gateway/README.md`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayErrorCode.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayErrorWriter.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayExceptionWebHandler.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayRequestIdPolicy.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/OwnerProblemResponseGlobalFilter.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/GatewayErrorContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/GatewayRequestIdAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/transport/GatewayTransportAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/ClientApiRoutingAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/routing/OperationsRoutingAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/RedisGuardUnavailableAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/guard/OperationsGuardFlowAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsCorsAndBodyLimitAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/web/OperationsResponseContractAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/observability/GatewayAccessLogAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/security/TrustedRequestBoundaryAutoTest.java`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/acceptance/OperationsGatewayAcceptanceAutoTest.java`

**Local fallback:** The Gateway WebFlux exception boundary must also normalize an uncommitted, application-owned
unexpected runtime failure to `500 INTERNAL_ERROR` through the same writer, and safely normalize standard
`ResponseStatusException` 4xx/5xx without exposing its generated reason/detail. Retain status-specific Gateway codes
where they already exist; otherwise use `GATEWAY_INVALID_REQUEST` for an unowned standard 4xx and the existing common
`INTERNAL_ERROR` for an unowned 5xx. Transport aborts/cancellation and already committed responses remain rethrown
rather than appending a second body. This closes the system-wide Profile requirement without adding another public
Gateway code.

- [x] Add fixed Clock, exact Profile, forbidden-member, local `HEAD`, valid Owner `HEAD`, and Owner-body-on-`HEAD` tests first; run RED.
- [x] Switch the WebFlux writer without importing MVC/Servlet. Preserve existing Gateway codes unless the confirmed design explicitly adds a new one.
- [x] Emit the binding `UPSTREAM_PROTOCOL_ERROR` code for invalid Owner/error transport responses and update every old-code assertion across routing, transport, guard, security, acceptance, and response-contract tests.
- [x] Keep explicit legacy-target support enabled only for rollout targets; strict new-Problem validation is the default.
- [x] Run focused and architecture tests, then Gateway `clean verify` and boundary scripts, recording pre-existing failures separately.

### Task 10: Switch Vault Go server and OpenAPI

**Files:**
- Modify: `vault-service/internal/httpapi/errors.go`
- Modify: `vault-service/internal/httpapi/headers.go`
- Modify: `vault-service/internal/httpapi/server.go`
- Modify: `vault-service/internal/httpapi/server_test.go`
- Modify: `vault-service/internal/contract/openapi_test.go`
- Modify: `vault-service/api/openapi/v1.yaml`
- Modify: Vault golden fixtures referenced by `internal/contract/openapi_test.go`, if present

**Behavior:** Go uses `http.StatusText(status)`, `about:blank`, six-digit UTC formatting, the unified request-ID regex/multiplicity, and `application/problem+json`. Success remains `application/json`. `HEAD` technical/business errors have no body. Cache-Control and Vault zero-leak rules remain unchanged.

- [x] Add fixed-time Problem and raw `HEAD` server tests first; run RED (the Go command was attempted and blocked by the absent toolchain; deterministic source/OpenAPI RED probes captured the old contract).
- [x] Update OpenAPI tests before schema edits; require six fields, no status/message, optional field errors only if Vault actually emits them, and request-ID response headers.
- [x] Implement server/OpenAPI changes and run every available gate. Java client tests and deterministic YAML/source/script/diff gates pass; `go`, `gofmt`, and the full repository script remain unavailable because the documented host/toolchain/infrastructure prerequisites are absent.

### Task 11: Align Commerce's public OpenAPI projection

**Files:**
- Modify: `commerce-service/api/openapi/client-v1.yaml`
- Modify: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1OpenApiContractAutoTest.java`
- Modify: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1OpenApiJavaDriftAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceProblemProfileAutoTest.java`

- [x] Change contract tests first to require all six fields, response `X-Request-Id`, exact time/request-ID patterns, no `status`/legacy `message`, and `errors` items containing only `field/message`; run RED.
- [x] Update the shared OpenAPI Problem schema and reusable response once; do not copy a Java Problem DTO into `paytech-commerce-client-api`.
- [x] Add one representative runtime Starter integration test; Commerce still has no business Controller and must not gain one for this task.
- [x] Run Commerce focused contract tests and `clean verify`.

### Task 12: Align repository-owned Nginx/connector limits and edge tests

**Files:**
- Modify: `test-environment/nginx/nginx.conf`
- Modify: `test-environment/compose.applications.yaml` only where the existing dirty deployment definition requires a matching mount/health assertion
- Modify: `api-gateway/api-gateway-web/src/main/resources/application.yml`
- Modify: `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/auto/transport/GatewayTransportAutoTest.java`
- Create or modify: repository-owned Nginx configuration tests under `test-environment/scripts/`

**Boundary:** Nginx absolute body/header/time limits must be strictly higher than published Gateway/application limits; `proxy_request_buffering off` allows Gateway to own parseable body timeout/limit responses; `/api` fallbacks proxy to Gateway. Nginx/TLS/connector failures that cannot enter a writer remain documented edge exceptions and are never decoded as Problem.

- [x] Add config assertions first and run RED.
- [x] Update only the repository test Nginx configuration, preserving existing deployment-script work.
- [x] Validate `nginx -t`/Compose config when the binary/runtime exists and attempt raw HTTP tests, recording the exact Redis-gated environment block.

### Task 13: Verification, documentation, and deployment-gated legacy cleanup record

**Files:**
- Modify: `paytech-foundation/README.md`
- Modify: affected component READMEs only where commands/contracts changed
- Modify: `agent-docs/CURRENT_IMPLEMENTATION.md`
- Modify: `docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md` only to update implementation status metadata after evidence exists
- Modify: this plan's checkboxes as tasks complete

- [x] Install the local Build/Foundation artifacts needed by downstream source verification; do not publish them.
- [x] Run focused and full gates proportionate to available local infrastructure, recording the Foundation Redis prerequisite block:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-build && ./mvnw clean install
cd /Users/kenny/Desktop/Workspace/Code-MoR/paytech-foundation && ./mvnw clean verify
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web && npm test && npm run build
cd /Users/kenny/Desktop/Workspace/Code-MoR/commerce-service && ./mvnw clean verify
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway && ./mvnw clean verify
cd /Users/kenny/Desktop/Workspace/Code-MoR && npm --prefix requirements-site test
```

- [x] Run affected Platform/BFF/Client Contract/Finance/Risk/Vault full gates when their documented prerequisites are available; record exact counts and environmental blockers.
- [x] Run `git diff --check` in the root and every affected nested repository.
- [x] Scan production sources/OpenAPI for old four-field producers and unintended top-level `message` parsing; distinguish the intentionally retained compatibility DTO/reader.
- [x] Update `CURRENT_IMPLEMENTATION.md` with source/test evidence only. Keep deployment as not deployed and Legacy removal pending.
- [x] Do not delete `paytech-common-core/.../ApiErrorResponse.java`, the compatibility decoder, or legacy compatibility tests in this task. Create a future cleanup checkpoint requiring proof that all deployed producers and consumers run the new Profile.

## Completion Boundary

This implementation is source-complete only when all currently implemented producer/consumer paths and public contracts use or understand the new Profile, focused tests pass, and any unavailable full gates are precisely documented. It is not rollout-complete until environments are deployed and observed. Legacy reader deletion is explicitly outside this no-deployment execution.
