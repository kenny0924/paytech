# Risk Operations Structured Rule Builder Web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give authorized Operations users a structured, fail-closed UI for immutable Rule Set versions and TEST Candidate static validation without exposing JSON/AST internals.

**Architecture:** Risk browser wire decoders, Authoring catalog matching, editor conversion, observed-head inference, command state and Candidate classification are pure TypeScript modules with Node tests. React components only render and coordinate those pure models; all network calls remain same-origin through the existing `OperationsSession`, and uncommitted rule content remains in memory.

**Tech Stack:** React 19.2.8, TypeScript 7.0.2 strict mode, Vite 8.2.2, Ant Design 6.6.2, React Router 7.18.3, Node `>=22.12.0` `node:test` (current verification runtime 24.6.0), browser `TextEncoder` and Web Crypto UUID.

**Spec:** `docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md`

## Global Constraints

- Register exactly component key `OPERATIONS_RISK_RULE_CONFIGURATION` at frontend route `/risk/rule-configuration`.
- Keep two tabs `rule-sets` and `test-candidates`; on page entry and tab change canonicalize the entire search string to only `?tab=rule-sets|test-candidates`, discarding every other key. Never place rule content, command bodies or idempotency keys in the URL.
- Show a structured builder only. The UI never displays raw JSON/AST, `nodeKey`, `parentNodeKey`, `sequenceNo`, `valueSequence`, `GROUP` or `PREDICATE` terminology.
- Match Authoring Context only to descriptor `CHECKOUT_PREPARE_AUTHORING_V1`, the exact nine fields, eleven operators and three Action code/version pairs. Any unknown, duplicate or missing metadata disables editing.
- Browser Action types and catalog data must not contain `handlerCode`, `implementationVersion` or `implementationHash`.
- Parse successful JSON through the existing duplicate-key/depth/size guard before any Risk decoder; a duplicate key must never be silently overwritten by `JSON.parse`.
- Execute every supplied success decoder inside `OperationsSession.exchange`, while that response's validated `X-Request-Id` is still in scope. A malformed 200 must become `502 INVALID_RESPONSE` with that request ID; never use global or mutable “last request” state.
- Preserve decimal values as strings. Accept only the backend’s `DECIMAL(24,8)` lexical/range contract, normalize to eight fractional digits and reject negative zero, exponent, leading plus, leading zero, more than eight decimals and JavaScript numbers.
- Generate hidden wire node keys deterministically per conversion, continuous sibling sequences and rule priorities `10,20,...`; the operator/operand matrix must exactly match Risk.
- Save warns at 49,152 UTF-8 bytes, allows 65,536 and blocks 65,537. Measure the actual `JSON.stringify` string with `TextEncoder`, not characters or object estimates.
- Save, Freeze and Candidate Create use a random UUID-style key held with the exact path/body for an unknown-result retry. Validate uses only `risk-validate-v1-{releaseId}-{expectedVersion}`.
- Do not automatically replay a POST on 401, timeout or 5xx. Preserve command context only for an explicit same-body retry.
- Infer an observed Rule Set head only from a complete stable `size=200` stream filtered by exact point/code with status omitted; Risk 409 remains authoritative.
- Any FROZEN version can create a Candidate. Only observed latest DRAFT may edit/freeze, observed latest FROZEN may save a new version, and RETIRED is fully read-only.
- Candidate recovery is visible only for `VALIDATING/version=2 + zero checks` when current Account ID equals the actor of the unique `DRAFT -> VALIDATING` state change; reconstruct `expectedVersion=1`.
- Keep rule content out of Local Storage, Session Storage, IndexedDB, console APIs and telemetry. The existing auth session storage is outside the Risk feature and remains unchanged.
- Existing Web files contain unrelated edits, including `Roles.tsx`, several pages, global `styles.css` and an untracked `pageHeading.test.ts`. Patch narrow hunks, use feature-local CSS, and never overwrite or stage unrelated changes.
- The repository has no DOM test framework. Pure model/API tests plus `npm run build` are this track’s automated gate; the local-integration plan owns browser/full-chain smoke evidence.

---

### Task 1: Define Strict Browser Wire Models and Authoring Catalog

**Files:**
- Create: `operations-console-web/src/risk/riskApiModels.ts`
- Create: `operations-console-web/src/risk/riskResponseValidation.ts`
- Create: `operations-console-web/src/risk/riskResponseValidation.test.ts`
- Create: `operations-console-web/src/risk/riskAuthoringCatalog.ts`
- Create: `operations-console-web/src/risk/riskAuthoringCatalog.test.ts`
- Create: `operations-console-web/src/risk/riskDecisionApi.ts`
- Create: `operations-console-web/src/risk/riskDecisionApi.test.ts`
- Modify: `operations-console-web/src/api/paging.ts`
- Modify: `operations-console-web/src/api/paging.test.ts`
- Modify: `operations-console-web/src/api/session.ts`
- Modify: `operations-console-web/src/api/session.test.ts`
- Modify: `operations-console-web/src/hooks/usePaging.ts`

**Interfaces:**
- Consumes: the BFF browser records defined by the BFF plan.
- Produces: exact TypeScript interfaces, nine success decoders, `RiskDecisionApi`, and one versioned authoring display catalog.

- [ ] **Step 1: Write the wire-contract RED fixtures and tests**

Create the test files first. Build one complete valid fixture for each of the nine success roots below, then add the strict negative matrix described in Step 3. The tests may initially import modules that do not exist; that missing-module failure is the intended first RED. Do not create either production model or decoder file before the Step 4 RED run.

- [ ] **Step 2: Freeze the exact target wire contract in RED fixtures**

Keep both production files absent in this step. In the test sources, encode complete fixtures and compile-time expectations for all nine success responses and four command requests against the following target contract; Step 5 creates these exported production declarations only after the Step 4 RED run. Use `string | null` for explicit nullable fields and `readonly` arrays. The Authoring Action target is:

```ts
export interface DecisionActionDescriptor {
  actionCode: string;
  actionName: string;
  actionCategory: string;
  versionNo: number;
  terminalFlag: boolean;
  decisionCode: string;
  mergePolicy: string;
  shadowSupportedFlag: boolean;
  status: string;
}
```

Define every nested shape in the same file so no field/null contract is implicit:

