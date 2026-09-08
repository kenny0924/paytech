# Commerce Client API Boundary Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove non-persistence Mapper naming, duplicated Gateway/Foundation validation, hand-written Map-based JSON creators, and undocumented public Commerce Client API contracts while preserving V1 fields and business behavior and applying the approved backward-compatible relaxation for unknown request properties.

**Architecture:** Foundation owns only stable HTTP header names and Request ID infrastructure; Gateway owns authentication and trusted identity injection; Commerce owns resource authorization, its Idempotency-Key policy, business input constraints and normal Jackson/Bean Validation request binding. The Commerce HTTP adapter has one conversion boundary named `*Converter`, and public Java/OpenAPI contracts document the same semantics.

**Tech Stack:** Java 25, Spring Boot 4.0.8, Spring MVC, Jackson 3, Jakarta Bean Validation, Maven, JUnit 5, RestTestClient, OpenAPI 3.1.

**Spec:** [`docs/superpowers/specs/2026-09-08-commerce-client-checkout-structure-simplification-design.md`](../specs/2026-09-08-commerce-client-checkout-structure-simplification-design.md), especially section 9; [`docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md`](../specs/2026-09-05-commerce-client-api-v1-design.md); [`docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md`](../specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md).

## Global Constraints

- Preserve existing user changes in the dirty Foundation, Gateway and Commerce repositories; edit only task-owned lines and never reset or overwrite unrelated work.
- Do not create a branch, worktree, commit, push, deploy, enable a Gateway route or apply Commerce SQL without a later explicit request.
- Automated Checkout behavior tests enter through random-port HTTP and use the real production chain; do not add direct Service/Processor/Repository behavior tests or method-level mocks.
- Unknown V1 JSON request properties are accepted and ignored; JSON scalar coercion into String is rejected.
- JSON structure/type errors are RFC 9457 `400 INVALID_REQUEST`; post-binding field semantic errors are `422 VALIDATION_ERROR` with safe field projections when available.
- Gateway authentication is trusted, but missing, duplicate or non-numeric trusted identity protocol headers fail closed at one Web boundary.
- `Idempotency-Key` syntax and semantics remain Commerce-owned; only its stable header name is shared.
- `Mapper` remains reserved for persistence; non-persistence conversion uses `*Converter`.
- JavaDoc and OpenAPI descriptions explain semantics and do not change schemas.

---

### Task 1: Centralize Stable HTTP Header Names

**Files:**
- Create: `paytech-foundation/paytech-common-core/src/main/java/com/paytech/foundation/common/http/ApiHeaderNames.java`
- Test: `paytech-foundation/paytech-common-core/src/test/java/com/paytech/foundation/common/auto/http/ApiHeaderNamesAutoTest.java`
- Modify: `paytech-foundation/paytech-http-problem/src/main/java/com/paytech/foundation/httpproblem/api/ApiProblemProfile.java`
- Modify: `api-gateway/api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayProtocol.java`
- Modify: Commerce API/filter imports that currently consume `ClientApiHeaders`
- Delete after all consumers migrate: `commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/common/ClientApiHeaders.java`

**Interfaces:**
- Produces: `final class ApiHeaderNames` with `REQUEST_ID`, `GATEWAY_CLIENT_ID`, `GATEWAY_CREDENTIAL_ID`, and `IDEMPOTENCY_KEY` String constants.
- Preserves: `ApiProblemProfile.REQUEST_ID_HEADER` and Gateway-local aliases where binary/source compatibility has consumers; aliases delegate to `ApiHeaderNames` rather than copy literals.

- [x] **Step 1: Write the failing Foundation contract test**

```java
assertEquals("X-Request-Id", ApiHeaderNames.REQUEST_ID);
assertEquals("X-Gateway-Client-Id", ApiHeaderNames.GATEWAY_CLIENT_ID);
assertEquals("X-Gateway-Credential-Id", ApiHeaderNames.GATEWAY_CREDENTIAL_ID);
assertEquals("Idempotency-Key", ApiHeaderNames.IDEMPOTENCY_KEY);
```

