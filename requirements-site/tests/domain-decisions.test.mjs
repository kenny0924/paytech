import test from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";

const workerUrl = new URL("../dist/server/index.js", import.meta.url);
const financeDatabaseUrl = new URL("../../agent-docs/database/FINANCE_DATABASE.md", import.meta.url);
const requirementsSourceUrl = new URL("../content/requirements.ts", import.meta.url);

const taxCalculationHashVector = Object.freeze({
  jcs: '{"domain":"CODE-MOR:FINANCE:TAX_CALCULATION:1","header":{"buyerTaxCountry":"AE","buyerTaxIdBlindIndex":null,"buyerTaxIdBlindIndexKeyVersion":null,"buyerTaxIdMasked":null,"buyerTaxIdVaultToken":null,"buyerTaxValidation":"NOT_REQUIRED","buyerTaxVaultMetadataHash":null,"buyerTaxVaultRecordVersion":null,"buyerType":"B2C","clientId":"800000000000000001","clientOrderId":null,"currency":"USD","exchangeRateId":"600000000000000001","operatingEntityId":"700000000000000001","orderId":"900000000000000001","reportingCollectedTax":"18.365","reportingCurrency":"AED","reportingReverseChargeTax":"0","requestHash":"1111111111111111111111111111111111111111111111111111111111111111","sourceRequestId":"tax-request-001","sourceSystem":"COMMERCE","taxSnapshotId":"910000000000000001","taxSnapshotNo":"TAX-20260823-000001","totalCollectedTax":"5","totalReverseChargeTax":"0"},"taxEvidence":[{"countryCode":"AE","evidenceNo":1,"evidenceType":"BILLING_COUNTRY","evidenceValueHash":"2222222222222222222222222222222222222222222222222222222222222222","observedTime":"2026-08-23T08:09:10.123456Z","sourceRefId":"billing-address-1","sourceSystem":"COMMERCE","taxEvidenceId":"920000000000000001","taxSnapshotId":"910000000000000001"},{"countryCode":"AE","evidenceNo":2,"evidenceType":"IP_COUNTRY","evidenceValueHash":"3333333333333333333333333333333333333333333333333333333333333333","observedTime":"2026-08-23T08:09:11.000000Z","sourceRefId":null,"sourceSystem":"COMMERCE","taxEvidenceId":"920000000000000002","taxSnapshotId":"910000000000000001"}],"taxLines":[{"collectedTaxAmount":"5","currency":"USD","fiscalScopeHash":"4444444444444444444444444444444444444444444444444444444444444444","jurisdictionId":"940000000000000001","liabilityMode":"SELLER","liableOperatingEntityId":"700000000000000001","liablePartyType":"OPERATING_ENTITY","lineKey":"ITEM-01|VAT|AE|950000000000000001|960000000000000001|970000000000000001|980000000000000001|SELLER","lineNo":1,"registrationId":"950000000000000001","reportingCollectedTax":"18.365","reportingCurrency":"AED","reportingReverseChargeTax":"0","reverseChargeTaxAmount":"0","sourceLineRef":"item-01","sourceOrderItemId":"900000000000000011","taxBasis":"100","taxLineId":"930000000000000001","taxRate":"0.05","taxRateId":"970000000000000001","taxRuleVersionId":"960000000000000001","taxSnapshotId":"910000000000000001","taxType":"VAT","treatmentId":"980000000000000001"},{"collectedTaxAmount":"0","currency":"USD","fiscalScopeHash":"5555555555555555555555555555555555555555555555555555555555555555","jurisdictionId":"940000000000000001","liabilityMode":"SELLER","liableOperatingEntityId":"700000000000000001","liablePartyType":"OPERATING_ENTITY","lineKey":"ITEM-02|VAT|AE|950000000000000001|960000000000000002|970000000000000002|980000000000000002|SELLER","lineNo":2,"registrationId":"950000000000000001","reportingCollectedTax":"0","reportingCurrency":"AED","reportingReverseChargeTax":"0","reverseChargeTaxAmount":"0","sourceLineRef":"item-02","sourceOrderItemId":"900000000000000012","taxBasis":"25","taxLineId":"930000000000000002","taxRate":"0","taxRateId":"970000000000000002","taxRuleVersionId":"960000000000000002","taxSnapshotId":"910000000000000001","taxType":"VAT","treatmentId":"980000000000000002"}]}',
  digest: "c1b0b421b77171b2d987e160b25aec1953ff7f54102b404282ac4cd0bebeb88a",
});

