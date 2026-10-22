import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import {
  allowedCredentialModes, bucketObjectCode, bucketPath, bucketRows, bucketStatusActions, bucketStatusBlocker, canManageFileStorage,
  createInstanceBody, createRouteBody, credentialBody, decodeBucketCheck, decodeInstanceList, decodeMutation, decodeRouteList, deletableRoute,
  instancePath, instanceStatusAction, instanceStatusNotice, manageFileStorage, routePath, routeTargets, sourceSystemLabel, storageAdminPath,
  storageCommand, targetValue, updateInstanceBody, updateRouteBody,
} from './fileStorage.ts';
import type { StorageBucket, StorageInstance, StorageRoute } from './fileStorage.ts';

const time = '2026-10-22T01:02:03.123456Z';
const now = Date.parse('2026-10-22T01:05:00Z');
const activeBucket: StorageBucket = { bucketName: 'mor-platform-files', status: 'ACTIVE', lastCheckTime: time, lastCheckResult: 'OK',
  routeCount: 1, version: 2, updateTime: time };
const draftBucket: StorageBucket = { ...activeBucket, bucketName: 'mor-workflow-files', status: 'DRAFT', routeCount: 0, version: 1 };
const minio: StorageInstance = { instanceCode: 'MINIO', storageType: 'MINIO', endpoint: 'https://minio.local.test:19400', region: 'us-east-1',
  pathStyleAccess: true, trustedCertificatePem: null, credentialMode: 'STATIC', credentialConfigured: true, credentialUpdateTime: time,
  connectTimeoutMs: 2000, readTimeoutMs: 10000, status: 'ACTIVE', version: 3, updateTime: time, routeCount: 1, buckets: [activeBucket, draftBucket] };
const aws: StorageInstance = { ...minio, instanceCode: 'AWS_ME', storageType: 'S3', endpoint: null, region: 'me-central-1', pathStyleAccess: false,
  credentialMode: 'IAM_ROLE', credentialConfigured: false, credentialUpdateTime: null, status: 'DRAFT', version: 1, routeCount: 0,
  buckets: [{ ...activeBucket, bucketName: 'aws-files', routeCount: 0 }] };
const defaultRoute: StorageRoute = { sourceSystem: '*', instanceCode: 'MINIO', bucketName: 'mor-platform-files',
  scanPolicyCode: 'CLIENT_ONBOARDING_FORMAT', maxUploadSeconds: 300, version: 1, updateTime: time };
const clientRoute: StorageRoute = { ...defaultRoute, sourceSystem: 'client-master-service', maxUploadSeconds: 600, version: 2 };
const mutation = { objectType: 'FILE_STORAGE_INSTANCE', objectId: '7312345678901234567', objectCode: 'MINIO', status: 'ACTIVE', version: 4 };

test('页面注册：存储管理挂在 /file-storage，维护按钮与 Platform 能力清单一致；接口路径对应 BFF 合同', () => {
  const page = pageCapabilities.find(p => p.componentKey === 'OPERATIONS_FILE_STORAGE');
  assert.equal(page?.frontendRoute, '/file-storage');
  assert.deepEqual(page?.actions.map(action => action.resourceCode), [manageFileStorage]);
  assert.equal(storageAdminPath, '/storage-admin/v1');
  assert.equal(instancePath('MINIO'), '/storage-admin/v1/instances/MINIO');
  assert.equal(bucketPath('MINIO', 'mor-platform-files'), '/storage-admin/v1/instances/MINIO/buckets/mor-platform-files');
  assert.equal(routePath('*'), '/storage-admin/v1/routes/*', '默认规则按 * 原样作为路径段');
});

