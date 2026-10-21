import assert from 'node:assert/strict';
import test from 'node:test';
import {
  businessModeScopeLabels, capabilityGroupNote, capabilityKindLabels, catalogExpansion, catalogGaps, catalogKeys, catalogLabel, catalogSummary,
  catalogTermTypeLabels, catalogTermTypeOptions, eventGroupNote, groupCatalog, groupExpandedByDefault, mergeFeeEvents, productExpandedByDefault,
} from './catalogTree.ts';
import type { CatalogProductNode } from './catalogTree.ts';
import type { CatalogCapability, CatalogFeeEvent, CatalogProduct } from './commercialConsole.ts';

// 样本与 commercial 初始化数据（commercial-initial-data.sql）一致：7 个产品、11 个可开通项、21 条计费事件登记。
const product = (index: number, productCode: string, productName: string, baseProduct = false): CatalogProduct => ({
  productId: `910000000000000100${index}`, productCode, productName, businessModeScope: 'BOTH', baseProduct, status: 'ACTIVE' });
const products: CatalogProduct[] = [
  product(1, 'ACQUIRING', 'Acquiring'), product(2, 'PAYOUT', 'Payout'), product(3, 'FX', 'FX'), product(4, 'DISPUTE', 'Dispute'),
  product(5, 'TAX_INVOICE', 'Tax and Invoice', true), product(6, 'DATA_ANALYTICS', 'Data Analytics'), product(7, 'PLATFORM', 'Platform', true),
];
let capabilitySequence = 2000;
const capability = (productCode: string, capabilityCode: string, capabilityKind: string, paymentMethodCode: string | null = null,
  dimensionValue: string | null = null): CatalogCapability => ({
  productCapabilityId: `91000000000000${++capabilitySequence}`, productCode, capabilityCode, capabilityName: capabilityCode, capabilityKind,
  paymentMethodCode, dimensionCode: dimensionValue ? 'CARD_BRAND' : null, dimensionValue, status: 'ACTIVE', dimensions: [], changeVersion: 1 });
const capabilities: CatalogCapability[] = [
  capability('ACQUIRING', 'CARD_VISA', 'PAYMENT_METHOD', 'CARD', 'VISA'), capability('ACQUIRING', 'CARD_MC', 'PAYMENT_METHOD', 'CARD', 'MASTERCARD'),
  capability('ACQUIRING', 'CARD_AMEX', 'PAYMENT_METHOD', 'CARD', 'AMEX'), capability('ACQUIRING', 'CARD_UNIONPAY', 'PAYMENT_METHOD', 'CARD', 'UNIONPAY'),
  capability('ACQUIRING', 'APPLE_PAY', 'PAYMENT_METHOD', 'APPLE_PAY'), capability('ACQUIRING', 'GOOGLE_PAY', 'PAYMENT_METHOD', 'GOOGLE_PAY'),
  capability('PAYOUT', 'PAYOUT_LOCAL', 'PAYOUT_RAIL'), capability('PAYOUT', 'PAYOUT_SWIFT', 'PAYOUT_RAIL'), capability('FX', 'FX_SPOT', 'FX'),
  capability('DISPUTE', 'DISPUTE_HANDLING', 'DISPUTE'), capability('DATA_ANALYTICS', 'ANALYTICS_SUBSCRIPTION', 'PERIODIC_SERVICE'),
];
let eventSequence = 3000;
const feeEvent = (productCode: string, feeEventCode: string, termType: string, billingFactType: string): CatalogFeeEvent => ({
  productFeeEventId: `91000000000000${++eventSequence}`, productCode, feeEventCode, termType, billingFactType, capabilityKind: null,
  capabilityCode: null, changeVersion: 1 });
