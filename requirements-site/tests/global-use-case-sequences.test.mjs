import test from "node:test";
import assert from "node:assert/strict";

const expectedUseCaseIds = [
  "E2E-01",
  "E2E-02",
  "E2E-03",
  "E2E-04",
  "E2E-05",
  "E2E-06",
  "E2E-07",
  "E2E-08",
  "E2E-09",
  "E2E-10",
  "E2E-11",
  "E2E-12",
  "E2E-13",
  "E2E-14",
  "E2E-15",
  "E2E-16",
];

async function loadSequenceCatalog() {
  try {
    return await import("../content/global-use-case-sequences.mjs");
  } catch {
    return null;
  }
}

async function loadSelectionBehavior() {
  try {
    return await import("../lib/use-case-selection.mjs");
  } catch {
    return null;
  }
}

test("every global use case has a valid entry-to-core sequence", async () => {
  const sequenceCatalog = await loadSequenceCatalog();

  assert.ok(sequenceCatalog, "global use case sequence catalog must exist");
  assert.deepEqual(
    Object.keys(sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES).sort(),
    expectedUseCaseIds,
  );
  assert.equal(sequenceCatalog.SEQUENCE_PARTICIPANTS.api_gateway?.type, "gateway");

  for (const useCaseId of expectedUseCaseIds) {
    const sequence = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES[useCaseId];
    const participantIds = new Set(sequence.participants);
    const participantTypes = sequence.participants.map(
      (participantId) => sequenceCatalog.SEQUENCE_PARTICIPANTS[participantId]?.type,
    );

    assert.ok(participantTypes.includes("entry"), `${useCaseId} must include an entry application`);
    assert.ok(participantTypes.includes("core"), `${useCaseId} must include a core system`);
    assert.ok(participantIds.has("api_gateway"), `${useCaseId} must pass external access through API Gateway`);
    assert.ok(
      sequence.messages.some((message) => message.from === "api_gateway" || message.to === "api_gateway"),
      `${useCaseId} must show the API Gateway call`,
    );
    assert.ok(sequence.messages.length > 0, `${useCaseId} must include calls`);

    for (const message of sequence.messages) {
      assert.ok(participantIds.has(message.from), `${useCaseId} has an unknown sender: ${message.from}`);
      assert.ok(participantIds.has(message.to), `${useCaseId} has an unknown receiver: ${message.to}`);
      assert.ok(["sync", "async"].includes(message.kind), `${useCaseId} has an invalid call kind`);
    }
  }
});

test("E2E-05, E2E-07, and E2E-09 list only participants used by their messages", async () => {
  const sequenceCatalog = await loadSequenceCatalog();

  assert.ok(sequenceCatalog, "global use case sequence catalog must exist");
  for (const useCaseId of ["E2E-05", "E2E-07", "E2E-09"]) {
    const sequence = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES[useCaseId];
    const messageParticipants = new Set(
      sequence.messages.flatMap((message) => [message.from, message.to]),
    );

    for (const participantId of sequence.participants) {
      assert.ok(
        messageParticipants.has(participantId),
        `${useCaseId} has an unused participant: ${participantId}`,
      );
    }
  }
});

test("all Console browser calls traverse their audience-specific BFF", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const expectedBffByConsole = {
    client_console: "client_console_bff",
    partner_console: "partner_console_bff",
    operations_console: "operations_console_bff",
  };

  assert.ok(sequenceCatalog, "global use case sequence catalog must exist");
  for (const [useCaseId, sequence] of Object.entries(sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES)) {
    for (const [index, message] of sequence.messages.entries()) {
      const expectedBff = expectedBffByConsole[message.from];
      if (!expectedBff || message.to !== "api_gateway") {
        continue;
      }

      const gatewayHop = sequence.messages[index + 1];
      const bffHop = sequence.messages[index + 2];
      assert.deepEqual(
        { from: gatewayHop?.from, to: gatewayHop?.to },
        { from: "api_gateway", to: expectedBff },
        `${useCaseId} must route ${message.from} through ${expectedBff}`,
      );
      assert.equal(bffHop?.from, expectedBff, `${useCaseId} must leave Gateway through ${expectedBff}`);
    }
  }
});

test("Console responses never shortcut from a core system directly to Gateway", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const consoleBffs = new Set([
    "client_console_bff",
    "partner_console_bff",
    "operations_console_bff",
  ]);

  assert.ok(sequenceCatalog, "global use case sequence catalog must exist");
  for (const [useCaseId, sequence] of Object.entries(sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES)) {
    for (const [index, message] of sequence.messages.entries()) {
      if (message.to !== "api_gateway") {
        continue;
      }
      const senderType = sequenceCatalog.SEQUENCE_PARTICIPANTS[message.from]?.type;
      if (senderType !== "core") {
        continue;
      }
      const hasBffRequestToSender = sequence.messages
        .slice(0, index)
        .some((candidate) => consoleBffs.has(candidate.from) && candidate.to === message.from);
      assert.equal(
        hasBffRequestToSender,
        false,
        `${useCaseId} must return a Console response through its BFF before Gateway`,
      );
    }
  }
});

test("E2E-15 keeps Operations rule authoring behind Gateway, BFF authorization, and TEST-only validation", async () => {
  const sequenceCatalog = await loadSequenceCatalog();

  assert.ok(sequenceCatalog, "global use case sequence catalog must exist");
  const sequence = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-15"];
  const labels = sequence.messages.map((message) => message.label).join("\n");

  assert.deepEqual(sequence.participants, [
    "operations",
    "operations_console",
    "api_gateway",
    "operations_console_bff",
    "platform",
    "risk",
  ]);
  assert.match(labels, /结构化|规则、条件组、字段、运算符、值/u);
  assert.match(labels, /65,536 UTF-8 Bytes/u);
  assert.match(labels, /16,384 Bytes/u);
  assert.match(labels, /PLT-I02/u);
  assert.match(labels, /risk-validate-v1-\{releaseId\}-\{expectedVersion\}/u);
  assert.match(labels, /HTTP 200 \+ REJECTED\/version=3/u);
  assert.match(labels, /waitingForShadow=true/u);
  assert.match(labels, /其他 Actor 不能接管/u);
  assert.doesNotMatch(labels, /执行 Shadow|激活 PRODUCTION/u);
});

test("clicking a global use case opens, switches, and closes its sequence", async () => {
  const selectionBehavior = await loadSelectionBehavior();

  assert.ok(selectionBehavior, "global use case selection behavior must exist");
  assert.equal(selectionBehavior.toggleUseCaseSelection(null, "E2E-01"), "E2E-01");
  assert.equal(selectionBehavior.toggleUseCaseSelection("E2E-01", "E2E-02"), "E2E-02");
  assert.equal(selectionBehavior.toggleUseCaseSelection("E2E-02", "E2E-02"), null);
});

test("closure sequences cover shared payable maturity, post-payout recovery, and consumer duties", async () => {
  const sequenceCatalog = await loadSequenceCatalog();

  assert.ok(sequenceCatalog, "global use case sequence catalog must exist");

  const maturity = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-10"];
  assert.ok(maturity.participants.includes("finance"));
  assert.ok(!maturity.participants.includes("risk"));
  assert.ok(maturity.participants.includes("client_console"));
  assert.ok(maturity.participants.includes("partner"));
  assert.ok(maturity.participants.includes("partner_console"));
  assert.ok(maturity.messages.some((message) => /Pending.*Available/.test(message.label)));
  assert.ok(maturity.messages.some((message) => /PAYABLE_BALANCE_MATURED:<economics_id>.*已成熟/u.test(message.label)));
  assert.ok(maturity.messages.some((message) => /balance_available_time/u.test(message.label)));
  assert.ok(maturity.messages.some((message) => /Client.*Partner.*PENDING.*AVAILABLE/su.test(message.label)));
  assert.ok(maturity.messages.some((message) => /查询合同成熟与出款资格/u.test(message.label)));
  assert.ok(!maturity.messages.some((message) => /RESERVE_REQUIREMENT|保证金门禁|ReserveHeld/u.test(message.label)));
  assert.ok(!maturity.messages.some((message) => /client_available_time|CLIENT_BALANCE_MATURED|BalanceAvailable|Partner.*直接.*AVAILABLE/u.test(message.label)));

  const recovery = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-11"];
  assert.ok(recovery.participants.includes("finance"));
  assert.ok(recovery.participants.includes("risk"));
  assert.ok(recovery.messages.some((message) => /Negative/.test(message.label)));
  assert.ok(recovery.messages.some((message) => /RefundSucceeded/u.test(message.label)));
  assert.ok(recovery.messages.some((message) => /PENDING.*AVAILABLE.*Client Recovery Receivable/u.test(message.label)));
  const refundRecovery = recovery.messages.find((message) => /RefundSucceeded.*PENDING.*AVAILABLE/u.test(message.label));
  assert.ok(refundRecovery, "RefundSucceeded must use current Client payable balances");
  assert.match(refundRecovery.label, /不动用 CLIENT_SECURITY_DEPOSIT/u);
  assert.ok(recovery.messages.some((message) => /outcome=LOST.*principal_bearer=CLIENT.*security_deposit_use_allowed=1.*Journal.*CLIENT_SECURITY_DEPOSIT/u.test(message.label)));
  assert.ok(recovery.messages.some((message) => /剩余.*CLIENT_RECOVERY_RECEIVABLE.*Journal/u.test(message.label)));
  assert.ok(recovery.messages.some((message) => /未来交易抵扣.*人工收款.*Journal/u.test(message.label)));
  assert.ok(recovery.messages.some((message) => /ClientRecoveryReceivableCreated.*Account ID.*Journal ID/u.test(message.label)));
  assert.ok(!recovery.messages.some((message) => /Recovery Case|Recovery Application|RecoveryApplied|Restricted Fund/u.test(message.label)));
  assert.ok(!recovery.messages.some((message) => /RefundUnknown|SOURCE_COVER|REFUND_CAP/u.test(message.label)));

  const consumerDuties = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-12"];
  for (const participantId of ["consumer", "commerce", "finance", "platform", "hosted_checkout"]) {
    assert.ok(consumerDuties.participants.includes(participantId));
  }
  assert.ok(consumerDuties.messages.some((message) => /税费/.test(message.label)));
  assert.ok(consumerDuties.messages.some((message) => /收据|发票/.test(message.label)));
});