function canonicalizeJcs(value) {
  if (Array.isArray(value)) {
    return `[${value.map(canonicalizeJcs).join(",")}]`;
  }
  if (value && typeof value === "object") {
    return `{${Object.keys(value).sort().map((key) =>
      `${JSON.stringify(key)}:${canonicalizeJcs(value[key])}`,
    ).join(",")}}`;
  }
  return JSON.stringify(value);
}

async function renderPage() {
  const worker = await import(workerUrl.href);
  const response = await worker.default.fetch(
    new Request("http://localhost/"),
    {},
    { waitUntil() {}, passThroughOnException() {} },
  );
  assert.equal(response.status, 200);
  return response.text();
}

function extractSystemBlock(source, systemCode, nextSystemCode) {
  const start = source.indexOf(`code: "${systemCode}"`);
  const end = source.indexOf(`code: "${nextSystemCode}"`, start);
  assert.ok(start >= 0, `${systemCode} system block must exist`);
  assert.ok(end > start, `${systemCode} system block must end before ${nextSystemCode}`);
  return source.slice(start, end);
}

function extractInterfaceLine(systemBlock, interfaceId) {
  const line = systemBlock.split("\n").find((candidate) => candidate.includes(`iuc("${interfaceId}"`));
  assert.ok(line, `${interfaceId} must exist in the Payment system block`);
  return line;
}

function extractStringArray(systemBlock, propertyName) {
  const match = systemBlock.match(new RegExp(`${propertyName}: \\[(.*?)\\]`, "su"));
  assert.ok(match, `${propertyName} must exist in the Payment system block`);
  return [...match[1].matchAll(/"([^"]+)"/g)].map((entry) => entry[1]);
}

test("checkout renders the immutable order context as the MVP sales model", async () => {
  const content = await renderPage();

  assert.match(content, /下单时传入商品与金额/);
  assert.match(content, /不可变订单上下文/);
  assert.match(content, /Order、不可变 Context 与唯一 Checkout Session=PREPARING 在同一本地事务创建/);
  assert.match(content, /Payment Intent 确认可用后才以 CAS 把 Session 置为 OPEN/);
  assert.equal(content.match(/Offering/g)?.length, 1);
  assert.match(content, /第一版不创建 Offering/);
});

test("rendered requirements expose complete V5 input and reference-style Commerce order context", async () => {
  const content = await renderPage();

  assert.match(content, /client_agreement_id[\s\S]*client_agreement_operating_entity_id/);
  assert.match(content, /consumer_terms_document_id[\s\S]*refund_policy_document_id[\s\S]*privacy_notice_document_id/);
  assert.match(content, /public_support_email[\s\S]*public_support_phone/);
  assert.match(content, /CODE-MOR:CCT:COMMERCIAL_CONTEXT:5/);
  assert.match(content, /成功返回完整 V5 Bundle/);
  assert.match(content, /Client Pricing[\s\S]*Partner Share[\s\S]*Platform Service Charge[\s\S]*Plan\/Version\/Scope[\s\S]*逐组件完整计算参数/);
  assert.match(content, /PLATFORM_SIGNATORY[\s\S]*platformServicePartyId/);
  assert.match(content, /Commerce 只冻结 Assignment\/Agreement Association\/命中 Scope[\s\S]*platform_service_party_id[\s\S]*Legal\/Seller 展示值[\s\S]*V5 Hash/);
  assert.match(content, /CCT-I14[\s\S]*term_scope_id[\s\S]*agreement_operating_entity_id[\s\S]*context_as_of_time[\s\S]*历史 Scope 闭包/);
  assert.match(content, /不接收、重算或验证整包 V5 commercial_context_hash/);
  assert.match(content, /FIN-I17[\s\S]*tax_snapshot_id[\s\S]*calculation_hash[\s\S]*source_order_item_id/);
  assert.match(content, /FIN-I22[\s\S]*expected_calculation_hash[\s\S]*Tax Line[\s\S]*Registration/);
  assert.match(content, /CODE-MOR:FINANCE:TAX_CALCULATION:1/);
  assert.match(content, /OrderPreparationContext[\s\S]*不依赖 Commerce 聚合 Hash/);
  assert.match(content, /Commerce 逻辑模型固定为 15 张表/);
  assert.match(content, /生成模型、人工 Schema、Java、物理数据库、验证与部署的当前状态只按 CURRENT_IMPLEMENTATION 台账声明/);
  assert.match(content, /Partner OE[\s\S]*Platform Finance Entity[\s\S]*两条各自平衡的 Journal/);
  assert.doesNotMatch(content, /Commercial Term 头\/组件结构化快照/);
  assert.doesNotMatch(content, /agreement_scope_binding_(?:id|version|hash)/);
  assert.doesNotMatch(content, /consumer_terms_version_id|seller_disclosure_version_id/);
  assert.doesNotMatch(content, /CODE-MOR:CCT:COMMERCIAL_CONTEXT:[234]/);
  assert.doesNotMatch(content, /expected_commercial_context_hash/);
  assert.doesNotMatch(
    content,
    /client_pricing_(?:content|scope)_hash|partner_share_(?:content|scope)_hash|canonical_hash/,
  );
});

