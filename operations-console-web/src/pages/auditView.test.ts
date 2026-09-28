import assert from 'node:assert/strict';
import test from 'node:test';
import { auditFilters, auditValue, validateAuditDetail } from './auditView.ts';
import { ApiError } from '../api/session.ts';
import type { AuditDetailResponseBody } from '../api/types.ts';

test('audit filters resolve a quick local calendar range at submit time and trim exact identifiers', () => {
  const now = new Date(2026, 8, 4, 18, 23, 45);
  const values = { timeRange: { preset: 'LAST_3_DAYS' as const },
    actorId: ' UNKNOWN ', actionCode: ' OPERATIONS_ACCOUNT_ENABLE ', result: ' SUCCEEDED ', requestId: ' request + / 123 ' };
  assert.deepEqual(auditFilters(values, now), { occurredFrom: new Date(2026, 8, 2).toISOString(),
    occurredTo: new Date(2026, 8, 5).toISOString(), actorId: 'UNKNOWN', actionCode: 'OPERATIONS_ACCOUNT_ENABLE',
    result: 'SUCCEEDED', requestId: 'request + / 123' });
  assert.equal(values.actorId, ' UNKNOWN ');
  assert.deepEqual(auditFilters({ actionCode: '  OPERATIONS_FUTURE_ACTION  ' }), { actionCode: 'OPERATIONS_FUTURE_ACTION' });
});

test('audit accepts no time selection and requires a valid ordered custom range', () => {
  assert.deepEqual(auditFilters({ actorId: ' ', requestId: '' }), {});
  assert.throws(() => auditFilters({ timeRange: { preset: 'CUSTOM', start: '2026-09-04T10:00:00' } }), /完整/);
  assert.throws(() => auditFilters({ timeRange: { preset: 'CUSTOM', start: 'invalid', end: '2026-09-04T11:00:00' } }), /有效时间/);
  assert.throws(() => auditFilters({ timeRange: { preset: 'CUSTOM', start: '2026-09-04T11:00:00', end: '2026-09-04T10:00:00' } }), /晚于开始/);
  assert.throws(() => auditFilters({ timeRange: { preset: 'CUSTOM', start: '2026-09-04T10:00:00', end: '2026-09-04T10:00:00' } }), /晚于开始/);
  const custom = { preset: 'CUSTOM' as const, start: '2026-09-04T10:00:00', end: '2026-09-04T11:00:00' };
  assert.deepEqual(auditFilters({ timeRange: custom }), { occurredFrom: new Date(custom.start).toISOString(),
    occurredTo: new Date(custom.end).toISOString() });
});

const detail: AuditDetailResponseBody = {
  event: { auditEventId: '123', actorId: 'UNKNOWN', actionCode: 'OPERATIONS_ACCOUNT_ENABLE', resourceType: 'USER_ACCOUNT',
    resourceId: '456', result: 'SUCCEEDED', requestId: 'request-123', occurredTime: '2026-09-04T00:00:00Z', reason: '恢复访问' },
  changes: [{ key: 'status', label: '状态', before: [], after: [null, 'ACTIVE'] }],
  attributes: [{ key: 'customKey', label: '自定义字段', values: ['UNKNOWN_FUTURE_VALUE', ''] }],
};

test('audit detail preserves historical absence, hidden values, duplicates and unknown values', () => {
  const original = structuredClone(detail);
  validateAuditDetail(detail, '123');
  validateAuditDetail({ ...detail, changes: [], attributes: [] }, '123');
  assert.deepEqual(detail, original);
  assert.equal(auditValue(null), '已隐藏');
  assert.equal(auditValue(''), '（空字符串）');
  assert.equal(auditValue('UNKNOWN_FUTURE_VALUE'), 'UNKNOWN_FUTURE_VALUE');
});

test('audit detail rejects mismatched identity or malformed value collections before rendering', () => {
  for (const invalid of [null, { ...detail, event: { ...detail.event, auditEventId: 'another' } },
    { ...detail, changes: null }, { ...detail, changes: [{ ...detail.changes[0], before: ['value', 42] }] },
    { ...detail, attributes: [{ key: 'key', label: 'label', values: null }] }]) {
    assert.throws(() => validateAuditDetail(invalid as AuditDetailResponseBody, '123'), error => error instanceof ApiError && error.status === 502);
  }
});