test('只读模式：只有维护按钮资源才显示写入口，菜单资源或其他按钮不算', () => {
  assert.equal(canManageFileStorage([{ resourceType: 'BUTTON', resourceCode: manageFileStorage }]), true);
  assert.equal(canManageFileStorage([{ resourceType: 'MENU', resourceCode: 'OPERATIONS_FILE_STORAGE' }]), false);
  assert.equal(canManageFileStorage([{ resourceType: 'MENU', resourceCode: manageFileStorage }]), false);
  assert.equal(canManageFileStorage([{ resourceType: 'BUTTON', resourceCode: 'OPERATIONS_CURRENCY_SPEC_MANAGE' }]), false);
});

test('实例列表解码：字段集合、枚举、计数与时间严格校验；多出凭据 Token 或重复代码失败关闭', () => {
  assert.deepEqual(decodeInstanceList({ items: [minio, aws] }).map(item => item.instanceCode), ['MINIO', 'AWS_ME']);
  for (const invalid of [
    { ...minio, storageType: 'GCS' }, { ...minio, status: 'ENABLED' }, { ...minio, credentialMode: 'ROOT' }, { ...minio, routeCount: -1 },
    { ...minio, version: 0 }, { ...minio, updateTime: 'yesterday' }, { ...minio, credentialVaultToken: 'vault-token' },
    { ...minio, instanceCode: 'minio' }, { ...minio, buckets: [{ ...activeBucket, lastCheckResult: 'BROKEN' }] },
    { ...minio, buckets: [activeBucket, activeBucket] }, { ...minio, buckets: [{ ...activeBucket, extra: 1 }] },
  ]) {
    assert.throws(() => decodeInstanceList({ items: [invalid] }), ApiError, JSON.stringify(invalid));
  }
  assert.throws(() => decodeInstanceList({ items: [minio, minio] }), ApiError);
  assert.throws(() => decodeInstanceList({ items: [minio], total: 1 }), ApiError);
});

test('写入规则解码：默认规则排在最前，来源系统、策略代码与上传期限校验', () => {
  assert.deepEqual(decodeRouteList({ items: [clientRoute, defaultRoute] }).map(route => route.sourceSystem), ['*', 'client-master-service']);
  for (const invalid of [{ ...clientRoute, sourceSystem: 'Payment' }, { ...clientRoute, maxUploadSeconds: 30 },
    { ...clientRoute, scanPolicyCode: 'lower' }, { ...clientRoute, extra: true }]) {
    assert.throws(() => decodeRouteList({ items: [invalid] }), ApiError, JSON.stringify(invalid));
  }
  assert.throws(() => decodeRouteList({ items: [defaultRoute, defaultRoute] }), ApiError);
});

test('写回执与检查结果：必须对应本次对象；桶以 {实例}/{桶名} 标识，删除回执状态为空', () => {
  assert.equal(decodeMutation(mutation, 'MINIO').version, 4);
  assert.equal(decodeMutation({ ...mutation, objectCode: 'MINIO/mor-workflow-files' }, bucketObjectCode('MINIO', 'mor-workflow-files')).objectCode,
    'MINIO/mor-workflow-files');
  assert.equal(decodeMutation({ ...mutation, objectCode: 'client-master-service', status: null }, 'client-master-service').status, null);
  assert.throws(() => decodeMutation(mutation, 'OTHER'), ApiError);
  assert.throws(() => decodeMutation({ ...mutation, version: 0 }, 'MINIO'), ApiError);
  assert.throws(() => decodeMutation({ ...mutation, extra: 1 }, 'MINIO'), ApiError);
  const check = { instanceCode: 'MINIO', bucketName: 'mor-workflow-files', result: 'VERSIONING_DISABLED', checkedAt: time, version: 2 };
  assert.equal(decodeBucketCheck(check, 'MINIO', 'mor-workflow-files').result, 'VERSIONING_DISABLED');
  assert.throws(() => decodeBucketCheck({ ...check, result: 'FAILED' }, 'MINIO', 'mor-workflow-files'), ApiError);
  assert.throws(() => decodeBucketCheck(check, 'MINIO', 'other-bucket'), ApiError);
});

