import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { pagingQuery } from '../api/paging.ts';
import {
  capabilityOptions, changeSummary, consolePaths, consoleRoutes, decodeAgreementPage, decodeBusinessChangePage, decodeCapabilities, decodeFeeEvents,
  decodeProducts, decodeTermPlanPage, decodeTermVersionPage, decodeVersionDiff, diffCounts, diffRows, exactAgreement, feeEventOptions, listFilters,
  recentClients, rememberClient, subscribableProductOptions,
} from './commercialConsole.ts';
import type { KeyValueStorage } from './commercialConsole.ts';

const agreementId = '7319000000000000101';
const clientId = '7300000000000000011';
const agreement = { agreementId, agreementNo: 'AGR-AE001-0001', agreementName: '示例合同', agreementType: 'CLIENT', status: 'ACTIVE', businessMode: 'MOR',
  fundMode: 'SETTLE_THROUGH_PLATFORM', clientId, localPartnerId: null, entityNo: 'AE001', effectiveFrom: '2026-10-01T00:00:00Z', effectiveTo: null,
  signedTime: null, activatedTime: null, changeVersion: 7, updateTime: '2026-10-01T00:00:00Z' };
const plan = { planId: '7319000000000000501', planCode: 'CP-1', termType: 'CLIENT_PRICING', agreementId, entityNo: null, clientId, localPartnerId: null,
  status: 'ACTIVE', changeVersion: '5', activeVersionId: '7319000000000000601', activeVersionNo: '1', activeEffectiveFrom: '2026-10-01T00:00:00Z',
  scheduledVersionId: null, scheduledVersionNo: null, scheduledEffectiveFrom: null, createTime: '2026-09-20T00:00:00Z', updateTime: '2026-10-15T00:00:00Z' };
const versionItem = { versionId: '7319000000000000603', planId: plan.planId, planCode: 'CP-1', termType: 'CLIENT_PRICING', agreementId, entityNo: null,
  clientId, localPartnerId: null, versionNo: '3', status: 'PENDING_APPROVAL', effectiveFrom: '2026-10-18T00:00:00Z', effectiveTo: null,
  roundingMode: 'HALF_UP', pricingModel: 'BLENDED', feeTaxTreatment: 'EXCLUSIVE', makerAccountId: '10001', checkerAccountId: null,
  submittedTime: '2026-10-19T09:00:00Z', priceCount: 8, changeVersion: '6', updateTime: '2026-10-19T09:00:00Z' };
const page = <T,>(items: T[], total = items.length) => ({ items, page: 1, size: 10, total });

test('路径与页面路由：对比与带筛选的变更历史', () => {
  assert.equal(consolePaths.versionDiff('1', '2'), '/commercial-admin/v1/commercial-terms/versions/1/diff/2');
  assert.equal(consoleRoutes.compare('1', '2'), '/commercial/terms/compare/1/2');
  assert.equal(consoleRoutes.history('AGREEMENT', agreementId), `/commercial/history?aggregateRootType=AGREEMENT&aggregateRootId=${agreementId}`);
});

test('列表解码：合同、方案、版本分页；可空字段统一为 null，非法行视为无效响应', () => {
  const agreements = decodeAgreementPage(page([{ ...agreement, effectiveTo: undefined }]));
  assert.equal(agreements.items[0].effectiveTo, null);
  assert.equal(agreements.total, 1);
  assert.equal(decodeTermPlanPage(page([plan])).items[0].scheduledVersionId, null);
  assert.equal(decodeTermVersionPage(page([versionItem])).items[0].makerAccountId, '10001');
  assert.throws(() => decodeAgreementPage(page([{ ...agreement, status: 'UNKNOWN' }])), ApiError);
  assert.throws(() => decodeTermVersionPage(page([{ ...versionItem, priceCount: -1 }])), ApiError);
  assert.throws(() => decodeAgreementPage({ items: [], page: 1, size: 10, total: -1 }), ApiError);
});

