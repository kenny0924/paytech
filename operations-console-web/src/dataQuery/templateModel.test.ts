import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import {
  completionWords, constraintsBody, columnRows, columnsBody, decodeTemplateDetail, decodeTrialResult, decodeValidation, draftFingerprint,
  draftFromDetail, emptyParameterRow, fieldErrorProblems, moveRow, parameterDeclarations, parameterHint, parameterRows, parameterSummaryTags,
  problemDiagnostics, problemTarget, trialFields, trialPaging, trialParams, updateTemplateBody,
} from './templateModel.ts';
import type { ParameterDeclaration, QueryConstraints, TemplateProblem } from './templateModel.ts';

const declarations: ParameterDeclaration[] = [
  { name: 'clientId', type: 'INTEGER', min: '1' },
  { name: 'statuses', type: 'LIST', elementType: 'ENUM', allowedValues: ['PAID', 'REFUNDED'], maxSize: 20 },
  { name: 'conditions', type: 'LIST', elementType: 'OBJECT', maxSize: 10,
    fields: [{ name: 'channel', type: 'STRING', maxLength: 32 }, { name: 'minAmount', type: 'DECIMAL' }] },
  { name: 'from', type: 'DATETIME' },
  { name: 'enabled', type: 'BOOLEAN', default: false },
  { name: 'sortBy', type: 'FRAGMENT', required: true, default: 'NEWEST', choices: { NEWEST: 'created_at DESC', OLDEST: 'created_at ASC' } },
  { name: 'currency', type: 'STRING', maxLength: 3, default: 'USD' },
  { name: 'channels', type: 'LIST', elementType: 'STRING', default: ['GEIDEA', 'MPGS_NI'] },
];

test('参数声明 ↔ 表单行往返一致，只输出当前类型适用的属性', () => {
  assert.deepEqual(parameterDeclarations(parameterRows(declarations)), declarations);
  const rows = parameterRows(declarations);
  assert.equal(rows[7].defaultText, 'GEIDEA, MPGS_NI');
  assert.equal(rows[4].defaultText, 'false');
  // 切换类型后不残留旧约束
  const changed = { ...rows[6], type: 'INTEGER' as const };
  assert.deepEqual(parameterDeclarations([changed]), [{ name: 'currency', type: 'INTEGER', default: 'USD' }]);
  const fragment = { ...emptyParameterRow('sort'), type: 'FRAGMENT' as const, maxLength: 5,
    choices: [{ key: 'a', value: ' A ', fragment: 'x ASC' }, { key: 'b', value: '', fragment: 'ignored' }] };
  assert.deepEqual(parameterDeclarations([fragment]), [{ name: 'sort', type: 'FRAGMENT', choices: { A: 'x ASC' } }]);
  const objects = { ...rows[2], defaultText: '[{"channel":"NI"}]' };
  assert.deepEqual(parameterDeclarations([objects])[0].default, [{ channel: 'NI' }]);
});

test('参数提示与排序', () => {
  const rows = parameterRows(declarations);
  assert.equal(parameterHint(rows[0]), '≥ 1');
  assert.equal(parameterHint(rows[1]), '最多 20 个 · PAID / REFUNDED');
  assert.equal(parameterHint(rows[5]), '默认 NEWEST · 2 个取值');
  assert.deepEqual(moveRow(['a', 'b', 'c'], 0, 2), ['b', 'c', 'a']);
  assert.deepEqual(moveRow(['a', 'b'], 0, 5), ['a', 'b']);
});

test('参数摘要：* 必填、[] 集合、⟨片段⟩', () => {
  const tags = parameterSummaryTags([
    { name: 'orderId', type: 'STRING', required: true }, { name: 'statuses', type: 'LIST', required: false },
    { name: 'midIds', type: 'LIST', required: true }, { name: 'sortBy', type: 'FRAGMENT', required: false },
  ]);
  assert.deepEqual(tags.map(tag => tag.text), ['orderId*', 'statuses[]', 'midIds[]*', 'sortBy⟨片段⟩']);
  assert.deepEqual(tags.map(tag => tag.kind), ['value', 'list', 'list', 'fragment']);
});

const problem = (patch: Partial<TemplateProblem>): TemplateProblem => ({ level: 'ERROR', line: null, column: null, field: null, message: 'm', ...patch });

