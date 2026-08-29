/**
 * Design-time executable specification for a complete Risk Dispute aggregate.
 *
 * This module validates normalized fixtures only. It models the invariants that
 * cannot be expressed by portable row CHECKs or single-column PDManer links;
 * it is not the future Java aggregate implementation.
 */

const RESULT_STATUSES = new Set(["WON", "LOST"]);
const INITIAL_DISPOSITION_SOURCES = new Set([
  "CLIENT_DISPOSITION",
  "OPERATIONS_DISPOSITION",
]);
const RESULT_TRIGGER_BY_SOURCE = Object.freeze({
  PROVIDER_ACTION: "PROVIDER_OUTCOME",
  CLIENT_DISPOSITION: "CLIENT_DISPOSITION",
  OPERATIONS_DISPOSITION: "OPERATIONS_DISPOSITION",
});

function isPresent(value) {
  return value !== null && value !== undefined;
}

function sameId(left, right) {
  return isPresent(left) && isPresent(right) && String(left) === String(right);
}

function positiveInteger(value) {
  return Number.isSafeInteger(value) && value > 0;
}

function rows(value) {
  return Array.isArray(value) ? value : [];
}

function addIf(condition, errors, code) {
  if (condition) errors.add(code);
}

function validateRequestPackages(snapshot, errors) {
  const caseId = snapshot.caseRow.disputeCaseId;
  const packagesById = new Map();
  for (const packageRow of rows(snapshot.packages)) {
    packagesById.set(String(packageRow.packageId), packageRow);
  }

  for (const request of rows(snapshot.requests)) {
    const packageRow = packagesById.get(String(request.packageId));
    addIf(
      !packageRow
        || !sameId(request.disputeCaseId, caseId)
        || !sameId(packageRow.disputeCaseId, caseId)
        || !sameId(request.disputeCaseId, packageRow.disputeCaseId),
      errors,
      "REQUEST_PACKAGE_SAME_CASE",
    );
  }
}

function orderedOutcomes(snapshot, errors) {
  const caseId = snapshot.caseRow.disputeCaseId;
  const outcomes = [...rows(snapshot.outcomes)].sort(
    (left, right) => Number(left.outcomeVersion) - Number(right.outcomeVersion),
  );

  outcomes.forEach((outcome, index) => {
    const expectedVersion = index + 1;
    const previous = outcomes[index - 1];
    const validVersion = positiveInteger(outcome.outcomeVersion)
      && outcome.outcomeVersion === expectedVersion;
    const validPredecessor = index === 0
      ? !isPresent(outcome.supersedesOutcomeId)
      : sameId(outcome.supersedesOutcomeId, previous.outcomeId);
    addIf(
      !sameId(outcome.disputeCaseId, caseId) || !validVersion || !validPredecessor,
      errors,
      "OUTCOME_PREDECESSOR_IS_SAME_CASE_PREVIOUS_VERSION",
    );

    if (INITIAL_DISPOSITION_SOURCES.has(outcome.decisionSourceType)) {
      addIf(
        outcome.outcomeVersion !== 1
          || outcome.outcome !== "LOST"
          || isPresent(outcome.supersedesOutcomeId)
          || isPresent(outcome.providerDisputeActionId),
        errors,
        "INITIAL_DISPOSITION_ONLY_CREATES_LOST",
      );
    }

    if (outcome.decisionSourceType === "OPERATIONS_CORRECTION") {
      addIf(
        outcome.outcomeVersion <= 1 || !isPresent(outcome.supersedesOutcomeId),
        errors,
        "CORRECTION_SOURCE_REQUIRES_PREDECESSOR",
      );
    }
    addIf(
      outcome.decisionSourceType === "PROVIDER_ACTION" && outcome.outcomeVersion !== 1,
      errors,
      "CORRECTION_SOURCE_REQUIRES_PREDECESSOR",
    );
  });

  return outcomes;
}