test('存储实例页签：MinIO 只能固定密钥；启停动作与提示随状态、桶与引用变化', () => {
  assert.deepEqual(allowedCredentialModes('MINIO'), ['STATIC']);
  assert.deepEqual(allowedCredentialModes('S3'), ['STATIC', 'IAM_ROLE']);
  assert.deepEqual(instanceStatusAction(minio), { target: 'DISABLED', verb: '停用', danger: true });
  assert.deepEqual(instanceStatusAction(aws), { target: 'ACTIVE', verb: '启用', danger: false });
  assert.deepEqual(instanceStatusAction({ ...minio, status: 'DISABLED' }).target, 'ACTIVE');
  assert.match(instanceStatusNotice(minio), /1 条写入规则引用/);
  assert.match(instanceStatusNotice({ ...minio, routeCount: 0 }), /读写都返回不可用/);
  assert.match(instanceStatusNotice(aws), /可被写入规则引用/);
  assert.match(instanceStatusNotice({ ...aws, buckets: [draftBucket] }), /启用会被拒绝/);
});

test('实例请求体：密钥原样发送、IAM 角色不发送密钥；编辑不带代码、类型与凭据', () => {
  const created = createInstanceBody({ instanceCode: ' MINIO_2 ', storageType: 'MINIO', endpoint: ' https://m.test ', region: ' us-east-1 ',
    pathStyleAccess: true, trustedCertificatePem: ' ', credentialMode: 'STATIC', accessKey: 'ak', secretKey: ' secret with space ',
    connectTimeoutMs: 2000, readTimeoutMs: 10000, reason: ' 新增 ' });
  assert.deepEqual(created, { instanceCode: 'MINIO_2', storageType: 'MINIO', endpoint: 'https://m.test', region: 'us-east-1', pathStyleAccess: true,
    trustedCertificatePem: null, credentialMode: 'STATIC', accessKey: 'ak', secretKey: ' secret with space ', connectTimeoutMs: 2000,
    readTimeoutMs: 10000, reason: '新增' });
  const iam = createInstanceBody({ instanceCode: 'AWS', storageType: 'S3', region: 'me-central-1', credentialMode: 'IAM_ROLE',
    accessKey: 'left-over', secretKey: 'left-over', reason: 'x' });
  assert.equal(iam.accessKey, null); assert.equal(iam.secretKey, null); assert.equal(iam.endpoint, null); assert.equal(iam.pathStyleAccess, false);
  const updated = updateInstanceBody({ instanceCode: 'X', storageType: 'S3', endpoint: '', region: 'us-east-1', pathStyleAccess: true,
    connectTimeoutMs: 3000, readTimeoutMs: 10000, reason: ' 调整 ' }, minio);
  assert.deepEqual(Object.keys(updated).sort(), ['connectTimeoutMs', 'endpoint', 'expectedVersion', 'pathStyleAccess', 'readTimeoutMs', 'reason',
    'region', 'trustedCertificatePem']);
  assert.equal(updated.expectedVersion, 3); assert.equal(updated.endpoint, null);
  assert.deepEqual(credentialBody({ credentialMode: 'IAM_ROLE', accessKey: 'ak', secretKey: 'sk', reason: '切换' }, aws),
    { credentialMode: 'IAM_ROLE', accessKey: null, secretKey: null, expectedVersion: 1, reason: '切换' });
  assert.equal(credentialBody({ credentialMode: 'STATIC', accessKey: 'ak2', secretKey: 'sk2', reason: '轮换' }, minio).secretKey, 'sk2');
});

