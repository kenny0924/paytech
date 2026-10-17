import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import {
  clientAccessText, decodeClient, decodeDeliveryDetail, decodeDeliverySummary, decodeEndpointCreated, decodeEndpointMutation,
  decodeEndpoints, decodeReplay, decodeSecretRotation, decodeTestResult, defaultDeliveryRange, deliveryFilters, deliveryStatusLabels,
  endpointBody, endpointStatusLabels, eventTypes, failureText, hostAndPath, isReplayable, newestAttemptsFirst, readWebhookQuery,
  replayBody, rotateBody, secretPattern, statusBody, testEndpointRequest, testResultText, urlErrorMessage, webhookCommand,
} from './webhook.ts';

const time = '2026-10-16T06:20:00Z';
const secret = `whsec_${'A'.repeat(40)}b-_`;
const endpoint = { endpointId: '880000000000072001', url: 'https://hooks.acme.example/paytech', eventTypes: ['order.paid', 'refund.succeeded'],
  status: 'ACTIVE', signingSecret: { rotationNo: 2, createTime: time, previousValidUntil: '2026-10-16T07:20:00Z' }, version: 3,
  createTime: time, updateTime: time };
const summary = { deliveryId: '880000000000073001', eventId: 'evt_01JA7B1KX9E5ZW', eventType: 'refund.failed', endpointId: '880000000000072001',
  status: 'DEAD', attemptCount: 2, lastAttemptTime: time, replayOfDeliveryId: null, createTime: time };
const attempt = (attemptNo: number) => ({ attemptNo, requestUrl: endpoint.url, rotationNo: 1, graceRotationNo: null, status: 'FAILED',
  httpStatus: 503, failureCode: 'HTTP_STATUS', durationMs: 812, startedTime: time, finishedTime: time });

test('Client 与端点解码：校验 ID、状态与事件类型，未知值失败关闭', () => {
  const client = { clientId: '880000000000071001', clientName: 'Acme Trading LLC', ownerStatus: 'ACTIVE', securityStatus: 'ACTIVE', endpointCount: 3 };
  assert.equal(decodeClient(client).endpointCount, 3);
  assert.throws(() => decodeClient({ ...client, ownerStatus: 'CLOSED' }), ApiError);
  assert.throws(() => decodeClient({ ...client, clientId: 880000000000071001 }), ApiError);
  assert.equal(decodeEndpoints([endpoint])[0].signingSecret.rotationNo, 2);
  assert.throws(() => decodeEndpoints(endpoint), ApiError);
  assert.throws(() => decodeEndpoints([{ ...endpoint, status: 'PAUSED' }]), ApiError);
  assert.throws(() => decodeEndpoints([{ ...endpoint, eventTypes: ['webhook.ping'] }]), ApiError);
  assert.throws(() => decodeEndpoints([{ ...endpoint, eventTypes: [] }]), ApiError);
  assert.throws(() => decodeEndpoints([{ ...endpoint, signingSecret: null }]), ApiError);
  assert.equal(decodeEndpoints([{ ...endpoint, signingSecret: { ...endpoint.signingSecret, previousValidUntil: null } }])[0].signingSecret.previousValidUntil, null);
});

test('密钥格式：新建与轮换结果必须是 whsec_ 加 43 位，解码错误不回显明文', () => {
  assert.ok(secretPattern.test(secret));
  assert.ok(!secretPattern.test('whsec_short') && !secretPattern.test(`whsec_${'A'.repeat(42)}=`));
  assert.equal(decodeEndpointCreated({ endpointId: '1', version: 1, signingSecret: secret }).signingSecret, secret);
  const leaked = 'whsec_bad secret value';
  assert.throws(() => decodeEndpointCreated({ endpointId: '1', version: 1, signingSecret: leaked }),
    (error: unknown) => error instanceof ApiError && !error.message.includes(leaked));
  assert.equal(decodeSecretRotation({ rotationNo: 3, signingSecret: secret, previousValidUntil: null }).previousValidUntil, null);
  assert.throws(() => decodeSecretRotation({ rotationNo: 0, signingSecret: secret, previousValidUntil: null }), ApiError);
  assert.equal(decodeEndpointMutation({ endpointId: '7', version: 4 }, '7').version, 4);
  assert.throws(() => decodeEndpointMutation({ endpointId: '8', version: 4 }, '7'), ApiError);
});

