import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

function readOptionalSource(relativePath) {
  try {
    return readFileSync(new URL(relativePath, import.meta.url), "utf8");
  } catch {
    return "";
  }
}

function extractSystemBlock(systemCode, nextSystemCode) {
  const start = source.indexOf(`code: "${systemCode}"`);
  const end = nextSystemCode
    ? source.indexOf(`code: "${nextSystemCode}"`, start)
    : source.indexOf("\n];", start);
  assert.ok(start >= 0, `${systemCode} system block must exist`);
  assert.ok(end > start, `${systemCode} system block must have a valid boundary`);
  return source.slice(start, end);
}

function extractCatalogLine(systemBlock, factory, id) {
  const line = systemBlock
    .split("\n")
    .find((candidate) => candidate.includes(`${factory}("${id}"`));
  assert.ok(line, `${id} must exist in its owning system block`);
  return line;
}

const source = readFileSync(
  new URL("../content/requirements.ts", import.meta.url),
  "utf8",
);
const platformDatabaseSource = readFileSync(
  new URL("../../agent-docs/database/PLATFORM_SUPPORT_DATABASE.md", import.meta.url),
  "utf8",
);
const commerceDatabaseSource = readFileSync(
  new URL("../../agent-docs/database/COMMERCE_DATABASE.md", import.meta.url),
  "utf8",
);
const clientContractDatabaseSource = readFileSync(
  new URL("../../agent-docs/database/CLIENT_CONTRACT_DATABASE.md", import.meta.url),
  "utf8",
);
const riskDatabaseSource = readFileSync(
  new URL("../../agent-docs/database/RISK_COMPLIANCE_DATABASE.md", import.meta.url),
  "utf8",
);
const financeDatabaseSource = readFileSync(
  new URL("../../agent-docs/database/FINANCE_DATABASE.md", import.meta.url),
  "utf8",
);
const financeUnifiedAccountDesignSource = readFileSync(
  new URL(
    "../../agent-docs/plans/2026-08-26-finance-unified-account-model-design.md",
    import.meta.url,
  ),
  "utf8",
);
const databaseOverviewSource = readFileSync(
  new URL("../../agent-docs/database/README.md", import.meta.url),
  "utf8",
);
const databaseDesignGuideSource = readFileSync(
  new URL("../../agent-docs/DATABASE_DESIGN_GUIDE.md", import.meta.url),
  "utf8",
);
const commerceScenarioSource = readOptionalSource(
  "../../agent-docs/database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md",
);
const commerceOrderContextDesignSource = readFileSync(
  new URL(
    "../../agent-docs/plans/2026-08-24-commerce-order-context-simplification-design.md",
    import.meta.url,
  ),
  "utf8",
);
const commerceConsumerEvidenceDesignSource = readFileSync(
  new URL(
    "../../agent-docs/plans/2026-08-24-commerce-consumer-analytics-evidence-design.md",
    import.meta.url,
  ),
  "utf8",
);
const commercePrivacySimplificationDesignSource = readFileSync(
  new URL(
    "../../agent-docs/plans/2026-08-24-commerce-privacy-fulfillment-simplification-design.md",
    import.meta.url,
  ),
  "utf8",
);
const commerceDocumentAuditCancelDesignSource = readFileSync(
  new URL(
    "../../agent-docs/plans/2026-08-24-commerce-document-audit-cancel-reversal-design.md",
    import.meta.url,
  ),
  "utf8",
);
const commerceClientApiDesignSource = readFileSync(
  new URL(
    "../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md",
    import.meta.url,
  ),
  "utf8",
);
const commerceClientApiDecisionSource = readFileSync(
  new URL(
    "../../agent-docs/decisions/2026-09-05-commerce-client-api-v1.md",
    import.meta.url,
  ),
  "utf8",
);
const systemWideProblemDesignSource = readFileSync(
  new URL(
    "../../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md",
    import.meta.url,
  ),
  "utf8",
);
const agentReadmeSource = readFileSync(
  new URL("../../agent-docs/README.md", import.meta.url),
  "utf8",
);
const projectContextSource = readFileSync(
  new URL("../../agent-docs/PROJECT_CONTEXT.md", import.meta.url),
  "utf8",
);
const currentImplementationSource = readFileSync(
  new URL("../../agent-docs/CURRENT_IMPLEMENTATION.md", import.meta.url),
  "utf8",
);
const documentGovernanceSource = readFileSync(
  new URL("../../agent-docs/DOCUMENT_GOVERNANCE.md", import.meta.url),
  "utf8",
);
const developmentConventionsSource = readFileSync(
  new URL("../../agent-docs/DEVELOPMENT_CONVENTIONS.md", import.meta.url),
  "utf8",
);
const javaProjectStructureSource = readFileSync(
  new URL("../../agent-docs/JAVA_PROJECT_STRUCTURE.md", import.meta.url),
  "utf8",
);
const rootAgentsSource = readFileSync(
  new URL("../../AGENTS.md", import.meta.url),
  "utf8",
);
const javaServiceBoundariesRuleSource = readFileSync(
  new URL("../../.cursor/rules/java-service-boundaries.mdc", import.meta.url),
  "utf8",
);
const unitTestGuideSource = readFileSync(
  new URL("../../agent-docs/UNIT_TEST_GUIDE.md", import.meta.url),
  "utf8",
);
const javaTestingRuleSource = readFileSync(
  new URL("../../.cursor/rules/java-testing.mdc", import.meta.url),
  "utf8",
);
const superpowersWorkflowRuleSource = readOptionalSource(
  "../../.cursor/rules/superpowers-workflow.mdc",
);
const superpowersServicePolicyDecisionSource = readFileSync(
  new URL(
    "../../agent-docs/decisions/2026-09-05-superpowers-and-service-interface-policy.md",
    import.meta.url,
  ),
  "utf8",
);
const businessModuleCollaborationDecisionSource = readFileSync(
  new URL(
    "../../agent-docs/decisions/2026-09-05-business-module-collaboration-policy.md",
    import.meta.url,
  ),
  "utf8",
);
const financeModularCodeDesignSource = readFileSync(
  new URL(
    "../../agent-docs/plans/2026-09-04-finance-modular-code-design.md",
    import.meta.url,
  ),
  "utf8",
);
const javaBusinessModuleBoundariesRuleSource = readFileSync(
  new URL("../../.cursor/rules/java-business-module-boundaries.mdc", import.meta.url),
  "utf8",
);
const rootReadmeSource = readFileSync(
  new URL("../../README.md", import.meta.url),
  "utf8",
);
const applicationArchitectureSource = readFileSync(
  new URL("../../agent-docs/APPLICATION_ARCHITECTURE.md", import.meta.url),
  "utf8",
);
const technologyStackSource = readFileSync(
  new URL("../../agent-docs/TECHNOLOGY_STACK.md", import.meta.url),
  "utf8",
);
const gatewayControlPlaneDesignSource = readFileSync(
  new URL(
    "../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md",
    import.meta.url,
  ),
  "utf8",
);
const platformSimplificationImplementationSource = readFileSync(
  new URL(
    "../../agent-docs/plans/2026-09-01-platform-support-database-simplification-implementation-plan.md",
    import.meta.url,
  ),
  "utf8",
);
const architectureDiagramSource = readFileSync(
  new URL("../components/architecture-diagrams.tsx", import.meta.url),
  "utf8",
);
const requirementsSiteSource = readFileSync(
  new URL("../components/requirements-site.tsx", import.meta.url),
  "utf8",
);

const expectedCommerceTables = [
  "checkout_session",
  "command_idempotency",
  "commerce_order",
  "compensation_event",
  "checkout_network_evidence",
  "consumer_acceptance",
  "consumer_context",
  "consumer_contract",
  "order_item",
  "order_legal",
  "order_funds_availability_projection",
  "order_payment_projection",
  "order_refund_position",
  "order_context",
  "refund_request",
].sort();

const expectedCommerceClientApiRoutes = [
  ["POST", "/api/v1/checkouts", "checkouts.write"],
  ["GET", "/api/v1/orders/{orderNo}/checkout", "orders.read"],
  ["GET", "/api/v1/orders/{orderNo}", "orders.read"],
  ["GET", "/api/v1/orders", "orders.read"],
  ["POST", "/api/v1/orders/{orderNo}/cancel", "orders.cancel"],
  ["GET", "/api/v1/orders/{orderNo}/refundable-amount", "refunds.read"],
  ["POST", "/api/v1/orders/{orderNo}/refunds", "refunds.write"],
  ["GET", "/api/v1/refunds/{refundRequestNo}", "refunds.read"],
  ["GET", "/api/v1/refunds", "refunds.read"],
  ["GET", "/api/v1/orders/{orderNo}/refunds", "refunds.read"],
];

const removedCommerceTables = [
  "acceptance_state_change",
  "consumer_acceptance_summary",
  "checkout_status_history",
  "consumer_contract_snapshot",
  "consumer_document_ref",
  "fulfill_callback_snapshot",
  "fulfillment",
  "fulfillment_confirmation",
  "fulfillment_evidence",
  "fulfillment_status_history",
  "order_amount_snapshot",
  "order_commercial_term",
  "order_commercial_term_component_snapshot",
  "order_commercial_term_snapshot",
  "order_contract_snapshot",
  "order_item_snapshot",
  "order_snapshot",
  "order_seller_snapshot",
  "order_status_history",
  "order_tax_line",
  "order_tax_line_snapshot",
  "order_term_component",
  "refund_payment_projection",
  "refund_reservation",
  "refund_reserve_change",
  "refund_reserve_history",
  "refund_status_history",
  "consumer_document",
  "business_change_record",
  "business_change_version",
];

function extractMarkdownSection(markdown, heading) {
  const start = markdown.indexOf(heading);
  if (start < 0) {
    return "";
  }
  const end = markdown.indexOf("\n### ", start + heading.length);
  return markdown.slice(start, end < 0 ? undefined : end);
}

function extractCommerceTableHeadings(markdown) {
  return [...markdown.matchAll(/^\| [^|]+ \| `([a-z0-9_]+)` \| `t_\1` \|/gmu)]
    .map((match) => match[1])
    .sort();
}

function calculateCheckoutTokenChecksum(core) {
  const alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  let numericValue = 0n;
  for (const character of core) {
    numericValue = (numericValue * 62n) + BigInt(alphabet.indexOf(character));
  }
  const bytes = new Uint8Array(20);
  for (let index = bytes.length - 1; index >= 0; index -= 1) {
    bytes[index] = Number(numericValue & 0xffn);
    numericValue >>= 8n;
  }
  assert.equal(numericValue, 0n, "KSUID core must decode to exactly 20 bytes");

  let checksum = 0xffff;
  for (const byte of bytes) {
    checksum ^= byte << 8;
    for (let bit = 0; bit < 8; bit += 1) {
      checksum = checksum & 0x8000
        ? ((checksum << 1) ^ 0x1021) & 0xffff
        : (checksum << 1) & 0xffff;
    }
  }

  let encoded = "";
  do {
    encoded = alphabet[checksum % 62] + encoded;
    checksum = Math.floor(checksum / 62);
  } while (checksum > 0);
  return encoded.padStart(3, "0");
}

test("requirements catalog contains every stable section", () => {
  for (const id of [
    "overview",
    "business-model",
    "architecture",
    "domain-map",
    "end-to-end",
    "use-cases",
    "systems",
    "entry-applications",
    "payment-model",
    "finance-model",
    "decisions",
    "open-questions",
  ]) {
    assert.match(source, new RegExp(`id:\\s*["']${id}["']`));
  }
});

test("requirements catalog defines the approved entry applications", () => {
  assert.match(source, /export const ENTRY_APPLICATIONS/);
  for (const code of [
    "API_GATEWAY",
    "HOSTED_CHECKOUT",
    "CLIENT_CONSOLE",
    "PARTNER_CONSOLE",
    "CLIENT_API",
    "OPERATIONS_CONSOLE",
    "CHANNEL_ENDPOINT",
    "CONSUMER_SERVICE",
  ]) {
    assert.match(source, new RegExp(`code:\\s*["']${code}["']`));
  }
});

