# Risk Operations BFF Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose nine strict Operations browser endpoints that authorize through PLT-I02 first and then make exactly one typed private Risk call.

**Architecture:** The BFF keeps its existing stateless `web + test` shape. Concrete Controllers use BFF-local browser models and one `RiskDecisionConfigurationClient` port; the sole adapter converts to/from the two existing `risk-api` interfaces, strictly validates upstream responses and removes Handler implementation fingerprints from Authoring Context.

**Tech Stack:** Java 25, Maven Wrapper 3.9.16, Spring Boot 4.0.8, Spring MVC, Jakarta Validation, Jackson, `RestClient`, Spring `@HttpExchange`, Nacos LoadBalancer, Foundation strict RFC 9457 client, JUnit 5 loopback simulators.

**Spec:** `docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md`

## Global Constraints

- Keep `OperationsRequestInterceptor` as the single PLT-I02 gate. It must run before MVC body conversion and before every Risk invocation.
- Add no Processor, business Service, Repository, database, cache or local persistence for this one-owner forwarding slice.
- Depend on `com.paytech:risk-api:1.0.0-SNAPSHOT`. Production field access and Owner↔BFF projection may occur only in `RiskDecisionConfigurationClientImpl`. `RiskClientConfiguration` and the dedicated Owner response converter/shape validator may refer to exact Owner `Class`/generic `Type` tokens solely to register and decode transport roots; only `RiskClientConfiguration` may additionally call `RiskDecisionConfigurationErrorCode.values()` for strict error registration. They must not otherwise dereference Owner fields, project browser data or expose an Owner type through a bean signature other than the existing typed Risk API proxies. `OperationsErrorStatusResolver` may import only `RiskDecisionConfigurationErrorCode` for the explicit not-found mapping. Controller, local client port and service model have zero `risk-api` imports. Test-source protocol simulators may construct Owner DTOs to prove wire compatibility.
- The browser model must not import `risk-api` and its Authoring Action record must not define `handlerCode`, `implementationVersion` or `implementationHash`.
- The adapter must validate all three Handler fields from upstream before deleting them from the browser projection.
- Use an independent `risk-service` discovery interceptor and independent client factory; never reuse the hard-coded Platform interceptor.
- Use base URL `http://risk-service`, connect timeout 2 seconds, socket read timeout 4 seconds, redirects `NEVER`, no application retry and `HttpServiceClientOptions.strict()`. Every write command must reach the transport at most once; do not claim that the JDK's underlying URLConnection never retries an idempotent read after an early transport failure.
- Propagate `X-Request-Id`, trusted actor account ID, session ID and original valid idempotency key. Never log raw keys, AST, literals, Handler fields or complete payloads.
- Four Risk top-level request records reject unknown fields and missing required properties while preserving explicit nullable values. Do not change JSON parsing for existing BFF endpoints.
- Browser idempotency keys match `[A-Za-z0-9_-]{16,128}`. Validate additionally requires exact `risk-validate-v1-{releaseId}-{expectedVersion}` after PLT-I02.
- Map Risk not-found to browser 404, dependency unavailable to 503, timeout to 504 and malformed successful upstream responses to Foundation `UPSTREAM_INVALID_RESPONSE`/502.
- Do not implement a BFF body-size filter or direct-BFF 413 assertions; Gateway owns exact byte counting and chunked rejection.
- Raise the existing Gateway Configuration Detail protocol ceiling to 65,536 generically, without a Risk-path condition.
- Add an isolated positive-decimal actor mode to the Platform test simulator for the new Risk route tests; keep its existing `account_10001` default and all existing account-management fixtures unchanged.
- Preserve the currently clean BFF repository and the unrelated dirty Risk API import change; do not edit or stage that Risk file.

---

### Task 1: Add the Risk API Dependency and Independent HTTP Client

**Files:**
- Modify: `operations-console-bff/pom.xml`
- Modify: `operations-console-bff/paytech-operations-console-bff-web/pom.xml`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/pom.xml`
- Create: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/integration/RiskDiscoveryInterceptor.java`
- Create: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskClientConfiguration.java`
- Create: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskOwnerResponseShapeValidator.java`
- Create: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskOwnerResponseHttpMessageConverter.java`
- Create: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskClientConfigurationAutoTest.java`

**Interfaces:**
- Consumes: `DecisionRuleSetManagementApi`, `DecisionReleaseManagementApi` and `RiskDecisionConfigurationErrorCode` from `risk-api`.
- Produces: beans `decisionRuleSetManagementApi`, `decisionReleaseManagementApi` and `operationsRiskProxyFactory`, plus discovery service ID `risk-service`.

- [ ] **Step 1: Install the Owner API artifact required by the test classpath**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
./mvnw -q -pl risk-api -am -DskipTests install
```

Expected: the existing Risk API artifact installs locally; this command changes no BFF source.

- [ ] **Step 2: Add the managed and module dependency setup**

Add to root dependency management:

```xml
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>risk-api</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Add `risk-api` without version to Web and with `test` scope to Test because the protocol simulator constructs Owner wire records. This is build scaffolding only; it adds no production behavior.

- [ ] **Step 3: Add the missing-discovery RED test**

In `RiskClientConfigurationAutoTest`, start an application context with the real Risk client factory and no `risk-service` discovery instance. Obtain `DecisionRuleSetManagementApi`, invoke `getAuthoringContext("10001", "session-10001", "CHECKOUT_PREPARE")` once, and require a `ServiceUnavailableException` whose code is `DEPENDENCY_UNAVAILABLE`, whose cause is preserved and whose message contains neither a request payload nor a discovery address. Assert the LoadBalancer choose counter is exactly one.

- [ ] **Step 4: Add the strict-success shape RED tests**

In a second `RiskClientConfigurationAutoTest` context, point the factory at a loopback server. For Authoring Context, return one valid success plus unknown-root-property and raw-duplicate-key variants. For Rule Set Detail, return one valid success plus a variant that omits its required-but-nullable `frozenTime` property. Require both valid bodies to deserialize and each malformed 200 to fail as `UPSTREAM_INVALID_RESPONSE`; the test must fail if a default typed Jackson converter silently drops, overwrites or invents required nullable data.

- [ ] **Step 5: Add strict converter-selection and token RED tests**

In `RiskClientConfigurationAutoTest`, require read support only for the nine exact success return Types listed in Step 10. Require raw `PagingResponseBody`, a wrong page generic and every unlisted root to return false, and every `canWrite` overload to return false. Add cases for a second trailing root and, for every structural/scalar token category listed in Step 9, mutate one otherwise valid response to the wrong JSON token and require the direct proxy call to throw `ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE)`.

- [ ] **Step 6: Add the production API-boundary architecture RED test**

Scan production `src/main/java` only. The exact allowlist is: `RiskClientConfiguration`, `RiskOwnerResponseHttpMessageConverter` and `RiskOwnerResponseShapeValidator` may use Owner `.class`/Type tokens without field access; only `RiskClientConfiguration` may additionally call `RiskDecisionConfigurationErrorCode.values()` to register the Step 11 strict error contract; `RiskDecisionConfigurationClientImpl` alone may call any other Owner accessor or construct an Owner request record; `OperationsErrorStatusResolver` may reference only `RiskDecisionConfigurationErrorCode` for status projection. Controller, local port, service models and every other production class have zero `risk-api` imports. Test sources remain outside this gate so the protocol simulator may construct Owner records.

- [ ] **Step 7: Run the client-configuration RED test**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=RiskClientConfigurationAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the build classpath resolves `risk-api`, then compilation or context startup fails specifically because the Risk client factory, strict converter and proxy beans do not exist.

- [ ] **Step 8: Implement the dedicated discovery interceptor**

Create a final component following the existing Platform interceptor shape but with:

```java
private static final String SERVICE_ID = "risk-service";
```

Choose through `LoadBalancerClient`, throw `ResourceAccessException("Risk 没有可用的发现实例")` when no instance exists, and execute through `LoadBalancerRequestFactory`. Do not inspect or rewrite payload bytes.

- [ ] **Step 9: Implement the Owner success-shape validator**

`RiskOwnerResponseShapeValidator` owns exact JSON property sets for the nine Owner success shapes and every nested response/request-shaped rule record. It validates a Jackson tree against the requested generic Java `Type`, including explicit nullable properties and empty arrays, without enforcing rule-tree semantics. Validate JSON token kinds and numeric ranges before binding: Object/Array at every structural position; integral JSON numbers within `int` range for versions, sequence numbers, release numbers, timeouts, page and size; integral `total` in `0..Long.MAX_VALUE`; Boolean for flags; String for IDs, codes, names, hashes and timestamps; explicit Null only at documented nullable properties; and String/Boolean/Null only for `literalValue`.

Keep missing-Null distinct from explicit-Null before typed records erase that information. Cover root, page wrapper/item, Rule/Node/Operand, Authoring Action including all three private Handler fields, frozen composition, state changes and checks. Do not use Owner record construction or Jackson coercion as the shape validator.

- [ ] **Step 10: Implement the strict Owner success converter**

`RiskOwnerResponseHttpMessageConverter` must:

```text
1. advertise read support only for these exact success return Types:
   RuleAuthoringContextResponseBodyV1,
   PagingResponseBody<RuleSetListItemResponseBodyV1>,
   RuleSetDetailResponseBodyV1,
   CreateRuleSetVersionResponseBodyV1,
   FreezeRuleSetVersionResponseBodyV1,
   PagingResponseBody<TestReleaseListItemResponseBodyV1>,
   TestReleaseDetailResponseBodyV1,
   CreateTestReleaseResponseBodyV1,
   ValidateTestReleaseResponseBodyV1;
2. use Jackson 3 StreamReadFeature.STRICT_DUPLICATE_DETECTION;
3. consume exactly one JSON root and require end-of-input, parse once to a tree, run the exact-key shape validator, then bind that same tree to the requested Owner Type;
4. throw a RestClient decoding failure for duplicate, trailing-root, unknown, missing or type-invalid successful JSON so the existing HttpServiceClientInterceptor maps it to UPSTREAM_INVALID_RESPONSE;
5. never write requests and never handle RFC 9457 error bodies.
```

Implement `canRead` against the complete `ResolvableType`, not raw classes. The real BFF HTTP boundary maps converter decoding failures to 502 later in Task 5.

- [ ] **Step 11: Implement the strict Risk client factory**

Define constants and beans:

```java
private static final String RISK_BASE_URL = "http://risk-service";
private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
private static final Duration READ_TIMEOUT = Duration.ofSeconds(4);

@Bean DecisionRuleSetManagementApi decisionRuleSetManagementApi(HttpServiceProxyFactory operationsRiskProxyFactory)
@Bean DecisionReleaseManagementApi decisionReleaseManagementApi(HttpServiceProxyFactory operationsRiskProxyFactory)
@Bean HttpServiceProxyFactory operationsRiskProxyFactory(
    Environment environment,
    RiskDiscoveryInterceptor discovery,
    @Value("${operations.risk-test-base-url:}") String testBaseUrl)
```

For a blank test URL, install the discovery interceptor. For a nonblank test URL, allow only `http://127.0.0.1:<positive-port>` under the `auto` profile with no user info, query or fragment. Configure a dedicated `SimpleClientHttpRequestFactory` with the two timeouts and a narrow `prepareConnection` override that disables `HttpURLConnection` redirects after the superclass setup. This transport choice is deliberate: Spring 7's streaming `JdkClientHttpRequestFactory` closes a partially read response at its deadline without preserving a timeout marker, so the strict Foundation classifier cannot distinguish that timeout from an unavailable dependency. Require executable tests for header timeout, partial-body timeout, partial-body disconnect and redirect refusal. Add the same MDC Request-ID interceptor as Platform. Build options as:

```java
HttpServiceClientOptions.strict().withDependencyFailureMapper((kind, cause) -> switch (kind) {
    case TIMEOUT -> new ErrorException(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, null, cause);
    case UNAVAILABLE -> new ServiceUnavailableException(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, cause);
});
```

Register only `RiskDecisionConfigurationErrorCode.values()`.

Insert `RiskOwnerResponseHttpMessageConverter` ahead of the default success converters for this RestClient. Keep `HttpServiceClientOptions.strict()` as the independent RFC 9457/error transport contract; the custom converter must not add legacy error support.

- [ ] **Step 12: Run client-configuration GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=RiskClientConfigurationAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the two typed proxies exist, one missing-discovery call maps safely to `DEPENDENCY_UNAVAILABLE`, malformed successful JSON is rejected before typed information loss, and no retry occurs.

### Task 2: Define BFF-Local Browser Models and Strict Request Decoding

**Files:**
- Create: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/service/model/RiskRuleSetModels.java`
- Create: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/service/model/RiskTestCandidateModels.java`
- Create: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskRequestShapeValidator.java`
- Create: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskRequestJsonConfiguration.java`
- Create: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskDecisionConfigurationModelAutoTest.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsHttpBodyNamingAutoTest.java`

**Interfaces:**
- Consumes: the exact browser wire contract in spec sections 7 and 10.
- Produces: BFF-local records used by Controller and client port, and strict JSON conversion limited to the four command-body root types.

- [ ] **Step 1: Add request-record construction RED tests**

In `RiskDecisionConfigurationModelAutoTest`, require defensive collection snapshots without changing validation classification. Request constructors preserve a null List and null elements in an unmodifiable `new ArrayList<>(values)` so `@NotNull` and element validation can produce 422; response constructors use `List.copyOf` only after adapter validation. Require nullable fields and empty arrays to serialize because every model uses `@JsonInclude(ALWAYS)`.

- [ ] **Step 2: Add strict command-wire RED tests**

Deserialize each of the four commands with all required properties. Assert 400-shape rejection for omitted nullable Base fields, omitted nullable node/action properties, any unknown top-level or nested field, duplicate raw JSON keys, numeric `literalValue`, string/decimal values for integral fields and string values for booleans. Assert the exact 422 validation classification for blank, pattern and size violations after successful strict decoding.

- [ ] **Step 3: Add AST non-ownership RED tests**

Prove only that duplicate node-key values in distinct array elements and syntactically valid GROUP/PREDICATE, CONTEXT/LITERAL and connectivity combinations remain deserializable here. Require the resulting BFF-local request to retain array order and values exactly; Task 3 then proves one unchanged Risk call and authoritative Risk 400/409 handling. Do not add Rule/Node/Operand identity or semantic validation to the model/converter tests.

- [ ] **Step 4: Run model RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=RiskDecisionConfigurationModelAutoTest,OperationsHttpBodyNamingAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the BFF-local Risk models and converter do not exist.

- [ ] **Step 5: Define Rule Set browser records**

`RiskRuleSetModels` must contain these exact public records:

```text
RuleSetQueryRequestBody(pointCode, ruleSetCode, status)
CreateRuleSetVersionRequestBody(pointCode, ruleSetCode, ruleSetName, baseRuleSetVersionId,
  baseContentHash, changeReason, rules)
RuleRequestBody(ruleCode, ruleName, priorityNo, enabledFlag, reasonCode, conditionNodes, action)
ConditionNodeRequestBody(nodeKey, parentNodeKey, sequenceNo, nodeType, groupOperator,
  comparisonOperator, operands)
OperandRequestBody(operandRole, valueSequence, sourceType, fieldCode, valueType, literalValue)
RuleActionRequestBody(actionCode, actionVersionNo)
FreezeRuleSetVersionRequestBody(expectedContentHash)
RuleAuthoringContextResponseBody(descriptorCode, point, fields, operators, actions, features,
  actionParameters, obligations, limitBindings)
DecisionPointDescriptorResponseBody(pointCode, pointName, businessDomain, inputContractCode,
  inputContractVersion, outputContractCode, defaultDecision, evaluationStrategy,
  failureStrategy, timeoutMillis, status)
DecisionInputFieldDescriptorResponseBody(fieldCode, valueType, requiredFlag,
  dataClassification, ruleReferenceFlag, actionParameterFlag)
DecisionActionDescriptorResponseBody(actionCode, actionName, actionCategory, versionNo,
  terminalFlag, decisionCode, mergePolicy, shadowSupportedFlag, status)
RuleSetListItemResponseBody(ruleSetVersionId, pointVersionId, pointCode, ruleSetCode,
  ruleSetName, versionNo, status, contentHash, createdBy, changeReason, createTime,
  frozenBy, frozenTime)
RuleSetDetailResponseBody(ruleSetVersionId, pointVersionId, pointCode, ruleSetCode,
  ruleSetName, versionNo, status, contentHash, createdBy, changeReason, createTime,
  frozenBy, frozenTime, rules)
CreateRuleSetVersionResponseBody(ruleSetVersionId, pointVersionId, ruleSetCode, versionNo,
  status, contentHash, createdBy, createTime)
FreezeRuleSetVersionResponseBody(ruleSetVersionId, pointVersionId, ruleSetCode, versionNo,
  status, contentHash, frozenBy, frozenTime)
```

Use `List<String>` for `operators` and `List<Void>` for each of the four intentionally empty Authoring collections. Use Owner-compatible Jakarta constraints and `@JsonProperty(isRequired=TRUE)` on every command property, including nullable ones. Allow `literalValue` only `String`, `Boolean` or null. Implement request List snapshotting as:

```java
private static <T> List<T> requestSnapshot(List<T> values) {
    return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
}
```

Only the already-validated adapter mapping may upgrade those Lists to `List.copyOf` before constructing Owner requests.

- [ ] **Step 6: Define TEST Candidate browser records**

`RiskTestCandidateModels` must contain:

```text
CandidateQueryRequestBody(pointCode, status)
CreateTestCandidateRequestBody(ruleSetVersionId, expectedRuleSetContentHash)
ValidateTestCandidateRequestBody(expectedVersion)
TestCandidateListItemResponseBody(releaseId, releaseNo, decisionPointId, pointCode,
  pointVersionId, ruleSetVersionId, environmentCode, status, version, contentHash,
  createdBy, createTime, allStaticChecksPassed)