test("manual payout creates four fresh scoped RCO-I09 evaluations and preserves replay boundaries", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-06"].messages;
  const labels = messages.map((message) => message.label).join("\n");
  const requests = messages.filter((message) => message.from === "finance"
    && message.to === "risk" && /RCO-I09/u.test(message.label));
  const responses = messages.filter((message) => message.from === "risk"
    && message.to === "finance" && /assessment_id/u.test(message.label));

  assert.equal(requests.length, 4);
  assert.deepEqual(
    requests.map((message) => message.label.match(/Payout create|final Finance approval|Execution create|external submit/iu)?.[0].toLowerCase()),
    ["payout create", "final finance approval", "execution create", "external submit"],
  );
  assert.equal(responses.length, 4);
  for (const response of responses) {
    assert.match(response.label, /assessment_id.*evaluation_id.*decision.*reason_codes\[\].*matched_controls\[\].*required_amount.*required_currency.*control_set_hash/u);
  }
  assert.match(labels, /同一幂等命令.*复用.*pair.*不同业务检查点.*新 pair/u);
  assert.match(labels, /PAYOUT_CREATION.*PAYOUT.*预分配 payout_id.*source_fact_version=1/u);
  assert.match(labels, /PAYOUT_FINAL_APPROVAL.*PAYOUT.*变更前 expected.*version/u);
  assert.match(labels, /PAYOUT_EXECUTION_CREATION.*PAYOUT_EXECUTION.*预分配 execution_id.*source_fact_version=1/u);
  assert.match(labels, /PAYOUT_EXTERNAL_SUBMISSION.*PAYOUT_EXECUTION.*CREATED.*变更前 expected.*version/u);
  assert.match(labels, /gross_amount=owner_debit_amount=cash_required_amount.*requested_amount.*beneficiary_amount/u);
  assert.match(labels, /CLIENT_RECOVERY_RECEIVABLE\/NONE.*= 0.*CLIENT_SECURITY_DEPOSIT\/NONE.*>= (?:本次 )?required_amount/u);
  assert.match(labels, /PARTNER_RECOVERY_RECEIVABLE\/NONE.*= 0/u);
  assert.match(labels, /eligibility_hash.*不能替代.*下一检查点/u);
  assert.match(labels, /request_hash.*Owner\/OE\/currency.*Beneficiary content_hash.*requested_amount.*fee_amount.*beneficiary_amount.*owner_debit_amount.*cash_required_amount.*fee_mode.*AVAILABLE\/LOCKED Account/u);
  assert.match(labels, /eligibility_hash.*完整 RCO-I09 返回.*Owner\/OE\/currency.*owner_debit_amount.*Canonical/u);
  assert.match(labels, /新 Execution.*Execution create.*external submit.*两对新 pair/u);
  assert.match(labels, /UNKNOWN.*阻断.*Retry/u);
  assert.match(labels, /Fail-Closed/u);
  const checkpoints = [
    { code: "PAYOUT_CREATION", sideEffect: /PAYOUT_LOCK/u },
    { code: "PAYOUT_FINAL_APPROVAL", sideEffect: /approval_hash.*APPROVED/u },
    { code: "PAYOUT_EXECUTION_CREATION", sideEffect: /创建 CREATED Execution/u },
    { code: "PAYOUT_EXTERNAL_SUBMISSION", sideEffect: /人工提交银行指令/u },
  ];
  for (const checkpoint of checkpoints) {
    const requestIndex = messages.findIndex((message) => message.from === "finance"
      && message.to === "risk" && message.label.includes(checkpoint.code));
    const responseIndex = messages.findIndex((message, index) => index > requestIndex
      && message.from === "risk" && message.to === "finance" && /assessment_id/u.test(message.label));
    const gateIndex = messages.findIndex((message, index) => index > responseIndex
      && message.from === "finance" && message.to === "finance"
      && message.label.includes(`${checkpoint.code} 本地门禁`));
    const sideEffectIndex = messages.findIndex((message, index) => index > gateIndex
      && checkpoint.sideEffect.test(message.label));
    assert.ok(requestIndex >= 0, `${checkpoint.code} must call RCO-I09`);
    assert.ok(responseIndex > requestIndex, `${checkpoint.code} must receive its own Risk result`);
    assert.ok(gateIndex > responseIndex, `${checkpoint.code} must run an independent local gate`);
    assert.ok(sideEffectIndex > gateIndex, `${checkpoint.code} gate must precede its side effect`);
    assert.match(messages[gateIndex].label, /ALLOW.*Assessment\/Evaluation.*Scope.*control_set_hash/u);
    assert.match(messages[gateIndex].label, /CLIENT_RECOVERY_RECEIVABLE.*CLIENT_SECURITY_DEPOSIT.*required_amount/u);
    assert.match(messages[gateIndex].label, /PARTNER_RECOVERY_RECEIVABLE.*required_amount\/required_currency.*NULL/u);
    assert.match(messages[gateIndex].label, /Fail-Closed/u);
  }
  assert.ok(messages.some((message) => message.from === "client_console"
    && message.to === "api_gateway" && /申请人直接撤回/u.test(message.label)));
  assert.ok(messages.some((message) => message.from === "partner_console"
    && message.to === "api_gateway" && /申请人直接撤回/u.test(message.label)));
  for (const consoleId of ["client_console", "partner_console"]) {
    assert.ok(messages.some((message) => message.from === consoleId
      && message.to === "api_gateway" && /查询 Payout、Execution、证据与银行对账状态/u.test(message.label)));
  }
  assert.doesNotMatch(labels, /ReserveHeld|ReserveReleased|Restricted Fund|Reserve Case/u);
  assert.doesNotMatch(labels, /Candidate|Promotion|Settlement Profile|Settlement Run|Settlement Payment|Settlement Attempt|current_attempt_id/u);
});

test("dispute RFI keeps Client silence open and requires an explicit disposition or verified Channel outcome", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-04"].messages;
  const openedIndex = messages.findIndex(
    (message) => message.from === "payment" && message.to === "risk" && /DisputeNoticeReceived/u.test(message.label),
  );
  const evidenceRequestedIndex = messages.findIndex(
    (message) => message.from === "risk" && message.to === "platform" && /DisputeEvidenceRequested/u.test(message.label),
  );
  const evidenceSubmittedIndex = messages.findIndex(
    (message) => message.from === "client_console" && message.to === "api_gateway" && /举证/u.test(message.label),
  );

  assert.ok(openedIndex >= 0, "a verified channel notice must open the dispute");
  assert.ok(evidenceRequestedIndex > openedIndex, "Risk must request Client evidence after opening the case");
  assert.ok(evidenceSubmittedIndex > evidenceRequestedIndex, "Client evidence submission follows the RFI notification");
  assert.match(messages[evidenceRequestedIndex].label, /EVIDENCE_REQUIRED/u);
  assert.match(messages[evidenceRequestedIndex].label, /Client.*截止.*早于.*Channel.*截止/u);
  assert.ok(!messages.some((message) => /DisputeOpened.*finance|冻结.*余额.*准备金|Recovery Receivable/u.test(message.label)));
  assert.ok(
    !messages.some((message) => /(?:未响应|无响应|沉默).*(?:自动)?(?:认损|DisputeLost)/u.test(message.label)),
    "Client silence must never accept the loss or emit DisputeLost",
  );
  assert.ok(
    messages.some((message) => message.from === "risk" && message.to === "risk" && /未响应.*案件保持.*EVIDENCE_REQUIRED/u.test(message.label)),
    "missing the Client deadline must keep the dispute open",
  );
  assert.ok(
    messages.some((message) => ["client_console", "operations_console"].includes(message.from) && /经授权.*明确.*(?:接受责任|放弃举证)/u.test(message.label)),
    "an authorized human may make an explicit disposition",
  );
  assert.ok(
    messages.some((message) => message.from === "risk" && message.to === "finance" && /明确授权.*(?:接受责任|放弃举证).*决定.*DisputeLost/u.test(message.label)),
    "only the explicit-disposition branch may produce DisputeLost without a Channel outcome",
  );
  assert.ok(
    messages.some((message) => message.from === "risk" && message.to === "finance" && /ChannelDisputeOutcomeReceived.*已验证.*LOST.*DisputeLost/u.test(message.label)),
    "a verified Channel loss may produce DisputeLost",
  );
  assert.ok(
    messages.every((message) => !/履约|Fulfillment/u.test(message.label)),
    "dispute evidence collection must not depend on the removed Fulfillment capability",
  );

  const labels = messages.map((message) => message.label).join("\n");
  assert.match(labels, /CHANNEL_ACTION.*Version 1.*WON\s*\/\s*LOST/u);
  assert.match(labels, /CLIENT_DISPOSITION.*OPERATIONS_DISPOSITION.*Version 1.*LOST/u);
  assert.match(labels, /Version 2\+.*OPERATIONS_CORRECTION/u);
  assert.match(labels, /trigger_id\s*=\s*decision_source_id/u);
  assert.match(labels, /decision_evaluation_id\s*\+\s*action_effect_id.*decided_by/u);

  const financeOutcomeMessages = messages.filter(
    (message) => message.from === "risk" && message.to === "finance" && /DisputeLost|DisputeOutcomeCorrected/u.test(message.label),
  );
  assert.ok(financeOutcomeMessages.some((message) => /DisputeLost/u.test(message.label)));
  assert.ok(financeOutcomeMessages.some((message) => /DisputeOutcomeCorrected/u.test(message.label)));
  for (const message of financeOutcomeMessages) {
    assert.match(message.label, /outcome_version/u);
    assert.match(message.label, /principal_bearer/u);
    assert.match(message.label, /security_deposit_use_allowed/u);
  }
});

