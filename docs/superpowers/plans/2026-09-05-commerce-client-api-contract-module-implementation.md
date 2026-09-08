# Commerce Client API Contract Module Implementation Plan

> **Historical implementation plan:** 本文保留 2026-09-05 首次落地的代码样例和当时门禁作为历史证据。
> Header 常量、可信 ID/Request ID 参数、请求未知属性策略、Jackson Creator、依赖数量和公共文档要求已经由
> [`2026-09-08-commerce-client-api-boundary-simplification.md`](./2026-09-08-commerce-client-api-boundary-simplification.md)
> 取代；不得用下文旧样例恢复 `ClientApiHeaders`、String 身份 ID、Map Creator 或“请求拒绝未知属性”。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Build the non-running <code>com.paytech:paytech-commerce-client-api</code> JAR that projects the ten approved Hosted Checkout V1 operations into ordinary Spring MVC Java interfaces and records, while keeping the public OpenAPI contract authoritative.

**Architecture:** Add one publishable contract module between Commerce Web and its tests: <code>paytech-commerce-test -> paytech-commerce-web -> paytech-commerce-client-api</code>. The module exposes three <code>@HttpExchange</code> interfaces on the private Owner path, carries no runtime implementation, and is guarded by reflection, JSON, Bean Validation, architecture, dependency-purity, and OpenAPI-to-Java drift tests.

**Tech Stack:** Java 25, Maven 3.9.16 Wrapper, Spring Framework <code>spring-web</code> HTTP service annotations, Jakarta Validation, Jackson annotations/Jackson 3 test mapper, JUnit 5, SnakeYAML, Maven Enforcer.

**Spec:** <code>docs/superpowers/specs/2026-09-05-commerce-client-api-contract-module-design.md</code>

## Global Constraints