```ts
export type RuleSetStatus = 'DRAFT' | 'FROZEN' | 'RETIRED';
export type CandidateStatus = 'DRAFT' | 'VALIDATING' | 'REJECTED';
export type OperatorCode = 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE'
  | 'IN' | 'NOT_IN' | 'BETWEEN' | 'IS_NULL' | 'IS_NOT_NULL';
export type ValueType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'DATETIME' | 'DURATION';

export interface DecisionPointDescriptor {
  pointCode: string; pointName: string; businessDomain: string;
  inputContractCode: string; inputContractVersion: number; outputContractCode: string;
  defaultDecision: string; evaluationStrategy: string; failureStrategy: string;
  timeoutMillis: number; status: string;
}
export interface DecisionInputFieldDescriptor {
  fieldCode: string; valueType: ValueType; requiredFlag: boolean;
  dataClassification: string; ruleReferenceFlag: boolean; actionParameterFlag: boolean;
}
export interface RuleAuthoringContext {
  descriptorCode: string; point: DecisionPointDescriptor;
  fields: readonly DecisionInputFieldDescriptor[]; operators: readonly OperatorCode[];
  actions: readonly DecisionActionDescriptor[]; features: readonly never[];
  actionParameters: readonly never[]; obligations: readonly never[]; limitBindings: readonly never[];
}
export interface RuleOperand {
  operandRole: 'LEFT' | 'RIGHT'; valueSequence: number;
  sourceType: 'CONTEXT' | 'LITERAL'; fieldCode: string | null;
  valueType: ValueType;
  literalValue: string | boolean | null;
}
export interface RuleConditionNode {
  nodeKey: string; parentNodeKey: string | null; sequenceNo: number;
  nodeType: 'GROUP' | 'PREDICATE'; groupOperator: 'AND' | 'OR' | 'NOT' | null;
  comparisonOperator: OperatorCode | null; operands: readonly RuleOperand[];
}
export interface RuleAction { actionCode: string; actionVersionNo: number; }
export interface RuleDefinition {
  ruleCode: string; ruleName: string; priorityNo: number; enabledFlag: boolean;
  reasonCode: string | null; conditionNodes: readonly RuleConditionNode[]; action: RuleAction;
}
export interface CreateRuleSetVersionRequest {
  pointCode: 'CHECKOUT_PREPARE'; ruleSetCode: string; ruleSetName: string;
  baseRuleSetVersionId: string | null; baseContentHash: string | null;
  changeReason: string; rules: readonly RuleDefinition[];
}
export interface FreezeRuleSetVersionRequest { expectedContentHash: string; }
export interface CreateTestCandidateRequest {
  ruleSetVersionId: string; expectedRuleSetContentHash: string;
}
export interface ValidateTestCandidateRequest { expectedVersion: number; }

export interface RuleSetVersionSummary {
  ruleSetVersionId: string; pointVersionId: string; pointCode: string;
  ruleSetCode: string; ruleSetName: string; versionNo: number; status: RuleSetStatus;
  contentHash: string; createdBy: string; changeReason: string; createTime: string;
  frozenBy: string | null; frozenTime: string | null;
}
export interface RuleSetListItem extends RuleSetVersionSummary {}
export interface RuleSetDetail extends RuleSetVersionSummary { rules: readonly RuleDefinition[]; }
export interface CreateRuleSetVersionResponse {
  ruleSetVersionId: string; pointVersionId: string; ruleSetCode: string;
  versionNo: number; status: 'DRAFT'; contentHash: string; createdBy: string; createTime: string;
}
export interface FreezeRuleSetVersionResponse {
  ruleSetVersionId: string; pointVersionId: string; ruleSetCode: string;
  versionNo: number; status: 'FROZEN'; contentHash: string; frozenBy: string; frozenTime: string;
}

export interface TestCandidateSummary {
  releaseId: string; releaseNo: number; decisionPointId: string; pointCode: string;
  pointVersionId: string; ruleSetVersionId: string; environmentCode: 'TEST';
  status: CandidateStatus; version: number; contentHash: string;
  createdBy: string; createTime: string; allStaticChecksPassed: boolean | null;
}
export interface TestCandidateListItem extends TestCandidateSummary {}
export interface FrozenComposition {
  decisionPointId: string; pointCode: string; pointVersionId: string; pointVersionNo: number;
  pointVersionContentHash: string; ruleSetVersionId: string; ruleSetCode: string;
  ruleSetVersionNo: number; ruleSetContentHash: string;
}
export interface ReleaseStateChange {
  auditEventId: string; eventTime: string; fromStatus: CandidateStatus | null;
  toStatus: CandidateStatus; reasonCode: string; triggerType: 'OPERATIONS_COMMAND';
  triggerId: string; actorId: string; aggregateVersion: number;
}
export interface ReleaseStaticCheck {
  auditEventId: string; eventTime: string; checkCode: string;
  checkStatus: 'PASSED' | 'FAILED'; severity: 'INFO' | 'ERROR';
  errorCode: string | null; errorSummary: string | null;
}
export interface TestCandidateDetail extends TestCandidateSummary {
  waitingForShadow: boolean; frozenComposition: FrozenComposition;
  stateChanges: readonly ReleaseStateChange[]; staticChecks: readonly ReleaseStaticCheck[];
}
export interface InitialStateChange {
  auditEventId: string; eventTime: string; fromStatus: null; toStatus: 'DRAFT';
  reasonCode: string; triggerType: 'OPERATIONS_COMMAND'; actorId: string; aggregateVersion: 1;
}
export interface ValidationCheck {
  checkCode: string; checkStatus: 'PASSED' | 'FAILED'; severity: 'INFO' | 'ERROR';
  errorCode: string | null; errorSummary: string | null;
}
export interface CreateTestCandidateResponse {
  releaseId: string; releaseNo: number; decisionPointId: string; pointCode: string;
  pointVersionId: string; ruleSetVersionId: string; environmentCode: 'TEST';
  status: 'DRAFT'; version: 1; contentHash: string; createdBy: string; createTime: string;
  initialStateChange: InitialStateChange;
}
export interface ValidateTestCandidateResponse {
  releaseId: string; releaseNo: number; environmentCode: 'TEST';
  status: 'VALIDATING' | 'REJECTED'; version: 2 | 3; contentHash: string;
  allStaticChecksPassed: boolean; checks: readonly ValidationCheck[];
}
```

Use these exact exported root names throughout the feature:

```ts
RuleAuthoringContext
RuleSetListItem
RuleSetDetail
CreateRuleSetVersionRequest
CreateRuleSetVersionResponse
FreezeRuleSetVersionRequest
FreezeRuleSetVersionResponse
CreateTestCandidateRequest
ValidateTestCandidateRequest
TestCandidateListItem
TestCandidateDetail
CreateTestCandidateResponse
ValidateTestCandidateResponse
```

No interface or type in `src/risk` may declare the three Handler implementation field names.

The browser enforces the same outer text constraints before serialization: `ruleSetCode`, `ruleCode` and non-null `reasonCode` match `[A-Z][A-Z0-9_]{0,63}`; `ruleSetName` and `ruleName` are trimmed NFC text of 1–255 UTF-16 code units; `changeReason` is trimmed NFC text of 1–500 code units. `ruleSetVersionId`, `pointVersionId`, `releaseId`, `decisionPointId`, `auditEventId`, `actorId`, `createdBy` and non-null `frozenBy` are positive decimal strings; `triggerId` is deliberately excluded and is exactly 64 lowercase hexadecimal characters. Versions/priorities/sequences are positive JSON integers, hashes are exactly 64 lowercase hexadecimal characters, and every nullable field must be present as either its valid scalar or JSON null.

- [ ] **Step 3: Complete strict successful-JSON and decoder RED assertions**

In `session.test.ts`, return a 200 JSON body with a duplicate object key and require `INVALID_RESPONSE` rather than last-value-wins parsing; retain existing maximum-length/depth tests. Add a `200` response with valid header `X-Request-Id: semantic-200`, syntactically valid JSON and a decoder that throws: require one decoder call and `ApiError(502, 'INVALID_RESPONSE', 'semantic-200')`. A malformed request-ID header yields `requestId=''`. Prove the successful second GET after one 401 refresh receives the original decoder exactly once for that second response and retains its second response ID on semantic failure. A supplied decoder must also run for `204` or an explicitly zero-length success with `undefined`, so unexpected emptiness is annotated with the same response ID. Also require `ApiError.fieldErrors` to be an immutable defensive copy of only valid RFC 9457 `{field,message}` entries and a frozen empty array otherwise.

For each response decoder, start from a complete valid fixture and test exact keys, required null presence, scalar token types, positive-decimal IDs, positive versions, 64-lowercase-hex hashes, real UTC microsecond timestamps, array shapes, paging windows and unique identities. The two Detail decoders additionally own all semantics needed to make their payload safe to render: `decodeRuleSetDetail` validates the one-root reachable AST, parent/sequence/depth/limit rules, operand roles/types/operator cardinality and action shape; `decodeCandidateDetail` validates the complete DRAFT/VALIDATING-pending/VALIDATING-passed/REJECTED state-change and eight-check matrices. A semantic mutation in either otherwise-valid HTTP 200 fixture must throw from the supplied decoder and therefore become `ApiError(502, 'INVALID_RESPONSE', '<that-response-id>')`; no component may first discover it after `request()` returns. Candidate Detail's valid fixture uses a `triggerId` containing `a-f` and asserts `^[0-9a-f]{64}$`, proving it is not sent through the decimal-ID decoder. Authoring tests additionally accept each of the five wire `ValueType` values, reject Handler fields, nonempty Feature/Parameter/Obligation/Limit arrays, duplicate field/action/operator, any missing public field, and any deviation from the owner wire order `fieldCode ASC`, `actionCode ASC/versionNo ASC` and the fixed operator sequence.

- [ ] **Step 4: Run decoder/catalog/API RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test \
  src/api/session.test.ts \
  src/api/paging.test.ts \
  src/risk/riskResponseValidation.test.ts \
  src/risk/riskAuthoringCatalog.test.ts \
  src/risk/riskDecisionApi.test.ts
