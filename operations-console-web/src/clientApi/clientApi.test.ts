import test from 'node:test';
import assert from 'node:assert/strict';
import { credentialFilters, scopeMutation, revokeMutation, scopeReferenceQuery } from './clientApi.ts';
test('数字前缀保持前缀查询；两组显式模式互不猜测', () => {
  assert.deepEqual(credentialFilters({ clientMode: 'clientName', client: '12345678', credentialMode: 'keyPrefix', credential: '12345678', scopeCode: 'checkout.read' }),
    { clientName: '12345678', keyPrefix: '12345678', scopeCode: 'checkout.read' });
  assert.deepEqual(credentialFilters({ clientMode: 'clientId', client: '123', credentialMode: 'credentialId', credential: '456' }), { clientId: '123', credentialId: '456' });
});
test('Scope 引用只发送当前标签，NONE 无分页', () => {
  assert.equal(scopeReferenceQuery('NONE', 3, 20).toString(), 'referenceKind=NONE');
  assert.equal(scopeReferenceQuery('CREDENTIAL', 3, 20).toString(), 'referenceKind=CREDENTIAL&page=3&size=20');
});
test('命令响应必须属于当前对象且具有真实版本', () => {
  assert.equal(scopeMutation({ apiScopeId: '123', scopeCode: 'checkout.read', version: 2 }, 'checkout.read').version, 2);
  assert.throws(() => scopeMutation({ apiScopeId: '123', scopeCode: 'checkout.write', version: 2 }, 'checkout.read'));
  assert.throws(() => scopeMutation({ apiScopeId: '123', scopeCode: 'checkout.read', version: 0 }, 'checkout.read'));
  assert.equal(revokeMutation({ credentialId: '123', status: 'REVOKED', version: 4 }, '123', 3).version, 4);
  assert.throws(() => revokeMutation({ credentialId: '456', status: 'REVOKED', version: 4 }, '123', 3));
  assert.throws(() => revokeMutation({ credentialId: '123', status: 'ACTIVE', version: 4 }, '123', 3));
});
