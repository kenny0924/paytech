import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { decodeMutation, pageDecoder } from './dataQueryCommon.ts';
import {
  addressLabel, connectionFingerprint, templateSourceOptions, connectionTestBody, createDataSourceBody, dataSourceFormValues, decodeConnectionTest, decodeDataSource,
  decodeDataSourcePage, deletionBlock, grantSegments, lastCheckLabel, saveGate, statusAction, updateDataSourceBody,
} from './dataSourceModel.ts';
import type { ConnectionTest, DataSource } from './dataSourceModel.ts';

const wire = {
  dataSourceId: '900000000000131001', dataSourceCode: 'PAYMENT_REPLICA', dataSourceName: '支付库从库', engineType: 'MYSQL', host: '10.0.12.21',
  port: 3306, databaseName: 'mor_payment', username: 'dq_reader', connectionParams: { sslMode: 'REQUIRED' }, maxPoolSize: 8, status: 'ACTIVE',
  lastCheckResult: 'PASSED', lastCheckMessage: null, lastCheckTime: '2026-10-22T01:14:00.000000Z', description: null, templateCount: 8,
  activeTemplateCount: 6, version: 4, createdBy: '1', updatedBy: '1', createTime: '2026-10-20T01:00:00.000000Z', updateTime: '2026-10-22T01:14:00.000000Z',
};
const source = (patch: Partial<DataSource> = {}): DataSource => ({ ...decodeDataSource(wire), ...patch });
const passed: ConnectionTest = { result: 'PASSED', connected: true, privilegesPassed: true, grants: [], violations: [], checkedAt: '2026-10-22T01:14:00Z' };

test('数据源解码：可空字段统一为 null，缺省连接参数为空对象；类型不符给通用错误', () => {
  const { connectionParams: _ignored, databaseName: _db, ...withoutOptional } = wire;
  const decoded = decodeDataSource(withoutOptional);
  assert.deepEqual(decoded.connectionParams, {});
  assert.equal(decoded.databaseName, null);
  assert.throws(() => decodeDataSource({ ...wire, port: '3306' }), (error: unknown) => error instanceof ApiError && error.code === 'INVALID_RESPONSE');
  assert.throws(() => decodeDataSource({ ...wire, engineType: 'POSTGRESQL' }), ApiError);
  assert.equal(decodeDataSourcePage({ items: [wire], page: 1, size: 10, total: 1 }).items[0].dataSourceCode, 'PAYMENT_REPLICA');
  assert.throws(() => pageDecoder(decodeDataSource)({ items: {}, page: 1, size: 10, total: 0 }), ApiError);
});

test('连接测试与写回执解码：grants / violations 缺省为空，回执必须是本次对象', () => {
  const test = decodeConnectionTest({ result: 'FAILED', connected: true, privilegesPassed: false, checkedAt: '2026-10-22T01:14:00Z' });
  assert.deepEqual([test.grants, test.violations], [[], []]);
  const mutation = { objectType: 'DATA_SOURCE', objectId: '1', objectCode: 'A', status: 'INACTIVE', version: 2 };
  assert.equal(decodeMutation(mutation, 'DATA_SOURCE', '1').version, 2);
  assert.throws(() => decodeMutation(mutation, 'DATA_SOURCE', '2'), ApiError);
  assert.throws(() => decodeMutation(mutation, 'QUERY_TEMPLATE'), ApiError);
});

test('地址与最后检查展示：没有默认库显示整个实例', () => {
  assert.equal(addressLabel(source()), '10.0.12.21:3306 / mor_payment');
  assert.equal(addressLabel(source({ databaseName: null })), '10.0.12.21:3306 · 整个实例');
  const format = (value: string | null) => value ? 'T' : '—';
  assert.equal(lastCheckLabel(source({ lastCheckResult: null }), format), '未检查');
  assert.equal(lastCheckLabel(source(), format), '通过 · T');
  assert.equal(lastCheckLabel(source({ lastCheckResult: 'FAILED', lastCheckMessage: '账号含 INSERT' }), format), '失败 · 账号含 INSERT');
});

