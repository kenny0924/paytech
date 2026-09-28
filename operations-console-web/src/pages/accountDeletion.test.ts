import assert from 'node:assert/strict';
import test from 'node:test';
import type { Account } from '../api/types.ts';
import { accountDeletionCommand, canDeleteAccount } from './accountDeletion.ts';
import { roleDeletionCommand } from './roleDeletion.ts';

const account: Account = {
  accountId: '900000000000000888', emailMasked: 's***@example.test', status: 'ACTIVE',
  passwordChangeRequired: false, lockedUntil: null, version: 4, memberId: '900000000000000999', memberVersion: 6, roles: [],
};

test('account deletion includes both expected versions and only the trimmed audit reason', () => {
  const command = accountDeletionCommand(account, '  移除离职人员  ', null);
  assert.equal(command.path, '/iam/v1/accounts/900000000000000888');
  assert.deepEqual(JSON.parse(command.payload), { expectedVersion: 4, expectedMemberVersion: 6, reason: '移除离职人员' });
});

test('account deletion retries reuse keys only for the same identity, account version, member version and reason', () => {
  const first = accountDeletionCommand(account, '移除人员', null);
  assert.equal(accountDeletionCommand(account, '  移除人员  ', first).key, first.key);
  for (const changed of [{ ...account, version: 5 }, { ...account, memberVersion: 7 }, { ...account, accountId: '900000000000000889' }]) {
    assert.notEqual(accountDeletionCommand(changed, '移除人员', first).key, first.key);
  }
  assert.notEqual(accountDeletionCommand(account, '调整人员', first).key, first.key);
  assert.notEqual(accountDeletionCommand(account, '移除人员', null).key, first.key);
  const role = { roleId: account.accountId, roleCode: 'TEST_ROLE', roleName: '测试角色', systemRole: false, employeeCount: 0, version: 4 };
  assert.notEqual(roleDeletionCommand(role, '移除人员', first).key, first.key);
});

test('delete entry excludes the current actor and every status except active or disabled', () => {
  assert.equal(canDeleteAccount(account, account.accountId), false);
  assert.equal(canDeleteAccount({ ...account, status: 'DISABLED' }, account.accountId), false);
  assert.equal(canDeleteAccount(account, 'another-account'), true);
  assert.equal(canDeleteAccount({ ...account, status: 'DISABLED' }, 'another-account'), true);
  for (const status of ['CLOSED', 'PENDING_VERIFICATION', 'UNKNOWN']) {
    assert.equal(canDeleteAccount({ ...account, status }, 'another-account'), false);
  }
});