TestCandidateDetailResponseBody(releaseId, releaseNo, decisionPointId, pointCode,
  pointVersionId, ruleSetVersionId, environmentCode, status, version, contentHash,
  createdBy, createTime, allStaticChecksPassed, waitingForShadow, frozenComposition,
  stateChanges, staticChecks)
FrozenCompositionResponseBody(decisionPointId, pointCode, pointVersionId, pointVersionNo,
  pointVersionContentHash, ruleSetVersionId, ruleSetCode, ruleSetVersionNo,
  ruleSetContentHash)
ReleaseStateChangeResponseBody(auditEventId, eventTime, fromStatus, toStatus, reasonCode,
  triggerType, triggerId, actorId, aggregateVersion)
ReleaseStaticCheckResponseBody(auditEventId, eventTime, checkCode, checkStatus, severity,
  errorCode, errorSummary)
InitialStateChangeResponseBody(auditEventId, eventTime, fromStatus, toStatus, reasonCode,
  triggerType, actorId, aggregateVersion)
ValidationCheckResponseBody(checkCode, checkStatus, severity, errorCode, errorSummary)
CreateTestCandidateResponseBody(releaseId, releaseNo, decisionPointId, pointCode,
  pointVersionId, ruleSetVersionId, environmentCode, status, version, contentHash,
  createdBy, createTime, initialStateChange)
ValidateTestCandidateResponseBody(releaseId, releaseNo, environmentCode, status, version,
  contentHash, allStaticChecksPassed, checks)
```

- [ ] **Step 7: Apply the exact Java type map to both model containers**

Use this complete Java type map for both model containers; no property may be left to inference:

| Java type | Exact properties |
|---|---|
| `String` | every `*Id`, `*Code`, `*Name`, `*Hash`, `*Time`, plus `createdBy`, `frozenBy`, `fromStatus`, `toStatus`, `descriptorCode`, `businessDomain`, `defaultDecision`, `evaluationStrategy`, `failureStrategy`, `status`, `dataClassification`, `actionCategory`, `changeReason`, `mergePolicy`, `nodeKey`, `parentNodeKey`, `nodeType`, `groupOperator`, `comparisonOperator`, `operandRole`, `sourceType`, `valueType`, `environmentCode`, `triggerType`, `checkStatus`, `severity`, `errorSummary` |
| boxed `Integer` | request/request-shaped `priorityNo`, `sequenceNo`, `valueSequence`, `actionVersionNo`, `expectedVersion` |
| primitive `int` | response `inputContractVersion`, `timeoutMillis`, every response `versionNo`, `version`, `releaseNo`, `pointVersionNo`, `ruleSetVersionNo`, `aggregateVersion`; Foundation page `page/size` |
| boxed `Boolean` | request/request-shaped `enabledFlag`; nullable Candidate `allStaticChecksPassed` |
| primitive `boolean` | response `requiredFlag`, `ruleReferenceFlag`, `actionParameterFlag`, `terminalFlag`, `shadowSupportedFlag`, `waitingForShadow`; Validate response `allStaticChecksPassed` |
| `Object` | only `OperandRequestBody.literalValue`, followed by the explicit String/Boolean/null constructor check |
| `List<String>` | `RuleAuthoringContextResponseBody.operators` |
| `List<Void>` | `features`, `actionParameters`, `obligations`, `limitBindings` |
| `List<RuleRequestBody>` | create request and detail `rules` |
| `List<ConditionNodeRequestBody>` | `conditionNodes` |
| `List<OperandRequestBody>` | `operands` |
| `List<DecisionInputFieldDescriptorResponseBody>` | `fields` |
| `List<DecisionActionDescriptorResponseBody>` | `actions` |
| `List<ReleaseStateChangeResponseBody>` | `stateChanges` |
| `List<ReleaseStaticCheckResponseBody>` | `staticChecks` |
| `List<ValidationCheckResponseBody>` | `checks` |
| nested record | `point`, `action`, `frozenComposition`, `initialStateChange` use their exact named record types above |

Concretely, the record component types are exactly the following. Request fields and the request-shaped Rule/Node/Operand/Action records reused inside Rule Set Detail stay boxed so Jakarta Validation can report present-null as 422. After the adapter validates non-null Owner response values, response-specific numeric/flag components use primitives; only explicitly nullable Candidate `allStaticChecksPassed` remains boxed. Foundation paging remains exactly `PagingResponseBody<T>(List<T> items, int page, int size, long total)`.

```java
RuleSetQueryRequestBody(String pointCode, String ruleSetCode, String status)
CandidateQueryRequestBody(String pointCode, String status)
CreateRuleSetVersionRequestBody(String pointCode, String ruleSetCode, String ruleSetName,
    String baseRuleSetVersionId, String baseContentHash, String changeReason,
    List<RuleRequestBody> rules)
RuleRequestBody(String ruleCode, String ruleName, Integer priorityNo, Boolean enabledFlag,
    String reasonCode, List<ConditionNodeRequestBody> conditionNodes, RuleActionRequestBody action)
ConditionNodeRequestBody(String nodeKey, String parentNodeKey, Integer sequenceNo,
    String nodeType, String groupOperator, String comparisonOperator, List<OperandRequestBody> operands)
OperandRequestBody(String operandRole, Integer valueSequence, String sourceType,
    String fieldCode, String valueType, Object literalValue)
RuleActionRequestBody(String actionCode, Integer actionVersionNo)
FreezeRuleSetVersionRequestBody(String expectedContentHash)
CreateTestCandidateRequestBody(String ruleSetVersionId, String expectedRuleSetContentHash)
ValidateTestCandidateRequestBody(Integer expectedVersion)

RuleAuthoringContextResponseBody(String descriptorCode, DecisionPointDescriptorResponseBody point,
    List<DecisionInputFieldDescriptorResponseBody> fields, List<String> operators,
    List<DecisionActionDescriptorResponseBody> actions, List<Void> features,
    List<Void> actionParameters, List<Void> obligations, List<Void> limitBindings)
DecisionPointDescriptorResponseBody(String pointCode, String pointName, String businessDomain,
    String inputContractCode, int inputContractVersion, String outputContractCode,
    String defaultDecision, String evaluationStrategy, String failureStrategy,
    int timeoutMillis, String status)
DecisionInputFieldDescriptorResponseBody(String fieldCode, String valueType, boolean requiredFlag,
    String dataClassification, boolean ruleReferenceFlag, boolean actionParameterFlag)
DecisionActionDescriptorResponseBody(String actionCode, String actionName, String actionCategory,
    int versionNo, boolean terminalFlag, String decisionCode, String mergePolicy,
    boolean shadowSupportedFlag, String status)
RuleSetListItemResponseBody(String ruleSetVersionId, String pointVersionId, String pointCode,
    String ruleSetCode, String ruleSetName, int versionNo, String status, String contentHash,
    String createdBy, String changeReason, String createTime, String frozenBy, String frozenTime)
RuleSetDetailResponseBody(String ruleSetVersionId, String pointVersionId, String pointCode,
    String ruleSetCode, String ruleSetName, int versionNo, String status, String contentHash,
    String createdBy, String changeReason, String createTime, String frozenBy, String frozenTime,
    List<RuleRequestBody> rules)
CreateRuleSetVersionResponseBody(String ruleSetVersionId, String pointVersionId, String ruleSetCode,
    int versionNo, String status, String contentHash, String createdBy, String createTime)
FreezeRuleSetVersionResponseBody(String ruleSetVersionId, String pointVersionId, String ruleSetCode,
    int versionNo, String status, String contentHash, String frozenBy, String frozenTime)

TestCandidateListItemResponseBody(String releaseId, int releaseNo, String decisionPointId,
    String pointCode, String pointVersionId, String ruleSetVersionId, String environmentCode,
    String status, int version, String contentHash, String createdBy, String createTime,
    Boolean allStaticChecksPassed)
TestCandidateDetailResponseBody(String releaseId, int releaseNo, String decisionPointId,
    String pointCode, String pointVersionId, String ruleSetVersionId, String environmentCode,
    String status, int version, String contentHash, String createdBy, String createTime,
    Boolean allStaticChecksPassed, boolean waitingForShadow,
    FrozenCompositionResponseBody frozenComposition,
    List<ReleaseStateChangeResponseBody> stateChanges,
    List<ReleaseStaticCheckResponseBody> staticChecks)
FrozenCompositionResponseBody(String decisionPointId, String pointCode, String pointVersionId,
    int pointVersionNo, String pointVersionContentHash, String ruleSetVersionId,
    String ruleSetCode, int ruleSetVersionNo, String ruleSetContentHash)
ReleaseStateChangeResponseBody(String auditEventId, String eventTime, String fromStatus,
    String toStatus, String reasonCode, String triggerType, String triggerId,
    String actorId, int aggregateVersion)
ReleaseStaticCheckResponseBody(String auditEventId, String eventTime, String checkCode,
    String checkStatus, String severity, String errorCode, String errorSummary)
InitialStateChangeResponseBody(String auditEventId, String eventTime, String fromStatus,
    String toStatus, String reasonCode, String triggerType, String actorId, int aggregateVersion)
ValidationCheckResponseBody(String checkCode, String checkStatus, String severity,
    String errorCode, String errorSummary)
