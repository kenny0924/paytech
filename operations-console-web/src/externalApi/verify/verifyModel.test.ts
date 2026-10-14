import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ApiError } from '../../api/session.ts';
import {
  decodeAccount, decodeAccounts, decodeCase, decodeCaseSummary, decodeRun, decodeRunStart, duplicateNames, emptyStep,
  hasTemplateVariable, issueStep, toEntries, toRows, toStepDrafts, toSteps,
} from './verifyModel.ts';

const time = '2026-10-14T06:00:00Z';
const invalid = (fn: () => unknown) => assert.throws(fn, (error: unknown) => error instanceof ApiError && error.status === 502);

test('账号：列表与详情解码，参数与凭据保持顺序，编码不一致或类型错误为 502', () => {
  const summary = { accountCode: 'MAIN', accountName: '主账号', channelCode: 'PAYERMAX', channelAccountId: 'A1', entityNo: 'E1', version: 1, updateTime: time };
  assert.equal(decodeAccounts([summary])[0].accountCode, 'MAIN');
  const detail = decodeAccount({ ...summary, parameters: { appId: 'a', merchantNo: 'm' }, credentials: { key: 'k' },
    referencedCaseCodes: ['CASE_A'], createTime: time, updatedBy: '10001' }, 'MAIN');
  assert.deepEqual(Object.keys(detail.parameters), ['appId', 'merchantNo']);
  invalid(() => decodeAccount({ ...summary, parameters: {}, credentials: { key: 1 }, referencedCaseCodes: [], createTime: time, updatedBy: '1' }, 'MAIN'));
  invalid(() => decodeAccounts({}));
});

test('用例与运行：状态枚举、最近运行与运行详情解码；未知状态为 502', () => {
  const brief = { runCode: '900001', runStatus: 'PASSED', startTime: time, finishTime: time };
  const summary = { caseCode: 'PAY', caseName: '用例', accountCode: 'MAIN', standardApiCodes: ['PAY'], stepCount: 1,
    verifyStatus: 'STALE', latestRun: brief, version: 2, updateTime: time };
  assert.equal(decodeCaseSummary(summary).latestRun?.runCode, '900001');
  invalid(() => decodeCaseSummary({ ...summary, verifyStatus: 'MAYBE' }));
  assert.equal(decodeCase({ ...summary, description: null, steps: [], createTime: time, updatedBy: '1' }, 'PAY').verifyStatus, 'STALE');
  assert.equal(decodeRunStart({ runCode: '7', runStatus: 'RUNNING' }).runCode, '7');
  invalid(() => decodeRunStart({ runCode: '7', runStatus: 'PASSED' }));
  const run = decodeRun({ runCode: '900001', caseCode: 'PAY', runStatus: 'FAILED', failureSummary: '步骤 pay：…', startedBy: '1',
    startTime: time, finishTime: time, caseSnapshot: { caseName: '用例', reason: '回归' }, configVersions: null,
    endpoints: [{ endpointCode: 'GW', baseUrl: 'https://x', sandbox: true }], stale: false,
    steps: [{ stepNo: 1, stepKey: 'pay', standardApiCode: 'PAY', stepStatus: 'FAILED', resolvedInput: { a: 1 },
      result: { kind: 'EXECUTED' }, assertions: [{ target: 'executeStatus', match: 'EQUALS', expected: 'SUCCESS', actual: 'FAIL', passed: false }],
      trace: { nodes: [] }, elapsedMs: 12 }, { stepNo: 2, stepKey: 'q', standardApiCode: 'PAY_QUERY', stepStatus: 'SKIPPED',
      resolvedInput: null, result: null, assertions: null, trace: null, elapsedMs: null }] }, '900001');
  assert.equal(run.caseName, '用例');
  assert.equal(run.steps[1].assertions.length, 0);
  assert.equal(run.endpoints[0].sandbox, true);
});

test('参数编辑行：去空白、跳过空行、重复名称提示', () => {
  const rows = [...toRows({ appId: 'a' }), { name: ' key ', value: ' v ' }, { name: '', value: '' }, { name: 'appId', value: 'b' }];
  assert.deepEqual(toEntries(rows), { appId: 'b', key: 'v' });
  assert.deepEqual(duplicateNames(rows), ['appId']);
});

test('步骤编辑态往返：执行期望与输出、问题期望、ONE_OF 逗号与 JSON、EXISTS 不带值', () => {
  const steps = [
    { id: 'pay', standardApiCode: 'PAY', input: { payment: { attemptId: 'VR${run.code}' } },
      expect: { executeStatus: 'SUCCESS', returnId: 'created', outputs: [
        { path: 'paymentStatus', match: 'EQUALS', value: 'PENDING' }, { path: 'nextAction.redirectUrl', match: 'REGEX', value: '^https://' },
        { path: 'channelTransactionRef', match: 'EXISTS' }, { path: 'paymentStatus', match: 'ONE_OF', value: ['PENDING', 'SUCCEEDED'] }] } },
    { id: 'bad', standardApiCode: 'PAY', input: {}, expect: { problem: { status: 422, code: 'VALIDATION_ERROR' } } },
  ];
  const drafts = toStepDrafts(steps);
  assert.equal(drafts[1].expect, 'PROBLEM');
  assert.deepEqual(toSteps(drafts), { steps, problems: [] });
  drafts[0].outputs[3].value = 'PENDING, FAILED';
  assert.deepEqual((toSteps(drafts).steps[0] as { expect: { outputs: { value: unknown }[] } }).expect.outputs[3].value, ['PENDING', 'FAILED']);
});

test('步骤编辑态：输入不是 JSON 对象、问题状态不是整数时报告问题；问题位置映射到步骤', () => {
  const drafts = [{ ...emptyStep(0), input: '[1]' }, { ...emptyStep(1), input: '{', expect: 'PROBLEM' as const, problemStatus: 'x' }];
  assert.deepEqual(toSteps(drafts).problems.map(item => `${item.step}:${item.field}`), ['0:input', '1:input', '1:problemStatus']);
  assert.equal(issueStep('steps[3].expect.outputs[0].path'), 3);
  assert.equal(issueStep('accountCode'), null);
});

test('期望值中的模板变量：整串或嵌入文字都识别，普通值与不完整写法不识别', () => {
  assert.equal(hasTemplateVariable('${steps.pay.outputs.channelTransactionRef}'), true);
  assert.equal(hasTemplateVariable('T-${run.code}'), true);
  assert.equal(hasTemplateVariable('"PENDING"'), false);
  assert.equal(hasTemplateVariable('$ 10.00'), false);
  assert.equal(hasTemplateVariable('${'), false);
});
