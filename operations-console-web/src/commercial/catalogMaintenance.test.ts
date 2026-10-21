import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import {
  acceptanceDimensionOptions, attachChoice, attachLabel, attachOptions, capabilityCodePattern, capabilityStatusAction, catalogCapabilityForm, catalogPaths,
  createCatalogCapabilityBody, decodeCatalogCapabilityWrite, decodeFeeEventWrite, dimensionBadges, feeEventAttachBody, newCatalogCapabilityForm,
  rowAttachLabels, updateCatalogCapabilityBody,
} from './catalogMaintenance.ts';
import { capabilityKindLabels, mergeFeeEvents } from './catalogTree.ts';
import { decodeCapabilities, decodeFeeEvents } from './commercialConsole.ts';
import type { CatalogCapability, CatalogFeeEvent } from './commercialConsole.ts';

const capability = (capabilityCode: string, capabilityKind: string, extra: Partial<CatalogCapability> = {}): CatalogCapability => ({
  productCapabilityId: '9100000000000002001', productCode: 'ACQUIRING', capabilityCode, capabilityName: capabilityCode === 'TOKEN_STORAGE' ? '存卡' : capabilityCode,
  capabilityKind, paymentMethodCode: capabilityKind === 'PAYMENT_METHOD' ? capabilityCode : null, dimensionCode: null, dimensionValue: null, status: 'ACTIVE',
  dimensions: [{ dimensionCode: 'CURRENCY', required: true }, { dimensionCode: 'CARD_BRAND', required: false }], changeVersion: 3, ...extra });
const feeEvent = (productFeeEventId: string, termType: string, capabilityKind: string | null, capabilityCode: string | null = null): CatalogFeeEvent => ({
  productFeeEventId, productCode: 'ACQUIRING', feeEventCode: 'TOKEN_STORED', termType, billingFactType: 'TOKEN', capabilityKind, capabilityCode, changeVersion: 2 });

test('AC-01 新增开通项请求：功能种类无支付方式，支付方式种类带支付方式代码；代码转大写；维度与必填成对', () => {
  assert.equal(catalogPaths.capabilityAction('5', 'retire'), '/commercial-admin/v1/products/capabilities/5/retire');
  assert.equal(catalogPaths.feeEventCapability('7'), '/commercial-admin/v1/products/fee-events/7/capability');
  assert.deepEqual(createCatalogCapabilityBody({ productCode: 'ACQUIRING', capabilityCode: ' recurring ', capabilityName: ' 周期扣款 ', capabilityKind: 'FEATURE',
    paymentMethodCode: 'CARD', dimensionCodes: [], requiredDimensions: ['CURRENCY'] }), { productCode: 'ACQUIRING', capabilityCode: 'RECURRING',
    capabilityName: '周期扣款', capabilityKind: 'FEATURE', paymentMethodCode: null, dimensions: [] });
  assert.deepEqual(createCatalogCapabilityBody({ ...newCatalogCapabilityForm('ACQUIRING'), capabilityCode: 'STC_PAY', capabilityName: 'STC Pay',
    paymentMethodCode: 'stc_pay', dimensionCodes: ['CURRENCY', 'CONSUMER_COUNTRY', 'CURRENCY'] }), { productCode: 'ACQUIRING', capabilityCode: 'STC_PAY',
    capabilityName: 'STC Pay', capabilityKind: 'PAYMENT_METHOD', paymentMethodCode: 'STC_PAY',
    dimensions: [{ dimensionCode: 'CURRENCY', required: true }, { dimensionCode: 'CONSUMER_COUNTRY', required: false }] });
  assert.equal(capabilityCodePattern.test('STC_PAY'), true);
  assert.equal(capabilityCodePattern.test('S'), false, '至少两位');
  assert.equal(capabilityCodePattern.test('1PAY'), false);
});

test('编辑开通项：读回表单往返，只提交名称 / 产品 / 维度与版本', () => {
  const card = capability('CARD', 'PAYMENT_METHOD');
  const form = catalogCapabilityForm(card);
  assert.deepEqual([form.dimensionCodes, form.requiredDimensions], [['CURRENCY', 'CARD_BRAND'], ['CURRENCY']]);
  assert.deepEqual(updateCatalogCapabilityBody(card.changeVersion ?? 1, { ...form, capabilityName: ' 银行卡 ' }), { expectedChangeVersion: 3,
    capabilityName: '银行卡', productCode: 'ACQUIRING', dimensions: card.dimensions });
  assert.deepEqual(dimensionBadges(card.dimensions), ['CURRENCY *', 'CARD_BRAND']);
  assert.equal(capabilityStatusAction('ACTIVE'), 'retire');
  assert.equal(capabilityStatusAction('RETIRED'), 'activate');
  assert.equal(capabilityStatusAction('UNKNOWN'), null);
  assert.equal(capabilityKindLabels.FEATURE, '功能');
  assert.deepEqual(acceptanceDimensionOptions([{ dimensionCode: 'CURRENCY', displayName: '币种', factSource: 'ORDER', evaluationPoint: 'CHECKOUT',
    acceptanceAllowed: true, pricingAllowed: true }, { dimensionCode: 'THREE_DS_RESULT', displayName: '3DS 结果', factSource: 'THREE_DS',
    evaluationPoint: 'AUTHENTICATION', acceptanceAllowed: false, pricingAllowed: true }]), [{ value: 'CURRENCY', label: '币种 CURRENCY' }], '只用于定价的维度不可开通');
});

