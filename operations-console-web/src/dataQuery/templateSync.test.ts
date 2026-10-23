import assert from 'node:assert/strict';
import test from 'node:test';
import { columnRows, emptyParameterRow, parameterRows } from './templateModel.ts';
import { mergeResultColumns, selectColumns, syncColumns, syncParameters, templateReferences, unreferencedParameters } from './templateSync.ts';

const body = `SELECT o.order_no, o.client_id AS client, COUNT(*) total, CASE WHEN o.paid THEN 1 ELSE 0 END, \`currency\`
FROM t_order o
{{!-- #{ignored} --}}
{{#join " AND " prefix="WHERE "}}
  {{#if statuses}}o.status IN (#{statuses}){{/if}}
  {{#if from && mode == 'X'}}o.create_time >= #{from}{{/if}}
  {{#if conditions}}({{#each conditions as c, i separator=" OR "}}o.channel = #{c.channel}{{/each}}){{/if}}
  {{#if __operator.accountId}}o.owner = #{__operator.accountId}{{/if}}
{{/join}}
ORDER BY @{sortBy} \\#{literal}`;

test('正文引用：按首次出现排序，识别集合与片段，排除注释、转义、循环变量与系统参数', () => {
  assert.deepEqual(templateReferences(body), [
    { name: 'statuses', type: 'LIST' }, { name: 'from', type: 'STRING' }, { name: 'mode', type: 'STRING' },
    { name: 'conditions', type: 'LIST' }, { name: 'sortBy', type: 'FRAGMENT' },
  ]);
});

test('参数同步：补新引用、删不再引用的自动行、自动行随用法改类型，手工行只提示未引用', () => {
  const manual = { ...emptyParameterRow('legacy'), type: 'INTEGER' as const };
  const first = syncParameters('SELECT 1 FROM t WHERE a = #{a} ORDER BY @{s}', [manual]);
  assert.deepEqual(first.map(row => [row.name, row.type, row.auto ?? false]), [['legacy', 'INTEGER', false], ['a', 'STRING', true], ['s', 'FRAGMENT', true]]);
  assert.equal(first[2].choices.length, 1);
  assert.equal(syncParameters('SELECT 1 FROM t WHERE a = #{a} ORDER BY @{s}', first), first, '没有变化时返回原数组');
  const second = syncParameters('SELECT 1 FROM t WHERE a IN (#{a})', first);
  assert.deepEqual(second.map(row => [row.name, row.type]), [['legacy', 'INTEGER'], ['a', 'LIST']]);
  assert.equal(second[1].key, first[1].key, '改类型保留行键');
  assert.equal(second[1].elementType, 'STRING');
  const edited = second.map(row => row.name === 'a' ? { ...row, auto: false } : row);
  assert.deepEqual(syncParameters('SELECT 1 FROM t', edited).map(row => row.name), ['legacy', 'a'], '改过的行不自动删除');
  assert.deepEqual([...unreferencedParameters('SELECT 1 FROM t WHERE a IN (#{a})', parameterRows([{ name: 'a', type: 'LIST' }, { name: 'b', type: 'STRING' }]))], ['b']);
});

test('SELECT 列：别名、隐式别名、列引用最后一段与反引号；表达式无别名或 * 时不完整', () => {
  assert.deepEqual(selectColumns(body), { names: ['order_no', 'client', 'total', 'currency'], complete: false });
  assert.deepEqual(selectColumns('SELECT DISTINCT a, (SELECT MAX(x) FROM y) AS m, \'a,b\' AS s\nFROM t'), { names: ['a', 'm', 's'], complete: true });
  assert.deepEqual(selectColumns('select t.* from t'), { names: [], complete: false });
  assert.equal(selectColumns('{{!-- 空 --}}'), null);
  assert.deepEqual(selectColumns('SELECT a FROM t WHERE b = #{from}')?.names, ['a'], 'FROM 参数名不截断选择项');
});

test('列同步：完整识别时删除不再出现的自动行，手工行保留；试运行结果补列', () => {
  const manual = columnRows([{ name: 'email', label: '邮箱', mask: 'FULL' }]);
  const first = syncColumns('SELECT a, b FROM t', manual);
  assert.deepEqual(first.map(row => [row.name, row.auto ?? false]), [['email', false], ['a', true], ['b', true]]);
  assert.equal(syncColumns('SELECT a, b FROM t', first), first);
  assert.deepEqual(syncColumns('SELECT a FROM t', first).map(row => row.name), ['email', 'a']);
  assert.deepEqual(syncColumns('SELECT a, COUNT(*) FROM t', first).map(row => row.name), ['email', 'a', 'b'], '识别不完整时不删除');
  const merged = mergeResultColumns(first, ['a', 'COUNT(*)']);
  assert.deepEqual(merged.map(row => row.name), ['email', 'a', 'b', 'COUNT(*)']);
  assert.equal(mergeResultColumns(merged, ['a']), merged);
});
