import { createHash } from "node:crypto";

const CANONICAL_POSITIVE_DECIMAL = /^[1-9][0-9]*$/;

function requireRecord(value, path) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError(`${path} must be an object`);
  }
  return value;
}

function requireCanonicalId(value, path) {
  if (typeof value !== "string" || !CANONICAL_POSITIVE_DECIMAL.test(value)) {
    throw new TypeError(`${path} must be a canonical positive decimal string`);
  }
  return value;
}

function requireUnicodeScalarString(value, path) {
  if (typeof value !== "string" || value.length === 0) {
    throw new TypeError(`${path} must be a non-empty string`);
  }
  for (let index = 0; index < value.length; index += 1) {
    const codeUnit = value.charCodeAt(index);
    if (codeUnit >= 0xd800 && codeUnit <= 0xdbff) {
      const next = value.charCodeAt(index + 1);
      if (!(next >= 0xdc00 && next <= 0xdfff)) {
        throw new TypeError(`${path} must contain Unicode scalar values only`);
      }
      index += 1;
      continue;
    }
    if (codeUnit >= 0xdc00 && codeUnit <= 0xdfff) {
      throw new TypeError(`${path} must contain Unicode scalar values only`);
    }
  }
  return value;
}

function requireSequence(value, path) {
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new TypeError(`${path} must be a positive safe integer`);
  }
  return value;
}

function canonicalSourceIdentity(row, path) {
  if (row.source_type === "RULE" && row.obligation_id === null) {
    return {
      obligation_id: null,
      rule_action_id: requireCanonicalId(row.rule_action_id, `${path}.rule_action_id`),
      source_type: "RULE",
    };
  }
  if (row.source_type === "OBLIGATION" && row.rule_action_id === null) {
    return {
      obligation_id: requireCanonicalId(row.obligation_id, `${path}.obligation_id`),
      rule_action_id: null,
      source_type: "OBLIGATION",
    };
  }
  throw new TypeError(`${path} must contain exactly one typed source identity`);
}

function canonicalContributor(row, index, effect) {
  const path = `contributors[${index}]`;
  requireRecord(row, path);
  const source = canonicalSourceIdentity(row, path);
  const resolvedTargetKey = requireUnicodeScalarString(row.resolved_target_key, `${path}.resolved_target_key`);
  const resolvedTargetType = requireUnicodeScalarString(row.resolved_target_type, `${path}.resolved_target_type`);
  if (resolvedTargetKey !== effect.target_key || resolvedTargetType !== effect.target_type) {
    throw new TypeError(`${path} target must equal the persisted Effect target`);
  }
  return {
    intent_id: requireCanonicalId(row.intent_id, `${path}.intent_id`),
    intent_sequence: requireSequence(row.intent_sequence, `${path}.intent_sequence`),
    obligation_id: source.obligation_id,
    resolved_target_key: resolvedTargetKey,
    resolved_target_type: resolvedTargetType,
    rule_action_id: source.rule_action_id,
    source_type: source.source_type,
  };
}

function canonicalEffect(effect) {
  requireRecord(effect, "effect");
  return {
    effect_id: requireCanonicalId(effect.effect_id, "effect.effect_id"),
    identity: {
      action_version_id: requireCanonicalId(effect.action_version_id, "effect.action_version_id"),
      effect_mode: requireUnicodeScalarString(effect.effect_mode, "effect.effect_mode"),
      evaluation_id: requireCanonicalId(effect.evaluation_id, "effect.evaluation_id"),
      merge_policy: requireUnicodeScalarString(effect.merge_policy, "effect.merge_policy"),
      target_key: requireUnicodeScalarString(effect.target_key, "effect.target_key"),
      target_type: requireUnicodeScalarString(effect.target_type, "effect.target_type"),
    },
  };
}

export function canonicalizeActionEffectIdentity(effectRow, intentRows) {
  const effect = canonicalEffect(effectRow);
  if (!Array.isArray(intentRows)) throw new TypeError("contributors must be an array");

  const contributors = intentRows
    .filter((row) => row?.status === "MERGED" && row?.merged_effect_id === effect.effect_id)
    .map((row, index) => canonicalContributor(row, index, effect.identity))
    .sort((left, right) => (
      left.intent_sequence - right.intent_sequence
      || (BigInt(left.intent_id) < BigInt(right.intent_id) ? -1 : BigInt(left.intent_id) > BigInt(right.intent_id) ? 1 : 0)
    ));
  if (contributors.length === 0) {
    throw new TypeError("effect must have at least one MERGED contributor");
  }

  // Keys are deliberately emitted in Unicode code-point order. For this closed
  // scalar-only schema JSON.stringify therefore produces RFC 8785 JCS bytes.
  return JSON.stringify({
    contributors,
    effect: effect.identity,
    schema: "RISK_ACTION_EFFECT_V1",
  });
}

export function hashActionEffectIdentity(effectRow, intentRows) {
  return createHash("sha256")
    .update(canonicalizeActionEffectIdentity(effectRow, intentRows), "utf8")
    .digest("hex");
}
