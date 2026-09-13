import assert from "node:assert/strict";
import test from "node:test";

async function loadValidator() {
  return import("./risk/dispute-aggregate-validator.mjs");
}

function validSnapshot() {
  return {
    caseRow: {
      disputeCaseId: "720001",
      currentOutcomeId: "720051",
      status: "LOST",
      version: 4,
    },
    packages: [{ packageId: "720021", disputeCaseId: "720001" }],
    requests: [{
      requestId: "720041",
      disputeCaseId: "720001",
      packageId: "720021",
      status: "SUCCEEDED",
      activeSlot: null,
    }],
    outcomes: [{
      outcomeId: "720051",
      disputeCaseId: "720001",
      outcomeVersion: 1,
      outcome: "LOST",
      decisionSourceType: "CHANNEL_ACTION",
      decisionSourceId: "730001",
      channelDisputeActionId: "730001",
      sourceVersion: 4,
      supersedesOutcomeId: null,
    }],
    liabilities: [{
      outcomeId: "720051",
      decisionEvaluationId: "310030",
      actionEffectId: "315030",
      decidedBy: null,
      principalBearer: "CLIENT",
      securityDepositUseAllowed: true,
    }],
    actionEffects: [{
      effectId: "315030",
      evaluationId: "310030",
      effectMode: "REAL",
      effectStatus: "APPLIED",
      executionType: "LOCAL_SYNC",
      executionStatus: "SUCCEEDED",
    }],
    stateChanges: [
      { disputeCaseId: "720001", fromStatus: null, toStatus: "EVIDENCE_REQUIRED", triggerType: "CHANNEL_NOTICE", triggerId: "710001", aggregateVersion: 1 },
      { disputeCaseId: "720001", fromStatus: "EVIDENCE_REQUIRED", toStatus: "UNDER_REVIEW", triggerType: "CLIENT_EVIDENCE", triggerId: "720021", aggregateVersion: 2 },
      { disputeCaseId: "720001", fromStatus: "UNDER_REVIEW", toStatus: "SUBMITTED", triggerType: "SUBMISSION_REQUEST", triggerId: "720041", aggregateVersion: 3 },
      { disputeCaseId: "720001", fromStatus: "SUBMITTED", toStatus: "LOST", triggerType: "CHANNEL_OUTCOME", triggerId: "730001", aggregateVersion: 4 },
    ],
  };
}

function clonedSnapshot() {
  return structuredClone(validSnapshot());
}

test("Dispute aggregate accepts a verified Channel LOST with rule provenance", async () => {
  const { collectDisputeAggregateErrors, validateDisputeAggregate } = await loadValidator();
  const snapshot = validSnapshot();
  assert.deepEqual(collectDisputeAggregateErrors(snapshot), []);
  assert.doesNotThrow(() => validateDisputeAggregate(snapshot));
});

test("Dispute aggregate accepts a verified Channel WON without deposit permission", async () => {
  const { collectDisputeAggregateErrors } = await loadValidator();
  const snapshot = clonedSnapshot();
  snapshot.outcomes[0].outcome = "WON";
  snapshot.liabilities[0].principalBearer = "CHANNEL";
  snapshot.liabilities[0].securityDepositUseAllowed = false;
  snapshot.stateChanges.at(-1).toStatus = "WON";
  snapshot.caseRow.status = "WON";
  assert.deepEqual(collectDisputeAggregateErrors(snapshot), []);
});

test("Dispute aggregate accepts explicit Client and Operations LOST dispositions", async () => {
  const { collectDisputeAggregateErrors } = await loadValidator();
  for (const sourceType of ["CLIENT_DISPOSITION", "OPERATIONS_DISPOSITION"]) {
    const snapshot = clonedSnapshot();
    snapshot.outcomes[0].decisionSourceType = sourceType;
    snapshot.outcomes[0].decisionSourceId = sourceType === "CLIENT_DISPOSITION" ? "740001" : "740002";
    snapshot.outcomes[0].channelDisputeActionId = null;
    snapshot.liabilities[0].decisionEvaluationId = null;
    snapshot.liabilities[0].actionEffectId = null;
    snapshot.liabilities[0].decidedBy = "900001";
    snapshot.stateChanges.at(-1).triggerType = sourceType;
    snapshot.stateChanges.at(-1).triggerId = snapshot.outcomes[0].decisionSourceId;
    assert.deepEqual(collectDisputeAggregateErrors(snapshot), []);
  }
});

