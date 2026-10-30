import assert from 'node:assert/strict';
import test from 'node:test';
import {
  checkEntry, decodeJointSettings, decodeJointStep, isLongText, jointBody, jointDraft, jointEffect, jointProblems, parameterFitsKey,
} from './jointModel.ts';
import type { JointConfig, KeyTypeInfo } from './jointModel.ts';
import { columnRows, decodeTemplateDetail, draftFingerprint, draftFromDetail, emptyDraft, parameterRows, problemTarget, updateTemplateBody } from './templateModel.ts';
import { jointChanges } from './versionDiff.ts';
import type { TemplateVersion } from './templateModel.ts';
import { testT } from '../i18n/testing.ts';

const t = testT('data-query');

const joint: JointConfig = {
  subjectKeyType: 'PAYMENT_INTENT_ID', keyParameter: { name: 'orderIds', keyType: 'ORDER_ID' },
  columns: [{ name: 'payment_intent_id', keyType: 'PAYMENT_INTENT_ID', keyRole: 'LINK' }, { name: 'client_id', keyType: 'CLIENT_ID', keyRole: 'DIMENSION' }],
};
const version = (patch: Record<string, unknown> = {}) => ({
  templateId: '9001', versionNo: 2, dataSourceId: '10', dataSourceCode: 'MOR_PAYMENT_REPLICA', dataSourceName: 'Payment', dataSourceStatus: 'ACTIVE',
  body: 'SELECT 1', parameters: [{ name: 'orderIds', type: 'LIST', elementType: 'INTEGER', required: true }], columns: [{ name: 'payment_intent_id', mask: 'NONE' }],
  constraints: { maxRows: 100, timeoutSeconds: 5, pagingMode: 'OFFSET', maxPageSize: 100, countAllowed: false }, restoredFromVersionNo: null, changeNote: null,
  createdBy: '1', createTime: '2026-10-29T00:00:00Z', joint, ...patch,
});
const detail = (currentVersion: Record<string, unknown>, patch: Record<string, unknown> = {}) => ({
  templateId: '9001', templateCode: 'JQ_PAYMENT_INTENT_BY_ORDER', templateName: 'Intent by order', description: null, status: 'ACTIVE', currentVersionNo: 2,
  version: 5, createdBy: '1', updatedBy: '1', createTime: '2026-10-29T00:00:00Z', updateTime: '2026-10-29T00:00:00Z', currentVersion, ...patch,
});

test('joint 往返：详情 → 草稿 → 保存请求体原样带回 joint；关闭参与发送 null；旧版本无 joint 为 null', () => {
  const decoded = decodeTemplateDetail(detail(version(), { jointQuery: { participating: true, reasonCode: null, reason: null } }), '9001');
  assert.deepEqual(decoded.currentVersion.joint, joint);
  assert.deepEqual(decoded.jointQuery, { participating: true, reasonCode: null, reason: null });
  const draft = draftFromDetail(decoded);
  assert.deepEqual(updateTemplateBody(draft, decoded.version, '').joint, joint);
  // 关闭后保留草稿内容，但请求体为 null；再打开恢复原配置
  const off = { ...draft, joint: { ...draft.joint, enabled: false } };
  assert.equal(updateTemplateBody(off, 5, '').joint, null);
  assert.notEqual(draftFingerprint(off), draftFingerprint(draft));
  assert.deepEqual(jointBody({ ...off.joint, enabled: true }), joint);
  const legacy = decodeTemplateDetail(detail(version({ joint: undefined })));
  assert.equal(legacy.currentVersion.joint, null);
  assert.equal(legacy.jointQuery, null);
  assert.equal(updateTemplateBody(draftFromDetail(legacy), 5, '').joint, null);
  assert.equal(jointBody(emptyDraft().joint), null);
  // 未修改的已有模板不产生未保存修改
  assert.equal(draftFingerprint(draftFromDetail(decoded)), draftFingerprint(draft));
});

test('joint.* 问题定位到联合查询页签', () => {
  assert.deepEqual(problemTarget({ level: 'ERROR', line: null, column: null, field: 'joint.columns[0].keyRole', message: 'x' }), { kind: 'joint', field: 'joint.columns[0].keyRole' });
  assert.deepEqual(problemTarget({ level: 'ERROR', line: null, column: null, field: 'joint', message: 'x' }), { kind: 'joint', field: 'joint' });
});