test('目录解码与下拉：订阅只列启用的非基础产品；可开通项按产品过滤；计费事件代码去重', () => {
  const products = decodeProducts({ products: [
    { productId: '1', productCode: 'ACQUIRING', productName: 'Acquiring', businessModeScope: 'BOTH', baseProduct: false, status: 'ACTIVE' },
    { productId: '2', productCode: 'PLATFORM', productName: 'Platform', businessModeScope: 'BOTH', baseProduct: true, status: 'ACTIVE' },
    { productId: '3', productCode: 'FX', productName: 'FX', businessModeScope: 'BOTH', baseProduct: false, status: 'RETIRED' }] });
  assert.deepEqual(subscribableProductOptions(products).map(o => o.value), ['ACQUIRING']);
  const capabilities = decodeCapabilities({ capabilities: [
    { productCapabilityId: '1', productCode: 'ACQUIRING', capabilityCode: 'CARD_VISA', capabilityName: 'Visa', capabilityKind: 'PAYMENT_METHOD',
      paymentMethodCode: 'CARD', dimensionCode: 'CARD_BRAND', dimensionValue: 'VISA', status: 'ACTIVE' },
    { productCapabilityId: '2', productCode: 'PAYOUT', capabilityCode: 'PAYOUT_LOCAL', capabilityName: 'Local', capabilityKind: 'PAYOUT_RAIL',
      status: 'ACTIVE' }] });
  assert.equal(capabilities[1].paymentMethodCode, null);
  assert.deepEqual(capabilityOptions(capabilities, ['ACQUIRING']).map(o => o.value), ['CARD_VISA']);
  assert.equal(capabilityOptions(capabilities).length, 2);
  const events = decodeFeeEvents({ feeEvents: [
    { productFeeEventId: '1', productCode: 'ACQUIRING', feeEventCode: 'CHARGEBACK', termType: 'CLIENT_PRICING', billingFactType: 'DISPUTE' },
    { productFeeEventId: '2', productCode: 'DISPUTE', feeEventCode: 'CHARGEBACK', termType: 'DISPUTE_FEE', billingFactType: 'DISPUTE' },
    { productFeeEventId: '3', productCode: 'ACQUIRING', feeEventCode: 'CAPTURE', termType: 'CLIENT_PRICING', billingFactType: 'PAYMENT' }] });
  assert.deepEqual(feeEventOptions(events), [{ value: 'CHARGEBACK', label: 'CHARGEBACK · ACQUIRING / DISPUTE' }, { value: 'CAPTURE', label: 'CAPTURE · ACQUIRING' }]);
  assert.throws(() => decodeProducts({}), ApiError);
});

test('筛选：去空白与空值，多选去重；分页 Query 把数组展开为重复参数', () => {
  const filters = listFilters({ clientId: ' 7300000000000000011 ', agreementNo: '  ', status: ['ACTIVE', 'DRAFT', 'ACTIVE', ' '], entityNo: undefined });
  assert.deepEqual(filters, { clientId: '7300000000000000011', status: ['ACTIVE', 'DRAFT'] });
  assert.equal(pagingQuery(filters, { page: 2, size: 20 }).toString(), 'clientId=7300000000000000011&status=ACTIVE&status=DRAFT&page=2&size=20');
  assert.equal(pagingQuery({ keyword: '' }, { page: 1, size: 10 }).toString(), 'keyword=&page=1&size=10');
  assert.equal(pagingQuery({ status: [] }, { page: 1, size: 10 }).toString(), 'page=1&size=10');
});

test('合同号换 ID：只取编号完全一致的一条', () => {
  const items = decodeAgreementPage(page([{ ...agreement, agreementNo: 'AGR-AE001-00011', agreementId: '7319000000000000102' }, agreement])).items;
  assert.equal(exactAgreement(items, ' AGR-AE001-0001 ')?.agreementId, agreementId);
  assert.equal(exactAgreement(items, 'AGR-AE001'), null);
});

const component = (code: string, type: string, rate: string | null, fixed: string | null = null) => ({ componentId: '1', componentNo: 1, componentCode: code,
  componentType: type, triggerCode: 'CAPTURE', calculationBase: 'GROSS_AMOUNT', refundBehavior: 'RETURN_PRORATA', rate, fixedAmount: fixed, currency: 'USD',
  minAmount: null, maxAmount: null, bandBasis: null, bandMode: null, bands: [] });
const price = (profile: string, currency: string, components: unknown[]) => ({ priceId: '1', entityNo: 'AE001', currency, pricingProfileCode: profile,
  paymentMethodCode: 'CARD', priorityNo: 10, conditions: [], components, settlement: null });
const version = (versionId: string, versionNo: string) => ({ versionId, planId: plan.planId, termType: 'CLIENT_PRICING', agreementId, versionNo,
  status: 'ACTIVE', effectiveFrom: '2026-10-01T00:00:00Z', effectiveTo: null, roundingMode: 'HALF_UP', pricingModel: 'BLENDED', feeTaxTreatment: 'EXCLUSIVE',
  changeVersion: '5' });
const diff = {
  base: version('7319000000000000601', '1'), target: version('7319000000000000602', '2'), versionDifferences: [],
  added: [price('APPLE_PAY_STD', 'USD', [component('MDR', 'PERCENTAGE', '0.025')])],
  removed: [price('CARD_VISA', 'AED', [component('MDR', 'PERCENTAGE', '0.025')])],
  changed: [{ entityNo: 'AE001', currency: 'USD', pricingProfileCode: 'CARD_VISA',
    base: price('CARD_VISA', 'USD', [component('MDR', 'PERCENTAGE', '0.029'), component('PER_TXN', 'FIXED', null, '0.30')]),
    target: price('CARD_VISA', 'USD', [component('MDR', 'PERCENTAGE', '0.022')]),
    differences: [{ field: 'priorityNo', baseValue: '10', targetValue: '20' }, { field: 'components[MDR].rate', baseValue: '0.029', targetValue: '0.022' },
      { field: 'components[PER_TXN]', baseValue: 'FIXED · CAPTURE', targetValue: null }] }],
  unchanged: [price('CARD_MC', 'USD', [component('MDR', 'PERCENTAGE', '0.03')])],
};