function validateCurrentOutcome(snapshot, outcomes, errors) {
  const { caseRow } = snapshot;
  const tail = outcomes.at(-1);
  const hasPointer = isPresent(caseRow.currentOutcomeId);

  if (!tail) {
    addIf(hasPointer, errors, "CURRENT_OUTCOME_IS_SAME_CASE_CHAIN_TAIL");
    addIf(RESULT_STATUSES.has(caseRow.status) || caseRow.status === "CLOSED", errors, "CASE_RESULT_STATUS_MATCHES_CURRENT_OUTCOME");
    return;
  }

  addIf(
    !sameId(caseRow.currentOutcomeId, tail.outcomeId)
      || !sameId(tail.disputeCaseId, caseRow.disputeCaseId),
    errors,
    "CURRENT_OUTCOME_IS_SAME_CASE_CHAIN_TAIL",
  );
  addIf(
    RESULT_STATUSES.has(caseRow.status) && caseRow.status !== tail.outcome,
    errors,
    "CASE_RESULT_STATUS_MATCHES_CURRENT_OUTCOME",
  );
  addIf(
    !RESULT_STATUSES.has(caseRow.status) && caseRow.status !== "CLOSED",
    errors,
    "CASE_RESULT_STATUS_MATCHES_CURRENT_OUTCOME",
  );
}

function liabilityHasAuthority(liability, outcome) {
  const evaluationPresent = isPresent(liability.decisionEvaluationId);
  const effectPresent = isPresent(liability.actionEffectId);
  const ruleAuthority = evaluationPresent && effectPresent && !isPresent(liability.decidedBy);
  const actorAuthority = !evaluationPresent && !effectPresent && isPresent(liability.decidedBy);
  if (
    INITIAL_DISPOSITION_SOURCES.has(outcome.decisionSourceType)
    || outcome.decisionSourceType === "OPERATIONS_CORRECTION"
  ) return actorAuthority;
  return ruleAuthority || actorAuthority;
}

function ruleEffectMatchesEvaluation(snapshot, liability) {
  const matchingEffects = rows(snapshot.actionEffects).filter(
    (effect) => sameId(effect.effectId, liability.actionEffectId),
  );
  if (matchingEffects.length !== 1) return false;
  const [effect] = matchingEffects;
  return !isPresent(liability.decidedBy)
    && sameId(effect.evaluationId, liability.decisionEvaluationId)
    && effect.effectMode === "REAL"
    && effect.effectStatus === "APPLIED"
    && effect.executionType === "LOCAL_SYNC"
    && effect.executionStatus === "SUCCEEDED";
}

function validateLiabilities(snapshot, outcomes, errors) {
  const outcomesById = new Map(outcomes.map((outcome) => [String(outcome.outcomeId), outcome]));
  const liabilityCount = new Map();

  for (const liability of rows(snapshot.liabilities)) {
    const outcome = outcomesById.get(String(liability.outcomeId));
    if (!outcome) {
      errors.add("EVERY_OUTCOME_HAS_EXACTLY_ONE_LIABILITY");
      continue;
    }
    const outcomeId = String(liability.outcomeId);
    liabilityCount.set(outcomeId, (liabilityCount.get(outcomeId) ?? 0) + 1);

    addIf(
      !liabilityHasAuthority(liability, outcome),
      errors,
      "LIABILITY_REQUIRES_RULE_OR_ACTOR_PROVENANCE",
    );
    addIf(
      isPresent(liability.decisionEvaluationId)
        && isPresent(liability.actionEffectId)
        && !ruleEffectMatchesEvaluation(snapshot, liability),
      errors,
      "LIABILITY_RULE_EFFECT_BELONGS_TO_EVALUATION",
    );

    if (liability.securityDepositUseAllowed === true || liability.securityDepositUseAllowed === 1) {
      addIf(
        outcome.outcome !== "LOST",
        errors,
        "SECURITY_DEPOSIT_PERMISSION_REQUIRES_LOST_OUTCOME",
      );
      addIf(
        liability.principalBearer !== "CLIENT",
        errors,
        "SECURITY_DEPOSIT_PERMISSION_REQUIRES_CLIENT_BEARER",
      );
    }
  }

  for (const outcome of outcomes) {
    addIf(
      liabilityCount.get(String(outcome.outcomeId)) !== 1,
      errors,
      "EVERY_OUTCOME_HAS_EXACTLY_ONE_LIABILITY",
    );
  }
}

