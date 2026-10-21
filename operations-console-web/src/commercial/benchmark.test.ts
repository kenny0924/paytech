import assert from 'node:assert/strict';
import test from 'node:test';
import { priceMatrix } from './commercial.ts';
import type { AgreementCapability, Price, PriceComponent } from './commercial.ts';
import {
  belowStandardComponents, benchmarkDetail, benchmarkMark, cellBelowStandard, conditionsCompatible, groupPricesByCapability, priceKey, priceMatchesCapability,
  relevanceText, sourceLabel,
} from './benchmark.ts';
import type { CatalogCapability } from './commercialConsole.ts';
import type { ComponentBenchmark } from './termReview.ts';

const below: ComponentBenchmark = { status: 'BELOW_STANDARD', standardRate: '0.029', standardFixedAmount: '0.30', standardMinAmount: null, standardMaxAmount: '5.00' };
const component = (benchmark: ComponentBenchmark | null): PriceComponent => ({ componentId: '1', componentNo: 1, componentCode: 'MDR', componentType: 'PERCENTAGE',
  triggerCode: 'CAPTURE', calculationBase: 'GROSS_AMOUNT', refundBehavior: 'RETURN_PRORATA', rate: '0.025', fixedAmount: null, currency: null, minAmount: null,
  maxAmount: null, bandBasis: null, bandMode: null, bands: [], benchmark });
const price = (currency: string, pricingProfileCode: string, paymentMethodCode: string | null, conditions: [string, string][] = [],
  benchmark: ComponentBenchmark | null = null): Price => ({ priceId: '1', entityNo: 'AE001', currency, pricingProfileCode, paymentMethodCode, priorityNo: 1,
  conditions: conditions.map(([dimensionCode, conditionValue]) => ({ dimensionCode: dimensionCode as Price['conditions'][number]['dimensionCode'], conditionValue })),
  components: [component(benchmark)], settlement: null, sourceTemplatePriceId: null });
const capability = (agreementCapabilityId: string, capabilityCode: string, conditions: [string, string[]][], status: AgreementCapability['status'] = 'ENABLED'):
  AgreementCapability => ({ agreementCapabilityId, entityNo: 'AE001', capabilityCode, status, effectiveFrom: '2026-10-01T00:00:00Z', effectiveTo: null,
  rollingReserveRate: null, rollingReserveDays: null, conditions: conditions.map(([dimensionCode, values]) => ({ dimensionCode, values })), require3ds: false });
const catalog = [{ capabilityCode: 'CARD', paymentMethodCode: 'CARD' }, { capabilityCode: 'APPLE_PAY', paymentMethodCode: 'APPLE_PAY' }] as CatalogCapability[];

test('AC-07 破标展示：低于模板标红「破标」，无来源「无标准可比」，不低于不标；悬停显示模板值', () => {
  assert.deepEqual(benchmarkMark(below), { label: '破标', color: 'red', below: true });
  assert.deepEqual(benchmarkMark({ ...below, status: 'NO_BENCHMARK' }), { label: '无标准可比', color: 'default', below: false });
  assert.equal(benchmarkMark({ ...below, status: 'AT_OR_ABOVE' }), null);
  assert.equal(benchmarkMark(null), null);
  assert.equal(benchmarkDetail(below), '模板值：费率 2.9% · 固定 0.30 · 最高 5.00');
  assert.match(benchmarkDetail({ ...below, status: 'NO_BENCHMARK' }) ?? '', /没有可比较/);
  assert.equal(belowStandardComponents([price('AED', 'A', 'CARD', [], below), price('USD', 'A', 'CARD', [], { ...below, status: 'AT_OR_ABOVE' })]), 1);
  assert.equal(cellBelowStandard([component(null), component(below)]), true);
  assert.equal(sourceLabel({ sourceTemplatePriceId: '9' }, '标准价 v2'), '标准价 v2');
  assert.equal(sourceLabel({ sourceTemplatePriceId: '9' }, null), '标准价目');
  assert.equal(sourceLabel({ sourceTemplatePriceId: null }, '标准价 v2'), null, '手工行无来源');
});