- [x] **Step 2: Run the test and verify RED**

Run: `./mvnw -pl paytech-common-core -Dtest=ApiHeaderNamesAutoTest test` from `paytech-foundation`.
Expected: compilation fails because `ApiHeaderNames` does not exist.

- [x] **Step 3: Add the minimal constant holder and migrate aliases/imports**

Use package `com.paytech.foundation.common.http`; keep a private constructor and no validation policies. Do not move Commerce regexes into Foundation.

- [x] **Step 4: Run Foundation and Gateway focused tests GREEN**

Run Foundation common-core test plus HTTP Problem Request ID tests, then compile/test the touched Gateway modules. Exact commands must be recorded with exit codes.

### Task 2: Establish One Trusted Header and Conversion Boundary

**Files:**
- Modify: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientCheckoutApiContractAutoTest.java`
- Modify: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1CommonContractAutoTest.java`
- Modify analogous Order/Refund API contract tests when their reflected signatures change.
- Rename: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/checkout/CheckoutWireMapper.java` to `CheckoutWireConverter.java`
- Modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/checkout/ClientCheckoutController.java`
- Modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/filter/ClientApiTrustedContextFilter.java`
- Modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/filter/ClientApiIdempotencyKeyFilter.java`
- Modify: all three Java API interfaces and `ClientApiConstraints.java`.

**Interfaces:**
- Trusted `clientId` and `credentialId` enter Java controller contracts as primitive `long` values; Request ID is infrastructure-owned and removed from business method parameters.
- `CheckoutWireConverter.toRequest(long clientId, CreateCheckoutRequestBody body)` does not receive or validate `Idempotency-Key`.
- Idempotency Key validation remains in exactly one Commerce Web ingress component.

- [x] **Step 1: Change reflection/HTTP contract expectations first**

Assert that Request ID is absent from Java method parameters, trusted identity parameters are `long`, Commerce no longer exposes header/ID regex duplicates, and the Checkout adapter uses `CheckoutWireConverter`.

- [x] **Step 2: Run focused Commerce contract tests and verify RED**

Run: `./mvnw -pl paytech-commerce-test -am -Dtest=ClientCheckoutApiContractAutoTest,ClientV1CommonContractAutoTest test` from `commerce-service`.
Expected: failures identify the old String signatures, local constants and Mapper name.

- [x] **Step 3: Apply the minimal production refactor**

Remove repeated Request ID and trusted-ID regex annotations, parse trusted IDs once through Spring type binding, remove dead request attributes and manual Converter parsing, retain resource-to-client authorization, and make the trusted context filter audit-only or otherwise single-purpose. Preserve one fail-closed ingress check for required/single trusted identity headers.

- [x] **Step 4: Run focused contract and database-independent HTTP tests GREEN**

Do not claim database-backed Checkout coverage if the local Commerce schema remains unavailable.

### Task 3: Replace Map Creators with Jackson and Bean Validation

**Files:**
- Modify: `commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/checkout/CheckoutModels.java`
- Modify: `commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/refund/RefundModels.java`
- Modify: Commerce Jackson configuration under `paytech-commerce-web/src/main/java/com/paytech/commerce/configuration/` or `src/main/resources/application.yml`, choosing the smallest verified Spring Boot 4/Jackson 3 integration.
- Modify: database-independent random-port HTTP validation tests under `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/v1/checkout/`.
- Remove only if no remaining consumer: Foundation request-body schema/validation exception additions and their special MVC mapping branches.

**Interfaces:**
- Request records use normal property-based Jackson construction.
- Unknown properties are ignored.
- JSON numbers/booleans do not coerce into String components.
- Existing Foundation MVC mapping owns standard Jackson parse/type errors.

- [x] **Step 1: Add HTTP RED cases through the real random-port endpoint**