```

Expected: tests fail because strict decoders, catalog matching, paging decoder support and the API wrapper are absent.

- [ ] **Step 5: Guard successful JSON inside the response-local transport boundary**

Export the transport boundary from `session.ts`:

```ts
export type SuccessDecoder<T> = (value: unknown) => T;
export interface RequestClient {
  request<T>(path: string, options?: RequestInit, decode?: SuccessDecoder<T>): Promise<T>;
}
export class OperationsSession implements RequestClient { /* existing implementation */ }
```

Thread the optional synchronous decoder through `request`, the GET-only 401 refresh path and `exchange`. In the successful branch, call a shared `parseJsonValue` that first applies `hasUniqueJsonObjectKeys` and the existing one-megabyte/depth limits, then parses once. Invoke the supplied decoder inside `exchange`, while the response-local validated request ID remains in scope. Treat 204 or explicit zero-length success as `undefined` and still invoke a supplied decoder. Normalize any parse or decoder exception to a fresh `ApiError(502, 'INVALID_RESPONSE', requestId)` without retaining the original exception/message. Reject invalid or duplicate-key JSON identically; do not change Problem/legacy error parsing. Existing callers omit argument three and remain source-compatible. POST still has no automatic retry.

In this same GREEN step, extend `ApiError` with `readonly fieldErrors: readonly { field: string; message: string }[]`. When `readProblem` accepts `errors`, validate the existing length/text/path limits, create and `Object.freeze` each pair and the containing copy, and otherwise use one frozen empty array. Never retain or expose the mutable parsed Problem array.

- [ ] **Step 6: Create the wire models and exact-key defensive decoders**

Create `riskApiModels.ts` with the exact Step 2 exported declarations, then implement `riskResponseValidation.ts`. Use `unknown` input throughout; never cast an unvalidated owner object into a browser model.

Use `unknown` input and small reusable functions:

```ts
type Decoder<T> = (value: unknown) => T;
function exactRecord(value: unknown, keys: readonly string[]): Record<string, unknown>;
function stringValue(value: unknown, pattern: RegExp, maximum: number): string;
function nullableString(value: unknown, pattern: RegExp, maximum: number): string | null;
function arrayValue<T>(value: unknown, decode: Decoder<T>): T[];
```

`exactRecord` compares sorted own keys exactly; raw duplicate JSON keys have already been rejected by `OperationsSession` before the decoder receives `unknown`. Return new objects/arrays instead of casting the source.

Keep the cross-field/graph checks inside the named root decoders. Implement private `validateRuleSetDetailSemantics` and `validateCandidateDetailSemantics` helpers in this module and invoke them before returning their immutable copies. They enforce the exact Task 2 AST matrix and Task 3 Candidate matrix respectively; React and later pure presentation helpers may consume only these validated roots. Do not defer owner-response validation to a `.then`, component, reducer or Detail-to-editor conversion after the transport promise resolves.

Export and test these nine named root decoders; the paging variants pass the corresponding item decoder into the shared page decoder:

```ts
export function decodeRuleAuthoringContext(value: unknown): RuleAuthoringContext;
export function decodeRuleSetPage(value: unknown): PagingResponseBody<RuleSetListItem>;
export function decodeRuleSetDetail(value: unknown): RuleSetDetail;
export function decodeCreateRuleSetVersionResponse(value: unknown): CreateRuleSetVersionResponse;
export function decodeFreezeRuleSetVersionResponse(value: unknown): FreezeRuleSetVersionResponse;
export function decodeCandidatePage(value: unknown): PagingResponseBody<TestCandidateListItem>;
export function decodeCandidateDetail(value: unknown): TestCandidateDetail;
export function decodeCreateCandidateResponse(value: unknown): CreateTestCandidateResponse;
export function decodeValidateCandidateResponse(value: unknown): ValidateTestCandidateResponse;
```

- [ ] **Step 7: Freeze owner wire order separately from the versioned display catalog**

First freeze the exact incoming owner wire order; the strict Authoring decoder validates these arrays without sorting malformed data into acceptability:

```ts
const AUTHORING_WIRE_FIELD_ORDER = [
  'country_code', 'currency', 'discount_amount', 'entity_source_type',
  'industry_code', 'locale', 'mcc_code', 'merchandise_amount', 'net_amount',
];
const AUTHORING_WIRE_ACTION_ORDER = ['ALLOW_CHECKOUT', 'DENY_CHECKOUT', 'REVIEW_CHECKOUT'];
```

Then export `CHECKOUT_PREPARE_AUTHORING_V1` with this separate operator-facing field order:

```ts
['country_code', 'locale', 'industry_code', 'mcc_code', 'currency',
 'merchandise_amount', 'discount_amount', 'net_amount', 'entity_source_type']
```

Exact operators:

```ts
['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NOT_IN', 'BETWEEN', 'IS_NULL', 'IS_NOT_NULL']
```

Its operator-facing Action order at version 1 is:

```ts
['ALLOW_CHECKOUT', 'REVIEW_CHECKOUT', 'DENY_CHECKOUT']
```

The wire match is exact: descriptor `CHECKOUT_PREPARE_AUTHORING_V1`; Point `Checkout Prepare/CHECKOUT/ORDER_PREPARATION/4/RISK_DECISION_RESULT/REVIEW/FIRST_TERMINAL/FAIL_CLOSED/500/ACTIVE`; every Field is required, `INTERNAL`, rule-reference enabled and action-parameter disabled; the six code/text fields use `STRING` and the three amount fields use `NUMBER`; Actions pair `ALLOW_CHECKOUT/DENY_CHECKOUT/REVIEW_CHECKOUT` with names `Allow Checkout/Deny Checkout/Review Checkout` and decisions `ALLOW/DENY/REVIEW` in owner wire order, all with category `DECISION`, version `1`, terminal, merge policy `MOST_RESTRICTIVE`, Shadow-supported and `ACTIVE`.

Freeze this UI-only display table so labels and widgets are executable rather than implementation choices:

| Field | Chinese label | Help text | Widget |
|---|---|---|---|
| `country_code` | 国家/地区 | ISO 3166-1 大写二字母，例如 AE | `COUNTRY_CODE` |
| `locale` | 语言区域 | 规范 BCP 47 标签，例如 ar-AE | `LOCALE` |
| `industry_code` | 行业代码 | 1–64 位平台行业大写代码 | `UPPER_CODE` |
| `mcc_code` | MCC | 保留前导零的四位十进制代码 | `MCC` |
| `currency` | 币种 | ISO 4217 大写三字母，例如 USD | `CURRENCY` |
| `merchandise_amount` | 商品金额 | 最多 16 位整数和 8 位小数 | `DECIMAL` |
| `discount_amount` | 优惠金额 | 最多 16 位整数和 8 位小数 | `DECIMAL` |
| `net_amount` | 净金额 | 最多 16 位整数和 8 位小数 | `DECIMAL` |
| `entity_source_type` | 经营主体来源 | 仅 PLATFORM、CLIENT 或 PARTNER | `ENTITY_SOURCE` |

Action labels are exactly `允许结账/转人工复核/拒绝结账` in the separate UI order `ALLOW_CHECKOUT/REVIEW_CHECKOUT/DENY_CHECKOUT`; operator labels are exactly `等于/不等于/大于/大于等于/小于/小于等于/属于/不属于/介于/为空/不为空`. Catalog data contains only these Chinese labels, the table's help text and widget kind; it must not copy Handler fields. `matchAuthoringContext(context)`—not the transport decoder—enforces the current nine-field STRING/NUMBER matrix after the decoder has already proved owner wire order. It matches Field and Action semantics by exact code/version, then projects them into the declared UI display order; it never compares the incoming arrays directly with display-order arrays and never sorts an invalid wire response into acceptability. It returns an editable catalog only when the full wire contract above, operator set, Action public semantics and four empty arrays match exactly; otherwise it returns a read-only incompatibility reason. Add a decoded, otherwise-valid BOOLEAN/DATETIME/DURATION field fixture and require a read-only incompatibility result rather than a transport failure.

- [ ] **Step 8: Extend paging with a root page decoder and exact filters**

Import `RequestClient` and `SuccessDecoder` from `session.ts`; do not redeclare a narrower interface in `paging.ts`. Keep current call compatibility and use these signatures:

```ts
export type PageDecoder<T> = SuccessDecoder<PagingResponseBody<T>>;

export async function requestPage<T>(path: string, paging: PagingRequestBody,
  signal?: AbortSignal, client: RequestClient = session,
  filters: Readonly<Record<string, string>> = {}, decodePage?: PageDecoder<T>): Promise<PagingResponseBody<T>>;

export async function requestAllPages<T>(path: string, signal: AbortSignal,
  client: RequestClient = session, filters: Readonly<Record<string, string>> = {},
  decodePage?: PageDecoder<T>): Promise<T[]>;

