import {test} from 'node:test';
import assert from 'node:assert/strict';
import {RequestFailure} from './auth.ts';
import {authIssues, authFailure} from './auth-form.ts';

test('forgot and resend use the same required and malformed email feedback', () => {
  for (const kind of ['forgot','sent'] as const) {
    assert.deepEqual(authIssues(kind,{email:'',password:'',confirm:''}),{email:'请输入邮箱地址'});
    assert.deepEqual(authIssues(kind,{email:'dsadsa',password:'',confirm:''}),{email:'请输入有效的邮箱地址'});
    assert.deepEqual(authIssues(kind,{email:'client+tag@例子.测试',password:'',confirm:''}),{});
  }
});

test('registration and reset report all field issues and preserve password whitespace', () => {
  assert.deepEqual(authIssues('register',{email:'invalid',password:'',confirm:''}),{
    email:'请输入有效的邮箱地址',password:'请输入密码',confirm:'请再次输入密码',
  });
  assert.deepEqual(authIssues('reset',{email:'',password:'short',confirm:'different'}),{
    password:'密码至少需要 15 个字符',confirm:'两次输入的密码不一致',
  });
  assert.deepEqual(authIssues('reset',{email:'',password:' a long password ',confirm:'a long password'}),{confirm:'两次输入的密码不一致'});
  assert.deepEqual(authIssues('reset',{email:'',password:' a long password ',confirm:' a long password '}),{});
});

test('server fields map to the visible inputs without diagnostics or unknown-field leakage', () => {
  assert.deepEqual(authFailure(new RequestFailure(422,'private detail','trace',{email:'raw email error',newPassword:'raw password error',tenantId:'hidden'}),'reset'),{
    fields:{password:'密码不符合要求，请调整后重试'},message:'',
  });
  assert.deepEqual(authFailure(new RequestFailure(422,'detail','trace',{email:'raw email error'}),'forgot'),{
    fields:{email:'请输入有效的邮箱地址'},message:'',
  });
});

test('dependency and link failures remain form feedback instead of invalid input', () => {
  assert.deepEqual(authFailure(new RequestFailure(503,'internal detail','trace'),'forgot'),{fields:{},message:'服务暂时不可用，请稍后重试'});
  assert.deepEqual(authFailure(new RequestFailure(0,'network detail'),'sent'),{fields:{},message:'网络连接失败，请检查后重试'});
  assert.deepEqual(authFailure(new RequestFailure(429,'limiter detail'),'register'),{fields:{},message:'尝试次数过多，请稍后再试'});
  assert.deepEqual(authFailure(new RequestFailure(400,'expired','trace',{},'CLIENT_EMAIL_LINK_INVALID'),'reset'),{fields:{},message:'链接已失效，请重新申请邮件'});
});