test("E2E-02 builds risk and tax evidence before committing the immutable Order Context", async () => {
  const sequenceCatalog = await loadSequenceCatalog();

  assert.ok(sequenceCatalog, "global use case sequence catalog must exist");
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-02"].messages;
  const preparationIndex = messages.findIndex((message) => /OrderPreparationContext/.test(message.label));
  const riskIndex = messages.findIndex((message) => message.from === "commerce" && message.to === "risk");
  const taxRequestIndex = messages.findIndex((message) => message.from === "commerce" && message.to === "finance" && /FIN-I17.*Tax Snapshot/.test(message.label));
  const taxResponseIndex = messages.findIndex((message) => message.from === "finance" && message.to === "commerce" && /tax_snapshot_id.*calculation_hash/.test(message.label));
  const contextIndex = messages.findIndex((message) => message.from === "commerce" && message.to === "commerce" && /Order Root\/Context\/Legal\/Item/.test(message.label));
  const paymentIntentIndex = messages.findIndex(
    (message) => message.from === "commerce" && message.to === "payment",
  );

  assert.ok(preparationIndex >= 0, "E2E-02 must build OrderPreparationContext before Checkout Risk");
  assert.ok(riskIndex > preparationIndex, "Checkout Risk must consume OrderPreparationContext");
  assert.ok(taxRequestIndex > riskIndex, "Finance Tax Snapshot must be requested only after Risk ALLOW");
  assert.ok(taxResponseIndex > taxRequestIndex, "Finance must return the minimal immutable tax result");
  assert.ok(contextIndex > taxResponseIndex, "the immutable Order Context must include Risk and Tax references");
  assert.ok(paymentIntentIndex > contextIndex, "Payment Intent must be created from the committed Order Context");
  assert.match(messages[preparationIndex].label, /预分配 Order\/Item ID.*V5 Hash.*Association\/Scope.*Platform Service Party.*Legal\/Seller/u);
  assert.match(messages[preparationIndex].label, /Order Context 尚未生成/u);
  assert.match(messages[riskIndex].label, /OrderPreparationContext.*industry_code\/mcc_code/u);
  assert.match(messages[riskIndex].label, /不要求 Commerce 聚合 Hash/u);
  assert.match(messages[taxRequestIndex].label, /预分配订单行 ID.*冻结输入/u);
  assert.match(messages[taxResponseIndex].label, /实收\/反向税汇总.*逐 Item 实收税额/u);

  const cctResponseLabels = messages
    .filter((message) => message.from === "client_contract" && message.to === "commerce")
    .map((message) => message.label)
    .join("\n");
  assert.match(cctResponseLabels, /client_agreement_id.*client_agreement_operating_entity_id/);
  assert.match(cctResponseLabels, /PARTNER.*partner_agreement_id.*partner_agreement_operating_entity_id/);
  assert.match(cctResponseLabels, /Consumer Terms.*Document.*File.*Hash/);
  assert.match(cctResponseLabels, /Refund Policy.*Document.*File.*Hash/);
  assert.match(cctResponseLabels, /Privacy Notice.*Document.*File.*Hash/);
  assert.match(cctResponseLabels, /Seller.*Legal Name.*Support Email.*Support Phone/);
  assert.match(
    cctResponseLabels,
    /public_tax_registration_number.*可空.*JSON null.*不得省略/su,
  );
  assert.match(cctResponseLabels, /Client Pricing.*Partner Share.*Platform Service Charge.*Plan\/Version\/Scope 稳定 ID/u);
  assert.match(cctResponseLabels, /PLATFORM_SIGNATORY Party ID/u);
  assert.match(cctResponseLabels, /逐组件完整计算参数.*不返回中间 Content\/Scope\/Component Hash/u);
  assert.match(cctResponseLabels, /CODE-MOR:CCT:COMMERCIAL_CONTEXT:5/u);
  assert.match(messages[contextIndex].label, /order_context_id.*Assignment\/Association\/Scope ID.*Platform Service Party ID.*V5 Hash.*Tax ID\/Hash/u);
  assert.match(messages[contextIndex].label, /不保存 finance_entity_id/u);
  assert.match(messages[contextIndex].label, /Order 主表金额.*逐 Item 税.*法律展示/u);
  assert.match(messages[contextIndex].label, /不复制 Tax Line 或 Term Component/u);
  assert.doesNotMatch(messages[contextIndex].label, /两套结构化 Commercial Term/u);
  assert.match(
    messages[paymentIntentIndex].label,
    /order_id.*order_context_id.*assignment_id.*operating_entity_id.*金额\/币种.*industry_code\/mcc_code/u,
  );
  assert.ok(
    messages.some((message) => message.from === "client_contract" && message.to === "platform" && /Integration Readiness/u.test(message.label)),
    "CCT-I13 must query Platform Integration Readiness synchronously",
  );
  assert.ok(
    messages.some((message) => message.from === "client_contract" && message.to === "payment" && /Routing Coverage/u.test(message.label)),
    "CCT-I13 must query Payment Routing Coverage synchronously",
  );
  for (const message of messages) {
    assert.doesNotMatch(message.label, /Binding Revision|Agreement.*Content Hash|Seller.*Content Hash/);
    assert.doesNotMatch(
      message.label,
      /client_pricing_(?:content|scope)_hash|partner_share_(?:content|scope)_hash|canonical_hash|COMMERCIAL_CONTEXT:[23]|Eligibility Evaluation|eligibility_(?:id|history_id|status)|evaluation_version|reason_summary_hash|dimension_set_hash|source_set_hash/u,
    );
  }
});

test("E2E-02 keeps technical risk outcomes separate and reserves limits without Action effects", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-02"].messages;
  const riskResult = messages.find(
    (message) => message.from === "risk" && message.to === "payment" && /FAILED\/TIMED_OUT/u.test(message.label),
  );
  const riskReservation = messages.find(
    (message) => message.from === "risk" && message.to === "risk" && /RESERVE_LIMIT_GROUP 系统义务/u.test(message.label),
  );

  assert.ok(riskResult, "E2E-02 must describe the typed Risk result");
  assert.match(riskResult.label, /SUCCEEDED.*业务决定/u);
  assert.match(riskResult.label, /FAILED\/TIMED_OUT.*不携带业务决定.*不默认放行/u);
  assert.match(riskResult.label, /Reservation Group ID/u);
  assert.ok(riskReservation, "E2E-02 must keep the typed RESERVE_LIMIT_GROUP obligation inside Risk");
  assert.match(riskReservation.label, /0\.\.N 条 Limit Binding/u);
  assert.match(riskReservation.label, /零 Binding.*空 Group/u);
  assert.match(riskReservation.label, /Reservation Group.*不生成 Action Intent\/Effect/u);
});

test("E2E-09 validates explicit Order Context facts and exact CCT scope without treating V5 as a trust anchor", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-09"].messages;
  const labels = messages.map((message) => message.label).join("\n");

  assert.match(labels, /order_id\/order_context_id\/assignment_id.*Operating Entity.*金额\/币种.*industry_code\/mcc_code.*逐字段交叉校验/u);
  assert.match(
    labels,
    /term_scope_id.*agreement_operating_entity_id.*context_as_of_time.*Platform Service Charge.*PLATFORM_SIGNATORY Party ID/u,
  );
  assert.match(labels, /Owner\/OE\/Scope\/Party 维度.*Finance Entity/u);
  assert.match(labels, /Partner OE.*平台 Finance Entity.*每本账独立借贷平衡/u);
  assert.doesNotMatch(labels, /校验 V5 Hash/u);
});