const info = (code: string, valueType: 'INTEGER' | 'STRING', defaultRole: 'LINK' | 'DIMENSION', status = 'ACTIVE'): [string, KeyTypeInfo] =>
  [code, { keyTypeCode: code, keyTypeName: `${code} name`, groupName: 'G', valueType, defaultRole, status, sortNo: 1 }];
const keyTypes = new Map([info('ORDER_ID', 'INTEGER', 'LINK'), info('PAYMENT_INTENT_ID', 'INTEGER', 'LINK'), info('CLIENT_ID', 'INTEGER', 'DIMENSION'),
  info('ORDER_NO', 'STRING', 'LINK'), info('OLD_ID', 'INTEGER', 'LINK', 'INACTIVE')]);

test('就地检查：LINK 列被屏蔽为错误、LINK 标在默认维度键上为提醒、参数类型与必填', () => {
  const parameters = parameterRows([{ name: 'orderIds', type: 'LIST', elementType: 'INTEGER', required: true }, { name: 'currency', type: 'STRING', required: true }]);
  const columns = columnRows([{ name: 'payment_intent_id', mask: 'FULL' }, { name: 'client_id', mask: 'NONE' }]);
  const draft = { ...jointDraft(joint), columns: [...jointDraft(joint).columns, { key: 'x', name: 'client_id', keyType: 'CLIENT_ID', keyRole: 'LINK' as const },
    { key: 'y', name: 'old', keyType: 'OLD_ID', keyRole: 'DIMENSION' as const }] };
  assert.deepEqual(jointProblems(draft, parameters, columns, keyTypes).map(problem => `${problem.level} ${problem.field}`), [
    'WARNING joint.keyParameter.name', 'ERROR joint.columns[0].keyRole', 'ERROR joint.columns[2].name', 'WARNING joint.columns[2].keyRole', 'ERROR joint.columns[3].keyType',
  ]);
  assert.equal(jointProblems(draft, parameters, columns, keyTypes)[1].message, t('joint.problem.linkMasked', { name: 'payment_intent_id' }));
  const stringKey = { ...jointDraft(joint), keyParameterKeyType: 'ORDER_NO', columns: [] };
  assert.deepEqual(jointProblems(stringKey, parameters, [], keyTypes).map(problem => `${problem.level} ${problem.field}`),
    ['ERROR joint.keyParameter.name', 'WARNING joint.keyParameter.name']);
  assert.deepEqual(jointProblems({ ...jointDraft(null) }, parameters, columns, keyTypes), []);
  const blank = { ...jointDraft(null), enabled: true };
  assert.deepEqual(jointProblems(blank, [], [], keyTypes).map(problem => problem.field), ['joint.subjectKeyType', 'joint.keyParameter.name', 'joint.keyParameter.keyType']);
  assert.ok(parameterFitsKey({ type: 'STRING', elementType: null }, 'INTEGER'));
  assert.ok(parameterFitsKey({ type: 'LIST', elementType: 'STRING' }, 'STRING'));
  assert.ok(!parameterFitsKey({ type: 'INTEGER', elementType: null }, 'STRING'));
  assert.ok(!parameterFitsKey({ type: 'DECIMAL', elementType: null }, 'INTEGER'));
});

test('底部一句话：带出的链路键与修正提示', () => {
  const draft = jointDraft(joint);
  assert.equal(jointEffect(draft, keyTypes, 0), t('joint.effect.withLinks', { trigger: 'ORDER_ID name', links: 'PAYMENT_INTENT_ID name' }));
  assert.equal(jointEffect(draft, keyTypes, 1), t('joint.effect.afterFix', { count: 1, effect: t('joint.effect.withLinks', { trigger: 'ORDER_ID name', links: 'PAYMENT_INTENT_ID name' }) }));
  assert.equal(jointEffect({ ...draft, enabled: false }, keyTypes, 0), t('joint.effect.off'));
});

