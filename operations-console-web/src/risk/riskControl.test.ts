import test from 'node:test';
import assert from 'node:assert/strict';
import { activationNeedsAcknowledgement, createExecutionReleaseApi, EXECUTION_POINTS, createRiskListApi, decodeExecutionItem, decodeExecutionRelease, decodeRiskListEntry, entryFilters, releaseActions, releaseStatusLabel } from './riskControl.ts';
import type { ExecutionRelease } from './riskControl.ts';
import type { RequestClient } from '../api/session.ts';
import { ApiError } from '../api/session.ts';

const TIME = '2026-10-15T08:00:00.123456Z';
const entryWire = { entryId: '80001', listId: '70001', subjectType: 'IP_ADDRESS', maskedSubjectKey: '203.0.***', sourceType: 'MANUAL', status: 'ACTIVE', effectiveFrom: TIME, effectiveTo: null, reason: '卡测试来源', createdBy: '10001', deactivatedBy: null, deactivatedReason: null, createTime: TIME };
const releaseWire = { releaseId: '90002', releaseNo: '2', pointCode: 'PAYMENT_ATTEMPT_RISK', flowVersionId: '60001', environment: 'TEST', status: 'SHADOWING', version: 2, contentHash: 'a'.repeat(64), activatedTime: null, supersededTime: null,
  checks: [{ checkCode: 'TERMINAL_ACTION', checkStatus: 'PASSED', errorCode: null, errorSummary: null }], shadowRun: null as null | Record<string, unknown> };
const completed = { shadowRunId: '95001', baselineReleaseId: '90001', status: 'COMPLETED', sampleTarget: 200, sampleCount: 200, allowToReviewCount: 4, allowToDenyCount: 2, denyToAllowCount: 0, actionChangeCount: 0, featureMissingCount: 0, failedCount: 0, timedOutCount: 0, p95Millis: 38, p99Millis: 71, checkStatus: 'PASSED' };

test('list entries decode only the masked key and reject any raw subject field', () => {
  const entry = decodeRiskListEntry(entryWire);
  assert.equal(entry.maskedSubjectKey, '203.0.***');
  assert.equal('subjectValue' in entry, false);
  assert.throws(() => decodeRiskListEntry({ ...entryWire, subjectValue: '203.0.113.7' }));
  assert.throws(() => decodeRiskListEntry({ ...entryWire, maskedSubjectKey: '' }));
});

test('unknown release and shadow statuses are kept and shown as their code', () => {
  assert.equal(decodeExecutionRelease({ ...releaseWire, status: 'REVIEW_PENDING' }).status, 'REVIEW_PENDING');
  assert.equal(releaseStatusLabel('REVIEW_PENDING'), 'REVIEW_PENDING');
  assert.equal(releaseStatusLabel('SUPERSEDED'), 'Superseded');
  const running = decodeExecutionRelease({ ...releaseWire, shadowRun: { ...completed, status: 'RUNNING', allowToReviewCount: null, allowToDenyCount: null, denyToAllowCount: null, actionChangeCount: null, featureMissingCount: null, failedCount: null, timedOutCount: null, p95Millis: null, p99Millis: null, checkStatus: null } });
  assert.equal(running.shadowRun!.denyToAllowCount, null);
  assert.throws(() => decodeExecutionRelease({ ...releaseWire, privateState: 'x' }));
  assert.throws(() => decodeExecutionItem({ releaseId: '90001' }));
});

test('actions follow release state: first release activates after validation, later ones need a passed shadow run', () => {
  const draft = decodeExecutionRelease({ ...releaseWire, status: 'DRAFT', checks: [] });
  assert.deepEqual(releaseActions(draft, false), { validate: true, shadow: false, activate: false });
  const ready = decodeExecutionRelease(releaseWire);
  assert.deepEqual(releaseActions(ready, false), { validate: false, shadow: false, activate: true });
  assert.deepEqual(releaseActions(ready, true), { validate: false, shadow: true, activate: false });
  const shadowed = decodeExecutionRelease({ ...releaseWire, shadowRun: completed });
  assert.deepEqual(releaseActions(shadowed, true), { validate: false, shadow: true, activate: true });
  const failed = decodeExecutionRelease({ ...releaseWire, shadowRun: { ...completed, failedCount: 1, checkStatus: 'FAILED' } });
  assert.equal(releaseActions(failed, true).activate, false);
  const running = decodeExecutionRelease({ ...releaseWire, shadowRun: { ...completed, status: 'RUNNING' } });
  assert.deepEqual(releaseActions(running, true), { validate: false, shadow: false, activate: false });
  assert.equal(releaseActions(decodeExecutionRelease({ ...releaseWire, status: 'ACTIVE' }), false).activate, false);
});