test('桶页签：按实例筛选展开；启用需 10 分钟内检查通过，被规则引用不能只读或停用，停用为终态', () => {
  assert.deepEqual(bucketRows([minio, aws]).map(row => `${row.instanceCode}/${row.bucketName}`),
    ['MINIO/mor-platform-files', 'MINIO/mor-workflow-files', 'AWS_ME/aws-files']);
  assert.deepEqual(bucketRows([minio, aws], 'AWS_ME').map(row => row.bucketName), ['aws-files']);
  assert.deepEqual(bucketStatusActions(draftBucket).map(a => a.target), ['ACTIVE']);
  assert.deepEqual(bucketStatusActions(activeBucket).map(a => a.target), ['READ_ONLY', 'DISABLED']);
  assert.deepEqual(bucketStatusActions({ ...activeBucket, status: 'READ_ONLY' }).map(a => a.target), ['ACTIVE', 'DISABLED']);
  assert.deepEqual(bucketStatusActions({ ...activeBucket, status: 'DISABLED' }), []);
  assert.equal(bucketStatusBlocker(draftBucket, 'ACTIVE', now), null);
  assert.match(bucketStatusBlocker(draftBucket, 'ACTIVE', now + 10 * 60_000) ?? '', /超过 10 分钟/);
  assert.match(bucketStatusBlocker({ ...draftBucket, lastCheckResult: 'ENCRYPTION_DISABLED' }, 'ACTIVE', now) ?? '', /先检查通过/);
  assert.match(bucketStatusBlocker({ ...draftBucket, lastCheckResult: null, lastCheckTime: null }, 'ACTIVE', now) ?? '', /先检查通过/);
  assert.match(bucketStatusBlocker(activeBucket, 'READ_ONLY', now) ?? '', /1 条写入规则引用/);
  assert.equal(bucketStatusBlocker({ ...activeBucket, routeCount: 0 }, 'DISABLED', now), null);
  assert.equal(bucketStatusBlocker({ ...activeBucket, status: 'READ_ONLY' }, 'ACTIVE', now), null, '只读恢复启用不要求重新检查');
});

test('写入规则页签：默认规则显示为默认且不可删除；目标只列启用实例下的启用桶；请求体拆分目标', () => {
  assert.equal(sourceSystemLabel('*'), '默认'); assert.equal(sourceSystemLabel('payment'), 'payment');
  assert.equal(deletableRoute(defaultRoute), false); assert.equal(deletableRoute(clientRoute), true);
  assert.deepEqual(routeTargets([minio, aws]), [{ instanceCode: 'MINIO', bucketName: 'mor-platform-files' }]);
  assert.deepEqual(createRouteBody({ sourceSystem: ' platform-support ', target: targetValue('MINIO', 'mor.files'), scanPolicyCode: ' CLIENT_ONBOARDING_FORMAT ',
    maxUploadSeconds: 300, reason: ' 入驻 ' }), { sourceSystem: 'platform-support', instanceCode: 'MINIO', bucketName: 'mor.files',
    scanPolicyCode: 'CLIENT_ONBOARDING_FORMAT', maxUploadSeconds: 300, reason: '入驻' });
  assert.deepEqual(updateRouteBody({ target: targetValue('MINIO', 'mor-workflow-files'), scanPolicyCode: 'CLIENT_ONBOARDING_FORMAT',
    maxUploadSeconds: 900, reason: '改桶' }, defaultRoute), { instanceCode: 'MINIO', bucketName: 'mor-workflow-files',
    scanPolicyCode: 'CLIENT_ONBOARDING_FORMAT', maxUploadSeconds: 900, expectedVersion: 1, reason: '改桶' });
});

test('写命令：同一目标与正文重试复用 Idempotency-Key，改内容换 Key；桶检查无请求体', () => {
  const first = storageCommand('/p', 'PUT', { a: 1 });
  assert.equal(storageCommand('/p', 'PUT', { a: 1 }, first).key, first.key);
  assert.notEqual(storageCommand('/p', 'PUT', { a: 2 }, first).key, first.key);
  assert.notEqual(storageCommand('/p', 'DELETE', { a: 1 }, first).key, first.key);
  assert.equal(storageCommand('/check', 'POST', null).body, null);
});
