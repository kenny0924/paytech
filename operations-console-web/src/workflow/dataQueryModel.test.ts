import assert from 'node:assert/strict';
import test from 'node:test';
import {
  allDataQuery, canCheckNow, checkCounts, checkFeedback, checkState, checkStateTag, completedBySystem, criteriaText, failureCodeText,
  missingText, outputsInline,
} from './dataQueryModel.ts';
import { completionMode, defaultExpandedAction, submissionSummary } from './taskModel.ts';
import { decodeDataQueryCheck, decodeDataQueryCheckResponse, decodeDataQueryTemplate, decodeInstanceDetail, decodeTemplateValidation } from './workflowApi.ts';
import type { ActionRun, DataQueryCheck } from './workflowTypes.ts';
import { testT } from '../i18n/testing.ts';

const t = testT('workflow');

// 接口 8.2 示例响应的检查块（真实形状：微秒时间、空数组、null）。
const unsatisfied: DataQueryCheck = { queryCode: 'GOLIVE_AGREEMENT_ACTIVE', descriptionKey: 'workflow.dataQuery.criteria.GOLIVE_AGREEMENT_ACTIVE',
  instruction: null, result: 'UNSATISFIED', missing: ['TERM_VERSION_NOT_ACTIVE'], failureCode: null, lastCheckedAt: '2026-10-29T08:10:03.123456Z',
  lastSucceededAt: '2026-10-29T08:10:03.123456Z', consecutiveFailures: 0, checking: false };
const run = (extra: Partial<ActionRun> = {}): ActionRun => ({ actionRunId: '701', nodeRunId: '601', actionKey: 'n4', actionType: 'DATA_QUERY', required: true,
  assigneeId: '9', status: 'PENDING', output: null, submittedBy: null, submittedTime: null, templateName: '合同已生效', dataQueryCheck: unsatisfied, ...extra });

test('检查块解码：缺失为 null，未知结果按从未检查，missing 只留字符串', () => {
  assert.equal(decodeDataQueryCheck(null), null);
  assert.equal(decodeDataQueryCheck(undefined), null);
  assert.deepEqual(decodeDataQueryCheck(JSON.parse(JSON.stringify(unsatisfied))), unsatisfied);
  const odd = decodeDataQueryCheck({ queryCode: 'Q', result: 'MAYBE', missing: ['A', 3, null], consecutiveFailures: '2', checking: true })!;
  assert.deepEqual([odd.result, odd.missing, odd.consecutiveFailures, odd.checking, odd.lastCheckedAt], [null, ['A'], 0, true, null]);
  const detail = decodeInstanceDetail({ instance: { instanceId: '1', status: 'RUNNING' }, actionRuns: [{ actionRunId: '701', actionType: 'DATA_QUERY', dataQueryCheck: unsatisfied },
    { actionRunId: '702', actionType: 'FORM', dataQueryCheck: null }] });
  assert.deepEqual(detail.actionRuns.map(item => item.dataQueryCheck?.result ?? null), ['UNSATISFIED', null]);
});

test('立即检查响应与模板选择项解码（接口 8.2、8.5）', () => {
  const response = decodeDataQueryCheckResponse({ actionRunId: '701', actionStatus: 'PENDING', instanceStatus: 'RUNNING', inProgress: false, check: unsatisfied });
  assert.deepEqual(response, { actionRunId: '701', actionStatus: 'PENDING', instanceStatus: 'RUNNING', inProgress: false, check: unsatisfied });
  assert.throws(() => decodeDataQueryCheckResponse({ actionStatus: 'PENDING' }));
  // 接口 8.5 列表项（含当前版本号与模板超时）；旧响应没有这两项时为 null。
  assert.deepEqual(decodeDataQueryTemplate({ templateCode: 'GOLIVE_AGREEMENT_DRAFTED', templateName: '合同已起草', description: null, dataSourceName: 'MOR_COMMERCIAL_REPLICA',
    parameters: [{ name: 'clientId', type: 'INTEGER', required: true }, { type: 'TEXT' }], versionNo: 3, timeoutSeconds: 5 }), { templateCode: 'GOLIVE_AGREEMENT_DRAFTED',
    templateName: '合同已起草', description: null, dataSourceName: 'MOR_COMMERCIAL_REPLICA', versionNo: 3, timeoutSeconds: 5,
    parameters: [{ name: 'clientId', type: 'INTEGER', required: true }] });
  const old = decodeDataQueryTemplate({ templateCode: 'Q', parameters: [] });
  assert.deepEqual([old.versionNo, old.timeoutSeconds, old.templateName], [null, null, 'Q']);
});

