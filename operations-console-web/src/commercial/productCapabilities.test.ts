import assert from 'node:assert/strict';
import test from 'node:test';
import type { AgreementCapability } from './commercial.ts';
import { capabilityChoices } from './capabilityConditions.ts';
import type { CatalogCapability, CatalogProduct } from './commercialConsole.ts';
import { capabilityDisplayName, productFootnote, productSections, sectionTitle, showEntityColumn } from './productCapabilities.ts';

const product = (productCode: string, productName: string, baseProduct = false, status = 'ACTIVE'): CatalogProduct => ({
  productId: `91000000000001${productCode.length}`, productCode, productName, businessModeScope: 'BOTH', baseProduct, status });
const products = [product('ACQUIRING', '收单'), product('PAYOUT', '出款'), product('FX', '外汇'), product('TAX_INVOICE', '税票', true),
  product('DATA_ANALYTICS', '数据分析', false, 'RETIRED'), product('PLATFORM', '平台', true)];
const item = (capabilityCode: string, productCode: string, capabilityName: string, status = 'ACTIVE'): CatalogCapability => ({
  productCapabilityId: '9100000000000002001', productCode, capabilityCode, capabilityName, capabilityKind: 'PAYMENT_METHOD', paymentMethodCode: null,
  dimensionCode: null, dimensionValue: null, status, dimensions: [], changeVersion: 1 });
const catalog = [item('CARD', 'ACQUIRING', '银行卡'), item('APPLE_PAY', 'ACQUIRING', 'Apple Pay'), item('PAYOUT_LOCAL', 'PAYOUT', '本地出款'),
  item('PAYOUT_SWIFT', 'PAYOUT', 'SWIFT 出款'), item('PAYOUT_OLD', 'PAYOUT', '旧出款', 'RETIRED'), item('FX_SPOT', 'FX', '即期换汇')];
const capability = (agreementCapabilityId: string, capabilityCode: string, entityNo = 'AE001'): AgreementCapability => ({ agreementCapabilityId, entityNo,
  capabilityCode, status: 'ENABLED', effectiveFrom: '2026-10-01T00:00:00Z', effectiveTo: null, rollingReserveRate: null, rollingReserveDays: null,
  conditions: [], require3ds: false });

test('F6 产品分组：目录产品顺序，开通项落到归属产品；空产品列出可开通项名称；归属不明的进「其他」', () => {
  const capabilities = [capability('1', 'APPLE_PAY'), capability('2', 'CARD'), capability('3', 'LEGACY_X'), capability('4', 'FX_SPOT')];
  const sections = productSections(['PAYOUT', 'ACQUIRING'], capabilities, products, catalog);
  assert.deepEqual(sections.map(section => [section.key, section.capabilities.map(c => c.agreementCapabilityId)]),
    [['ACQUIRING', ['1', '2']], ['PAYOUT', []], ['OTHER', ['3', '4']]], '按目录产品顺序；目录查不到或产品未订阅的开通项进其他');
  assert.deepEqual(sections[1].availableNames, ['本地出款', 'SWIFT 出款'], '只列启用的可开通项');
  assert.deepEqual(sections[0].availableNames, [], '有开通项的区块不列');
  assert.equal(sections[2].other, true);
  assert.deepEqual([sectionTitle(sections[0]), sectionTitle(sections[2])], ['收单 ACQUIRING', "Other (product can't be determined from the catalog)"]);
  assert.deepEqual(productSections(['ACQUIRING'], [capability('2', 'CARD')], products, catalog).map(section => section.key), ['ACQUIRING'], '没有其他时不出现');
});

test('F6 目录读取失败：订阅产品只显示代码，开通项全部进「其他」，不丢', () => {
  const sections = productSections(['ACQUIRING', 'PAYOUT'], [capability('1', 'CARD')], [], []);
  assert.deepEqual(sections.map(section => [section.key, sectionTitle(section), section.capabilities.length]),
    [['ACQUIRING', 'ACQUIRING', 0], ['PAYOUT', 'PAYOUT', 0], ['OTHER', "Other (product can't be determined from the catalog)", 1]]);
  assert.deepEqual(productSections(['NEW_PRODUCT', 'ACQUIRING'], [], products, catalog).map(section => section.key), ['ACQUIRING', 'NEW_PRODUCT'],
    '目录里没有的订阅产品追加在后');
  assert.equal(productFootnote(['ACQUIRING'], []), null);
  assert.deepEqual(productSections([], [], products, catalog), []);
});

test('F6 页底与 OE 列：未订阅（启用的非基础产品）与默认具备（基础产品）；OE 列只在 PARTNER 多 OE 时显示', () => {
  assert.equal(productFootnote(['ACQUIRING'], products), 'Not subscribed: 出款, 外汇; Included by default: 税票, 平台', '停用产品不列为可订阅');
  assert.equal(productFootnote(['ACQUIRING', 'PAYOUT', 'FX'], products), 'Included by default: 税票, 平台');
  assert.equal(showEntityColumn({ agreementType: 'PARTNER', operatingEntityNos: ['AE001', 'SA001'] }), true);
  assert.equal(showEntityColumn({ agreementType: 'PARTNER', operatingEntityNos: ['AE001'] }), false);
  assert.equal(showEntityColumn({ agreementType: 'CLIENT', operatingEntityNos: [] }), false);
  assert.equal(capabilityDisplayName('CARD', catalog), '银行卡');
  assert.equal(capabilityDisplayName('LEGACY_X', catalog), null);
});

test('F6 按产品打开新增抽屉：卡片只列该产品的项，已开通仍带上那条开通项；不给产品时列全部已订阅产品', () => {
  const choices = capabilityChoices(catalog, ['ACQUIRING', 'PAYOUT'], [capability('2', 'CARD')], 'AE001', 'ACQUIRING');
  assert.deepEqual(choices.map(choice => [choice.item.capabilityCode, choice.existing?.agreementCapabilityId ?? null]), [['CARD', '2'], ['APPLE_PAY', null]]);
  assert.deepEqual(capabilityChoices(catalog, ['ACQUIRING', 'PAYOUT'], [], 'AE001').map(choice => choice.item.capabilityCode),
    ['CARD', 'APPLE_PAY', 'PAYOUT_LOCAL', 'PAYOUT_SWIFT']);
});