CreateTestCandidateResponseBody(String releaseId, int releaseNo, String decisionPointId,
    String pointCode, String pointVersionId, String ruleSetVersionId, String environmentCode,
    String status, int version, String contentHash, String createdBy, String createTime,
    InitialStateChangeResponseBody initialStateChange)
ValidateTestCandidateResponseBody(String releaseId, int releaseNo, String environmentCode,
    String status, int version, String contentHash, boolean allStaticChecksPassed,
    List<ValidationCheckResponseBody> checks)
```

- [ ] **Step 8: Apply the complete request null and validation matrix**

Apply this complete request null/validation matrix, using the constants exactly as declared in the Owner API (`CODE=^[A-Z][A-Z0-9_]{0,63}$`, `FIELD_CODE=^[a-z][a-z0-9_]{0,63}$`, `NODE_KEY=^[\\x21-\\x7E]{1,64}$`, `POSITIVE_DECIMAL_ID=^[1-9][0-9]{0,18}$`, `CONTENT_HASH=^[0-9a-f]{64}$`):

| Request field | Null | Jakarta / constructor contract |
|---|---:|---|
| `pointCode`, `ruleSetCode`, `ruleCode`, `actionCode` | no | `@NotBlank @Size(max=64) @Pattern(CODE)` |
| `ruleSetName`, `ruleName` | no | `@NotBlank @Size(max=255)` |
| `changeReason` | no | `@NotBlank @Size(max=500)` |
| `baseRuleSetVersionId` | yes, property required | `@Pattern(POSITIVE_DECIMAL_ID)` |
| `baseContentHash` | yes, property required | `@Pattern(CONTENT_HASH)`; both Base fields are null together or non-null together, forwarded for Risk’s authoritative concurrency check |
| create `rules` | no | `@NotNull @Size(min=1,max=200) List<@NotNull @Valid RuleRequestBody>` plus `requestSnapshot` |
| Rule `action` | no | `@NotNull @Valid RuleActionRequestBody` |
| `priorityNo`, `sequenceNo`, `valueSequence`, `actionVersionNo`, `expectedVersion` | no | `@NotNull @Positive Integer`; strict converter accepts integral JSON number only |
| `enabledFlag` | no | `@NotNull Boolean`; strict converter accepts JSON Boolean only |
| `reasonCode` | yes, property required | `@Size(max=64) @Pattern(CODE)` |
| `conditionNodes` | no | `@NotNull @Size(min=1,max=2000) List<@NotNull @Valid ConditionNodeRequestBody>` plus `requestSnapshot` |
| `nodeKey` | no | `@NotBlank @Pattern(NODE_KEY)` |
| `parentNodeKey` | yes, property required | `@Pattern(NODE_KEY)` |
| `nodeType` | no | `@NotBlank @Pattern(^(GROUP|PREDICATE)$)` |
| `groupOperator` | yes, property required | `@Pattern(^(AND|OR|NOT)$)` |
| `comparisonOperator` | yes, property required | `@Pattern(^(EQ|NE|GT|GTE|LT|LTE|IN|NOT_IN|BETWEEN|IS_NULL|IS_NOT_NULL)$)` |
| `operands` | no | `@NotNull @Size(max=101) List<@NotNull @Valid OperandRequestBody>` plus `requestSnapshot` |
| `operandRole` | no | `@NotBlank @Pattern(^(LEFT|RIGHT)$)` |
| `sourceType` | no | `@NotBlank @Pattern(^(CONTEXT|LITERAL)$)` |
| `fieldCode` | yes, property required | `@Size(max=64) @Pattern(FIELD_CODE)` |
| `valueType` | no | `@NotBlank @Pattern(^(STRING|NUMBER|BOOLEAN|DATETIME|DURATION)$)` |
| `literalValue` | yes, property required | exact runtime type is String, Boolean or null; numeric JSON is rejected before binding |
| Freeze `expectedContentHash` | no | `@NotNull @Pattern(CONTENT_HASH)` |
| Create Candidate `ruleSetVersionId` | no | `@NotNull @Pattern(POSITIVE_DECIMAL_ID)` |
| Create Candidate `expectedRuleSetContentHash` | no | `@NotNull @Pattern(CONTENT_HASH)` |

`@JsonProperty(isRequired=TRUE)` applies to every property in the four command roots and all their nested records, including nullable properties. Query records are not request-body JSON: Rule Set query has nullable `pointCode/ruleSetCode/status`, Candidate query has nullable `pointCode/status`, with CODE and closed status patterns. Every response record uses `@JsonInclude(ALWAYS)` and defensive copies for every List.

- [ ] **Step 9: Apply the complete response null and Candidate-state matrix**

The response null matrix is also closed: all properties are non-null except `RuleRequestBody.reasonCode`, `parentNodeKey`, `groupOperator`, `comparisonOperator`, `fieldCode`, `literalValue`, Rule Set `frozenBy/frozenTime`, Candidate `allStaticChecksPassed`, state-change `fromStatus`, and check `errorCode/errorSummary`. Candidate state-change `reasonCode` is always non-null and is validated against the exact state chain below. FROZEN Rule Sets require both frozen fields non-null; DRAFT and RETIRED require both null. GROUP/PREDICATE and CONTEXT/LITERAL null combinations follow the backend contract. Only the initial state change has null `fromStatus`. Passed checks require `INFO/null/null`; failed checks require `ERROR/nonblank/nonblank`. The adapter, not record constructors, enforces state-dependent combinations.

Candidate response combinations are exact:

| Shape | Required state |
|---|---|
| List DRAFT | `version=1`, `allStaticChecksPassed=null` |
| List VALIDATING | `version=2`, checks-derived value null or true, never false |
| List REJECTED | `version=3`, `allStaticChecksPassed=false` |
| Detail DRAFT | one creation state, zero checks, derived null, `waitingForShadow=false` |
| Detail VALIDATING recovery | two states, zero checks, derived null, `waitingForShadow=false` |
| Detail VALIDATING passed | two states, eight ordered PASSED checks, derived true, `waitingForShadow=true` |
| Detail REJECTED | three states, eight ordered checks with at least one FAILED, derived false, `waitingForShadow=false` |
| Create response | `TEST/DRAFT/version=1` and initial `null→DRAFT/CANDIDATE_CREATED/OPERATIONS_COMMAND/aggregateVersion=1` |
| Validate response | true iff `VALIDATING/version=2` with eight PASSED; false iff `REJECTED/version=3` with at least one FAILED |

Candidate Detail state changes are exactly: aggregate 1 `null -> DRAFT/CANDIDATE_CREATED`; aggregate 2 `DRAFT -> VALIDATING/STATIC_VALIDATION_STARTED` when validation has begun; and, only for REJECTED, aggregate 3 `VALIDATING -> REJECTED/STATIC_VALIDATION_FAILED`. Every event uses `triggerType=OPERATIONS_COMMAND`, a positive-decimal `auditEventId/actorId` and a 64-lowercase-hex `triggerId`. Events 2 and 3 must have the same actor and trigger; event 1 belongs to the independent Candidate Create command and need not share either. The Create response’s `initialStateChange` has the same aggregate-1 constants but intentionally has no `triggerId` property.

- [ ] **Step 10: Implement the Risk request-shape validator**

`RiskRequestShapeValidator` compares exact key sets recursively for the four command roots and checks only non-null JSON token kinds: integral in-range JSON numbers for `priorityNo/sequenceNo/valueSequence/actionVersionNo/expectedVersion`, booleans for `enabledFlag`, strings for scalar text, arrays/objects at structural positions, and string/boolean/null only for `literalValue`. A present JSON null is accepted into every declared property—even a field whose validation contract is non-null—so `@NotNull`/`@NotBlank` and element validation can produce 422. Missing properties, unknown properties, duplicate/trailing roots and wrong non-null token kinds remain 400. Null array elements likewise survive request snapshotting for element-level 422. `GROUP.operands=[]`, `PREDICATE.operands=[]` and other AST-shape combinations are forwarded for Risk’s authoritative semantic decision. Numeric `literalValue` is always a token-kind 400.

- [ ] **Step 11: Install the Risk-only strict request converter**

Create a `WebMvcConfigurer` that inserts one Spring 7/Jackson 3 `JacksonJsonHttpMessageConverter` backed by a copied `tools.jackson.databind.json.JsonMapper` ahead of the default only when its root type is one of:

```java
Set.of(CreateRuleSetVersionRequestBody.class,
    FreezeRuleSetVersionRequestBody.class,
    CreateTestCandidateRequestBody.class,
    ValidateTestCandidateRequestBody.class)