test('问题 → 编辑器标记：按 1 起始行列换算偏移，标签 / 值 / 标识符整段下划线，越界收敛', () => {
  const body = 'SELECT a\nFROM t\nWHERE x IN (#{channels}) {{#if from}}y{{/if}}\n';
  const [value, tag, word, outOfLine, outOfDocument, noLine] = problemDiagnostics(body, [
    problem({ line: 3, column: 13 }), problem({ line: 3, column: 26, level: 'WARNING' }), problem({ line: 2, column: 6 }),
    problem({ line: 1, column: 99 }), problem({ line: 99, column: 1 }), problem({ field: 'parameters[0]' }),
  ]);
  assert.equal(body.slice(value.from, value.to), '#{channels}');
  assert.equal(value.severity, 'error');
  assert.equal(body.slice(tag.from, tag.to), '{{#if from}}');
  assert.equal(tag.severity, 'warning');
  assert.equal(body.slice(word.from, word.to), 't');
  assert.equal(body.slice(outOfLine.from, outOfLine.to), 'SELECT a');
  assert.equal(outOfDocument.from, body.length);
  assert.equal(noLine, undefined);
});

test('422 字段错误转问题：body 的行列前缀解析；问题定位到正文或右侧标签页', () => {
  const problems = fieldErrorProblems([
    { field: 'body', message: '第 8 行第 34 列：参数 channels 未声明' }, { field: 'constraints.maxPageSize', message: '不能大于 maxRows' },
  ]);
  assert.deepEqual(problems[0], { level: 'ERROR', line: 8, column: 34, field: 'body', message: '参数 channels 未声明' });
  assert.deepEqual(problemTarget(problems[0]), { kind: 'body', line: 8, column: 34 });
  assert.deepEqual(problemTarget(problems[1]), { kind: 'constraints', field: 'maxPageSize' });
  assert.deepEqual(problemTarget(problem({ field: 'parameters[2].choices.A' })), { kind: 'parameter', index: 2 });
  assert.deepEqual(problemTarget(problem({ field: 'columns[1].keepEnd' })), { kind: 'columns', index: 1 });
  assert.deepEqual(problemTarget(problem({ field: 'dataSourceId' })), { kind: 'dataSource' });
  assert.deepEqual(problemTarget(problem({ field: 'other' })), { kind: 'none' });
});

test('自动补全：值参数、对象字段、片段名、标签与系统参数', () => {
  const words = completionWords(parameterRows(declarations));
  const labels = (kind: string) => words.filter(word => word.kind === kind).map(word => word.label);
  assert.ok(labels('value').includes('clientId'));
  assert.ok(labels('value').includes('conditions.minAmount'));
  assert.deepEqual(labels('fragment'), ['sortBy']);
  assert.ok(labels('tag').includes('#join'));
  assert.ok(labels('system').includes('__operator.accountId'));
});

test('试运行表单：按声明生成控件，未填参数不传，数字按字符串传，JSON 集合必须是数组', () => {
  const fields = trialFields(parameterRows(declarations));
  assert.deepEqual(fields.map(field => field.control), ['integer', 'tags', 'json', 'datetime', 'boolean', 'select', 'text', 'tags']);
  assert.deepEqual(fields[1].options, ['PAID', 'REFUNDED']);
  assert.deepEqual(fields[5].options, ['NEWEST', 'OLDEST']);
  assert.equal(fields[5].required, true);
  assert.equal(fields[6].placeholder, '默认 USD');
  const result = trialParams(fields, { clientId: 1234567890123456789, statuses: [], conditions: '[{"channel":"NI"}]', from: '2026-10-01T00:00:00',
    enabled: false, sortBy: 'OLDEST', currency: '' });
  assert.deepEqual(result.params, { clientId: '1234567890123456800', conditions: [{ channel: 'NI' }], from: '2026-10-01T00:00:00', enabled: false, sortBy: 'OLDEST' });
  assert.deepEqual(trialParams(fields, { conditions: '{"a":1}' }).errors, [{ name: 'conditions', message: '请输入 JSON 数组' }]);
  assert.deepEqual(trialParams(fields, { conditions: '[' }).errors, [{ name: 'conditions', message: 'JSON 格式不正确' }]);
});