test('版本对比：joint 作为独立区块', () => {
  const before = { ...decodeTemplateDetail(detail(version())).currentVersion } as TemplateVersion;
  const after: TemplateVersion = { ...before, versionNo: 3, joint: { ...joint, keyParameter: { name: 'orderIds', keyType: 'ORDER_NO' },
    columns: [{ name: 'payment_intent_id', keyType: 'PAYMENT_INTENT_ID', keyRole: 'DIMENSION' }, { name: 'attempt_id', keyType: 'ATTEMPT_ID', keyRole: 'LINK' }] } };
  assert.deepEqual(jointChanges(before, after).map(change => `${change.subject}: ${change.change}`), [
    'Queried By: orderIds · ORDER_ID → orderIds · ORDER_NO',
    'Linked column payment_intent_id: PAYMENT_INTENT_ID · Link → PAYMENT_INTENT_ID · Dimension',
    'Linked column attempt_id: Added ATTEMPT_ID · Link',
    'Linked column client_id: Removed',
  ]);
  assert.deepEqual(jointChanges(before, { ...before, joint: null }).map(change => change.change), ['No longer takes part']);
  assert.deepEqual(jointChanges({ ...before, joint: null }, { ...before, joint: null }), []);
  assert.equal(jointChanges({ ...before, joint: null }, before)[0].change, 'Now takes part');
});

test('入口输入：逐行校验 64 位整数与正则，空行忽略，去重并记录行号；超过上限', () => {
  const integer = { valueType: 'INTEGER' as const, valuePattern: '[0-9]{1,19}' };
  const result = checkEntry('2051887102284566529\n\n  12a  \n2051887102284566529\n9223372036854775808\n0042', integer, 20);
  assert.deepEqual(result.values, ['2051887102284566529', '0042']);
  assert.deepEqual(result.valueLines, [1, 6]);
  assert.deepEqual(result.invalid, [3, 5]);
  assert.equal(result.empty, false);
  assert.ok(checkEntry(' \n ', integer, 20).empty);
  assert.ok(checkEntry('1\n2\n3', integer, 2).tooMany);
  const refund = checkEntry(`rfd_${'a'.repeat(30)}\nRF20261029-0001`, { valueType: 'STRING', valuePattern: 'rfd_[0-9A-Za-z]{30}' }, 20);
  assert.deepEqual(refund.invalid, [2]);
  assert.ok(isLongText('9223372036854775807') && !isLongText('9223372036854775808') && isLongText('0009223372036854775807'));
});

test('J1 / J3 解码：键值一律为字符串；非法响应报错', () => {
  const settings = decodeJointSettings({ entryKeyTypes: [{ keyTypeCode: 'B', keyTypeName: 'b', groupName: 'G', valueType: 'STRING', valuePattern: null, inputHint: 'h', sortNo: 20 },
    { keyTypeCode: 'A', keyTypeName: 'a', groupName: 'G', valueType: 'INTEGER', sortNo: 10 }], groups: [{ groupName: 'Z', rank: 900 }, { groupName: 'G', rank: 10 }],
    limits: { maxEntryValues: 20, maxValuesPerKey: 50, maxRowsPerTemplate: 100, maxExecutionsPerStep: 20, maxDepth: 6, maxTemplateExecutions: 60, maxTotalSeconds: 60, extra: 1 } });
  assert.deepEqual(settings.keyTypes.map(item => item.keyTypeCode), ['A', 'B']);
  assert.deepEqual(settings.groups.map(group => group.groupName), ['G', 'Z']);
  assert.equal(Object.keys(settings.limits).length, 7);
  assert.throws(() => decodeJointSettings({ keyTypes: [], groups: [], limits: { maxEntryValues: 20 } }));
  const step = decodeJointStep({ depth: 0, nodes: [{ nodeId: '0:T:K', templateCode: 'T', templateName: 'Refund', versionNo: 2, dataSourceCode: 'DS', subjectKeyType: 'R',
    groupName: 'G', laneRank: 10, subjectSortNo: 30, triggerKeyType: 'K', keyParameter: 'nos', triggerValues: ['x'], status: 'SUCCEEDED', errorCode: null, errorMessage: null,
    pagingMode: 'OFFSET', truncated: false, elapsedMillis: 4, columns: [{ name: 'order_id', label: 'Order', keyType: 'ORDER_ID', keyRole: 'LINK' }], rows: [['2051']] }],
    links: [{ nodeId: '0:T:K', rowIndex: 0, column: 'order_id', keyType: 'ORDER_ID', value: '2051' }], discoveredKeys: [{ keyType: 'ORDER_ID', values: ['2051'] }],
    truncations: [], elapsedMillis: 9 });
  assert.equal(step.nodes[0].keyParameterList, null);
  assert.deepEqual(step.nodes[0].warnings, []);
  assert.throws(() => decodeJointStep({ depth: 0, nodes: [], links: [{ nodeId: 'x', rowIndex: 0, column: 'c', keyType: 'K', value: 2051 }] }));
});