const acquiringTermTypes = ['CLIENT_PRICING', 'PARTNER_SHARE', 'PLATFORM_SERVICE_CHARGE'];
const feeEvents: CatalogFeeEvent[] = [
  ...['AUTH', 'CAPTURE', 'SALE'].flatMap(code => acquiringTermTypes.map(termType => feeEvent('ACQUIRING', code, termType, 'PAYMENT'))),
  ...acquiringTermTypes.map(termType => feeEvent('ACQUIRING', 'REFUND', termType, 'REFUND')),
  feeEvent('ACQUIRING', 'CHARGEBACK', 'CLIENT_PRICING', 'DISPUTE'), feeEvent('ACQUIRING', 'TOKEN_STORED', 'CLIENT_PRICING', 'PAYMENT'),
  feeEvent('PAYOUT', 'PAYOUT_SUBMITTED', 'PAYOUT_FEE', 'PAYOUT_FEE'), feeEvent('PAYOUT', 'PAYOUT_RETURNED', 'PAYOUT_FEE', 'PAYOUT_FEE'),
  feeEvent('FX', 'FX_DEAL', 'FX_SPREAD', 'FX_SELL'), feeEvent('DISPUTE', 'CHARGEBACK', 'DISPUTE_FEE', 'DISPUTE'),
  ...['MONTHLY_CYCLE', 'SETUP', 'KYB_REVIEW'].map(code => feeEvent('PLATFORM', code, 'PLATFORM_FEE', 'PLATFORM_FEE')),
];

const byCode = (nodes: CatalogProductNode[], productCode: string) => {
  const node = nodes.find(item => item.product.productCode === productCode);
  assert.ok(node, `缺少产品 ${productCode}`);
  return node;
};

test('样本与初始化数据规模一致', () => {
  assert.equal(products.length, 7);
  assert.equal(capabilities.length, 11);
  assert.equal(feeEvents.length, 21);
});

test('AC-01 按产品分组：接口顺序与每个产品的可开通项 / 计费事件计数', () => {
  const nodes = groupCatalog(products, capabilities, feeEvents);
  assert.deepEqual(nodes.map(node => [node.product.productCode, node.capabilities.length, node.eventCount]), [
    ['ACQUIRING', 6, 6], ['PAYOUT', 2, 2], ['FX', 1, 1], ['DISPUTE', 1, 1], ['TAX_INVOICE', 0, 0], ['DATA_ANALYTICS', 1, 0], ['PLATFORM', 0, 3],
  ]);
  assert.ok(nodes.every(node => node.eventCount === node.totalEventCount));
  assert.deepEqual(catalogSummary(nodes), { products: 7, capabilities: 11, events: 13 });
});

test('AC-02 同产品同事件合并一行：21 条登记合并为 13 行，条款类型按目录顺序', () => {
  const rows = mergeFeeEvents(feeEvents);
  assert.equal(rows.length, 13);
  assert.equal(rows.reduce((sum, row) => sum + row.termTypes.length, 0), 21);
  const acquiring = byCode(groupCatalog(products, capabilities, feeEvents), 'ACQUIRING');
  assert.deepEqual(acquiring.events.map(row => row.feeEventCode), ['AUTH', 'CAPTURE', 'SALE', 'REFUND', 'CHARGEBACK', 'TOKEN_STORED']);
  for (const row of acquiring.events.slice(0, 4)) assert.deepEqual(row.termTypes, acquiringTermTypes);
  assert.deepEqual(acquiring.events[0].billingFactTypes, ['PAYMENT']);
  assert.deepEqual(acquiring.events[3].billingFactTypes, ['REFUND']);
  assert.deepEqual(acquiring.events[4].termTypes, ['CLIENT_PRICING']);
});

test('计费事实类型去重并按出现顺序保留多个值', () => {
  const rows = mergeFeeEvents([feeEvent('X', 'E', 'A', 'F1'), feeEvent('X', 'E', 'B', 'F1'), feeEvent('X', 'E', 'C', 'F2')]);
  assert.deepEqual(rows.map(row => [row.termTypes, row.billingFactTypes]), [[['A', 'B', 'C'], ['F1', 'F2']]]);
});