test("rendered requirements use real-time CCT readiness without persisted eligibility proof", async () => {
  const content = await renderPage();

  assert.match(content, /client_id、country、currency、industry_code、mcc_code/);
  assert.match(content, /READY \| NOT_READY \| RESTRICTED/);
  assert.match(content, /每次调用实时校验/);
  assert.match(content, /非持久化诊断.*不是交易证明/);
  assert.doesNotMatch(content, /ClientEligibilityChanged/);
  assert.doesNotMatch(
    content,
    /eligibility_(?:id|history_id|status)|evaluation_version|reason_summary_hash|dimension_set_hash|source_set_hash/,
  );
});

test("rendered requirements defer automated retention and legal hold governance beyond MVP", async () => {
  const content = await renderPage();

  assert.match(content, /未来留存与 Legal Hold 治理占位/);
  assert.match(content, /MVP 不发布 RetentionPolicyChanged、LegalHoldPlaced 或 LegalHoldReleased/);
  assert.match(content, /不自动归档、匿名化或销毁/);
});

test("rendered requirements scopes CCT BCR to approved audit records", async () => {
  const content = await renderPage();

  assert.match(content, /BCR 按 Record Type 白名单启用/);
  assert.match(content, /不为所有业务表自动建立变更历史/);
  assert.match(content, /Child current_version 只表示最近审计版本，不是独立 CAS Token/);
});

test("finance renders calculation separately from double-entry posting", async () => {
  const content = await renderPage();

  assert.match(content, /计费与分润计算/);
  assert.match(content, /复式账本与余额/);
  assert.match(content, /预计成本/);
  assert.match(content, /实际成本/);
  assert.match(content, /收入减成本/);
  assert.match(content, /成本差异生成新的 Accounting Instruction/);
});

test("Finance defines and verifies the versioned Tax Calculation Hash contract", async () => {
  const financeDatabase = await readFile(financeDatabaseUrl, "utf8");

  assert.match(financeDatabase, /CODE-MOR:FINANCE:TAX_CALCULATION:1/u);
  assert.match(financeDatabase, /Tax Snapshot 输出 Header[\s\S]*taxEvidence[\s\S]*taxLines/u);
  assert.match(financeDatabase, /evidence_no[\s\S]*line_no[\s\S]*line_key/u);
  assert.match(financeDatabase, /BIGINT[\s\S]*DECIMAL[\s\S]*JSON `null`[\s\S]*六位微秒 UTC/u);
  assert.match(financeDatabase, /FIN-I22[\s\S]*重算[\s\S]*calculation_hash[\s\S]*Fail Closed/u);
  for (const sourceId of [
    "registrationId",
    "jurisdictionId",
    "taxRuleVersionId",
    "taxRateId",
    "treatmentId",
  ]) {
    assert.match(financeDatabase, new RegExp(`\\b${sourceId}\\b`, "u"));
  }
  assert.match(financeDatabase, /对应不可变来源/u);

  const taxCalculatedEvent = financeDatabase
    .split("\n")
    .find((line) => line.startsWith("| `TaxCalculated` |"));
  assert.ok(taxCalculatedEvent, "Finance event catalog must contain TaxCalculated");
  assert.match(taxCalculatedEvent, /`calculation_hash`/u);
  assert.doesNotMatch(taxCalculatedEvent, /Content Hash/u);

  const parsedVector = JSON.parse(taxCalculationHashVector.jcs);
  assert.equal(canonicalizeJcs(parsedVector), taxCalculationHashVector.jcs);
  assert.ok(Object.hasOwn(parsedVector.header, "clientOrderId"));
  assert.ok(!Object.hasOwn(parsedVector.header, "externalOrderId"));
  assert.deepEqual(parsedVector.taxEvidence.map(({ evidenceNo }) => evidenceNo), [1, 2]);
  assert.deepEqual(parsedVector.taxLines.map(({ lineNo, lineKey }) => [lineNo, lineKey]), [
    [1, "ITEM-01|VAT|AE|950000000000000001|960000000000000001|970000000000000001|980000000000000001|SELLER"],
    [2, "ITEM-02|VAT|AE|950000000000000001|960000000000000002|970000000000000002|980000000000000002|SELLER"],
  ]);
  assert.match(taxCalculationHashVector.digest, /^[a-f0-9]{64}$/u);
  assert.equal(
    createHash("sha256").update(taxCalculationHashVector.jcs, "utf8").digest("hex"),
    taxCalculationHashVector.digest,
  );
});

