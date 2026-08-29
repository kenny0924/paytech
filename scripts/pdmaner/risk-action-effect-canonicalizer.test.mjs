import assert from "node:assert/strict";
import test from "node:test";
import {
  canonicalizeActionEffectIdentity,
  hashActionEffectIdentity,
} from "./risk/action-effect-canonicalizer.mjs";

function effectFixture() {
  return {
    target_type: "CONSUMER",
    effect_id: "315001",
    merge_policy: "MAX",
    evaluation_id: "310001",
    target_key: "consumer:1001",
    effect_mode: "REAL",
    action_version_id: "240011",
    effect_status: "CREATED",
  };
}

function contributorFixtures() {
  return [
    {
      status: "MERGED",
      merged_effect_id: "315001",
      intent_id: "314002",
      intent_sequence: 2,
      source_type: "OBLIGATION",
      rule_action_id: null,
      obligation_id: "250101",
      resolved_target_type: "CONSUMER",
      resolved_target_key: "consumer:1001",
    },
    {
      resolved_target_key: "consumer:1001",
      source_type: "RULE",
      intent_sequence: 1,
      obligation_id: null,
      merged_effect_id: "315001",
      status: "MERGED",
      resolved_target_type: "CONSUMER",
      intent_id: "314001",
      rule_action_id: "242001",
    },
    {
      status: "SUPPRESSED",
      merged_effect_id: null,
      intent_id: "314003",
      intent_sequence: 3,
      source_type: "RULE",
      rule_action_id: "242002",
      obligation_id: null,
      resolved_target_type: "CONSUMER",
      resolved_target_key: "consumer:1001",
    },
  ];
}

test("Risk Action Effect V1 canonical identity is byte-stable and database-recomputable", () => {
  const effect = effectFixture();
  const contributors = contributorFixtures();
  const expected = "{\"contributors\":[{\"intent_id\":\"314001\",\"intent_sequence\":1,\"obligation_id\":null,\"resolved_target_key\":\"consumer:1001\",\"resolved_target_type\":\"CONSUMER\",\"rule_action_id\":\"242001\",\"source_type\":\"RULE\"},{\"intent_id\":\"314002\",\"intent_sequence\":2,\"obligation_id\":\"250101\",\"resolved_target_key\":\"consumer:1001\",\"resolved_target_type\":\"CONSUMER\",\"rule_action_id\":null,\"source_type\":\"OBLIGATION\"}],\"effect\":{\"action_version_id\":\"240011\",\"effect_mode\":\"REAL\",\"evaluation_id\":\"310001\",\"merge_policy\":\"MAX\",\"target_key\":\"consumer:1001\",\"target_type\":\"CONSUMER\"},\"schema\":\"RISK_ACTION_EFFECT_V1\"}";

  assert.equal(canonicalizeActionEffectIdentity(effect, contributors), expected);
  assert.equal(
    canonicalizeActionEffectIdentity({ ...effect }, contributors.toReversed()),
    expected,
    "对象属性和输入行顺序不得改变 Canonical bytes",
  );
  assert.match(hashActionEffectIdentity(effect, contributors), /^[0-9a-f]{64}$/);
  assert.equal(
    hashActionEffectIdentity(effect, contributors),
    hashActionEffectIdentity({ ...effect }, contributors.toReversed()),
  );

  const changed = { ...effect, action_version_id: "240012" };
  assert.notEqual(hashActionEffectIdentity(changed, contributors), hashActionEffectIdentity(effect, contributors));
});

test("Risk Action Effect V1 rejects noncanonical IDs and invalid source XOR", () => {
  const paddedId = contributorFixtures();
  paddedId[0].intent_id = "0314002";
  assert.throws(
    () => canonicalizeActionEffectIdentity(effectFixture(), paddedId),
    /canonical positive decimal string/,
  );

  const invalidSource = contributorFixtures();
  invalidSource[1].obligation_id = "250102";
  assert.throws(
    () => canonicalizeActionEffectIdentity(effectFixture(), invalidSource),
    /source identity/,
  );
});
