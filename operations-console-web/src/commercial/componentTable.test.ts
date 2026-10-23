import assert from 'node:assert/strict';
import test from 'node:test';
import { bandIssues, componentTypes, priceDraft, pricesBody } from './commercial.ts';
import type { ComponentType, Price, PriceComponent } from './commercial.ts';
import {
  componentCurrency, componentTypePatch, copiedComponentCode, copyComponentDraft, editableColumns, isEditable,
  labeledOptions, percentInput, percentIssue, priceSummary, rateFromPercent, tierSummary, toFormDrafts, toSubmitDrafts,
} from './componentTable.ts';

let componentNo = 0;
const component = (componentCode: string, componentType: ComponentType, extra: Partial<PriceComponent> = {}): PriceComponent => ({
  componentId: `73190000000000009${++componentNo}`, componentNo, componentCode, componentType, triggerCode: 'CAPTURE', calculationBase: 'GROSS_AMOUNT',
  refundBehavior: 'RETURN_PRORATA', rate: null, fixedAmount: null, currency: null, minAmount: null, maxAmount: null, bandBasis: null, bandMode: null,
  bands: [], benchmark: null, ...extra });
const settlement = { settlementBusinessDays: 2, businessCalendarCode: 'AE_BUSINESS_DAYS', settlementTimezone: 'UTC', cutoffSecondOfDay: 86399 };
const price = (priceId: string, currency: string, pricingProfileCode: string, priorityNo: number, components: PriceComponent[], client = true): Price => ({
  priceId, entityNo: 'AE001', currency, pricingProfileCode, paymentMethodCode: 'CARD', priorityNo, conditions: [], components,
  settlement: client ? settlement : null, sourceTemplatePriceId: null });
const acquiring = (currency: string) => [
  component('CAPTURE_TIERED', 'TIERED', { currency, bandBasis: 'TRANSACTION_AMOUNT', bandMode: 'WHOLE_BASE', bands: [
    { bandId: '1', bandNo: 1, lowerBound: '0', upperBound: '1000', rate: '0.029', fixedAmount: null, currency },
    { bandId: '2', bandNo: 2, lowerBound: '1000', upperBound: null, rate: '0.025', fixedAmount: null, currency }] }),
  component('CAPTURE_FIXED', 'FIXED', { calculationBase: 'FIXED_PER_EVENT', refundBehavior: 'KEEP', fixedAmount: '0.30', currency }),
  component('SALE_PCT', 'PERCENTAGE', { triggerCode: 'SALE', rate: '0.029', minAmount: '0.50', currency }),
  component('SALE_PLAIN_PCT', 'PERCENTAGE', { triggerCode: 'SALE', rate: '0.0001234567', currency: null }),
  component('REFUND_FIXED', 'FIXED', { triggerCode: 'REFUND', calculationBase: 'FIXED_PER_EVENT', refundBehavior: 'KEEP', fixedAmount: '1.00', currency }),
  component('CHARGEBACK_FIXED', 'FIXED', { triggerCode: 'CHARGEBACK', calculationBase: 'FIXED_PER_EVENT', refundBehavior: 'KEEP', fixedAmount: '15.00', currency }),
];
/** DEMO 样本（AE001）：AED / USD 收单价格各一条（Client，带结算字段），一条 Partner 分润价格（无结算字段，费率 1 与 0）。 */
const demoPrices: Price[] = [
  price('7319000000000000801', 'AED', 'CARD_VISA_CREDIT_DOMESTIC', 1, acquiring('AED')),
  price('7319000000000000802', 'USD', 'CARD_STANDARD', 5, acquiring('USD')),
  price('7319000000000000803', 'USD', 'CARD_STANDARD', 6, [
    component('PARTNER_SHARE', 'PERCENTAGE', { calculationBase: 'ACTUAL_CLIENT_FEE', refundBehavior: 'RETURN_ALL', rate: '1' }),
    component('PARTNER_ZERO', 'PERCENTAGE', { calculationBase: 'BUY_RATE_DIFFERENCE', refundBehavior: 'KEEP', rate: '0', maxAmount: '5.00', currency: 'USD' }),
  ], false),
];