test("E2E-05 resolves exact CCT scopes and fails closed instead of using current fees", async () => {
  const sequenceCatalog = await loadSequenceCatalog();

  assert.ok(sequenceCatalog, "global use case sequence catalog must exist");
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-05"].messages;
  const contextReadIndex = messages.findIndex(
    (message) => message.from === "finance" && message.to === "commerce" && /order_id\/order_context_id.*Pricing\/Share\/Platform Service Charge Scope ID.*Platform Service Party ID.*V5 审计摘要/.test(message.label),
  );
  const contextCrossCheckIndex = messages.findIndex(
    (message) => message.from === "finance" && message.to === "finance" && /PaymentSucceeded.*逐字段交叉校验/.test(message.label),
  );
  const scopeReadIndex = messages.findIndex(
    (message) => message.from === "finance" && message.to === "client_contract" && /GetImmutableCommercialTermScope/.test(message.label),
  );
  const freezeIndex = messages.findIndex(
    (message) => message.from === "finance" && message.to === "finance" && /Transaction Economics Component 冻结.*Version\/Scope\/Component ID.*完整参数/.test(message.label),
  );
  const paymentSucceeded = messages.find(
    (message) => message.from === "payment" && message.to === "finance" && /PaymentSucceeded/u.test(message.label),
  );
  const channelOpenItemIndex = messages.findIndex(
    (message) => message.from === "finance" && message.to === "finance"
      && /ACTIVE Finance channel_account.*Channel Open Item.*CHANNEL_UNSETTLED_RECEIVABLE/u.test(message.label),
  );

  assert.ok(contextReadIndex >= 0, "Finance must read one immutable Commerce Order Context response");
  assert.ok(contextCrossCheckIndex > contextReadIndex, "Finance must cross-check Payment and Commerce explicit facts");
  assert.ok(scopeReadIndex > contextCrossCheckIndex, "Finance must resolve each immutable CCT scope only after the Order Context facts match");
  assert.ok(freezeIndex > scopeReadIndex, "Finance must validate the scope before freezing calculation inputs");
  assert.match(messages[scopeReadIndex].label, /term_scope_id \+ agreement_operating_entity_id \+ context_as_of_time/u);
  assert.match(messages[scopeReadIndex].label, /不接收 V5 Hash/u);
  assert.match(messages[scopeReadIndex].label, /不重新解析当前 ACTIVE/u);
  assert.match(messages[freezeIndex].label, /暂时调用失败不 ACK.*PaymentSucceeded 重投/u);
  assert.match(messages[freezeIndex].label, /永久完整性错误.*DLQ/u);
  assert.match(messages[freezeIndex].label, /禁止当前\/默认\/零费率.*Journal/u);
  assert.ok(paymentSucceeded, "PaymentSucceeded must be self-contained for Finance");
  assert.match(paymentSucceeded.label, /payment_intent_id.*payment_attempt_id/u);
  assert.match(paymentSucceeded.label, /channel_id.*channel_account_id.*mid_id/u);
  assert.match(paymentSucceeded.label, /channel_transaction_ref_id.*reference_type.*external_transaction_id/u);
  assert.ok(channelOpenItemIndex > freezeIndex, "Finance must create its local Channel Open Item after freezing the economics context");
  assert.match(messages[channelOpenItemIndex].label, /channel_id \+ mid_id \+ Operating Entity \+ currency/u);
  assert.match(messages[channelOpenItemIndex].label, /Finance channel_account_id.*payment_intent_id\/payment_attempt_id.*channel_transaction_ref_id.*reference_type.*external_transaction_id.*transaction_economics_id.*gross_receivable/u);
  assert.match(messages[channelOpenItemIndex].label, /Fail-Closed.*不在线查询 Payment/u);
  assert.equal(messages.some((message) => message.from === "finance" && message.to === "payment"), false);
});

test("E2E-02 derives Acceptance readiness from three immutable evidence-bound facts without a Fulfillment flow", async () => {
  const sequenceCatalog = await loadSequenceCatalog();

  assert.ok(sequenceCatalog, "global use case sequence catalog must exist");
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-02"].messages;
  const acceptanceEvidenceIndex = messages.findIndex(
    (message) => message.to === "commerce" && /LEGAL_ACCEPTANCE.*Network Evidence/u.test(message.label),
  );
  const acceptanceIndex = messages.findIndex(
    (message) => message.to === "commerce" && /三种不可变 Acceptance/u.test(message.label),
  );
  const readinessRequestIndex = messages.findIndex(
    (message) => message.from === "payment" && message.to === "commerce" && /COM-I10/u.test(message.label),
  );
  const readinessResponseIndex = messages.findIndex(
    (message) => message.from === "commerce" && message.to === "payment" && /consumer_terms_acceptance_id/u.test(message.label),
  );

  assert.ok(acceptanceEvidenceIndex >= 0, "Acceptance must bind trusted LEGAL_ACCEPTANCE network evidence");
  assert.ok(acceptanceIndex > acceptanceEvidenceIndex, "Acceptance must be inserted after its bound network evidence");
  assert.match(
    messages[acceptanceIndex].label,
    /CONSUMER_TERMS.*REFUND_POLICY.*PRIVACY_NOTICE/u,
  );
  assert.ok(readinessRequestIndex > acceptanceIndex, "Payment readiness must be queried after all acceptance facts exist");
  assert.ok(readinessResponseIndex > readinessRequestIndex, "Commerce must return the derived readiness result");
  assert.match(messages[readinessRequestIndex].label, /服务端实时校验/u);
  assert.match(
    messages[readinessResponseIndex].label,
    /consumer_terms_acceptance_id.*refund_policy_acceptance_id.*privacy_notice_acceptance_id.*acceptance_completed_at.*Order Version.*Expires At/u,
  );
  assert.doesNotMatch(
    messages.map((message) => message.label).join("\n"),
    /acceptance_summary_(?:id|version)|completion_acceptance_id|Accepted Set Hash/u,
  );
  assert.doesNotMatch(messages.map((message) => message.label).join("\n"), /Fulfillment|履约/u);

  const commerceMessages = messages
    .filter((message) => message.from === "commerce")
    .map((message) => message.label)
    .join("\n");
  assert.doesNotMatch(
    commerceMessages,
    /webhook_endpoint_id|webhook_endpoint_ver_id|Webhook Endpoint Version/u,
  );
});

test("E2E-01 exposes a non-persisted CCT-I10 readiness diagnostic", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-01"].messages;
  const labels = messages.map((message) => message.label).join("\n");

  assert.match(labels, /client_id.*country.*currency.*industry_code.*mcc_code/u);
  assert.match(labels, /READY \| NOT_READY \| RESTRICTED.*checked_at.*reason_code/u);
  assert.match(labels, /非持久化诊断.*不是交易证明/u);
  assert.match(labels, /Integration Readiness/u);
  assert.match(labels, /Routing Coverage/u);
  assert.match(labels, /当前 ACTIVE Tenant.*API Credential.*Scope.*Endpoint Revision.*Subscription/u);
  assert.match(labels, /同步派生.*Fail-Closed/u);
  const platformReadiness = messages.find(
    (message) => message.from === "platform" && message.to === "client_contract"
      && /READY \| NOT_READY \| RESTRICTED/u.test(message.label),
  );
  assert.ok(platformReadiness, "E2E-01 must expose the exact Platform readiness response");
  assert.match(platformReadiness.label, /技术依赖失败.*不返回业务状态/u);
  assert.doesNotMatch(platformReadiness.label, /UNKNOWN/u);
  assert.doesNotMatch(labels, /冻结 Requirement Config|Readiness Evaluation|追加不可变 Eligibility Evaluation|ClientEligibilityChanged/u);
});

test("E2E-01 scopes Risk Case idempotency by typed Application, submission and role", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-01"].messages;
  const createCase = messages.find(
    (message) => message.from === "client_contract" && message.to === "risk",
  );
  const returnCase = messages.find(
    (message) => message.from === "risk" && message.to === "client_contract" && /due_diligence_case_id/u.test(message.label),
  );

  assert.ok(createCase, "E2E-01 must show the post-commit Risk Case call");
  assert.match(createCase.label, /source_namespace=CCT_CLIENT_APPLICATION/u);
  assert.match(
    createCase.label,
    /source_namespace \+ source_application_id \+ source_submission_id \+ role_type \+ role_id \+ qualification_type/u,
  );
  assert.match(createCase.label, /同 submissionId 同参幂等.*异参冲突/u);
  assert.doesNotMatch(createCase.label, /submission_content_hash/u);
  assert.ok(returnCase, "E2E-01 must return the main Role Case by the same typed identity");
  assert.match(returnCase.label, /主 Role.*Typed.*身份/u);
});

