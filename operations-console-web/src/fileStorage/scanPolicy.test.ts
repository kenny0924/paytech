import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import {
  activationNotice, byteSizeLabel, bytesToMegabytes, createPolicyBody, decodePolicyMutation, decodeScanPolicyDetail, decodeScanPolicyList,
  megabytesToBytes, mimeTypeLabel, newRevisionBlocker, policyPath, retireBlocker, revisionPath, scanPolicyPath, selectablePolicies,
  unsupportedMimeTypes, updateRevisionBody, versionedReasonBody,
} from './scanPolicy.ts';
import { testT } from '../i18n/testing.ts';

const t = testT('file');
import type { ScanPolicyDetail, ScanPolicyRevision, ScanPolicySummary } from './scanPolicy.ts';

const time = '2026-10-22T07:40:00.123456Z';
const supported = ['application/pdf', 'image/png', 'image/jpeg'];
const onboarding: ScanPolicySummary = { policyCode: 'CLIENT_ONBOARDING_FORMAT', activeRevisionNo: 2, draftRevisionNo: 3,
  allowedMimeTypes: supported, maxSizeBytes: '10485760', routeCount: 2, status: 'ACTIVE', updateTime: time };
const cardBin: ScanPolicySummary = { ...onboarding, policyCode: 'CARD_BIN_CSV', activeRevisionNo: 1, draftRevisionNo: null,
  allowedMimeTypes: ['text/csv'], maxSizeBytes: '52428800', routeCount: 1 };
const draftOnly: ScanPolicySummary = { ...onboarding, policyCode: 'MERCHANT_LOGO_PUBLIC', activeRevisionNo: null, draftRevisionNo: 1,
  allowedMimeTypes: ['image/png', 'image/jpeg'], maxSizeBytes: '2097152', routeCount: 0, status: 'DRAFT' };
const requirement = { scannerCode: 'PLATFORM_FORMAT', scanType: 'FORMAT', minimumScannerVersion: '1', acceptedResultCodes: ['FORMAT_VALID'] };
const revision = (revisionNo: number, status: ScanPolicyRevision['status'], extra: Partial<ScanPolicyRevision> = {}): ScanPolicyRevision => ({
  revisionNo, status, allowedMimeTypes: supported, maxSizeBytes: '10485760', requirements: [requirement], version: 1, createTime: time, updateTime: time, ...extra });
const detail: ScanPolicyDetail = { policyCode: 'CLIENT_ONBOARDING_FORMAT',
  routes: [{ sourceSystem: '*', accessLevel: 'PRIVATE', instanceCode: 'MINIO', bucketName: 'mor-platform-files' }],
  revisions: [revision(3, 'DRAFT', { maxSizeBytes: '20971520', version: 2 }), revision(2, 'ACTIVE'), revision(1, 'RETIRED')] };

test('路径：策略、修订与激活都挂在策略代码下', () => {
  assert.equal(scanPolicyPath, '/storage-admin/v1/scan-policies');
  assert.equal(policyPath('CARD_BIN_CSV'), '/storage-admin/v1/scan-policies/CARD_BIN_CSV');
  assert.equal(revisionPath('CARD_BIN_CSV', 2), '/storage-admin/v1/scan-policies/CARD_BIN_CSV/revisions/2');
});