export interface UsePagingResult<T> {
  data: PagingResponseBody<T>;
  loading: boolean;
  error: string;
  refresh: () => void;
  applyFilters: (filters: Readonly<Record<string, string>>) => void;
  pagination: {
    current: number;
    pageSize: number;
    total: number;
    showSizeChanger: true;
    pageSizeOptions: number[];
    onChange: (page: number, size: number) => void;
  };
}
export function usePaging<T>(path: string, decodePage?: PageDecoder<T>): UsePagingResult<T>;
```

Implement one private `requestPageAtExpectedTotal` helper used by both public functions. It accepts the requested page/size, an optional first-page `expectedTotal` and `itemsBeforePage`, then calls `client.request` with a third-argument closure. Inside that response-local closure, apply the supplied whole-page decoder (or the legacy structural cast when absent) and verify page, size, nonnegative total, the exact item count for that page, range bounds, stable total when supplied, and `itemsBeforePage + items.length === total` on the final page. `requestPage` delegates with no expected total and zero prior items. `requestAllPages` freezes the first page's total, supplies it and the accumulated count to every later helper call, and performs no response-derived validation after `client.request` returns; concatenation is the only outer-loop operation. Thus a later-page total change or incomplete final page is normalized while that later response's Request ID remains in scope.

Calls without a decoder retain existing behavior; both Risk list paths must supply one. In `paging.test.ts`, use valid JSON with distinct headers `X-Request-Id: paging-200`, `paging-total-200` and `paging-final-200`: one fixture fails the strict decoder on an extra root/item, one passes that decoder but fails the requested page/range check, one changes total on page two, and one truncates the final page. Every case must yield `502 INVALID_RESPONSE` with the Request ID of the exact rejected response. Prove the named page decoder runs for `requestPage`, every `requestAllPages` fetch and `usePaging`. The explicit `RequestClient` annotation prevents TypeScript from inferring the private-field `OperationsSession` concrete class.

The two list tabs use `usePaging` with their exact path and named page decoder. They build filters only through these types, so no unknown Gateway query can be emitted:

```ts
export type RuleSetFilters = Readonly<Partial<Record<'pointCode' | 'ruleSetCode' | 'status', string>>>;
export type CandidateFilters = Readonly<Partial<Record<'pointCode' | 'status', string>>>;
```

- [ ] **Step 9: Implement the nine-route API wrapper**

Define the exact interface:

```ts
export interface RiskDecisionApi {
  authoringContext(pointCode: string, signal?: AbortSignal): Promise<RuleAuthoringContext>;
  ruleSets(paging: PagingRequestBody, filters: RuleSetFilters, signal?: AbortSignal): Promise<PagingResponseBody<RuleSetListItem>>;
  ruleSet(ruleSetVersionId: string, signal?: AbortSignal): Promise<RuleSetDetail>;
  createRuleSetVersion(payload: string, key: string): Promise<CreateRuleSetVersionResponse>;
  freezeRuleSetVersion(ruleSetVersionId: string, payload: string, key: string): Promise<FreezeRuleSetVersionResponse>;
  candidates(paging: PagingRequestBody, filters: CandidateFilters, signal?: AbortSignal): Promise<PagingResponseBody<TestCandidateListItem>>;
  candidate(releaseId: string, signal?: AbortSignal): Promise<TestCandidateDetail>;
  createCandidate(payload: string, key: string): Promise<CreateTestCandidateResponse>;
  validateCandidate(releaseId: string, payload: string, key: string): Promise<ValidateTestCandidateResponse>;
}
```

Also export the construction boundary:

```ts
export interface RiskRequestClient extends RequestClient {}
export function createRiskDecisionApi(client: RiskRequestClient = session): RiskDecisionApi;
export const riskDecisionApi: RiskDecisionApi;
```

The factory hard-codes exactly these relative paths beneath the existing `/api/operations` session base:

```text
/decision-rule-sets/v1/authoring-context/{pointCode}
/decision-rule-sets/v1/rule-sets
/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}
/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze
/decision-releases/v1/candidates
/decision-releases/v1/candidates/{releaseId}
/decision-releases/v1/candidates/{releaseId}/validate
```

GET calls use the named root decoders as the third `request` argument, exact filter types, encoded path segments and AbortSignal. Rule Set and Candidate list calls pass `decodeRuleSetPage` and `decodeCandidatePage` through the shared paging helpers. POST calls take the already serialized body whose bytes were measured, set `Content-Type: application/json` and the exact `Idempotency-Key`, then call `RequestClient.request` once with the matching decoder; never decode in a `.then` or after `request` returns. A malformed POST success is therefore an unknown command result carrying its validated response ID, and the UI may retry it only explicitly with the identical body/key.

Use a structural fake with no `OperationsSession` dependency or private fields:

```ts
class JournaledRequestClient implements RequestClient {
  async request<T>(path: string, options: RequestInit = {}, decode?: SuccessDecoder<T>): Promise<T> {
    journal.push({ path, options });
    const value = nextRawResponse();
    return decode ? decode(value) : value as T;
  }
}
const fake: RequestClient = new JournaledRequestClient();
createRiskDecisionApi(fake);
```

Tests assert every method/path/query/header/body, all nine calls supply and invoke exactly one decoder, and each makes exactly one fake request with no implicit second serialization or retry.

- [ ] **Step 10: Run focused GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test \
  src/api/session.test.ts \
  src/api/paging.test.ts \
  src/risk/riskResponseValidation.test.ts \
  src/risk/riskAuthoringCatalog.test.ts \
  src/risk/riskDecisionApi.test.ts
```

Expected: all decoder/catalog/API tests pass.

### Task 2: Build the Pure Structured Rule Editor Conversion

**Files:**
- Create: `operations-console-web/src/risk/ruleBuilder.ts`
- Create: `operations-console-web/src/risk/ruleBuilder.test.ts`

**Interfaces:**
- Consumes: strict Rule Set detail, matched authoring catalog and operator/value rules.
- Produces: `detailToEditor(detail)`, `editorToCreateRequest(editor, base)` and canonical decimal/string validation without exposing wire structural fields to React.

- [ ] **Step 1: Write valid round-trip and editor-input RED tests against the target contract**

Create `ruleBuilder.test.ts` first. Feed `detailToEditor` only valid `RuleSetDetail` fixtures already accepted by `decodeRuleSetDetail`: cover one golden rule, a semantic detail→editor→request round trip, and directional golden pairs for `net_amount > 10` and `10 > net_amount`, plus valid context-right IN/BETWEEN/null fixtures. Round trip must preserve every Operand Role and never normalize one direction into the other.

Construct editor models directly for the browser-authoring/output boundary and cover AND/OR child counts 1/2, NOT child counts 0/1/2, depth 12/13, Rule priorities, 200/201 rules, 2,000/2,001 generated nodes, 10,000/10,001 generated operands, IN sizes 1/100/101, BETWEEN sizes 1/2/3 and null operators with zero literals. Duplicate/unreachable/cyclic owner node keys, multiple roots, discontinuous wire sequences and malformed owner operand roles belong only to Task 1 `riskResponseValidation.test.ts`; do not make `detailToEditor` a second owner-response validator. Import the following not-yet-created production contract so this suite is RED before implementation.

```ts
export type EditorCondition =
  | { id: string; kind: 'ALL' | 'ANY' | 'NOT'; children: EditorCondition[] }
  | { id: string; kind: 'CONDITION'; fieldSide: 'LEFT' | 'RIGHT';
      fieldCode: string; operator: string; values: string[] };

export interface EditorRule {
  id: string;
  ruleCode: string;
  ruleName: string;
  enabled: boolean;
  reasonCode: string;
  condition: EditorCondition;
  actionCode: string;
  actionVersionNo: number;
}

export interface RuleSetEditorModel {
  pointCode: 'CHECKOUT_PREPARE';
  ruleSetCode: string;
  ruleSetName: string;
  changeReason: string;
  rules: EditorRule[];
}
```

React renders `ALL/ANY/NOT/CONDITION` as 满足全部/满足任一/不满足/条件; it never displays the wire discriminator values.

- [ ] **Step 2: Run builder RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/ruleBuilder.test.ts
```

Expected: FAIL because conversion and canonical decimal functions do not exist.

- [ ] **Step 3: Implement the discriminated union and strict decimal normalization**

Export:

```ts
export function canonicalDecimal(value: string): string;
```

Accept range `-9999999999999999.99999999..9999999999999999.99999999`, at most 16 integer digits and 8 fractional digits. Normalize `1`, `1.0`, `1.00000000` to `1.00000000` and `0` to `0.00000000`. Reject `-0`, `-0.0`, `+1`, `01`, `1e0`, `.1`, `1.`, `1.000000001` and all number-typed input without converting through `Number`.

- [ ] **Step 4: Implement detail-to-editor reconstruction**

Consume only a `RuleSetDetail` already accepted by `decodeRuleSetDetail`. Index flat nodes by `nodeKey`, rebuild the already-validated one-root tree and recursively order its already-proved continuous siblings. Map wire AND/OR/NOT to ALL/ANY/NOT. For every PREDICATE, preserve the one validated CONTEXT operand's exact `LEFT|RIGHT` role as `fieldSide` and map the ordered opposite-side Literal values without changing operand direction. This function is a pure total transformation over a validated root; owner-response graph, role or cardinality failures are rejected inside the response-local decoder before `request()` returns, where the current Request ID is still available.

- [ ] **Step 5: Implement editor-to-wire preorder conversion**

Define `RuleSetBase` as `{ ruleSetVersionId: string; contentHash: string } | null` and `editorToCreateRequest(editor: RuleSetEditorModel, base: RuleSetBase): CreateRuleSetVersionRequest`. Walk each rule preorder and generate `n1,n2,...` keys. Root parent is null and sequence is 1; every sibling uses `1..N`. Groups emit null comparison and empty operands. Predicates emit the CONTEXT operand at `fieldSide`/sequence 1 and every Literal on the opposite side at sequence `1..N`; null operators emit only the CONTEXT operand at its preserved side. New conditions default to `fieldSide='LEFT'`, but conversion never rewrites a loaded RIGHT-side field. Rule priorities are `(index + 1) * 10`; blank editor `reasonCode` becomes explicit JSON null. Emit every nullable property and every empty array explicitly.

- [ ] **Step 6: Implement field/widget validation**

Require ISO country `[A-Z]{2}`, stable industry code `[A-Z][A-Z0-9_]{0,63}`, MCC `[0-9]{4}`, currency `[A-Z]{3}`, entity source `PLATFORM|CLIENT|PARTNER`, and canonical decimal for three amount fields. Normalize locale only with `Intl.getCanonicalLocales`: accept `en`, `en-US`, `en-us -> en-US`, `zh-hans-cn -> zh-Hans-CN` and `ar-AE`; reject blank, underscore forms, arrays/multiple tags and any `RangeError`. This is immediate browser feedback only; Risk remains authoritative. String supports `EQ/NE/IN/NOT_IN/IS_NULL/IS_NOT_NULL`; Number additionally supports `GT/GTE/LT/LTE/BETWEEN`. Validate both field sides for the supported matrix; do not assume commutativity for any operator.

- [ ] **Step 7: Run GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/ruleBuilder.test.ts
```

Expected: all structural, operator, decimal, limit and golden-round-trip tests pass.

### Task 3: Implement Head Inference, Size/Idempotency and Candidate State Machines

**Files:**
- Create: `operations-console-web/src/risk/ruleSetHead.ts`
- Create: `operations-console-web/src/risk/ruleSetHead.test.ts`
- Create: `operations-console-web/src/risk/riskCommands.ts`
- Create: `operations-console-web/src/risk/riskCommands.test.ts`
- Create: `operations-console-web/src/risk/testCandidateState.ts`
- Create: `operations-console-web/src/risk/testCandidateState.test.ts`