test("E2E-01 authenticates a CUSTOMER Account and provisions its Client Tenant before readiness", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-01"].messages;
  const labels = messages.map((message) => message.label);
  const authenticationIndex = labels.findIndex((label) => /CUSTOMER.*Email\/Password.*邮件链接/u.test(label));
  const accountContextIndex = labels.findIndex((label) => /Account Context/u.test(label));
  const draftApplicationIndex = labels.findIndex((label) => /CCT-I15 START_OR_RESUME.*DRAFT.*requested_by_account_id/u.test(label));
  const provisionIndex = labels.findIndex((label) => /PLT-I17.*tenant_type=CLIENT.*owner_ref_id.*requested_by_account_id.*source_version.*payload_hash/u.test(label));
  const uploadIndex = labels.findIndex((label) => /PLT-I04.*Account Context.*PENDING.*TENANT_ONBOARDING_OWNER.*tenant_id/u.test(label));
  const finalSubmitIndex = labels.findIndex((label) => /CCT-I15 SUBMIT.*冻结.*File ID.*Risk/u.test(label));
  const riskIndex = labels.findIndex((label) => /CCT_CLIENT_APPLICATION.*RCO-I11/u.test(label));
  const activationIndex = labels.findIndex((label) => /激活 Client Tenant.*撤销 TENANT_ONBOARDING_OWNER.*授予 TENANT_OWNER/u.test(label));
  const tenantSelectionIndex = labels.findIndex((label) => /PLT-I16.*选择.*Client Tenant Member/u.test(label));
  const sessionRotationIndex = labels.findIndex((label) => /新建固定 Client Tenant Session.*撤销.*旧 Refresh Family/u.test(label));
  const cookieReplacementIndex = labels.findIndex((label) => /替换 __Secure-client-refresh.*Path=\/api\/client\/auth\/v1/u.test(label));
  const tenantContextIndex = labels.findIndex((label) => /Client Tenant Context Token.*不复用旧 Session/u.test(label));
  const beneficiaryIndex = labels.findIndex((label) => /可选提交 Beneficiary Account/u.test(label));
  const integrationConfigIndex = labels.findIndex((label) => /配置 API Credential、Webhook/u.test(label));
  const readinessIndex = labels.findIndex((label) => /CCT-I10.*实时诊断/u.test(label));

  for (const [name, index] of Object.entries({
    authenticationIndex,
    accountContextIndex,
    draftApplicationIndex,
    provisionIndex,
    uploadIndex,
    finalSubmitIndex,
    riskIndex,
    activationIndex,
    tenantSelectionIndex,
    sessionRotationIndex,
    cookieReplacementIndex,
    tenantContextIndex,
    beneficiaryIndex,
    integrationConfigIndex,
    readinessIndex,
  })) {
    assert.ok(index >= 0, `E2E-01 must include ${name}`);
  }
  assert.ok(authenticationIndex < accountContextIndex);
  assert.ok(accountContextIndex < draftApplicationIndex);
  assert.ok(draftApplicationIndex < provisionIndex);
  assert.ok(provisionIndex < uploadIndex);
  assert.ok(uploadIndex < finalSubmitIndex);
  assert.ok(finalSubmitIndex < riskIndex);
  assert.ok(riskIndex < activationIndex);
  assert.ok(activationIndex < tenantSelectionIndex);
  assert.ok(tenantSelectionIndex < sessionRotationIndex);
  assert.ok(sessionRotationIndex < tenantContextIndex);
  assert.ok(tenantContextIndex < cookieReplacementIndex);
  assert.ok(tenantContextIndex < beneficiaryIndex);
  assert.ok(tenantContextIndex < integrationConfigIndex);
  assert.ok(riskIndex < readinessIndex);

  const provision = messages[provisionIndex].label;
  assert.match(provision, /同版本同 Hash.*幂等.*同版本异 Hash.*冲突.*旧版本.*拒绝/u);
  assert.doesNotMatch(provision, /owner_type|owner_id|sourceVersion/u);
  assert.match(labels.join("\n"), /PENDING.*不签发普通 Tenant Context Token/u);
});

test("E2E-02 keeps the PLT-I03 tuple inside Gateway and forwards only minimal Client identity", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-02"].messages;
  const credentialValidation = messages.find(
    (message) => message.from === "api_gateway" && message.to === "platform" && /PLT-I03/u.test(message.label),
  );
  const clientApiRoute = messages.find(
    (message) => message.from === "api_gateway" && message.to === "client_api",
  );

  assert.ok(credentialValidation);
  assert.match(
    credentialValidation.label,
    /tenant_id.*tenant_type=CLIENT.*owner_ref_id=client_id.*credential_id.*ACTIVE Scope/u,
  );
  assert.match(credentialValidation.label, /Access Tenant/u);
  assert.ok(clientApiRoute);
  assert.match(
    clientApiRoute.label,
    /精确要求 checkouts\.write.*X-Request-Id.*X-Gateway-Client-Id.*X-Gateway-Credential-Id/iu,
  );
  assert.match(clientApiRoute.label, /不下传.*Authorization.*Tenant.*Scope.*Route.*Channel/iu);
});

for (const [sequenceId, requiredScope] of [
  ["E2E-12", "checkouts.write"],
  ["E2E-13", "orders.cancel"],
]) {
  test(`${sequenceId} validates the exact Client API scope before routing`, async () => {
    const sequenceCatalog = await loadSequenceCatalog();
    const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES[sequenceId].messages;
    const validationIndex = messages.findIndex((message) => (
      message.from === "api_gateway" && message.to === "platform" && /PLT-I03/u.test(message.label)
    ));
    const resultIndex = messages.findIndex((message) => (
      message.from === "platform" && message.to === "api_gateway" && /tenant_type=CLIENT/u.test(message.label)
    ));
    const routeIndex = messages.findIndex((message) => (
      message.from === "api_gateway" && message.to === "client_api"
    ));

    assert.ok(validationIndex >= 0, `${sequenceId} 缺少 Gateway -> Platform PLT-I03`);
    assert.match(messages[validationIndex].label, /PHC verifier/u);
    assert.match(messages[validationIndex].label, /不提交.*Tenant.*Client.*required Scope/u);
    assert.doesNotMatch(messages[validationIndex].label, new RegExp(requiredScope.replace(".", "\\."), "u"));
    assert.ok(resultIndex > validationIndex, `${sequenceId} 缺少 PLT-I03 可信结果`);
    assert.match(
      messages[resultIndex].label,
      /trusted tenant_id.*tenant_type=CLIENT.*owner_ref_id.*credential_id.*effective ACTIVE scopes.*401.*503/iu,
    );
    assert.ok(routeIndex > resultIndex, `${sequenceId} 不得在 PLT-I03 结果前路由 Client API`);
    assert.match(messages[routeIndex].label, new RegExp(requiredScope.replace(".", "\\."), "u"));
    assert.match(
      messages[routeIndex].label,
      /X-Request-Id.*X-Gateway-Client-Id.*X-Gateway-Credential-Id.*不下传.*Authorization.*Tenant.*Scope.*Route.*Channel/iu,
    );
  });
}

test("E2E-14 onboards a Local Partner through the typed application and one Tenant token", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const sequence = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-14"];

  assert.ok(sequence, "E2E-14 must exist");
  assert.deepEqual(sequence.participants, [
    "partner",
    "partner_console",
    "api_gateway",
    "partner_console_bff",
    "platform",
    "client_contract",
    "risk",
  ]);

  const messageLabels = sequence.messages.map((message) => message.label);
  const labels = messageLabels.join("\n");
  assert.match(labels, /全局 CUSTOMER Account.*Email\/Password.*Google/u);
  const draftIndex = messageLabels.findIndex((label) => /CCT-I15 START_OR_RESUME.*DRAFT Partner Application.*requested_by_account_id/u.test(label));
  const provisionIndex = messageLabels.findIndex((label) => /PLT-I17.*tenant_type=PARTNER.*owner_ref_id.*requested_by_account_id.*source_version.*payload_hash/u.test(label));
  const pendingIndex = messageLabels.findIndex((label) => /PENDING Partner Tenant.*不签发普通 Tenant Context Token/u.test(label));
  const uploadIndex = messageLabels.findIndex((label) => /PLT-I04.*Account Context.*PENDING.*TENANT_ONBOARDING_OWNER.*tenant_id/u.test(label));
  const submitIndex = messageLabels.findIndex((label) => /CCT-I15 SUBMIT.*File ID.*Risk/u.test(label));
  const riskIndex = messageLabels.findIndex((label) => /CCT_LOCAL_PARTNER_APPLICATION.*LOCAL_PARTNER.*PARTNER_DUE_DILIGENCE/u.test(label));
  const activationIndex = messageLabels.findIndex((label) => /激活 Partner Tenant.*撤销 TENANT_ONBOARDING_OWNER.*授予 TENANT_OWNER/u.test(label));
  const selectionIndex = messageLabels.findIndex((label) => /PLT-I16 选择单一 Partner Tenant Member/u.test(label));
  const sessionIndex = messageLabels.findIndex((label) => /新建固定 Partner Tenant Session.*撤销.*旧 Refresh Family/u.test(label));
  const tokenIndex = messageLabels.findIndex((label) => /Partner Tenant Context Token.*不复用旧 Session/u.test(label));
  const cookieIndex = messageLabels.findIndex((label) => /替换 __Secure-partner-refresh.*Path=\/api\/partner\/auth\/v1/u.test(label));

  for (const [name, index] of Object.entries({
    draftIndex, provisionIndex, pendingIndex, uploadIndex, submitIndex, riskIndex,
    activationIndex, selectionIndex, sessionIndex, tokenIndex, cookieIndex,
  })) {
    assert.ok(index >= 0, `E2E-14 must include ${name}`);
  }
  assert.ok(draftIndex < provisionIndex);
  assert.ok(provisionIndex < pendingIndex);
  assert.ok(pendingIndex < uploadIndex);
  assert.ok(uploadIndex < submitIndex);
  assert.ok(submitIndex < riskIndex);
  assert.ok(riskIndex < activationIndex);
  assert.ok(activationIndex < selectionIndex);
  assert.ok(selectionIndex < sessionIndex);
  assert.ok(sessionIndex < tokenIndex);
  assert.ok(tokenIndex < cookieIndex);

  assert.match(messageLabels[provisionIndex], /同版本同 Hash.*幂等.*同版本异 Hash.*冲突.*旧版本.*拒绝/u);
  assert.match(labels, /CCT_LOCAL_PARTNER_APPLICATION.*LOCAL_PARTNER.*PARTNER_DUE_DILIGENCE/u);
  assert.match(labels, /RELATED_PERSON.*RELATED_PERSON_KYC/u);
  assert.match(labels, /CCT.*APPROVED.*Local Partner.*ACTIVE/u);
  assert.match(labels, /source_version.*owner_status=ACTIVE/u);
});