test("system catalog renders six functional systems instead of deployment-level fragments", async () => {
  const content = await renderPage();

  assert.equal(content.match(/class="system-card"/g)?.length, 6);
  for (const system of [
    "Client &amp; Contract System",
    "Commerce System",
    "Payment Orchestration System",
    "Risk &amp; Compliance System",
    "Finance System",
    "Platform Support System",
  ]) {
    assert.match(content, new RegExp(system));
  }
  for (const section of ["独立用例", "接口用例", "MQ 通知", "不负责"]) {
    assert.match(content, new RegExp(section));
  }
  assert.doesNotMatch(content, /14 组业务与支撑系统/);
});

test("rendered Platform catalog routes model status to its authority and keeps Tenant-owned credentials", async () => {
  const content = await renderPage();

  assert.match(content, /Platform Support 当前生成模型统计由 agent-docs\/CURRENT_IMPLEMENTATION\.md 维护/u);
  assert.match(content, /Client API Scope 目录的增量契约/u);
  assert.match(content, /所有外部 HTTP API.*Platform.*公共 Route Catalog.*内部服务调用.*绕过 Gateway/su);
  assert.match(content, /OPERATIONS.*CLIENT.*PARTNER.*CHECKOUT.*CLIENT_API.*CHANNEL_CALLBACK/su);
  assert.match(content, /Tenant.*API Credential.*PHC verifier.*ACTIVE Scope/su);
  assert.match(content, /Integration Readiness.*一致查询快照.*同步派生/su);
  assert.doesNotMatch(content, /管理通用配置生命周期|Technical Configuration|ConfigurationActivated|ClientIntegrationReadinessChanged/u);
});

test("system catalog distinguishes synchronous interfaces from MQ notifications", async () => {
  const content = await renderPage();

  assert.match(content, /同步命令和查询通过系统接口/);
  assert.match(content, /MQ 仅用于内部回调和业务事实通知/);
  assert.match(content, /订阅 MQ/);
  assert.doesNotMatch(content, /ReserveHoldRequested|ReserveReleaseRequested/);
});

test("system boundaries prohibit cross-database access and keep entry apps stateless", async () => {
  const content = await renderPage();

  assert.match(content, /禁止跨库查询和写入/);
  assert.match(content, /入口应用不拥有核心业务数据/);
});

test("payment separates authorization Void from Commerce-approved refunds", async () => {
  const content = await renderPage();

  assert.match(content, /Void.*原 Authorization.*不要求.*Refund Request/su);
  assert.match(content, /渠道退款.*必须引用 Commerce refundRequestId/su);
});

test("Cancel, Void, and Reversal have strict non-overlapping semantics", async () => {
  const content = await renderPage();

  assert.match(content, /COM-I05.*取消未支付订单/su);
  assert.match(content, /COM-I08.*撤销支付/su);
  assert.match(content, /PAY-I13.*解析并执行支付撤销/su);
  assert.match(content, /授权成功但未 Capture.*Void/su);
  assert.match(content, /全额 Capture.*标准 Refund/su);
  assert.match(content, /Cancel 不会静默升级为 Reversal/u);
  assert.match(content, /PARTIAL_CAPTURE_REVERSAL_NOT_SUPPORTED/u);
});

