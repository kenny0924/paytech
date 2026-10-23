import assert from 'node:assert/strict';
import test from 'node:test';
import { declarationChanges, lineDiff, sideBySide } from './versionDiff.ts';
import type { TemplateVersion } from './templateModel.ts';

const compact = (left: string, right: string) => lineDiff(left, right).map(line => `${line.kind[0]}${line.leftNo ?? '-'}:${line.rightNo ?? '-'} ${line.text}`);

test('逐行对比：修改行显示为删除 + 新增，行号分别对应左右版本', () => {
  const v2 = ['SELECT a', 'FROM t', '{{#if to}}x <= #{to}{{/if}}', 'ORDER BY @{sortBy}'].join('\n');
  const v3 = ['SELECT a', 'FROM t', '{{#if to}}x < #{to}{{/if}}', 'ORDER BY @{sortBy}', '{{!-- 偏移分页 --}}'].join('\n');
  assert.deepEqual(compact(v2, v3), [
    's1:1 SELECT a', 's2:2 FROM t', 'r3:- {{#if to}}x <= #{to}{{/if}}', 'a-:3 {{#if to}}x < #{to}{{/if}}',
    's4:4 ORDER BY @{sortBy}', 'a-:5 {{!-- 偏移分页 --}}',
  ]);
});

test('逐行对比：相同、全部新增、中间插入与删除', () => {
  assert.ok(lineDiff('a\nb', 'a\nb').every(line => line.kind === 'same'));
  assert.deepEqual(compact('', 'x'), ['r1:- ', 'a-:1 x']);
  assert.deepEqual(compact('a\nc', 'a\nb\nc'), ['s1:1 a', 'a-:2 b', 's2:3 c']);
  assert.deepEqual(compact('a\nb\nc\nd', 'a\nd'), ['s1:1 a', 'r2:- b', 'r3:- c', 's4:2 d']);
  assert.deepEqual(compact('x\na\ny', 'a'), ['r1:- x', 's2:1 a', 'r3:- y']);
});

test('并排视图：删除与新增成对对齐，多出的一侧留空', () => {
  const rows = sideBySide(lineDiff('a\nb\nc\nz', 'a\nB\nz'));
  assert.deepEqual(rows.map(row => [row.left?.text ?? null, row.right?.text ?? null]), [['a', 'a'], ['b', 'B'], ['c', null], ['z', 'z']]);
});

const base: TemplateVersion = {
  templateId: '1', versionNo: 2, dataSourceId: '10', dataSourceCode: 'PAYMENT_REPLICA', dataSourceName: '支付库', dataSourceStatus: 'ACTIVE', body: '',
  parameters: [
    { name: 'clientId', type: 'INTEGER' },
    { name: 'sortBy', type: 'FRAGMENT', choices: { NEWEST: 'created_at DESC', OLDEST: 'created_at ASC' } },
    { name: 'currency', type: 'STRING', maxLength: 3 },
  ],
  columns: [{ name: 'email', mask: 'FULL' }], constraints: { maxRows: 1000, timeoutSeconds: 5, pagingMode: 'OFFSET', maxPageSize: 100, countAllowed: true },
  restoredFromVersionNo: null, changeNote: null, createdBy: '1', createTime: '2026-10-20T00:00:00Z',
};

test('参数 / 约束 / 列 / 数据源变化', () => {
  const next: TemplateVersion = { ...base, versionNo: 3, dataSourceId: '11', dataSourceCode: 'LEDGER_REPLICA',
    parameters: [
      { name: 'sortBy', type: 'FRAGMENT', required: true, choices: { NEWEST: 'created_at DESC', OLDEST: 'created_at ASC', AMOUNT: 'amount DESC' } },
      { name: 'clientId', type: 'INTEGER' },
      { name: 'channels', type: 'LIST', elementType: 'STRING' },
    ],
    columns: [{ name: 'email', mask: 'KEEP_EDGES', keepStart: 2, keepEnd: 4 }, { name: 'id', label: '编号' }],
    constraints: { ...base.constraints, timeoutSeconds: 10 } };
  assert.deepEqual(declarationChanges(base, next).map(change => `${change.subject}: ${change.change}`), [
    'Parameter sortBy: Now required', 'Parameter sortBy: Fragment choice added AMOUNT → amount DESC', 'Parameter channels: Added LIST<STRING>',
    'Parameter currency: Removed', 'Parameter Order: clientId, sortBy → sortBy, clientId', 'Constraint Timeout: 5 s → 10 s',
    'Column email: email · Fully Masked → email · Keep Edges (first 2, last 4)', 'Column id: Added 编号 · No Masking',
    'Data Source: PAYMENT_REPLICA → LEDGER_REPLICA',
  ]);
  assert.deepEqual(declarationChanges(base, { ...base, versionNo: 3 }), []);
});
