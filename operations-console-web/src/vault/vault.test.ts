import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import {
  decodeDataTypeList, decodeDataTypeMutation, decodeProtect, decodeReveal, isJsonObjectText, manualDataTypes, manualEventFilters, manualPath,
  sampleValuesRequired, snapshotRows, ticketPattern, valueFormatBody, withValueFormatMessage,
} from './vault.ts';
import type { VaultDataType } from './vault.ts';

const type = (code: string, overrides: Partial<VaultDataType> = {}): VaultDataType => ({
  dataTypeCode: code, tokenMode: 'STABLE', dataClass: 'STANDARD', description: 'd', manualProtectEnabled: true,
  manualRevealEnabled: true, status: 'ACTIVE', version: 1, updateTime: '2026-10-15T00:00:00Z', valueFormat: 'TEXT', ...overrides,
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

test('data type decoders default a missing value format to TEXT and reject unknown formats', () => {
  const { valueFormat: _omitted, ...legacy } = type('A_V1');
  assert.equal(decodeDataTypeList({ currentPolicyVersion: 2, items: [legacy] }).items[0].valueFormat, 'TEXT');
  assert.equal(decodeDataTypeMutation({ dataType: legacy, policyVersion: 3 }, 'A_V1').dataType.valueFormat, 'TEXT');
  const regex = decodeDataTypeList({ currentPolicyVersion: 2, items: [type('B_V1', { valueFormat: 'REGEX', valuePattern: '[0-9]+' })] }).items[0];
  assert.deepEqual([regex.valueFormat, regex.valuePattern], ['REGEX', '[0-9]+']);
  assert.throws(() => decodeDataTypeList({ currentPolicyVersion: 2, items: [{ ...type('A_V1'), valueFormat: 'XML' }] }));
  assert.throws(() => decodeDataTypeList({ currentPolicyVersion: 2, items: [type('A_V1', { valueFormat: 'REGEX' })] }));
  assert.throws(() => decodeDataTypeList({ currentPolicyVersion: 2, items: [{ ...type('A_V1'), valuePattern: 1 }] }));
  // BFF 把空正则序列化为 null：视为无正则，解码后不保留该字段。
  const nullPattern = decodeDataTypeList({ currentPolicyVersion: 2, items: [{ ...type('A_V1'), valuePattern: null }] }).items[0];
  assert.equal('valuePattern' in nullPattern, false);
  assert.throws(() => decodeDataTypeList({ currentPolicyVersion: 2, items: [type('A_V1', { valueFormat: 'REGEX', valuePattern: null as unknown as string })] }));
});

test('samples are required only when the result is REGEX and new, switched or the pattern changed', () => {
  const regex = type('A_V1', { valueFormat: 'REGEX', valuePattern: '[0-9]+' });
  assert.equal(sampleValuesRequired(null, 'REGEX', '[0-9]+'), true);
  assert.equal(sampleValuesRequired(null, 'TEXT', undefined), false);
  assert.equal(sampleValuesRequired(type('A_V1'), 'REGEX', '[0-9]+'), true);
  assert.equal(sampleValuesRequired(regex, 'REGEX', '[0-9]+'), false);
  assert.equal(sampleValuesRequired(regex, 'REGEX', '[0-9]{2}'), true);
  assert.equal(sampleValuesRequired(regex, 'JSON', undefined), false);
});

test('value format body sends pattern and non-empty samples only for REGEX', () => {
  assert.deepEqual(valueFormatBody('JSON', '[0-9]+', ['1']), { valueFormat: 'JSON' });
  assert.deepEqual(valueFormatBody('REGEX', '[0-9]+', ['', undefined]), { valueFormat: 'REGEX', valuePattern: '[0-9]+' });
  assert.deepEqual(valueFormatBody('REGEX', '[0-9]+', [' 1', '', '22']), { valueFormat: 'REGEX', valuePattern: '[0-9]+', sampleValues: [' 1', '22'] });
});

test('JSON precheck requires an object root', () => {
  assert.ok(isJsonObjectText('{"a":{"b":[1]}}'));
  assert.ok(isJsonObjectText(' {} '));
  for (const text of ['[]', 'null', '"x"', '1', '{"a":1} x', '{a:1}', '']) assert.ok(!isJsonObjectText(text), text);
});

test('value format error codes show fixed messages without echoing the response detail', () => {
  const mapped = withValueFormatMessage(new ApiError(400, 'VALUE_PATTERN_MISMATCH', 'req-1', 'raw value 13800000000')) as ApiError;
  assert.deepEqual([mapped.message, mapped.code, mapped.requestId, mapped.status], ['值不符合数据类型的正则', 'VALUE_PATTERN_MISMATCH', 'req-1', 400]);
  const other = new ApiError(409, 'VERSION_CONFLICT');
  assert.equal(withValueFormatMessage(other), other);
});

test('snapshot rows label value format fields', () => {
  assert.deepEqual(snapshotRows({ valueFormat: 'TEXT' }, { valueFormat: 'REGEX', valuePattern: '[0-9]+' }).map(r => r.label), ['值格式', '正则']);
});