**Interfaces:**
- Consumes: `RiskDecisionApi`, decoded Rule Set/Candidate models and current Account ID.
- Produces: observed-head decisions, exact UTF-8 command envelopes and a fail-closed Candidate presentation state.

- [ ] **Step 1: Write RED tests for head, command and Candidate behavior**

In the three test files, cover 0, 1, 200 and 201 Rule Set items; stable/changing total; duplicate ID/version; wrong point/code; create-time order different from version order; version gaps; head detail mismatch; selected-detail mismatch; and abort between pages. Add ASCII/Chinese/emoji exact-byte cases, random-key reuse/reset rules and deterministic Validate key cases. Candidate presentation tests consume only decoder-valid DRAFT, pending VALIDATING, passed VALIDATING and REJECTED details, cover both current-actor recovery branches, and assert a fixed no-data-leak invariant error for a deliberately impossible internal test double. Malformed owner state/check ordering belongs only to Task 1 `riskResponseValidation.test.ts`. Command tests cover `400/401/403/404/409/413/422/429` as deterministic failures that clear the key, and timeout/network/unrecognized plus `500/502/503/504` as unknown results that retain the exact command.

- [ ] **Step 2: Run all three RED suites**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test \
  src/risk/ruleSetHead.test.ts \
  src/risk/riskCommands.test.ts \
  src/risk/testCandidateState.test.ts
```

Expected: FAIL because the three pure modules do not exist.

- [ ] **Step 3: Implement complete-stream observed head**

Export:

```ts
export async function loadObservedRuleSetHead(api: RiskDecisionApi, pointCode: string,
  ruleSetCode: string, signal: AbortSignal): Promise<RuleSetDetail | null>;
export class ObservedHeadChangedError extends Error {}
export function requireSelectedObservedHead(selected: RuleSetDetail,
  observed: RuleSetDetail | null): RuleSetDetail;
export function requireUnusedRuleSetCode(observed: RuleSetDetail | null): void;
```

Call Rule Set List with exact filters `{pointCode, ruleSetCode}`, page size 200 and no `status`. Require every page total identical, accumulated count equal total, every item point/code exact, and unique version ID/version number. Choose the maximum version number even with gaps, then GET its detail and require matching ID, version, point/code and hash. Any drift throws a safe refresh error and yields no latest-only action.

Return `null` only when the complete stable stream has `total=0`; that is the sole state in which the page may open a first-version editor with a user-entered Rule Set Code.

`requireSelectedObservedHead` returns the observed detail only when selected and observed IDs, version numbers, point/code and hashes are byte-identical; otherwise it throws `ObservedHeadChangedError` and never substitutes the observed detail as an edit/freeze base. `requireUnusedRuleSetCode` permits first creation only for `null`. These are advisory local errors, not fabricated Risk 409 responses.

- [ ] **Step 4: Implement exact command envelopes**

```ts
export const SAVE_WARNING_BYTES = 49_152;
export const SAVE_MAX_BYTES = 65_536;
export function utf8ByteLength(payload: string): number;
export function saveSizeState(payload: string): 'NORMAL' | 'WARNING' | 'TOO_LARGE';
export function validateKey(releaseId: string, expectedVersion: number): string;
export interface PendingCommand { path: string; payload: string; key: string; }
export function randomCommand(path: string, payload: string, previous: PendingCommand | null): PendingCommand;
export type CommandSettlement = 'SUCCESS' | 'DETERMINISTIC_HTTP_FAILURE' | 'UNKNOWN_RESULT';
export function settlePendingCommand(pending: PendingCommand,
  settlement: CommandSettlement): PendingCommand | null;
```

`randomCommand` reuses the key only when path and payload are byte-identical; otherwise use `crypto.randomUUID()`. `settlePendingCommand` clears on confirmed success and deterministic Gateway/BFF 4xx (`400/401/403/404/409/413/422/429`), while timeout, disconnect, unrecognized errors and `500/502/503/504` map to `UNKNOWN_RESULT` and retain the exact path/body/key for an explicit retry. Clearing a key never clears the editor/confirmation state; that behavior belongs to the page reducer. `validateKey` returns exactly `risk-validate-v1-${releaseId}-${expectedVersion}` and never accepts an external override. `saveSizeState` uses `utf8ByteLength`: `NORMAL` below 49,152, `WARNING` from 49,152 through 65,536, and `TOO_LARGE` from 65,537.

- [ ] **Step 5: Complete the exact UTF-8 size classification**

Construct ASCII, Chinese and emoji JSON strings whose encoded sizes are exactly 49,152, 65,536 and 65,537. Require warning only at or above 49,152, send eligibility through 65,536 and rejection from 65,537. Assert `payload.length` differs in multibyte cases to prevent character-count regressions.

- [ ] **Step 6: Implement Candidate classification**

Export the complete presentation contract:

```ts
export type CandidatePresentation =
  | { kind: 'READY_TO_VALIDATE'; expectedVersion: 1 }
  | { kind: 'RECOVERABLE_BY_CURRENT_ACTOR'; expectedVersion: 1; key: string }
  | { kind: 'RECOVERY_OWNED_BY_OTHER_ACTOR'; ownerActorId: string }
  | { kind: 'WAITING_FOR_SHADOW'; checks: readonly ReleaseStaticCheck[] }
  | { kind: 'REJECTED'; checks: readonly ReleaseStaticCheck[] };

export function classifyTestCandidate(detail: TestCandidateDetail,
  currentActorId: string): CandidatePresentation;
```

Enforce the fixed check order:

```ts
['REFERENCE_INTEGRITY', 'AST_SHAPE', 'AST_TYPES', 'FEATURE_DEPENDENCY',
 'HANDLER_HASH', 'ACTION_PARAMETERS', 'TERMINAL_ACTION', 'SYSTEM_OBLIGATION']
```

Classify only these exact combinations:

- DRAFT/v1 has one canonical creation state change, `staticChecks=[]`, `allStaticChecksPassed=null`, `waitingForShadow=false` and is ready with expectedVersion 1.
- VALIDATING/v2 recovery has exactly the canonical creation and DRAFT→VALIDATING changes, `staticChecks=[]`, `allStaticChecksPassed=null`, `waitingForShadow=false`; its second event’s actor owns recovery, and the current actor receives expectedVersion 1 plus the reconstructed deterministic key only on exact ID equality.
- VALIDATING/v2 completion has the same two events, eight ordered `PASSED/INFO/null/null` checks, `allStaticChecksPassed=true` and `waitingForShadow=true`.
- REJECTED/v3 has the canonical third VALIDATING→REJECTED event, eight ordered checks, `allStaticChecksPassed=false`, `waitingForShadow=false`, and at least one `FAILED/ERROR/non-null errorCode/non-null safe summary`; every passed check still has `INFO/null/null`.

`decodeCandidateDetail` has already validated unique audit IDs, aggregate versions `1..N`, nondecreasing event times, same actor/trigger for start and rejection, exact root/event terminal state and every state/check combination while the response Request ID was in scope. `classifyTestCandidate` performs only the current-actor-dependent split between the two recovery presentations and maps the other three validated combinations. An impossible branch throws a fixed local invariant error without echoing data, but no malformed owner response may reach this helper in production.

- [ ] **Step 7: Run focused GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test \
  src/risk/ruleSetHead.test.ts \
  src/risk/riskCommands.test.ts \
  src/risk/testCandidateState.test.ts
```

Expected: all head, command and Candidate tests pass.

### Task 4: Build the Rule Set and TEST Candidate React Experience

**Files:**
- Create: `operations-console-web/src/pages/RiskRuleConfiguration.tsx`
- Create: `operations-console-web/src/risk/RuleSetTab.tsx`
- Create: `operations-console-web/src/risk/RuleSetEditor.tsx`
- Create: `operations-console-web/src/risk/ConditionBuilder.tsx`
- Create: `operations-console-web/src/risk/RuleSetDetails.tsx`
- Create: `operations-console-web/src/risk/TestCandidateTab.tsx`
- Create: `operations-console-web/src/risk/TestCandidateDetails.tsx`
- Create: `operations-console-web/src/risk/riskPageModel.ts`
- Create: `operations-console-web/src/risk/riskPageModel.test.ts`
- Create: `operations-console-web/src/risk/riskPageWiring.test.ts`
- Create: `operations-console-web/src/risk/riskRuleConfiguration.css`
- Modify: `operations-console-web/src/navigation/menu.ts`
- Modify: `operations-console-web/src/navigation/menu.test.ts`
- Modify: `operations-console-web/src/App.tsx`
- Modify: `operations-console-web/src/hooks/usePaging.ts`

**Interfaces:**
- Consumes: pure modules from Tasks 1–3, current navigation resources and Account ID.
- Produces: the registered page, two tabs, full-width editor, details drawers and permission-aware actions.

- [ ] **Step 1: Add route and authorization-refresh RED assertions**

Extend `menu.test.ts` with the Risk parent directory and page component/path, plus an unknown component/path counterexample. In `riskPageWiring.test.ts`, require the new page path/component registration, initial-vs-background navigation loading, last-valid resources on refresh failure, write-only revocation without unmount and READ revocation with unmount. Do not create the page-model tests yet and do not modify the user-owned untracked `pageHeading.test.ts`.