test("Payment owns refund capacity and publishes only authoritative refund terminals", async () => {
  const source = await readFile(requirementsSourceUrl, "utf8");
  const paymentBlock = extractSystemBlock(source, "PAYMENT", "RISK_COMPLIANCE");
  const pay06 = paymentBlock.split("\n").find((line) => line.includes('uc("PAY-06"'));
  const payI04 = extractInterfaceLine(paymentBlock, "PAY-I04");
  const payI11 = extractInterfaceLine(paymentBlock, "PAY-I11");
  const payI12 = extractInterfaceLine(paymentBlock, "PAY-I12");
  const payI13 = extractInterfaceLine(paymentBlock, "PAY-I13");

  assert.match(pay06, /不发布 PaymentAuthorized MQ 事件/u);
  assert.match(payI04, /commerce_refund_request_id.*refund_amount/u);
  assert.match(payI04, /Payment Refund Position.*CAS.*reserved_amount.*Channel Refund/u);
  assert.match(payI04, /Intent.*成功 Attempt.*Payment Refund Position.*不复制.*Binding Hash/u);
  assert.match(payI04, /UNKNOWN.*Payment 本地.*不对外发布/u);
  assert.match(payI04, /RefundSucceeded.*Commerce.*Finance.*RefundFailed.*RefundCancelled.*只通知 Commerce/u);
  assert.doesNotMatch(payI04, /refund_funding_reservation_id|Funding Reservation/u);
  assert.match(payI11, /实时静态配置覆盖诊断.*READY \| NOT_READY \| RESTRICTED、checked_at、reason_code.*候选解释/u);
  assert.match(payI11, /不持久化 Fact\/Version\/Scope Hash.*不把瞬时健康当静态覆盖.*不发布 RoutingCoverageChanged/u);
  assert.match(payI12, /PENDING（reason_code=PAYMENT_IN_PROGRESS）/u);
  assert.match(payI13, /只供 Commerce 内部调用.*调用方不提交 payment_attempt_id 或 target_channel_txn_ref_id/u);
  assert.match(payI13, /锁定 Intent、Attempt、Action 与 Channel Transaction Ref.*Expected Version\/CAS/u);
  assert.match(payI13, /REFUND_REQUIRED.*captured_amount.*PARTIAL_CAPTURE_REVERSAL_NOT_SUPPORTED/u);
  assert.deepEqual(extractStringArray(paymentBlock, "subscribes"), ["OperatingEntityChanged"]);
  assert.deepEqual(extractStringArray(paymentBlock, "publishes"), [
    "PaymentAttemptStarted", "PaymentAttemptFailed", "PaymentAttemptUnknown", "PaymentSucceeded", "PaymentIntentTerminated",
    "DuplicatePaymentDetected", "DuplicatePaymentRemediated", "RefundSucceeded", "RefundFailed", "RefundCancelled",
    "DisputeNoticeReceived", "DisputeSubmissionStatusChanged", "ChannelDisputeOutcomeReceived", "RouteSelected", "ChannelHealthChanged",
    "MidActivated", "MidSuspended", "MidClosed",
  ]);
});

test("refund lifecycle uses independent Commerce and Payment positions while Finance posts success only", async () => {
  const content = await renderPage();

  assert.match(content, /仅保留 t_order_refund_position 与 t_refund_request/);
  assert.match(content, /client_refund_id[\s\S]*refund_amount[\s\S]*channel_refund_id/);
  assert.match(content, /PENDING、PROCESSING、SUCCEEDED、FAILED、CANCELLED/);
  assert.match(content, /Payment.*Refund Position.*CAS.*Channel Refund/);
  assert.match(content, /UNKNOWN.*只保留在 Payment 本地.*Commerce.*PROCESSING/);
  assert.match(content, /权威成功立即消费 Commerce 本地额度，权威失败或取消立即释放/);
  assert.match(content, /Finance 只消费 RefundSucceeded.*记账/);
  assert.doesNotMatch(content, /refund_funding_reservation_id|FIN-I21|SOURCE_COVER|REFUND_CAP|RefundUnknown/);
});

test("entry applications remain outside the six core systems", async () => {
  const content = await renderPage();

  assert.equal(content.match(/class="entry-app-card(?:\s[^"]*)?"/g)?.length, 8);
  assert.equal(content.match(/class="system-card"/g)?.length, 6);
  assert.match(content, /API Gateway/);
  assert.match(content, /Client API/);
  assert.doesNotMatch(content, /Client API &amp; API Gateway/);
  assert.match(content, /不拥有业务数据，也不编排跨系统业务流程/);
  assert.match(content, /Client Console/);
  assert.match(content, /Partner Console/);
  assert.match(content, /Operations Console/);
  assert.match(content, /Channel Callback Endpoint/);
});