test('动作模板配置校验响应（action-templates/validation）：problems 为空即通过，缺 problems 视为无效响应', () => {
  assert.deepEqual(decodeTemplateValidation({ problems: [] }), []);
  assert.deepEqual(decodeTemplateValidation({ problems: [{ code: 'DATA_QUERY_OUTPUT_UNDECLARED', nodeKey: null, edgeKey: null, itemKey: 'client_name',
    message: '查询模板 GOLIVE_CLIENT 没有返回列 out_client_name' }, { code: 'DATA_QUERY_TEMPLATE_INACTIVE' }] }), [
    { code: 'DATA_QUERY_OUTPUT_UNDECLARED', nodeKey: null, edgeKey: null, itemKey: 'client_name', message: '查询模板 GOLIVE_CLIENT 没有返回列 out_client_name' },
    { code: 'DATA_QUERY_TEMPLATE_INACTIVE', nodeKey: null, edgeKey: null, itemKey: null, message: '' }]);
  assert.throws(() => decodeTemplateValidation({}));
});

test('任务卡状态：已完成即已满足，已取消，首查未完成为检查中，其余取最近结果', () => {
  assert.equal(checkState(run()), 'UNSATISFIED');
  assert.equal(checkState(run({ status: 'COMPLETED', dataQueryCheck: { ...unsatisfied, result: 'SATISFIED', missing: [] } })), 'SATISFIED');
  assert.equal(checkState(run({ status: 'CANCELLED' })), 'CANCELLED');
  assert.equal(checkState(run({ dataQueryCheck: { ...unsatisfied, result: null, missing: [], lastCheckedAt: null, checking: true } })), 'CHECKING');
  assert.equal(checkState(run({ dataQueryCheck: null })), 'CHECKING');
  assert.equal(checkState(run({ dataQueryCheck: { ...unsatisfied, result: 'FAILED', missing: [], failureCode: 'DATA_SOURCE_UNAVAILABLE', consecutiveFailures: 3 } })), 'FAILED');
  assert.deepEqual(checkStateTag('UNSATISFIED'), { label: t('dataQuery.check.UNSATISFIED'), color: 'warning' });
  assert.deepEqual(checkStateTag('FAILED'), { label: 'Check failed', color: 'error' });
});

test('文案：完成标准先取文案键译文、再取说明、最后取编码；原因码与失败码缺译文时显示原码', () => {
  assert.equal(criteriaText(unsatisfied), t('dataQuery.criteria.GOLIVE_AGREEMENT_ACTIVE'));
  assert.equal(criteriaText({ ...unsatisfied, descriptionKey: 'workflow.dataQuery.criteria.UNKNOWN', instruction: '合同生效' }), '合同生效');
  assert.equal(criteriaText({ descriptionKey: 'other.namespace.key', instruction: ' ', queryCode: 'Q1' }), 'Q1');
  assert.equal(criteriaText(null), '');
  assert.equal(missingText('TERM_VERSION_NOT_ACTIVE'), t('dataQuery.missing.TERM_VERSION_NOT_ACTIVE'));
  assert.equal(missingText('CHANNEL_IDENTITY_MISSING'), 'CHANNEL_IDENTITY_MISSING');
  assert.equal(failureCodeText('QUERY_TIMEOUT'), t('dataQuery.failure.QUERY_TIMEOUT'));
  assert.equal(failureCodeText('SOMETHING_NEW'), `${t('dataQuery.failure.QUERY_FAILED')} (SOMETHING_NEW)`);
  assert.equal(failureCodeText(null), t('dataQuery.failure.QUERY_FAILED'));
});