test("Dispute aggregate accepts an authorized Operations correction after close", async () => {
  const { collectDisputeAggregateErrors } = await loadValidator();
  const snapshot = clonedSnapshot();
  snapshot.outcomes.push({
    outcomeId: "720052",
    disputeCaseId: "720001",
    outcomeVersion: 2,
    outcome: "WON",
    decisionSourceType: "OPERATIONS_CORRECTION",
    decisionSourceId: "740002",
    channelDisputeActionId: "730002",
    sourceVersion: 1,
    supersedesOutcomeId: "720051",
  });
  snapshot.liabilities.push({
    outcomeId: "720052",
    decisionEvaluationId: null,
    actionEffectId: null,
    decidedBy: "900002",
    principalBearer: "PLATFORM",
    securityDepositUseAllowed: false,
  });
  snapshot.stateChanges.push({
    disputeCaseId: "720001",
    fromStatus: "LOST",
    toStatus: "CLOSED",
    triggerType: "CASE_CLOSE_COMMAND",
    triggerId: "risk-command:720001-close",
    aggregateVersion: 5,
  });
  snapshot.stateChanges.push({
    disputeCaseId: "720001",
    fromStatus: "CLOSED",
    toStatus: "CLOSED",
    triggerType: "OPERATIONS_CORRECTION",
    triggerId: "740002",
    aggregateVersion: 6,
  });
  snapshot.caseRow.currentOutcomeId = "720052";
  snapshot.caseRow.status = "CLOSED";
  snapshot.caseRow.version = 6;
  assert.deepEqual(collectDisputeAggregateErrors(snapshot), []);

  snapshot.liabilities[1].decisionEvaluationId = "310031";
  snapshot.liabilities[1].actionEffectId = "315031";
  snapshot.actionEffects.push({
    effectId: "315031",
    evaluationId: "310031",
    effectMode: "REAL",
    effectStatus: "APPLIED",
    executionType: "LOCAL_SYNC",
    executionStatus: "SUCCEEDED",
  });
  assert.ok(collectDisputeAggregateErrors(snapshot).includes("LIABILITY_REQUIRES_RULE_OR_ACTOR_PROVENANCE"));
});

test("Dispute initial dispositions may only create an explicitly authorized LOST", async () => {
  const { collectDisputeAggregateErrors } = await loadValidator();
  for (const sourceType of ["CLIENT_DISPOSITION", "OPERATIONS_DISPOSITION"]) {
    const won = clonedSnapshot();
    won.outcomes[0].decisionSourceType = sourceType;
    won.outcomes[0].decisionSourceId = "740001";
    won.outcomes[0].channelDisputeActionId = null;
    won.outcomes[0].outcome = "WON";
    won.caseRow.status = "WON";
    won.liabilities[0].securityDepositUseAllowed = false;
    won.liabilities[0].decisionEvaluationId = null;
    won.liabilities[0].actionEffectId = null;
    won.liabilities[0].decidedBy = "900001";
    assert.ok(collectDisputeAggregateErrors(won).includes("INITIAL_DISPOSITION_ONLY_CREATES_LOST"));

    const mixedAuthority = clonedSnapshot();
    mixedAuthority.outcomes[0].decisionSourceType = sourceType;
    mixedAuthority.outcomes[0].decisionSourceId = "740001";
    mixedAuthority.outcomes[0].channelDisputeActionId = null;
    mixedAuthority.liabilities[0].decidedBy = "900001";
    mixedAuthority.stateChanges.at(-1).triggerType = sourceType;
    mixedAuthority.stateChanges.at(-1).triggerId = "740001";
    assert.ok(collectDisputeAggregateErrors(mixedAuthority).includes("LIABILITY_REQUIRES_RULE_OR_ACTOR_PROVENANCE"));
  }
});