- [ ] **Step 2: Run the page-registration RED tests**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test \
  src/navigation/menu.test.ts \
  src/risk/riskPageWiring.test.ts
```

Expected: FAIL because the component map/page do not exist.

- [ ] **Step 3a: Register a minimal protected page shell**

Add to `componentRoutes`:

```ts
OPERATIONS_RISK_RULE_CONFIGURATION: '/risk/rule-configuration'
```

Create a minimal `RiskRuleConfiguration` shell, lazy-load it in `App.tsx`, render its route only when the menu contains the exact component key, and pass `resources`, `actorId={context.accountId}` and `onAuthorizationChanged={() => setReload(value => value + 1)}`. Do not edit or stage the existing untracked `pageHeading.test.ts`.

- [ ] **Step 3b: Refactor background authorization refresh without clearing the page first**

Refactor `Console` navigation loading into an initial blocking load and a background authorization refresh. The first load may render the existing full content spinner. After resources have loaded once, `onAuthorizationChanged` keeps the current `<Routes>` tree and Risk component mounted until the replacement response is decoded. On a successful refresh, replace `resources` atomically: if READ remains, re-render buttons from the new resources without clearing editor/confirmation/pending state; if READ is gone, the normal authorized-route projection may unmount the page and no persistence is attempted. On a background refresh failure, retain the last valid resources/page and show a safe retry notification; do not replace them with `[]`.

- [ ] **Step 3c: Run the route/authorization GREEN tests**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test \
  src/navigation/menu.test.ts \
  src/risk/riskPageWiring.test.ts
```

Expected: registration and first-load/background-refresh behavior pass before page-state work begins.

- [ ] **Step 3d: Compile the minimal protected shell**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
npm run build
```

Expected: the lazy route, background refresh and minimal page shell type-check and bundle.

- [ ] **Step 4a: Add tab and action-authorization RED tests**

Create `riskPageModel.test.ts` with failing tests for canonical tab parsing/search, the exact Button-resource map, absence of permission-by-label/subtree inference, and submit-time permission recheck. Require the caller to preserve in-memory content, invoke `onAuthorizationChanged` and emit zero HTTP requests when a write permission disappears immediately before POST.

- [ ] **Step 4b: Run the tab/action RED slice**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/riskPageModel.test.ts
```

Expected: FAIL because the page coordination model is absent.

- [ ] **Step 4c: Implement tab and action authorization helpers**

Export:

```ts
export type RiskTab = 'rule-sets' | 'test-candidates';
export type RiskAction = 'SAVE' | 'FREEZE' | 'CREATE_CANDIDATE' | 'VALIDATE_CANDIDATE';
export class RiskActionUnavailableError extends Error {}
export function parseRiskTab(search: string): RiskTab;
export function riskTabSearch(tab: RiskTab): string;
export function hasRiskAction(resources: readonly ConsoleResource[], action: RiskAction): boolean;
export function requireRiskAction(resources: readonly ConsoleResource[], action: RiskAction): void;
```

`riskTabSearch` returns exactly `?tab=rule-sets` or `?tab=test-candidates`; page entry immediately replaces any noncanonical search. Map actions only through this closed Resource table—never by display label, permission substring or tree position:

```text
SAVE               -> OPERATIONS_RISK_RULE_SET_SAVE
FREEZE             -> OPERATIONS_RISK_RULE_SET_FREEZE
CREATE_CANDIDATE   -> OPERATIONS_RISK_TEST_CANDIDATE_CREATE
VALIDATE_CANDIDATE -> OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE
```

`requireRiskAction` throws `RiskActionUnavailableError` and is called again immediately before every POST; the caller catches it, calls `onAuthorizationChanged`, emits no HTTP request and leaves in-memory content intact.

- [ ] **Step 4d: Run the tab/action GREEN slice**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/riskPageModel.test.ts
```

Expected: canonical URL and exact permission checks pass before command-state work begins.

- [ ] **Step 4e: Extend the page model with command-state RED tests**

Add failing tests for all deterministic/unknown command failures and reducer transitions. Explicitly assert that 409 and 422 preserve the editor, only `422 + VALIDATION_ERROR` projects validated field errors, and every 422 clears confirmation/key. A 422 with any other valid RFC 9457 code keeps an empty field-error list. Every deterministic HTTP result retains the validated `ApiError.code`, safe browser `message` and canonical-or-empty `requestId`; a 400 business failure renders its safe Detail, while 422 renders field errors rather than its general message. Require `ApiError(502, 'INVALID_RESPONSE', 'semantic-200')` and a 503 to retain the exact pending command plus the validated request ID, while timeout, disconnect and a plain `TypeError` retain the command with `requestId=''`. Assert Create Candidate success switches the tab/search, selects the returned release ID and requests that exact detail even when it is absent from the current first list page.

- [ ] **Step 4f: Run the command-state RED slice**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/riskPageModel.test.ts
```

Expected: the new tests fail because failure projection and reducer transitions are absent.

- [ ] **Step 4g: Implement command failure projection**

Extend the production contract:

```ts
export type RiskConfirmation = Readonly<
  | { action: 'FREEZE'; ruleSetVersionId: string; payload: string }
  | { action: 'CREATE_CANDIDATE'; payload: string }
  | { action: 'VALIDATE_CANDIDATE'; releaseId: string; payload: string }>;
export interface RiskPageCommandState {
  activeTab: RiskTab;
  editor: RuleSetEditorModel | null;
  confirmation: RiskConfirmation | null;
  selectedRuleSetVersionId: string | null;
  selectedCandidateReleaseId: string | null;
  pendingCommand: PendingCommand | null;
  fieldErrors: readonly { field: string; message: string }[];
}
export type RiskCommandResult =
  | { kind: 'SUCCESS'; action: 'SAVE'; ruleSetVersionId: string }
  | { kind: 'SUCCESS'; action: 'FREEZE'; ruleSetVersionId: string }
  | { kind: 'SUCCESS'; action: 'CREATE_CANDIDATE'; releaseId: string }
  | { kind: 'SUCCESS'; action: 'VALIDATE_CANDIDATE'; releaseId: string }
  | { kind: 'HTTP_FAILURE'; action: RiskAction; status: 400 | 401 | 403 | 404 | 409 | 413 | 422 | 429;
      code: string; message: string; requestId: string;
      fieldErrors: readonly { field: string; message: string }[] }
  | { kind: 'UNKNOWN_RESULT'; action: RiskAction; pendingCommand: PendingCommand; requestId: string };
export function classifyCommandFailure(action: RiskAction, pending: PendingCommand,
  error: unknown): RiskCommandResult;
export function reduceRiskCommandResult(state: RiskPageCommandState,
  result: RiskCommandResult): RiskPageCommandState;
```

`classifyCommandFailure` maps `ApiError` statuses `400/401/403/404/409/413/422/429` to `HTTP_FAILURE` and copies only its already safe `code`, browser `message` and canonical-or-empty `requestId`; no raw Problem object survives. It copies the immutable `ApiError.fieldErrors` only when `status===422 && code==='VALIDATION_ERROR'`, and supplies a frozen empty list for every other status/code pair. Thus a valid RFC 9457 422 carrying another code is deterministic but never masquerades as editable validation feedback. Display the safe 400 business message, status-specific safe message and nonempty request ID where applicable; for `422 + VALIDATION_ERROR`, display only the validated field errors plus request ID, never the general message. Timeout, disconnect, unrecognized errors and `500/502/503/504` map to `UNKNOWN_RESULT` with the exact pending path/body/key. Copy `error.requestId` only for an `ApiError`; its transport validation guarantees either the canonical response ID or `''`. Non-HTTP/network errors use `''`. Never put request ID into `PendingCommand` or idempotency identity.

- [ ] **Step 4h: Implement reducer transitions**

While the page remains mounted, `reduceRiskCommandResult` preserves the editor for every deterministic 4xx and unknown result; it preserves confirmation for `400/401/403/409/413/429` and unknown results, closes stale detail plus its confirmation on 404, and closes confirmation on every 422 so field corrections happen in the editor only when validated errors exist. Every deterministic 4xx clears the pending key; only unknown results retain it. Treat `200 + REJECTED` as data rather than an error. A `422 + VALIDATION_ERROR` replaces `state.fieldErrors` with the result list and preserves the editor; navigation, a new command and every other result clear stale field errors. SAVE success clears editor/confirmation/pending and selects the returned Rule Set Version for a fresh GET; FREEZE and VALIDATE keep their tab, select the returned coordinate and GET its detail. CREATE_CANDIDATE success atomically switches `activeTab` to `test-candidates`, replaces the URL with `?tab=test-candidates`, sets `selectedCandidateReleaseId` to the returned `releaseId`, clears confirmation/pending, and GETs that exact detail rather than relying on list order.

