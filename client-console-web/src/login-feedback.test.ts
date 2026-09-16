import {test} from 'node:test';
import assert from 'node:assert/strict';
import {RequestFailure} from './auth.ts';
import {loginIssues, loginFailure} from './login-feedback.ts';

test('login reports required and malformed fields without enforcing new-password policy', () => {
  assert.deepEqual(loginIssues('', ''), {email:'请输入邮箱地址', password:'请输入密码'});
  assert.equal(loginIssues('dasdas', 'short').email, '请输入有效的邮箱地址');
  assert.deepEqual(loginIssues(' client+tag@example.test ', 'short'), {});
  assert.equal(loginIssues('a@b@c.test', 'password').email, '请输入有效的邮箱地址');
  assert.equal(loginIssues('client@example', 'password').email, '请输入有效的邮箱地址');
  assert.deepEqual(loginIssues('client@例子.测试', 'password'), {});
});

test('credential rejection marks both fields without exposing diagnostic details', () => {
  const result=loginFailure(new RequestFailure(401,'演示邮箱或密码错误','browser-fixture',{},'CLIENT_AUTHENTICATION_FAILED'));
  assert.deepEqual(result, {message:'邮箱或密码不正确，请重新输入', invalidCredentials:true});
});

test('network, rate and service failures do not mark the credentials as incorrect', () => {
  assert.deepEqual(loginFailure(new RequestFailure(0,'transport details')), {message:'网络连接失败，请检查后重试', invalidCredentials:false});
  assert.deepEqual(loginFailure(new RequestFailure(429,'limiter details')), {message:'尝试次数过多，请稍后再试', invalidCredentials:false});
  assert.deepEqual(loginFailure(new RequestFailure(503,'internal dependency details','req-id')), {message:'登录服务暂时不可用，请稍后重试', invalidCredentials:false});
  assert.deepEqual(loginFailure(new Error('internal error')), {message:'暂时无法登录，请稍后重试', invalidCredentials:false});
});