Add legal unknown top-level and nested properties together with one deliberately invalid known field; assert the response identifies only the known field, proving unknown properties reached normal Bean Validation without entering the database. Add one numeric `unitAmount` case returning `400 INVALID_REQUEST`, and one blank/format case returning `422 VALIDATION_ERROR` with a safe external field path. These tests must not require MySQL because every request stops before business execution.

- [x] **Step 2: Run the focused HTTP cases and verify the expected RED failures**

Record which current behavior is wrong: unknown property rejection and/or scalar coercion acceptance.

- [x] **Step 3: Remove the Map creators and configure strict scalar binding**

Delete `requireOnly`, raw Map conversion helpers, `fromJson` creators and duplicated field-name sets. Retain compact constructors used for defensive copying and Bean Validation annotations. Jackson 3 record binding invokes `@JsonSetter(nulls = FAIL)` for an absent creator property as well, so preserve the approved “absent is allowed, explicit null is rejected” distinction through one tested reusable deserializer instead of repeating DTO-specific creators or setters.

- [x] **Step 4: Run the same HTTP cases GREEN and re-run Foundation RFC 9457 tests**

Confirm response media type, HTTP status, top-level code and safe field projection. Do not add a second DTO-specific exception hierarchy.

### Task 4: Complete Public JavaDoc and OpenAPI Descriptions

**Files:**
- Modify: all Java files under `commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/` that expose public API methods, records, record components or non-obvious enum semantics.
- Modify: `commerce-service/api/openapi/client-v1.yaml`.
- Modify: Java/OpenAPI drift tests only where they validate documentation presence semantically.
- Modify: the current design/implementation status documents only to reflect verified facts.

**Interfaces:**
- Java record component documentation uses record-level `@param` tags so generated JavaDoc associates each description with the public component.
- API methods use `@param`, `@return` and concise behavior/idempotency notes.
- OpenAPI operations, parameters, schemas and properties describe the same formats/defaults/nullability without changing types or required lists.

- [x] **Step 1: Add a documentation contract check where executable semantics exist**

Extend existing OpenAPI drift tests to detect missing operation/schema/property descriptions without grepping Java source. Human prose itself does not receive brittle exact-text tests.

- [x] **Step 2: Run the documentation/drift test and verify RED**

Expected: it reports currently undocumented operations/properties.

- [x] **Step 3: Add JavaDoc and OpenAPI descriptions**

Cover Checkout, Order, Refund, common models and enums consistently. Do not change schema, status values, requiredness or route metadata while documenting them.

- [x] **Step 4: Run contract tests and JavaDoc generation GREEN**

Run Maven JavaDoc generation for `paytech-commerce-client-api` with doclint enabled enough to catch malformed tags, then run all Commerce contract/OpenAPI drift tests.

### Task 5: Cross-Repository Verification and Truthful Status

**Files:**
- Modify as needed: `agent-docs/CURRENT_IMPLEMENTATION.md`, the two Commerce design/plan documents that mention `CheckoutWireMapper`, and the Client API contract-module design sections superseded by this approved addendum.

- [x] **Step 1: Search for stale names and duplicated policies**

Search production/current design sources for `CheckoutWireMapper`, `ClientApiHeaders`, `REQUEST_ID` regex duplication, `POSITIVE_DECIMAL_ID`, request-record `JsonCreator.Mode.DELEGATING`, and DTO field-name allowlists. Historical completed plans may retain code samples only when clearly historical; current authority must not contradict implementation.

- [x] **Step 2: Run proportional verification**

Run Foundation common-core/http-problem/mvc-web tests, touched Gateway tests, Commerce database-independent test set, Commerce API JavaDoc, PDManer/model checks when affected, and `npm --prefix requirements-site test`. Run database-backed HTTP/coverage only if the manually prepared local schema is present; otherwise report that existing environment blocker precisely.

- [x] **Step 3: Review all dirty diffs**

Separate pre-existing/user changes from this task, verify no SQL/deployment/route activation/commit occurred, and update current-state documentation only with commands actually run and results actually observed.