test("E2E-14 keeps the RCO-I11 Case response separate from the qualification decision lifecycle", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-14"].messages;
  const caseResponseIndex = messages.findIndex(
    (message) => message.from === "risk"
      && message.to === "client_contract"
      && message.kind === "sync"
      && /主 Role Case identity/u.test(message.label),
  );
  const decisionEventIndex = messages.findIndex(
    (message) => message.from === "risk"
      && message.to === "client_contract"
      && message.kind === "async"
      && /RoleQualificationApproved.*qualification_decision_id.*qualification_decision_version/u.test(message.label),
  );
  const cctApprovalIndex = messages.findIndex(
    (message) => message.from === "client_contract"
      && message.to === "client_contract"
      && /Application=APPROVED.*Local Partner=ACTIVE/u.test(message.label),
  );

  assert.ok(caseResponseIndex >= 0, "RCO-I11 must return the main Role Case identity synchronously");
  assert.doesNotMatch(
    messages[caseResponseIndex].label,
    /Qualification Decision|qualification_decision|decision_version|APPROVED|REJECTED/u,
  );
  assert.ok(decisionEventIndex > caseResponseIndex, "Risk decision must arrive after RCO-I11");
  assert.ok(cctApprovalIndex > decisionEventIndex, "CCT approval must consume the later Risk decision");
});

test("E2E-03 inherits parent commercial proof into REFUND transaction economics", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-03"].messages;
  const inheritance = messages.find((message) => /REFUND Transaction Economics/.test(message.label));

  assert.ok(inheritance, "refund processing must freeze transaction economics");
  assert.match(inheritance.label, /parent_economics_id.*Agreement-OE Association/u);
  assert.match(inheritance.label, /Component Link.*Scope.*计算参数/u);
  assert.match(inheritance.label, /finance_entity_id/u);
  assert.match(inheritance.label, /PLATFORM_SERVICE_CHARGE/u);
  assert.match(inheritance.label, /不在结果头重复保存 V5 commercial_context_hash/u);
  assert.match(inheritance.label, /Partner OE.*平台 Finance Entity.*各自平衡/u);
  assert.match(inheritance.label, /不回读当前 CCT/);
});

test("E2E-03 keeps independent Commerce and Payment refund positions and posts Finance on success only", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-03"].messages;
  const labels = messages.map((message) => message.label).join("\n");
  const paymentToCommerce = messages
    .filter((message) => message.from === "payment" && message.to === "commerce")
    .map((message) => message.label)
    .join("\n");
  const financeToCommerce = messages
    .filter((message) => message.from === "finance" && message.to === "commerce")
    .map((message) => message.label)
    .join("\n");
  const paymentToFinance = messages
    .filter((message) => message.from === "payment" && message.to === "finance")
    .map((message) => message.label)
    .join("\n");

  assert.match(labels, /client_refund_id.*refund_amount/su);
  assert.match(labels, /PENDING.*PROCESSING.*SUCCEEDED.*FAILED\/CANCELLED/su);
  assert.match(labels, /不保存 UNKNOWN 投影/u);
  assert.match(labels, /本地.*reserved_amount.*PAY-I04/su);
  assert.match(labels, /Payment Refund Position.*CAS.*reserved_amount.*Channel Refund/u);
  assert.match(labels, /UNKNOWN.*Payment 本地.*Commerce.*PROCESSING/u);
  assert.match(paymentToFinance, /RefundSucceeded/u);
  assert.doesNotMatch(paymentToFinance, /RefundFailed|RefundCancelled|RefundUnknown/u);
  assert.doesNotMatch(paymentToCommerce, /RefundUnknown/u);
  assert.deepEqual(
    messages
      .filter((message) => message.from === "payment" && /RefundSucceeded/u.test(message.label))
      .map((message) => message.to)
      .sort(),
    ["commerce", "finance"],
  );
  assert.deepEqual(
    messages
      .filter((message) => message.from === "payment" && /RefundFailed|RefundCancelled/u.test(message.label))
      .map((message) => message.to),
    ["commerce"],
  );
  assert.doesNotMatch(financeToCommerce, /RefundFunding(?:Released|Consumed)|Ack 后/u);
  assert.doesNotMatch(labels, /refund_funding_reservation_id|FIN-I21|Funding Reservation|REFUND_CAP|SOURCE_COVER|RefundUnknown|RELEASE_PENDING|origin_scope_key|origin_request_ref/u);
});

test("E2E-13 separates unpaid Cancel, authorization Void, and captured Refund", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-13"].messages;
  const labels = messages.map((message) => message.label).join("\n");

  assert.match(labels, /COM-I05.*Cancel/u);
  assert.match(labels, /没有成功 Authorization、Capture 或 Sale/u);
  assert.match(labels, /Cancel 不会静默升级为 Reversal/u);
  assert.match(labels, /PAY-I13.*锁.*CAS/u);
  assert.match(labels, /授权成功但未 Capture.*Void/u);
  assert.match(labels, /全额 Capture.*REFUND_REQUIRED.*Refund Request/u);
  assert.match(labels, /PARTIAL_CAPTURE_REVERSAL_NOT_SUPPORTED/u);
  assert.match(labels, /Order 仍保持 PAID/u);
  assert.doesNotMatch(labels, /payment_action\.action_type=REVERSAL|t_reversal_request/u);
});