test("Dispute correction requires a direct predecessor and a trusted CLOSED self-transition", async () => {
  const { collectDisputeAggregateErrors } = await loadValidator();
  const invalidInitialCorrection = clonedSnapshot();
  invalidInitialCorrection.outcomes[0].decisionSourceType = "OPERATIONS_CORRECTION";
  invalidInitialCorrection.outcomes[0].decisionSourceId = "740002";
  invalidInitialCorrection.outcomes[0].channelDisputeActionId = null;
  assert.ok(collectDisputeAggregateErrors(invalidInitialCorrection).includes("CORRECTION_SOURCE_REQUIRES_PREDECESSOR"));

  const channelCorrection = clonedSnapshot();
  channelCorrection.outcomes[0].outcomeVersion = 2;
  channelCorrection.outcomes[0].supersedesOutcomeId = "720050";
  assert.ok(collectDisputeAggregateErrors(channelCorrection).includes("CORRECTION_SOURCE_REQUIRES_PREDECESSOR"));

  const untrustedClosedSelf = clonedSnapshot();
  untrustedClosedSelf.caseRow.status = "CLOSED";
  untrustedClosedSelf.stateChanges.push({
    disputeCaseId: "720001",
    fromStatus: "LOST",
    toStatus: "CLOSED",
    triggerType: "CASE_CLOSE_COMMAND",
    triggerId: "risk-command:720001-close",
    aggregateVersion: 5,
  });
  untrustedClosedSelf.stateChanges.push({
    disputeCaseId: "720001",
    fromStatus: "CLOSED",
    toStatus: "CLOSED",
    triggerType: "SYSTEM_TIMER",
    triggerId: "risk-timer:720001",
    aggregateVersion: 6,
  });
  untrustedClosedSelf.caseRow.version = 6;
  assert.ok(collectDisputeAggregateErrors(untrustedClosedSelf).includes("CLOSED_SELF_TRANSITION_REQUIRES_TRUSTED_CORRECTION"));
});

test("Dispute LOST transition rejects timer or silence triggers", async () => {
  const { collectDisputeAggregateErrors } = await loadValidator();
  for (const triggerType of ["SYSTEM_TIMER", "CLIENT_SILENCE", "DEADLINE_EXPIRED"]) {
    const snapshot = clonedSnapshot();
    snapshot.stateChanges.at(-1).triggerType = triggerType;
    assert.ok(collectDisputeAggregateErrors(snapshot).includes("LOST_TRANSITION_REQUIRES_AUTHORIZED_SOURCE"));
  }

  const wrongSourceCoordinate = clonedSnapshot();
  wrongSourceCoordinate.stateChanges.at(-1).triggerId = "730999";
  assert.ok(collectDisputeAggregateErrors(wrongSourceCoordinate).includes("RESULT_TRANSITION_SOURCE_COORDINATE_MATCHES_OUTCOME"));
});

test("Dispute first result state must match the V1 Outcome even after Case close", async () => {
  const { collectDisputeAggregateErrors } = await loadValidator();
  const snapshot = clonedSnapshot();
  snapshot.stateChanges.at(-1).toStatus = "WON";
  snapshot.stateChanges.push({
    disputeCaseId: "720001",
    fromStatus: "WON",
    toStatus: "CLOSED",
    triggerType: "CASE_CLOSE_COMMAND",
    triggerId: "risk-command:720001-close",
    aggregateVersion: 5,
  });
  snapshot.caseRow.status = "CLOSED";
  snapshot.caseRow.version = 5;
  assert.ok(collectDisputeAggregateErrors(snapshot).includes("CASE_RESULT_STATUS_MATCHES_CURRENT_OUTCOME"));
});

test("Dispute correction Trigger ID must bind each Operations correction source in version order", async () => {
  const { collectDisputeAggregateErrors } = await loadValidator();
  const snapshot = clonedSnapshot();
  snapshot.outcomes.push({
    outcomeId: "720052",
    disputeCaseId: "720001",
    outcomeVersion: 2,
    outcome: "WON",
    decisionSourceType: "OPERATIONS_CORRECTION",
    decisionSourceId: "740002",
    channelDisputeActionId: "730002",
    sourceVersion: 1,
    supersedesOutcomeId: "720051",
  });
  snapshot.liabilities.push({
    outcomeId: "720052",
    decisionEvaluationId: null,
    actionEffectId: null,
    decidedBy: "900002",
    principalBearer: "PLATFORM",
    securityDepositUseAllowed: false,
  });
  snapshot.stateChanges.push({ disputeCaseId: "720001", fromStatus: "LOST", toStatus: "CLOSED", triggerType: "CASE_CLOSE_COMMAND", triggerId: "risk-command:720001-close", aggregateVersion: 5 });
  snapshot.stateChanges.push({ disputeCaseId: "720001", fromStatus: "CLOSED", toStatus: "CLOSED", triggerType: "OPERATIONS_CORRECTION", triggerId: "unrelated-command", aggregateVersion: 6 });
  snapshot.caseRow.currentOutcomeId = "720052";
  snapshot.caseRow.status = "CLOSED";
  snapshot.caseRow.version = 6;
  assert.ok(collectDisputeAggregateErrors(snapshot).includes("CORRECTION_TRIGGER_COORDINATE_MATCHES_OUTCOME"));

  snapshot.outcomes.push({
    outcomeId: "720053",
    disputeCaseId: "720001",
    outcomeVersion: 3,
    outcome: "LOST",
    decisionSourceType: "OPERATIONS_CORRECTION",
    decisionSourceId: "740003",
    channelDisputeActionId: "730003",
    sourceVersion: 1,
    supersedesOutcomeId: "720052",
  });
  snapshot.liabilities.push({
    outcomeId: "720053",
    decisionEvaluationId: null,
    actionEffectId: null,
    decidedBy: "900003",
    principalBearer: "CLIENT",
    securityDepositUseAllowed: false,
  });
  snapshot.stateChanges.at(-1).triggerId = "740003";
  snapshot.stateChanges.push({ disputeCaseId: "720001", fromStatus: "CLOSED", toStatus: "CLOSED", triggerType: "OPERATIONS_CORRECTION", triggerId: "740002", aggregateVersion: 7 });
  snapshot.caseRow.currentOutcomeId = "720053";
  snapshot.caseRow.version = 7;
  assert.ok(collectDisputeAggregateErrors(snapshot).includes("CORRECTION_TRIGGER_COORDINATE_MATCHES_OUTCOME"));
});

