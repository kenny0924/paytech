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
const agentReadmeSource = readFileSync(
  new URL("../../agent-docs/README.md", import.meta.url),
  "utf8",
);
const projectContextSource = readFileSync(
  new URL("../../agent-docs/PROJECT_CONTEXT.md", import.meta.url),
  "utf8",
);
const rootAgentsSource = readFileSync(
  new URL("../../AGENTS.md", import.meta.url),
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
  "order_payment_projection",
  "order_refund_position",
  "order_context",
  "refund_request",
].sort();

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
    "PROVIDER_ENDPOINT",
    "CONSUMER_SERVICE",
  ]) {
    assert.match(source, new RegExp(`code:\\s*["']${code}["']`));
  }
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
  assert.match(bankRecon, /provider_batch_id.*PROVIDER_SETTLEMENT.*payout_execution_id.*PAYOUT.*tax_payment_id.*TAX/u);
  assert.match(bankRecon, /外部 Debit.*容量不足.*不得拒绝.*新余额/u);
  assert.doesNotMatch(financeBlock, /Settlement Profile|Settlement Run|Settlement Payment|Settlement Attempt|active_settlement_cash|client_available_time|CLIENT_BALANCE_MATURED|BalanceAvailable|Provider Cash Item/u);

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
  assert.match(paymentCreate, /PaymentSucceeded.*payment_intent_id\/payment_attempt_id.*provider_id\/provider_account_id\/mid_id.*provider_transaction_ref_id\/reference_type\/external_transaction_id/u);
  assert.match(requirementsSiteSource, /assessment_id.*evaluation_id.*decision.*reason_codes.*matched_controls.*required_amount.*required_currency.*control_set_hash/u);
  assert.match(architectureDiagramSource, /Provider 清算.*Manual Payout/u);
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
    "### 5.4 CCT-I13 原子 Bundle",
  );

  assert.match(cctI12, /public_tax_registration_number.*可空/u);
  assert.match(cctI13, /public_tax_registration_number.*可空.*JSON null.*不得省略/u);
  assert.match(consumerLegalContract, /public_tax_registration_number.*可空/u);
  assert.match(canonicalBundle, /public_tax_registration_number.*固定保留.*JSON `null`.*不得省略/u);
});