test('AC-03 跨产品同名事件不合并：CHARGEBACK 在 ACQUIRING 与 DISPUTE 各一行', () => {
  const chargebacks = mergeFeeEvents(feeEvents).filter(row => row.feeEventCode === 'CHARGEBACK');
  assert.deepEqual(chargebacks.map(row => [row.productCode, row.termTypes]), [['ACQUIRING', ['CLIENT_PRICING']], ['DISPUTE', ['DISPUTE_FEE']]]);
});

test('AC-04 筛选 Partner 分润：只剩 ACQUIRING 的四个事件，可开通项不受影响，计数为命中 / 全部', () => {
  const nodes = groupCatalog(products, capabilities, feeEvents, 'PARTNER_SHARE');
  assert.deepEqual(nodes.map(node => node.product.productCode), ['ACQUIRING']);
  const [acquiring] = nodes;
  assert.deepEqual(acquiring.events.map(row => row.feeEventCode), ['AUTH', 'CAPTURE', 'SALE', 'REFUND']);
  assert.equal(acquiring.capabilities.length, 6);
  assert.deepEqual([acquiring.eventCount, acquiring.totalEventCount], [4, 6]);
  assert.deepEqual(catalogSummary(nodes), { products: 1, capabilities: 6, events: 4 });
});

test('AC-04 筛选 Partner 底价：未登记事件，主表为空，筛选项计数为 0', () => {
  assert.deepEqual(groupCatalog(products, capabilities, feeEvents, 'PARTNER_BUY_RATE'), []);
  const option = catalogTermTypeOptions(feeEvents).find(item => item.termType === 'PARTNER_BUY_RATE');
  assert.deepEqual(option, { termType: 'PARTNER_BUY_RATE', label: 'Partner 底价', eventCount: 0 });
});

test('条款类型筛选项：目录出现顺序加页面已知类型，计数为合并后行数', () => {
  assert.deepEqual(catalogTermTypeOptions(feeEvents).map(item => [item.termType, item.label, item.eventCount]), [
    ['CLIENT_PRICING', 'Client 定价', 6], ['PARTNER_SHARE', 'Partner 分润', 4], ['PLATFORM_SERVICE_CHARGE', '平台服务费', 4],
    ['PAYOUT_FEE', '出款费', 2], ['FX_SPREAD', '外汇点差', 1], ['DISPUTE_FEE', '争议费', 1], ['PLATFORM_FEE', '平台费', 3],
    ['PARTNER_BUY_RATE', 'Partner 底价', 0],
  ]);
  const withBuyRate = catalogTermTypeOptions([...feeEvents, feeEvent('ACQUIRING', 'AUTH', 'PARTNER_BUY_RATE', 'PAYMENT')]);
  assert.equal(withBuyRate.filter(item => item.termType === 'PARTNER_BUY_RATE').length, 1);
  assert.equal(withBuyRate.find(item => item.termType === 'PARTNER_BUY_RATE')?.eventCount, 1);
});

test('AC-05 目录缺口：只有 DATA_ANALYTICS；基础产品 PLATFORM、TAX_INVOICE 不提示', () => {
  assert.deepEqual(catalogGaps(products, capabilities, feeEvents), [{ productCode: 'DATA_ANALYTICS', productName: 'Data Analytics', capabilityCount: 1 }]);
  const nodes = groupCatalog(products, capabilities, feeEvents);
  assert.deepEqual(nodes.filter(node => node.gap).map(node => node.product.productCode), ['DATA_ANALYTICS']);
  assert.deepEqual(catalogGaps(products, capabilities, [...feeEvents, feeEvent('DATA_ANALYTICS', 'MONTHLY_CYCLE', 'CLIENT_PRICING', 'PLATFORM_FEE')]), []);
});

test('筛选时缺口产品无命中事件，不进入主表；未命中的其他产品同样移除', () => {
  const clientPricing = groupCatalog(products, capabilities, feeEvents, 'CLIENT_PRICING');
  assert.deepEqual(clientPricing.map(node => [node.product.productCode, node.eventCount, node.totalEventCount]), [['ACQUIRING', 6, 6]]);
  assert.ok(clientPricing.every(node => !node.gap));
});