test("Commerce Client API V1 freezes exactly ten Hosted Checkout routes and five scopes", () => {
  const routeRows = [...commerceClientApiDesignSource.matchAll(
    /^\| `client-api-commerce-[^`]+` \| (GET|POST) \| `([^`]+)` \| `[^`]+` \| `([^`]+)` \|/gmu,
  )].map((match) => [match[1], match[2], match[3]]);
  const scopes = [...new Set(routeRows.map(([, , scope]) => scope))].sort();
  const clientApiBlock = source.slice(
    source.indexOf('code: "CLIENT_API"'),
    source.indexOf('code: "OPERATIONS_CONSOLE"'),
  );

  assert.deepEqual(routeRows, expectedCommerceClientApiRoutes);
  assert.deepEqual(scopes, [
    "checkouts.write",
    "orders.cancel",
    "orders.read",
    "refunds.read",
    "refunds.write",
  ]);
  assert.match(clientApiBlock, /面向 Client Server/u);
  assert.match(clientApiBlock, /connectsTo: \["Commerce"\]/u);
  assert.match(clientApiBlock, /不提供 DirectPay、Payout、Webhook 管理或无过滤全量列表/u);
  assert.match(commerceClientApiDecisionSource, /固定接口为：[\s\S]*10\..*GET \/api\/v1\/orders\/\{orderNo\}\/refunds/u);
});

test("Commerce Client API V1 keeps POST idempotency and delegates Problem ownership explicitly", () => {
  for (const contractSource of [source, commerceClientApiDecisionSource]) {
    assert.match(contractSource, /所有 POST.*Idempotency-Key/su);
    assert.match(contractSource, /Client \+ operation \+ key/u);
    assert.match(contractSource, /IDEMPOTENCY_KEY_REUSED/u);
  }
  assert.match(commerceClientApiDesignSource, /每个 POST.*Idempotency-Key/u);
  assert.match(commerceClientApiDesignSource, /caller_scope = CLIENT:<trustedClientId>/u);
  assert.match(commerceClientApiDesignSource, /operation_type = CREATE_CHECKOUT \| CANCEL_ORDER \| CREATE_REFUND/u);
  assert.match(commerceClientApiDesignSource, /idempotency_key = exact case-sensitive header value/u);
  assert.match(commerceClientApiDesignSource, /IDEMPOTENCY_KEY_REUSED/u);
  assert.match(commerceClientApiDecisionSource, /Error-contract supersession:.*2026-09-06-system-wide-rfc-9457-problem-details\.md/su);
  assert.match(commerceClientApiDesignSource, /Error-contract supersession:.*2026-09-06-system-wide-rfc-9457-problem-details-design\.md/su);
  assert.match(systemWideProblemDesignSource, /application\/problem\+json/u);
  assert.match(systemWideProblemDesignSource, /Producer 当前不输出 `status` 或 `instance`/u);
  assert.match(commerceClientApiDesignSource, /Gateway 只转发 Header，零自动重试、零响应缓存/u);
});

test("every concrete Checkout token example satisfies the frozen CRC16 Base62 format", () => {
  const tokens = [...new Set(commerceClientApiDesignSource.match(/chk_[0-9A-Za-z]{30}/gu) ?? [])];

  assert.ok(tokens.length >= 2, "the design must retain both a wire example and a fixed vector");
  for (const token of tokens) {
    const core = token.slice(4, 31);
    const checksum = token.slice(31);
    assert.equal(token.length, 34);
    assert.equal(checksum, calculateCheckoutTokenChecksum(core), `${token} has an invalid checksum`);
  }
});

test("Commerce Client API V1 freezes the minimal Checkout request, decimal model, expiry, and business-key replay matrix", () => {
  const requestSection = commerceClientApiDesignSource.slice(
    commerceClientApiDesignSource.indexOf("### 5.1 请求"),
    commerceClientApiDesignSource.indexOf("### 5.2 不接受的字段"),
  );
  const requestJson = requestSection.match(/```json\n([\s\S]*?)\n```/u)?.[1];
  assert.ok(requestJson, "Create Checkout must have a JSON request example");
  const request = JSON.parse(requestJson);

  assert.deepEqual(Object.keys(request).sort(), [
    "clientOrderId",
    "consumerCountryCode",
    "currency",
    "expiresAt",
    "items",
  ]);
  assert.equal(typeof request.items[0].quantity, "string");
  assert.equal(typeof request.items[0].unitAmount, "string");
  assert.equal(typeof request.items[0].discountAmount, "string");
  assert.match(commerceClientApiDesignSource, /Java 使用 `BigDecimal`，金额持久化使用 `DECIMAL\(24,8\)`/u);
  assert.match(commerceClientApiDesignSource, /locale`，固定使用 `en-US`/u);
  assert.match(commerceClientApiDesignSource, /`industryCode`、`mccCode`、`classificationId`/u);
  assert.match(commerceClientApiDesignSource, /request\.expiresAt \?\? operationContextTime \+ 30 minutes/u);
  assert.match(commerceClientApiDesignSource, /operationContextTime \+ 5 minutes[\s\S]*operationContextTime \+ 24 hours/u);
  assert.match(commerceClientApiDesignSource, /新 Key、已有相同业务 ID[\s\S]*CLIENT_ORDER_ID_ALREADY_EXISTS[\s\S]*相同不可变内容返回已有 201/u);
  assert.match(commerceClientApiDesignSource, /新 Key、业务 ID 但内容不同[\s\S]*CLIENT_REFUND_ID_CONFLICT/u);
});

test("Commerce Client API V1 separates Order lifecycle from Finance-owned funds availability", () => {
  assert.match(commerceClientApiDesignSource, /4 prefix \+ 27 KSUID \+ 3 checksum = 34 characters/u);
  assert.match(commerceClientApiDesignSource, /0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz/u);
  assert.match(commerceClientApiDesignSource, /public_token CHAR\(34\)[\s\S]*COLLATE ascii_bin[\s\S]*NOT NULL/u);
  assert.match(commerceClientApiDesignSource, /删除 `public_token_hash`.*不增加 Token Hash、HMAC Key Version、Vault Token、KMS Ref/su);
  assert.match(commerceClientApiDesignSource, /CREATED \| OPEN \| PAID \| CANCELLED \| EXPIRED/u);
  assert.match(commerceClientApiDesignSource, /OrderStatus 不增加 `SETTLED`/u);
  assert.match(commerceClientApiDesignSource, /Order created[\s\S]*NOT_APPLICABLE[\s\S]*PaymentSucceeded[\s\S]*PENDING[\s\S]*EconomicsCalculated[\s\S]*PayableBalanceMatured[\s\S]*AVAILABLE[\s\S]*FULLY_OFFSET/u);
  assert.match(commerceClientApiDesignSource, /`FULLY_OFFSET` 没有资金进入 AVAILABLE，因此 V1 不发送 `order\.funds_available`/u);
});

test("the system-wide authority freezes the Problem Profile and Commerce keeps exactly eight safe Webhook events", () => {
  const canonicalProblemClauses = source.split("\n")
    .filter((line) => line.includes("RFC 9457-based Code-MoR Problem Profile"));
  assert.equal(canonicalProblemClauses.length, 2);
  for (const clause of canonicalProblemClauses) {
    assert.match(clause, /Body 必填 type\/title\/detail\/code\/requestId\/timestamp/u);
    assert.match(clause, /type 固定 about:blank/u);
    assert.match(clause, /Body 不复制 (?:HTTP )?status/u);
    assert.match(clause, /可选.*(?:errors|字段错误)/u);
    assert.match(clause, /field\/message/u);
    assert.match(clause, /X-Request-Id.*Body.*完全一致/u);
    assert.match(clause, /HEAD/u);
  }

  const problemSection = systemWideProblemDesignSource.slice(
    systemWideProblemDesignSource.indexOf("### 3.2 Body"),
    systemWideProblemDesignSource.indexOf("### 3.3 字段路径"),
  );
  const requiredMemberSection = problemSection.slice(
    problemSection.indexOf("必填成员："),
    problemSection.indexOf("可选成员："),
  );
  const optionalMemberSection = problemSection.slice(
    problemSection.indexOf("可选成员："),
    problemSection.indexOf("Producer 当前"),
  );
  const tableMembers = (section) => [...section.matchAll(/^\| `([^`]+)` \|/gmu)]
    .map((match) => match[1]);
  assert.deepEqual(tableMembers(requiredMemberSection), [
    "type",
    "title",
    "detail",
    "code",
    "requestId",
    "timestamp",
  ]);
  assert.deepEqual(tableMembers(optionalMemberSection), ["errors"]);
  const problemJson = problemSection.match(/```json\n([\s\S]*?)\n```/u)?.[1];
  assert.ok(problemJson, "the current system-wide Problem authority must have a JSON example");
  const problem = JSON.parse(problemJson);
  assert.deepEqual(Object.keys(problem).sort(), [
    "code",
    "detail",
    "errors",
    "requestId",
    "timestamp",
    "title",
    "type",
  ]);
  assert.equal(problem.type, "about:blank");
  assert.deepEqual(Object.keys(problem.errors[0]).sort(), ["field", "message"]);
  assert.ok(!Object.hasOwn(problem, "status"));
  assert.ok(!Object.hasOwn(problem, "instance"));
  assert.ok(!Object.hasOwn(problem, "message"));

  const eventCatalogSection = commerceClientApiDesignSource.slice(
    commerceClientApiDesignSource.indexOf("### 13.1 事件目录"),
    commerceClientApiDesignSource.indexOf("### 13.2 Envelope"),
  );
  const eventCatalog = eventCatalogSection.match(/```text\n([\s\S]*?)\n```/u)?.[1]
    .trim()
    .split("\n");
  assert.deepEqual(eventCatalog, [
    "order.paid",
    "order.cancelled",
    "order.expired",
    "order.funds_available",
    "refund.created",
    "refund.succeeded",
    "refund.failed",
    "refund.cancelled",
  ]);
  assert.match(commerceClientApiDesignSource, /Platform Support.*Endpoint.*Subscription.*Secret.*Delivery.*Retry.*Replay/su);
  assert.match(commerceClientApiDesignSource, /同一 Client 的全部 Webhook 事件命名空间内永久唯一/u);
  assert.match(commerceClientApiDesignSource, /同一值不得跨 Event Type、Schema Version 或来源资源复用/u);
  assert.match(commerceClientApiDesignSource, /at-least-once[\s\S]*重复[\s\S]*乱序/u);
  assert.match(commerceClientApiDesignSource, /V1 每个 Attempt 只使用一个 ACTIVE Secret/u);
  assert.match(commerceClientApiDesignSource, /版本跳跃或不确定时 GET 最新状态/u);
});

test("core systems expose the interfaces required by Partner Console", () => {
  assert.match(
    source,
    /iuc\("CCT-I08", "提交 Operating Entity 入驻申请", \["Client Console", "Partner Console", "Operations Console"\]/,
  );
  assert.match(source, /iuc\("PAY-I09", "查询 Partner 授权的 MID"/);
  assert.match(source, /iuc\("FIN-I11", "提交 Partner 出款申请"/);
});

test("requirements catalog contains all business systems", () => {
  assert.doesNotMatch(source, /export const DOMAIN_DEFINITIONS/);
  assert.doesNotMatch(source, /Payment Orchestration：支付和退款事实/);
  assert.doesNotMatch(source, /其他五个系统：已发生的业务 MQ 通知/);
  for (const code of [
    "CLIENT_CONTRACT",
    "COMMERCE",
    "PAYMENT",
    "RISK_COMPLIANCE",
    "FINANCE",
    "PLATFORM",
  ]) {
    assert.match(source, new RegExp(`code:\\s*["']${code}["']`));
  }
});

test("every system exposes use cases", () => {
  for (const prefix of [
    "CCT-",
    "COM-",
    "PAY-",
    "RCO-",
    "FIN-",
    "PLT-",
  ]) {
    assert.match(source, new RegExp(prefix.replace("-", "\\-")));
  }
});

test("Finance catalog exposes tax payment, period close, payout cancel, and material reconciliation as internal capabilities", () => {
  const financeBlock = source.slice(
    source.indexOf('code: "FINANCE"'),
    source.indexOf('code: "PLATFORM"'),
  );

  for (const domain of [
    "Tax Return & Tax Payment",
    "Beneficiary Account",
    "Manual Payout",
    "Payout Execution & Reconciliation",
    "Trial Balance & Period Close",
    "Reconciliation & Resolution",
  ]) {
    assert.match(financeBlock, new RegExp(`internalDomains:[^\\n]+${domain}`, "u"));
  }

  const taxLifecycle = financeBlock.match(/uc\("FIN-21"[^\n]+/u)?.[0] ?? "";
  assert.match(taxLifecycle, /Tax Return.*Tax Payment/u);
  assert.match(taxLifecycle, /不可变.*Version.*双人审批/u);
  assert.match(taxLifecycle, /tax_payable_account_id.*active_slot.*UNKNOWN.*未扣款.*新的 payment_no.*execution_key/u);
  assert.match(taxLifecycle, /available_cash.*active_payout_cash.*active_tax_cash/u);
  assert.match(taxLifecycle, /同一 tax_payable_account_id.*net_balance.*全部.*活动 Tax Payment/u);
  assert.match(taxLifecycle, /不同 Return.*合计/u);
  assert.match(taxLifecycle, /BANK_CASH t_account.*FAILED.*完整对账.*BANK_CASH/u);
  assert.doesNotMatch(financeBlock, /t_funding_(?:position|commitment|allocation|move)|Funding Commitment/u);

  const periodClose = financeBlock.match(/uc\("FIN-22"[^\n]+/u)?.[0] ?? "";
  assert.match(periodClose, /试算平衡.*期间关账.*重开/u);
  assert.match(periodClose, /as_of_sequence.*close_hash.*Maker.*Checker/u);
  assert.match(periodClose, /新的 REOPEN.*不覆盖/u);

  const payoutCancel = financeBlock.match(/uc\("FIN-23"[^\n]+/u)?.[0] ?? "";
  assert.match(payoutCancel, /Payout.*取消/u);
  assert.match(payoutCancel, /PENDING_APPROVAL.*无 Execution.*直接撤回/u);
  assert.match(payoutCancel, /APPROVED.*ON_HOLD.*RETRY_REQUIRED.*PAYOUT_CANCEL.*Maker.*Checker/u);
  assert.match(payoutCancel, /UNKNOWN.*部分.*Match.*PAYOUT_RELEASE/u);
  assert.match(payoutCancel, /不维护或释放通用 Funding、Allocation 或 Position/u);

  const reconResolution = financeBlock.match(/uc\("FIN-24"[^\n]+/u)?.[0] ?? "";
  assert.match(reconResolution, /重大.*对账差异/u);
  assert.match(reconResolution, /Maker.*Checker.*ROUNDING/u);
  assert.match(reconResolution, /金额变化.*新的 Accounting Instruction/u);
  assert.match(reconResolution, /LATE_DEBIT:<bank_item_id>.*RECON_SUSPENSE_ASSET.*BANK_CASH.*LATE_DEBIT/u);
  assert.match(reconResolution, /旧 FAILED Payout Execution.*普通成功.*新 Execution.*现金承诺/u);

  const bankRecon = financeBlock.match(/uc\("FIN-12"[^\n]+/u)?.[0] ?? "";
  assert.match(bankRecon, /channel_batch_id.*CHANNEL_SETTLEMENT.*payout_execution_id.*PAYOUT.*tax_payment_id.*TAX/u);
  assert.match(bankRecon, /外部 Debit.*容量不足.*不得拒绝.*新余额/u);
  assert.doesNotMatch(financeBlock, /Settlement Profile|Settlement Run|Settlement Payment|Settlement Attempt|active_settlement_cash|client_available_time|CLIENT_BALANCE_MATURED|BalanceAvailable|Channel Cash Item/u);

  const interfaceIds = [...financeBlock.matchAll(/iuc\("FIN-I(\d+)"/gu)]
    .map((match) => Number(match[1]));
  assert.equal(Math.max(...interfaceIds), 22, "internal Finance capabilities must not add a public interface use case");
});

test("Finance catalog freezes shared maturity, Manual Payout, and the four-point RCO-I09 contract", () => {
  const financeBlock = source.slice(
    source.indexOf('code: "FINANCE"'),
    source.indexOf('code: "PLATFORM"'),
  );
  const riskI09 = source.match(/iuc\("RCO-I09"[^\n]+/u)?.[0] ?? "";
  const payoutCreate = financeBlock.match(/uc\("FIN-13"[^\n]+/u)?.[0] ?? "";
  const payoutExecution = financeBlock.match(/uc\("FIN-14"[^\n]+/u)?.[0] ?? "";
  const beneficiary = financeBlock.match(/uc\("FIN-16"[^\n]+/u)?.[0] ?? "";
  const maturity = financeBlock.match(/uc\("FIN-18"[^\n]+/u)?.[0] ?? "";
  const paymentBlock = source.slice(source.indexOf('code: "PAYMENT"'), source.indexOf('code: "RISK"'));
  const paymentCreate = paymentBlock.match(/iuc\("PAY-I01"[^\n]+/u)?.[0] ?? "";

  assert.match(riskI09, /PAYOUT_CREATION.*PAYOUT_FINAL_APPROVAL.*PAYOUT_EXECUTION_CREATION.*PAYOUT_EXTERNAL_SUBMISSION/u);
  assert.match(riskI09, /assessment_id.*evaluation_id.*decision.*reason_codes.*matched_controls.*required_amount.*required_currency.*control_set_hash/u);
  assert.match(riskI09, /source_fact_version.*变更前.*expected version/iu);
  assert.doesNotMatch(riskI09, /eligibility_result_id|result_version|result_hash/u);
  assert.match(payoutCreate, /Client\/Local Partner.*AVAILABLE.*PENDING_APPROVAL.*PAYOUT_LOCK/u);
  assert.match(payoutCreate, /ADDED_ON_TOP.*DEDUCTED_FROM_PAYOUT.*beneficiary_amount.*owner_debit_amount.*cash_required_amount/u);
  assert.match(payoutCreate, /request_hash.*Owner\/OE\/currency.*Beneficiary content_hash.*全部金额.*fee_mode.*AVAILABLE\/LOCKED Account/u);
  assert.match(payoutExecution, /Final.*Approval.*Execution.*External.*RCO-I09/iu);
  assert.match(payoutExecution, /CREATED.*BANK_CASH.*UNKNOWN.*FAILED.*CONFIRMED.*PAID/u);
  assert.match(payoutExecution, /四个 checkpoint.*本地门禁.*相互独立.*本次.*required_amount/u);
  assert.match(payoutExecution, /eligibility_hash.*完整 RCO-I09 返回.*Owner\/OE\/currency.*owner_debit_amount.*Canonical/u);
  assert.match(beneficiary, /Finance-owned.*单表.*DRAFT.*VERIFIED.*content_hash/u);
  assert.match(maturity, /Client.*Partner.*PENDING.*balance_available_time.*PAYABLE_BALANCE_MATURED:<economics_id>.*AVAILABLE/u);
  assert.match(financeBlock, /eligibility_hash.*不能替代.*下一检查点/u);
  assert.match(paymentCreate, /PaymentSucceeded.*payment_intent_id\/payment_attempt_id.*channel_id\/channel_account_id\/mid_id.*channel_transaction_ref_id\/reference_type\/external_transaction_id/u);
  assert.match(requirementsSiteSource, /assessment_id.*evaluation_id.*decision.*reason_codes.*matched_controls.*required_amount.*required_currency.*control_set_hash/u);
  assert.match(architectureDiagramSource, /Channel 清算.*Manual Payout/u);
});

test("Commerce has no Fulfillment capability or Risk query", () => {
  const commerceBlock = source.slice(source.indexOf('code: "COMMERCE"'), source.indexOf('code: "PAYMENT"'));
  assert.doesNotMatch(commerceBlock, /Fulfillment|履约/u);
});

test("requirements freeze Agreement, direct Consumer Legal, and V5 commercial evidence", () => {
  assert.match(source, /client_agreement_id.*client_agreement_operating_entity_id/s);
  assert.match(source, /consumer_terms_document_id.*refund_policy_document_id.*privacy_notice_document_id/s);
  assert.match(source, /public_support_email.*public_support_phone/s);
  assert.match(source, /CODE-MOR:CCT:COMMERCIAL_CONTEXT:5/);
  assert.match(source, /Platform Service Charge.*platformServicePartyId/su);
  assert.doesNotMatch(source, /agreement_scope_binding_(?:id|version|hash)/);
  assert.doesNotMatch(source, /consumer_terms_version_id|seller_disclosure_version_id/);
});

test("CCT seller tax registration stays optional and explicit in the V5 Canonical JSON", () => {
  const cctI12 = source.match(/iuc\("CCT-I12"[^\n]+/u)?.[0] ?? "";
  const cctI13 = source.match(/iuc\("CCT-I13"[^\n]+/u)?.[0] ?? "";
  const consumerLegalContract = extractMarkdownSection(
    clientContractDatabaseSource,
    "### 5.2 Consumer Legal 解析",
  );
  const canonicalBundle = extractMarkdownSection(
    clientContractDatabaseSource,
    "### 5.4 CCT-I13 V5 原子 Bundle（legacy / pending migration）",
  );

  assert.match(cctI12, /public_tax_registration_number.*可空/u);
  assert.match(cctI13, /public_tax_registration_number.*可空.*JSON null.*不得省略/u);
  assert.match(consumerLegalContract, /public_tax_registration_number.*可空/u);
  assert.match(canonicalBundle, /public_tax_registration_number.*固定保留.*JSON `null`.*不得省略/u);
});

test("CCT exposes the Profile/Price target while preserving the legacy V5 migration contract", () => {
  const cct09 = source.match(/uc\("CCT-09"[^\n]+/u)?.[0] ?? "";
  const cctI05 = source.match(/iuc\("CCT-I05"[^\n]+/u)?.[0] ?? "";
  const cctI06 = source.match(/iuc\("CCT-I06"[^\n]+/u)?.[0] ?? "";
  const cctI13 = source.match(/iuc\("CCT-I13"[^\n]+/u)?.[0] ?? "";
  const cctI14 = source.match(/iuc\("CCT-I14"[^\n]+/u)?.[0] ?? "";
  const commerce03 = source.match(/uc\("COM-03"[^\n]+/u)?.[0] ?? "";
  const commerceI04 = source.match(/iuc\("COM-I04"[^\n]+/u)?.[0] ?? "";

  assert.match(cct09, /Client Pricing.*Partner Share.*Platform Service Charge.*Payment Method Profile Code.*Operating Entity.*Price.*百分比、固定金额或阶梯 Component\/Band/u);
  assert.match(cctI05, /Legacy V5 Scope.*Client Pricing Plan\/Version\/Scope.*Profile\/Price\/Band Wire.*(?:不得|禁止).*Scope ID.*Price ID/u);
  assert.match(cctI06, /Legacy V5 Scope.*Partner Share Plan\/Version\/Scope.*ACTUAL_CLIENT_FEE.*Profile\/Price\/Band Wire/u);
  assert.match(cctI13, /Legacy V5 Scope.*完整 V5 Bundle.*Profile\/Classification Proof.*唯一 ACTIVE Assignment/u);
  assert.match(cctI13, /Partner Share.*Platform Service Charge.*PLATFORM_SIGNATORY.*platformServicePartyId/u);
  assert.match(cctI13, /三份 Consumer Legal.*完整 Seller Disclosure/u);
  assert.match(cctI13, /Plan\/Version\/Scope 稳定 ID.*Version\/Scope 结构字段.*逐组件完整计算参数/u);
  assert.match(cctI13, /CODE-MOR:CCT:COMMERCIAL_CONTEXT:5/u);
  assert.match(cctI14, /Legacy V5 Scope.*term_scope_id.*agreement_operating_entity_id.*context_as_of_time.*历史 Scope 闭包/u);
  assert.match(cctI14, /owner_type\/owner_id.*agreement_id\/operating_entity_id.*Plan\/Version.*完整 Scope.*稳定排序.*Component/u);
  assert.match(cctI14, /不重新解析当前 ACTIVE.*Fail-Closed/u);
  assert.match(cctI14, /PLATFORM_SERVICE_CHARGE.*platform_service_party_id/u);
  assert.match(cctI14, /不接收、重算或验证整包 V5 commercial_context_hash/u);
  assert.match(commerce03, /不可变 Order Context/u);
  assert.match(commerce03, /Agreement Association.*Client Pricing\/可选 Partner Share Scope/u);
  assert.match(commerce03, /platform_service_charge_scope_id \+ platform_service_party_id/u);
  assert.match(commerceI04, /金额与税务引用.*Client Pricing\/可选 Partner Share Scope/u);
  assert.match(commerceI04, /platform_service_charge_scope_id \+ platform_service_party_id/u);
  assert.match(commerceI04, /order_id.*order_context_id.*commercial_context_time/u);
  assert.match(commerceI04, /PaymentSucceeded.*order_id\/order_context_id\/assignment_id.*Operating Entity.*金额\/币种.*industry_code\/mcc_code.*逐字段交叉校验/u);
  assert.match(commerceI04, /不返回 finance_entity_id.*Term Component.*服务费金额.*会计状态/u);

  assert.doesNotMatch(commerce03, /Commercial Term 头\/组件结构化快照/u);
  assert.doesNotMatch(commerceI04, /完整结构化组件参数/u);

  assert.doesNotMatch(source, /CODE-MOR:CCT:COMMERCIAL_CONTEXT:[234]/u);
  assert.doesNotMatch(source, /expected_commercial_context_hash/u);
  assert.doesNotMatch(
    source,
    /client_pricing_(?:content|scope)_hash|partner_share_(?:content|scope)_hash|canonical_hash/u,
  );
});

test("Finance returns a minimal tax result and exposes full tax evidence only by exact immutable query", () => {
  const finI17 = source.match(/iuc\("FIN-I17"[^\n]+/u)?.[0] ?? "";
  const finI22 = source.match(/iuc\("FIN-I22", "查询不可变 Tax Snapshot"[^\n]+/u)?.[0] ?? "";

  assert.match(finI17, /预分配 Order\/Item ID.*不可变 Tax Snapshot/u);
  assert.match(finI17, /tax_snapshot_id.*calculation_hash.*total_collected_tax.*total_reverse_charge_tax.*currency/u);
  assert.match(finI17, /source_order_item_id.*line_tax_amount/u);
  assert.match(finI17, /Fail-Closed.*禁止默认、当前或零税率/u);
  assert.doesNotMatch(finI17, /Registration.*Jurisdiction.*Rule\/Rate\/Treatment/u);

  assert.match(finI22, /tax_snapshot_id.*expected_calculation_hash/u);
  assert.match(finI22, /Tax Snapshot.*Tax Line.*Registration.*Jurisdiction.*Rule\/Rate\/Treatment.*责任模式/u);
  assert.match(finI22, /不得重新执行税务决策.*当前税务规则.*Fail-Closed/u);
  assert.match(financeDatabaseSource, /FIN-I17.*只返回.*tax_snapshot_id.*calculation_hash.*实收\/反向税汇总.*source_order_item_id/su);
  assert.match(financeDatabaseSource, /GetImmutableTaxSnapshot\(tax_snapshot_id, expected_calculation_hash\).*Tax Line.*Registration.*Jurisdiction.*Fail Closed/su);
});

test("interface use-case IDs remain unique after adding immutable reference queries", () => {
  const interfaceIds = [...source.matchAll(/iuc\("([A-Z]+-I\d+)"/gu)]
    .map((match) => match[1]);

  assert.equal(new Set(interfaceIds).size, interfaceIds.length);
});

test("CCT-I10 and CCT-I13 calculate readiness in real time without an eligibility object", () => {
  const cctBlock = source.slice(
    source.indexOf('code: "CLIENT_CONTRACT"'),
    source.indexOf('code: "COMMERCE"'),
  );
  const cctI10 = source.match(/iuc\("CCT-I10"[^\n]+/u)?.[0] ?? "";
  const cctI13 = source.match(/iuc\("CCT-I13"[^\n]+/u)?.[0] ?? "";

  assert.match(cctI10, /client_id.*country.*currency.*industry_code.*mcc_code/u);
  assert.match(cctI10, /READY \| NOT_READY \| RESTRICTED.*checked_at/u);
  assert.match(cctI10, /非 READY.*reason_code/u);
  assert.match(cctI10, /非持久化诊断.*不是交易证明/u);
  assert.match(cctI13, /每次调用实时校验/u);
  assert.match(cctI13, /Integration Readiness.*Routing Coverage/u);
  assert.match(cctI13, /成功返回完整 V5 Bundle/u);
  assert.doesNotMatch(
    source,
    /eligibility_(?:id|history_id|status)|evaluation_version|reason_summary_hash|dimension_set_hash|source_set_hash/u,
  );
  assert.doesNotMatch(cctBlock, /ClientIntegrationReadinessChanged|RoutingCoverageChanged|MidActivated|MidSuspended|MidClosed/u);
  assert.doesNotMatch(source, /ClientEligibilityChanged/u);
});

test("MVP defers automated retention and Legal Hold event orchestration", () => {
  const platformBlock = source.slice(
    source.indexOf('code: "PLATFORM"'),
    source.indexOf("export const ENTRY_APPLICATIONS"),
  );
  const platform13 = source.match(/uc\("PLT-13"[^\n]+/u)?.[0] ?? "";

  assert.match(platform13, /未来留存与 Legal Hold 治理占位/u);
  assert.match(platform13, /MVP 不发布.*不自动归档、匿名化或销毁/u);
  assert.match(platform13, /"later"/u);
  const eventDeclarations = [...source.matchAll(/(?:subscribes|publishes): \[[^\n]+/gu)]
    .map((match) => match[0])
    .join("\n");
  assert.doesNotMatch(eventDeclarations, /RetentionPolicyChanged|LegalHoldPlaced|LegalHoldReleased/u);
  assert.doesNotMatch(platformBlock, /Job & Retention/u);
});

test("Risk freezes OrderPreparationContext before the immutable Order Context exists", () => {
  const checkoutInputSnapshot = riskDatabaseSource.match(
    /^\| `checkout_input_snapshot` \|.*$/mu,
  )?.[0] ?? "";

  assert.match(checkoutInputSnapshot, /Commerce.*OrderPreparationContext/u);
  assert.match(checkoutInputSnapshot, /预分配.*order_id\/order_item_id/u);
  assert.match(checkoutInputSnapshot, /Client\/OE\/Source Tuple.*国家\/Locale.*industry_code\/mcc_code.*税前(?: `merchandise_amount\/discount_amount\/net_amount`|金额).*币种/u);
  assert.match(checkoutInputSnapshot, /V5 商业上下文.*Agreement\/Scope.*Platform Service Charge Scope\/Party/u);
  assert.match(checkoutInputSnapshot, /不消费 Finance Entity ID/u);
  assert.match(checkoutInputSnapshot, /三份 Legal\/Seller/u);
  assert.match(checkoutInputSnapshot, /Create Order.*交互前.*Consumer\/Network Evidence.*可空/u);
  assert.match(checkoutInputSnapshot, /Finance Tax Snapshot.*最终金额.*`order_context_id` 尚未生成.*禁止作为输入/u);
  assert.match(checkoutInputSnapshot, /input_hash.*完整 Canonical 输入/u);
  assert.match(checkoutInputSnapshot, /Risk 不回查 Commerce/u);
});

test("Risk decision releases use typed AST and engineering-registered handlers without SQL or Script", () => {
  const rco12 = source.split("\n").find((line) => line.includes('uc("RCO-12"')) ?? "";
  const rco13 = source.split("\n").find((line) => line.includes('uc("RCO-13"')) ?? "";

  assert.match(rco12, /工程注册.*Feature\/Action Handler.*类型安全 AST.*版本化 Release/u);
  assert.match(rco12, /implementation_version\/implementation_hash/u);
  assert.match(rco12, /八类静态校验.*Shadow.*两名不同 Reviewer.*原子发布/u);
  assert.match(rco12, /只能编排白名单能力/u);
  assert.match(rco12, /运营和规则作者.*不能提交或执行 SQL、Script/u);
  assert.match(rco12, /新增 Handler.*工程实现.*自动测试.*部署注册.*版本发布/u);
  assert.doesNotMatch(rco12, /受控 SQL\/Script|SQL\/Script Artifact/u);

  assert.match(rco13, /Shadow\/Replay.*只保存.*Trace.*SIMULATED Effect.*差异/u);
  assert.match(rco13, /不得.*真实 Action.*Restriction.*List Entry.*Limit Reservation\/Movement.*业务事件/u);
  assert.match(rco13, /质量门禁通过后才能进入审核/u);
  assert.match(source, /风险决策.*工程注册.*类型安全 AST.*禁止运营.*SQL\/Script/u);
  assert.doesNotMatch(source, /受控 SQL\/Script (?:Feature|Artifact)|代码规则及受控 SQL\/Script/u);
});

test("database guidance keeps stable Agreement associations separate from Profile-priced terms", () => {
  for (const [name, guidance] of [
    ["database overview", databaseOverviewSource],
    ["database design guide", databaseDesignGuideSource],
  ]) {
    assert.match(
      guidance,
      /Agreement\/Association 稳定 ID.*Payment Method Profile.*payment_method_profile_code.*Term Version(?:\/|、)Price(?:\/|、)Component\/Band/su,
      `${name} must distinguish stable Agreement associations from Profile-priced terms`,
    );
    assert.match(
      guidance,
      /独立 Agreement、Acceptance/u,
      `${name} must use independent legal facts as its immutable-history example`,
    );
    assert.match(
      guidance,
      /CCT.*没有 Agreement Version 表/u,
      `${name} must explicitly reject an Agreement Version table in CCT`,
    );
    assert.doesNotMatch(
      guidance,
      /合同版本/u,
      `${name} must not describe an Agreement Version as a current modeling pattern`,
    );
  }
});

test("Agreement replacement and status events carry distinct lifecycle evidence", () => {
  assert.match(source, /AgreementReplaced.*old_agreement_id.*new_agreement_id/s);
  assert.match(source, /AgreementStatusChanged.*agreement_id.*from_status.*to_status/s);
  assert.match(source, /状态变化不伪装成内容替换/);
});

test("Commerce delegates consumer-document persistence to Platform Support", () => {
  assert.doesNotMatch(commerceDatabaseSource, /\| Document \| `consumer_document` \|/u);
  assert.doesNotMatch(commerceDatabaseSource, /`t_consumer_document`/u);
  assert.match(commerceDatabaseSource, /Platform Support.*Credit Note.*原 Receipt/su);
  assert.match(commerceDatabaseSource, /Commerce 不保存 Consumer Document Current Projection/u);
});

test("Commerce authority freezes the generated 15-table Client API model while preserving historical evidence", () => {
  const tableHeadings = extractCommerceTableHeadings(commerceDatabaseSource);

  assert.deepEqual(tableHeadings, expectedCommerceTables);
  assert.equal(tableHeadings.length, 15);
  for (const tableName of removedCommerceTables) {
    assert.ok(
      !tableHeadings.includes(tableName),
      `removed Commerce table must not remain in the current catalog: ${tableName}`,
    );
  }

  assert.match(commercePrivacySimplificationDesignSource, /固化 2026-08-24 已确认/u);
  assert.match(commerceDocumentAuditCancelDesignSource, /14 张表.*223 个字段.*20 个唯一索引.*11 个普通索引.*14 条本库关系.*4 个 Diagram Group/su);
  assert.match(commerceDatabaseSource, /V1 权威逻辑目标已确认/u);
  assert.match(commerceDatabaseSource, /2026-09-11-cct-commerce-oe-pricing-model/u);
  assert.match(
    currentImplementationSource,
    /\| Commerce \| 15 \| 249 \| 24 \| 12 \| 15 \| — \| 4 \|/u,
  );
  assert.match(projectContextSource, /当前实现、验证与部署见.*CURRENT_IMPLEMENTATION\.md/su);
});

test("the current implementation ledger owns core model snapshots while entry documents route to it", () => {
  const sharedEntrySources = [
    rootAgentsSource,
    rootReadmeSource,
    agentReadmeSource,
    projectContextSource,
  ];

  for (const entrySource of sharedEntrySources) {
    assert.match(entrySource, /CURRENT_IMPLEMENTATION\.md/u);
    assert.match(entrySource, /DOCUMENT_GOVERNANCE\.md/u);
  }

  assert.match(currentImplementationSource, /\| Commerce \| 15 \| 249 \| 24 \| 12 \| 15 \| — \| 4 \|/u);
  assert.match(currentImplementationSource, /\| Payment Orchestration \| 27 \| 363 \| 41 \| 43 \| 63 \| 7 \| 7 \|/u);
  assert.match(currentImplementationSource, /\| Finance \| 84 \| 1,109 \| 145 \| 155 \| 160 \| 22 \| 6 \|/u);
  assert.match(currentImplementationSource, /\| Risk & Compliance \| 81 \| 1,003 \| 119 \| 87 \| 127 \| 9 \| 8 \|/u);
  assert.match(documentGovernanceSource, /Spec 冻结目标和契约，Plan 记录可执行步骤，实施记录保存实际证据/u);
  assert.match(documentGovernanceSource, /被取代时增加状态与窄范围后继链接，不重写历史/u);
});

test("the current ledger owns the Platform snapshot and governance routes control-plane authority", () => {
  assert.match(
    currentImplementationSource,
    /\| Platform Support \| 64 \| 831 \| 123 \| 102 \| 109 \| 5 \| 7 \|/u,
  );
  assert.match(
    documentGovernanceSource,
    /入口应用、Gateway、BFF、前端和部署边界[\s\S]*APPLICATION_ARCHITECTURE\.md/u,
  );
  assert.match(
    documentGovernanceSource,
    /六系统业务数据事实和状态机[\s\S]*database\/README\.md[\s\S]*当前专项决定和 PDManer/u,
  );

  assert.match(applicationArchitectureSource, /所有外部 HTTP API.*Platform.*公共 Route Catalog/su);
  assert.match(applicationArchitectureSource, /内部服务.*绕过 Gateway/su);
  assert.match(
    currentImplementationSource,
    /API Gateway[^\n]*动态 Snapshot、数据库 Target\/Route 绑定与转发[^\n]*已实现[^\n]*2026-09-12 本轮动态路由完整制品已部署 mor-test[^\n]*61 条 Operations Route[^\n]*既有 Client 代码[^\n]*Client 入口未开放/u,
  );
});

test("Project context owns exactly the current public control plane without runtime limiter state", () => {
  assert.match(
    projectContextSource,
    /Platform Support.*六张公共控制面当前配置表.*Target、Route、Header、Query Parameter、Guard Rule 和 Console Resource.*不拥有 Gateway 运行限流状态、数据库 Snapshot、Release、Ack、Tenant Override 或通用技术配置/su,
  );
  assert.doesNotMatch(
    projectContextSource,
    /current database owns no Gateway Route\/Guard\/runtime limiter state/iu,
  );
});

test("Application architecture exposes Client Manual Payout without a settlement alias", () => {
  assert.match(applicationArchitectureSource, /Client balance, statement, and Manual Payout request/u);
  assert.match(applicationArchitectureSource, /`\/api\/v1\/payouts\/\*\*`[\s\S]*Finance/u);
  assert.doesNotMatch(applicationArchitectureSource, /Client balance, statement, and settlement request/u);
  assert.doesNotMatch(applicationArchitectureSource, /`\/api\/v1\/settlements\/\*\*`/u);
});

test("Platform predecessor implementation record declares shared-gate completion", () => {
  assert.match(
    platformSimplificationImplementationSource,
    /63 表统一公共 API 控制面后继模型[\s\S]*最终共享文档与总门禁已完成/u,
  );
  assert.doesNotMatch(
    platformSimplificationImplementationSource,
    /最终共享文档与总门禁进行中/u,
  );
});

test("document governance keeps superseded Platform stages historical", () => {
  assert.match(
    documentGovernanceSource,
    /某项专项决策可以窄范围取代主题 Owner 的一部分[\s\S]*Superseded sections/u,
  );
  assert.match(
    documentGovernanceSource,
    /Spec 冻结目标和契约，Plan 记录可执行步骤，实施记录保存实际证据[\s\S]*被取代时增加状态与窄范围后继链接，不重写历史/u,
  );
  assert.match(agentReadmeSource, /DOCUMENT_GOVERNANCE\.md/u);
});

test("the architecture diagram keeps dispute recovery on the explicit Risk authority", () => {
  assert.doesNotMatch(
    architectureDiagramSource,
    /Dispute(?:Lost|OutcomeCorrected)[\s\S]{0,160}未结算余额/u,
  );
  assert.match(
    architectureDiagramSource,
    /DisputeLost\s*\/\s*DisputeOutcomeCorrected[\s\S]{0,500}outcome=LOST[\s\S]{0,500}principal_bearer=CLIENT[\s\S]{0,500}security_deposit_use_allowed=1[\s\S]{0,500}CLIENT_SECURITY_DEPOSIT\/NONE[\s\S]{0,500}CLIENT_RECOVERY_RECEIVABLE\/NONE/u,
  );
});

test("shared authority does not narrow corrected dispute outcomes back to DisputeLost only", () => {
  assert.match(
    documentGovernanceSource,
    /项目背景、六系统职责和术语[\s\S]*PROJECT_CONTEXT\.md/u,
  );
  assert.match(
    projectContextSource,
    /DisputeLost\/DisputeOutcomeCorrected[\s\S]*outcome=LOST[\s\S]*principal_bearer=CLIENT[\s\S]*security_deposit_use_allowed=1/u,
  );
  assert.doesNotMatch(projectContextSource, /only an explicitly allowed `DisputeLost` may/u);
});

test("Commerce and Payment reserve refunds locally while Finance posts success only", () => {
  assert.match(source, /Commerce 逻辑模型固定为 15 张表/u);
  assert.match(source, /生成模型、人工 Schema、Java、物理数据库、验证与部署的当前状态只按 CURRENT_IMPLEMENTATION 台账声明/u);
  assert.match(source, /仅保留 t_order_refund_position 与 t_refund_request/u);
  assert.match(source, /client_refund_id.*refund_amount.*channel_refund_id/su);
  assert.doesNotMatch(source, /refund_funding_reservation_id|FIN-I21|SOURCE_COVER|REFUND_CAP|RefundUnknown/u);
  assert.match(source, /PENDING、PROCESSING、SUCCEEDED、FAILED、CANCELLED/u);
  assert.match(source, /UNKNOWN.*只保留在 Payment 本地.*Commerce.*PROCESSING/u);
  assert.match(source, /Payment Refund Position.*CAS.*reserved_amount.*Channel Refund/u);
  assert.match(source, /权威成功立即消费 Commerce 本地额度，权威失败或取消立即释放/u);
  assert.match(source, /Finance 只消费 RefundSucceeded.*记账/u);
  assert.match(commerceDatabaseSource, /Commerce 本地预占成功后直接调用 PAY-I04/u);
  assert.match(commerceDatabaseSource, /Payment 以自身 Refund Position CAS 创建 Channel Refund/u);
  assert.match(commerceDatabaseSource, /CANCELLED.*Channel Action 提交前.*受控主动取消/u);
  assert.doesNotMatch(source, /RefundFunding(?:Released|Consumed)/u);
});

test("Commerce does not persist generic BCR history", () => {
  assert.match(commerceDatabaseSource, /Commerce V1 不保存 `business_change_record\/business_change_version`/u);
  assert.doesNotMatch(commerceDatabaseSource, /\| Audit \| `business_change_(?:record|version)`/u);
  assert.match(commerceDatabaseSource, /冻结或追加式事实/u);
});

test("PAY-I13 resolves the authoritative reversal branch without a caller-selected payment reference", () => {
  const payI13 = source.split("\n").find((line) => line.includes('iuc("PAY-I13"')) ?? "";

  assert.match(payI13, /只供 Commerce 内部调用/u);
  assert.match(payI13, /调用方不提交 payment_attempt_id 或 target_channel_txn_ref_id/u);
  assert.match(payI13, /锁定 Intent.*Attempt.*Action.*Channel Transaction Ref.*Expected Version\/CAS/su);
  assert.match(payI13, /REFUND_REQUIRED.*Commerce.*标准全额.*Refund Request/su);
  assert.match(payI13, /PARTIAL_CAPTURE_REVERSAL_NOT_SUPPORTED/u);
  assert.doesNotMatch(payI13, /PAY-I13 VoidAuthorization|PARTIAL_CAPTURE_REVERSAL_UNSUPPORTED/u);
});

test("PAY-I02 keeps the first-channel-submit check fail-closed without persistent Gate facts", () => {
  const payI02 = source.split("\n").find((line) => line.includes('iuc("PAY-I02"')) ?? "";

  assert.match(payI02, /首次 AUTHORIZE\/SALE\/CAPTURE Channel 提交前同步查询 CCT.*Risk/u);
  assert.match(payI02, /检查通过结果不持久化为独立 Gate/u);
  assert.match(payI02, /明确阻断、缺失、超时或技术失败均 Fail-Closed/u);
  assert.match(payI02, /Attempt\/Action FAILED.*failure_stage\/status_reason_code.*状态流转/u);
  assert.match(payI02, /不创建 Channel Interaction/u);
});

test("Payment requirements freeze the generic customer-action, exact refund and duplicate-success contracts", () => {
  const pay06 = source.split("\n").find((line) => line.includes('uc("PAY-06"')) ?? "";
  const pay14 = source.split("\n").find((line) => line.includes('uc("PAY-14"')) ?? "";
  const payI02 = source.split("\n").find((line) => line.includes('iuc("PAY-I02"')) ?? "";
  const payI04 = source.split("\n").find((line) => line.includes('iuc("PAY-I04"')) ?? "";

  assert.match(payI02, /Payment Method Catalog.*payment_method_code.*Attempt/u);
  assert.match(payI02, /Payment Customer Action.*REDIRECT.*THREE_DS_CHALLENGE.*SDK.*APP_SWITCH.*QR_CODE/u);
  assert.match(payI02, /稳定.*channel_reference.*不持久化.*Redirect URL.*Client Secret.*SDK Token.*QR Payload/u);
  assert.match(pay06, /PAYMENT_INTENT.*PAYMENT_ATTEMPT.*PAYMENT_ACTION.*CHANNEL_REFUND.*DUPLICATE_PAYMENT_CASE/u);
  assert.match(pay06, /Callback\/Item\/Exception.*Dispute.*Event.*不重复写入.*状态流转/u);
  assert.match(payI04, /Payment Refund Position.*original_channel_txn_ref_id.*target_channel_txn_ref_id.*REFUND.*original_channel_txn_ref_id/u);
  assert.match(pay14, /同一 Intent.*第二.*不同.*(?:SALE|完成资金的 CAPTURE).*成功事实/u);
  assert.match(pay14, /重复 Callback.*同一外部交易.*QUERY.*AUTHORIZE.*CAPTURE.*不.*重复支付/u);
  assert.match(pay14, /successful_attempt_id.*不(?:替换|改写).*CONFLICTED.*补偿退款/u);
});

test("Acceptance readiness is derived from three immutable acceptances bound to network evidence", () => {
  const acceptance = commerceDatabaseSource;
  const networkEvidence = commerceDatabaseSource;

  assert.doesNotMatch(commerceDatabaseSource, /^#### `consumer_acceptance_summary`$/mu);
  assert.match(acceptance, /CONSUMER_TERMS.*REFUND_POLICY.*PRIVACY_NOTICE/su);
  assert.match(acceptance, /LEGAL_ACCEPTANCE/u);
  assert.match(acceptance, /Consumer Contract 1:N Acceptance/u);
  assert.match(acceptance, /三个显式 Acceptance ID/u);
  assert.match(networkEvidence, /client_ip_address.*user_agent.*trusted_proxy_chain/su);
  assert.match(networkEvidence, /不保存 Network Vault Token、Blind Index、Key Version 或 `device_identifier`/u);
});

test("Commerce design and navigation expose the current privacy and Fulfillment-removal decision", () => {
  assert.match(commerceOrderContextDesignSource, /状态：用户已确认/u);
  assert.match(commerceConsumerEvidenceDesignSource, /状态：历史 23 表阶段/u);
  assert.match(commercePrivacySimplificationDesignSource, /固化 2026-08-24 已确认/u);
  assert.match(
    agentReadmeSource,
    /Commerce.*COMMERCE_DATABASE\.md.*COMMERCE_REAL_WORLD_SCENARIOS\.md.*commerce-document-audit-cancel-reversal-design\.md.*commerce-privacy-fulfillment-simplification-design\.md.*commerce-consumer-analytics-evidence-design\.md/su,
  );
  assert.match(
    databaseOverviewSource,
    /Commerce.*COMMERCE_DATABASE\.md.*COMMERCE_REAL_WORLD_SCENARIOS\.md.*commerce-document-audit-cancel-reversal-design\.md/su,
  );
  assert.match(
    documentGovernanceSource,
    /六系统业务数据事实和状态机[\s\S]*database\/README\.md[\s\S]*当前专项决定和 PDManer/u,
  );
  assert.match(projectContextSource, /更窄的业务不变量由.*database\/README\.md.*专项决策拥有/su);
  assert.match(currentImplementationSource, /\| Commerce \| 15 \| 249 \| 24 \| 12 \| 15 \| — \| 4 \|/u);
});

test("agent guidance routes consumer evidence work and preserves its non-negotiable boundaries", () => {
  assert.match(
    rootAgentsSource,
    /Read `agent-docs\/plans\/2026-08-24-commerce-privacy-fulfillment-simplification-design\.md` before changing Commerce Consumer Context, Checkout Network Evidence, Consumer Acceptance, COM-I10 Payment Readiness/u,
  );
  assert.match(rootAgentsSource, /Never add a default Vault field bundle/u);
  assert.match(rootAgentsSource, /Token, Blind Index\/Key Version, Masked Value, Vault Record Version and Metadata Hash.*demonstrated/u);
  assert.match(rootAgentsSource, /COM-I10.*exactly three immutable.*CONSUMER_TERMS.*REFUND_POLICY.*PRIVACY_NOTICE/su);
  assert.match(rootAgentsSource, /three explicit Acceptance IDs.*acceptance_completed_at.*Order Version.*expiry/u);
  assert.match(rootAgentsSource, /consumer_reference.*non-PII.*Email.*Phone.*government identifier/su);
  assert.match(rootAgentsSource, /Email and Phone each use an approved stable Vault Value Token/su);
  assert.match(rootAgentsSource, /client_ip_address.*user_agent.*trusted_proxy_chain/su);
  assert.match(rootAgentsSource, /DisputeEvidenceRequested.*Client deadline.*Channel deadline/su);
});

test("Commerce real-world scenario maps exactly the 15 approved target tables", () => {
  assert.match(commerceScenarioSource, /NOVA_COMMERCE_UAE_2026/u);
  assert.match(commerceScenarioSource, /15 表目标逐表映射/u);
  assert.match(commerceScenarioSource, /当前生成模型为 15 表 \/ 249 字段.*Schema 尚未应用/su);
  for (const tableName of expectedCommerceTables) {
    assert.match(
      commerceScenarioSource,
      new RegExp("\\| `" + tableName + "` \\|", "u"),
      `real-world scenario must map ${tableName}`,
    );
  }
  for (const tableName of [
    "order_tax_line",
    "order_commercial_term",
    "order_term_component",
  ]) {
    assert.doesNotMatch(
      commerceScenarioSource,
      new RegExp("\\| `" + tableName + "` \\|", "u"),
      `real-world scenario must not map removed local copy ${tableName}`,
    );
  }
});

test("requirements route on-demand dispute RFI notification through Platform Support", () => {
  const risk09 = source.match(/uc\("RCO-09"[^\n]+/u)?.[0] ?? "";
  const platform04 = source.match(/uc\("PLT-04"[^\n]+/u)?.[0] ?? "";

  assert.match(risk09, /EVIDENCE_REQUIRED.*DisputeEvidenceRequested/u);
  assert.match(
    platform04,
    /收到 DisputeEvidenceRequested.*当前 ACTIVE.*Subscription.*VERIFIED.*Endpoint Revision.*冻结/u,
  );
  assert.match(platform04, /只负责通知与文件.*不拥有争议状态.*通用 RFI/u);
});

test("Platform Support requirements route current model statistics to the ledger and preserve the control-plane baseline", () => {
  assert.match(source, /Platform Support 当前生成模型统计由 agent-docs\/CURRENT_IMPLEMENTATION\.md 维护/u);
  assert.match(source, /31 条复合外键、85 条 CHECK 契约与 36 个 `\*_hash` 字段/u);
  assert.match(source, /Client & Contract 当前物理模型为 40 表、443 字段、63 个唯一索引、46 个访问索引、67 条本库关系、7 条 1:1 关系和 6 个 Diagram Groups/u);
  assert.match(source, /Risk & Compliance 当前生成模型统计由 agent-docs\/CURRENT_IMPLEMENTATION\.md 维护/u);
  assert.match(source, /Reliable Technical Execution/u);
  assert.match(source, /只保留本系统 compensation_event 与 command_idempotency/u);
  assert.doesNotMatch(source, /Technical Job|Technical Configuration/u);
  assert.match(platformDatabaseSource, /63 (?:张)?表.*822 (?:个)?字段.*123 个唯一索引.*99 个访问索引.*107 条本库关系.*5 条 1:1.*7 个 Diagram Groups/su);
  assert.match(platformDatabaseSource, /31 条.*复合 FK.*85 条.*CHECK.*36 个.*_hash/su);
});

test("Platform Support catalog uses Tenant credentials and derives readiness without persistence", () => {
  const platform = extractSystemBlock("PLATFORM");
  const platform02 = extractCatalogLine(platform, "uc", "PLT-02");
  const platform16 = extractCatalogLine(platform, "uc", "PLT-16");
  const platformI03 = extractCatalogLine(platform, "iuc", "PLT-I03");
  const platformI13 = extractCatalogLine(platform, "iuc", "PLT-I13");
  const platformI19 = extractCatalogLine(platform, "iuc", "PLT-I19");
  const platformI20 = extractCatalogLine(platform, "iuc", "PLT-I20");
  const platformI22 = extractCatalogLine(platform, "iuc", "PLT-I22");

  assert.doesNotMatch(platform, /uc\("PLT-09"|Technical Configuration/u);
  assert.match(platform02, /\["client", "operations"\].*ACTIVE CLIENT Tenant/u);
  assert.doesNotMatch(platform02, /"partner"/u);
  assert.match(platformI03, /Tenant.*Credential.*ACTIVE Scope/u);
  assert.match(platformI03, /PHC verifier.*tenant_id.*tenant_type.*owner_ref_id.*credential_id/u);
  assert.match(platformI03, /owner_ref_id.*Access Tenant/u);
  assert.match(platformI03, /ACTIVE CLIENT Tenant.*同一 Credential/u);
  assert.match(platformI03, /调用方.*不得覆盖.*owner_ref_id|禁止调用方覆盖.*owner_ref_id/u);
  assert.match(platformI03, /不得.*Key Prefix.*推断/u);
  assert.match(platformI13, /同一一致查询快照/u);
  assert.match(platformI13, /Tenant.*API Credential.*Scope.*Endpoint Revision.*Subscription/u);
  assert.match(platformI13, /\["Client & Contract", "Client Console", "Operations Console"\]/u);
  assert.match(platformI13, /READY \| NOT_READY \| RESTRICTED.*checked_at.*reason_code/u);
  assert.deepEqual(
    [...new Set(platformI13.match(/(?<![A-Z_])(?:READY|NOT_READY|RESTRICTED|UNKNOWN)(?![A-Z_])/gu) ?? [])].sort(),
    ["NOT_READY", "READY", "RESTRICTED"],
  );
  assert.match(platformI13, /技术失败.*接口错误.*不携带 status/u);
  assert.doesNotMatch(platformI13, /Partner Console|UNKNOWN/u);
  assert.match(platformI13, /同步派生.*Fail-Closed/u);
  assert.match(platformI13, /不保存.*Evaluation.*Version.*Hash/u);
  assert.doesNotMatch(platformI13, /Requirement Config|Platform 继续拥有该事实/u);
  assert.doesNotMatch(platform, /ConfigurationActivated|ClientIntegrationReadinessChanged/u);
  assert.match(platform16, /所有外部 HTTP API.*六类.*Route.*Target.*Guard/u);
  assert.match(platformI19, /permission_id.*required_scope_code.*互斥.*Platform 数据库管理.*完整快照.*不再要求静态业务 Registry/u);
  assert.match(platformI20, /每次读取完整全局不可变 Snapshot.*不使用 ETag.*304/u);
  assert.match(platformI20, /完整校验.*原子切换.*Fail-Closed/u);
  assert.match(platformI22, /OPERATIONS.*ACTIVE.*RETIRING.*RS256.*RSA-2048\+.*Fail-Closed/u);
  assert.match(platformI22, /operations-console.*CUSTOMER.*Client\/Partner.*client-console\/CLIENT.*partner-console\/PARTNER/u);
  assert.match(platform, /不拥有 Redis 运行时 Bucket、Counter、Lease 或 TTL/u);
  assert.doesNotMatch(platform, /不拥有当前 Gateway Route、Guard Profile/u);
});

test("unified authentication catalog keeps Account, Tenant, onboarding, and Risk ownership distinct", () => {
  const cct = extractSystemBlock("CLIENT_CONTRACT", "COMMERCE");
  const risk = extractSystemBlock("RISK_COMPLIANCE", "FINANCE");
  const platform = extractSystemBlock("PLATFORM");

  assert.match(extractCatalogLine(platform, "uc", "PLT-14"), /管理 CUSTOMER 全局账号与 Google 绑定/u);
  assert.match(extractCatalogLine(platform, "uc", "PLT-15"), /管理 Tenant Member、Role 与 Invitation/u);
  const platformI02 = extractCatalogLine(platform, "iuc", "PLT-I02");
  assert.match(platformI02, /Operations Console BFF.*Client Console BFF.*Partner Console BFF.*内部业务服务/u);
  assert.match(platformI02, /Gateway 可信上下文/u);
  assert.match(platformI02, /不得?把 JWT Claim 当作资源授权事实/u);
  assert.match(extractCatalogLine(platform, "iuc", "PLT-I15"), /CUSTOMER 注册、Email 验证、Password\/Google 登录、Refresh、Logout/u);
  assert.match(extractCatalogLine(platform, "iuc", "PLT-I16"), /Account 或单一 Tenant Member 上下文.*Audience Token/u);
  const platformI04 = extractCatalogLine(platform, "iuc", "PLT-I04");
  const platformI17 = extractCatalogLine(platform, "iuc", "PLT-I17");
  assert.match(platformI04, /Client Console BFF/u);
  assert.match(platformI04, /Account Context.*requested_by_account_id.*PENDING.*TENANT_ONBOARDING_OWNER/u);
  assert.match(platformI17, /CCT Access Tenant Provision\/Status 版本化投影/u);
  assert.match(
    platformI17,
    /tenant_type.*owner_ref_id.*requested_by_account_id.*source_version.*payload_hash/u,
  );
  assert.match(platformI17, /同版本同 Hash.*幂等.*同版本异 Hash.*冲突.*旧版本.*拒绝/u);
  assert.doesNotMatch(platformI17, /owner_type|owner_id|sourceVersion/u);
  assert.match(extractCatalogLine(platform, "iuc", "PLT-I18"), /创建、撤销和接受 Tenant Invitation/u);
  const cctI15 = extractCatalogLine(cct, "iuc", "CCT-I15");
  assert.match(cctI15, /START_OR_RESUME.*DRAFT.*requested_by_account_id.*Platform/u);
  assert.match(cctI15, /SUBMIT.*File ID.*Risk/u);
  assert.match(extractCatalogLine(cct, "iuc", "CCT-I16"), /requested_by_account_id.*本人 Client\/Partner Application 进度/u);
  assert.match(extractCatalogLine(risk, "iuc", "RCO-I11"), /Typed Namespace\/Application\/Role\/Qualification\/Submission Hash.*主 Role Case/u);

  assert.match(extractCatalogLine(cct, "uc", "CCT-01"), /Account.*先存在.*PENDING Client.*Application/u);
  assert.match(extractCatalogLine(cct, "uc", "CCT-05"), /Account.*先存在.*PENDING Local Partner.*Application/u);
  assert.doesNotMatch(source, /Client、Partner、Consumer.*待.*设计|不为 Client、Partner.*认证|外部 IdP.*唯一/u);
});

test("unified authentication and public gateway decisions preserve exact channels, profiles, cookies, and guards", () => {
  const gateway = source.match(/code: "API_GATEWAY"[\s\S]+?\n {2}\},/u)?.[0] ?? "";

  assert.match(gateway, /Client、Partner 与 Operations.*Platform.*Audience.*RS256 Access JWT/u);
  assert.doesNotMatch(gateway, /其他渠道凭据方案仍待设计/u);
  assert.match(source, /Operations = Email\/Password only；admin 是唯一非 Email Bootstrap/u);
  assert.match(source, /CUSTOMER = Email\/Password \+ Google OIDC/u);
  assert.match(source, /一个 CUSTOMER Account 可同时加入 CLIENT 与 PARTNER Tenant/u);
  assert.match(source, /一个 Access Token 绑定零个或一个 Tenant Member/u);
  assert.match(source, /业务 Email OTP 与 Authentication Challenge 是独立能力/u);
  assert.match(source, /Authorization Code \+ PKCE \+ state \+ nonce/u);
  assert.match(source, /相同 Email.*绝不自动绑定 Google/u);
  assert.match(source, /__Secure-operations-refresh.*Path=\/api\/operations\/auth\/v1/u);
  assert.match(source, /__Secure-client-refresh.*Path=\/api\/client\/auth\/v1/u);
  assert.match(source, /__Secure-partner-refresh.*Path=\/api\/partner\/auth\/v1/u);
  assert.match(source, /Route Channel.*OPERATIONS、CLIENT、PARTNER、CHECKOUT、CLIENT_API、CHANNEL_CALLBACK/u);
  assert.match(source, /Security Profile.*OPERATIONS_JWT、CLIENT_JWT、PARTNER_JWT、CLIENT_API_CREDENTIAL、CHECKOUT_SESSION、CHANNEL_CALLBACK、PUBLIC/u);
  assert.match(source, /SHARED_ROUTE、CLIENT_IP、AUTHENTICATED_PRINCIPAL、TENANT/u);
  assert.match(source, /TENANT.*PLT-I03.*可信 tenant_id.*Redis/u);
  assert.doesNotMatch(source, /Gateway 数据库不拥有 Route|Platform.*不拥有.*Route.*Path.*Method.*Upstream/u);
});

test("matching Google Email requires existing Password reauthentication before explicit linking", () => {
  const platform = extractSystemBlock("PLATFORM");
  const decisions = source.slice(
    source.indexOf("export const DECISIONS"),
    source.indexOf("export const OPEN_QUESTIONS"),
  );

  assert.match(extractCatalogLine(platform, "uc", "PLT-14"), /已验证且匹配的 Google Email.*现有 Password.*重新认证成功.*显式绑定/u);
  assert.match(extractCatalogLine(platform, "iuc", "PLT-I15"), /已验证且匹配的 Google Email.*现有 Password.*重新认证成功.*显式绑定/u);
  assert.match(decisions, /已验证且匹配的 Google Email.*现有 Password.*重新认证成功.*显式绑定/u);
});

test("Audience tokens omit aggregate Tenant IAM and Email claims", () => {
  const platform = extractSystemBlock("PLATFORM");
  const decisions = source.slice(
    source.indexOf("export const DECISIONS"),
    source.indexOf("export const OPEN_QUESTIONS"),
  );

  assert.match(extractCatalogLine(platform, "iuc", "PLT-I16"), /不得.*全部 Tenants、Roles、Permissions 或 Email/u);
  assert.match(decisions, /不得.*全部 Tenants、Roles、Permissions 或 Email/u);
});

test("console diagrams route browsers through channel BFFs and retain domain ownership", () => {
  assert.match(architectureDiagramSource, /Client Console BFF.*Partner Console BFF.*Operations Console BFF/su);
  assert.match(architectureDiagramSource, /Platform.*认证.*Tenant IAM.*权威/su);
  assert.match(architectureDiagramSource, /CCT.*Client\/Local Partner\/Application.*Risk.*资格/su);
  assert.doesNotMatch(architectureDiagramSource, /"Configuration"/u);

  const clientConsole = source.match(/code: "CLIENT_CONSOLE"[\s\S]+?\n {2}\},/u)?.[0] ?? "";
  const partnerConsole = source.match(/code: "PARTNER_CONSOLE"[\s\S]+?\n {2}\},/u)?.[0] ?? "";
  const operationsConsole = source.match(/code: "OPERATIONS_CONSOLE"[\s\S]+?\n {2}\},/u)?.[0] ?? "";
  assert.match(clientConsole, /Email 注册.*Google Login.*Invitation.*Tenant 选择/u);
  assert.match(partnerConsole, /Email 注册.*Google Login.*Invitation.*Tenant 选择/u);
  assert.match(operationsConsole, /Email\/Password only/u);
  assert.doesNotMatch(operationsConsole, /Google Login/u);
});

test("all public HTTP APIs use the Platform control plane while runtime safety remains Gateway-owned", () => {
  const platform = source.match(
    /code: "PLATFORM"[\s\S]+?\n {2}\},\n\];/u,
  )?.[0] ?? "";

  assert.notEqual(platform, "");
  assert.match(source, /所有外部 HTTP API.*统一进入 Platform.*公共 Route Catalog.*内部服务调用.*绕过 Gateway/u);
  assert.match(source, /恰好六张当前配置表.*Target.*Route.*Header.*Query.*Guard.*Console Resource/u);
  assert.match(source, /不增加 Gateway `\*_ver`、Release、Snapshot、Ack 或 Tenant Guard Override/u);
  assert.match(source, /Approved Target Registry.*target_service_code.*channel.*discovery_service_name.*双重校验/u);
  assert.match(source, /全局 Guard 参数.*可信 tenant_id.*Redis.*Tenant 隔离/u);
  assert.doesNotMatch(source, /动态 Snapshot.*尚未切换.*静态 Operations Catalog.*部署前运行时事实/u);
  assert.match(currentImplementationSource, /API Gateway[^\n]*动态 Snapshot、数据库 Target\/Route 绑定与转发[^\n]*已实现/u);
  assert.match(source, /公网 TLS.*Nginx.*覆盖.*X-Real-IP.*Gateway.*Guard.*访问日志/u);
  assert.match(source, /Gateway、BFF、Platform Support、Vault.*核心 Java 服务.*私有 HTTP.*不持有 X\.509/u);
  assert.doesNotMatch(gatewayControlPlaneDesignSource, /Forwarded Chain/u);
  assert.doesNotMatch(gatewayControlPlaneDesignSource, /全部\s*`X-Forwarded-`\s*前缀/u);
  assert.match(gatewayControlPlaneDesignSource, /转发前.*代理 Header.*删除/u);
  assert.match(
    gatewayControlPlaneDesignSource,
    /X-Forwarded-For.*X-Forwarded-Host.*X-Forwarded-Port.*X-Forwarded-Prefix.*X-Forwarded-Proto.*X-Forwarded-Server.*X-Forwarded-Ssl/su,
  );
  assert.doesNotMatch(source, /当前 Platform Support 模型.*不发布或修改.*Route|未来变化必须先获得新的设计审批/u);
});

test("Operations authentication stays behind its BFF and Gateway validates only the short-lived access token", () => {
  const platform01 = source.match(/uc\("PLT-01"[^\n]+/u)?.[0] ?? "";
  const platformI01 = source.match(/iuc\("PLT-I01"[^\n]+/u)?.[0] ?? "";
  const gateway = source.match(/code: "API_GATEWAY"[\s\S]+?\n {2}\},/u)?.[0] ?? "";

  assert.match(platform01, /内部 Operations 账号.*不可逆密码校验值.*15 分钟.*RS256.*可撤销 Refresh Session/u);
  assert.match(platform01, /operations-console-web.*API Gateway.*operations-console-bff.*Platform 内部接口/u);
  assert.match(platformI01, /仅供 operations-console-bff.*登录、Refresh、Logout/u);
  assert.match(gateway, /Operations.*Platform 签发的对应 Audience 最小 RS256 Access JWT/u);
  assert.match(gateway, /不处理账号密码登录、Refresh、Logout 业务或 Token 签发/u);
  assert.match(gateway, /Operations 浏览器只转发到 operations-console-bff/u);
});

test("Platform persists the Commerce Order Context reference before FiscalRecordCreated arrives", () => {
  assert.match(platformDatabaseSource, /order_id.*commerce_order_context_id.*consumer_terms_file_id.*refund_policy_file_id.*privacy_notice_file_id/s);
  assert.match(platformDatabaseSource, /file_id \+ content_hash.*核对.*不复制三份 Content Hash/su);
  assert.match(platformDatabaseSource, /Event Key.*Version.*Payload Hash/su);
  assert.match(platformDatabaseSource, /同来源键\/版本、不同 Payload Hash.*CONFLICT/u);
  assert.match(platformDatabaseSource, /双输入齐备后推进 `READY_TO_RENDER`/u);
  assert.doesNotMatch(platformDatabaseSource, /source_set_hash|consumer_terms_content_hash|refund_policy_content_hash|privacy_notice_content_hash/u);
});

test("Commerce uses Client business references and explicit Order Context facts without an aggregate snapshot hash", () => {
  const commerce01 = source.match(/uc\("COM-01"[^\n]+/u)?.[0] ?? "";
  const commerce02 = source.match(/uc\("COM-02"[^\n]+/u)?.[0] ?? "";
  const commerceI01 = source.match(/iuc\("COM-I01"[^\n]+/u)?.[0] ?? "";
  const commerceI04 = source.match(/iuc\("COM-I04"[^\n]+/u)?.[0] ?? "";
  const commerceI10 = source.match(/iuc\("COM-I10"[^\n]+/u)?.[0] ?? "";

  assert.match(commerce01, /Client 命名空间.*clientOrderId.*新的 Order.*新的 clientOrderId/u);
  assert.match(commerce02, /\(client_id, client_order_id\).*Idempotency-Key/u);
  assert.match(commerceI01, /clientOrderId/u);
  assert.match(commerceI04, /order_id.*order_context_id/u);
  assert.match(commerceI04, /operating_entity_id.*assignment_id/u);
  assert.match(commerceI10, /order_id \+ order_context_id/u);
  assert.doesNotMatch(source, /creation_request_hash|order_snapshot_id|snapshot_schema_version|CODE-MOR:COMMERCE:ORDER_SNAPSHOT/u);
});

test("COM-I10 derives Payment readiness from three explicit immutable acceptances", () => {
  const commerceI10 = source.match(/iuc\("COM-I10"[^\n]+/u)?.[0] ?? "";

  assert.match(commerceI10, /服务端.*实时/u);
  assert.match(commerceI10, /CONSUMER_TERMS.*REFUND_POLICY.*PRIVACY_NOTICE/u);
  assert.match(commerceI10, /consumer_terms_acceptance_id/u);
  assert.match(commerceI10, /refund_policy_acceptance_id/u);
  assert.match(commerceI10, /privacy_notice_acceptance_id/u);
  assert.match(commerceI10, /acceptance_completed_at/u);
  assert.match(commerceI10, /Order Version.*Expires At/u);
  assert.doesNotMatch(commerceI10, /Summary|summary|Accepted Set Hash|completion_acceptance_id/u);
});

test("Commerce and Risk use stable Consumer tokens and restricted Network Evidence through the internal minimum contract", () => {
  const commerceBlock = source.match(/code: "COMMERCE"[\s\S]*?\n {2}\},\n {2}\{\n {4}code: "PAYMENT"/u)?.[0] ?? "";
  const riskI04 = source.match(/iuc\("RCO-I04"[^\n]+/u)?.[0] ?? "";
  const commerceI11 = source.match(/iuc\("COM-I11"[^\n]+/u)?.[0] ?? "";
  const commerceI12 = source.match(/iuc\("COM-I12"[^\n]+/u)?.[0] ?? "";

  assert.match(commerceBlock, /consumer_reference.*非 PII.*不透明/su);
  assert.match(commerceBlock, /consumer_email_vault_token.*consumer_phone_vault_token.*consumer_profile_vault_token/su);
  assert.match(commerceBlock, /client_ip_address.*user_agent.*trusted_proxy_chain/su);
  assert.match(riskI04, /PAYMENT_START.*Network Evidence/u);
  assert.match(riskI04, /稳定 consumer_email_vault_token.*consumer_phone_vault_token/su);
  assert.match(riskI04, /client_ip_address.*user_agent.*trusted_proxy_chain/su);
  assert.match(riskI04, /不得进入外部 API、普通日志、MQ 或通用审计 JSON/u);
  assert.match(commerceI11, /consumer_reference.*非 PII.*拒绝 Email、Phone、证件号/su);
  assert.match(commerceI11, /Profile Payload 禁止重复 Email\/Phone/u);
  assert.match(commerceI12, /不调用 Vault.*不保存 Blind Index\/Key Version\/device identifier/u);
  assert.doesNotMatch(commerceBlock, /consumer_(?:ref|email|phone)_blind_(?:index|key)/u);
});

test("RCO-I04 separates successful decisions from technical outcomes and typed Limit obligations", () => {
  const riskI04 = source.match(/iuc\("RCO-I04"[^\n]+/u)?.[0] ?? "";

  assert.doesNotMatch(source, /iuc\("RCO-I05", "检查和占用交易限额"/u);
  assert.match(riskI04, /SUCCEEDED.*proposed_decision.*business_decision/su);
  assert.match(riskI04, /FAILED\/TIMED_OUT.*不携带业务决定.*不执行默认放行/su);
  assert.match(riskI04, /FAILED\/TIMED_OUT.*不形成 REAL 或 SIMULATED Action Effect/su);
  assert.match(riskI04, /PAYMENT.*ALLOW.*RESERVE_LIMIT_GROUP 系统义务.*0\.\.N 条 Decision Point Limit Binding.*Reservation Group/su);
  assert.match(riskI04, /零 Binding.*空 Group/su);
  assert.match(riskI04, /不生成 Action Intent\/Effect/u);
  assert.match(riskI04, /准确 Point Version.*数据库可重算/u);
});

test("RCO-I12 closes the private Rule Set authoring and TEST Candidate validation slice", () => {
  const riskBlock = extractSystemBlock("RISK_COMPLIANCE", "FINANCE");
  const riskI12 = extractCatalogLine(riskBlock, "iuc", "RCO-I12");

  assert.match(riskI12, /内部[^。]*Risk-owned[^。]*CHECKOUT_PREPARE/u);
  assert.match(riskI12, /Operations Console → API Gateway → Operations Console BFF → Platform PLT-I02 → Risk 私有 API/u);
  assert.match(riskI12, /结构化规则构建器[^。]*不向运营人员暴露 JSON、AST、Node Key 或 Sequence/u);
  assert.match(riskI12, /BFF[^。]*严格校验完整 Risk 上游[^。]*浏览器[^。]*不返回 handlerCode\/implementationVersion\/implementationHash/u);
  assert.match(riskI12, /Authoring Context[^。]*9 个/u);
  assert.match(riskI12, /65,536 UTF-8 Bytes[^。]*16,384 Bytes/u);
  assert.match(riskI12, /DRAFT[^。]*created_by[^。]*change_reason/u);
  assert.match(riskI12, /Freeze[^。]*原子冻结 Rule Set 与 Companion Point Version/u);
  assert.match(riskI12, /TEST Candidate[^。]*已冻结 CHECKOUT_PREPARE Rule Set/u);
  assert.match(riskI12, /8 项静态校验/u);
  assert.match(riskI12, /HTTP 200 \+ REJECTED/u);
  assert.match(riskI12, /VALIDATING[^。]*零检查[^。]*同键恢复/u);
  assert.match(riskI12, /risk-validate-v1-\{releaseId\}-\{expectedVersion\}/u);
  assert.match(riskI12, /BFF[^。]*Idempotency-Key[^。]*精确校验[^。]*不一致[^。]*Risk 零调用/u);
  assert.match(riskI12, /部分检查[^。]*Fail-Closed/u);
  assert.match(riskI12, /不执行 Shadow[^。]*不执行运行期评估/u);
});

test("MVP reliability uses local compensation events and business idempotency", () => {
  assert.match(source, /MVP 不建设通用 Inbox\/Outbox/);
  assert.match(source, /compensation_event/);
  assert.match(source, /XXL-Job 负责兜底扫描\/分片/);
  assert.match(source, /数据库 Claim\/Lease 防止多实例并发/);
  assert.match(source, /消费幂等落在接收方业务表或投影/);
});

test("Vault remains non-core and excludes payment card data", () => {
  assert.match(source, /Vault 是内部安全基础设施而不是第七个核心业务系统/);
  assert.match(source, /CCT Party V1 只有 Email、Phone 使用标量 Vault Token/);
  assert.match(source, /姓名、生日、法律标识和地址由 CCT 结构化保存/);
  assert.match(source, /登录密码不进入 CCT 或 Vault/);
  assert.match(source, /Token、Blind Index\/Key Version、Masked Value、Vault Record Version 与 Metadata Hash.*真实消费者/u);
  assert.match(source, /Vault.*通用.*不创建 Consumer、Customer、Order 或 Network Evidence 业务表/u);
  assert.match(source, /Vault.*私有网络.*内部 HTTP.*X-Caller-Service.*可伪造.*静态 Allowlist/su);
  assert.match(source, /Gateway、BFF、浏览器和外部 Client 不直接访问 Vault/);
  assert.doesNotMatch(source, /Vault 只允许 mTLS\/Workload Identity/u);
  assert.match(source, /PAN、CVV\/CVC、完整磁道数据和 PIN\/PIN Block 不进入 Code-MoR/);
  assert.match(source, /Channel Token、Scheme、BIN、Last4、Issuer Country 和 Channel Fingerprint/);
  assert.match(source, /这些最小卡片元数据仍需分类、脱敏、授权和留存控制/);
  assert.match(source, /Commerce Consumer Email\/Phone 稳定 Value Token.*Commerce→Payment→Risk/su);
  assert.match(source, /不等于自动符合 PCI/);
  assert.match(technologyStackSource, /完整 Body 日志.*不覆盖.*Vault.*普通日志/su);
});

test("Finance stores only authoritative dispute-loss source versions", () => {
  assert.doesNotMatch(financeDatabaseSource, /DisputeOpened\/DisputeLost.*Source Version/u);
  assert.match(financeDatabaseSource, /DisputeLost\/DisputeOutcomeCorrected.*outcome_id\/outcome_version.*security_deposit_use_allowed.*来源版本/u);
  assert.match(financeDatabaseSource, /DisputeOpened.*不进入 Finance Transaction Economics 或 Journal/u);
  assert.match(riskDatabaseSource, /DisputeOpened.*不触发 Finance 记账或冻结/u);
  assert.match(riskDatabaseSource, /只有权威.*DisputeLost.*Finance Journal/u);
});

test("Finance logical authority keeps the simplified Tax and Fiscal shape", () => {
  for (const retiredTable of [
    "TAX_RULE_VERSION",
    "TAX_RULE_APPROVAL",
    "TAX_RETURN_VER",
    "TAX_RETURN_APPROVAL",
  ]) {
    assert.doesNotMatch(financeDatabaseSource, new RegExp(retiredTable, "u"));
  }

  assert.match(financeDatabaseSource, /\| `tax_rule` \| 一行一个可审批、可生效的精确税务规则版本/u);
  assert.match(financeDatabaseSource, /\| `tax_return` \| 一行一个可审批、可提交和可更正的精确税务申报版本/u);
  assert.match(financeDatabaseSource, /Tax Rule 与 Tax Return 不建独立 Approval 表/u);
  assert.match(financeDatabaseSource, /Tax Registration、Rule Version、Rate、Treatment 和 Exchange Rate 的历史版本不可覆盖/u);
  assert.match(financeDatabaseSource, /FILED Version 及其 Line\/Approval 不可覆盖/u);
  assert.doesNotMatch(financeDatabaseSource, /Tax Rule Approval (?:表|Root)|Tax Return 只有一个稳定 Root/u);
});

test("historical Finance designs preserve their superseded 98-table stage", () => {
  assert.match(financeUnifiedAccountDesignSource, /当前 Finance 为 98 表/u);
  assert.match(financeUnifiedAccountDesignSource, /RESERVED.*CLIENT_RESTRICTED_FUNDS.*历史.*保证金 Account.*替代/su);
  assert.match(financeUnifiedAccountDesignSource, /entity_type.*entity_id.*currency.*ledger_book_id.*推导/su);
  assert.doesNotMatch(financeUnifiedAccountDesignSource, /LOCAL_PARTNER[^\n]*PENDING|PARTNER_PAYABLE_PENDING/u);
  assert.match(currentImplementationSource, /\| Finance \| 84 \| 1,109 \| 145 \| 155 \| 160 \| 22 \| 6 \|/u);
  assert.match(
    documentGovernanceSource,
    /Spec 冻结目标和契约，Plan 记录可执行步骤，实施记录保存实际证据[\s\S]*被取代时增加状态与窄范围后继链接，不重写历史/u,
  );
});

test("Platform never turns Client silence into a dispute loss", () => {
  assert.doesNotMatch(platformDatabaseSource, /Client 未响应后的认损由 Risk 决定/u);
  assert.match(platformDatabaseSource, /Client 未响应.*EVIDENCE_REQUIRED.*不得自动认损.*显式授权.*Channel.*LOST.*Risk.*DisputeLost/su);
});

test("Superpowers skills require explicit authorization from the current user", () => {
  for (const workflowSource of [rootAgentsSource, developmentConventionsSource]) {
    assert.match(
      workflowSource,
      /默认不得调用任何 Superpowers skill/u,
    );
    assert.match(
      workflowSource,
      /当前用户[\s\S]{0,80}当前请求[\s\S]{0,80}明确指定/u,
    );
    assert.match(
      workflowSource,
      /REQUIRED SUB-SKILL[\s\S]{0,160}不得跨请求沿用/u,
    );
    assert.doesNotMatch(workflowSource, /设计阶段默认使用|代码阶段按真实触发使用/u);
  }
  assert.match(
    superpowersWorkflowRuleSource,
    /Superpowers 显式授权规则[\s\S]{0,400}默认不得调用任何 Superpowers skill/u,
  );
  assert.match(
    superpowersWorkflowRuleSource,
    /任务类型[\s\S]{0,120}REQUIRED SUB-SKILL[\s\S]{0,160}不得跨请求沿用/u,
  );
  assert.match(
    rootAgentsSource,
    /docs\/superpowers\/\*\*[\s\S]{0,120}不构成 skill 使用授权/u,
  );
  assert.match(
    documentGovernanceSource,
    /Agent\/Superpowers 工作流[\s\S]{0,160}AGENTS\.md[\s\S]{0,160}唯一现行权威/u,
  );
  assert.match(superpowersServicePolicyDecisionSource, /Decision status: Confirmed/u);
  assert.match(
    superpowersServicePolicyDecisionSource,
    /Last amended: 2026-09-08[\s\S]{0,800}D1\. Superpowers 仅按当前用户的明确指定使用/u,
  );
  assert.doesNotMatch(
    rootAgentsSource,
    /设计阶段默认使用|代码阶段按真实触发使用|用户请求 Superpowers-assisted planning/u,
  );
});

test("Service interface policy requires interface plus Impl without a single-implementation exception", () => {
  for (const policySource of [
    developmentConventionsSource,
    javaProjectStructureSource,
    rootAgentsSource,
    javaServiceBoundariesRuleSource,
  ]) {
    assert.match(policySource, /业务 Service[\s\S]{0,80}interface \+ \*Impl/u);
    assert.match(policySource, /Domain Service[\s\S]{0,80}interface \+ \*Impl/u);
    assert.match(policySource, /没有业务职责的空 Service/u);
    assert.match(policySource, /Processor、Controller[\s\S]{0,40}具体类/u);
    assert.match(policySource, /Repository[\s\S]{0,40}interface \+ \*RepositoryImpl/u);
    assert.doesNotMatch(
      policySource,
      /(?:只有一个实现|单实现)[\s\S]{0,80}(?:可以|允许|可直接)[\s\S]{0,40}(?:直接使用具体类|省略接口)/u,
    );
  }
  for (const boundarySource of [
    superpowersServicePolicyDecisionSource,
    developmentConventionsSource,
    javaProjectStructureSource,
  ]) {
    assert.match(
      boundarySource,
      /DataObject[\s\S]{0,160}(?:Owner|所属业务能力)[\s\S]{0,80}JAR/u,
    );
    assert.match(
      boundarySource,
      /DataObject[\s\S]{0,280}(?:跨 Maven 模块|其他 Maven 模块|其他模块)[\s\S]{0,120}(?:不得|不能)/u,
    );
  }
  assert.match(currentImplementationSource, /\| JAVA-02 \|[\s\S]{0,240}尚未完成全量形态审计与迁移/u);
});

test("Java tests are organized by executable entry and independent risk scenarios", () => {
  for (const policySource of [
    unitTestGuideSource,
    developmentConventionsSource,
    rootAgentsSource,
    javaTestingRuleSource,
  ]) {
    assert.match(policySource, /HTTP\s*\/\s*(?:API|`\*-api`)/u);
    assert.match(policySource, /MQ[\s\S]{0,80}(?:Listener|Event Handler)/u);
    assert.match(policySource, /Job Handler/u);
    assert.match(policySource, /Compensation Event Handler/u);
    assert.match(policySource, /(?:公开静态|public static)[\s\S]{0,40}(?:纯 Java|pure-Java)/iu);
    assert.match(policySource, /(?:每个 Service 方法|one test per Service method)/iu);
  }
  assert.match(
    unitTestGuideSource,
    /同一入口下的一组独立测试用例[\s\S]{0,120}不得因为新增了一个 Service 方法/u,
  );
  assert.match(
    unitTestGuideSource,
    /不同场景原则上拆成可独立运行、可独立诊断的测试方法/u,
  );
  assert.match(
    javaTestingRuleSource,
    /不把多种路径堆进一个测试[\s\S]{0,40}不能只测 happy path/u,
  );
});

test("business module collaboration follows the Payment-based Owner Processor boundary", () => {
  assert.match(businessModuleCollaborationDecisionSource, /Decision status: Confirmed/u);
  assert.match(businessModuleCollaborationDecisionSource, /2026-09-16-payment-based-coding-conventions\.md/u);
  assert.match(
    javaProjectStructureSource,
    /业务 Owner 模块的 `service\/processor` 编排/u,
  );
  assert.match(
    javaProjectStructureSource,
    /Owner 公开能力建立单向依赖[\s\S]{0,100}无环/u,
  );
  assert.match(
    javaProjectStructureSource,
    /无稳定 Owner 或存在依赖环时，使用 Web Processor/u,
  );
  assert.match(
    developmentConventionsSource,
    /公开的 Service 或 Processor[\s\S]{0,180}不得访问对方 `\*Impl`/u,
  );
  assert.match(
    javaBusinessModuleBoundariesRuleSource,
    /DataObject[\s\S]{0,160}Owner[\s\S]{0,80}JAR/u,
  );
});

test("shared writing conventions retain Payment responsibilities without promoting temporary exceptions", () => {
  const decision = readFileSync(
    new URL("../../agent-docs/decisions/2026-09-16-payment-based-coding-conventions.md", import.meta.url),
    "utf8",
  );
  const paymentGuide = readFileSync(
    new URL("../../payment-orchestration-service/docs/development/processor-conventions.md", import.meta.url),
    "utf8",
  );
  const writeApiExample = readFileSync(
    new URL("../../agent-docs/examples/WRITE_API_EXAMPLE.md", import.meta.url),
    "utf8",
  );
  const paymentInstructions = readFileSync(
    new URL("../../payment-orchestration-service/AGENTS.md", import.meta.url),
    "utf8",
  );
  assert.match(decision, /Decision status: Confirmed/u);
  assert.match(decision, /Mock \+ TODO[\s\S]{0,80}不推广为长期编码规则/u);
  for (const policy of [developmentConventionsSource, javaProjectStructureSource, writeApiExample]) {
    assert.match(policy, /2026-09-16-payment-based-coding-conventions\.md/u);
    assert.match(policy, /RequestBody[\s\S]{0,160}ResponseBody[\s\S]{0,100}class/u);
    assert.match(policy, /Processor[\s\S]{0,80}(?:可查询|可以直接查询|可以查询)[\s\S]{0,50}Repository/u);
    assert.match(policy, /Service[\s\S]{0,100}不调用其他 Service 或 Processor|Service 之间的调用组装放在 Processor/u);
    assert.match(policy, /Context[\s\S]{0,140}DataObject/u);
    assert.match(policy, /Context[\s\S]{0,80}(?:不进入|绝不进入)(?: Repository\/Mapper|持久化层)/u);
    assert.match(policy, /AbstractLockProcessor/u);
    assert.match(policy, /SQL[\s\S]{0,120}(?:行数|成功后)[\s\S]{0,100}(?:同步|失败)/u);
  }
  assert.match(developmentConventionsSource, /Processor 入口统一拒绝调用方已有活动数据库事务/u);
  assert.match(developmentConventionsSource, /多个 Service 必须一起保存时，Processor 开启一个事务/u);
  assert.match(developmentConventionsSource, /超过三个业务参数时封装具名 Context/u);
  assert.match(developmentConventionsSource, /公共命令幂等组件[\s\S]{0,90}不因为公共包存在而强制接入/u);
  assert.match(developmentConventionsSource, /## 9\. 配置与 Nacos/u);
  assert.doesNotMatch(javaProjectStructureSource, /Processor 未直接调用 Repository、Mapper|HTTP DTO 按业务能力放入独立 `\*Models`/u);
  for (const entry of [rootAgentsSource, agentReadmeSource, paymentInstructions, paymentGuide]) {
    assert.match(entry, /examples\/WRITE_API_EXAMPLE\.md/u);
  }
  assert.match(rootAgentsSource, /首次改动实现前实际读取/u);
  assert.match(rootAgentsSource, /完成前按示例第 11 节逐项核对/u);
  assert.match(writeApiExample, /## 11\. 交付前逐项核对/u);
  assert.match(writeApiExample, /源码与规范的已知差异/u);
  assert.match(writeApiExample, /View[\s\S]{0,120}写接口及普通内部传递一般不增加 View/u);
  assert.match(paymentGuide, /不再维护第二份完整教程/u);
  assert.doesNotMatch(paymentGuide, /protected PayResponse processWithinLock/u);
});

test("Finance module design projects the shared collaboration policy", () => {
  for (const moduleName of [
    "paytech-finance-api",
    "paytech-finance-accounting",
    "paytech-finance-treasury",
    "paytech-finance-tax",
    "paytech-finance-web",
    "paytech-finance-test",
  ]) {
    assert.match(financeModularCodeDesignSource, new RegExp(moduleName, "u"));
  }
  assert.match(financeModularCodeDesignSource, /默认不建立直接 Maven 依赖/u);
  assert.match(
    financeModularCodeDesignSource,
    /稳定、可复用[\s\S]{0,200}单向依赖[\s\S]{0,240}Owner[\s\S]{0,160}Domain \/ Application/u,
  );
  assert.doesNotMatch(financeModularCodeDesignSource, /三个业务模块不相互依赖/u);
  assert.doesNotMatch(financeModularCodeDesignSource, /保留当前本库外键/u);
  assert.match(agentReadmeSource, /2026-09-04-finance-modular-code-design\.md/u);
  assert.match(currentImplementationSource, /\| JAVA-03 \|/u);
});
