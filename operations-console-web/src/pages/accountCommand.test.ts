import assert from 'node:assert/strict';
import test from 'node:test';
import type { Account } from '../api/types.ts';
import { accountCommand } from './accountCommand.ts';

const account: Account = {
  accountId: '900000000000000888', emailMasked: 's***@example.test', status: 'DISABLED',
  passwordChangeRequired: false, lockedUntil: null, version: 4, memberId: '900000000000000999', memberVersion: 6, roles: [],
};

test('enable uses POST with only the account version and trimmed reason', () => {
  const command = accountCommand('enable', account, { reason: '  恢复工作访问  ', roleIds: ['stale-role'] }, null);
  assert.equal(command.path, '/iam/v1/accounts/900000000000000888/enable');
  assert.equal(command.method, 'POST');
  assert.deepEqual(JSON.parse(command.payload), { expectedVersion: 4, reason: '恢复工作访问' });
});

test('disable keeps its own route and excludes hidden role fields', () => {
  const command = accountCommand('disable', account, { reason: '  暂停访问  ', roleIds: ['stale-role'] }, null);
  assert.equal(command.path, '/iam/v1/accounts/900000000000000888/disable');
  assert.equal(command.method, 'POST');
  assert.deepEqual(JSON.parse(command.payload), { expectedVersion: 4, reason: '暂停访问' });
});

test('manual status retries keep the key only for the same target, action, version and reason', () => {
  const first = accountCommand('enable', account, { reason: '恢复访问' }, null);
  assert.equal(accountCommand('enable', account, { reason: '  恢复访问  ' }, first).key, first.key);
  assert.notEqual(accountCommand('disable', account, { reason: '恢复访问' }, first).key, first.key);
  assert.notEqual(accountCommand('enable', { ...account, accountId: '900000000000000889' }, { reason: '恢复访问' }, first).key, first.key);
  assert.notEqual(accountCommand('enable', { ...account, version: 5 }, { reason: '恢复访问' }, first).key, first.key);
  assert.notEqual(accountCommand('enable', account, { reason: '恢复新职责' }, first).key, first.key);
  assert.notEqual(accountCommand('enable', account, { reason: '恢复访问' }, null).key, first.key);
});

test('create and role assignment retain their existing body and method contracts', () => {
  const values = { email: 'employee@example.test', initialPassword: 'synthetic-password', roleIds: ['123'], reason: '人员入职' };
  const create = accountCommand('create', undefined, values, null);
  assert.equal(create.path, '/iam/v1/accounts');
  assert.equal(create.method, 'POST');
  assert.deepEqual(JSON.parse(create.payload), values);
  const roles = accountCommand('roles', account, { roleIds: ['123'], reason: '调整职责' }, null);
  assert.equal(roles.path, '/iam/v1/accounts/900000000000000888/roles');
  assert.equal(roles.method, 'PUT');
  assert.deepEqual(JSON.parse(roles.payload), { roleIds: ['123'], reason: '调整职责', expectedMemberVersion: 6 });
});
