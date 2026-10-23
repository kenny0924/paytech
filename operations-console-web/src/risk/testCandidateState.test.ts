import assert from 'node:assert/strict';
import test from 'node:test';
import type { TestCandidateDetail } from './riskApiModels.ts';
import { candidateDetail } from './riskFixtures.ts';
import { decodeCandidateDetail } from './riskResponseValidation.ts';
import { classifyTestCandidate } from './testCandidateState.ts';

test('decoded DRAFT is ready with expectedVersion 1 for creator and another current actor', () => {
  const detail = decodeCandidateDetail(candidateDetail('draft'));
  for (const actor of ['3', '9']) assert.deepEqual(classifyTestCandidate(detail, actor), { kind: 'READY_TO_VALIDATE', expectedVersion: 1 });
});

test('only the exact actor on validation start can reconstruct the original version 1 recovery command', () => {
  const detail = decodeCandidateDetail(candidateDetail('pending'));
  assert.deepEqual(classifyTestCandidate(detail, '9'), { kind: 'RECOVERABLE_BY_CURRENT_ACTOR', expectedVersion: 1, key: 'risk-validate-v1-4-1' });
  for (const actor of ['3', '99', '09', '9 ', '']) {
    assert.deepEqual(classifyTestCandidate(detail, actor), { kind: 'RECOVERY_OWNED_BY_OTHER_ACTOR', ownerActorId: '9' });
  }
});

test('recovery ownership compares large account IDs as exact strings', () => {
  const ownerActorId = '9007199254740993';
  const wire = candidateDetail('pending');
  wire.stateChanges[1]!.actorId = ownerActorId;
  const detail = decodeCandidateDetail(wire);
  assert.deepEqual(classifyTestCandidate(detail, ownerActorId), { kind: 'RECOVERABLE_BY_CURRENT_ACTOR', expectedVersion: 1, key: 'risk-validate-v1-4-1' });
  assert.deepEqual(classifyTestCandidate(detail, '9007199254740992'), { kind: 'RECOVERY_OWNED_BY_OTHER_ACTOR', ownerActorId });
});

for (const state of ['passed', 'rejected'] as const) {
  test(`decoded ${state} exposes eight ordered checks without an executable recovery action`, () => {
    const detail = decodeCandidateDetail(candidateDetail(state));
    const before = structuredClone(detail);
    for (const actor of ['9', '3']) {
      const result = classifyTestCandidate(detail, actor);
      assert.deepEqual(result, { kind: state === 'passed' ? 'WAITING_FOR_SHADOW' : 'REJECTED', checks: detail.staticChecks });
      assert.ok('checks' in result);
      assert.deepEqual(result.checks.map(check => check.checkCode), [
        'REFERENCE_INTEGRITY', 'AST_SHAPE', 'AST_TYPES', 'FEATURE_DEPENDENCY',
        'HANDLER_HASH', 'ACTION_PARAMETERS', 'TERMINAL_ACTION', 'SYSTEM_OBLIGATION',
      ]);
      assert.deepEqual(result.checks.map(check => check.checkStatus), state === 'passed'
        ? ['PASSED', 'PASSED', 'PASSED', 'PASSED', 'PASSED', 'PASSED', 'PASSED', 'PASSED']
        : ['FAILED', 'PASSED', 'PASSED', 'PASSED', 'PASSED', 'PASSED', 'PASSED', 'PASSED']);
      assert.equal('key' in result, false);
      assert.equal('expectedVersion' in result, false);
    }
    assert.deepEqual(detail, before);
  });
}

test('classification leaves decoded draft and pending facts unchanged', () => {
  for (const state of ['draft', 'pending'] as const) {
    const detail = decodeCandidateDetail(candidateDetail(state));
    const before = structuredClone(detail);
    classifyTestCandidate(detail, '9');
    classifyTestCandidate(detail, '3');
    assert.deepEqual(detail, before);
  }
});

test('an impossible internal state throws a fixed invariant error without disclosing its data', () => {
  // Deliberately bypass the response decoder: malformed Owner responses are tested at that boundary.
  const impossible = { ...candidateDetail(), status: 'PRIVATE_UNREACHABLE_STATE', releaseId: 'PRIVATE_RELEASE', contentHash: 'PRIVATE_HASH' } as unknown as TestCandidateDetail;
  assert.throws(() => classifyTestCandidate(impossible, 'PRIVATE_ACTOR'), error => {
    assert.ok(error instanceof Error);
    assert.equal(error.message, "The candidate version status isn't recognized. Refresh and try again.");
    assert.equal('cause' in error, false);
    assert.equal('status' in error, false);
    assert.doesNotMatch(String(error), /PRIVATE/);
    return true;
  });
});