test('测试结果、投递与 Replay 解码', () => {
  assert.equal(decodeTestResult({ result: 'SUCCEEDED', httpStatus: 204, failureCode: null, durationMs: 136 }).httpStatus, 204);
  assert.throws(() => decodeTestResult({ result: 'FAILED', httpStatus: null, failureCode: null, durationMs: null }), ApiError);
  assert.throws(() => decodeTestResult({ result: 'FAILED', httpStatus: null, failureCode: 'HTTP_STATUS', durationMs: 10 }), ApiError);
  assert.throws(() => decodeTestResult({ result: 'FAILED', httpStatus: null, failureCode: 'SOCKET_RESET', durationMs: 10 }), ApiError);
  assert.equal(decodeDeliverySummary(summary).status, 'DEAD');
  assert.throws(() => decodeDeliverySummary({ ...summary, status: 'FAILED' }), ApiError);
  assert.throws(() => decodeDeliverySummary({ ...summary, replayOfDeliveryId: 'abc' }), ApiError);
  const detail = { ...summary, requestedBy: null, attempts: [attempt(1), attempt(2)],
    event: { eventId: summary.eventId, eventType: summary.eventType, schemaVersion: 1, sourceSystem: 'commerce-service', envelope: { eventId: summary.eventId } } };
  assert.equal(decodeDeliveryDetail(detail, summary.deliveryId).attempts.length, 2);
  assert.throws(() => decodeDeliveryDetail(detail, '999'), ApiError);
  assert.throws(() => decodeDeliveryDetail({ ...detail, event: { ...detail.event, envelope: [] } }, summary.deliveryId), ApiError);
  assert.throws(() => decodeDeliveryDetail({ ...detail, attempts: [{ ...attempt(1), status: 'UNKNOWN' }] }, summary.deliveryId), ApiError);
  assert.equal(decodeReplay({ deliveryId: '42' }).deliveryId, '42');
  assert.throws(() => decodeReplay({ deliveryId: '' }), ApiError);
});

test('中文映射：状态、失败代码与测试结果摘要', () => {
  assert.equal(endpointStatusLabels.DISABLED, '已停用');
  assert.deepEqual(Object.values(deliveryStatusLabels), ['待投递', '处理中', '等待重试', '已送达', '已放弃', '已取消']);
  assert.equal(failureText('HTTP_STATUS', 503), '对方返回 HTTP 503');
  assert.equal(failureText('BLOCKED_ADDRESS', null), '地址解析到内网，已拦截');
  assert.equal(failureText('INTERRUPTED', null), '发送中断（结果未知）');
  assert.equal(testResultText({ result: 'SUCCEEDED', httpStatus: 204, failureCode: null, durationMs: 136 }), '成功 · HTTP 204 · 136 ms');
  assert.equal(testResultText({ result: 'FAILED', httpStatus: 503, failureCode: 'HTTP_STATUS', durationMs: 812 }), '失败 · 对方返回 HTTP 503 · 耗时 812 ms');
  assert.equal(testResultText({ result: 'FAILED', httpStatus: null, failureCode: 'TIMEOUT', durationMs: null }), '失败 · 超时');
  assert.equal(clientAccessText({ ownerStatus: 'ACTIVE', securityStatus: 'ACTIVE' }), '正常');
  assert.equal(clientAccessText({ ownerStatus: 'ACTIVE', securityStatus: 'SUSPENDED' }), '平台访问已暂停');
  assert.equal(clientAccessText({ ownerStatus: 'PENDING', securityStatus: 'ACTIVE' }), '平台访问未开通');
});

test('只有已结束的投递可以 Replay；尝试最新在前', () => {
  assert.deepEqual(['CREATED', 'PROCESSING', 'RETRY_WAIT', 'SUCCEEDED', 'DEAD', 'CANCELLED'].filter(s => isReplayable(s as never)),
    ['SUCCEEDED', 'DEAD', 'CANCELLED']);
  assert.deepEqual(newestAttemptsFirst([attempt(1), attempt(3), attempt(2)] as never).map(a => a.attemptNo), [3, 2, 1]);
});

