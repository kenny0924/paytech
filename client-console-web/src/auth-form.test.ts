import {test} from 'node:test';
import assert from 'node:assert/strict';
import {RequestFailure} from './auth.ts';
import {authIssues, authFailure} from './auth-form.ts';

test('forgot and resend use the same required and malformed email feedback', () => {
  for (const kind of ['forgot','sent'] as const) {
    assert.deepEqual(authIssues(kind,{email:'',password:'',confirm:''}),{email:'Enter your email address'});
    assert.deepEqual(authIssues(kind,{email:'dsadsa',password:'',confirm:''}),{email:'Enter a valid email address'});
    assert.deepEqual(authIssues(kind,{email:'client+tag@例子.测试',password:'',confirm:''}),{});
  }
});

test('registration and reset report all field issues and preserve password whitespace', () => {
  assert.deepEqual(authIssues('register',{email:'invalid',password:'',confirm:''}),{
    email:'Enter a valid email address',password:'Enter your password',confirm:'Enter your password again',
  });
  assert.deepEqual(authIssues('reset',{email:'',password:'short',confirm:'different'}),{
    password:'Password must be at least 15 characters',confirm:"The passwords don't match",
  });
  assert.deepEqual(authIssues('reset',{email:'',password:' a long password ',confirm:'a long password'}),{confirm:"The passwords don't match"});
  assert.deepEqual(authIssues('reset',{email:'',password:' a long password ',confirm:' a long password '}),{});
});

test('server fields map to the visible inputs without diagnostics or unknown-field leakage', () => {
  assert.deepEqual(authFailure(new RequestFailure(422,'private detail','trace',{email:'raw email error',newPassword:'raw password error',tenantId:'hidden'}),'reset'),{
    fields:{password:"The password doesn't meet the requirements. Please adjust it and try again."},message:'',
  });
  assert.deepEqual(authFailure(new RequestFailure(422,'detail','trace',{email:'raw email error'}),'forgot'),{
    fields:{email:'Enter a valid email address'},message:'',
  });
});

test('dependency and link failures remain form feedback instead of invalid input', () => {
  assert.deepEqual(authFailure(new RequestFailure(503,'internal detail','trace'),'forgot'),{fields:{},message:'The service is temporarily unavailable. Please try again later.'});
  assert.deepEqual(authFailure(new RequestFailure(0,'network detail'),'sent'),{fields:{},message:'Network connection failed. Please check it and try again.'});
  assert.deepEqual(authFailure(new RequestFailure(429,'limiter detail'),'register'),{fields:{},message:'Too many attempts. Please try again later.'});
  assert.deepEqual(authFailure(new RequestFailure(400,'expired','trace',{},'CLIENT_EMAIL_LINK_INVALID'),'reset'),{fields:{},message:'This link has expired. Please request a new email.'});
});
