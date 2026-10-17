import assert from 'node:assert/strict';
import test from 'node:test';
import {
  accessKeyPattern, attentionStatus, decodeMutation, decodeProvider, decodeRequestDetail, decodeTemplateDetail,
  decodeTemplateSummary, decodeTestSend, eventQueuePattern, eventReceiving, regionPattern, secretKeyPattern, testSendHint,
  missingRequired, notificationCommand, placeholders, recordFilters,
} from './notification.ts';

const time = '2026-10-15T08:00:00Z';

test('占位符按出现顺序去重，忽略格式不合法的写法', () => {
  assert.deepEqual(placeholders('退款 ${refundNo}', '${name} ${refundNo} ${1bad} ${amount}'), ['refundNo', 'name', 'amount']);
  assert.deepEqual(missingRequired('请打开 ${lnk}', ['link']), ['link']);
  assert.deepEqual(missingRequired('请打开 ${link}', ['link']), []);
});

test('列表状态取最需要关注的投递状态', () => {
  assert.equal(attentionStatus({ SENT: 1, RETRY_WAIT: 1 }), 'RETRY_WAIT');
  assert.equal(attentionStatus({ DELIVERED: 2, FAILED: 1 }), 'FAILED');
  assert.equal(attentionStatus({}), null);
});

test('发送记录筛选默认最近 7 天，转 UTC，拒绝超过 31 天或倒置的范围', () => {
  const now = new Date('2026-10-15T10:00:00');
  const filters = recordFilters({ sourceSystem: ' payment ', deliveryStatus: 'FAILED', templateCode: '' }, now);
  assert.equal(filters.sourceSystem, 'payment'); assert.equal(filters.deliveryStatus, 'FAILED'); assert.equal(filters.templateCode, undefined);
  assert.equal(new Date(filters.createdTo).getTime() - new Date(filters.createdFrom).getTime(), 7 * 86_400_000);
  assert.throws(() => recordFilters({ timeRange: { preset: 'CUSTOM', start: '2026-08-01T00:00:00', end: '2026-10-01T00:00:00' } }, now), /31 天/);
  assert.throws(() => recordFilters({ timeRange: { preset: 'CUSTOM', start: '2026-10-02T00:00:00', end: '2026-10-01T00:00:00' } }, now), /晚于/);
  assert.throws(() => recordFilters({ timeRange: { preset: 'CUSTOM', start: '2026-10-02T00:00:00' } }, now), /完整/);
});

test('同一目标与正文重试复用 Idempotency-Key，改草稿换新 Key', () => {
  const first = notificationCommand('/t', 'PUT', { a: 1 });
  assert.equal(notificationCommand('/t', 'PUT', { a: 1 }, first).key, first.key);
  assert.notEqual(notificationCommand('/t', 'PUT', { a: 2 }, first).key, first.key);
});