test('投递时间范围默认最近 24 小时，转 UTC，拒绝超过 31 天或倒置的范围', () => {
  const now = new Date('2026-10-16T15:00:30');
  const range = defaultDeliveryRange(now);
  assert.equal(range.preset, 'CUSTOM'); assert.equal(range.end, '2026-10-16T15:01:00'); assert.equal(range.start, '2026-10-15T15:01:00');
  const filters = deliveryFilters('880000000000071001', { eventId: ' evt_1 ', status: 'DEAD', endpointId: '' }, now);
  assert.equal(filters.clientId, '880000000000071001'); assert.equal(filters.eventId, 'evt_1'); assert.equal(filters.status, 'DEAD');
  assert.equal(filters.endpointId, undefined); assert.equal(filters.eventType, undefined);
  assert.equal(new Date(filters.createdTo).getTime() - new Date(filters.createdFrom).getTime(), 86_400_000);
  assert.ok(filters.createdFrom.endsWith('Z'));
  assert.doesNotThrow(() => deliveryFilters('1', { timeRange: { preset: 'CUSTOM', start: '2026-09-15T00:00:00', end: '2026-10-16T00:00:00' } }, now));
  assert.throws(() => deliveryFilters('1', { timeRange: { preset: 'CUSTOM', start: '2026-09-01T00:00:00', end: '2026-10-16T00:00:00' } }, now), /31 天/);
  assert.throws(() => deliveryFilters('1', { timeRange: { preset: 'CUSTOM', start: '2026-10-16T00:00:00', end: '2026-10-15T00:00:00' } }, now), /晚于/);
  assert.throws(() => deliveryFilters('1', { timeRange: { preset: 'CUSTOM', start: '2026-10-16T00:00:00' } }, now), /完整/);
});

test('请求体：事件按固定顺序去重、原因去空白；测试事件不带请求体', () => {
  const body = endpointBody({ url: ' https://hooks.acme.example/paytech ', eventTypes: ['refund.succeeded', 'order.paid', 'order.paid'], reason: ' 接入 ' });
  assert.deepEqual(body, { url: 'https://hooks.acme.example/paytech', eventTypes: ['order.paid', 'refund.succeeded'], expectedVersion: 0, reason: '接入' });
  assert.equal(endpointBody({ url: 'https://a.example', eventTypes: [...eventTypes], reason: 'r' }, 3).expectedVersion, 3);
  assert.deepEqual(statusBody('DISABLED', 4, ' 暂停 '), { status: 'DISABLED', expectedVersion: 4, reason: '暂停' });
  assert.deepEqual(rotateBody(true, ' 泄露 '), { immediate: true, reason: '泄露' });
  assert.deepEqual(replayBody(' 重发 '), { reason: '重发' });
  const request = testEndpointRequest('key-1');
  assert.equal(request.method, 'POST'); assert.equal('body' in request, false);
  assert.deepEqual(request.headers, { 'Content-Type': 'application/json', 'Idempotency-Key': 'key-1' });
  assert.notEqual(testEndpointRequest().headers, testEndpointRequest().headers);
  const first = webhookCommand('/e', 'PUT', { a: 1 });
  assert.equal(webhookCommand('/e', 'PUT', { a: 1 }, first).key, first.key);
  assert.notEqual(webhookCommand('/e', 'PUT', { a: 2 }, first).key, first.key);
});

test('URL 查询参数恢复 Client 与标签页；地址错误码回填地址字段', () => {
  assert.deepEqual(readWebhookQuery(new URLSearchParams('clientId=880000000000071001&tab=deliveries')), { clientId: '880000000000071001', tab: 'deliveries' });
  assert.deepEqual(readWebhookQuery(new URLSearchParams('clientId=abc&tab=x')), { clientId: null, tab: 'endpoints' });
  assert.equal(hostAndPath('https://erp.acme.example/webhooks/refunds?x=1'), 'erp.acme.example/webhooks/refunds');
  assert.equal(hostAndPath('https://hooks.acme.example/'), 'hooks.acme.example');
  assert.equal(urlErrorMessage(new ApiError(400, 'WEBHOOK_URL_NOT_PUBLIC', '', '')), '端点地址指向内网或保留地址');
  assert.equal(urlErrorMessage(new ApiError(400, 'WEBHOOK_URL_UNRESOLVABLE', '', '')), '端点主机名无法解析');
  assert.equal(urlErrorMessage(new ApiError(400, 'WEBHOOK_URL_NOT_ALLOWED', '', '')), '地址格式不正确或协议不允许');
  assert.equal(urlErrorMessage(new ApiError(400, 'BUSINESS_CHECK_FAILED', '', '端点地址指向内网或保留地址')), null);
  assert.equal(urlErrorMessage(new ApiError(409, 'WEBHOOK_URL_NOT_PUBLIC', '', '')), null);
});