test('百分比换算：字符串精确移动小数点，往返一致', () => {
  assert.equal(percentInput('0.025'), '2.5');
  assert.equal(rateFromPercent('2.2'), '0.022');
  assert.equal(rateFromPercent('2.5'), '0.025');
  assert.equal(percentInput('1'), '100');
  assert.equal(rateFromPercent('100'), '1');
  assert.equal(percentInput('0'), '0');
  assert.equal(rateFromPercent('0'), '0');
  assert.equal(percentInput('0.5'), '50');
  assert.equal(rateFromPercent('50'), '0.5');
  assert.equal(percentInput('0.0000000001'), '0.00000001');
  assert.equal(rateFromPercent('0.00000001'), '0.0000000001');
  assert.equal(percentInput('0.1234567891'), '12.34567891');
  assert.equal(rateFromPercent('12.34567891'), '0.1234567891');
  assert.equal(rateFromPercent('2.50'), '0.025');
  assert.equal(rateFromPercent(' 2.9 '), '0.029');
  for (const rate of ['0', '1', '0.029', '0.0275', '0.00005', '0.1', '0.9999999999', '0.0000000001']) {
    assert.equal(rateFromPercent(percentInput(rate)), rate, rate);
  }
});

test('百分比换算：空值保持空，非法输入原样返回并由校验报错', () => {
  assert.equal(percentInput(null), null);
  assert.equal(rateFromPercent(null), null);
  assert.equal(percentInput(undefined), null);
  assert.equal(rateFromPercent(''), '');
  assert.equal(percentInput(''), '');
  assert.equal(rateFromPercent('2.2%'), '2.2%');
  assert.equal(rateFromPercent('-1'), '-1');
  assert.equal(rateFromPercent('abc'), 'abc');
  assert.equal(percentIssue(null), null);
  assert.equal(percentIssue(''), null);
  assert.equal(percentIssue('2.2'), null);
  assert.equal(percentIssue('100'), null);
  assert.equal(percentIssue('100.00'), null);
  assert.equal(percentIssue('0.00000001'), null);
  assert.ok(percentIssue('0.000000001'), '9 位小数超出接口 10 位');
  assert.ok(percentIssue('100.5'));
  assert.ok(percentIssue('101'));
  assert.ok(percentIssue('2.2%'));
  assert.ok(percentIssue('-1'));
  assert.ok(percentIssue('02'));
});

test('组件币种：固定额、阶梯、带上下限的百分比取价格币种，其余为空', () => {
  assert.equal(componentCurrency({ componentType: 'FIXED', minAmount: null, maxAmount: null }, 'AED'), 'AED');
  assert.equal(componentCurrency({ componentType: 'TIERED', minAmount: null, maxAmount: null }, 'AED'), 'AED');
  assert.equal(componentCurrency({ componentType: 'PERCENTAGE', minAmount: '0.50', maxAmount: null }, 'USD'), 'USD');
  assert.equal(componentCurrency({ componentType: 'PERCENTAGE', minAmount: null, maxAmount: '9' }, 'USD'), 'USD');
  assert.equal(componentCurrency({ componentType: 'PERCENTAGE', minAmount: null, maxAmount: null }, 'USD'), null);
  assert.equal(componentCurrency({ componentType: 'PERCENTAGE', minAmount: '  ', maxAmount: '' }, 'USD'), null);
});

test('改价格币种后组件币种随之推导，费率换回小数', () => {
  const [draft] = toFormDrafts([demoPrices[0]]);
  const [submitted] = toSubmitDrafts([{ ...draft, currency: 'USD' }]);
  assert.deepEqual(submitted.components.map(c => c.currency), ['USD', 'USD', 'USD', null, 'USD', 'USD']);
  assert.deepEqual(submitted.components[0].bands?.map(b => b.rate), ['0.029', '0.025']);
  assert.equal(submitted.components[2].rate, '0.029');
});