test('试运行分页：只发送当前分页方式适用的字段', () => {
  const offset: QueryConstraints = { maxRows: 1000, timeoutSeconds: 10, pagingMode: 'OFFSET', maxPageSize: 100, countAllowed: true };
  assert.deepEqual(trialPaging(offset, { withTotal: true }), { page: 1, size: 100, withTotal: true });
  assert.deepEqual(trialPaging({ ...offset, countAllowed: false }, { page: 2, size: 20, withTotal: true }), { page: 2, size: 20, withTotal: false });
  assert.deepEqual(trialPaging({ ...offset, pagingMode: 'CURSOR' }, { cursor: 'abc', page: 3 }), { size: 100, withTotal: false, cursor: 'abc' });
  assert.deepEqual(trialPaging({ ...offset, pagingMode: 'NONE' }, { page: 3, withTotal: true }), { withTotal: false });
  assert.deepEqual(constraintsBody({ ...offset, pagingMode: 'NONE', cursorColumns: ['id'] }), { maxRows: 1000, timeoutSeconds: 10, pagingMode: 'NONE' });
  assert.deepEqual(constraintsBody({ ...offset, pagingMode: 'CURSOR', cursorColumns: ['id'] }),
    { maxRows: 1000, timeoutSeconds: 10, pagingMode: 'CURSOR', maxPageSize: 100, cursorColumns: ['id'] });
});

const version = {
  templateId: '9001', versionNo: 3, dataSourceId: '9100', dataSourceCode: 'PAYMENT_REPLICA', dataSourceName: '支付库', dataSourceStatus: 'ACTIVE',
  body: 'SELECT 1', parameters: declarations, columns: [{ name: 'email', label: '邮箱', mask: 'KEEP_EDGES', keepStart: 2, keepEnd: 4 }],
  constraints: { maxRows: 1000, timeoutSeconds: 10, pagingMode: 'OFFSET', maxPageSize: 100, countAllowed: true },
  restoredFromVersionNo: null, changeNote: '新增排序', createdBy: '1', createTime: '2026-10-22T01:00:00.000000Z',
};
const detailWire = {
  templateId: '9001', templateCode: 'PAY_REFUND_PENDING', templateName: '处理中的退款', description: null, status: 'ACTIVE', currentVersionNo: 3,
  version: 7, createdBy: '1', updatedBy: '1', createTime: '2026-10-20T01:00:00.000000Z', updateTime: '2026-10-22T01:00:00.000000Z', currentVersion: version,
};

test('模板详情解码与草稿：当前版本号必须一致；未修改的草稿指纹不变，列声明往返一致', () => {
  const detail = decodeTemplateDetail(detailWire, '9001');
  assert.throws(() => decodeTemplateDetail({ ...detailWire, currentVersionNo: 2 }), ApiError);
  assert.throws(() => decodeTemplateDetail(detailWire, '9002'), ApiError);
  const draft = draftFromDetail(detail);
  assert.equal(draftFingerprint(draftFromDetail(detail)), draftFingerprint(draft));
  assert.notEqual(draftFingerprint({ ...draft, body: 'SELECT 2' }), draftFingerprint(draft));
  assert.deepEqual(columnsBody(columnRows(version.columns as never)), version.columns);
  const body = updateTemplateBody(draft, 7, ' ');
  assert.equal(body.expectedVersion, 7);
  assert.equal(body.changeNote, null);
  assert.equal('templateCode' in body, false);
});

test('校验与试运行结果解码：行与列对齐，单元格只允许字符串、布尔与 null', () => {
  assert.deepEqual(decodeValidation({ valid: true, problems: [] }), { valid: true, problems: [] });
  assert.equal(decodeValidation({ valid: false, problems: [{ level: 'ERROR', line: 8, column: 34, message: 'x' }] }).problems[0].field, null);
  const trial = { versionNo: 0, columns: [{ name: 'id' }, { name: 'ok', label: '成功' }], rows: [['1', true], [null, false]], hasMore: true,
    total: 137, elapsedMillis: 84, sql: 'SELECT ?', bindings: ['1'] };
  assert.equal(decodeTrialResult(trial).countSql, null);
  assert.throws(() => decodeTrialResult({ ...trial, rows: [['1']] }), ApiError);
  assert.throws(() => decodeTrialResult({ ...trial, rows: [[1, true]] }), ApiError);
});
