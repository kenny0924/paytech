import {test} from 'node:test';
import assert from 'node:assert/strict';
import {RequestFailure} from './auth.ts';
import {loginIssues, loginFailure} from './login-feedback.ts';

test('login reports required and malformed fields without enforcing new-password policy', () => {
  assert.deepEqual(loginIssues('', ''), {email:'Enter your email address', password:'Enter your password'});
  assert.equal(loginIssues('dasdas', 'short').email, 'Enter a valid email address');
  assert.deepEqual(loginIssues(' client+tag@example.test ', 'short'), {});
  assert.equal(loginIssues('a@b@c.test', 'password').email, 'Enter a valid email address');
  assert.equal(loginIssues('client@example', 'password').email, 'Enter a valid email address');
  assert.deepEqual(loginIssues('client@例子.测试', 'password'), {});
});

test('credential rejection marks both fields without exposing diagnostic details', () => {
  const result=loginFailure(new RequestFailure(401,'演示邮箱或密码错误','browser-fixture',{},'CLIENT_AUTHENTICATION_FAILED'));
  assert.deepEqual(result, {message:'Incorrect email or password. Please try again.', invalidCredentials:true});
});

test('network, rate and service failures do not mark the credentials as incorrect', () => {
  assert.deepEqual(loginFailure(new RequestFailure(0,'transport details')), {message:'Network connection failed. Please check it and try again.', invalidCredentials:false});
  assert.deepEqual(loginFailure(new RequestFailure(429,'limiter details')), {message:'Too many attempts. Please try again later.', invalidCredentials:false});
  assert.deepEqual(loginFailure(new RequestFailure(503,'internal dependency details','req-id')), {message:'Sign-in is temporarily unavailable. Please try again later.', invalidCredentials:false});
  assert.deepEqual(loginFailure(new Error('internal error')), {message:"We can't sign you in right now. Please try again later.", invalidCredentials:false});
});