function validateInitialResultTransition(stateChanges, outcomes, errors) {
  const initialOutcome = outcomes.find((outcome) => outcome.outcomeVersion === 1);
  const resultChanges = stateChanges.filter((row) => RESULT_STATUSES.has(row.toStatus));
  if (!initialOutcome) {
    addIf(resultChanges.length > 0, errors, "CASE_RESULT_STATUS_MATCHES_CURRENT_OUTCOME");
    return;
  }

  const [resultChange] = resultChanges;
  addIf(
    resultChanges.length !== 1 || resultChange?.toStatus !== initialOutcome.outcome,
    errors,
    "CASE_RESULT_STATUS_MATCHES_CURRENT_OUTCOME",
  );
  if (!resultChange) return;

  const expectedTrigger = RESULT_TRIGGER_BY_SOURCE[initialOutcome.decisionSourceType];
  const sourceCanProduceResult = isPresent(expectedTrigger)
    && (initialOutcome.outcome === "LOST" || initialOutcome.decisionSourceType === "PROVIDER_ACTION");
  if (initialOutcome.outcome === "LOST") {
    addIf(
      !sourceCanProduceResult || resultChange.triggerType !== expectedTrigger,
      errors,
      "LOST_TRANSITION_REQUIRES_AUTHORIZED_SOURCE",
    );
  }

  addIf(
    !sourceCanProduceResult
      || resultChange.triggerType !== expectedTrigger
      || !sameId(resultChange.triggerId, initialOutcome.decisionSourceId)
      || (
        initialOutcome.decisionSourceType === "PROVIDER_ACTION"
        && !sameId(resultChange.triggerId, initialOutcome.providerDisputeActionId)
      ),
    errors,
    "RESULT_TRANSITION_SOURCE_COORDINATE_MATCHES_OUTCOME",
  );
}

function validateClosedCorrections(stateChanges, outcomes, errors) {
  const corrections = outcomes.filter((outcome) => outcome.decisionSourceType === "OPERATIONS_CORRECTION");
  const closedSelfChanges = stateChanges.filter(
    (row) => row.fromStatus === "CLOSED" && row.toStatus === "CLOSED",
  );
  for (const [index, change] of closedSelfChanges.entries()) {
    addIf(
      change.triggerType !== "OPERATIONS_CORRECTION",
      errors,
      "CLOSED_SELF_TRANSITION_REQUIRES_TRUSTED_CORRECTION",
    );
    addIf(
      !sameId(change.triggerId, corrections[index]?.decisionSourceId),
      errors,
      "CORRECTION_TRIGGER_COORDINATE_MATCHES_OUTCOME",
    );
  }

  addIf(
    corrections.length !== closedSelfChanges.length
      || outcomes.slice(1).some((outcome) => outcome.decisionSourceType !== "OPERATIONS_CORRECTION"),
    errors,
    "CLOSED_SELF_TRANSITION_REQUIRES_TRUSTED_CORRECTION",
  );
}

function validateStateChanges(snapshot, outcomes, errors) {
  const { caseRow } = snapshot;
  const stateChanges = [...rows(snapshot.stateChanges)].sort(
    (left, right) => Number(left.aggregateVersion) - Number(right.aggregateVersion),
  );
  let previousStatus = null;

  stateChanges.forEach((change, index) => {
    const expectedVersion = index + 1;
    addIf(
      !sameId(change.disputeCaseId, caseRow.disputeCaseId)
        || !positiveInteger(change.aggregateVersion)
        || change.aggregateVersion !== expectedVersion
        || change.fromStatus !== previousStatus,
      errors,
      "STATE_CHANGE_VERSION_MATCHES_CASE_VERSION",
    );
    previousStatus = change.toStatus;
  });

  addIf(
    !positiveInteger(caseRow.version)
      || stateChanges.length !== caseRow.version
      || stateChanges.at(-1)?.toStatus !== caseRow.status,
    errors,
    "STATE_CHANGE_VERSION_MATCHES_CASE_VERSION",
  );

  validateInitialResultTransition(stateChanges, outcomes, errors);
  validateClosedCorrections(stateChanges, outcomes, errors);
}

export function collectDisputeAggregateErrors(snapshot) {
  const errors = new Set();
  if (!snapshot || typeof snapshot !== "object" || !snapshot.caseRow) {
    return ["INVALID_DISPUTE_AGGREGATE_SNAPSHOT"];
  }

  validateRequestPackages(snapshot, errors);
  const outcomes = orderedOutcomes(snapshot, errors);
  validateCurrentOutcome(snapshot, outcomes, errors);
  validateLiabilities(snapshot, outcomes, errors);
  validateStateChanges(snapshot, outcomes, errors);
  return [...errors].sort();
}

export function validateDisputeAggregate(snapshot) {
  const errors = collectDisputeAggregateErrors(snapshot);
  if (errors.length > 0) {
    throw new Error(`Invalid Dispute aggregate: ${errors.join(", ")}`);
  }
}
