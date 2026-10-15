import assert from 'node:assert/strict';
import test from 'node:test';
import {
  decodeDataTypeList, decodeDataTypeMutation, decodeProtect, decodeReveal, manualDataTypes, manualEventFilters, manualPath, snapshotRows,
  ticketPattern,
} from './vault.ts';
import type { VaultDataType } from './vault.ts';

const type = (code: string, overrides: Partial<VaultDataType> = {}): VaultDataType => ({
  dataTypeCode: code, tokenMode: 'STABLE', dataClass: 'STANDARD', description: 'd', manualProtectEnabled: true,
  manualRevealEnabled: true, status: 'ACTIVE', version: 1, updateTime: '2026-10-15T00:00:00Z', ...overrides,
});

test('manual data types hide SECRET without secret permission and respect switches and status', () => {
  const items = [type('EMAIL_ADDRESS_V1'), type('CHANNEL_ACCOUNT_CONFIG_V1', { dataClass: 'SECRET' }),
    type('PHONE_E164_V1', { manualProtectEnabled: false }), type('OLD_V1', { status: 'DISABLED' })];
  assert.deepEqual(manualDataTypes(items, 'protect', false).map(t => t.dataTypeCode), ['EMAIL_ADDRESS_V1']);
  assert.deepEqual(manualDataTypes(items, 'protect', true).map(t => t.dataTypeCode), ['EMAIL_ADDRESS_V1', 'CHANNEL_ACCOUNT_CONFIG_V1']);
  assert.deepEqual(manualDataTypes(items, 'reveal', false).map(t => t.dataTypeCode), ['EMAIL_ADDRESS_V1', 'PHONE_E164_V1']);
});

test('secret permission selects the secret route', () => {
  assert.equal(manualPath('protect', false), '/vault-admin/v1/manual/protect');
  assert.equal(manualPath('reveal', true), '/vault-admin/v1/manual/secret-reveal');
});

test('decoders reject malformed or mismatched responses', () => {
  assert.equal(decodeDataTypeList({ currentPolicyVersion: 2, items: [type('A_V1')] }).items.length, 1);
  assert.throws(() => decodeDataTypeList({ currentPolicyVersion: 2, items: [{ ...type('A_V1'), tokenMode: 'OTHER' }] }));
  assert.throws(() => decodeDataTypeMutation({ dataType: type('A_V1'), policyVersion: 3 }, 'B_V1'));
  assert.equal(decodeProtect({ token: 'A'.repeat(43) }).token.length, 43);
  assert.throws(() => decodeProtect({ token: 'short' }));
  assert.deepEqual(decodeReveal({ dataTypeCode: 'A_V1', value: '' }), { dataTypeCode: 'A_V1', value: '' });
  assert.throws(() => decodeReveal({ dataTypeCode: 'a', value: 'x' }));
});

test('snapshot rows mark changed fields and show booleans in Chinese', () => {
  const rows = snapshotRows({ status: 'ACTIVE', manualRevealEnabled: false }, { status: 'DISABLED', manualRevealEnabled: false });
  assert.deepEqual(rows.map(r => [r.label, r.before, r.after, r.changed]), [['状态', 'ACTIVE', 'DISABLED', true], ['允许人工解密', '否', '否', false]]);
  assert.equal(snapshotRows(null, { status: 'ACTIVE' })[0].before, '—');
});

test('ticket reference format', () => {
  assert.ok(ticketPattern.test('OPS-1024'));
  assert.ok(ticketPattern.test('JIRA#12.3_a'));
  assert.ok(!ticketPattern.test('OPS 1'));
  assert.ok(!ticketPattern.test('x'.repeat(65)));
});

test('manual event filters convert the time range and trim values', () => {
  const filters = manualEventFilters({ actorId: ' 10001 ', action: 'REVEAL',
    timeRange: { preset: 'CUSTOM', start: '2026-10-01T00:00:00', end: '2026-10-02T00:00:00' } });
  assert.equal(filters.actorId, '10001');
  assert.equal(filters.action, 'REVEAL');
  assert.ok(filters.from < filters.to);
  assert.throws(() => manualEventFilters({ timeRange: { preset: 'CUSTOM', start: '2026-10-02T00:00:00', end: '2026-10-01T00:00:00' } }));
  assert.deepEqual(manualEventFilters({}), {});
});