test("Payment sequences preserve the frozen unknown, refund, and cancellation contracts", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const paymentFlow = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-02"].messages;
  const refundFlow = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-03"].messages;
  const reversalFlow = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-13"].messages;
  const paymentLabels = paymentFlow.map((message) => message.label).join("\n");
  const refundLabels = refundFlow.map((message) => message.label).join("\n");
  const reversalLabels = reversalFlow.map((message) => message.label).join("\n");

  assert.match(paymentLabels, /FAILED \+ failure_stage/u);
  assert.match(paymentLabels, /首次资金 Action 提交前.*不持久化独立 Gate/u);
  assert.match(paymentLabels, /阻断、缺失、超时或技术失败.*Attempt\/Action FAILED.*State Transition/u);
  assert.match(paymentLabels, /不创建 Channel Interaction/u);
  assert.doesNotMatch(paymentLabels, /RISK_FAILED|RISK_REJECTED|LIMIT_REJECTED|SUCCESS_CONFLICT/u);
  const paymentAttemptUnknownMessages = paymentFlow.filter((message) => /PaymentAttemptUnknown/.test(message.label));
  assert.equal(paymentAttemptUnknownMessages.length, 1, "PaymentAttemptUnknown must be emitted once per UNKNOWN transition");
  const [paymentAttemptUnknown] = paymentAttemptUnknownMessages;
  assert.deepEqual(
    { from: paymentAttemptUnknown.from, to: paymentAttemptUnknown.to, kind: paymentAttemptUnknown.kind },
    { from: "payment", to: "risk", kind: "async" },
  );
  assert.match(paymentAttemptUnknown.label, /order\/order_context\/intent\/attempt\/action、版本/u);
  assert.match(paymentAttemptUnknown.label, /Channel ID \+ Channel Account ID \+ MID ID \+ Payment Action ID/u);
  assert.doesNotMatch(paymentAttemptUnknown.label, /Connection Version|connection_version_id|mid_credential_ref_id/u);
  assert.match(paymentAttemptUnknown.label, /金额币种.*reason\/event time/u);
  const duplicateDetectedIndex = paymentFlow.findIndex((message) => /DuplicatePaymentDetected/.test(message.label));
  const duplicateRemediatedIndex = paymentFlow.findIndex((message) => /DuplicatePaymentRemediated/.test(message.label));
  assert.ok(duplicateDetectedIndex >= 0, "Payment must publish DuplicatePaymentDetected");
  assert.ok(duplicateRemediatedIndex > duplicateDetectedIndex, "DuplicatePaymentRemediated follows detection");
  for (const messageIndex of [duplicateDetectedIndex, duplicateRemediatedIndex]) {
    assert.deepEqual(
      { from: paymentFlow[messageIndex].from, to: paymentFlow[messageIndex].to, kind: paymentFlow[messageIndex].kind },
      { from: "payment", to: "finance", kind: "async" },
    );
  }

  const refundUnknownMessages = refundFlow.filter((message) => /RefundUnknown/.test(message.label));
  assert.equal(refundUnknownMessages.length, 0, "Refund UNKNOWN is Payment-local and must not be published");
  assert.match(refundLabels, /commerce_refund_request_id.*refund_amount/u);
  assert.match(refundLabels, /Payment Refund Position.*CAS.*reserved_amount.*Channel Refund/u);
  assert.doesNotMatch(refundLabels, /refund_funding_reservation_id|Funding Reservation|FIN-I21|SOURCE_COVER|REFUND_CAP/u);

  assert.match(reversalLabels, /COM-I05 Cancel：POST \/api\/v1\/orders\/\{orderNo\}\/cancel/u);
  assert.match(reversalLabels, /Idempotency-Key，无 Body、无外部 Expected Version/u);
  assert.doesNotMatch(reversalLabels, /COM-I05 Cancel（(?:client_order_id|order_id)、Expected Version/u);
  assert.match(reversalLabels, /PAY-I12[\s\S]*确定性子幂等键/u);
  assert.match(reversalLabels, /USE_CANCEL[\s\S]*确定性子幂等键[\s\S]*PAY-I12/u);
  const commerceReserveIndex = reversalFlow.findIndex((message) => /Commerce Refund Request.*Order Refund Position/u.test(message.label));
  const paymentRefundIndex = reversalFlow.findIndex((message) => /PAY-I04/.test(message.label));
  assert.ok(commerceReserveIndex >= 0, "Commerce must reserve its local refund position after REFUND_REQUIRED");
  assert.ok(paymentRefundIndex > commerceReserveIndex, "Commerce calls PAY-I04 directly after its local reserve");
  assert.equal(
    reversalFlow.slice(0, paymentRefundIndex + 1).filter((message) => message.from === "finance" || message.to === "finance").length,
    0,
    "Finance must not gate Channel Refund creation",
  );
  assert.deepEqual(
    { from: reversalFlow[paymentRefundIndex].from, to: reversalFlow[paymentRefundIndex].to, kind: reversalFlow[paymentRefundIndex].kind },
    { from: "commerce", to: "payment", kind: "sync" },
  );
  const voidSucceeded = reversalFlow.find((message) => /返回 Void 权威终态/.test(message.label));
  assert.ok(voidSucceeded, "Void success must have an authoritative terminal message");
  assert.deepEqual(
    { from: voidSucceeded.from, to: voidSucceeded.to, kind: voidSucceeded.kind },
    { from: "acquirer", to: "payment", kind: "sync" },
  );
  assert.match(voidSucceeded.label, /Attempt=VOIDED \/ AUTHORIZATION_VOIDED、Intent=CANCELLED/u);
  assert.doesNotMatch(voidSucceeded.label, /Attempt=FAILED/u);
  assert.match(reversalLabels, /PAY-I13[\s\S]*captured_amount/u);
});

test("Payment sequences freeze generic customer actions, exact refund navigation, and duplicate-success evidence", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const paymentLabels = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-02"].messages
    .map((message) => message.label)
    .join("\n");
  const refundLabels = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-03"].messages
    .map((message) => message.label)
    .join("\n");

  assert.match(paymentLabels, /Payment Method Catalog.*payment_method_code.*Attempt/u);
  assert.match(paymentLabels, /Payment Customer Action.*REDIRECT.*THREE_DS_CHALLENGE.*SDK.*APP_SWITCH.*QR_CODE/u);
  assert.match(paymentLabels, /只持久化.*非敏感.*channel_reference.*不保存.*Redirect URL.*Client Secret.*SDK Token.*QR Payload/u);
  assert.match(paymentLabels, /同一 Intent.*第二.*不同.*SALE.*CAPTURE.*成功事实/u);
  assert.match(paymentLabels, /重复 Callback.*同一外部交易.*QUERY.*AUTHORIZE.*CAPTURE.*不.*Duplicate Payment/u);
  assert.match(paymentLabels, /successful_attempt_id.*不(?:替换|改写).*CONFLICTED.*重复 Ref.*补偿退款/u);
  assert.match(paymentLabels, /PAYMENT_INTENT.*PAYMENT_ATTEMPT.*PAYMENT_ACTION.*CHANNEL_REFUND.*DUPLICATE_PAYMENT_CASE/u);
  assert.match(paymentLabels, /Callback\/Item\/Exception.*Dispute Event.*不写入.*State Transition/u);
  assert.match(refundLabels, /Payment Refund Position.*original_channel_txn_ref_id.*原 Payment Action.*Channel Account.*MID/u);
  assert.match(refundLabels, /REFUND Action.*target_channel_txn_ref_id.*REFUND Transaction Ref.*original_channel_txn_ref_id/u);
});

test("Finance sequences resolve exact CCT scopes and freeze their components locally", async () => {
  const sequenceCatalog = await loadSequenceCatalog();

  for (const useCaseId of ["E2E-05", "E2E-07"]) {
    const labels = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES[useCaseId].messages
      .filter((message) => message.from === "finance")
      .map((message) => message.label)
      .join("\n");

    assert.match(labels, /Scope ID/u);
    assert.match(labels, /V5.*审计摘要/u);
    assert.match(labels, /不可变/u);
    assert.match(labels, /冻结.*(?:完整参数|原交易组件)/u);
    assert.match(labels, /不(?:重新解析|查询)当前 ACTIVE/u);
    assert.match(labels, /不 ACK.*重投/u);
    assert.match(labels, /永久完整性错误.*DLQ/u);
    assert.match(labels, /禁止当前\/默认\/零费率|不使用(?:当前\/默认\/零|默认)费率/u);
    assert.doesNotMatch(
      labels,
      /client_pricing_(?:content|scope)_hash|partner_share_(?:content|scope)_hash|canonical_hash|COMMERCIAL_CONTEXT:[23]/u,
    );
  }
});

test("E2E-12 resolves current readiness and freezes legal/tax references in the Order Context", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const labels = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-12"].messages
    .map((message) => message.label)
    .join("\n");

  assert.match(labels, /每次实时校验/u);
  assert.match(labels, /完整 V5 Bundle/u);
  assert.match(labels, /Commerce 持久化.*Agreement Association.*Pricing\/Share\/Platform Service Charge Scope ID.*Platform Service Party ID/u);
  assert.match(labels, /OrderPreparationContext.*不依赖 Commerce 聚合 Hash/u);
  assert.match(labels, /FIN-I17.*tax_snapshot_id\/calculation_hash.*逐Item实收税/u);
  assert.match(labels, /唯一不可变 Order Context.*order_context_id/u);
  assert.match(labels, /LEGAL_ACCEPTANCE.*Network Evidence/u);
  assert.match(labels, /三种不可变 Acceptance/u);
  assert.match(labels, /COM-I10.*三种必需 Acceptance/u);
  assert.match(labels, /三个显式 Acceptance ID.*acceptance_completed_at.*Order Version.*Expires At/u);
  assert.doesNotMatch(
    labels,
    /Eligibility Evaluation|eligibility_(?:id|history_id|status)|evaluation_version|reason_summary_hash|dimension_set_hash|source_set_hash|Acceptance Summary|acceptance_summary/u,
  );
});

test("E2E-02 passes stable Consumer tokens and restricted Network Evidence only through the synchronous risk path", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const messages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-02"].messages;
  const labels = messages.map((message) => message.label).join("\n");
  const paymentStartEvidenceIndex = messages.findIndex(
    (message) => message.to === "commerce" && /PAYMENT_START.*Network Evidence/u.test(message.label),
  );
  const riskIndex = messages.findIndex(
    (message) => message.from === "payment" && message.to === "risk" && /最新 Consumer Context.*PAYMENT_START/u.test(message.label),
  );

  assert.match(labels, /非 PII.*consumer_reference.*禁止.*Email.*Phone.*证件号/u);
  assert.match(labels, /Email.*Phone.*稳定 Value Tokenization/u);
  assert.match(labels, /consumer_profile_vault_token.*禁止重复 Email\/Phone/u);
  assert.match(labels, /client_ip_address.*user_agent.*trusted_proxy_chain/u);
  assert.match(labels, /不调用 Vault.*不生成 Blind Index\/Key Version.*device identifier/u);
  assert.ok(paymentStartEvidenceIndex >= 0, "PAYMENT_START must capture immutable network evidence");
  assert.ok(riskIndex > paymentStartEvidenceIndex, "Risk evaluation must use the bound PAYMENT_START evidence");
  assert.match(messages[riskIndex].label, /稳定 Email\/Phone Token.*网络原值.*批准维度/u);
  assert.match(messages[riskIndex].label, /禁止外部 API、普通日志、MQ 和通用审计 JSON/u);
  assert.match(labels, /command_idempotency.*operation slot 稳定键/u);
  assert.match(labels, /本地事务崩溃.*重放同一稳定键/u);
  assert.match(labels, /Key 轮换不改变 Token/u);
  assert.match(labels, /共享 Token.*无引用.*Retention\/Legal Hold/u);
});