test('表单草稿：组件与档位费率显示为百分比', () => {
  const [draft] = toFormDrafts([demoPrices[0]]);
  assert.equal(draft.components[2].rate, '2.9');
  assert.equal(draft.components[3].rate, '0.01234567');
  assert.deepEqual(draft.components[0].bands?.map(b => b.rate), ['2.9', '2.5']);
  assert.equal(draft.components[1].rate, null);
  assert.equal(draft.components[1].bands, null);
  assert.deepEqual(bandIssues(draft.components[0].bands ?? [], 'WHOLE_BASE'), [], '档位提示只看有无费率，与百分比换算无关');
});

test('AC-01：不改值直接保存，请求体与改版前逐字段相同（含组件币种）', () => {
  const before = pricesBody('5', demoPrices.map(priceDraft));
  const after = pricesBody('5', toSubmitDrafts(toFormDrafts(demoPrices)));
  assert.deepEqual(after, before);
  assert.equal(after.prices[2].settlementBusinessDays, null);
  assert.equal(after.prices[0].components[3].currency, null);
  assert.equal(after.prices[0].components[1].currency, 'AED');
});

test('复制行：组件代码追加 _COPY，不超过 64 位；档位深拷贝', () => {
  assert.equal(copiedComponentCode('MDR'), 'MDR_COPY');
  const long = 'A'.repeat(64);
  assert.equal(copiedComponentCode(long), `${'A'.repeat(59)}_COPY`);
  assert.equal(copiedComponentCode(long).length, 64);
  const [draft] = toFormDrafts([demoPrices[0]]);
  const copy = copyComponentDraft(draft.components[0]);
  assert.equal(copy.componentCode, 'CAPTURE_TIERED_COPY');
  assert.deepEqual(copy.bands, draft.components[0].bands);
  assert.notEqual(copy.bands?.[0], draft.components[0].bands?.[0]);
});

test('价格摘要与阶梯单元格文案', () => {
  const [draft] = toFormDrafts([demoPrices[0]]);
  assert.equal(priceSummary(draft, 0), 'Price 1 · AE001 · AED · CARD_VISA_CREDIT_DOMESTIC · Priority 1 · 6 components');
  assert.equal(priceSummary(undefined, 2), 'Price 3 · — · — · — · Priority — · 0 components');
  assert.equal(tierSummary(draft.components[0].bands, 'WHOLE_BASE'), '2 tiers · Whole Base');
  assert.equal(tierSummary(null, null), '0 tiers · No mode selected');
});

test('类型 → 可编辑列；切换为阶梯补齐默认档位', () => {
  assert.deepEqual(componentTypes.map(type => editableColumns[type]), [['rate', 'minAmount', 'maxAmount'], ['fixedAmount'], ['bands']]);
  assert.equal(isEditable('FIXED', 'rate'), false);
  assert.equal(isEditable('PERCENTAGE', 'minAmount'), true);
  assert.equal(isEditable(undefined, 'rate'), false);
  assert.deepEqual(componentTypePatch('TIERED', { bands: null, bandBasis: null, bandMode: null }), {
    bands: [{ lowerBound: '0', upperBound: null, rate: null, fixedAmount: null }], bandBasis: 'TRANSACTION_AMOUNT', bandMode: 'WHOLE_BASE' });
  assert.deepEqual(componentTypePatch('FIXED', { bands: null, bandBasis: null, bandMode: null }), {});
});

test('标签：计算基数、退款处理、阶梯基数与模式；缺失时回退代码', () => {
  assert.deepEqual(labeledOptions(['GROSS_AMOUNT', 'UNKNOWN'], 'calculationBase'), [{ value: 'GROSS_AMOUNT', label: 'Gross Amount' }, { value: 'UNKNOWN', label: 'UNKNOWN' }]);
  assert.deepEqual(labeledOptions(['RETURN_PRORATA'], 'refundBehavior'), [{ value: 'RETURN_PRORATA', label: 'Refund Pro Rata' }]);
  assert.deepEqual(labeledOptions(['PERIOD_VOLUME'], 'bandBasis'), [{ value: 'PERIOD_VOLUME', label: 'Period Volume' }]);
  assert.deepEqual(labeledOptions(['PROGRESSIVE'], 'bandMode'), [{ value: 'PROGRESSIVE', label: 'Progressive' }]);
});