test('SHOW GRANTS 违规权限标红：只允许 USAGE、SELECT、SHOW VIEW，角色授权整行违规', () => {
  const violations = (line: string) => grantSegments(line).filter(segment => segment.violation).map(segment => segment.text.trim());
  assert.deepEqual(violations('GRANT USAGE ON *.* TO `dq`@`%`'), []);
  assert.deepEqual(violations('GRANT SELECT, SHOW VIEW ON `mor_payment`.* TO `dq`@`%`'), []);
  assert.deepEqual(violations('GRANT SELECT, INSERT, UPDATE ON `mor_commercial`.* TO `dq`@`%`'), ['INSERT', 'UPDATE']);
  assert.deepEqual(violations('GRANT SELECT (id, name), UPDATE (name) ON t TO u'), ['UPDATE (name)']);
  assert.deepEqual(violations('GRANT `app_writer`@`%` TO `dq`@`%`'), ['GRANT `app_writer`@`%` TO `dq`@`%`']);
  const line = 'GRANT SELECT, INSERT ON db.* TO u';
  assert.equal(grantSegments(line).map(segment => segment.text).join(''), line);
});

test('请求体：新建带编码与密码；修改不带编码、密码留空为 null；默认库留空表示整个实例；空连接参数行忽略', () => {
  const values = { ...dataSourceFormValues(source()), databaseName: ' ', password: '',
    connectionParams: [{ key: 'sslMode' as const, value: ' REQUIRED ' }, { key: 'socketTimeout' as const, value: '' }, {}] };
  const update = updateDataSourceBody(values, source());
  assert.equal('dataSourceCode' in update, false);
  assert.deepEqual([update.password, update.databaseName, update.expectedVersion], [null, null, 4]);
  assert.deepEqual(update.connectionParams, { sslMode: 'REQUIRED' });
  const create = createDataSourceBody({ ...dataSourceFormValues(null), dataSourceCode: ' NEW_DS ', dataSourceName: 'x', host: 'h', username: 'u', password: 'p' });
  assert.deepEqual([create.dataSourceCode, create.password, create.engineType, create.port, create.maxPoolSize], ['NEW_DS', 'p', 'MYSQL', 3306, 5]);
  assert.equal(connectionTestBody(values, source()).dataSourceId, '900000000000131001');
  assert.equal(connectionTestBody({ ...values, password: 'x' }, null).password, 'x');
});

test('保存门槛：启用中的数据源必须用当前表单配置测试通过；停用与新建不要求', () => {
  const values = dataSourceFormValues(source());
  const fingerprint = connectionFingerprint(values);
  assert.equal(saveGate(null, null, fingerprint).allowed, true);
  assert.equal(saveGate(source({ status: 'INACTIVE' }), null, fingerprint).allowed, true);
  assert.equal(saveGate(source(), null, fingerprint).allowed, false);
  assert.equal(saveGate(source(), { fingerprint, result: passed }, fingerprint).allowed, true);
  assert.equal(saveGate(source(), { fingerprint, result: { ...passed, result: 'FAILED' } }, fingerprint).allowed, false);
  const changed = connectionFingerprint({ ...values, username: 'other' });
  assert.equal(saveGate(source(), { fingerprint, result: passed }, changed).allowed, false);
  assert.equal(connectionFingerprint({ ...values, dataSourceName: '改名不影响' }), fingerprint);
});

test('状态动作与删除前置条件', () => {
  assert.equal(statusAction(source())?.target, 'INACTIVE');
  assert.equal(statusAction(source({ status: 'INACTIVE' }))?.target, 'ACTIVE');
  assert.equal(statusAction(source({ status: 'DELETED' })), null);
  assert.match(deletionBlock(source()) ?? '', /先停用/);
  assert.match(deletionBlock(source({ status: 'INACTIVE' })) ?? '', /6 个启用中的模板/);
  assert.equal(deletionBlock(source({ status: 'INACTIVE', activeTemplateCount: 0 })), null);
});

test('模板编辑器数据源选项：只列启用中的；正在使用的停用数据源保留并标注', () => {
  const active = source(); const inactive = source({ dataSourceId: '2', dataSourceCode: 'RISK_REPLICA', status: 'INACTIVE' });
  const other = source({ dataSourceId: '3', dataSourceCode: 'OLD_REPLICA', status: 'INACTIVE' });
  assert.deepEqual(templateSourceOptions([active, inactive, other], [null]).map(option => option.value), ['900000000000131001']);
  assert.deepEqual(templateSourceOptions([active, inactive, other], ['2', undefined]).map(option => option.label),
    ['PAYMENT_REPLICA · MySQL', 'RISK_REPLICA · MySQL（停用）']);
});