test("CCT returns the complete V5 bundle while Commerce persists one explicit immutable Order Context", () => {
  const cct09 = source.match(/uc\("CCT-09"[^\n]+/u)?.[0] ?? "";
  const cctI05 = source.match(/iuc\("CCT-I05"[^\n]+/u)?.[0] ?? "";
  const cctI06 = source.match(/iuc\("CCT-I06"[^\n]+/u)?.[0] ?? "";
  const cctI13 = source.match(/iuc\("CCT-I13"[^\n]+/u)?.[0] ?? "";
  const cctI14 = source.match(/iuc\("CCT-I14"[^\n]+/u)?.[0] ?? "";
  const commerce03 = source.match(/uc\("COM-03"[^\n]+/u)?.[0] ?? "";
  const commerceI04 = source.match(/iuc\("COM-I04"[^\n]+/u)?.[0] ?? "";

  assert.match(cct09, /Client Pricing.*Partner Share.*Platform Service Charge.*Commercial Term 四层模型.*完整组件/u);
  assert.match(cctI05, /Client Pricing Plan\/Version\/Scope.*结构字段.*完整计算参数.*不返回中间 Hash/u);
  assert.match(cctI06, /Partner Share Plan\/Version\/Scope.*完整计算参数.*ACTUAL_CLIENT_FEE/u);
  assert.match(cctI13, /完整 V5 Bundle.*Profile\/Classification Proof.*唯一 ACTIVE Assignment/u);
  assert.match(cctI13, /Partner Share.*Platform Service Charge.*PLATFORM_SIGNATORY.*platformServicePartyId/u);
  assert.match(cctI13, /三份 Consumer Legal.*完整 Seller Disclosure/u);
  assert.match(cctI13, /Plan\/Version\/Scope 稳定 ID.*Version\/Scope 结构字段.*逐组件完整计算参数/u);
  assert.match(cctI13, /CODE-MOR:CCT:COMMERCIAL_CONTEXT:5/u);
  assert.match(cctI14, /term_scope_id.*agreement_operating_entity_id.*context_as_of_time.*历史 Scope 闭包/u);
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

test("database guidance keeps stable Agreement associations separate from commercial term versions", () => {
  for (const [name, guidance] of [
    ["database overview", databaseOverviewSource],
    ["database design guide", databaseDesignGuideSource],
  ]) {
    assert.match(
      guidance,
      /Agreement\/Association 稳定 ID.*Pricing\/Share\/Platform Service Charge Version\/Scope/su,
      `${name} must distinguish stable Agreement associations from commercial versions`,
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

test("Commerce authority freezes the exact 14-table audit and document simplification baseline", () => {
  const tableHeadings = extractCommerceTableHeadings(commerceDatabaseSource);

  assert.deepEqual(tableHeadings, expectedCommerceTables);
  assert.equal(tableHeadings.length, 14);
  for (const tableName of removedCommerceTables) {
    assert.ok(
      !tableHeadings.includes(tableName),
      `removed Commerce table must not remain in the current catalog: ${tableName}`,
    );
  }

  assert.match(commercePrivacySimplificationDesignSource, /固化 2026-08-24 已确认/u);
  assert.match(commerceDocumentAuditCancelDesignSource, /14 张表.*223 个字段.*20 个唯一索引.*11 个普通索引.*14 条本库关系.*4 个 Diagram Group/su);
  assert.match(
    projectContextSource,
    /14 (?:张表|tables).*223 (?:个)?字段|14 tables, 223 fields/u,
  );
  assert.match(projectContextSource, /20 (?:个)?(?:唯一索引|unique indexes).*11 (?:个)?(?:访问索引|access indexes)/u);
  assert.match(projectContextSource, /14 (?:条)?(?:本库关系|local relations).*4 (?:个)?(?:Diagram Groups?|diagram groups?)/u);
  assert.doesNotMatch(projectContextSource, /Commerce.*32 张/u);
});

test("shared entry documents expose the current Commerce, Payment, Finance, and Risk snapshots", () => {
  const sharedEntrySources = [
    rootAgentsSource,
    rootReadmeSource,
    agentReadmeSource,
    projectContextSource,
    databaseDesignGuideSource,
    databaseOverviewSource,
  ];

  for (const entrySource of sharedEntrySources) {
    assert.match(entrySource, /14 (?:张)?表(?:、|,| and | \/ )223 (?:个)?字段|14 tables(?: and|,| \/) 223 fields/u);
    assert.match(entrySource, /29 (?:张)?表(?:、|,| and | \/ )373 (?:个)?字段|29 tables(?: and|,| \/) 373 fields|29 \/ 373/u);
    assert.match(entrySource, /84 (?:张)?表(?:、|,| and | \/ )1,100 (?:个)?字段|84 tables(?: and|,| \/) 1,100 fields|84 \/ 1,100/u);
    assert.match(entrySource, /71 (?:张)?表(?:、|,| and | \/ )865 (?:个)?字段|71 tables(?: and|,| \/) 865 fields|71 \/ 865/u);
    assert.match(entrySource, /2026-08-28-risk-limit-balanced-simplification-design\.md/u);
    assert.match(entrySource, /2026-08-28-risk-limit-balanced-simplification-implementation-plan\.md/u);
    assert.match(entrySource, /2026-08-27-risk-decision-engine-balanced-simplification-implementation-plan\.md/u);
    assert.match(entrySource, /2026-08-28-risk-dispute-balanced-optimization-design\.md/u);
    assert.match(entrySource, /2026-08-28-risk-dispute-balanced-optimization-implementation-plan\.md/u);
  }
  for (const entrySource of [rootAgentsSource, rootReadmeSource, agentReadmeSource, projectContextSource, databaseDesignGuideSource, databaseOverviewSource]) {
    assert.match(entrySource, /Finance[\s\S]*145 (?:个)?(?:唯一索引|unique indexes)[\s\S]*155 (?:个)?(?:访问索引|access indexes)[\s\S]*160 (?:条)?(?:本库关系|local relations)[\s\S]*22 (?:条 )?(?:1:1 关系|one-to-one relations)[\s\S]*6 (?:个 )?Diagram Groups?/u);
    assert.match(entrySource, /Risk[\s\S]*106 (?:个)?(?:唯一索引|unique indexes)[\s\S]*74 (?:个)?(?:访问索引|access indexes)[\s\S]*111 (?:条)?(?:本库关系|local relations)[\s\S]*9 (?:条 )?(?:1:1 关系|one-to-one relations)[\s\S]*8 (?:个 )?Diagram Groups?[\s\S]*83 (?:个 )?CHECK/u);
  }
});

test("shared entry documents expose the current Platform public-control-plane authority", () => {
  const sharedEntrySources = [
    rootAgentsSource,
    rootReadmeSource,
    agentReadmeSource,
    projectContextSource,
    databaseDesignGuideSource,
    databaseOverviewSource,
  ];

  for (const entrySource of sharedEntrySources) {
    assert.match(
      entrySource,
      /Platform(?: Support)?[\s\S]*63 (?:张)?表(?:、|,| and | \/ )822 (?:个)?字段|Platform(?: Support)?[\s\S]*63 tables(?: and|,| \/ ) 822 fields|Platform(?: Support)?[\s\S]*63 \/ 822/u,
    );
    assert.match(
      entrySource,
      /2026-09-01-gateway-platform-support-dynamic-operations-configuration-design\.md/u,
    );
    assert.match(
      entrySource,
      /2026-09-01-platform-public-api-control-plane-pdmaner\.md/u,
    );
  }

  for (const entrySource of [databaseDesignGuideSource, databaseOverviewSource]) {
    assert.match(entrySource, /31 (?:条 )?(?:复合外键|复合 FK|composite FKs?)/u);
    assert.match(entrySource, /85 (?:条 )?(?:CHECK|named CHECK)/u);
    assert.match(entrySource, /36 (?:个 )?(?:`\*_hash`|exact `\*_hash`)/u);
  }

  assert.match(applicationArchitectureSource, /所有外部 HTTP API.*Platform.*公共 Route Catalog/su);
  assert.match(applicationArchitectureSource, /内部服务.*绕过 Gateway/su);
  assert.match(
    applicationArchitectureSource,
    /Platform 已实现手工 Schema\/初始化 DML.*PLT-I20 全量 Snapshot Provider.*Gateway 动态消费.*尚未实现.*静态 Operations Catalog/su,
  );
});

test("Project context owns exactly the current public control plane without runtime limiter state", () => {
  assert.match(
    projectContextSource,
    /Platform Support[\s\S]*owns exactly six public-control-plane current-configuration tables[\s\S]*does not own Gateway runtime limiter state, database Snapshot, Release, Ack, Tenant Override, or generic technical configuration/iu,
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

test("active Platform navigation treats the 57-table stage as a predecessor", () => {
  const agentNavigation = agentReadmeSource.slice(0, agentReadmeSource.indexOf("## 规范来源与优先级"));
  const databaseChecklist = extractMarkdownSection(databaseDesignGuideSource, "## 15. 验证清单");

  for (const navigation of [agentNavigation, databaseChecklist]) {
    assert.match(navigation, /Platform(?: Support)?[\s\S]*63 (?:张)?表[、,\s/]*822 (?:个)?字段/u);
    assert.match(navigation, /31 (?:条\s*)?(?:复合外键|复合 FK|composite FKs?)/u);
    assert.match(navigation, /85 (?:条\s*)?(?:CHECK|具名 CHECK|named CHECK)/u);
    assert.match(navigation, /36 (?:个 )?`?\*_hash`?/u);
    assert.match(navigation, /2026-09-01-gateway-platform-support-dynamic-operations-configuration-design\.md/u);
    assert.match(navigation, /2026-09-01-platform-public-api-control-plane-pdmaner\.md/u);
    assert.match(navigation, /57 表[\s\S]{0,600}(?:前序|历史|未受影响)/u);
  }

  assert.doesNotMatch(
    agentNavigation,
    /57 表(?:(?!前序|历史|未受影响)[^。\n])*(?:管理当前 Platform 物理边界|当前 Platform 物理边界)/u,
  );
  assert.doesNotMatch(databaseChecklist, /Platform Support 57 表 PDManer-only 阶段/u);
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
  for (const entrySource of [rootAgentsSource, projectContextSource]) {
    assert.match(
      entrySource,
      /DisputeLost\/DisputeOutcomeCorrected[\s\S]*outcome=LOST[\s\S]*principal_bearer=CLIENT[\s\S]*security_deposit_use_allowed=1/u,
    );
    assert.doesNotMatch(entrySource, /only an explicitly allowed `DisputeLost` may/u);
  }
});

test("Commerce and Payment reserve refunds locally while Finance posts success only", () => {
  assert.match(source, /14 张表、223 个字段、20 个唯一索引、11 个访问索引、14 条本库关系和 4 个 Diagram Group/u);
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
  assert.match(commerceDatabaseSource, /CANCELLED.*Provider Action 提交前.*受控主动取消/u);
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
  assert.match(payI13, /调用方不提交 payment_attempt_id 或 target_provider_txn_ref_id/u);
  assert.match(payI13, /锁定 Intent.*Attempt.*Action.*Provider Transaction Ref.*Expected Version\/CAS/su);
  assert.match(payI13, /REFUND_REQUIRED.*Commerce.*标准全额.*Refund Request/su);
  assert.match(payI13, /PARTIAL_CAPTURE_REVERSAL_NOT_SUPPORTED/u);
  assert.doesNotMatch(payI13, /PAY-I13 VoidAuthorization|PARTIAL_CAPTURE_REVERSAL_UNSUPPORTED/u);
});

test("PAY-I02 keeps the first-provider-submit check fail-closed without persistent Gate facts", () => {
  const payI02 = source.split("\n").find((line) => line.includes('iuc("PAY-I02"')) ?? "";

  assert.match(payI02, /首次 AUTHORIZE\/SALE\/CAPTURE Provider 提交前同步查询 CCT.*Risk/u);
  assert.match(payI02, /检查通过结果不持久化为独立 Gate/u);
  assert.match(payI02, /明确阻断、缺失、超时或技术失败均 Fail-Closed/u);
  assert.match(payI02, /Attempt\/Action FAILED.*failure_stage\/status_reason_code.*状态流转/u);
  assert.match(payI02, /不创建 Provider Interaction/u);
});

test("Payment requirements freeze the generic customer-action, exact refund and duplicate-success contracts", () => {
  const pay06 = source.split("\n").find((line) => line.includes('uc("PAY-06"')) ?? "";
  const pay14 = source.split("\n").find((line) => line.includes('uc("PAY-14"')) ?? "";
  const payI02 = source.split("\n").find((line) => line.includes('iuc("PAY-I02"')) ?? "";
  const payI04 = source.split("\n").find((line) => line.includes('iuc("PAY-I04"')) ?? "";

  assert.match(payI02, /Payment Method Catalog.*payment_method_code.*Attempt/u);
  assert.match(payI02, /Payment Customer Action.*REDIRECT.*THREE_DS_CHALLENGE.*SDK.*APP_SWITCH.*QR_CODE/u);
  assert.match(payI02, /稳定.*provider_reference.*不持久化.*Redirect URL.*Client Secret.*SDK Token.*QR Payload/u);
  assert.match(pay06, /PAYMENT_INTENT.*PAYMENT_ATTEMPT.*PAYMENT_ACTION.*CHANNEL_REFUND.*DUPLICATE_PAYMENT_CASE/u);
  assert.match(pay06, /Callback\/Item\/Exception.*Dispute.*Event.*不重复写入.*状态流转/u);
  assert.match(payI04, /Payment Refund Position.*original_provider_transaction_ref_id.*target_provider_transaction_ref_id.*REFUND.*original_provider_transaction_ref_id/u);
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
    /Commerce.*14 (?:张表|表).*COMMERCE_REAL_WORLD_SCENARIOS\.md.*commerce-document-audit-cancel-reversal-design\.md/su,
  );
  assert.match(projectContextSource, /Commerce.*14 (?:张表|tables).*223 (?:个)?字段|Commerce.*14 tables, 223 fields/su);
  assert.match(projectContextSource, /commerce-document-audit-cancel-reversal-design\.md/u);
  assert.match(projectContextSource, /commerce-privacy-fulfillment-simplification-design\.md/u);
  assert.match(
    projectContextSource,
    /Commerce.*未授权.*手工数据库 SQL.*Java.*运行环境/su,
  );
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
  assert.match(rootAgentsSource, /DisputeEvidenceRequested.*Client deadline.*Provider deadline/su);
});

test("Commerce real-world scenario maps exactly the 14 approved tables", () => {
  assert.match(commerceScenarioSource, /NOVA_COMMERCE_UAE_2026/u);
  assert.match(commerceScenarioSource, /14 表逐表映射/u);
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

test("Platform Support requirements expose the generated 63-table public-control-plane snapshot", () => {
  assert.match(source, /Platform Support 当前物理模型为 63 表、822 字段、123 个唯一索引、99 个访问索引、107 条本库关系、5 条 1:1 关系和 7 个 Diagram Groups/u);
  assert.match(source, /31 条复合外键、85 条 CHECK 契约与 36 个 `\*_hash` 字段/u);
  assert.match(source, /Client & Contract 当前物理模型为 37 表、423 字段、57 个唯一索引、44 个访问索引、64 条本库关系、7 条 1:1 关系和 6 个 Diagram Groups/u);
  assert.match(source, /Risk & Compliance 当前物理模型保持 71 表、865 字段、106 个唯一索引、74 个访问索引、111 条本库关系、9 条 1:1 关系和 8 个 Diagram Groups，并冻结 83 条 CHECK 契约/u);
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
  assert.match(platformI19, /permission_id.*required_scope_code.*互斥.*Approved Target Registry/u);
  assert.match(platformI20, /每次读取完整全局不可变 Snapshot.*不使用 ETag.*304/u);
  assert.match(platformI20, /完整校验.*原子切换.*Fail-Closed/u);
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
  assert.match(source, /Route Channel.*OPERATIONS、CLIENT、PARTNER、CHECKOUT、CLIENT_API、PROVIDER_CALLBACK/u);
  assert.match(source, /Security Profile.*OPERATIONS_JWT、CLIENT_JWT、PARTNER_JWT、CLIENT_API_CREDENTIAL、CHECKOUT_SESSION、PROVIDER_CALLBACK、PUBLIC/u);
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
  assert.match(source, /动态 Snapshot.*尚未切换.*静态 Operations Catalog.*部署前运行时事实/u);
  assert.match(source, /公网 TLS.*Nginx.*覆盖.*X-Real-IP.*Gateway.*Guard.*访问日志/u);
  assert.match(source, /Gateway、BFF、Platform Support、Vault.*核心 Java 服务.*私有 HTTP.*不持有 X\.509/u);
  assert.doesNotMatch(gatewayControlPlaneDesignSource, /Forwarded Chain/u);
  assert.match(gatewayControlPlaneDesignSource, /转发前.*代理 Header.*删除/u);
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

  assert.match(commerce01, /Client 命名空间.*client_order_id.*新的 Order.*新的 client_order_id/u);
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
  assert.match(source, /Provider Token、Scheme、BIN、Last4、Issuer Country 和 Provider Fingerprint/);
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
  assert.match(projectContextSource, /Finance[\s\S]*84 tables[\s\S]*1,100 fields/u);
  assert.match(projectContextSource, /Intermediate design and implementation records preserve historical stages and must not be treated as the current table structure/u);
  assert.match(rootAgentsSource, /Intermediate Finance design and implementation records preserve historical stages and must not be treated as the current table structure/u);
});

test("Platform never turns Client silence into a dispute loss", () => {
  assert.doesNotMatch(platformDatabaseSource, /Client 未响应后的认损由 Risk 决定/u);
  assert.match(platformDatabaseSource, /Client 未响应.*EVIDENCE_REQUIRED.*不得自动认损.*显式授权.*Provider.*LOST.*Risk.*DisputeLost/su);
});