- [ ] **Step 4i: Run the complete page-model GREEN gate**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/riskPageModel.test.ts
```

Expected: action authorization, error projection and every reducer transition pass before UI detail work begins.

- [ ] **Step 5: Implement the page shell and tab URL**

Use the pure helpers to normalize `tab`, defaulting to Rule Sets and replacing the entire search string. Default the point filter to `CHECKOUT_PREPARE`. Keep both tab definitions visible but fetch only the active tab.

Extend `usePaging(path, decoder, initialFilters?)` compatibly so a caller may seed its initial filter state. Omitted `initialFilters` remains `{}` for every existing caller. The Risk page must pass `CHECKOUT_PREPARE` through this initializer so its first request is already scoped; it must not emit an unfiltered request and then abort it after an effect updates the filter. Freeze this narrow wiring in `riskPageWiring.test.ts` and retain the existing paging-helper regression.

- [ ] **Step 6: Compile the registered shell and canonical-tab wiring**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
npm run build
```

Expected: the registered shell, lazy route and canonical-tab wiring type-check and bundle.

- [ ] **Step 7: Implement Rule Set list, detail and action wiring**

Use decoded paging with filters `pointCode/ruleSetCode/status`. Detail shows version metadata, short hash plus explicit full-copy action and a read-only structured rule tree. Derive action visibility from the exact READ/write Button resources, while treating BFF 403 as authoritative. The top-level “新建规则集” action requires SAVE and opens only after a complete stable lookup of the entered new code returns `null`. Before edit/freeze/save-new-version, call observed-head inference. Enable Candidate creation from any exact FROZEN detail. RETIRED shows no commands. A 409 preserves editor/command memory and opens the newly observed head in a separate read-only drawer only after the user requests refresh.

- [ ] **Step 8: Compile the Rule Set list/detail slice**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
npm run build
```

Expected: list/detail/head-inference wiring type-checks without adding a raw model escape hatch.

- [ ] **Step 9: Implement the full-width structured editor**

Render Rule Set code/name/change reason and reorderable Rule Cards. Use native drag/drop plus Move Up/Down buttons, then rely on pure conversion for wire sequence. First stream creation permits Rule Set Code entry; after first successful save it is permanently read-only. Render ALL/ANY/NOT nested controls, field/operator-specific value controls, exact inline validation and Action selector. Each condition shows a user-facing “字段在左 / 字段在右” comparison-order control and a live expression preview, defaulting new rows to field-left while faithfully displaying loaded field-right predicates. Do not expose Operand Role names or render/offer a raw-payload preview.

- [ ] **Step 10a: Re-run the builder tests**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/ruleBuilder.test.ts
```

Expected: the structured editor still satisfies the pure wire-conversion contract.

- [ ] **Step 10b: Compile the structured editor**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
npm run build
```

Expected: the component bundle compiles.

- [ ] **Step 11: Implement command confirmation and size feedback**

Freeze confirmation displays code/version/hash summary and sends only `expectedContentHash`. Candidate creation sends exact version ID/hash. Save measures the exact serialized string later passed to the API, shows current UTF-8 bytes, warns from 49,152 and disables at 65,537. Route-specific 413 copy says 64 KiB for Save and 16 KiB for Freeze/Create/Validate. Success clears editor/pending command and refreshes; 400/409/413/422 preserve the relevant in-memory state but clear the old random key; 422 also displays only validated field errors. A 403 calls `onAuthorizationChanged`; 404 closes stale detail; 5xx shows safe Request ID and retains exact path/body/key for explicit retry.

- [ ] **Step 12a: Run the command-state tests**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test \
  src/risk/riskCommands.test.ts \
  src/risk/riskPageModel.test.ts
```

Expected: exact-size, key settlement and error projection pass.

- [ ] **Step 12b: Compile the command UI**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
npm run build
```

Expected: command UI wiring compiles.

- [ ] **Step 13: Implement Candidate list, detail and actions**

Use filters `pointCode/status`. Detail shows frozen composition, state changes ordered by aggregate version and the eight checks. Wire each classification kind to its exact copy/action: Validate DRAFT with expectedVersion 1; recover only current actor with the reconstructed deterministic key; other actor receives guidance to contact owner or create a new Candidate; passed waits for future Shadow; rejected is a normal result. Any malformed or unknown Candidate combination is rejected by the response-local decoder as `502 INVALID_RESPONSE` with that response's validated Request ID and never renders a partial detail.

- [ ] **Step 14a: Run the Candidate-state tests**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test \
  src/risk/testCandidateState.test.ts \
  src/risk/riskPageModel.test.ts
```

Expected: every Candidate state/recovery presentation remains closed over the approved matrix.

- [ ] **Step 14b: Compile the Candidate UI**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
npm run build
```

Expected: the Candidate page compiles.

- [ ] **Step 15a: Run the complete page-model GREEN tests**

Import `riskRuleConfiguration.css` only from the page. Do not edit dirty `src/styles.css`. Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test \
  src/navigation/menu.test.ts \
  src/risk/riskPageModel.test.ts \
  src/risk/riskPageWiring.test.ts
```

Expected: coordination, menu and wiring tests pass.

- [ ] **Step 15b: Compile after adding feature-local styles**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
npm run build
```

Expected: the strict TypeScript/Vite production build passes.

### Task 5: Complete Error Projection, Permission UX and Unsaved-Content Safety

**Files:**
- Modify: `operations-console-web/src/pages/roleEditor.ts`
- Modify: `operations-console-web/src/pages/roleEditor.test.ts`
- Modify: `operations-console-web/src/pages/Roles.tsx`
- Modify: `operations-console-web/src/main.tsx`
- Create: `operations-console-web/src/risk/useUnsavedRuleGuard.ts`
- Create: `operations-console-web/src/risk/useUnsavedRuleGuard.test.ts`
- Create: `operations-console-web/src/risk/riskSecurity.test.ts`
- Modify: `operations-console-web/README.md`

**Interfaces:**
- Consumes: permission tree semantics and React Router data-router blocking.
- Produces: explicit READ/write permission coupling, browser/navigation leave warning and source-level anti-persistence proof.

- [ ] **Step 1: Write the permission and security RED tests**

Extend `roleEditor.test.ts` to require the duplicate-READ removal impact, and create `riskSecurity.test.ts`. Do not create the unsaved-navigation tests yet. RFC 9457 field-error parsing and page projection were already completed by Tasks 1 and 4.

- [ ] **Step 2: Run the safety RED tests**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test \
  src/pages/roleEditor.test.ts \
  src/risk/riskSecurity.test.ts
```

Expected: FAIL because `permissionRemovalImpact` and the Risk security gate are absent.

- [ ] **Step 3: Make removal of write descendants an explicit confirmation**

Export from `roleEditor.ts`:

```ts
export interface PermissionRemovalImpact {
  removedPermissionIds: string[];
  removedDescendantPermissionIds: string[];
}
export function permissionRemovalImpact(nodes: PermissionNode[], permissionIds: string[],
  nodeId: string): PermissionRemovalImpact;
```

It returns selected IDs removed from the whole subtree and the selected IDs owned below the target node, both deduplicated and sorted. In `Roles.tsx`, before unchecking a node whose `removedDescendantPermissionIds` is nonempty, show an Ant Design confirmation that its dependent child permissions will also be removed. Only confirmation calls `togglePermissionNode(..., false)`. Existing checked behavior continues to add ancestor READ, so selecting any Risk write button adds the shared READ ID without duplicating it.

- [ ] **Step 4a: Implement duplicate READ resource bindings**

Build a permission tree where the root `MENU` resource `OPERATIONS_RISK_MANAGEMENT` and page `MENU` resource `OPERATIONS_RISK_RULE_CONFIGURATION` share the exact READ permission ID, while the four `BUTTON` resources have distinct SAVE/FREEZE/CREATE_CANDIDATE/VALIDATE_CANDIDATE permission IDs. Prove selecting any button yields READ plus that write exactly once; canceling either READ-bearing node reports/removes all selected writes only after confirmation; siblings outside the Risk subtree remain selected. Add a source-level wiring assertion that `Roles.tsx` calls `permissionRemovalImpact` before `togglePermissionNode(..., false)` and only the confirmation callback performs the removal.

- [ ] **Step 4b: Run the permission GREEN gate**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/pages/roleEditor.test.ts
```

Expected: duplicate READ selection/removal and confirmation wiring pass independently.

- [ ] **Step 5a: Write the pure unsaved-navigation RED tests**

Create `useUnsavedRuleGuard.test.ts` with tests for browser-unload cleanup, confirm/cancel, repeated blocker callbacks, dirty→false, blocker replacement and dispose-before-resolution. At this step import only the pure functions and controller contracts listed in Step 5c; do not add React/data-router source assertions yet.

- [ ] **Step 5b: Run the pure unsaved-navigation RED gate**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/useUnsavedRuleGuard.test.ts
```

Expected: FAIL because the pure unload/controller implementation is absent.

- [ ] **Step 5c: Implement the pure unload and blocker controller**

Use this testable boundary in `useUnsavedRuleGuard.ts` without adding React Router wiring yet:

```ts
export interface BeforeUnloadTarget {
  addEventListener(type: 'beforeunload', listener: (event: BeforeUnloadEvent) => void): void;
  removeEventListener(type: 'beforeunload', listener: (event: BeforeUnloadEvent) => void): void;
}
export interface BlockedNavigation {
  proceed(): void;
  reset(): void;
}
export interface BlockedNavigationController {
  handle(blocked: BlockedNavigation, dirty: boolean): void;
  dispose(): void;
}
export function installBeforeUnload(target: BeforeUnloadTarget,
  shouldBlock: () => boolean): () => void;
export function nextBlockedNavigation(confirmed: boolean): 'PROCEED' | 'RESET';
export function createBlockedNavigationController(
  confirmLeave: () => Promise<boolean>): BlockedNavigationController;
```

