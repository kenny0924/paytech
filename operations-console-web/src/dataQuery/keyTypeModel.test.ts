import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import {
  createKeyTypeBody, decodeKeyType, groupKeyTypes, keyTypeForm, keyTypeOrderBody, matchesPattern, moveKeyType, newKeyTypeForm, patternProblem, updateKeyTypeBody,
} from './keyTypeModel.ts';
import type { QueryKeyType } from './keyTypeModel.ts';

const keyType = (code: string, groupName: string, sortNo: number, patch: Partial<QueryKeyType> = {}): QueryKeyType => ({
  keyTypeId: String(1000 + sortNo), keyTypeCode: code, keyTypeName: code, groupName, valueType: 'INTEGER', defaultRole: 'LINK', entryAllowed: true, valuePattern: null,
  inputHint: null, sortNo, description: null, status: 'ACTIVE', version: 3, referencedTemplateCount: 0, updatedBy: '1', updateTime: '2026-10-29T00:00:00Z', ...patch,
});

test('入口正则子集：拒绝反向引用、前后断言、内联标志、所有格与原子组及两边含义不同的写法', () => {
  for (const ok of ['', '[0-9]{1,19}', 'ord_([0-9A-Za-z]{30}|[A-Za-z0-9_-]{22})', 'rfd_[0-9A-Za-z]{30}', '[!-~]{1,128}', '[A-Z0-9]{5}', '(?:ab)+c?', '(?<name>x)\\d+',
    'a\\+\\+', '[*+]+', '\\[x]', 'a*?b']) {
    assert.equal(patternProblem(ok), null, ok);
  }
  for (const bad of ['(a)\\1', '(?<n>a)\\k<n>', 'a(?=b)', 'a(?!b)', '(?<=a)b', '(?<!a)b', '(?i)abc', '(?>a)', 'a*+', 'a++', 'a?+', 'a{2}+',
    '[]a]', '[^]', '[a-z&&[^b]]', '[a[b]]', '\\p{L}', '\\Aabc\\z', '\\Qa.b\\E', '\\h']) {
    assert.equal(patternProblem(bad), 'unsupported', bad);
  }
  assert.equal(patternProblem('(ab'), 'invalid');
  assert.equal(patternProblem('a'.repeat(257)), 'tooLong');
});

test('入口值整串匹配', () => {
  assert.ok(matchesPattern('rfd_[0-9A-Za-z]{30}', `rfd_${'a'.repeat(30)}`));
  assert.ok(!matchesPattern('rfd_[0-9A-Za-z]{30}', `xrfd_${'a'.repeat(30)}`));
  assert.ok(!matchesPattern('[0-9]{1,19}', '12a'));
  assert.ok(matchesPattern('a|b', 'b'));
  assert.ok(!matchesPattern('a|b', 'ab'));
  assert.ok(matchesPattern(null, 'anything'));
  assert.ok(!matchesPattern('(', 'x'));
});

test('分组：组顺序取组内最小 sort_no，组内按 sort_no；拖动只在组内移动并按位置重写 sort_no', () => {
  const items = [keyType('CLIENT_ID', 'Dim', 900), keyType('ORDER_ID', 'Acq', 20), keyType('INTENT_ID', 'Pay', 40), keyType('CHECKOUT_ID', 'Acq', 10),
    keyType('REFUND_NO', 'Acq', 31, { status: 'INACTIVE' })];
  assert.deepEqual(groupKeyTypes(items).map(group => [group.groupName, group.rank, group.items.map(item => item.keyTypeCode)]), [
    ['Acq', 10, ['CHECKOUT_ID', 'ORDER_ID', 'REFUND_NO']], ['Pay', 40, ['INTENT_ID']], ['Dim', 900, ['CLIENT_ID']],
  ]);
  const moved = moveKeyType(items, items[2].keyTypeId, items[2].keyTypeId);
  assert.equal(moved, null);
  assert.equal(moveKeyType(items, items[1].keyTypeId, items[2].keyTypeId), null, 'cross-group drop is rejected');
  const next = moveKeyType(items, items[4].keyTypeId, items[3].keyTypeId)!;
  assert.deepEqual(next.map(item => [item.keyTypeCode, item.sortNo]), [['REFUND_NO', 10], ['CHECKOUT_ID', 20], ['ORDER_ID', 30], ['INTENT_ID', 40], ['CLIENT_ID', 50]]);
  assert.deepEqual(keyTypeOrderBody(next).items.slice(0, 2), [{ keyTypeId: items[4].keyTypeId, expectedVersion: 3 }, { keyTypeId: items[3].keyTypeId, expectedVersion: 3 }]);
});

test('表单 ↔ 请求体：空可选项为 null；新建排在分组末尾', () => {
  const item = keyType('ORDER_NO', 'Acq', 21, { valueType: 'STRING', valuePattern: 'ord_.+', inputHint: 'ord_ prefix', description: 'Order number', version: 4 });
  const form = keyTypeForm(item);
  assert.deepEqual(updateKeyTypeBody({ ...form, inputHint: '  ' }, 4), { keyTypeName: 'ORDER_NO', groupName: 'Acq', valueType: 'STRING', defaultRole: 'LINK',
    entryAllowed: true, valuePattern: 'ord_.+', inputHint: null, sortNo: 21, description: 'Order number', expectedVersion: 4 });
  assert.equal(createKeyTypeBody({ ...form, keyTypeCode: ' PAYOUT_ID ' }).keyTypeCode, 'PAYOUT_ID');
  assert.equal(newKeyTypeForm([item, keyType('CLIENT_ID', 'Dim', 900)], 'Acq').sortNo, 30);
  assert.equal(newKeyTypeForm([item, keyType('CLIENT_ID', 'Dim', 900)]).sortNo, 910);
});

test('解码：referencedTemplateCount 缺省为 0，空正则视为不校验；非法响应报 502', () => {
  const raw = { ...keyType('X', 'G', 1), valuePattern: '', referencedTemplateCount: undefined };
  const decoded = decodeKeyType(raw);
  assert.equal(decoded.valuePattern, null);
  assert.equal(decoded.referencedTemplateCount, 0);
  assert.throws(() => decodeKeyType({ ...raw, valueType: 'DECIMAL' }), (error: unknown) => error instanceof ApiError && error.status === 502);
});