test('策略列表解码：字段、状态与修订号一致性严格校验；多余字段、重复代码失败关闭', () => {
  const list = decodeScanPolicyList({ items: [onboarding, cardBin, draftOnly], supportedMimeTypes: supported });
  assert.deepEqual(list.items.map(item => item.policyCode), ['CLIENT_ONBOARDING_FORMAT', 'CARD_BIN_CSV', 'MERCHANT_LOGO_PUBLIC']);
  for (const invalid of [{ ...onboarding, status: 'ENABLED' }, { ...onboarding, maxSizeBytes: 10485760 }, { ...onboarding, routeCount: -1 },
    { ...onboarding, activeRevisionNo: null }, { ...draftOnly, activeRevisionNo: 1 }, { ...onboarding, extra: 1 }, { ...onboarding, policyCode: 'lower' },
    { ...onboarding, allowedMimeTypes: ['image/png', 'image/png'] }]) {
    assert.throws(() => decodeScanPolicyList({ items: [invalid], supportedMimeTypes: supported }), ApiError, JSON.stringify(invalid));
  }
  assert.throws(() => decodeScanPolicyList({ items: [onboarding, onboarding], supportedMimeTypes: supported }), ApiError);
  assert.throws(() => decodeScanPolicyList({ items: [], supportedMimeTypes: [] }), ApiError, '可识别类型不能为空');
  assert.throws(() => decodeScanPolicyList({ items: [] }), ApiError);
});

test('策略详情解码：必须是请求的策略；修订倒序，最多一个生效与一个草稿；检查要求版本号可为数字或字符串', () => {
  assert.deepEqual(decodeScanPolicyDetail(detail, 'CLIENT_ONBOARDING_FORMAT').revisions.map(item => item.revisionNo), [3, 2, 1]);
  const numericVersion = { ...detail, revisions: [revision(2, 'ACTIVE', { requirements: [{ ...requirement, minimumScannerVersion: 1 as unknown as string }] })] };
  assert.equal(decodeScanPolicyDetail(numericVersion, 'CLIENT_ONBOARDING_FORMAT').revisions[0].requirements[0].minimumScannerVersion, '1');
  assert.throws(() => decodeScanPolicyDetail(detail, 'OTHER'), ApiError);
  assert.throws(() => decodeScanPolicyDetail({ ...detail, revisions: [revision(1, 'RETIRED'), revision(2, 'ACTIVE')] }, detail.policyCode), ApiError, '须倒序');
  assert.throws(() => decodeScanPolicyDetail({ ...detail, revisions: [revision(2, 'ACTIVE'), revision(1, 'ACTIVE')] }, detail.policyCode), ApiError);
  assert.throws(() => decodeScanPolicyDetail({ ...detail, routes: [{ ...detail.routes[0], accessLevel: 'ANY' }] }, detail.policyCode), ApiError);
  assert.throws(() => decodeScanPolicyDetail({ ...detail, revisions: [revision(2, 'ACTIVE', { requirements: [{ ...requirement, token: 'x' }] as never })] },
    detail.policyCode), ApiError);
});

test('写回执：objectCode 为 {策略代码}/{修订号}；已知修订号须一致', () => {
  const mutation = { objectType: 'FILE_SCAN_POLICY', objectId: '9001', objectCode: 'MERCHANT_LOGO_PUBLIC/1', status: 'DRAFT', version: 1 };
  assert.equal(decodePolicyMutation(mutation, 'MERCHANT_LOGO_PUBLIC', 1).objectCode, 'MERCHANT_LOGO_PUBLIC/1');
  assert.equal(decodePolicyMutation({ ...mutation, objectCode: 'MERCHANT_LOGO_PUBLIC/4' }, 'MERCHANT_LOGO_PUBLIC').status, 'DRAFT');
  assert.throws(() => decodePolicyMutation(mutation, 'MERCHANT_LOGO_PUBLIC', 2), ApiError);
  assert.throws(() => decodePolicyMutation({ ...mutation, objectCode: 'MERCHANT_LOGO_PUBLIC' }, 'MERCHANT_LOGO_PUBLIC'), ApiError);
  assert.throws(() => decodePolicyMutation({ ...mutation, objectCode: 'OTHER/1' }, 'MERCHANT_LOGO_PUBLIC'), ApiError);
});