`installBeforeUnload` sets `preventDefault()` and `returnValue` only while dirty and returns exact listener cleanup. `createBlockedNavigationController` permits one confirmation flight at a time, calls exactly one of `proceed/reset`, resets and detaches the active flight immediately if `dirty` becomes false so a never-resolving old confirmation cannot queue-block a later navigation, and ignores promise completion after `dispose` or after a newer blocker replaces the old one.

- [ ] **Step 5d: Run the pure unsaved-navigation GREEN gate**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/useUnsavedRuleGuard.test.ts
```

Expected: pure browser-unload and asynchronous blocker concurrency behavior pass before React wiring.

- [ ] **Step 5e: Add data-router and hook-wiring RED assertions**

Extend `useUnsavedRuleGuard.test.ts` with source-level assertions that `main.tsx` owns exactly one data router and `RouterProvider`, the hook composes `useBlocker(dirty)` with the tested controller, and the Risk page calls the hook with its explicit dirty predicate and an Ant Design modal confirmation. Run the same test file and require RED before touching `main.tsx` or the hook.

- [ ] **Step 5f: Run the data-router/hook wiring RED gate**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/useUnsavedRuleGuard.test.ts
```

Expected: FAIL because `main.tsx`, the hook and the Risk page do not yet satisfy the new wiring assertions.

- [ ] **Step 5g: Enable in-app route blocking and browser close warning**

Replace `<BrowserRouter>` in `main.tsx` with one data router:

```tsx
const router = createBrowserRouter([{ path: '*', element: <App /> }]);
// Existing ConfigProvider and AntApp remain unchanged.
<RouterProvider router={router} />
```

Complete the React boundary:

```ts
export function useUnsavedRuleGuard(dirty: boolean,
  confirmLeave: () => Promise<boolean>): void;
```

The hook combines the already-tested controller with a `useBlocker` predicate that synchronously increments one navigation-attempt generation before Router schedules its state transition. Each adapter captures the generation that created its blocked object. Its Router adapter is additionally two-phase: asynchronous confirmation may only request `PROCEED/RESET`; a subsequent React commit/layout effect invokes Router only when both blocker object identity and the synchronous attempt generation remain current. The generation check is required because a child reducer/layout effect can commit before RouterProvider's `startTransition` publishes a replacement blocker; comparing only the React-visible object is insufficient. An old Promise must never call an old blocker method directly. Cleanup revokes the adapter before disposing the controller; do not use a timer, microtask or Router internal as an identity proxy. The page defines dirty as `editor has any change from its loaded baseline || pendingCommand !== null`; a pristine opened editor is clean, but an unknown-result command remains dirty even when fields match the baseline. The page supplies `confirmLeave` through Ant Design `App.useApp().modal.confirm`.

- [ ] **Step 5h: Run the route/hook wiring GREEN test**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/useUnsavedRuleGuard.test.ts
```

Expected: data-router ownership, hook composition and page dirty-state wiring pass their source gate.

- [ ] **Step 5i: Compile the data-router and React hook**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
npm run build
```

Expected: React Router and modal-confirm wiring type-check and bundle.

- [ ] **Step 6: Add the Risk security source gate**

`riskSecurity.test.ts` recursively reads production `.ts/.tsx` files under `src/risk` plus `src/pages/RiskRuleConfiguration.tsx`, excluding `*.test.ts` and fixtures. Use the repository's TypeScript-aware Vite/Oxc parser and traverse the reachable local import/export closure, including helpers outside `src/risk`; allow only the exact external UI modules `react`, `react-router-dom` and `antd`, and reject unresolved executable local imports. Reject direct, optional, aliased and statically computed access to:

```text
localStorage
sessionStorage
indexedDB
handlerCode
implementationVersion
implementationHash
```

Also reject dynamic import, `require`, `eval` and `Function`, including computed member forms. Resolve string values and sensitive-global aliases from each AST use site through the nearest lexical binding identity, with Program/function/block scopes, parameters/imports/catch bindings and `var` owned by its nearest function; do not use one file-wide name map. Every declaration that belongs to the same function-scoped `var`/parameter binding merges its candidates regardless of source traversal order; it must never replace earlier candidates, while a genuine block/catch lexical shadow remains independent. Treat function parameters, catch parameters and `for-in`/`for-of` bindings as having an external `UNKNOWN` candidate before applying any default; a benign default must not erase a possible runtime value. Project initializer candidates through nested `ObjectPattern`, statically known `ArrayPattern` and `AssignmentPattern` defaults, while handling rest bindings conservatively without treating the rest value as an alias of its parent. Preserve structured candidates through subsequent ordinary local alias chains without executing code or getters. Evaluate candidates individually rather than requiring distinct source expressions to share AST-node identity. A sensitive-global alias fails closed when any possible initializer is sensitive; a static string is trusted only when the projected candidates are fully determined and consistent. Break cycles by binding identity. The bounded static-string evaluator covers literal/bound strings, concatenation, templates and statically known string-array `.join`; recognize static `globalThis.window`-style member chains, and make unresolved computed access through `globalThis`, `window` or `self` (including aliases) fail closed without rejecting ordinary-object computed access. The only storage-owner exception is the exact existing `src/api/session.ts` `sessionStorage` owner. This is a frozen source gate, not a claim to be a general JavaScript sandbox. Allow test fixtures to mention forbidden names only in assertions. Also prove `riskTabSearch` emits only the canonical `tab` key and `parseRiskTab` defaults safely while discarding extra input on rewrite.

- [ ] **Step 7: Run the complete Node test suite**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
npm test
```

Expected: all Node tests pass. Do not claim automated DOM interaction coverage.

- [ ] **Step 8: Build the production bundle**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
npm run build
```

Expected: strict TypeScript checking and the Vite production build pass.

- [ ] **Step 9: Check the Web diff**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
git diff --check
```

Expected: no whitespace error.

- [ ] **Step 10: Update the Web implementation documentation**

Document the structured builder, exact catalog, head-inference limits, size/idempotency behavior, Candidate end states, in-memory-only rule content and actual test/build evidence. Keep Shadow, approval, production activation and runtime evaluation explicitly outside this Web slice. Do not assert a mutable local/shared deployment state in this component README; state that component tests/build do not prove deployment and current-state facts belong only to the workspace `agent-docs/CURRENT_IMPLEMENTATION.md` ledger.

- [ ] **Step 11: Stage only the Web track if commit authorization is active**

All paths below are clean at planning baseline except the pre-existing dirty `src/pages/Roles.tsx`; stage that file interactively and leave `Accounts.tsx`, `Audit.tsx`, `GatewayConfiguration.tsx`, `styles.css` and untracked `pageHeading.test.ts` untouched:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
git add -- \
  README.md \
  src/App.tsx \
  src/api/paging.ts \
  src/api/paging.test.ts \
  src/api/session.ts \
  src/api/session.test.ts \
  src/hooks/usePaging.ts \
  src/main.tsx \
  src/navigation/menu.ts \
  src/navigation/menu.test.ts \
  src/pages/RiskRuleConfiguration.tsx \
  src/pages/roleEditor.ts \
  src/pages/roleEditor.test.ts \
  src/risk/ConditionBuilder.tsx \
  src/risk/RuleSetDetails.tsx \
  src/risk/RuleSetEditor.tsx \
  src/risk/RuleSetTab.tsx \
  src/risk/TestCandidateDetails.tsx \
  src/risk/TestCandidateTab.tsx \
  src/risk/riskApiModels.ts \
  src/risk/riskAuthoringCatalog.ts \
  src/risk/riskAuthoringCatalog.test.ts \
  src/risk/riskCommands.ts \
  src/risk/riskCommands.test.ts \
  src/risk/riskDecisionApi.ts \
  src/risk/riskDecisionApi.test.ts \
  src/risk/riskPageModel.ts \
  src/risk/riskPageModel.test.ts \
  src/risk/riskPageWiring.test.ts \
  src/risk/riskResponseValidation.ts \
  src/risk/riskResponseValidation.test.ts \
  src/risk/riskRuleConfiguration.css \
  src/risk/riskSecurity.test.ts \
  src/risk/ruleBuilder.ts \
  src/risk/ruleBuilder.test.ts \
  src/risk/ruleSetHead.ts \
  src/risk/ruleSetHead.test.ts \
  src/risk/testCandidateState.ts \
  src/risk/testCandidateState.test.ts \
  src/risk/useUnsavedRuleGuard.ts \
  src/risk/useUnsavedRuleGuard.test.ts
git add -p -- src/pages/Roles.tsx
git diff --cached --check
git diff --cached --name-only
git diff --cached
```

Reject every unrelated `Roles.tsx` hunk and require the staged name list to contain none of the five excluded user-owned paths.

- [ ] **Step 12: Commit the approved staged Web diff**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
git commit -m "feat(operations-web): add structured Risk rule builder"
```
