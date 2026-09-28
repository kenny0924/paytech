import assert from 'node:assert/strict';
import test from 'node:test';
import type { RoleListItem } from '../api/types.ts';
import { roleDeletionCommand } from './roleDeletion.ts';

const role: RoleListItem = {
  roleId: '900000000000000777', roleCode: 'SUPPORT_TEAM', roleName: '客服角色', systemRole: false,
  version: 7, employeeCount: 0,
};

test('role deletion sends only the expected version and trimmed reason to the exact role path', () => {
  const command = roleDeletionCommand(role, '  清理不再使用的角色  ', null);
  assert.equal(command.path, '/iam/v1/roles/900000000000000777');
  assert.deepEqual(JSON.parse(command.payload), { expectedVersion: 7, reason: '清理不再使用的角色' });
});

test('manual deletion retries reuse a key only for the same role version and reason', () => {
  const first = roleDeletionCommand(role, '清理角色', null);
  assert.equal(roleDeletionCommand(role, '  清理角色  ', first).key, first.key);
  assert.notEqual(roleDeletionCommand(role, '重新配置角色', first).key, first.key);
  assert.notEqual(roleDeletionCommand({ ...role, version: 8 }, '清理角色', first).key, first.key);
  assert.notEqual(roleDeletionCommand({ ...role, roleId: '900000000000000778' }, '清理角色', first).key, first.key);
  assert.notEqual(roleDeletionCommand(role, '清理角色', null).key, first.key);
});
