import test from 'node:test';
import assert from 'node:assert/strict';
import { createRiskEvaluationApi, PAYMENT_ATTEMPT_POINT } from './riskEvaluationApi.ts';
import { evaluationWire, explanationWire } from './riskEvaluationFixtures.ts';
import { ApiError } from '../api/session.ts';
import type { RequestClient } from '../api/session.ts';

function client(response: unknown | (() => never)) {
  const calls: { path: string; options: RequestInit }[] = [];
  const request: RequestClient = { request: async <T,>(path: string, options: RequestInit = {}, decode?: (v: unknown) => T) => {
    calls.push({ path, options });
    if (typeof response === 'function') return (response as () => never)();
    return decode!(response);
  } };
  return { calls, api: createRiskEvaluationApi(request) };
}

test('按评估 ID 查结论：GET 编码路径，传递取消信号，回执评估 ID 必须一致', async () => {
  const { calls, api } = client(evaluationWire);
  const signal = new AbortController().signal;
  const result = await api.get('236600000000000001', signal);
  assert.equal(result.flowCode, 'PAY_ATTEMPT_FLOW');
  assert.equal(calls[0]!.path, '/risk-decisions/v1/evaluations/236600000000000001');
  assert.equal(calls[0]!.options.method, undefined);
  assert.equal(calls[0]!.options.signal, signal);
  await assert.rejects(api.get('236600000000000009'), /invalid Risk response/);
});

test('按支付尝试查正式评估：只带 pointCode 与 businessKey，回执业务身份与正式模式必须一致', async () => {
  const { calls, api } = client(evaluationWire);
  const result = await api.find(PAYMENT_ATTEMPT_POINT, '991015000000000002');
  assert.equal(result.evaluationId, '236600000000000001');
  assert.equal(calls[0]!.path, '/risk-decisions/v1/evaluations?pointCode=PAYMENT_ATTEMPT_RISK&businessKey=991015000000000002');
  await assert.rejects(api.find(PAYMENT_ATTEMPT_POINT, '991015000000000003'));
  await assert.rejects(client({ ...evaluationWire, mode: 'SHADOW' }).api.find(PAYMENT_ATTEMPT_POINT, '991015000000000002'));
  await assert.rejects(client(evaluationWire).api.find('CHECKOUT_PREPARE', '991015000000000002'));
});

test('评估解释：路径为 /explanation，回执评估 ID 必须一致，条件树与受限输入原样解码', async () => {
  const { calls, api } = client(explanationWire);
  const explanation = await api.explanation('236600000000000001');
  assert.equal(calls[0]!.path, '/risk-decisions/v1/evaluations/236600000000000001/explanation');
  assert.equal(explanation.inputs.clientIpAddress, '203.0.113.9');
  assert.equal(explanation.rules[2]!.condition!.children.length, 2);
  await assert.rejects(api.explanation('236600000000000002'));
});

test('错误原样抛出：404 供页面显示“没有找到该评估”，403 与其他错误保留 Request ID', async () => {
  const notFound = client(() => { throw new ApiError(404, 'NOT_FOUND', 'req-404'); });
  await assert.rejects(notFound.api.find(PAYMENT_ATTEMPT_POINT, '1'), (e: unknown) => e instanceof ApiError && e.status === 404);
  const forbidden = client(() => { throw new ApiError(403, 'FORBIDDEN', 'req-403'); });
  await assert.rejects(forbidden.api.get('1'), (e: unknown) => e instanceof ApiError && e.status === 403 && e.requestId === 'req-403');
  const upstream = client(() => { throw new ApiError(502, 'UPSTREAM_INVALID_RESPONSE', 'req-502'); });
  await assert.rejects(upstream.api.explanation('1'), (e: unknown) => e instanceof ApiError && e.message === '服务暂时不可用，请稍后重试。');
});
