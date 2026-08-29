import assert from "node:assert/strict";
import test from "node:test";

const HASH_A = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
const HASH_B = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
const EXPECTED_CANONICAL = `{"disputeCaseId":"720001","evidence":[{"contentHash":"${HASH_A}","evidenceType":"RECEIPT","fileId":"710101"},{"contentHash":"${HASH_A}","evidenceType":"TERMS","fileId":"2"},{"contentHash":"${HASH_B}","evidenceType":"TERMS","fileId":"10"}],"schema":"CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1","versionNo":1}`;
const EXPECTED_HASH = "547e636e0c6d5762f70d0604e95d0df145f66f4fcf2619032190029503abb433";

async function loadCanonicalizer() {
  return import("./risk/dispute-evidence-package-canonicalizer.mjs");
}

function validPackage() {
  return {
    disputeCaseId: "720001",
    evidence: [
      { contentHash: HASH_B, evidenceType: "TERMS", fileId: "10" },
      { contentHash: HASH_A, evidenceType: "RECEIPT", fileId: "710101" },
      { contentHash: HASH_A, evidenceType: "TERMS", fileId: "2" },
    ],
    versionNo: 1,
  };
}

function deepFreeze(value) {
  if (value === null || typeof value !== "object") return value;
  for (const child of Object.values(value)) deepFreeze(child);
  return Object.freeze(value);
}

test("Dispute Evidence Package canonical identity is order-independent and byte-stable", async () => {
  const { canonicalizeDisputeEvidencePackage, hashDisputeEvidencePackage } = await loadCanonicalizer();
  const input = validPackage();
  const original = structuredClone(input);
  deepFreeze(input);

  assert.equal(canonicalizeDisputeEvidencePackage(input), EXPECTED_CANONICAL);
  assert.equal(hashDisputeEvidencePackage(input), EXPECTED_HASH);
  assert.equal(
    canonicalizeDisputeEvidencePackage({ ...input, evidence: input.evidence.toReversed() }),
    EXPECTED_CANONICAL,
  );
  assert.deepEqual(input, original, "canonicalization must not mutate caller-owned evidence rows");
});

test("Dispute Evidence Package preserves the approved one-row golden vector", async () => {
  const { canonicalizeDisputeEvidencePackage, hashDisputeEvidencePackage } = await loadCanonicalizer();
  const input = {
    disputeCaseId: "720001",
    evidence: [{ contentHash: HASH_A, evidenceType: "RECEIPT", fileId: "710101" }],
    versionNo: 1,
  };
  assert.equal(
    canonicalizeDisputeEvidencePackage(input),
    `{"disputeCaseId":"720001","evidence":[{"contentHash":"${HASH_A}","evidenceType":"RECEIPT","fileId":"710101"}],"schema":"CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1","versionNo":1}`,
  );
  assert.equal(
    hashDisputeEvidencePackage(input),
    "eeb0a7cb32c7aabadb3c30f57caa7ba3ec05cf82de2e633aafe4c1e4eeada177",
  );
});

test("Dispute Evidence Package canonical identity rejects open or malformed shapes", async () => {
  const { canonicalizeDisputeEvidencePackage } = await loadCanonicalizer();
  const sparseEvidence = [];
  sparseEvidence[1] = validPackage().evidence[0];
  const invalidInputs = [
    { ...validPackage(), unknown: true },
    { ...validPackage(), disputeCaseId: "0720001" },
    { ...validPackage(), disputeCaseId: "9223372036854775808" },
    { ...validPackage(), versionNo: 0 },
    { ...validPackage(), versionNo: 2147483648 },
    { ...validPackage(), evidence: [] },
    { ...validPackage(), evidence: sparseEvidence },
    { ...validPackage(), evidence: [{ ...validPackage().evidence[0], unknown: true }] },
    { ...validPackage(), evidence: [{ ...validPackage().evidence[0], fileId: "0" }] },
    { ...validPackage(), evidence: [{ ...validPackage().evidence[0], evidenceType: "receipt" }] },
    { ...validPackage(), evidence: [{ ...validPackage().evidence[0], contentHash: HASH_A.toUpperCase() }] },
    {
      ...validPackage(),
      evidence: [validPackage().evidence[0], { ...validPackage().evidence[0], contentHash: HASH_A }],
    },
  ];

  for (const invalid of invalidInputs) {
    assert.throws(() => canonicalizeDisputeEvidencePackage(invalid), TypeError);
  }
});