test("E2E-02 keeps the Operating Entity frozen across retries and requires a new Client business reference after permanent invalidation", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const labels = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-02"].messages
    .map((message) => message.label)
    .join("\n");

  assert.match(labels, /Client 命名空间.*clientOrderId/u);
  assert.match(labels, /无合格 Operating Entity|路由覆盖.*依赖失败/u);
  assert.match(labels, /不创建 Order/u);
  assert.match(labels, /临时 Channel\/MID 不可用.*保留同一 Order/u);
  assert.match(labels, /新 Attempt.*同一冻结 Operating Entity/u);
  assert.match(labels, /OperatingEntityChanged.*永久不可用/u);
  assert.match(labels, /阻止新 Attempt.*活动或 UNKNOWN Attempt/u);
  assert.match(labels, /终止唯一 Payment Intent[\s\S]*取消仍开放的未支付 Order 与 Checkout Session/u);
  assert.match(labels, /新的 clientOrderId.*POST \/api\/v1\/checkouts/u);
  assert.match(labels, /不自动创建替代 Order.*不跨 Operating Entity 重试/u);
  assert.doesNotMatch(labels, /order_snapshot_id|snapshot_schema_version|snapshot_hash/u);
});

test("Finance operational sequences close payout cancellation and material reconciliation under dual control", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const settlementLabels = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-06"].messages
    .map((message) => message.label)
    .join("\n");
  const reconLabels = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-08"].messages
    .map((message) => message.label)
    .join("\n");

  assert.match(settlementLabels, /PAYOUT_CANCEL.*Maker/u);
  assert.match(settlementLabels, /不同 Checker.*同一.*Content Hash/u);
  assert.match(settlementLabels, /PENDING_APPROVAL.*无 Execution.*直接撤回/u);
  assert.match(settlementLabels, /APPROVED.*ON_HOLD.*RETRY_REQUIRED.*PAYOUT_CANCEL/u);
  assert.match(settlementLabels, /全部 Execution.*ABORTED.*权威 FAILED/u);
  assert.match(settlementLabels, /UNKNOWN.*部分借记.*未决.*Match/u);
  assert.match(settlementLabels, /available_cash.*bank_cash_account\.net_balance.*active_payout_cash.*active_tax_cash/u);
  assert.match(settlementLabels, /CREATED Execution.*占用.*cash_required_amount/u);
  assert.match(settlementLabels, /UNKNOWN.*禁止.*替代.*取消/u);
  assert.match(settlementLabels, /权威未扣款.*FAILED.*释放 BANK_CASH.*Owner.*LOCKED.*RETRY_REQUIRED/u);
  assert.match(settlementLabels, /新 Execution.*attempt_no.*execution_key/u);
  assert.match(settlementLabels, /BANK_ACCEPTED.*不等于.*PAID/u);
  assert.match(settlementLabels, /CONFIRMED.*PAYOUT_DEBIT.*PAYOUT_PAID.*RECONCILED.*PAID/u);
  assert.match(settlementLabels, /PAYOUT_RELEASE.*LOCKED.*AVAILABLE/u);
  assert.doesNotMatch(settlementLabels, /Funding|Allocation|Position/u);
  assert.doesNotMatch(settlementLabels, /Settlement Cancel|Settlement Payment|Settlement Attempt|current_attempt_id|active_settlement_cash/u);

  assert.match(reconLabels, /重大.*Recon Resolution.*Maker/u);
  assert.match(reconLabels, /不同 Checker.*同一.*Content Hash/u);
  assert.match(reconLabels, /ROUNDING_ADJUSTMENT.*确定性舍入/u);
  assert.match(reconLabels, /金额变化.*新的 Accounting Instruction.*Journal/u);
  assert.match(reconLabels, /不覆盖.*Exception.*来源证据/u);
  assert.match(reconLabels, /CHANNEL_UNSETTLED_RECEIVABLE.*CHANNEL_SETTLEMENT_RECEIVABLE.*BANK_CASH/u);
  assert.match(reconLabels, /CHANNEL_UNSETTLED_PAYABLE.*CHANNEL_SETTLEMENT_PAYABLE.*BANK_CASH/u);
  assert.match(reconLabels, /最终.*Settlement\/Remittance Batch.*交易对账完整/u);
  assert.match(reconLabels, /FEE\/ADJUSTMENT.*Cost Confirm\/Allocation.*已批异常/u);
  assert.match(reconLabels, /channel_batch_id.*CHANNEL_SETTLEMENT.*payout_execution_id.*PAYOUT.*tax_payment_id.*TAX/u);
  assert.match(reconLabels, /外部 Debit.*容量不足.*不得拒绝.*新余额/u);
  assert.match(reconLabels, /LATE_DEBIT:<bank_item_id>.*RECON_SUSPENSE_ASSET.*BANK_CASH.*LATE_DEBIT/u);
  assert.match(reconLabels, /旧 FAILED Payout Execution.*普通成功.*新 Execution.*现金承诺/u);
  assert.doesNotMatch(reconLabels, /Channel Cash Item|Finance.*查询支付、退款与渠道流水|FAILED Settlement Attempt/u);
});

test("Finance operational sequences perform period close and tax payment with immutable evidence", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const closeLabels = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-09"].messages
    .map((message) => message.label)
    .join("\n");
  const taxSequence = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-12"];
  const taxLabels = taxSequence.messages.map((message) => message.label).join("\n");

  assert.match(closeLabels, /Maker.*CLOSE.*as_of_sequence/u);
  assert.match(closeLabels, /compensation_event.*Instruction.*Journal.*对账/u);
  assert.match(closeLabels, /试算平衡.*全部 OPEN 异常.*损益结转.*重新试算.*close_hash/u);
  assert.match(closeLabels, /不同 Checker.*同一 close_hash/u);
  assert.match(closeLabels, /新的 REOPEN.*原 Close Hash.*不覆盖/u);
  assert.match(closeLabels, /REOPEN.*不同 Maker.*Checker/u);

  assert.ok(taxSequence.participants.includes("operations_console"));
  assert.ok(taxSequence.participants.includes("bank"));
  assert.match(taxLabels, /Tax Liability Summary.*不可变 Tax Return Version/u);
  assert.match(taxLabels, /Tax Return.*Maker[\s\S]*不同 Checker.*同一 Content Hash/u);
  assert.match(taxLabels, /tax_payable_account_id.*TAX_PAYABLE.*BANK_CASH.*t_account/u);
  assert.match(taxLabels, /available_cash.*active_payout_cash.*active_tax_cash/u);
  assert.match(taxLabels, /同一 tax_payable_account_id.*net_balance.*全部.*活动 Tax Payment/u);
  assert.match(taxLabels, /不同 Tax Return.*合计/u);
  assert.match(taxLabels, /UNKNOWN.*active_slot=1.*不释放.*不允许替代/u);
  assert.match(taxLabels, /BANK_CASH t_account.*权威未扣款证据.*FAILED.*释放 active_slot.*保留 Ledger Tax Payable.*新的 payment_no.*execution_key/u);
  assert.match(taxLabels, /银行借记.*完整对账.*Tax Payment.*RECONCILED/u);
  assert.doesNotMatch(taxLabels, /Funding|Commitment|Allocation|Position/u);
});

test("Finance accounting receives no DisputeWon fact", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const accountingMessages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-09"].messages;

  assert.ok(
    !accountingMessages.some(
      (message) => message.from === "risk" && message.to === "finance" && /DisputeWon/u.test(message.label),
    ),
    "DisputeWon must not produce a Finance action",
  );
});

test("dispute loss facts use the security deposit only under the explicit three-part gate", async () => {
  const sequenceCatalog = await loadSequenceCatalog();
  const recoveryMessages = sequenceCatalog.GLOBAL_USE_CASE_SEQUENCES["E2E-11"].messages;
  const labels = recoveryMessages.map((message) => message.label).join("\n");

  assert.ok(
    !recoveryMessages.some(
      (message) => /Dispute(?:Lost|OutcomeCorrected).*(?:未结算余额|PENDING|AVAILABLE)/u.test(message.label),
    ),
    "dispute outcome facts must not consume ordinary unpaid Client balances",
  );
  assert.match(labels, /DisputeLost\s*\/\s*DisputeOutcomeCorrected.*outcome_version.*principal_bearer.*security_deposit_use_allowed/u);
  assert.match(labels, /outcome\s*=\s*LOST.*principal_bearer\s*=\s*CLIENT.*security_deposit_use_allowed\s*=\s*1/u);
  assert.match(labels, /不得.*(?:原因|reason|来源|source|规则).*推导/u);
  assert.match(labels, /CLIENT_SECURITY_DEPOSIT\/NONE.*CLIENT_RECOVERY_RECEIVABLE\/NONE/u);
});