test("Dispute deposit permission requires LOST Client liability and frozen authority", async () => {
  const { collectDisputeAggregateErrors } = await loadValidator();
  const noAuthority = clonedSnapshot();
  noAuthority.liabilities[0].decisionEvaluationId = null;
  noAuthority.liabilities[0].actionEffectId = null;
  assert.ok(collectDisputeAggregateErrors(noAuthority).includes("LIABILITY_REQUIRES_RULE_OR_ACTOR_PROVENANCE"));

  const won = clonedSnapshot();
  won.outcomes[0].outcome = "WON";
  won.caseRow.status = "WON";
  assert.ok(collectDisputeAggregateErrors(won).includes("SECURITY_DEPOSIT_PERMISSION_REQUIRES_LOST_OUTCOME"));

  const platformBearer = clonedSnapshot();
  platformBearer.liabilities[0].principalBearer = "PLATFORM";
  assert.ok(collectDisputeAggregateErrors(platformBearer).includes("SECURITY_DEPOSIT_PERMISSION_REQUIRES_CLIENT_BEARER"));
});

test("Dispute rule Liability binds a successful local Effect to the same Evaluation", async () => {
  const { collectDisputeAggregateErrors } = await loadValidator();
  for (const mutate of [
    (snapshot) => { snapshot.actionEffects = []; },
    (snapshot) => { snapshot.actionEffects.push(structuredClone(snapshot.actionEffects[0])); },
    (snapshot) => { snapshot.liabilities[0].decidedBy = "900001"; },
    (snapshot) => { snapshot.actionEffects[0].evaluationId = "310999"; },
    (snapshot) => { snapshot.actionEffects[0].effectMode = "SIMULATED"; },
    (snapshot) => { snapshot.actionEffects[0].effectStatus = "PENDING_ASYNC"; },
    (snapshot) => { snapshot.actionEffects[0].executionType = "MQ_PUBLISH"; },
    (snapshot) => { snapshot.actionEffects[0].executionStatus = "FAILED"; },
  ]) {
    const snapshot = clonedSnapshot();
    mutate(snapshot);
    assert.ok(collectDisputeAggregateErrors(snapshot).includes("LIABILITY_RULE_EFFECT_BELONGS_TO_EVALUATION"));
  }
});

test("Dispute current pointer, status, version history and same-Case references stay coherent", async () => {
  const { collectDisputeAggregateErrors } = await loadValidator();
  const invalid = clonedSnapshot();
  invalid.requests[0].disputeCaseId = "720999";
  invalid.caseRow.currentOutcomeId = "720999";
  invalid.caseRow.status = "WON";
  invalid.caseRow.version = 8;
  const errors = collectDisputeAggregateErrors(invalid);
  for (const code of [
    "REQUEST_PACKAGE_SAME_CASE",
    "CURRENT_OUTCOME_IS_SAME_CASE_CHAIN_TAIL",
    "CASE_RESULT_STATUS_MATCHES_CURRENT_OUTCOME",
    "STATE_CHANGE_VERSION_MATCHES_CASE_VERSION",
  ]) assert.ok(errors.includes(code), `missing ${code}`);
});
