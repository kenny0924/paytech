import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeTenant, decodeMember, decodeRole, decodePermission, decodeSource, decodeEvaluated, decodeMutation, filters, replaceQuery } from './tenantAccess.ts';
import type { Tenant } from './tenantAccess.ts';
import { configurationCommand } from '../configuration/configurationModel.ts';
const at = '2026-09-14T06:00:00Z';
const tenant: Tenant = { tenantId: '81001', tenantType: 'CLIENT', ownerRefId: '31001', tenantName: '合成客户', ownerStatus: 'ACTIVE', securityStatus: 'ACTIVE', version: 1,
  tenantAccessAllowed: true, restrictionReasons: [], createdAt: at, updatedAt: at };
const member = { tenantId: '81001', memberId: '92001', accountId: '91001', emailMasked: 'a***@example.test', memberType: 'OWNER', memberStatus: 'ACTIVE', accountStatus: 'ACTIVE', createdAt: at, updatedAt: at };
const role = { grantId: '93001', roleId: '94001', roleCode: 'TEST_READER', roleName: '只读角色', roleScope: 'TENANT', roleTenantId: '81001', roleStatus: 'ACTIVE', grantStatus: 'ACTIVE', effectiveFrom: at, effectiveTo: null, effective: true, reasons: [] };
const permission = { permissionId: '95001', permissionCode: 'TEST_READ', resourceType: 'ORDER', actionCode: 'READ', permissionStatus: 'ACTIVE', sourceCount: 2, effectiveSourceCount: 1, effective: true, reasons: [] };
const source = { ...role, rolePermissionId: '96001', rolePermissionStatus: 'ACTIVE' };
test('客户及合作伙伴租户详情都保留双状态，未知类型不展示', () => {
  assert.deepEqual(decodeTenant(tenant, tenant.tenantId), tenant);
  assert.equal(decodeTenant({ ...tenant, tenantType: 'PARTNER' }).tenantType, 'PARTNER');
  for (const tenantType of ['INTERNAL', 'UNKNOWN', null]) assert.throws(() => decodeTenant({ ...tenant, tenantType }));
});
test('来源未激活的租户恢复安全状态后仍显示访问未满足', () => {
  const blocked = { ...tenant, ownerStatus: 'PENDING', tenantAccessAllowed: false, restrictionReasons: [{ code: 'TENANT_OWNER_INACTIVE', message: '尚未激活' }] };
  assert.equal(decodeTenant(blocked).tenantAccessAllowed, false);
  assert.throws(() => decodeTenant({ ...blocked, tenantAccessAllowed: true }));
});
test('详情身份必须匹配路径且大整数始终是字符串', () => {
  assert.throws(() => decodeTenant(tenant, '81002'));
  for (const tenantId of [81001, '01', '9223372036854775808', '-1']) assert.throws(() => decodeTenant({ ...tenant, tenantId }));
});
test('成员详情限制到同一租户和成员，额外秘密字段被移除', () => {
  assert.deepEqual(decodeMember({ ...member, rawEmail: 'SECRET', emailVaultToken: 'SECRET' }, '81001', '92001'), member);
  assert.throws(() => decodeMember(member, '81002'));
  assert.throws(() => decodeMember(member, '81001', '92002'));
  assert.equal(decodeMember({ ...member, emailMasked: null }, '81001').emailMasked, null);
});
test('平台角色与租户角色按精确范围展示', () => {
  assert.deepEqual(decodeRole(role, '81001'), role);
  assert.equal(decodeRole({ ...role, roleScope: 'PLATFORM', roleTenantId: null }, '81001').roleScope, 'PLATFORM');
  assert.throws(() => decodeRole(role, '81002'));
  assert.throws(() => decodeRole({ ...role, roleScope: 'PLATFORM' }, '81001'));
});
test('包含微秒的有效区间可被正确解析，同一时刻或反向区间拒绝', () => {
  assert.equal(decodeRole({ ...role, effectiveTo: '2026-09-14T06:00:00.000001Z' }, '81001').effective, true);
  for (const effectiveTo of [at, '2026-09-13T06:00:00Z']) assert.throws(() => decodeRole({ ...role, effectiveTo }, '81001'));
});
test('权限允许多个来源，其中一个有效就可显示有效', () => {
  assert.equal(decodePermission(permission).effective, true);
  assert.throws(() => decodePermission({ ...permission, effectiveSourceCount: 0 }));
  assert.throws(() => decodePermission({ ...permission, effectiveSourceCount: 3 }));
  assert.throws(() => decodePermission({ ...permission, sourceCount: Number.MAX_SAFE_INTEGER + 1 }));
});
test('所有来源无效时保留原因，但有效汇总不能附带失效原因', () => {
  const reasons = [{ code: 'GRANT_EXPIRED', message: '授权已过期' }];
  assert.equal(decodePermission({ ...permission, effective: false, effectiveSourceCount: 0, reasons }).reasons[0].code, 'GRANT_EXPIRED');
  assert.throws(() => decodePermission({ ...permission, reasons }));
});
test('来源保留角色授权和权限关联的不同状态', () => {
  assert.deepEqual(decodeSource(source, '81001'), source);
  assert.equal(decodeSource({ ...source, rolePermissionStatus: 'REVOKED', effective: false, reasons: [{ code: 'ROLE_PERMISSION_REVOKED', message: '权限关联撤销' }] }, '81001').effective, false);
  assert.throws(() => decodeSource({ ...source, rolePermissionStatus: 'UNKNOWN' }, '81001'));
});
test('未知、重复、乱序原因及缺失原因拒绝作为成功响应展示', () => {
  for (const reasons of [null, [{ code: 'UNKNOWN', message: '未知' }], [{ code: 'GRANT_EXPIRED', message: '过期' }, { code: 'GRANT_REVOKED', message: '撤销' }], [{ code: 'GRANT_EXPIRED', message: '过期' }, { code: 'GRANT_EXPIRED', message: '过期' }]])
    assert.throws(() => decodeRole({ ...role, effective: false, reasons }, '81001'));
});
test('带计算时刻的分页拒绝重复身份、超限大小和不安全总数', () => {
  const page = { items: [permission], page: 1, size: 10, total: 1, evaluatedAt: at };
  assert.equal(decodeEvaluated(page, decodePermission, p => p.permissionId).evaluatedAt, at);
  for (const invalid of [{ ...page, items: [permission, permission] }, { ...page, size: 200 }, { ...page, evaluatedAt: null }, { ...page, total: Number.MAX_SAFE_INTEGER + 1 }])
    assert.throws(() => decodeEvaluated(invalid, decodePermission, p => p.permissionId));
});
test('暂停与恢复响应仅接受同一租户、目标状态以及本次版本', () => {
  const response = { tenantId: '81001', securityStatus: 'SUSPENDED', version: 2 };
  assert.deepEqual(decodeMutation(response, tenant, 'SUSPENDED'), response);
  for (const invalid of [{ ...response, tenantId: '81002' }, { ...response, securityStatus: 'ACTIVE' }, { ...response, version: 3 }]) assert.throws(() => decodeMutation(invalid, tenant, 'SUSPENDED'));
});
test('网络结果不确定时保留同一命令，用户修改原因后生成新命令', () => {
  const path = '/tenant-access-admin/v1/tenants/81001/security-status'; const body = { securityStatus: 'SUSPENDED', expectedVersion: 1, reason: '合成暂停' };
  const first = configurationCommand(path, 'POST', body);
  assert.equal(configurationCommand(path, 'POST', body, first).key, first.key);
  assert.notEqual(configurationCommand(path, 'POST', { ...body, reason: '更正原因' }, first).key, first.key);
});
test('筛选及来源翻页保留 URL 中的其他标签和分页状态', () => {
  assert.deepEqual(filters({ tenantName: '  Sample_100%  ', tenantType: 'CLIENT', ownerRefId: ' ' }), { tenantName: 'Sample_100%', tenantType: 'CLIENT' });
  const changed = replaceQuery('?tab=permissions&permissionPage=3&rolePage=2&permission=95001', { sourcePage: '2', sourceSize: '10' });
  const params = new URLSearchParams(changed); assert.equal(params.get('rolePage'), '2'); assert.equal(params.get('permissionPage'), '3'); assert.equal(params.get('permission'), '95001');
  assert.equal(new URLSearchParams(replaceQuery(changed, { permission: null, sourcePage: null })).get('permission'), null);
});