test('计费事件挂接：无需开通 / 任一种类 / 功能开通项三类选择 ↔ 请求体；合并行列出各登记挂接', () => {
  const catalog = [capability('CARD', 'PAYMENT_METHOD'), capability('TOKEN_STORAGE', 'FEATURE'), capability('PAYOUT_LOCAL', 'PAYOUT_RAIL', { productCode: 'PAYOUT' })];
  const options = attachOptions('ACQUIRING', catalog).map(option => option.value);
  assert.deepEqual(options, ['NONE', 'KIND:PAYMENT_METHOD', 'KIND:PAYOUT_RAIL', 'KIND:FX', 'KIND:DISPUTE', 'KIND:PERIODIC_SERVICE', 'FEATURE:TOKEN_STORAGE']);
  assert.deepEqual(feeEventAttachBody(2, 'FEATURE:TOKEN_STORAGE'), { expectedChangeVersion: 2, capabilityKind: 'FEATURE', capabilityCode: 'TOKEN_STORAGE' });
  assert.deepEqual(feeEventAttachBody(2, 'KIND:PAYMENT_METHOD'), { expectedChangeVersion: 2, capabilityKind: 'PAYMENT_METHOD', capabilityCode: null });
  assert.deepEqual(feeEventAttachBody(2, 'NONE'), { expectedChangeVersion: 2, capabilityKind: null, capabilityCode: null });
  assert.equal(attachChoice(feeEvent('1', 'CLIENT_PRICING', 'FEATURE', 'TOKEN_STORAGE')), 'FEATURE:TOKEN_STORAGE');
  assert.equal(attachChoice(feeEvent('1', 'CLIENT_PRICING', 'PAYMENT_METHOD')), 'KIND:PAYMENT_METHOD');
  assert.equal(attachChoice(feeEvent('1', 'CLIENT_PRICING', null)), 'NONE');
  assert.equal(attachLabel(feeEvent('1', 'CLIENT_PRICING', 'FEATURE', 'TOKEN_STORAGE'), catalog), '功能 · 存卡 TOKEN_STORAGE');
  const [row] = mergeFeeEvents([feeEvent('1', 'CLIENT_PRICING', 'FEATURE', 'TOKEN_STORAGE'), feeEvent('2', 'PARTNER_SHARE', null)]);
  assert.deepEqual(row.feeEvents.map(event => event.productFeeEventId), ['1', '2']);
  assert.deepEqual(rowAttachLabels(row, catalog), ['功能 · 存卡 TOKEN_STORAGE', '无需开通']);
});

test('目录解码：新字段缺省兼容旧 BFF，存在时严格校验；写回执 ID 一致', () => {
  const legacy = decodeCapabilities({ capabilities: [{ productCapabilityId: '1', productCode: 'ACQUIRING', capabilityCode: 'CARD', capabilityName: 'Card',
    capabilityKind: 'PAYMENT_METHOD', paymentMethodCode: 'CARD', status: 'ACTIVE' }] })[0];
  assert.deepEqual([legacy.dimensions, legacy.changeVersion], [[], null]);
  assert.throws(() => decodeCapabilities({ capabilities: [{ ...legacy, dimensions: [{ required: true }] }] }), ApiError);
  const events = decodeFeeEvents({ feeEvents: [{ productFeeEventId: '7', productCode: 'ACQUIRING', feeEventCode: 'CAPTURE', termType: 'CLIENT_PRICING',
    billingFactType: 'PAYMENT' }] });
  assert.deepEqual([events[0].capabilityKind, events[0].capabilityCode, events[0].changeVersion], [null, null, null]);
  assert.equal(decodeCatalogCapabilityWrite('1')({ ...legacy, changeVersion: 2 }).changeVersion, 2);
  assert.throws(() => decodeCatalogCapabilityWrite('9')(legacy), ApiError);
  assert.equal(decodeFeeEventWrite('7')({ ...events[0], capabilityKind: 'PAYMENT_METHOD', changeVersion: '3' }).capabilityKind, 'PAYMENT_METHOD');
  assert.throws(() => decodeFeeEventWrite('8')(events[0]), ApiError);
});