test('版本对比：回执版本必须一致且同一方案；按组件展开为修改 / 删除 / 新增 / 未变行', () => {
  const decoded = decodeVersionDiff('7319000000000000601', '7319000000000000602')(diff);
  assert.deepEqual(diffCounts(decoded), { CHANGED: 1, ADDED: 1, REMOVED: 1, UNCHANGED: 1 });
  const rows = diffRows(decoded);
  const visaUsd = rows.filter(row => row.pricingProfileCode === 'CARD_VISA' && row.currency === 'USD');
  assert.deepEqual(visaUsd.map(row => [row.componentCode, row.change]), [['MDR', 'CHANGED'], ['PER_TXN', 'REMOVED']]);
  assert.deepEqual(visaUsd[0].fields.map(f => f.field), ['priorityNo', 'components[MDR].rate']);
  assert.equal(visaUsd[0].baseSummary, '2.9%');
  assert.equal(visaUsd[0].targetSummary, '2.2%');
  assert.equal(rows.find(row => row.pricingProfileCode === 'APPLE_PAY_STD')?.change, 'ADDED');
  assert.equal(rows.find(row => row.pricingProfileCode === 'CARD_VISA' && row.currency === 'AED')?.targetSummary, null);
  const unchanged = rows.find(row => row.pricingProfileCode === 'CARD_MC');
  assert.equal(unchanged?.baseSummary, unchanged?.targetSummary);
  assert.throws(() => decodeVersionDiff('7319000000000000601', '7319000000000000699')(diff), ApiError);
  assert.throws(() => decodeVersionDiff('7319000000000000601', '7319000000000000602')({ ...diff, target: { ...diff.target, planId: '7319000000000000509' } }), ApiError);
});

test('变更历史：比较结果与摘要；NOT_COMPARED 必须没有变化字段', () => {
  const entry = { historyId: '1004', recordType: 'AGREEMENT', recordId: agreementId, versionNo: 3, operationType: 'UPDATE', createBy: '10001',
    createTime: '2026-10-20T06:00:00Z', meta: { triggerType: 'COMMAND', triggerId: '881004', reasonCode: 'AGREEMENT_ACTIVATED' },
    data: { status: 'ACTIVE', products: ['ACQUIRING'] }, comparison: 'COMPARED', previousVersionNo: 2, previousData: { status: 'SIGNED', products: [] },
    changedFields: ['products', 'status'] };
  const [decoded] = decodeBusinessChangePage(page([entry])).items;
  assert.equal(changeSummary(decoded), 'products [] → ["ACQUIRING"]；status SIGNED → ACTIVE');
  const first = decodeBusinessChangePage(page([{ ...entry, comparison: 'FIRST_VERSION', previousVersionNo: null, previousData: null }])).items[0];
  assert.equal(changeSummary(first), '首个快照：products、status');
  const skipped = decodeBusinessChangePage(page([{ ...entry, comparison: 'NOT_COMPARED', previousVersionNo: null, previousData: null, changedFields: null }])).items[0];
  assert.equal(changeSummary(skipped), '上一快照超出回溯范围，未比较');
  assert.throws(() => decodeBusinessChangePage(page([{ ...entry, comparison: 'NOT_COMPARED' }])), ApiError);
  assert.throws(() => decodeBusinessChangePage(page([{ ...entry, comparison: 'GUESSED' }])), ApiError);
});

test('最近使用的 Client：新的在前、去重、最多 10 个；非法 ID 与损坏存储被忽略', () => {
  const values = new Map<string, string>();
  const storage: KeyValueStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => { values.set(key, value); } };
  for (let index = 1; index <= 12; index++) rememberClient(storage, String(7300000000000000000n + BigInt(index)));
  rememberClient(storage, '7300000000000000005');
  const recent = recentClients(storage);
  assert.equal(recent.length, 10);
  assert.equal(recent[0], '7300000000000000005');
  assert.equal(new Set(recent).size, 10);
  assert.deepEqual(rememberClient(storage, 'abc'), recent);
  values.set('operations.commercial.recentClients', '{broken');
  assert.deepEqual(recentClients(storage), []);
  assert.deepEqual(recentClients(undefined), []);
  const failing: KeyValueStorage = { getItem: () => null, setItem: () => { throw new Error('quota'); } };
  assert.deepEqual(rememberClient(failing, clientId), [clientId]);
});