test('AC-06 中文标签与未知值回退原代码', () => {
  assert.equal(catalogLabel(catalogTermTypeLabels, 'PAYOUT_FEE'), '出款费');
  assert.equal(catalogLabel(catalogTermTypeLabels, 'FX_SPREAD'), '外汇点差');
  assert.equal(catalogLabel(catalogTermTypeLabels, 'DISPUTE_FEE'), '争议费');
  assert.equal(catalogLabel(catalogTermTypeLabels, 'PLATFORM_FEE'), '平台费');
  assert.equal(catalogLabel(catalogTermTypeLabels, 'CLIENT_PRICING'), 'Client 定价');
  assert.equal(catalogLabel(catalogTermTypeLabels, 'NEW_TERM'), 'NEW_TERM');
  assert.deepEqual(['PAYMENT_METHOD', 'PAYOUT_RAIL', 'FX', 'DISPUTE', 'PERIODIC_SERVICE', 'NEW_KIND'].map(code => catalogLabel(capabilityKindLabels, code)),
    ['支付方式', '出款通道', '外汇', '争议', '周期服务', 'NEW_KIND']);
  assert.deepEqual(['BOTH', 'MOR_ONLY', 'PSP_ONLY', 'OTHER'].map(code => catalogLabel(businessModeScopeLabels, code)), ['MOR / PSP', 'MOR', 'PSP', 'OTHER']);
});

test('AC-09 默认展开：有子项或缺口的产品展开，TAX_INVOICE 收起；分组有数据展开、为 0 收起', () => {
  const nodes = groupCatalog(products, capabilities, feeEvents);
  assert.deepEqual(nodes.filter(node => !productExpandedByDefault(node)).map(node => node.product.productCode), ['TAX_INVOICE']);
  assert.equal(groupExpandedByDefault(6), true);
  assert.equal(groupExpandedByDefault(0), false);
  const filtered = byCode(groupCatalog(products, capabilities, feeEvents, 'PARTNER_SHARE'), 'ACQUIRING');
  assert.equal(productExpandedByDefault(filtered), true);
});

test('分组为空时的原因文案', () => {
  const nodes = groupCatalog(products, capabilities, feeEvents);
  assert.deepEqual(capabilityGroupNote(byCode(nodes, 'PLATFORM')), { text: '基础产品默认具备，无需开通', warning: false });
  assert.deepEqual(eventGroupNote(byCode(nodes, 'TAX_INVOICE')), { text: '无事件费用', warning: false });
  assert.deepEqual(eventGroupNote(byCode(nodes, 'DATA_ANALYTICS')), { text: '⚠ 未登记计费事件，开通后不会产生费用', warning: true });
  assert.equal(capabilityGroupNote(byCode(nodes, 'ACQUIRING')), null);
  assert.equal(eventGroupNote(byCode(nodes, 'PLATFORM')), null);
  const empty = groupCatalog([product(8, 'EMPTY', 'Empty')], [], []);
  assert.deepEqual(capabilityGroupNote(empty[0]), { text: '暂无可开通项', warning: false });
  assert.deepEqual(eventGroupNote(empty[0]), { text: '暂无计费事件', warning: false });
  assert.equal(empty[0].gap, false);
});

test('全部展开 / 全部收起同时作用于产品与两组分组', () => {
  const nodes = groupCatalog(products, capabilities, feeEvents);
  const expanded = catalogExpansion(nodes, true);
  assert.equal(Object.keys(expanded).length, 21);
  assert.equal(expanded[catalogKeys.product('TAX_INVOICE')], true);
  assert.equal(expanded[catalogKeys.events('DATA_ANALYTICS')], true);
  assert.ok(Object.values(catalogExpansion(nodes, false)).every(value => value === false));
});