- The artifact is exactly <code>com.paytech:paytech-commerce-client-api</code>, packaging <code>jar</code>, with its version inherited from <code>paytech-commerce-service</code>.
- The only deployable Commerce application remains <code>paytech-commerce-web</code>; do not add a Main class, Spring Boot application, port, configuration file, discovery identity, or deployment resource.
- The public HTTP authority remains <code>commerce-service/api/openapi/client-v1.yaml</code> on <code>/api/v1/**</code>. Java interfaces use only the private base path <code>/external/client-api/v1</code>; the application context path <code>/commerce</code> is not part of the interface constant.
- Create exactly three interfaces and ten operations: two Checkout, three Order, and five Refund operations.
- Do not add <code>locale</code>, <code>industryCode</code>, or <code>mccCode</code>. Locale remains the platform default <code>en-US</code>, while industry/MCC remain Client-owned context outside this request.
- Every method explicitly accepts <code>X-Request-Id</code>, <code>X-Gateway-Client-Id</code>, and <code>X-Gateway-Credential-Id</code>. The three POST methods additionally accept <code>Idempotency-Key</code>.
- No method accepts Authorization, Credential Secret, Tenant, Scope, Route Code, Channel, Actor, Realm, Session, or a generic Header map.
- Create Checkout and Create Refund return typed <code>ResponseEntity</code> values for <code>201 + Location</code>. Cancel returns <code>ResponseEntity&lt;CancelOrderResult&gt;</code> for the definitive 200 or pending 202 result.
- DTOs are public records grouped in <code>ClientApiModels</code>, <code>CheckoutModels</code>, <code>OrderModels</code>, and <code>RefundModels</code>. They never enter Repository, Mapper, DataObject, Domain, or Application packages.
- Status, reason, and origin properties remain <code>String</code> in DTOs. Nine enums implement <code>CodeEnum</code> only as known-code catalogs, and adjacent enum constants have one blank line.
- Times use <code>Instant</code>. Amounts and quantities use decimal <code>String</code>; do not use <code>double</code>, <code>float</code>, <code>BigDecimal</code>, or JSON numbers in this wire projection.
- The non-negative decimal pattern allows at most 16 integer digits and 8 fractional digits. The positive pattern has the same precision and rejects every zero representation.
- Required-but-nullable response properties always serialize their JSON key. Optional non-null properties omit null during serialization; explicit request null is rejected.
- Request records reject unknown fields under the strict contract mapper. Response records accept unknown future fields. Unknown future status codes deserialize into their <code>String</code> fields.
- The API module has exactly four direct dependencies: <code>paytech-common-core</code>, <code>spring-web</code>, <code>jakarta.validation-api</code>, and <code>jackson-annotations</code>.
- Do not introduce <code>spring-webflux</code>, <code>io.projectreactor:*</code>, <code>Mono</code>, <code>Flux</code>, a reactive Controller, or a reactive return type.
- Do not add Controller, Processor, Service, Domain Service, Repository, Mapper, DataObject, Problem DTO, exception Advice, HTTP client factory, persistence, MQ, Redis, Nacos, Vault, or another system API dependency in this slice.
- Preserve every unrelated dirty-worktree change. In particular, do not edit <code>commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md</code>.
- Use RED-GREEN TDD for every behavior-bearing task. Do not weaken an assertion merely to make an unexpected result pass.
- The project does not authorize commits by default. Each task includes a precise optional commit boundary; execute those commands only after separate user authorization.

---

## File Structure

Create these production files:

~~~text
commerce-service/paytech-commerce-client-api/pom.xml
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/package-info.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/common/ClientApiHeaders.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/common/ClientApiPaths.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/common/ClientApiConstraints.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/common/ClientApiModels.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/checkout/ClientCheckoutApi.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/checkout/CheckoutModels.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/order/ClientOrderApi.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/order/OrderModels.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/refund/ClientRefundApi.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/refund/RefundModels.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/enums/CancellationReason.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/enums/CancellationStatus.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/enums/CheckoutStatus.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/enums/FundsAvailabilityStatus.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/enums/OrderStatus.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/enums/PaymentStatus.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/enums/RefundReason.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/enums/RefundRequestOrigin.java
commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/enums/RefundStatus.java
~~~

Create these test files:

~~~text
commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1ContractTestSupport.java
commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1CommonContractAutoTest.java
commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientCheckoutApiContractAutoTest.java
commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientOrderApiContractAutoTest.java
commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientRefundApiContractAutoTest.java
commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1OpenApiJavaDriftAutoTest.java
~~~

Modify these implementation and verification files:

~~~text
commerce-service/pom.xml
commerce-service/paytech-commerce-web/pom.xml
commerce-service/paytech-commerce-test/pom.xml
commerce-service/api/openapi/client-v1.yaml
commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceModuleBoundaryAutoTest.java
commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/architecture/CommerceArchitectureAutoTest.java
commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1OpenApiContractAutoTest.java
commerce-service/README.md
agent-docs/CURRENT_IMPLEMENTATION.md
agent-docs/DOCUMENT_GOVERNANCE.md
agent-docs/README.md
agent-docs/decisions/2026-09-05-commerce-client-api-contract-module.md
docs/superpowers/specs/2026-09-05-commerce-client-api-contract-module-design.md
~~~

<code>ClientV1ContractTestSupport</code> owns only test reflection/JSON helpers. It must not become production code. The three capability tests own their exact interface and model contracts; <code>ClientV1OpenApiJavaDriftAutoTest</code> owns cross-representation comparison; the existing module and architecture tests own dependency and source-layout policy.

## Preflight: Preserve the Starting State

- [ ] Run the root and Commerce status checks before editing.

~~~bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
git status --short

cd /Users/kenny/Desktop/Workspace/Code-MoR/commerce-service
git status --short
git branch --show-current
git rev-parse HEAD
shasum -a 256 docs/database/COMMERCE_PHYSICAL_DECISIONS.md
~~~

Expected Commerce evidence at plan creation: branch <code>main</code>, HEAD <code>74217ac</code>, and protected-file SHA-256 <code>3618a06163a16637399419779cb40bd908f3f611c77a47aa1e480139dcbaec12</code>. If execution starts from newer user work, record the newer values and preserve them; do not reset either repository.

- [ ] Run the unchanged baseline.

~~~bash
./mvnw clean verify
~~~

Expected: <code>BUILD SUCCESS</code>. If it fails before any task edit, report the failing test and keep that failure separate from this plan.

### Task 1: Add the Maven Contract-Module Boundary

**Files:**
- Create: <code>commerce-service/paytech-commerce-client-api/pom.xml</code>
- Modify: <code>commerce-service/pom.xml</code>
- Modify: <code>commerce-service/paytech-commerce-web/pom.xml</code>
- Modify: <code>commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceModuleBoundaryAutoTest.java</code>
- Test: <code>CommerceModuleBoundaryAutoTest</code>

**Interfaces:**
- Consumes: the existing Parent BOM and the existing <code>paytech-commerce-web</code>/<code>paytech-commerce-test</code> modules.
- Produces: dependency direction <code>test -> web -> client-api</code>, Maven module/build order <code>client-api -> web -> test</code>, a publishable ordinary API JAR, exactly four allowed API dependencies, and an enforced ban on Project Reactor.

- [ ] **Step 1: Change the module-boundary test to state the approved graph before creating the module.**

Replace the two-module assertion and add direct dependency/publishability assertions with this exact intent:

~~~java
assertEquals(
    List.of("paytech-commerce-client-api", "paytech-commerce-web", "paytech-commerce-test"),
    values(readPom(""), "/project/modules/module"));

assertEquals(Set.of(
    "com.paytech:paytech-common-core",
    "org.springframework:spring-web",
    "jakarta.validation:jakarta.validation-api",
    "com.fasterxml.jackson.core:jackson-annotations"
), directDependencies(readPom("paytech-commerce-client-api")).stream()
    .map(Dependency::coordinate)
    .collect(java.util.stream.Collectors.toUnmodifiableSet()));

assertTrue(directDependencies(readPom("paytech-commerce-web")).stream()
    .anyMatch(dependency -> dependency.coordinate()
        .equals("com.paytech:paytech-commerce-client-api")));
assertEquals(1, directDependencies(readPom("paytech-commerce-test")).stream()
    .filter(dependency -> dependency.coordinate().startsWith("com.paytech:"))
    .count());
assertTrue(values(readPom("paytech-commerce-client-api"),
    "/project/build/plugins/plugin/artifactId").stream()
    .noneMatch("spring-boot-maven-plugin"::equals));
assertTrue(values(readPom("paytech-commerce-client-api"),
    "/project/properties/maven.install.skip").isEmpty());
assertTrue(values(readPom("paytech-commerce-client-api"),
    "/project/properties/maven.deploy.skip").isEmpty());
~~~

Add <code>io.projectreactor:*</code> to <code>REQUIRED_BANS</code>. Keep the existing assertion that no future internal <code>paytech-commerce-api</code> exists; change its message so that it distinguishes that future internal module from the approved <code>paytech-commerce-client-api</code>.

- [ ] **Step 2: Run the focused test and observe RED.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=CommerceModuleBoundaryAutoTest test
~~~

Expected: FAIL because <code>paytech-commerce-client-api/pom.xml</code> and the three-module graph do not exist yet. A compilation failure caused by importing future API classes is not acceptable in this task; this test must remain POM/source-structure based.

- [ ] **Step 3: Create the minimal API POM.**

Create <code>paytech-commerce-client-api/pom.xml</code> with this complete dependency shape:

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.paytech</groupId>
        <artifactId>paytech-commerce-service</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
    <artifactId>paytech-commerce-client-api</artifactId>
    <name>Commerce Client API</name>
    <description>Non-running Commerce Hosted Checkout V1 owner contract.</description>
    <dependencies>
        <dependency>
            <groupId>com.paytech</groupId>
            <artifactId>paytech-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-web</artifactId>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-annotations</artifactId>
        </dependency>
    </dependencies>
</project>
~~~

- [ ] **Step 4: Wire the parent and Web POMs.**

In the parent:

~~~xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.paytech</groupId>
            <artifactId>paytech-commerce-client-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.paytech</groupId>
            <artifactId>paytech-commerce-web</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>

<modules>
    <module>paytech-commerce-client-api</module>
    <module>paytech-commerce-web</module>
    <module>paytech-commerce-test</module>
</modules>
~~~

Add this Enforcer exclusion beside the existing WebFlux exclusion:

~~~xml
<exclude>io.projectreactor:*</exclude>
~~~

Add this compile dependency to <code>paytech-commerce-web/pom.xml</code>:

~~~xml
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>paytech-commerce-client-api</artifactId>
</dependency>
~~~

- [ ] **Step 5: Run the module-boundary test GREEN and inspect the API dependency tree.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=CommerceModuleBoundaryAutoTest test
./mvnw -pl paytech-commerce-client-api dependency:tree -Dverbose
~~~

Expected: the test passes; the API tree contains the four declared direct dependencies, contains no <code>spring-webflux</code> or <code>io.projectreactor</code> artifact, and has no Commerce Web/Test dependency.

- [ ] **Step 6: Optional commit boundary, only if the user separately authorizes commits.**

~~~bash
git add pom.xml paytech-commerce-client-api/pom.xml paytech-commerce-web/pom.xml paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceModuleBoundaryAutoTest.java
git commit -m "build: add Commerce Client API contract module"
~~~

### Task 2: Close the Three Approved OpenAPI Wire Gaps

**Files:**
- Modify: <code>commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1OpenApiContractAutoTest.java</code>
- Modify: <code>commerce-service/api/openapi/client-v1.yaml</code>
- Test: <code>ClientV1OpenApiContractAutoTest</code>

**Interfaces:**
- Consumes: the existing nine Path objects/ten operations in <code>client-v1.yaml</code>.
- Produces: exact <code>DECIMAL(24,8)</code>-compatible text precision, constrained nullable <code>clientRefundId</code>, and a required positive delay-seconds <code>Retry-After</code> header on Cancel 202.

- [ ] **Step 1: Add failing assertions for all three gaps.**

Extend the existing tests with these exact checks:

~~~java
assertPattern(schemas, "DecimalAmount",
    List.of("0", "19.95", "9999999999999999.12345678"),
    List.of("00", "19\\.95", "1e2", "99999999999999999.1",
        "9999999999999999.123456789"));
assertPattern(schemas, "PositiveDecimalAmount",
    List.of("0.00000001", "19.95"),
    List.of("0", "0.00", "00.01", "1e2", "99999999999999999.1"));

Map<String, Object> clientRefundId = map(map(
    schemas.get("RefundResponse")).get("properties")).containsKey("clientRefundId")
        ? map(map(map(schemas.get("RefundResponse")).get("properties")).get("clientRefundId"))
        : Map.of();
List<Object> alternatives = list(clientRefundId.get("anyOf"));
assertEquals(2, alternatives.size());
assertEquals("#/components/schemas/ClientReference", map(alternatives.getFirst()).get("$ref"));
assertEquals("null", map(alternatives.get(1)).get("type"));

Map<String, Object> pending = response(map(map(map(document().get("paths"))
    .get("/api/v1/orders/{orderNo}/cancel")).get("post"))
    .get("responses") instanceof Map<?, ?> responses ? responses.get("202") : null);
Map<String, Object> retryAfter = map(map(pending.get("headers")).get("Retry-After"));
assertEquals(Boolean.TRUE, retryAfter.get("required"));
assertEquals("integer", map(retryAfter.get("schema")).get("type"));
assertEquals(1, map(retryAfter.get("schema")).get("minimum"));
~~~

Extract <code>assertNullableClientReference</code> and <code>assertRequiredRetryAfter</code> helpers from the shown assertions so the existing test remains within the project line-length rule; both helpers perform exactly the checks shown.

- [ ] **Step 2: Run the OpenAPI test and observe three RED causes.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientV1OpenApiContractAutoTest test
~~~

Expected: FAIL because a 17-digit integer is currently accepted, <code>RefundResponse.clientRefundId</code> currently has an unconstrained nullable string, and <code>CancellationPendingResponse</code> has no <code>Retry-After</code>.

- [ ] **Step 3: Apply the exact YAML corrections.**

Use these schemas:

~~~yaml
DecimalAmount:
  type: string
  pattern: '^(?:0|[1-9][0-9]{0,15})(?:[.][0-9]{1,8})?$'
PositiveDecimalAmount:
  type: string
  description: Positive decimal string; currency minor-unit validation occurs at the external boundary.
  pattern: '^(?=.*[1-9])(?:0|[1-9][0-9]{0,15})(?:[.][0-9]{1,8})?$'
~~~

Use this required-but-nullable Client Reference:

~~~yaml
clientRefundId:
  anyOf:
    - $ref: '#/components/schemas/ClientReference'
    - type: 'null'
~~~

Add this header to <code>components.responses.CancellationPendingResponse</code>:

~~~yaml
headers:
  Retry-After:
    description: Positive delay in seconds before polling the order again.
    required: true
    schema:
      type: integer
      minimum: 1
~~~

Do not change any Route, Scope, success status, request size, authentication scheme, or other schema in this task.

- [ ] **Step 4: Run the focused test GREEN and check YAML whitespace.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientV1OpenApiContractAutoTest test
git diff --check -- api/openapi/client-v1.yaml paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1OpenApiContractAutoTest.java
~~~

Expected: focused tests pass and <code>git diff --check</code> emits no output.

- [ ] **Step 5: Optional commit boundary, only if separately authorized.**

~~~bash
git add api/openapi/client-v1.yaml paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1OpenApiContractAutoTest.java
git commit -m "fix: close Commerce Client API wire gaps"
~~~

### Task 3: Publish Shared Constants, Amounts, and Known-Code Catalogs

**Files:**
- Create: <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/package-info.java</code>
- Create: <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/common/ClientApiHeaders.java</code>
- Create: <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/common/ClientApiPaths.java</code>
- Create: <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/common/ClientApiConstraints.java</code>
- Create: <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/common/ClientApiModels.java</code>
- Create: the nine files under <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/enums/</code>
- Create: <code>commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1ContractTestSupport.java</code>
- Create: <code>commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1CommonContractAutoTest.java</code>
- Modify: <code>commerce-service/paytech-commerce-test/pom.xml</code>
- Test: <code>ClientV1CommonContractAutoTest</code>

**Interfaces:**
- Consumes: <code>CodeEnum</code>, Jackson annotations, Jakarta Validation, and the corrected OpenAPI primitives.
- Produces: four protocol header constants, one private base path, reusable validation expressions, <code>ClientApiModels.Amounts</code>, nine non-wire enum catalogs, and focused test helpers used by Tasks 4–7.

- [ ] **Step 1: Add the validation provider used by real Bean Validation assertions.**

Add this test-scoped dependency to <code>paytech-commerce-test/pom.xml</code>:

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
    <scope>test</scope>
</dependency>
~~~

It is test infrastructure only; do not add it to the API POM or Web POM.

- [ ] **Step 2: Create the shared test support and the failing common-contract test.**

<code>ClientV1ContractTestSupport</code> must provide these package-private operations:

~~~java
static Map<String, String> routes(Class<?>... apiTypes);
static Set<String> requestHeaders(Method method);
static void assertRecord(Class<?> type, String... nameAndSimpleType);
static void assertGenericReturn(Method method, Class<?> rawType, Class<?> argumentType);
static Validator validator();
static ObjectMapper strictJson();
static Set<String> codes(Class<? extends Enum<?>> enumType);
~~~

Implement <code>routes</code> by reading each declared method's <code>@GetExchange</code> or <code>@PostExchange</code> annotation and returning keys such as <code>GET /orders/{orderNo}</code>. Implement <code>requestHeaders</code> by reading <code>@RequestHeader.name/value</code>. Implement <code>assertRecord</code> by comparing record-component order and <code>getType().getSimpleName()</code>. Build <code>strictJson</code> from the project Jackson 3 mapper with <code>FAIL_ON_UNKNOWN_PROPERTIES</code> and <code>FAIL_ON_MISSING_CREATOR_PROPERTIES</code> enabled and Java Time modules discovered. Implement <code>codes</code> by casting each constant to <code>CodeEnum</code> and calling <code>getCode()</code>.

The failing common test must assert:

~~~java
assertEquals(Set.of(
    "REQUEST_ID", "GATEWAY_CLIENT_ID", "GATEWAY_CREDENTIAL_ID", "IDEMPOTENCY_KEY"
), Arrays.stream(ClientApiHeaders.class.getDeclaredFields())
    .filter(field -> Modifier.isPublic(field.getModifiers()))
    .map(Field::getName)
    .collect(Collectors.toUnmodifiableSet()));
assertEquals("X-Request-Id", ClientApiHeaders.REQUEST_ID);
assertEquals("X-Gateway-Client-Id", ClientApiHeaders.GATEWAY_CLIENT_ID);
assertEquals("X-Gateway-Credential-Id", ClientApiHeaders.GATEWAY_CREDENTIAL_ID);
assertEquals("Idempotency-Key", ClientApiHeaders.IDEMPOTENCY_KEY);
assertEquals("/external/client-api/v1", ClientApiPaths.V1);
assertFalse(ClientApiPaths.V1.startsWith("/api/"));
assertFalse(ClientApiPaths.V1.startsWith("/commerce/"));

assertRecord(Amounts.class,
    "merchandiseAmount:String", "discountAmount:String", "netAmount:String",
    "taxAmount:String", "roundingAmount:String", "totalAmount:String", "paidAmount:String");
assertEquals(Set.of("CREATED", "OPEN", "PAID", "CANCELLED", "EXPIRED"),
    codes(OrderStatus.class));
assertEquals(Set.of("PREPARING", "OPEN", "PAYMENT_PROCESSING", "COMPLETED", "CANCELLED", "EXPIRED"),
    codes(CheckoutStatus.class));
assertEquals(Set.of("NOT_STARTED", "PROCESSING", "SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED"),
    codes(PaymentStatus.class));
assertEquals(Set.of("PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "CANCELLED"),
    codes(RefundStatus.class));
assertEquals(Set.of("NOT_APPLICABLE", "PENDING", "AVAILABLE", "FULLY_OFFSET"),
    codes(FundsAvailabilityStatus.class));
assertEquals(Set.of("PENDING"), codes(CancellationStatus.class));
assertEquals(Set.of("PAYMENT_IN_PROGRESS"), codes(CancellationReason.class));
assertEquals(Set.of("REQUESTED_BY_CUSTOMER", "DUPLICATE", "FRAUDULENT", "OTHER"),
    codes(RefundReason.class));
assertEquals(Set.of("CLIENT", "OPERATIONS"), codes(RefundRequestOrigin.class));
~~~

Also validate one accepted <code>Amounts</code> and rejected samples <code>00</code>, <code>1e2</code>, <code>99999999999999999.1</code>, and nine fractional digits. Serialize an <code>Amounts</code> with <code>paidAmount == null</code> and assert the key is absent.

- [ ] **Step 3: Run the common test and observe RED.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientV1CommonContractAutoTest test
~~~

Expected: test compilation fails because the common contract types do not yet exist.

- [ ] **Step 4: Create constants and the shared record.**

Use these exact constant values:

~~~java
public final class ClientApiHeaders {
    public static final String REQUEST_ID = "X-Request-Id";
    public static final String GATEWAY_CLIENT_ID = "X-Gateway-Client-Id";
    public static final String GATEWAY_CREDENTIAL_ID = "X-Gateway-Credential-Id";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private ClientApiHeaders() {
    }
}

public final class ClientApiPaths {
    public static final String V1 = "/external/client-api/v1";
    private ClientApiPaths() {
    }
}

public final class ClientApiConstraints {
    public static final String REQUEST_ID = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$";
    public static final String POSITIVE_DECIMAL_ID = "^[1-9][0-9]{0,18}$";
    public static final String IDEMPOTENCY_KEY = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,254}$";
    public static final String CLIENT_REFERENCE = "^[!-~]+$";
    public static final String CURRENCY = "^[A-Z]{3}$";
    public static final String COUNTRY = "^[A-Z]{2}$";
    public static final String DECIMAL = "^(?:0|[1-9][0-9]{0,15})(?:[.][0-9]{1,8})?$";
    public static final String POSITIVE_DECIMAL =
        "^(?=.*[1-9])(?:0|[1-9][0-9]{0,15})(?:[.][0-9]{1,8})?$";
    private ClientApiConstraints() {
    }
}
~~~

Create <code>ClientApiModels</code> as a final container with a private constructor and this record:

~~~java
@JsonIgnoreProperties(ignoreUnknown = true)
public record Amounts(
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Pattern(regexp = ClientApiConstraints.DECIMAL) String merchandiseAmount,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Pattern(regexp = ClientApiConstraints.DECIMAL) String discountAmount,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Pattern(regexp = ClientApiConstraints.DECIMAL) String netAmount,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Pattern(regexp = ClientApiConstraints.DECIMAL) String taxAmount,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Pattern(regexp = ClientApiConstraints.DECIMAL) String roundingAmount,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Pattern(regexp = ClientApiConstraints.DECIMAL) String totalAmount,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Pattern(regexp = ClientApiConstraints.DECIMAL) String paidAmount) {
}
~~~

The root <code>package-info.java</code> must state that this package is a non-running, server-side projection of the public Commerce Client API, not an external SDK or an internal service API.

- [ ] **Step 5: Create all nine CodeEnum catalogs with exact codes.**

Create each file with its own explicit constructor and getter:

~~~java
public enum OrderStatus implements CodeEnum {
    CREATED("CREATED"),

    OPEN("OPEN"),

    PAID("PAID"),

    CANCELLED("CANCELLED"),

    EXPIRED("EXPIRED");

    private final String code;

    OrderStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}

public enum CheckoutStatus implements CodeEnum {
    PREPARING("PREPARING"),

    OPEN("OPEN"),

    PAYMENT_PROCESSING("PAYMENT_PROCESSING"),

    COMPLETED("COMPLETED"),

    CANCELLED("CANCELLED"),

    EXPIRED("EXPIRED");

    private final String code;

    CheckoutStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}

public enum PaymentStatus implements CodeEnum {
    NOT_STARTED("NOT_STARTED"),

    PROCESSING("PROCESSING"),

    SUCCEEDED("SUCCEEDED"),

    FAILED("FAILED"),

    CANCELLED("CANCELLED"),

    EXPIRED("EXPIRED");

    private final String code;

    PaymentStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}

public enum RefundStatus implements CodeEnum {
    PENDING("PENDING"),

    PROCESSING("PROCESSING"),

    SUCCEEDED("SUCCEEDED"),

    FAILED("FAILED"),

    CANCELLED("CANCELLED");

    private final String code;

    RefundStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}

public enum FundsAvailabilityStatus implements CodeEnum {
    NOT_APPLICABLE("NOT_APPLICABLE"),

    PENDING("PENDING"),

    AVAILABLE("AVAILABLE"),

    FULLY_OFFSET("FULLY_OFFSET");

    private final String code;

    FundsAvailabilityStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}

public enum CancellationStatus implements CodeEnum {
    PENDING("PENDING");

    private final String code;

    CancellationStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}

public enum CancellationReason implements CodeEnum {
    PAYMENT_IN_PROGRESS("PAYMENT_IN_PROGRESS");

    private final String code;

    CancellationReason(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}

public enum RefundReason implements CodeEnum {
    REQUESTED_BY_CUSTOMER("REQUESTED_BY_CUSTOMER"),

    DUPLICATE("DUPLICATE"),

    FRAUDULENT("FRAUDULENT"),

    OTHER("OTHER");

    private final String code;

    RefundReason(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}

public enum RefundRequestOrigin implements CodeEnum {
    CLIENT("CLIENT"),

    OPERATIONS("OPERATIONS");

    private final String code;

    RefundRequestOrigin(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
~~~

Each declaration belongs in the same-named Java file with package <code>com.paytech.commerce.client.api.v1.enums</code> and an import of <code>com.paytech.foundation.common.enums.CodeEnum</code>. Do not add parse helpers or use these enums as DTO component types.

- [ ] **Step 6: Run the common contract GREEN.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientV1CommonContractAutoTest test
~~~

Expected: constants, decimal validation, <code>Amounts</code> JSON behavior, and all nine code sets pass.

- [ ] **Step 7: Optional commit boundary, only if separately authorized.**

~~~bash
git add paytech-commerce-client-api/src/main/java paytech-commerce-test/pom.xml paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1ContractTestSupport.java paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1CommonContractAutoTest.java
git commit -m "feat: add Commerce Client API shared contracts"
~~~

### Task 4: Add the Checkout Interface and Models

**Files:**
- Create: <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/checkout/ClientCheckoutApi.java</code>
- Create: <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/checkout/CheckoutModels.java</code>
- Create: <code>commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientCheckoutApiContractAutoTest.java</code>
- Test: <code>ClientCheckoutApiContractAutoTest</code>

**Interfaces:**
- Consumes: <code>ClientApiHeaders</code>, <code>ClientApiPaths.V1</code>, <code>ClientApiConstraints</code>, and <code>ClientApiModels.Amounts</code>.
- Produces: <code>ClientCheckoutApi.createCheckout</code>, <code>ClientCheckoutApi.getCheckout</code>, and four Checkout records.

- [ ] **Step 1: Write the failing Checkout reflection, validation, and JSON tests.**

Assert the complete route map and headers:

~~~java
assertEquals(Map.of(
    "POST /checkouts", "createCheckout",
    "GET /orders/{orderNo}/checkout", "getCheckout"
), routes(ClientCheckoutApi.class));

assertEquals(Set.of(
    "X-Request-Id", "X-Gateway-Client-Id", "X-Gateway-Credential-Id", "Idempotency-Key"
), requestHeaders(ClientCheckoutApi.class.getMethod("createCheckout",
    String.class, String.class, String.class, String.class,
    CreateCheckoutRequestBody.class)));
assertEquals(Set.of(
    "X-Request-Id", "X-Gateway-Client-Id", "X-Gateway-Credential-Id"
), requestHeaders(ClientCheckoutApi.class.getMethod("getCheckout",
    String.class, String.class, String.class, String.class)));
~~~

Assert <code>@HttpExchange.value == ClientApiPaths.V1</code>, the POST body has both <code>@RequestBody</code> and <code>@Valid</code>, Create returns <code>ResponseEntity&lt;CreateCheckoutResponseBody&gt;</code>, and Get returns <code>CheckoutResponseBody</code>. Assert these exact record shapes:

~~~java
assertRecord(CreateCheckoutRequestBody.class,
    "clientOrderId:String", "currency:String", "consumerCountryCode:String",
    "expiresAt:Instant", "items:List");
assertRecord(CheckoutItemRequest.class,
    "clientItemId:String", "name:String", "description:String", "productTypeCode:String",
    "taxCategoryCode:String", "quantity:String", "unitAmount:String", "discountAmount:String");
assertRecord(CreateCheckoutResponseBody.class,
    "orderNo:String", "clientOrderId:String", "checkoutUrl:String",
    "currency:String", "amounts:Amounts", "expiresAt:Instant");
assertRecord(CheckoutResponseBody.class,
    "orderNo:String", "clientOrderId:String", "checkoutStatus:String",
    "checkoutUrl:String", "expiresAt:Instant", "updatedAt:Instant", "version:int");
~~~

Use the strict mapper and Validator to prove:

- a request with one valid item is accepted;
- empty items and 101 items are rejected;
- 17 integer digits, 9 fractional digits, exponent notation, and zero quantity are rejected;
- missing optional <code>expiresAt</code>, <code>description</code>, and <code>discountAmount</code> are accepted and omitted on serialization;
- explicit JSON null for those three optional request fields is rejected;
- an unknown request property is rejected;
- <code>checkoutUrl: null</code> remains present in <code>CheckoutResponseBody</code>;
- <code>checkoutStatus: "FUTURE_STATUS"</code> deserializes as the same String;
- an unknown response property is ignored.
- complete serialize/deserialize round trips preserve every component of <code>CreateCheckoutRequestBody</code>, <code>CreateCheckoutResponseBody</code>, and <code>CheckoutResponseBody</code>, including <code>Instant</code> values and nested items/amounts.

- [ ] **Step 2: Run the Checkout test and observe RED.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientCheckoutApiContractAutoTest test
~~~

Expected: test compilation fails because <code>ClientCheckoutApi</code> and <code>CheckoutModels</code> do not exist.

- [ ] **Step 3: Create the exact Checkout interface.**

Use ordinary Spring MVC HTTP service annotations and these complete signatures:

~~~java
@HttpExchange(value = ClientApiPaths.V1, accept = MediaType.APPLICATION_JSON_VALUE)
public interface ClientCheckoutApi {

    @PostExchange(value = "/checkouts", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CreateCheckoutResponseBody> createCheckout(
        @RequestHeader(ClientApiHeaders.REQUEST_ID)
        @NotBlank @Size(max = 64) @Pattern(regexp = ClientApiConstraints.REQUEST_ID) String requestId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CLIENT_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String clientId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CREDENTIAL_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String credentialId,
        @RequestHeader(ClientApiHeaders.IDEMPOTENCY_KEY)
        @NotBlank @Size(max = 255) @Pattern(regexp = ClientApiConstraints.IDEMPOTENCY_KEY)
        String idempotencyKey,
        @RequestBody @NotNull @Valid CreateCheckoutRequestBody requestBody);

    @GetExchange("/orders/{orderNo}/checkout")
    CheckoutResponseBody getCheckout(
        @RequestHeader(ClientApiHeaders.REQUEST_ID)
        @NotBlank @Size(max = 64) @Pattern(regexp = ClientApiConstraints.REQUEST_ID) String requestId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CLIENT_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String clientId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CREDENTIAL_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String credentialId,
        @PathVariable("orderNo") @NotBlank @Size(max = 64) String orderNo);
}
~~~

Do not add default implementations, <code>Mono</code>, <code>Flux</code>, or an Authorization parameter.

- [ ] **Step 4: Create the exact Checkout record shapes.**

Use a final <code>CheckoutModels</code> container with a private constructor. Request types carry <code>@JsonIgnoreProperties(ignoreUnknown = false)</code>; response types carry <code>@JsonIgnoreProperties(ignoreUnknown = true)</code>. Mark every OpenAPI-required request and response component with <code>@JsonProperty(isRequired = OptBoolean.TRUE)</code>. Apply these component constraints:

~~~java
public record CreateCheckoutRequestBody(
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Size(max = 128) @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
    String clientOrderId,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Pattern(regexp = ClientApiConstraints.CURRENCY) String currency,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Pattern(regexp = ClientApiConstraints.COUNTRY) String consumerCountryCode,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonSetter(nulls = Nulls.FAIL) Instant expiresAt,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull @Size(min = 1, max = 100)
    List<@NotNull @Valid CheckoutItemRequest> items) {
    public CreateCheckoutRequestBody {
        items = items == null ? null : List.copyOf(items);
    }
}

public record CheckoutItemRequest(
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Size(max = 128) @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
    String clientItemId,
    @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 255) String name,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonSetter(nulls = Nulls.FAIL)
    @Size(max = 1024) String description,
    @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 64) String productTypeCode,
    @JsonProperty(isRequired = OptBoolean.TRUE) @NotBlank @Size(max = 64) String taxCategoryCode,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL) String quantity,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Pattern(regexp = ClientApiConstraints.DECIMAL) String unitAmount,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonSetter(nulls = Nulls.FAIL)
    @Pattern(regexp = ClientApiConstraints.DECIMAL) String discountAmount) {
}
~~~

Document <code>CreateCheckoutRequestBody.expiresAt</code> as optional: a null/missing value tells the later Commerce Web/business implementation to calculate the approved 30-minute default; the contract record itself must not call a clock or materialize that default.

Create response records with exactly the shapes asserted in Step 1. Use <code>String</code> for both URL fields, <code>Instant</code> for times, <code>String</code> for <code>checkoutStatus</code>, and <code>int</code> for version. On <code>CheckoutResponseBody.checkoutUrl</code>, combine required-property metadata with <code>@JsonInclude(ALWAYS)</code> so a null value emits the key. Use <code>@NotNull @Valid</code> on <code>amounts</code>, <code>@NotNull</code> on every other required object component, and <code>@PositiveOrZero</code> on version.

- [ ] **Step 5: Run the Checkout test GREEN.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientCheckoutApiContractAutoTest test
~~~

Expected: two exact ordinary MVC signatures, request validation, optional-null rejection, nullable response preservation, and future status compatibility pass.

- [ ] **Step 6: Optional commit boundary, only if separately authorized.**

~~~bash
git add paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/checkout paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientCheckoutApiContractAutoTest.java
git commit -m "feat: add Commerce checkout contract"
~~~

### Task 5: Add Order Query and Cancel Contracts

**Files:**
- Create: <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/order/ClientOrderApi.java</code>
- Create: <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/order/OrderModels.java</code>
- Create: <code>commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientOrderApiContractAutoTest.java</code>
- Test: <code>ClientOrderApiContractAutoTest</code>

**Interfaces:**
- Consumes: common constants/models and Checkout status codes.
- Produces: two exact Order queries, one bodyless Cancel command, the complete Order projection, and the sealed two-result Cancel union.

- [ ] **Step 1: Write the failing Order contract test.**

Assert this exact route map:

~~~java
assertEquals(Map.of(
    "GET /orders/{orderNo}", "getOrder",
    "GET /orders", "getOrderByClientOrderId",
    "POST /orders/{orderNo}/cancel", "cancelOrder"
), routes(ClientOrderApi.class));
~~~

Assert all three trusted headers on all methods, <code>Idempotency-Key</code> only on <code>cancelOrder</code>, no <code>@RequestBody</code> parameter on Cancel, <code>@RequestParam("clientOrderId")</code> on the exact query, and <code>ResponseEntity&lt;CancelOrderResult&gt;</code> as the Cancel return type.

Assert these exact record shapes:

~~~java
assertRecord(OrderResponseBody.class,
    "orderNo:String", "clientOrderId:String", "orderStatus:String", "checkoutStatus:String",
    "paymentStatus:String", "currency:String", "consumerCountryCode:String", "items:List",
    "amounts:Amounts", "lastPaymentAttempt:LastPaymentAttempt", "refundSummary:RefundSummary",
    "fundsAvailability:FundsAvailability", "createdAt:Instant", "expiresAt:Instant",
    "paidAt:Instant", "updatedAt:Instant", "version:int");
assertRecord(OrderItem.class,
    "clientItemId:String", "name:String", "description:String", "productTypeCode:String",
    "taxCategoryCode:String", "quantity:String", "unitAmount:String", "discountAmount:String");
assertRecord(LastPaymentAttempt.class, "status:String");
assertRecord(RefundSummary.class,
    "processingAmount:String", "succeededAmount:String", "refundableAmount:String");
assertRecord(FundsAvailability.class,
    "status:String", "availableAmount:String", "scheduledAt:Instant", "availableAt:Instant",
    "asOf:Instant", "updatedAt:Instant", "version:int");
assertRecord(CancelOrderResponseBody.class,
    "orderNo:String", "orderStatus:String", "checkoutStatus:String", "cancelledAt:Instant", "version:int");
assertRecord(CancellationPendingResponseBody.class,
    "orderNo:String", "cancellationStatus:String", "reasonCode:String");
~~~

Also assert:

~~~java
assertTrue(CancelOrderResult.class.isSealed());
assertEquals(Set.of(CancelOrderResponseBody.class, CancellationPendingResponseBody.class),
    Set.of(CancelOrderResult.class.getPermittedSubclasses()));
~~~

Serialize each Cancel subtype through <code>CancelOrderResult</code> and assert no type discriminator field is emitted. Deserialize <code>orderStatus: "SETTLED_IN_FUTURE"</code>, <code>paymentStatus: "FUTURE_PAYMENT_STATUS"</code>, and <code>fundsAvailability.status: "FUTURE_FUNDS_STATUS"</code> as Strings. Prove <code>availableAmount</code>, <code>scheduledAt</code>, and <code>availableAt</code> remain present when null, while optional <code>lastPaymentAttempt</code> and <code>paidAt</code> are omitted when null.

Round-trip a fully populated <code>OrderResponseBody</code>, both Cancel result records, and an Order response with all permitted nullable values. Assert every record component and <code>Instant</code> survives unchanged.

- [ ] **Step 2: Run the Order test and observe RED.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientOrderApiContractAutoTest test
~~~

Expected: test compilation fails because the Order API and models do not exist.

- [ ] **Step 3: Create the exact Order interface.**

Use these complete signatures:

~~~java
@HttpExchange(value = ClientApiPaths.V1, accept = MediaType.APPLICATION_JSON_VALUE)
public interface ClientOrderApi {

    @GetExchange("/orders/{orderNo}")
    OrderResponseBody getOrder(
        @RequestHeader(ClientApiHeaders.REQUEST_ID)
        @NotBlank @Size(max = 64) @Pattern(regexp = ClientApiConstraints.REQUEST_ID) String requestId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CLIENT_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String clientId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CREDENTIAL_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String credentialId,
        @PathVariable("orderNo") @NotBlank @Size(max = 64) String orderNo);

    @GetExchange("/orders")
    OrderResponseBody getOrderByClientOrderId(
        @RequestHeader(ClientApiHeaders.REQUEST_ID)
        @NotBlank @Size(max = 64) @Pattern(regexp = ClientApiConstraints.REQUEST_ID) String requestId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CLIENT_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String clientId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CREDENTIAL_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String credentialId,
        @RequestParam("clientOrderId")
        @NotBlank @Size(max = 128) @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
        String clientOrderId);

    @PostExchange("/orders/{orderNo}/cancel")
    ResponseEntity<CancelOrderResult> cancelOrder(
        @RequestHeader(ClientApiHeaders.REQUEST_ID)
        @NotBlank @Size(max = 64) @Pattern(regexp = ClientApiConstraints.REQUEST_ID) String requestId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CLIENT_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String clientId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CREDENTIAL_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String credentialId,
        @PathVariable("orderNo") @NotBlank @Size(max = 64) String orderNo,
        @RequestHeader(ClientApiHeaders.IDEMPOTENCY_KEY)
        @NotBlank @Size(max = 255) @Pattern(regexp = ClientApiConstraints.IDEMPOTENCY_KEY)
        String idempotencyKey);
}
~~~

There is no Cancel body and no expected-version parameter.

- [ ] **Step 4: Create the complete Order model container.**

Create every record listed in Step 1 with <code>@JsonIgnoreProperties(ignoreUnknown = true)</code>. Mark every OpenAPI-required component with <code>@JsonProperty(isRequired = OptBoolean.TRUE)</code>. Apply decimal constraints to every amount/quantity String, currency/country constraints to those fields, <code>@NotNull @Valid</code> to nested required objects/list elements, defensive <code>List.copyOf</code> to <code>items</code>, and <code>@PositiveOrZero</code> to versions.

Define the Cancel union exactly as:

~~~java
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
    @JsonSubTypes.Type(CancelOrderResponseBody.class),
    @JsonSubTypes.Type(CancellationPendingResponseBody.class)
})
public sealed interface CancelOrderResult
    permits CancelOrderResponseBody, CancellationPendingResponseBody {
}
~~~

Both Cancel records implement <code>CancelOrderResult</code>. Keep all status/reason components typed as <code>String</code>.

For null behavior, use:

~~~java
@JsonInclude(JsonInclude.Include.NON_NULL)
LastPaymentAttempt lastPaymentAttempt

@JsonInclude(JsonInclude.Include.NON_NULL)
Instant paidAt

@JsonProperty(isRequired = OptBoolean.TRUE)
@JsonInclude(JsonInclude.Include.ALWAYS)
@Pattern(regexp = ClientApiConstraints.DECIMAL)
String availableAmount

@JsonProperty(isRequired = OptBoolean.TRUE)
@JsonInclude(JsonInclude.Include.ALWAYS)
Instant scheduledAt

@JsonProperty(isRequired = OptBoolean.TRUE)
@JsonInclude(JsonInclude.Include.ALWAYS)
Instant availableAt
~~~

Do not create a superset Cancel DTO, an Object return, a Map return, or a JSON discriminator.

- [ ] **Step 5: Run the Order test GREEN.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientOrderApiContractAutoTest test
~~~

Expected: three routes, bodyless Cancel, generic return types, record shapes, sealed subtype deduction, nullable/optional behavior, and future String statuses pass.

- [ ] **Step 6: Optional commit boundary, only if separately authorized.**

~~~bash
git add paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/order paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientOrderApiContractAutoTest.java
git commit -m "feat: add Commerce order contract"
~~~

### Task 6: Add Refund Contracts

**Files:**
- Create: <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/refund/ClientRefundApi.java</code>
- Create: <code>commerce-service/paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/refund/RefundModels.java</code>
- Create: <code>commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientRefundApiContractAutoTest.java</code>
- Test: <code>ClientRefundApiContractAutoTest</code>

**Interfaces:**
- Consumes: common constants and the corrected nullable Client Reference schema.
- Produces: five Refund operations and four Refund response/request projections.

- [ ] **Step 1: Write the failing Refund contract test.**

Assert the exact route map:

~~~java
assertEquals(Map.of(
    "GET /orders/{orderNo}/refundable-amount", "getRefundableAmount",
    "POST /orders/{orderNo}/refunds", "createRefund",
    "GET /orders/{orderNo}/refunds", "listOrderRefunds",
    "GET /refunds/{refundRequestNo}", "getRefund",
    "GET /refunds", "getRefundByClientRefundId"
), routes(ClientRefundApi.class));
~~~

Assert all three trusted headers on all methods, <code>Idempotency-Key</code> only on <code>createRefund</code>, <code>@Valid @RequestBody</code> only there, <code>ResponseEntity&lt;CreateRefundResponseBody&gt;</code> on create, and direct DTO return types on the four queries. Assert <code>limit</code> uses <code>defaultValue = "20"</code>, <code>@Min(1)</code>, and <code>@Max(100)</code>; <code>cursor</code> is optional with <code>@Size(max = 512)</code>.

Assert these exact records:

~~~java
assertRecord(CreateRefundRequestBody.class,
    "clientRefundId:String", "amount:String", "reasonCode:String", "reasonSummary:String");
assertRecord(RefundableAmountResponseBody.class,
    "orderNo:String", "currency:String", "refundableAmount:String", "asOf:Instant", "version:int");
assertRecord(CreateRefundResponseBody.class,
    "refundRequestNo:String", "clientRefundId:String", "orderNo:String",
    "amount:String", "currency:String", "createdAt:Instant");
assertRecord(RefundResponseBody.class,
    "refundRequestNo:String", "clientRefundId:String", "requestOrigin:String", "orderNo:String",
    "amount:String", "currency:String", "refundStatus:String", "reasonCode:String",
    "statusReasonCode:String", "createdAt:Instant", "completedAt:Instant",
    "updatedAt:Instant", "version:int");
assertRecord(RefundListResponseBody.class, "items:List", "nextCursor:String");
~~~

Use Validator/JSON tests to prove positive amount, 128-character printable Client reference, exact four create reason codes, optional <code>reasonSummary</code> omission and explicit-null rejection, request unknown-field rejection, response unknown-field acceptance, future response status/origin Strings, and required null keys for <code>clientRefundId</code>, <code>statusReasonCode</code>, <code>completedAt</code>, and <code>nextCursor</code>.

Round-trip one complete instance of every Refund request/response record and one nullable Refund/list response. Assert all components, nested list entries, and <code>Instant</code> values survive unchanged.

- [ ] **Step 2: Run the Refund test and observe RED.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientRefundApiContractAutoTest test
~~~

Expected: test compilation fails because the Refund API and models do not exist.

- [ ] **Step 3: Create the complete Refund interface.**

Use these complete signatures:

~~~java
@HttpExchange(value = ClientApiPaths.V1, accept = MediaType.APPLICATION_JSON_VALUE)
public interface ClientRefundApi {

    @GetExchange("/orders/{orderNo}/refundable-amount")
    RefundableAmountResponseBody getRefundableAmount(
        @RequestHeader(ClientApiHeaders.REQUEST_ID)
        @NotBlank @Size(max = 64) @Pattern(regexp = ClientApiConstraints.REQUEST_ID) String requestId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CLIENT_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String clientId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CREDENTIAL_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String credentialId,
        @PathVariable("orderNo") @NotBlank @Size(max = 64) String orderNo);

    @PostExchange(value = "/orders/{orderNo}/refunds",
        contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CreateRefundResponseBody> createRefund(
        @RequestHeader(ClientApiHeaders.REQUEST_ID)
        @NotBlank @Size(max = 64) @Pattern(regexp = ClientApiConstraints.REQUEST_ID) String requestId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CLIENT_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String clientId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CREDENTIAL_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String credentialId,
        @PathVariable("orderNo") @NotBlank @Size(max = 64) String orderNo,
        @RequestHeader(ClientApiHeaders.IDEMPOTENCY_KEY)
        @NotBlank @Size(max = 255) @Pattern(regexp = ClientApiConstraints.IDEMPOTENCY_KEY)
        String idempotencyKey,
        @RequestBody @NotNull @Valid CreateRefundRequestBody requestBody);

    @GetExchange("/orders/{orderNo}/refunds")
    RefundListResponseBody listOrderRefunds(
        @RequestHeader(ClientApiHeaders.REQUEST_ID)
        @NotBlank @Size(max = 64) @Pattern(regexp = ClientApiConstraints.REQUEST_ID) String requestId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CLIENT_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String clientId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CREDENTIAL_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String credentialId,
        @PathVariable("orderNo") @NotBlank @Size(max = 64) String orderNo,
        @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(100) int limit,
        @RequestParam(name = "cursor", required = false) @Size(max = 512) String cursor);

    @GetExchange("/refunds/{refundRequestNo}")
    RefundResponseBody getRefund(
        @RequestHeader(ClientApiHeaders.REQUEST_ID)
        @NotBlank @Size(max = 64) @Pattern(regexp = ClientApiConstraints.REQUEST_ID) String requestId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CLIENT_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String clientId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CREDENTIAL_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String credentialId,
        @PathVariable("refundRequestNo") @NotBlank @Size(max = 64) String refundRequestNo);

    @GetExchange("/refunds")
    RefundResponseBody getRefundByClientRefundId(
        @RequestHeader(ClientApiHeaders.REQUEST_ID)
        @NotBlank @Size(max = 64) @Pattern(regexp = ClientApiConstraints.REQUEST_ID) String requestId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CLIENT_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String clientId,
        @RequestHeader(ClientApiHeaders.GATEWAY_CREDENTIAL_ID)
        @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL_ID) String credentialId,
        @RequestParam("clientRefundId")
        @NotBlank @Size(max = 128) @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
        String clientRefundId);
}
~~~

The only POST is <code>createRefund</code>.

- [ ] **Step 4: Create the complete Refund model container.**

Create a final <code>RefundModels</code> container with a private constructor. Requests reject unknown fields and responses accept unknown fields. Every OpenAPI-required request and response component uses <code>@JsonProperty(isRequired = OptBoolean.TRUE)</code>. Use this exact create-request contract:

~~~java
public record CreateRefundRequestBody(
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Size(max = 128) @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
    String clientRefundId,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL) String amount,
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Pattern(regexp = "^(REQUESTED_BY_CUSTOMER|DUPLICATE|FRAUDULENT|OTHER)$")
    String reasonCode,
    @JsonInclude(JsonInclude.Include.NON_NULL) @JsonSetter(nulls = Nulls.FAIL)
    @Size(max = 512) String reasonSummary) {
}
~~~

Create the four response records with the exact components from Step 1. Apply <code>@JsonInclude(ALWAYS)</code> plus required-property metadata to required-but-nullable <code>RefundResponseBody.clientRefundId</code>, <code>statusReasonCode</code>, <code>completedAt</code>, and <code>RefundListResponseBody.nextCursor</code>. Apply the Client Reference size/pattern annotations to nullable <code>clientRefundId</code>; Jakarta constraints naturally skip null but validate non-null values. Defensively copy <code>RefundListResponseBody.items</code>.

Keep <code>requestOrigin</code>, <code>refundStatus</code>, <code>reasonCode</code>, and <code>statusReasonCode</code> as Strings. Do not apply a closed enum or closed response-side Pattern to future-extensible response codes.

- [ ] **Step 5: Run the Refund test GREEN.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientRefundApiContractAutoTest test
~~~

Expected: five routes, exact query/default rules, create-only command headers/body, decimal/reference validation, nullable keys, and future String codes pass.

- [ ] **Step 6: Optional commit boundary, only if separately authorized.**

~~~bash
git add paytech-commerce-client-api/src/main/java/com/paytech/commerce/client/api/v1/refund paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientRefundApiContractAutoTest.java
git commit -m "feat: add Commerce refund contract"
~~~

### Task 7: Enforce Java/OpenAPI Drift and Source Purity

**Files:**
- Create: <code>commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1OpenApiJavaDriftAutoTest.java</code>
- Modify: <code>commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/architecture/CommerceArchitectureAutoTest.java</code>
- Modify: <code>commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceModuleBoundaryAutoTest.java</code>
- Test: <code>ClientV1OpenApiJavaDriftAutoTest</code>, <code>CommerceArchitectureAutoTest</code>, <code>CommerceModuleBoundaryAutoTest</code>

**Interfaces:**
- Consumes: all three Java APIs/models, all nine known-code catalogs, and the public OpenAPI document.
- Produces: bidirectional drift detection and a permanent proof that the contract JAR remains non-running, MVC-only, and outside Web implementation packages.

- [ ] **Step 1: Write the failing architecture expectations before changing the scanner.**

Change the DTO authority constant to:

~~~java
private static final String CLIENT_API_PACKAGE = "com.paytech.commerce.client.api.v1";
~~~

Make <code>sources()</code> scan both exact roots:

~~~java
List.of(
    reactorRoot().resolve("paytech-commerce-client-api/src/main/java"),
    reactorRoot().resolve("paytech-commerce-web/src/main/java")
)
~~~

Add an API purity test that fails if any API source:

- contains <code>@SpringBootApplication</code>, <code>@RestController</code>, <code>@Controller</code>, <code>@Service</code>, <code>@Repository</code>, or <code>@Configuration</code>;
- declares a type ending in <code>Controller</code>, <code>Processor</code>, <code>Service</code>, <code>ServiceImpl</code>, <code>Repository</code>, <code>RepositoryImpl</code>, <code>Mapper</code>, or <code>DataObject</code>;
- imports a class from a Commerce Web package;
- contains <code>Mono&lt;</code>, <code>Flux&lt;</code>, or an <code>io.projectreactor</code> import.

Add a fixture with <code>record LeakedResponseBody(String value) {}</code> in the old Web DTO package and assert the policy rejects it. Add a fixture in <code>com.paytech.commerce.client.api.v1.checkout</code> and assert the same policy accepts it.

- [ ] **Step 2: Run the architecture test and observe RED.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=CommerceArchitectureAutoTest test
~~~

Expected: FAIL until the source scanner and Client DTO authority are updated to include the new module.

- [ ] **Step 3: Implement the two-root scanner and focused purity validator.**

Retain all existing Service/Repository/interface, Controller/Processor persistence-bypass, cross-owner persistence, and cycle assertions. Extract a package-private <code>validateClientApiPurity(List&lt;SourceFile&gt;)</code> invoked by both real-source and fixture tests. It must apply only to sources whose package starts with <code>com.paytech.commerce.client.api.v1</code>, so it does not misclassify the existing Web application.

- [ ] **Step 4: Write the failing OpenAPI-to-Java descriptor test.**

Use an immutable operation descriptor with these values:

~~~java
record OperationContract(
    Class<?> apiType,
    String methodName,
    String httpMethod,
    String publicPath,
    String privatePath,
    Set<String> headers,
    Set<String> pathParameters,
    Set<String> queryParameters,
    Class<?> requestBodyType,
    String returnSignature) {
}

List<OperationContract> OPERATIONS = List.of(
    new OperationContract(ClientCheckoutApi.class, "createCheckout", "POST",
        "/api/v1/checkouts", "/checkouts", POST_HEADERS, Set.of(), Set.of(),
        CreateCheckoutRequestBody.class, "ResponseEntity<CreateCheckoutResponseBody>"),
    new OperationContract(ClientCheckoutApi.class, "getCheckout", "GET",
        "/api/v1/orders/{orderNo}/checkout", "/orders/{orderNo}/checkout",
        READ_HEADERS, Set.of("orderNo"), Set.of(), null, "CheckoutResponseBody"),
    new OperationContract(ClientOrderApi.class, "getOrder", "GET",
        "/api/v1/orders/{orderNo}", "/orders/{orderNo}",
        READ_HEADERS, Set.of("orderNo"), Set.of(), null, "OrderResponseBody"),
    new OperationContract(ClientOrderApi.class, "getOrderByClientOrderId", "GET",
        "/api/v1/orders", "/orders", READ_HEADERS, Set.of(), Set.of("clientOrderId"),
        null, "OrderResponseBody"),
    new OperationContract(ClientOrderApi.class, "cancelOrder", "POST",
        "/api/v1/orders/{orderNo}/cancel", "/orders/{orderNo}/cancel",
        POST_HEADERS, Set.of("orderNo"), Set.of(), null, "ResponseEntity<CancelOrderResult>"),
    new OperationContract(ClientRefundApi.class, "getRefundableAmount", "GET",
        "/api/v1/orders/{orderNo}/refundable-amount", "/orders/{orderNo}/refundable-amount",
        READ_HEADERS, Set.of("orderNo"), Set.of(), null, "RefundableAmountResponseBody"),
    new OperationContract(ClientRefundApi.class, "createRefund", "POST",
        "/api/v1/orders/{orderNo}/refunds", "/orders/{orderNo}/refunds",
        POST_HEADERS, Set.of("orderNo"), Set.of(), CreateRefundRequestBody.class,
        "ResponseEntity<CreateRefundResponseBody>"),
    new OperationContract(ClientRefundApi.class, "listOrderRefunds", "GET",
        "/api/v1/orders/{orderNo}/refunds", "/orders/{orderNo}/refunds",
        READ_HEADERS, Set.of("orderNo"), Set.of("limit", "cursor"),
        null, "RefundListResponseBody"),
    new OperationContract(ClientRefundApi.class, "getRefund", "GET",
        "/api/v1/refunds/{refundRequestNo}", "/refunds/{refundRequestNo}",
        READ_HEADERS, Set.of("refundRequestNo"), Set.of(), null, "RefundResponseBody"),
    new OperationContract(ClientRefundApi.class, "getRefundByClientRefundId", "GET",
        "/api/v1/refunds", "/refunds", READ_HEADERS, Set.of(), Set.of("clientRefundId"),
        null, "RefundResponseBody")
);
~~~

Define <code>READ_HEADERS</code> as the three trusted names and <code>POST_HEADERS</code> as those three plus <code>Idempotency-Key</code>. The test must compare all ten Java methods with all ten OpenAPI operation IDs, HTTP verbs, public/private path suffixes, parameter locations/names, body presence/type, and success body type.

Normalize reflected returns with this helper so direct and generic return assertions use one stable representation:

~~~java
private String returnSignature(Method method) {
    Type returnType = method.getGenericReturnType();
    if (returnType instanceof ParameterizedType parameterized) {
        Class<?> raw = (Class<?>) parameterized.getRawType();
        Class<?> argument = (Class<?>) parameterized.getActualTypeArguments()[0];
        return raw.getSimpleName() + "<" + argument.getSimpleName() + ">";
    }
    return method.getReturnType().getSimpleName();
}
~~~

Define a schema descriptor for every record:

~~~java
Map<String, Class<?>> SCHEMAS = Map.ofEntries(
    entry("Amounts", Amounts.class),
    entry("CreateCheckoutRequest", CreateCheckoutRequestBody.class),
    entry("CheckoutItemRequest", CheckoutItemRequest.class),
    entry("CreateCheckoutResponse", CreateCheckoutResponseBody.class),
    entry("CheckoutResponse", CheckoutResponseBody.class),
    entry("OrderItem", OrderItem.class),
    entry("OrderResponse", OrderResponseBody.class),
    entry("CancelOrderResponse", CancelOrderResponseBody.class),
    entry("CancellationPendingResponse", CancellationPendingResponseBody.class),
    entry("RefundableAmountResponse", RefundableAmountResponseBody.class),
    entry("CreateRefundRequest", CreateRefundRequestBody.class),
    entry("CreateRefundResponse", CreateRefundResponseBody.class),
    entry("RefundResponse", RefundResponseBody.class),
    entry("RefundListResponse", RefundListResponseBody.class)
);
~~~

For inline Order schemas, separately compare <code>LastPaymentAttempt</code>, <code>RefundSummary</code>, and <code>FundsAvailability</code> to the nested OpenAPI property objects. Assert component names/order, Java primitive/String/Instant/List/object mapping, required-property metadata, nullable <code>@JsonInclude(ALWAYS)</code>, optional <code>NON_NULL</code>, sizes, patterns, and list bounds.

Compare OpenAPI <code>x-known-values</code>/closed request enums to the nine catalog code sets, but assert every DTO code component remains <code>String</code>.

- [ ] **Step 5: Add synthetic mutation tests that prove the drift checker fails closed.**

Extract validation into methods accepting in-memory descriptors/maps, then assert each mutation throws <code>AssertionError</code>:

~~~java
assertThrows(AssertionError.class, () -> validateOperations(
    replacePrivatePath(OPERATIONS, "createCheckout", "/wrong")));
assertThrows(AssertionError.class, () -> validateOperations(
    removeHeader(OPERATIONS, "cancelOrder", "Idempotency-Key")));
assertThrows(AssertionError.class, () -> validateRecord(
    "CreateRefundRequest", descriptorWithExtraField("unexpected")));
assertThrows(AssertionError.class, () -> validateCodeField(
    "OrderResponse", "orderStatus", OrderStatus.class));
assertThrows(AssertionError.class, () -> validateDecimalPattern(
    "^(?:0|[1-9][0-9]{0,16})(?:[.][0-9]{1,8})?$"));
~~~

The helpers operate only on test descriptors or copied YAML maps; they never rewrite the actual OpenAPI file.

Implement the mutation helpers as immutable copies: <code>replacePrivatePath</code> maps the ten-operation list and changes only the named operation; <code>removeHeader</code> copies only that operation's Header set minus the named value; <code>descriptorWithExtraField</code> copies the real schema descriptor and appends the named component. <code>validateCodeField</code> asserts the supplied Java type is exactly <code>String.class</code>; <code>validateDecimalPattern</code> compiles the supplied expression and applies the accepted/rejected values from Task 2.

- [ ] **Step 6: Run the drift test and observe RED, then implement its comparison helpers.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientV1OpenApiJavaDriftAutoTest test
~~~

Expected first run: FAIL until all descriptor-to-reflection/YAML comparison helpers are complete. After implementing the helpers, rerun the same command and expect PASS.

- [ ] **Step 7: Extend module purity to inspect resources and the built classpath.**

In <code>CommerceModuleBoundaryAutoTest</code>, assert the API module:

- has no <code>src/main/resources</code> files;
- has no Main method or <code>@SpringBootApplication</code>;
- has no class/package name for Controller, Processor, Service, Repository, Mapper, or DataObject;
- has no <code>reactor.core.publisher.Mono</code> or <code>Flux</code> class available from its dependency tree;
- remains publishable while Test remains install/deploy skipped.

- [ ] **Step 8: Run the combined contract/architecture gate GREEN.**

~~~bash
./mvnw -pl paytech-commerce-test -am -Dtest=ClientV1OpenApiJavaDriftAutoTest,ClientV1OpenApiContractAutoTest,ClientV1CommonContractAutoTest,ClientCheckoutApiContractAutoTest,ClientOrderApiContractAutoTest,ClientRefundApiContractAutoTest,CommerceModuleBoundaryAutoTest,CommerceArchitectureAutoTest test
~~~

Expected: all listed tests pass with no Controller or runtime implementation present.

- [ ] **Step 9: Optional commit boundary, only if separately authorized.**

~~~bash
git add paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1OpenApiJavaDriftAutoTest.java paytech-commerce-test/src/test/java/com/paytech/commerce/auto/architecture/CommerceArchitectureAutoTest.java paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceModuleBoundaryAutoTest.java
git commit -m "test: guard Commerce Client API contract drift"
~~~

### Task 8: Update Truthful Documentation and Run Full Verification

**Files:**
- Modify: <code>commerce-service/README.md</code>
- Modify: <code>agent-docs/CURRENT_IMPLEMENTATION.md</code>
- Modify: <code>agent-docs/DOCUMENT_GOVERNANCE.md</code>
- Modify: <code>agent-docs/README.md</code>
- Modify: <code>agent-docs/decisions/2026-09-05-commerce-client-api-contract-module.md</code>
- Modify: <code>docs/superpowers/specs/2026-09-05-commerce-client-api-contract-module-design.md</code>
- Verify: all changed files and the protected Commerce database decision file

**Interfaces:**
- Consumes: verified code/test evidence from Tasks 1–7.
- Produces: one truthful current-state report and discoverable authority routing without claiming Controller, Gateway activation, end-to-end behavior, database migration, or deployment.

- [ ] **Step 1: Update the Commerce README only after the combined gate is green.**

Make these exact factual changes:

- Introduction: say that the repository now contains a non-running Client API V1 contract JAR and public OpenAPI in addition to the Spring MVC application foundation; explicitly say no Commerce business Controller is registered.
- Modules: add <code>paytech-commerce-client-api</code> as a publishable compile-time Owner ingress contract; keep Web as the only deployable module and Test as non-published evidence.
- Dependency direction: change it to <code>test -> web -> client-api</code>.
- Build output: mention the ordinary Client API JAR and the Web ordinary/executable JARs.
- Current non-goals: remove only the obsolete “no API JAR” claim; retain no SQL, persistence, cross-system integration, Gateway activation, Hosted Checkout page, and deployment claims.
- Clarify that the contract module is not a second service, a client SDK, or the future internal <code>paytech-commerce-api</code>.

- [ ] **Step 2: Update the decision/design implementation markers.**

Only after <code>./mvnw clean verify</code> passes, change:

~~~text
Implementation status: Not started
~~~

to:

~~~text
Implementation status: Implemented and locally verified
~~~

in both the contract-module decision and design. Keep <code>Deployment status: Not applicable</code>. Do not change the broader Hosted Checkout V1 business implementation status.

- [ ] **Step 3: Route the narrow authority without creating a second source of truth.**

In <code>agent-docs/DOCUMENT_GOVERNANCE.md</code> and the Commerce Client API row of <code>agent-docs/README.md</code>, link:

~~~text
agent-docs/decisions/2026-09-05-commerce-client-api-contract-module.md
docs/superpowers/specs/2026-09-05-commerce-client-api-contract-module-design.md
docs/superpowers/plans/2026-09-05-commerce-client-api-contract-module-implementation.md
~~~

Describe those files as owning only the non-running Java Owner ingress contract module, its Java/OpenAPI projection rules, and this implementation sequence. Leave the original Client API decision/design as owner of business and public wire semantics outside the three explicitly superseded gaps.

- [ ] **Step 4: Update current implementation facts surgically.**

In the Commerce row of <code>agent-docs/CURRENT_IMPLEMENTATION.md</code>, record:

- Parent + Client API + Web + Test now exist;
- the Client API JAR contains three interfaces, ten operations, records, and nine known-code catalogs;
- public OpenAPI and Java projection drift/purity tests pass locally;
- no Controller, business implementation, database/SQL migration, Gateway route activation, Checkout page, end-to-end verification, deployment, or production availability exists.

In <code>COMMERCE-CLIENT-API-01</code>, replace the obsolete statement that OpenAPI/Java do not exist with the narrow implemented facts above, while retaining every unresolved Foundation, CCT, Risk, Payment, Finance, Platform, Gateway, Nginx, database, and frontend gate. Patch only those Commerce cells because this file contains concurrent work from other tasks.

- [ ] **Step 5: Run full Commerce verification.**

~~~bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/commerce-service
./mvnw clean verify
./mvnw -pl paytech-commerce-client-api dependency:tree -Dverbose
jar tf paytech-commerce-client-api/target/paytech-commerce-client-api-1.0.0-SNAPSHOT.jar
~~~

Expected:

- <code>BUILD SUCCESS</code>;
- dependency tree contains no WebFlux, Reactor, Boot starter, persistence, infrastructure, or other Owner API;
- the JAR contains the three API interfaces, four model/constant areas, and nine enum classes;
- the JAR contains no Spring Boot application, Controller, Processor, Service, Repository, Mapper, DataObject, configuration resource, Mapper XML, or reactive type.

- [ ] **Step 6: Run repository-level requirements and formatting gates.**

~~~bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
npm --prefix requirements-site test
npm --prefix requirements-site run lint
git diff --check
~~~

Expected: requirements tests and lint pass; <code>git diff --check</code> emits no whitespace errors. If a failure predates this slice, show the command/output and prove it is unrelated before reporting partial verification.

- [ ] **Step 7: Recheck preservation and review the final diff.**

~~~bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/commerce-service
shasum -a 256 docs/database/COMMERCE_PHYSICAL_DECISIONS.md
git status --short
git diff --stat
git diff --check

cd /Users/kenny/Desktop/Workspace/Code-MoR
git status --short
git diff --check
~~~

Expected protected-file SHA-256 remains the execution-start value. Review every changed path against this plan and confirm there is no Controller, no reactive type, no database edit, and no unrelated root-file rewrite.

- [ ] **Step 8: Optional final commit boundary, only if separately authorized.**

~~~bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/commerce-service
git add README.md
git commit -m "docs: record Commerce Client API contract module"

cd /Users/kenny/Desktop/Workspace/Code-MoR
git add agent-docs/CURRENT_IMPLEMENTATION.md agent-docs/DOCUMENT_GOVERNANCE.md agent-docs/README.md agent-docs/decisions/2026-09-05-commerce-client-api-contract-module.md docs/superpowers/specs/2026-09-05-commerce-client-api-contract-module-design.md docs/superpowers/plans/2026-09-05-commerce-client-api-contract-module-implementation.md
git commit -m "docs: govern Commerce Client API contract module"
~~~

Do not push, merge, deploy, activate Gateway routes, or publish artifacts under this plan.

## Completion Evidence

The implementation is complete only when all of the following are simultaneously true:

- the dependency direction is exactly <code>test -> web -> client-api</code>, while Maven lists/builds the modules as <code>client-api -> web -> test</code>;
- the API module has exactly the four allowed direct dependencies and no reactive/runtime infrastructure;
- three interfaces expose exactly ten private-path methods with exact trusted headers and POST idempotency;
- Java DTOs, optional/nullable rules, decimal constraints, code catalogs, and public OpenAPI agree;
- the three approved OpenAPI gaps are covered by tests;
- architecture tests prove the module contains contracts only;
- <code>./mvnw clean verify</code>, requirements-site tests/lint, dependency inspection, JAR inspection, and whitespace checks pass;
- documentation says “contract implemented and locally verified” while continuing to say the broader business API is not implemented, activated, deployed, or production-ready;
- the protected Commerce database decision file and unrelated dirty work remain unchanged.

## Plan Self-Review Checklist

- Spec sections 1–3 map to Tasks 1, 3, and 7.
- Spec sections 4–5 map to Tasks 4–7.
- Spec sections 6–8 map to Tasks 2–7.
- Spec sections 9–10 are protected by Task 7 purity checks and Task 8 wording.
- Spec sections 11–13 map to every RED/GREEN command and Task 8 full evidence.
- Every production type named by a later task is created by Task 3, 4, 5, or 6.
- No task creates a runtime implementation, a reactive signature, or a database artifact.
- Optional commit boundaries remain disabled unless the user separately authorizes them.