test('activation acknowledgement is required for first release and for deny-to-allow or authentication changes', () => {
  const release = (run: Record<string, unknown>): ExecutionRelease => decodeExecutionRelease({ ...releaseWire, shadowRun: run });
  assert.equal(activationNeedsAcknowledgement(decodeExecutionRelease(releaseWire), false), true);
  assert.equal(activationNeedsAcknowledgement(release(completed), true), false);
  assert.equal(activationNeedsAcknowledgement(release({ ...completed, denyToAllowCount: 1 }), true), true);
  assert.equal(activationNeedsAcknowledgement(release({ ...completed, actionChangeCount: 7 }), true), true);
});

test('执行发布支持支付尝试与结账准备，支付排第一作为默认点；结账影子无认证变化且无拒绝转放行时不需确认', () => {
  assert.deepEqual(EXECUTION_POINTS, ['PAYMENT_ATTEMPT_RISK', 'CHECKOUT_PREPARE']);
  const checkout = decodeExecutionRelease({ ...releaseWire, pointCode: 'CHECKOUT_PREPARE', shadowRun: { ...completed, denyToAllowCount: 0, actionChangeCount: 0 } });
  assert.equal(activationNeedsAcknowledgement(checkout, true), false);
});

test('subject value filter is trimmed and empty filters are omitted', () => {
  assert.deepEqual(entryFilters('  203.0.113.7 ', 'ACTIVE'), { subjectValue: '203.0.113.7', status: 'ACTIVE' });
  assert.deepEqual(entryFilters('   ', ''), {});
});

test('API clients send body-less JSON posts for validate and activate and check echoed identities', async () => {
  const calls: { path: string; options: RequestInit }[] = [];
  const client = (response: unknown): RequestClient => ({ request: async <T,>(path: string, options: RequestInit = {}, decode?: (v: unknown) => T) => { calls.push({ path, options }); return decode!(response); } });
  const api = createExecutionReleaseApi(client(releaseWire));
  await api.activate('90002');
  assert.equal(calls[0]!.path, '/decision-releases/v1/executions/90002/activate');
  assert.equal(calls[0]!.options.body, undefined);
  const headers = calls[0]!.options.headers as Record<string, string>;
  assert.equal(headers['Content-Type'], 'application/json');
  assert.match(headers['Idempotency-Key']!, /^[0-9a-f-]{36}$/);
  await assert.rejects(api.detail('90003'));
  await assert.rejects(createExecutionReleaseApi(client(releaseWire)).create('PAYMENT_ATTEMPT_RISK', '60009'));
  await assert.rejects(createRiskListApi(client(entryWire)).deactivate('BLOCK_IP', '80009', '误封'));
  const added = await createRiskListApi(client(entryWire)).add('BLOCK_IP', { subjectValue: '203.0.113.7', effectiveFrom: null, effectiveTo: null, reason: '卡测试来源' });
  assert.equal(added.maskedSubjectKey, '203.0.***');
});

test('write commands reuse the idempotency key only when retrying an unknown result', async () => {
  const keys: string[] = [];
  let outcome: 'unknown' | 'conflict' | 'ok' = 'unknown';
  const client: RequestClient = { request: async <T,>(_path: string, options: RequestInit = {}, decode?: (v: unknown) => T) => {
    keys.push((options.headers as Record<string, string>)['Idempotency-Key']!);
    if (outcome === 'unknown') throw new TypeError('connection lost');
    if (outcome === 'conflict') throw new ApiError(409, 'RISK_EXECUTION_RELEASE_STATE_CONFLICT', '状态冲突');
    return decode!(releaseWire);
  } };
  const api = createExecutionReleaseApi(client);
  await assert.rejects(api.activate('90002'));
  outcome = 'ok';
  await api.activate('90002');
  assert.equal(keys[1], keys[0], '结果未知后的重试沿用原键');
  outcome = 'conflict';
  await assert.rejects(api.activate('90002'));
  assert.notEqual(keys[2], keys[1], '成功后的新命令换新键');
  outcome = 'ok';
  await api.activate('90002');
  assert.notEqual(keys[3], keys[2], '确定的 4xx 后换新键');
});