test("Partner Console is read-only for transactions and can request only share payouts", async () => {
  const content = await renderPage();

  assert.match(content, /提交主体信息与开户材料/);
  assert.match(content, /查询关联主体、MID 和脱敏交易明细/);
  assert.match(content, /提交 Partner 出款申请/);
  assert.match(content, /仅限已形成的 Partner 可用分润余额/);
  assert.match(content, /禁止操作订单、支付、退款、路由和账本/);
  assert.match(content, /平台自有 Operating Entity 不创建虚假 Partner 或 Client 来源关系/);
});

test("rendered consoles expose unified CUSTOMER authentication without extending it to Consumers", async () => {
  const content = await renderPage();

  assert.match(content, /Operations.*Email\/Password only.*admin.*唯一非 Email Bootstrap/su);
  assert.match(content, /CUSTOMER.*Email\/Password.*Google OIDC/su);
  assert.match(content, /一个 CUSTOMER Account.*CLIENT.*PARTNER Tenant/su);
  assert.match(content, /一个 Access Token.*零个或一个 Tenant Member/su);
  assert.match(content, /业务 Email OTP.*Authentication Challenge.*独立/su);
  assert.match(content, /Consumer 认证.*明确排除/u);
  assert.match(content, /Client Console.*Email 注册.*Google Login.*Invitation.*Tenant 选择/su);
  assert.match(content, /Partner Console.*Email 注册.*Google Login.*Invitation.*Tenant 选择/su);
});

test("Operating Entity sources and the two-stage routing boundary are explicit", async () => {
  const content = await renderPage();

  assert.match(content, /PLATFORM、CLIENT、PARTNER/);
  assert.match(content, /Organization Client 可申请使用自身或经平台审核关联的 Operating Entity/);
  assert.match(content, /当前不结构化保存多公司多层持股路径/);
  assert.match(content, /Client 和 Local Partner 均不可在交易请求中指定 Operating Entity 或 MID/);
  assert.match(content, /CCT.*冻结唯一 Operating Entity/);
  assert.match(content, /Payment.*同一 Operating Entity.*选择 MID/);
  assert.match(content, /重试.*同一 Operating Entity.*更换 MID/);
});

test("checkout resolves the single Primary Classification server-side without Client routing selection", async () => {
  const content = await renderPage();

  assert.match(content, /Client Business Profile 可以维护多组已批准的 industry_code \+ mcc_code 组合/);
  assert.match(content, /任一交易时点最多有一组有效 Primary Classification/);
  assert.match(content, /CCT-I13.*服务端解析该时点唯一有效 Primary Classification/su);
  assert.match(content, /Commerce 不提交、选择或回退分类/);
  assert.match(content, /不接收 locale、industryCode\/mccCode\/classificationId/);
  assert.match(content, /Client 不能选择 Operating Entity、Channel 或 MID/);
});

test("CCT uses the lean automatic assignment proof without policy or assignment versions", async () => {
  const content = await renderPage();

  assert.match(content, /Source Relation、Affiliation、Assignment Rule 和 Assignment 四层/);
  assert.match(content, /返回.*assignment_id、rule_id、affiliation_id、entity_source_relation_id/);
  assert.match(content, /不再返回 Assignment Version、Assignment Scope Hash、Affiliation Revision 或 Source Relation Revision/);
  assert.match(content, /规则目标主体必须同时具备有效 Affiliation 和唯一有效 Source Relation/);
  assert.match(content, /CCT 状态变更 Processor 在同一本地事务内同步重算/);
  assert.match(content, /查询、定时任务和人工操作不生成分配/);
  assert.match(content, /Payment.*冻结 assignment_id/);
  assert.doesNotMatch(content, /Assignment Version\/Scope Hash/);
});