test('立即检查：只对运行中实例里开放节点上的待处理数据查询；没有「完成」，也不默认展开为可完成', () => {
  assert.equal(canCheckNow(run(), { status: 'ACTIVE' }, true), true);
  assert.equal(canCheckNow(run(), { status: 'APPROVING' }, true), true);
  assert.equal(canCheckNow(run(), { status: 'PASSED' }, true), false);
  assert.equal(canCheckNow(run(), { status: 'ACTIVE' }, false), false);
  assert.equal(canCheckNow(run({ status: 'COMPLETED' }), { status: 'ACTIVE' }, true), false);
  assert.equal(canCheckNow(run({ actionType: 'FORM' }), { status: 'ACTIVE' }, true), false);
  assert.equal(completionMode(run(), { status: 'ACTIVE' }, true, '9'), 'LOCKED');
  assert.equal(completionMode(run({ actionType: 'FORM' }), { status: 'ACTIVE' }, true, '9'), 'COMPLETE');
  assert.equal(defaultExpandedAction([run(), run({ actionRunId: '702', actionType: 'FORM' })], { status: 'ACTIVE' }, true, '9'), '702');
});

test('立即检查的反馈：进行中、已满足自动完成、仍未满足、检查失败', () => {
  assert.deepEqual(checkFeedback({ inProgress: true, actionStatus: 'PENDING', check: unsatisfied }, '合同已生效'), { kind: 'inProgress', text: t('dataQuery.check.inProgress') });
  assert.deepEqual(checkFeedback({ inProgress: false, actionStatus: 'COMPLETED', check: { ...unsatisfied, result: 'SATISFIED', missing: [] } }, '合同已生效'),
    { kind: 'completed', text: t('dataQuery.check.completed', { name: '合同已生效' }) });
  assert.deepEqual(checkFeedback({ inProgress: false, actionStatus: 'PENDING', check: unsatisfied }, 'x'),
    { kind: 'checked', text: t('dataQuery.check.stillMissing', { count: 1 }), warn: true });
  const failed = checkFeedback({ inProgress: false, actionStatus: 'PENDING', check: { ...unsatisfied, result: 'FAILED', failureCode: 'QUERY_TIMEOUT' } }, 'x');
  assert.deepEqual(failed, { kind: 'checked', text: `${t('dataQuery.check.FAILED')}: ${t('dataQuery.failure.QUERY_TIMEOUT')}`, warn: true });
});

test('系统完成与实例计数：没有提交人的已完成数据查询为系统完成；输出摘要与提交内容', () => {
  const satisfied = run({ status: 'COMPLETED', output: { crr: 'MEDIUM', mcc: 5732, empty: null }, submittedTime: '2026-10-29T09:40:00Z',
    dataQueryCheck: { ...unsatisfied, result: 'SATISFIED', missing: [] } });
  assert.equal(completedBySystem(satisfied), true);
  assert.equal(completedBySystem({ ...satisfied, submittedBy: '9' }), false);
  assert.equal(completedBySystem({ ...satisfied, actionType: 'FORM' }), false);
  assert.equal(outputsInline(satisfied.output), 'crr = MEDIUM · mcc = 5732');
  assert.equal(outputsInline(null), '');
  assert.equal(submissionSummary(satisfied, undefined, new Map()), 'crr = MEDIUM · mcc = 5732');
  assert.equal(submissionSummary({ ...satisfied, output: {} }, undefined, new Map()), t('dataQuery.check.SATISFIED'));
  const failed = run({ actionRunId: '703', dataQueryCheck: { ...unsatisfied, result: 'FAILED', missing: [] } });
  assert.deepEqual(checkCounts([satisfied, run(), failed, run({ actionType: 'FORM', status: 'COMPLETED' }), run({ status: 'CANCELLED' })]),
    { satisfied: 1, unsatisfied: 1, failed: 1 });
  assert.equal(allDataQuery([run(), failed]), true);
  assert.equal(allDataQuery([run(), run({ actionType: 'FORM' })]), false);
  assert.equal(allDataQuery([]), false);
});