```

Its copied `JsonMapper` enables Jackson 3 `StreamReadFeature.STRICT_DUPLICATE_DETECTION`. Require the whole Body root to be one non-null Object and consume exactly one root; an array, scalar or root JSON null is a 400 shape error. Run `RiskRequestShapeValidator` before binding. This rejects Jackson coercions such as `"1" -> Integer`, `1.0 -> Integer` and `"true" -> Boolean`. Override read capability for exactly these four roots; all existing BFF request types continue using the default converter. `com.fasterxml.jackson.annotation` remains allowed for record annotations required by the existing wire style, but do not import Jackson 2 core/databind classes; do not buffer or reread the HTTP body.

Freeze the browser error boundary in the model and real-HTTP tests:

| Input class | Result after the single PLT-I02 call |
|---|---|
| malformed JSON, root array/scalar/null, second root, duplicate key, unknown/missing property, wrong non-null JSON token, Header/Path/Query error, Validate key mismatch | `400 INVALID_REQUEST`, zero Risk calls |
| present null in a field marked `Null=no` (including `action` and non-null List fields), blank/pattern/size/positive violation, `rules=[]`, `conditionNodes=[]`, any declared maximum-size overrun or null array element | `422 VALIDATION_ERROR` with safe `errors[]`, zero Risk calls |
| Base pairing, GROUP/PREDICATE, CONTEXT/LITERAL and AST identity/connectivity semantics | forward once to Risk; preserve Owner 400/409 result |
| valid Risk 200 with bad shape/token/null/state/sort | `502 UPSTREAM_INVALID_RESPONSE` |

- [ ] **Step 12: Run model GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=RiskDecisionConfigurationModelAutoTest,OperationsHttpBodyNamingAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: strict Risk model and naming tests pass; existing Owner `*BodyV1` names remain unchanged.

### Task 3: Implement the Local Port and Strict Owner Adapter

**Files:**
- Create: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/integration/RiskDecisionConfigurationClient.java`
- Create: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/integration/impl/RiskDecisionConfigurationClientImpl.java`
- Create: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/simulator/RiskDecisionConfigurationSimulator.java`
- Create: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskDecisionConfigurationClientAutoTest.java`

**Interfaces:**
- Consumes: BFF-local records, `OperatorContext`, `DecisionRuleSetManagementApi` and `DecisionReleaseManagementApi`.
- Produces: nine local methods with no Owner DTO leakage.

- [ ] **Step 1: Build the stateful loopback Risk test simulator**

In `RiskDecisionConfigurationSimulator`, serve all nine Owner paths on a random `127.0.0.1` port. Keep an append-only request journal and deterministic modes `SUCCESS`, `PROBLEM`, `MALFORMED_SUCCESS`, `REDIRECT`, `TIMEOUT` and `DISCONNECT`. The simulator is test-only, resets deterministically and records enough request data for exact forwarding assertions without emitting payloads to application logs.

- [ ] **Step 2: Add exact forwarding and successful-map RED tests**

In `RiskDecisionConfigurationClientAutoTest`, instantiate the planned strict proxy and adapter against the simulator. For all nine methods, require exact actor/session/request-ID/key/query/body forwarding, one upstream call and every successful response field mapped to its BFF-local counterpart. Require complete Authoring Handler validation while asserting the three Handler fields are absent from the returned browser model.

- [ ] **Step 3: Add strict upstream-failure and correlation RED tests**

Exercise `PROBLEM`, `MALFORMED_SUCCESS`, `REDIRECT`, `TIMEOUT` and `DISCONNECT`; require strict RFC 9457 propagation, malformed-success rejection and one call/no retry. Add valid-shape-but-different success fixtures for every request/response correlation listed in Step 11 and require `UPSTREAM_INVALID_RESPONSE`. Add a FAILED-check fixture whose summary contains a distinctive Literal canary and require rejection with the canary absent from exposed output and captured logs.

- [ ] **Step 4: Add the AST non-ownership adapter RED test**

Construct one Save request containing duplicate `nodeKey` values. Require the adapter to forward it exactly once without reordering or local semantic rejection, then propagate Risk’s RFC 9457 semantic 400. This is the executable proof that the BFF does not own AST identity validation.

- [ ] **Step 5: Run adapter RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=RiskDecisionConfigurationClientAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the port/adapter do not exist.

- [ ] **Step 6: Define the exact port**

```java
public interface RiskDecisionConfigurationClient {
    RuleAuthoringContextResponseBody authoringContext(OperatorContext context, String pointCode);
    PagingResponseBody<RuleSetListItemResponseBody> ruleSets(OperatorContext context, int page, int size,
        RuleSetQueryRequestBody query);
    RuleSetDetailResponseBody ruleSet(OperatorContext context, String ruleSetVersionId);
    CreateRuleSetVersionResponseBody createRuleSetVersion(OperatorContext context, String idempotencyKey,
        CreateRuleSetVersionRequestBody request);
    FreezeRuleSetVersionResponseBody freezeRuleSetVersion(OperatorContext context, String idempotencyKey,
        String ruleSetVersionId, FreezeRuleSetVersionRequestBody request);
    PagingResponseBody<TestCandidateListItemResponseBody> candidates(OperatorContext context, int page, int size,
        CandidateQueryRequestBody query);
    TestCandidateDetailResponseBody candidate(OperatorContext context, String releaseId);
    CreateTestCandidateResponseBody createCandidate(OperatorContext context, String idempotencyKey,
        CreateTestCandidateRequestBody request);
    ValidateTestCandidateResponseBody validateCandidate(OperatorContext context, String idempotencyKey,
        String releaseId, ValidateTestCandidateRequestBody request);
}
```

- [ ] **Step 7: Implement explicit request mapping**

Construct Owner request records field-by-field. Recursively map Rules, nodes, operands and action without reflective conversion or JSON round-tripping. Pass `context.accountId()` and `context.sessionId()` to every call and the original key to each POST.

- [ ] **Step 8: Implement strict scalar and closed-code validation**

Use compiled patterns for positive decimal IDs, stable uppercase codes, field codes, 64-character lowercase hex hashes and UTC microsecond timestamps `YYYY-MM-DDTHH:mm:ss.SSSSSSZ`; also parse timestamps as real `Instant` values. Every violation must execute:

```java
throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
```

Keep the validation matrix in a private static nested validator inside `RiskDecisionConfigurationClientImpl`. Its methods accept only BFF-local values or scalar/List values already extracted by the enclosing implementation; it never accepts/returns an Owner DTO and never invokes an Owner accessor itself. Accept only these closed codes: Rule Set status `DRAFT/FROZEN/RETIRED`; Candidate status `DRAFT/VALIDATING/REJECTED`; environment `TEST`; node `GROUP/PREDICATE`; group `AND/OR/NOT`; comparison `EQ/NE/GT/GTE/LT/LTE/IN/NOT_IN/BETWEEN/IS_NULL/IS_NOT_NULL`; value type `STRING/NUMBER/BOOLEAN/DATETIME/DURATION`; operand role `LEFT/RIGHT`; source type `CONTEXT/LITERAL`; trigger `OPERATIONS_COMMAND`; check status `PASSED/FAILED`; severity `INFO/ERROR`.

Require positive-decimal Strings for `ruleSetVersionId`, `pointVersionId`, `releaseId`, `decisionPointId`, `auditEventId`, `actorId`, `createdBy` and non-null `frozenBy`; positive Integers for all versions, release numbers, priorities and sequences; `aggregateVersion` starting at 1; and exactly 64 lowercase hexadecimal characters for `triggerId`.

- [ ] **Step 9: Implement state-dependent response validation**

Require Rule Set frozen fields to be both valid non-null actor/time values only for FROZEN and both null for DRAFT/RETIRED. Candidate DRAFT is version 1 with `allStaticChecksPassed=null`; its Detail has creation only, zero checks and `waitingForShadow=false`. VALIDATING is version 2 with derived value null or true, never false; recovery has creation plus validation-start, zero checks and `waitingForShadow=false`, while passed validation has those two states, eight ordered PASSED/INFO/null/null checks, derived true and `waitingForShadow=true`. REJECTED is version 3 with creation, validation-start and validation-failed states, eight ordered checks including at least one FAILED/ERROR/nonblank-code/canonical-summary check, derived false and `waitingForShadow=false`. Every state change uses `triggerType=OPERATIONS_COMMAND`; only creation has null `fromStatus`, and validation-start plus validation-failed share actor and trigger. Create is exactly `TEST/DRAFT/version=1`; Validate is exactly VALIDATING/version 2/all-passed true or REJECTED/version 3/all-passed false. Reject every other null, state or transition combination as `UPSTREAM_INVALID_RESPONSE`.

- [ ] **Step 10: Implement page, uniqueness and wire-order validation**

Require a page response to equal the requested `page/size`, have `total >= 0` and `items.size() <= size`, be empty when `(page-1)*size >= total`, be nonempty otherwise, and satisfy `(page-1)*size + items.size() <= total`. Item primary IDs must be unique within the page. Rule details have unique rule codes, node keys per rule and `(operandRole,valueSequence)` per node; Candidate audit/check IDs are unique.

Require Rule Set pages in `createTime DESC, numeric ruleSetVersionId DESC`; Candidate pages in `createTime DESC, numeric releaseId DESC`; Rule detail in `priorityNo ASC, ruleCode ASC`, node preorder with siblings in `sequenceNo ASC` and numeric node-key tie-break, and operands in `LEFT` before `RIGHT` then `valueSequence ASC`. Require Authoring fields in `fieldCode ASC`, actions in `actionCode ASC, versionNo ASC`, operators equal to the fixed 11-code sequence, Candidate state changes in `aggregateVersion ASC, auditEventId ASC` and static checks in the fixed eight-code order. Validate before copying and preserve the upstream order exactly; never sort malformed data into acceptability.

- [ ] **Step 11: Implement all nine request/response correlation checks**

Validate every correlation after shape/state validation and before returning browser models:

- Authoring response `point.pointCode` equals the requested `pointCode`;
- every Rule Set list item matches each non-null `pointCode`, `ruleSetCode` and `status` filter;
- Rule Set detail `ruleSetVersionId` equals the path ID;
- Save response `ruleSetCode` equals the request value and is the newly created `DRAFT` shape;
- Freeze response `ruleSetVersionId` equals the path ID and `contentHash` equals `expectedContentHash`;
- every Candidate list item matches each non-null `pointCode` and `status` filter;
- Candidate detail `releaseId` equals the path ID, and its top-level decision-point/point-version/rule-set identities equal `frozenComposition`;
- Candidate Create response `ruleSetVersionId` equals the request value;
- Validate response `releaseId` equals the path ID; its already-closed state/version matrix must also be the valid successor of the request `expectedVersion` (`VALIDATING=expected+1`, `REJECTED=expected+2`).

- [ ] **Step 12: Implement canonical FAILED-summary validation**

For every FAILED static check, require length at most 1,000 and exact grammar `first=<ERROR_CODE>@<SAFE_PATH>; violation_count=<POSITIVE_INT>`, with the captured first code equal to `errorCode`. `SAFE_PATH` is only `-`; one of `actionParameters,businessCoordinates,closure,duplicates,features,ids,inputFields,nodes,obligations,operands,persistedActions,point,release,ruleActions,rules,ruleSetBindings,ruleSets`; `node[._][1-9][0-9]*`; `inputFields/[a-z][a-z0-9_]{0,63}`; or `(actionVersions|nodes|operands|rules)/[1-9][0-9]*`. Parse the count as a positive Java integer. Anything else—including an extra suffix or Literal canary—is `UPSTREAM_INVALID_RESPONSE` and must never reach the browser or logs.

- [ ] **Step 13: Validate full Authoring Context before safe projection**

Require descriptor `CHECKOUT_PREPARE_AUTHORING_V1` and Point tuple `pointCode=CHECKOUT_PREPARE`, `pointName=Checkout Prepare`, `businessDomain=CHECKOUT`, `inputContractCode=ORDER_PREPARATION`, `inputContractVersion=4`, `outputContractCode=RISK_DECISION_RESULT`, `defaultDecision=REVIEW`, `evaluationStrategy=FIRST_TERMINAL`, `failureStrategy=FAIL_CLOSED`, `timeoutMillis=500`, `status=ACTIVE`.

Require unique Field codes and Action code/version pairs. Every Field has `dataClassification=INTERNAL`, `requiredFlag=true`, `ruleReferenceFlag=true`, `actionParameterFlag=false` and value type in `STRING/NUMBER/BOOLEAN/DATETIME/DURATION`. Every Action has `actionCategory=DECISION`, `terminalFlag=true`, `mergePolicy=MOST_RESTRICTIVE`, `shadowSupportedFlag=true`, `status=ACTIVE`, and only these paired public semantics: `ALLOW_CHECKOUT/ALLOW`, `REVIEW_CHECKOUT/REVIEW`, `DENY_CHECKOUT/DENY`; version is positive.

For every upstream Action, require valid nonblank `handlerCode`, `implementationVersion` and 64-hex `implementationHash` in addition to those public relations. Return a new browser `DecisionActionDescriptorResponseBody` containing only:

```text
actionCode, actionName, actionCategory, versionNo, terminalFlag, decisionCode,
mergePolicy, shadowSupportedFlag, status
```

Validate `features`, `actionParameters`, `obligations` and `limitBindings` as non-null and exactly empty lists; any element or nonempty list is an upstream contract violation. Do not silently replace null with empty. The RED tests mutate every Point scalar, Field flag/classification/type and Action category/decision/merge/terminal/shadow/status relation independently so no unknown-but-nonblank code passes a generic string check.

- [ ] **Step 14: Run adapter GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=RiskDecisionConfigurationClientAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all nine direct client/adapter mappings, numeric-ID tie breaks (`9` versus `10`), page boundaries, state/null matrices, correlations and transport failure cases pass.

### Task 4: Publish the Nine Concrete Controller Methods

**Files:**
- Create: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/ext/rest/RiskDecisionConfigurationController.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/ext/rest/OperationsRequestInterceptor.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/OperationsErrorStatusResolver.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsConsoleBffApplicationAutoTest.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsBrowserBoundaryAutoTest.java`
- Create: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskDecisionConfigurationBrowserBoundaryAutoTest.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/base/OperationsBffHttpTestBase.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/configuration/OperationsBffTestConfiguration.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/simulator/PlatformOperationsSimulator.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/simulator/RiskDecisionConfigurationSimulator.java`

**Interfaces:**
- Consumes: `RiskDecisionConfigurationClient` and BFF-local models.
- Produces: exact downstream paths `/decision-rule-sets/v1/**` and `/decision-releases/v1/**` for Gateway.

- [ ] **Step 1: Add exact route-assembly RED assertions**

In `OperationsConsoleBffApplicationAutoTest`, require exactly these mappings and no wildcard `/risk/**` handler:

```text
GET  /decision-rule-sets/v1/authoring-context/{pointCode}
GET  /decision-rule-sets/v1/rule-sets
GET  /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}
POST /decision-rule-sets/v1/rule-sets
POST /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze
GET  /decision-releases/v1/candidates
GET  /decision-releases/v1/candidates/{releaseId}
POST /decision-releases/v1/candidates
POST /decision-releases/v1/candidates/{releaseId}/validate
```

Require a concrete `RiskDecisionConfigurationController` and no class named `*Risk*Processor`, `*Risk*Service` or `*Risk*Repository` in the BFF production package.

- [ ] **Step 2: Register isolated Platform and Risk test modes**

Register `RiskDecisionConfigurationSimulator` through `operations.risk-test-base-url`. Add an isolated `RISK_ACCOUNT_ID="10001"` mode to `PlatformOperationsSimulator` without changing its default `account_10001` fixture. Wire both simulators into `OperationsBffHttpTestBase` and `OperationsBffTestConfiguration`, including deterministic reset and close hooks.

- [ ] **Step 3: Add representative authorization and forwarding RED tests**

In `RiskDecisionConfigurationBrowserBoundaryAutoTest`, add four real-HTTP cases: successful Authoring Context with Platform journal sequence lower than the single Risk call; denied Authoring Context with one Platform call and zero Risk calls; successful Save with exact actor/session/request-ID/key/body forwarding; and Validate with a format-valid but nondeterministic key returning RFC 9457 400 after one Platform call and zero Risk calls.

- [ ] **Step 4: Add PLT-I02-before-protocol-validation RED tests**

Add an invalid Origin case on an authenticated Risk GET and invalid/missing/non-JSON Content-Type cases on an authenticated Risk POST. Each must prove exactly one completed Platform authorization before zero Risk calls. These cases are RED against the current interceptor ordering.

- [ ] **Step 5: Run route RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=OperationsConsoleBffApplicationAutoTest,RiskDecisionConfigurationBrowserBoundaryAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the nine mappings and Controller do not exist.

- [ ] **Step 6: Create the concrete Controller and exact query-key helper**

Create `RiskDecisionConfigurationController` with injected `RiskDecisionConfigurationClient`, direct parameter constraints and two explicit base mappings: `/decision-rule-sets/v1` and `/decision-releases/v1`. Do not add class-level `@Validated`: executable browser-boundary RED proved that it diverts direct parameter violations into `ConstraintViolationException` and bypasses the Foundation MVC RFC 9457 mapping, while Spring MVC's built-in Handler Method Validation retains the same annotations and maps them through `HandlerMethodValidationException`. Add one private helper that requires actual query keys to be a case-sensitive subset of the method’s exact allowlist. Every Controller method invokes this helper as its first statement; non-list endpoints pass the empty set. Any unknown or case-changed query key becomes 400 after PLT-I02 and before Risk. Do not add a wildcard mapping or environment query.

- [ ] **Step 7: Add Authoring Context**

Implement `GET /decision-rule-sets/v1/authoring-context/{pointCode}`. Require an empty query-key set, apply `@Pattern("^[A-Z][A-Z0-9_]{0,63}$")` to `pointCode`, and forward once through `client.authoringContext`.

- [ ] **Step 8: Add Rule Set List**

Implement `GET /decision-rule-sets/v1/rule-sets?page&size&pointCode&ruleSetCode&status`. Allow only `page,size,pointCode,ruleSetCode,status`; omitted filters remain valid. Use `Integer page/size` and immediately call `PagingRequestBody.of(page,size)`. Apply `@Pattern("^[A-Z][A-Z0-9_]{0,63}$")` to non-null `pointCode/ruleSetCode` and `@Pattern("^(DRAFT|FROZEN|RETIRED)$")` to non-null status, then forward once.

- [ ] **Step 9: Add Rule Set Detail**

Implement `GET /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}` with an empty query-key set and `@Pattern("^[1-9][0-9]{0,18}$")` on the path ID, then forward once.

- [ ] **Step 10: Add Rule Set Save**

Implement `POST /decision-rule-sets/v1/rule-sets` with an empty query-key set, `@NotNull @Valid @RequestBody CreateRuleSetVersionRequestBody`, and `Idempotency-Key` annotated `@NotBlank @Size(min=16,max=128) @Pattern("^[A-Za-z0-9_-]{16,128}$")`. Forward the original key and decoded body once.

- [ ] **Step 11: Add Rule Set Freeze**

Implement `POST /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze` with an empty query-key set, positive-decimal path validation, `@NotNull @Valid @RequestBody FreezeRuleSetVersionRequestBody`, and the exact Save idempotency-key annotations. Forward the original path, key and body once.

- [ ] **Step 12: Add Candidate List**

Implement `GET /decision-releases/v1/candidates?page&size&pointCode&status`. Allow only `page,size,pointCode,status`, use `Integer page/size` and immediately call `PagingRequestBody.of(page,size)`, apply CODE to non-null `pointCode` and `@Pattern("^(DRAFT|VALIDATING|REJECTED)$")` to non-null status, then forward once.

- [ ] **Step 13: Add Candidate Detail**

Implement `GET /decision-releases/v1/candidates/{releaseId}` with an empty query-key set and `@Pattern("^[1-9][0-9]{0,18}$")` on `releaseId`, then forward once.

- [ ] **Step 14: Add Candidate Create**

Implement `POST /decision-releases/v1/candidates` with an empty query-key set, `@NotNull @Valid @RequestBody CreateTestCandidateRequestBody`, and the exact Save idempotency-key annotations. The body’s `ruleSetVersionId` remains positive-decimal validated by the model; forward once and do not accept an environment query.

- [ ] **Step 15: Add Candidate Validate forwarding**

Implement `POST /decision-releases/v1/candidates/{releaseId}/validate` with an empty query-key set, positive-decimal `releaseId`, `@NotNull @Valid @RequestBody ValidateTestCandidateRequestBody`, and the exact Save idempotency-key annotations. Leave the additional deterministic-key equality check to Step 17; do not call Risk more than once.

All query-key, path/query/header and paging violations from Steps 7–15 are 400 after PLT-I02 and before Risk.

- [ ] **Step 16: Move protected-request protocol validation behind PLT-I02**

Refactor `OperationsRequestInterceptor.preHandle` without changing the public Login/Refresh contract. For public authentication requests, retain Origin/Content-Type validation before returning. For every protected Handler method, first resolve the trusted operator context, call `client.authorize(context)` and store the context attribute; only then validate Origin and, for non-read methods, Content-Type. No request body is read in the interceptor. Add ordering assertions proving invalid Origin and invalid/missing/non-JSON Content-Type each produce exactly one completed PLT-I02 call followed by zero Risk calls. Update the existing protected Account delete/enable and Role delete invalid-Origin assertions to the same one-PLT/zero-business-command contract; executable full browser regression exposed their former zero-PLT expectation as an obsolete oracle. Authentication/context failure still stops before PLT-I02; PLT-I02 denial stops before Origin/Content-Type and body binding.

- [ ] **Step 17: Enforce the deterministic Validate key after PLT-I02**

Inside only the Validate Controller method, before calling the port:

```java
String expectedKey = "risk-validate-v1-" + releaseId + "-" + request.expectedVersion();
if (!expectedKey.equals(idempotencyKey)) {
    throw new InvalidParameterException("Idempotency-Key 与 releaseId、expectedVersion 不一致");
}
```

Do not rewrite the supplied key. Because the existing interceptor executes first, this check happens after authorization.

- [ ] **Step 18: Map Risk not-found to 404**

Extend `OperationsErrorStatusResolver`:

```java
if (errorCode == RiskDecisionConfigurationErrorCode.RISK_DECISION_CONFIGURATION_NOT_FOUND) {
    return HttpStatus.NOT_FOUND;
}
```

Retain default Foundation mappings for common 400/409/500/502 and existing BFF 503/504.

- [ ] **Step 19: Run route and representative browser-boundary GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=OperationsConsoleBffApplicationAutoTest,RiskDecisionConfigurationBrowserBoundaryAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all nine exact mappings and architecture assertions pass, the representative GET/POST forward once, and PLT-I02 denial/key mismatch still produce zero Risk calls.

### Task 5: Prove PLT-I02-First, Strict Mapping and Zero-Call Failures

**Files:**
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskDecisionConfigurationBrowserBoundaryAutoTest.java`
- Create: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsRiskDiscoveryUnavailableAutoTest.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/base/OperationsBffHttpTestBase.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/configuration/OperationsBffTestConfiguration.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/simulator/PlatformOperationsSimulator.java`

**Interfaces:**
- Consumes: the real random-port BFF chain, real Controllers/interceptor/client adapter and two stateful protocol simulators.
- Produces: request journals that prove call order, exact forwarding, no retry and zero Risk calls on every local rejection.

- [ ] **Step 1: Expand the direct adapter’s Risk loopback protocol simulator**

Extend the registered Risk simulator so its append-only in-memory journal contains method, path, raw query, actor, session, request ID, idempotency key and call sequence; request bodies remain test-process-only for exact assertions. Reset its state before each browser-boundary case and close it with the test application.

- [ ] **Step 2: Add the browser-boundary discovery-unavailable test**

Create `OperationsRiskDiscoveryUnavailableAutoTest`: start the real random-port BFF with successful Platform authorization, omit `operations.risk-test-base-url`, expose no `risk-service` discovery instance, call Authoring Context, and require RFC 9457 503/code `DEPENDENCY_UNAVAILABLE`, exactly one PLT-I02 call, no retry and no discovery address in the response.

- [ ] **Step 3: Complete both simulator modes and prove actor reset**

Keep Dynamic Properties `operations.platform-test-base-url` and `operations.risk-test-base-url` plus both close hooks. Keep `PlatformOperationsSimulator.ACCOUNT_ID="account_10001"` as its reset default and `RISK_ACCOUNT_ID="10001"` as the scoped Risk identity. Bind the five permission IDs and nine route codes exactly as Platform does. Add an after-each assertion that `useRiskIdentity()` returns to the original identity, while existing account paths, self-account checks and CSV fixtures remain byte-for-byte unchanged.

- [ ] **Step 4: Test all successful maps and Authoring redaction**

For all nine endpoints, hold Platform after its authorization decision is constructed but before any response Header/Body byte is written, send the real browser request asynchronously, and prove for 250 ms that Risk's first-operation arrival latch remains unset, its journal remains empty and the browser future remains incomplete. Release in `finally`, then require exactly one completed Platform authorization before exactly one Risk call, retain the monotonic journal order as supplemental evidence, and compare every response field to the simulator source. Do not treat a single representative route or a post-hoc arrival sequence as the all-route completion proof. For Authoring Context, make upstream Handler fields non-null and distinctive, then assert none of their names or values exists in browser JSON.

- [ ] **Step 5: Test PLT-I02 denial on all nine routes**

For each route, deny PLT-I02 and require exactly one completed Platform authorization plus an unchanged Risk journal. Require denial before query/path/header validation and body conversion.

- [ ] **Step 6: Test exact query allowlists and forwarding**

On all nine authorized routes, add `?probe=x` and require RFC 9457 `400 INVALID_REQUEST`, one prior PLT-I02 call and zero Risk calls. For each list route, prove empty query, `page/size` only, one optional filter, all optional filters and the full allowed set each forward once. Unknown `probe` and case-changed `Page` fail after one PLT-I02 call with zero Risk calls.

- [ ] **Step 7: Test strict body decoding and protected protocol errors**

For each POST, require malformed/duplicate/trailing/unknown/missing/wrong-token cases to return 400; require body-field present-null, blank/pattern/size/positive/list-constraint cases to return 422 with safe external field paths. Direct path, query and header constraint failures—including short or malformed `Idempotency-Key`—return 400 `INVALID_REQUEST` through Spring MVC Handler Method Validation, never 500. Add invalid Origin for GET and invalid/missing/non-JSON Content-Type for POST. Every protected local rejection has exactly one prior PLT-I02 call and zero Risk calls.

- [ ] **Step 8: Test deterministic Validate-key mismatches**

For Validate, use a random-but-format-valid key, a key containing the wrong release ID and a key containing the wrong expected version. Each returns RFC 9457 `400 INVALID_REQUEST` after one PLT-I02 call and with zero Risk calls.

- [ ] **Step 9: Test strict successful-response and correlation failures**

Cover invalid IDs/codes/enums/hash/time/null/page window/duplicates/order, every valid-shape-but-different request/response correlation and unsafe/noncanonical FAILED summaries. Require each case to become 502, with any summary canary absent from browser bodies and captured logs. Assert `200 + REJECTED + eight canonical checks` is a normal success response.

- [ ] **Step 10: Test RFC 9457 isolation and transport failures**

Give every upstream Problem a distinctive valid but different request ID and safe-detail canary. Require the BFF-generated browser Problem header/body request ID to equal the current browser request ID, and require the upstream request ID/detail to be absent from browser JSON and logs. Require a legacy four-field error body to be rejected as a malformed non-RFC-9457 dependency response. Require 502 for malformed success, 503 for unavailable, 504 for timeout, one upstream call per POST and no retry for redirect, timeout or disconnect.

- [ ] **Step 11: Test timeout recovery without caller-owned actor projection**

Add the exact method `validateTimeoutIsNotRetriedAndDetailPreservesRecoveryActorFacts`: time out one Validate request, assert one Risk call and no retry, then require the same strict Candidate Detail—including its original state-change actor facts—to be returned for two differently authenticated callers. The stateless BFF must not invent a recovery coordinate or caller-specific projection; Web owns current-actor comparison and action visibility.

- [ ] **Step 12: Test metadata-only logging**

Use distinctive strings for a literal, an unsafe upstream `errorSummary`, Handler code/hash, full content hash and idempotency key. Exercise success, denial, timeout and malformed response, capture application logs, and assert none of those strings appears in logs or browser Problem bodies. Request ID, route code, status and duration may appear.

Execution dependency discovered by this RED: before Step 13, execute Task 6 Steps 1 and 5 as one TDD slice. First add the exact seven-prefix assertion and prove it fails against the current five-prefix configuration; then append only `/decision-rule-sets` and `/decision-releases`. This ordering is necessary because Step 12 exercises the production logging filter, while those two metadata-only prefixes were originally scheduled later. Do not bring forward Task 6's generic ceiling, final assembly, README or closeout work.

- [ ] **Step 13: Run focused GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=RiskDecisionConfigurationModelAutoTest,RiskDecisionConfigurationClientAutoTest,RiskDecisionConfigurationBrowserBoundaryAutoTest,OperationsRiskDiscoveryUnavailableAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all focused tests pass. Do not add a direct-BFF 413 case.

### Task 6: Complete Configuration, Generic Ceiling and Repository Verification

**Files:**
- Modify: `operations-console-bff/paytech-operations-console-bff-web/src/main/resources/application.yml`
- Modify: `operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/integration/impl/PlatformGatewayConfigurationClientImpl.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsBrowserBoundaryAutoTest.java`
- Modify: `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsConsoleBffApplicationAutoTest.java`
- Modify: `operations-console-bff/README.md`

**Interfaces:**
- Consumes: the completed Risk adapter and Platform’s new generic ceiling.
- Produces: complete application assembly, seven metadata-only prefixes and 65,536-compatible Gateway detail projection.

- [ ] **Step 1: Add metadata-prefix RED assertions**

Before changing production files, update `OperationsConsoleBffApplicationAutoTest` to require the existing five metadata-only prefixes followed by exactly. This Step and Step 5 execute immediately after Task 5 Step 12 so Task 5's real logging test can reach GREEN; retain their evidence under Task 6 and do not repeat or weaken it during the remaining Task 6 work:

Append exactly:

```yaml
      - /decision-rule-sets
      - /decision-releases
```

- [ ] **Step 2: Add generic Gateway-detail ceiling RED assertions**

Update `OperationsBrowserBoundaryAutoTest` with Platform Route Detail fixtures where 65,536 must map successfully and 65,537 must become `UPSTREAM_INVALID_RESPONSE`/502. Use an existing non-Risk Gateway route so the assertion proves a generic protocol ceiling.

- [ ] **Step 3: Add final assembly RED assertions**

In `OperationsConsoleBffApplicationAutoTest`, require the exact nine new handler methods, one `DecisionRuleSetManagementApi` proxy, one `DecisionReleaseManagementApi` proxy, one Risk port implementation, separate Platform/Risk proxy factories and no Risk Processor/Service/Repository/database bean.

- [ ] **Step 4: Run the configuration RED tests**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=OperationsBrowserBoundaryAutoTest,OperationsConsoleBffApplicationAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the new prefix and 65,536 assertions fail; the completed Risk assembly assertions pass.

- [ ] **Step 5: Add the two metadata-only prefixes**

Append the two exact YAML prefixes from Step 1, keeping the existing five in order.

- [ ] **Step 6: Raise the generic Gateway-detail protocol ceiling**

In `PlatformGatewayConfigurationClientImpl`, change only the generic successful-response assertion:

```java
result.bodyLimitBytes() >= 0 && result.bodyLimitBytes() <= 65_536
```

Do not inspect the route path.

- [ ] **Step 7: Run the configuration GREEN tests**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=OperationsBrowserBoundaryAutoTest,OperationsConsoleBffApplicationAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: both test classes pass.

- [ ] **Step 8: Run the full Maven verification**

Run:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw clean verify
```

Expected: the full BFF suite exits zero with the stateful loopback simulators and no external Platform/Risk/Nacos dependency.

- [ ] **Step 9: Check the complete BFF diff**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
git diff --check
```

Expected: the working-tree diff has no whitespace errors.

- [ ] **Step 10: Update the BFF README from verified evidence**

Document the nine paths, PLT-I02-first order, strict Risk client, Handler redaction, deterministic Validate key, metadata-only prefixes and the actual test count from Step 8. State that body-size 413 remains Gateway-owned. Do not assert a mutable local/shared deployment state in this component README; say explicitly that component verification does not prove deployment and route current-state facts belong only to the workspace `agent-docs/CURRENT_IMPLEMENTATION.md` ledger.

- [ ] **Step 11: Inspect exact BFF ownership before staging**

The recorded pre-edit BFF baseline is clean, but recheck it before touching the index. Inspect all existing files changed by this plan:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
git status --short
git diff -- \
  pom.xml \
  paytech-operations-console-bff-web/pom.xml \
  paytech-operations-console-bff-test/pom.xml \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/ext/rest/OperationsRequestInterceptor.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/OperationsErrorStatusResolver.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/integration/impl/PlatformGatewayConfigurationClientImpl.java \
  paytech-operations-console-bff-web/src/main/resources/application.yml \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsHttpBodyNamingAutoTest.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsConsoleBffApplicationAutoTest.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsBrowserBoundaryAutoTest.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/base/OperationsBffHttpTestBase.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/configuration/OperationsBffTestConfiguration.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/simulator/PlatformOperationsSimulator.java \
  README.md
```

If any listed path was already dirty in the Task 1 baseline, keep its unrelated hunks unstaged in Step 13. The recorded clean baseline has no pre-existing planned new path; if that fact changes before execution, stop before staging and revise the literal allowlists so the affected path moves from Step 12 to Step 13 after `git add -N`. Do not run a placeholder or dynamically broaden either command.

- [ ] **Step 12: Stage only the exact whole-file allowlist of newly created BFF files**

Under the recorded clean baseline, these are the only paths eligible for whole-file staging:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
git add -- \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/integration/RiskDiscoveryInterceptor.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskClientConfiguration.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskOwnerResponseShapeValidator.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskOwnerResponseHttpMessageConverter.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/service/model/RiskRuleSetModels.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/service/model/RiskTestCandidateModels.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskRequestShapeValidator.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskRequestJsonConfiguration.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/integration/RiskDecisionConfigurationClient.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/integration/impl/RiskDecisionConfigurationClientImpl.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/ext/rest/RiskDecisionConfigurationController.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskClientConfigurationAutoTest.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskDecisionConfigurationModelAutoTest.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/simulator/RiskDecisionConfigurationSimulator.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskDecisionConfigurationClientAutoTest.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskDecisionConfigurationBrowserBoundaryAutoTest.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsRiskDiscoveryUnavailableAutoTest.java
```

- [ ] **Step 13: Interactively stage every existing or pre-dirty BFF path**

Use interactive staging even though the recorded baseline is clean; accept only this plan’s complete hunks and reject any concurrent change:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
git add -p -- \
  pom.xml \
  paytech-operations-console-bff-web/pom.xml \
  paytech-operations-console-bff-test/pom.xml \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/ext/rest/OperationsRequestInterceptor.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/OperationsErrorStatusResolver.java \
  paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/integration/impl/PlatformGatewayConfigurationClientImpl.java \
  paytech-operations-console-bff-web/src/main/resources/application.yml \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsHttpBodyNamingAutoTest.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsConsoleBffApplicationAutoTest.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/OperationsBrowserBoundaryAutoTest.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/base/OperationsBffHttpTestBase.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/configuration/OperationsBffTestConfiguration.java \
  paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/test/simulator/PlatformOperationsSimulator.java \
  README.md
```

- [ ] **Step 14: Verify the exact staged BFF artifact**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
git diff --cached --check
git diff --cached --name-only
git diff --cached
```

Expected: the staged names are a subset of exactly the 31 paths listed in Steps 12–13, every required completed path is present, and the staged diff contains no unrelated hunk, root plan, generated artifact or `risk-compliance-service` file.

- [ ] **Step 15: Commit only after staged review and active authorization**

If commit authorization is active and Step 14 has been reviewed, run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
git commit -m "feat(operations-bff): proxy Risk rule configuration"
```

Expected: the commit contains only the reviewed BFF adapter slice. Without active authorization, leave this checkbox pending and do not create a commit.