test("the rendered model closes onboarding, shared maturity, manual payout, and recovery", async () => {
  const content = await renderPage();

  assert.match(content, /Beneficiary Account/);
  assert.match(content, /Manual Payout/);
  assert.match(content, /CONTRACT MATURITY/);
  assert.match(content, /Agreement-OE.*T\+N.*Cutoff.*节假日日历/);
  assert.match(content, /Channel 到账、银行对账和保证金门禁不改变成熟时间/);
  assert.doesNotMatch(content, /Availability Policy|版本化策略决定成熟/);
  assert.doesNotMatch(content, /满足 Channel\/银行匹配.*后才进入 Available/);
  assert.doesNotMatch(content, /Pending、Available、Reserved、Locked/);
  assert.match(content, /Pending、Available、Locked、保证金和 Negative/);
  assert.match(content, /CLIENT_SECURITY_DEPOSIT\/NONE/);
  assert.match(content, /CLIENT_RECOVERY_RECEIVABLE\/NONE/);
  assert.match(content, /保证金.*负债类 Account.*追偿应收.*资产类 Account/);
  assert.match(content, /MIN_SECURITY_DEPOSIT.*最终最低保证金金额/);
  assert.match(content, /Payout create.*final Finance approval.*Execution create.*external submit/isu);
  assert.match(content, /assessment_id.*evaluation_id.*reason_codes.*matched_controls.*control_set_hash/su);
  assert.match(content, /CLIENT_RECOVERY_RECEIVABLE\/NONE.*(?:为 0|余额必须为 0).*CLIENT_SECURITY_DEPOSIT\/NONE.*required_amount/su);
  assert.match(content, /交易资格/);
  assert.match(content, /Locked for Payout/);
  assert.match(content, /RefundSucceeded.*不动用 Client Security Deposit/);
  assert.match(content, /DisputeLost.*明确允许.*Client Security Deposit.*Recovery Receivable/);
  assert.match(content, /未来交易抵扣.*人工收款.*Journal/);
  assert.doesNotMatch(content, /CLIENT_RESTRICTED_FUNDS|restricted_fund|reserve_case|recovery_case|recovery_application/);
  assert.doesNotMatch(content, /FIN-I06|ReserveHeld|ReserveReleased|RecoveryApplied/);
  assert.doesNotMatch(content, /退款与拒付依次冲减余额和准备金|未被活动退款并发锁覆盖/);
  assert.doesNotMatch(content, /Settlement Profile|Settlement Run|Locked for Settlement|CLIENT_BALANCE_MATURED|client_available_time|active_settlement_cash/);
  assert.match(content, /消费者条款与卖方披露/);
  assert.match(content, /税费与收据\/发票/);
});

test("the rendered dispute contract freezes outcome provenance and liability authority", async () => {
  const content = await renderPage();

  assert.match(content, /CHANNEL_ACTION[\s\S]*Version 1[\s\S]*WON\s*\/\s*LOST/u);
  assert.match(content, /CLIENT_DISPOSITION[\s\S]*OPERATIONS_DISPOSITION[\s\S]*Version 1[\s\S]*LOST/u);
  assert.match(content, /Version 2\+[\s\S]*OPERATIONS_CORRECTION/u);
  assert.match(content, /trigger_id\s*=\s*decision_source_id/u);
  assert.match(content, /decision_evaluation_id\s*\+\s*action_effect_id[\s\S]*decided_by/u);
  assert.match(content, /DisputeLost[\s\S]*DisputeOutcomeCorrected[\s\S]*security_deposit_use_allowed/u);
  assert.match(content, /outcome\s*=\s*LOST[\s\S]*principal_bearer\s*=\s*CLIENT[\s\S]*security_deposit_use_allowed\s*=\s*1/u);
  assert.match(content, /Finance[^。；]*不得[^。；]*(?:原因|reason|来源|source|规则)[^。；]*推导/u);
});

test("top-level tabs separate architecture from global and system use cases", async () => {
  const content = await renderPage();

  assert.match(content, /role="tablist"/);
  assert.match(content, /data-content-tab="architecture"/);
  assert.match(content, /data-content-tab="use-cases"/);
  assert.match(content, /data-view-panel="architecture"/);
  assert.match(content, /data-view-panel="use-cases"/);
  assert.match(content, /U01[\s\S]*全局用例/);
  assert.match(content, /U02[\s\S]*系统用例/);
});

test("every global use case can reveal its system call sequence", async () => {
  const content = await renderPage();

  assert.equal(content.match(/aria-label="查看 E2E-\d{2} 时序图"/g)?.length, 16);
  assert.match(content, /点击任意全局用例查看系统调用时序图/);
});