test('请求体：类型去重并按可识别顺序；大小以 MB 录入换算为字节字符串，越界拒绝；草稿修改带原版本', () => {
  assert.deepEqual(createPolicyBody({ policyCode: ' MERCHANT_LOGO_PUBLIC ', allowedMimeTypes: ['image/jpeg', 'image/png', 'image/jpeg'],
    maxSizeMegabytes: 2, reason: ' 商户 Logo ' }, supported),
    { policyCode: 'MERCHANT_LOGO_PUBLIC', allowedMimeTypes: ['image/png', 'image/jpeg'], maxSizeBytes: '2097152', reason: '商户 Logo' });
  assert.throws(() => createPolicyBody({ policyCode: 'X', allowedMimeTypes: [], maxSizeMegabytes: 1, reason: 'x' }, supported), { message: t('policy.allowedTypesRequired') });
  assert.throws(() => createPolicyBody({ policyCode: 'X', allowedMimeTypes: ['text/csv'], maxSizeMegabytes: 1, reason: 'x' }, supported), { message: t('policy.allowedTypesUnsupported') });
  assert.throws(() => megabytesToBytes(101), /100 MB/); assert.throws(() => megabytesToBytes(0), /100 MB/);
  assert.equal(megabytesToBytes(100), '104857600'); assert.equal(megabytesToBytes(0.5), '524288');
  assert.equal(bytesToMegabytes('20971520'), 20);
  assert.deepEqual(updateRevisionBody({ allowedMimeTypes: ['application/pdf'], maxSizeMegabytes: 20, reason: ' 放宽 ' }, detail.revisions[0], supported),
    { allowedMimeTypes: ['application/pdf'], maxSizeBytes: '20971520', expectedVersion: 2, reason: '放宽' });
  assert.deepEqual(versionedReasonBody(detail.revisions[1], ' 激活 '), { expectedVersion: 1, reason: '激活' });
});

test('页签规则：无法识别的类型提示；已有草稿不能新建修订；被引用不能停用；规则下拉只列有生效修订的策略', () => {
  assert.deepEqual(unsupportedMimeTypes(['text/csv', 'image/png'], supported), ['text/csv']);
  const withCsv = [...supported, 'text/csv'];
  assert.deepEqual(unsupportedMimeTypes(['text/csv', 'image/png'], withCsv), [], '可识别类型来自接口 supportedMimeTypes，不在前端写死');
  assert.deepEqual(createPolicyBody({ policyCode: 'CARD_BIN_CSV', allowedMimeTypes: ['text/csv'], maxSizeMegabytes: 50, reason: 'x' }, withCsv).allowedMimeTypes,
    ['text/csv']);
  assert.equal(mimeTypeLabel('application/pdf'), 'PDF'); assert.equal(mimeTypeLabel('text/csv'), 'CSV');
  assert.equal(byteSizeLabel('10485760'), '10 MB'); assert.equal(byteSizeLabel('86016'), '84 KB'); assert.equal(byteSizeLabel('193'), '193 B');
  assert.equal(newRevisionBlocker(detail), t('policy.newRevisionBlocker.draft', { revisionNo: 3 }));
  assert.match(newRevisionBlocker(detail) ?? '', /Draft r3 already exists/);
  assert.equal(newRevisionBlocker({ ...detail, revisions: detail.revisions.slice(1) }), null);
  assert.equal(newRevisionBlocker({ ...detail, revisions: [revision(1, 'RETIRED')] }), t('policy.newRevisionBlocker.noActive'));
  assert.equal(retireBlocker(detail), t('policy.retireBlocker.referenced', { count: 1 }));
  assert.equal(retireBlocker({ ...detail, routes: [] }), null);
  assert.equal(activationNotice(detail, detail.revisions[0]), t('policy.activationNotice.replace', { draftNo: 3, activeNo: 2 }));
  assert.match(activationNotice(detail, detail.revisions[0]), /checked against r3.*r2 is retired.*still checked against r2/);
  assert.deepEqual(selectablePolicies([onboarding, cardBin, draftOnly]).map(item => item.policyCode), ['CLIENT_ONBOARDING_FORMAT', 'CARD_BIN_CSV']);
});
