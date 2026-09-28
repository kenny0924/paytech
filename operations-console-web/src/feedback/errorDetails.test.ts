import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { copyableErrorDetails, errorDetails, errorNotificationKey } from './errorDetails.ts';

test('business failures retain the API boundary safe message and request correlation', () => {
  const details = errorDetails(new ApiError(400, 'BUSINESS_CHECK_FAILED', 'request-17', '不能停用当前登录账号'));
  assert.deepEqual(details, { code: 'BUSINESS_CHECK_FAILED', message: '不能停用当前登录账号', requestId: 'request-17' });
  const system = errorDetails(new ApiError(503, 'SERVICE_UNAVAILABLE', 'request-18', 'password=do-not-display'));
  assert.equal(system?.message, '服务暂时不可用，请稍后重试。');
});

test('unknown thrown values use a safe fallback without exposing messages or stacks', () => {
  for (const failure of [new Error('password=do-not-display'), 'secret-token',
    { code: 'INTERNAL', message: 'private payload', requestId: 'untrusted-id' }, undefined]) {
    assert.deepEqual(errorDetails(failure), {
      code: 'REQUEST_FAILED', message: '请求未完成，请稍后重试。', requestId: '',
    });
  }
});

test('cancellation and responses from an old session stay silent', () => {
  assert.equal(errorDetails(new DOMException('private cancellation reason', 'AbortError')), null);
  assert.equal(errorDetails(new ApiError(401, 'SESSION_CHANGED', 'request-old')), null);
  assert.notEqual(errorDetails(new ApiError(401, 'OPERATIONS_SESSION_INVALID')), null);
});

test('deduplication uses request ID plus code, or code plus message when the ID is absent', () => {
  const first = { code: 'INVALID_REQUEST', message: '参数有误', requestId: 'request-1' };
  assert.equal(errorNotificationKey(first), errorNotificationKey({ ...first, message: '另一条安全消息' }));
  assert.notEqual(errorNotificationKey(first), errorNotificationKey({ ...first, code: 'BUSINESS_CHECK_FAILED' }));
  assert.notEqual(errorNotificationKey(first), errorNotificationKey({ ...first, requestId: 'request-2' }));
  const anonymous = { ...first, requestId: '' };
  assert.equal(errorNotificationKey(anonymous), errorNotificationKey({ ...anonymous }));
  assert.notEqual(errorNotificationKey(anonymous), errorNotificationKey({ ...anonymous, message: '需要重新输入' }));
});

test('copy text contains exactly the three displayed fields with an explicit missing request ID', () => {
  const missing = errorDetails(new ApiError(429, 'RATE_LIMITED', '   '))!;
  assert.deepEqual(JSON.parse(copyableErrorDetails(missing)), {
    code: 'RATE_LIMITED', message: '操作过于频繁，请稍后再试。', requestId: '未返回',
  });
  const known = errorDetails(new ApiError(403, 'OPERATIONS_PERMISSION_DENIED', 'request-3'))!;
  assert.deepEqual(JSON.parse(copyableErrorDetails(known)), known);
});