test('解码器拒绝缺字段、编码不一致或非法状态', () => {
  const summary = { templateCode: 'NOTIFICATION_TEST', medium: 'EMAIL', status: 'ACTIVE', locales: ['zh-CN'], transientTemplate: false,
    routeStatus: null, version: 1, updateTime: time };
  assert.equal(decodeTemplateSummary(summary).templateCode, 'NOTIFICATION_TEST');
  assert.throws(() => decodeTemplateSummary({ ...summary, status: 'PAUSED' }));
  const detail = { ...summary, requiredParameters: ['link'], contents: [{ locale: 'zh-CN', subjectTemplate: 's', bodyTemplate: '${link}', version: 1, updateTime: time }],
    route: { primaryProviderCode: 'SES', fallbackProviderCode: null, status: 'ACTIVE', version: 1, updateTime: time } };
  assert.equal(decodeTemplateDetail(detail, 'NOTIFICATION_TEST').route?.primaryProviderCode, 'SES');
  assert.throws(() => decodeTemplateDetail(detail, 'OTHER'));
  assert.throws(() => decodeMutation({ code: 'A', version: 0 }, 'A'));
  assert.throws(() => decodeMutation({ code: 'B', version: 2 }, 'A'));
  assert.equal(decodeMutation({ code: 'zh-CN', version: 2 }).version, 2);
  const provider = { providerCode: 'SES', medium: 'EMAIL', providerType: 'AMAZON_SES', status: 'ACTIVE', region: 'me-central-1',
    fromAddress: null, configurationSet: null, credentialConfigured: false, credentialUpdateTime: null,
    missingConfiguration: ['fromAddress', 'credential'], adapterConfigured: true, eventQueueUrl: null, eventPollTime: null,
    eventPollError: null, eventReceiveTime: null, version: 1, updateTime: time, references: [] };
  assert.deepEqual(decodeProvider(provider).missingConfiguration, ['fromAddress', 'credential']);
  assert.throws(() => decodeProvider({ ...provider, eventQueueUrl: undefined }), '缺少事件字段即合同漂移');
  assert.throws(() => decodeProvider({ ...provider, eventPollError: 1 }));
  const queue = 'https://sqs.ap-southeast-1.amazonaws.com/123456789012/ses-events';
  assert.ok(eventQueuePattern.test(queue));
  for (const bad of ['http://sqs.ap-southeast-1.amazonaws.com/123456789012/q', 'https://sqs.ap-southeast-1.example.com/123456789012/q',
    'https://sqs.ap-southeast-1.amazonaws.com/1234/q', `${queue}?x=1`]) assert.ok(!eventQueuePattern.test(bad), bad);
  assert.deepEqual(eventReceiving(provider), { kind: 'none' });
  assert.deepEqual(eventReceiving({ ...provider, eventQueueUrl: queue }), { kind: 'waiting' });
  assert.deepEqual(eventReceiving({ ...provider, eventQueueUrl: queue, eventPollError: 'SQS_ACCESS_DENIED', eventPollTime: time }),
    { kind: 'failed', error: 'SQS_ACCESS_DENIED', lastPoll: time });
  assert.deepEqual(eventReceiving({ ...provider, eventQueueUrl: queue, eventPollTime: time }), { kind: 'ok', lastPoll: time, lastEvent: null });
  assert.throws(() => decodeProvider({ ...provider, adapterConfigured: 'yes' }));
  assert.throws(() => decodeProvider({ ...provider, missingConfiguration: null }));
  assert.throws(() => decodeProvider({ ...provider, credentialConfigured: undefined }));
  assert.equal(decodeTestSend({ outcome: 'REJECTED', resultCode: 'SES_CREDENTIAL_REJECTED' }).outcome, 'REJECTED');
  assert.throws(() => decodeTestSend({ outcome: 'SENT', resultCode: 'SES_ACCEPTED' }));
  assert.match(testSendHint({ outcome: 'REJECTED', resultCode: 'SES_CREDENTIAL_REJECTED' }), /IAM 策略/);
  assert.match(testSendHint({ outcome: 'REJECTED', resultCode: 'SES_MESSAGE_REJECTED' }), /沙箱/);
  assert.match(testSendHint({ outcome: 'REJECTED', resultCode: 'SES_CLIENT_ERROR' }), /IAM 权限/);
  assert.match(testSendHint({ outcome: 'UNKNOWN', resultCode: 'TIMEOUT' }), /查看收件箱/);
  assert.match(testSendHint({ outcome: 'ACCEPTED', resultCode: 'SES_ACCEPTED' }), /已接受/);
  assert.throws(() => decodeTestSend({ outcome: 'ACCEPTED', resultCode: 'bad code' }));
  assert.ok(regionPattern.test('me-central-1') && !regionPattern.test('Middle East'));
  assert.ok(accessKeyPattern.test('AKIASYNTHETIC0000001') && !accessKeyPattern.test('akia'));
  assert.ok(secretKeyPattern.test('abc/DEF+123') && !secretKeyPattern.test('has space'));
  const record = { requestId: '1', parameterKeys: [], deliveries: [{ deliveryId: '2', locale: null, parameterKeys: [], status: 'SENT', attemptCount: 1,
    nextRetryTime: null, updateTime: time, attempts: [{ attemptId: '3', status: 'ACCEPTED', startedTime: time, eventOutcome: 'HARD_BOUNCE',
      eventOutcomeCode: 'Permanent/General', eventOutcomeTime: time, complaintCode: null, complaintTime: null }] }] };
  assert.equal(decodeRequestDetail(record, '1').deliveries.length, 1);
  const withAttempt = (patch: Record<string, unknown>) => ({ ...record, deliveries: [{ ...record.deliveries[0],
    attempts: [{ ...record.deliveries[0].attempts[0], ...patch }] }] });
  assert.throws(() => decodeRequestDetail(withAttempt({ eventOutcome: undefined }), '1'), '缺少事件字段即合同漂移');
  assert.throws(() => decodeRequestDetail(withAttempt({ complaintTime: 5 }), '1'));
  assert.throws(() => decodeRequestDetail(record, '9'));
});