test('相关性：画像条件与接受条件相容（同维度交集，未写维度视为全集）；支付方式、币种必须在开通范围', () => {
  const card = capability('1', 'CARD', [['CURRENCY', ['AED', 'USD']], ['CARD_BRAND', ['VISA', 'MASTERCARD']]]);
  assert.equal(conditionsCompatible(price('AED', 'P', 'CARD', [['CARD_BRAND', 'VISA'], ['FUNDING_TYPE', 'DEBIT']]), card), true);
  assert.equal(conditionsCompatible(price('AED', 'P', 'CARD', [['CARD_BRAND', 'AMEX']]), card), false);
  assert.equal(conditionsCompatible(price('AED', 'P', 'CARD', [['CARD_BRAND', 'AMEX'], ['CARD_BRAND', 'VISA']]), card), true, '同维度任一相交');
  assert.equal(priceMatchesCapability(price('USD', 'P', 'CARD'), card, catalog), true);
  assert.equal(priceMatchesCapability(price('EUR', 'P', 'CARD'), card, catalog), false);
  assert.equal(priceMatchesCapability(price('USD', 'P', 'APPLE_PAY'), card, catalog), false);
  assert.equal(priceMatchesCapability({ ...price('USD', 'P', 'CARD'), entityNo: 'SA001' }, card, catalog), false);
  assert.equal(relevanceText({ entityNo: 'AE001', currency: 'AED', pricingProfileCode: 'CARD_AMEX', reasonCode: 'CONDITION_OUT_OF_SCOPE', message: null }),
    'AE001 / AED / CARD_AMEX：画像条件超出接受条件');
  assert.equal(relevanceText({ entityNo: 'AE001', currency: 'AED', pricingProfileCode: 'X', reasonCode: 'NEW_CODE', message: '说明' }), 'AE001 / AED / X：NEW_CODE（说明）');
});

test('AC-12 按开通项分组：开通项顺序 → 其他价格 → 未对应开通项（相关性提示或挂不上）', () => {
  const capabilities = [capability('1', 'CARD', [['CURRENCY', ['AED']], ['CARD_BRAND', ['VISA']]]), capability('2', 'APPLE_PAY', [['CURRENCY', ['AED']]])];
  const prices = [price('AED', 'CARD_VISA', 'CARD', [['CARD_BRAND', 'VISA']]), price('AED', 'CARD_AMEX', 'CARD', [['CARD_BRAND', 'AMEX']]),
    price('AED', 'APPLE', 'APPLE_PAY'), price('AED', 'PAYOUT', null), price('AED', 'CARD_ANY', 'CARD')];
  const warnings = [{ entityNo: 'AE001', currency: 'AED', pricingProfileCode: 'CARD_ANY', reasonCode: 'CONDITION_OUT_OF_SCOPE', message: null }];
  const groups = groupPricesByCapability(priceMatrix(prices).rows, capabilities, catalog, warnings);
  assert.deepEqual(groups.map(group => [group.key, group.unmatched, group.rows.map(row => row.pricingProfileCode)]), [
    ['1', false, ['CARD_VISA']], ['2', false, ['APPLE']], ['OTHER', false, ['PAYOUT']], ['UNMATCHED', true, ['CARD_AMEX', 'CARD_ANY']]]);
  assert.equal(groups[3].warnings.length, 1, '提示挂在未对应分组');
  assert.equal(groups[0].title, 'AE001 · CARD');
  assert.equal(priceKey(warnings[0]), 'AE001|AED|CARD_ANY');
  const suspendedFirst = groupPricesByCapability(priceMatrix([price('AED', 'APPLE', 'APPLE_PAY')]).rows,
    [capability('3', 'APPLE_PAY', [], 'SUSPENDED'), capability('2', 'APPLE_PAY', [['CURRENCY', ['AED']]])], catalog, []);
  assert.equal(suspendedFirst[0].key, '2', '多个可对应时 ENABLED 优先');
  assert.deepEqual(groupPricesByCapability([], capabilities, catalog, []), []);
});
