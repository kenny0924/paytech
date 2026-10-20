import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { commercialPaths } from './commercial.ts';
import {
  capabilityLabel, cutoffTime, dashboardMetrics, decodeDashboard, decodeRateCard, decodeSimulation, priceErrorLabels, rateCardPath, simulationBody,
  simulationErrorMessage, simulationFieldName,
} from './commercialPreview.ts';
import type { SimulationFormValues } from './commercialPreview.ts';

const clientId = '7300000000000000011';
const component = { componentId: '7319000000000000811', componentNo: 1, componentCode: 'MDR', componentType: 'PERCENTAGE', triggerCode: 'PAYMENT_CAPTURED',
  calculationBase: 'GROSS_AMOUNT', refundBehavior: 'RETURN_PRORATA', rate: '0.029', fixedAmount: null, currency: 'USD', minAmount: '0.50', maxAmount: null,
  bandBasis: null, bandMode: null, bands: [] };
const settlement = { settlementBusinessDays: 2, businessCalendarCode: 'AE_BUSINESS_DAYS', settlementTimezone: 'Asia/Dubai', cutoffSecondOfDay: 0 };
const rateCard = { clientId, asOf: '2026-10-20T00:00:00Z', currency: null, agreements: [{
  agreementId: '7319000000000000101', agreementNo: 'AGR-1', entityNo: 'AE001', businessMode: 'MOR', fundMode: 'SETTLE_THROUGH_PLATFORM', pricingAuthority: 'PLATFORM',
  effectiveFrom: '2026-10-01T00:00:00Z', effectiveTo: null, products: ['CARD_ACQUIRING'],
  capabilities: [{ agreementCapabilityId: '7319000000000000301', capabilityCode: 'CARD_VISA', productCode: 'CARD_ACQUIRING', capabilityKind: 'PAYMENT_METHOD',
    paymentMethodCode: 'CARD', dimensionCode: 'CARD_BRAND', dimensionValue: 'VISA', entityNo: 'AE001', status: 'ENABLED', effectiveFrom: '2026-10-01T00:00:00Z',
    effectiveTo: null, rollingReserveRate: '0.05', rollingReserveDays: 90, limits: [{ limitCode: 'CURRENCY', limitValue: 'USD' }] }],
  termVersions: [{ planId: '7319000000000000501', termType: 'CLIENT_PRICING', versionId: '7319000000000000601', versionNo: '1', status: 'ACTIVE', timing: 'CURRENT',
    effectiveFrom: '2026-10-01T00:00:00Z', effectiveTo: null, pricingModel: 'BLENDED', feeTaxTreatment: 'EXCLUSIVE',
    capabilities: [{ agreementCapabilityId: '7319000000000000301', capabilityCode: 'CARD_VISA', currencies: [{ currency: 'USD', profiles: [{
      priceId: '7319000000000000701', pricingProfileCode: 'CARD_VISA_STANDARD', profileName: 'Visa 标准', specificity: 1, priorityNo: 10,
      conditions: [{ dimensionCode: 'CARD_BRAND', conditionValue: 'VISA' }], settlement,
      feeEvents: [{ feeEventCode: 'PAYMENT_CAPTURED', components: [component] }] }] }] }] }] }] };
const price = { termType: 'CLIENT_PRICING', agreementId: '7319000000000000101', planId: '7319000000000000501', termVersionId: '7319000000000000601',
  entityNo: 'AE001', clientId, localPartnerId: null, priceId: '7319000000000000701', pricingProfileCode: 'CARD_VISA_STANDARD', paymentMethodCode: 'CARD',
  priorityNo: 10, currency: 'USD', pricingModel: 'BLENDED', feeTaxTreatment: 'EXCLUSIVE', conditions: [{ dimensionCode: 'CARD_BRAND', conditionValue: 'VISA' }],
  components: [component], settlement };
const form: SimulationFormValues = { termVersionId: ' 7319000000000000601 ', entityNo: 'ae001', agreementId: '7319000000000000101', termType: 'CLIENT_PRICING',
  currency: 'usd', contextAsOfTime: '2026-10-20T00:00:00Z', ownerId: clientId, paymentMethodCode: 'card',
  dimensions: [{ dimensionCode: 'CARD_BRAND', value: 'visa' }, { dimensionCode: 'THREE_DS_RESULT', notApplicable: true },
    { dimensionCode: 'ISSUER_COUNTRY', value: ' ' }, { dimensionCode: 'CARD_BRAND', value: 'MASTERCARD' }], amount: ' 1000.00 ' };

test('工作台：未接入卡片 count 必须为 null，已接入必须为非负整数', () => {
  const pending = { asOf: '2026-10-20T00:00:00Z', metrics: dashboardMetrics.map(metricCode => ({ metricCode, count: null, status: 'PENDING_INTEGRATION' })) };
  assert.equal(decodeDashboard(pending).metrics.length, 4);
  assert.equal(decodeDashboard({ ...pending, metrics: [{ metricCode: 'ACTIVE_AGREEMENTS', count: 3, status: 'AVAILABLE' }] }).metrics[0].count, 3);
  assert.throws(() => decodeDashboard({ ...pending, metrics: [{ metricCode: 'ACTIVE_AGREEMENTS', count: 0, status: 'PENDING_INTEGRATION' }] }), ApiError);
  assert.throws(() => decodeDashboard({ ...pending, metrics: [{ metricCode: 'UNKNOWN', count: null, status: 'PENDING_INTEGRATION' }] }), ApiError);
});

test('费率卡：Client 一致时解码合同、开通项、版本与币种块；Client 不一致视为无效响应', () => {
  const card = decodeRateCard(clientId)(rateCard);
  const block = card.agreements[0].termVersions[0].capabilities[0].currencies[0];
  assert.equal(card.currency, null);
  assert.equal(block.profiles[0].feeEvents[0].components[0].rate, '0.029');
  assert.equal(block.profiles[0].settlement?.settlementTimezone, 'Asia/Dubai');
  assert.throws(() => decodeRateCard('7300000000000000099')(rateCard), ApiError);
  assert.throws(() => decodeRateCard(clientId)({ ...rateCard, agreements: [{ ...rateCard.agreements[0], entityNo: 'X1' }] }), ApiError);
});

test('费率卡查询路径：空值不带参数，币种转大写', () => {
  assert.equal(rateCardPath(commercialPaths.rateCards, { clientId }), `/commercial-admin/v1/rate-cards?clientId=${clientId}`);
  assert.equal(rateCardPath(commercialPaths.rateCards, { clientId, asOf: '2026-10-20T00:00:00Z', currency: 'aed' }),
    `/commercial-admin/v1/rate-cards?clientId=${clientId}&asOf=2026-10-20T00%3A00%3A00Z&currency=AED`);
});

test('显示辅助：开通项名称与截止时刻', () => {
  assert.equal(capabilityLabel(rateCard.agreements[0].capabilities[0]), 'CARD_VISA（CARD · CARD_BRAND=VISA）');
  assert.equal(capabilityLabel({ capabilityCode: 'PAYOUT', paymentMethodCode: null, dimensionCode: null, dimensionValue: null }), 'PAYOUT');
  assert.equal(cutoffTime(0), '00:00:00');
  assert.equal(cutoffTime(64800), '18:00:00');
});

test('试算请求：去空白转大写；不适用为 null，空值不提交，同维度只保留首行；Partner 条款写 localPartnerId', () => {
  const body = simulationBody(form, false);
  assert.deepEqual(body, { termVersionId: '7319000000000000601', entityNo: 'AE001', agreementId: '7319000000000000101', termType: 'CLIENT_PRICING',
    currency: 'USD', contextAsOfTime: '2026-10-20T00:00:00Z', clientId, localPartnerId: null,
    paymentFacts: { paymentMethodCode: 'CARD', dimensions: { CARD_BRAND: 'VISA', THREE_DS_RESULT: null } }, amount: '1000.00' });
  const partner = simulationBody({ ...form, termType: 'PARTNER_SHARE', amount: '', paymentMethodCode: '' }, true);
  assert.equal(partner.clientId, null);
  assert.equal(partner.localPartnerId, clientId);
  assert.equal(partner.paymentFacts.paymentMethodCode, null);
  assert.equal(Object.hasOwn(partner, 'amount'), false);
});

test('试算结果：命中价格须与请求的版本、OE、币种一致；示例费用可为空', () => {
  const request = { termVersionId: '7319000000000000601', entityNo: 'AE001', currency: 'USD' };
  const illustration = { amount: '1000.00', currency: 'USD', fractionDigits: 2, feeEvents: [{ feeEventCode: 'PAYMENT_CAPTURED', totalAmount: '29.00',
    components: [{ componentNo: 1, componentCode: 'MDR', componentType: 'PERCENTAGE', calculationBase: 'GROSS_AMOUNT', computed: true,
      rawAmount: '29.00000000', amount: '29.00', note: null }] }] };
  const result = decodeSimulation(request)({ price, illustration });
  assert.equal(result.illustration?.feeEvents[0].totalAmount, '29.00');
  assert.equal(decodeSimulation(request)({ price, illustration: null }).illustration, null);
  assert.throws(() => decodeSimulation({ ...request, currency: 'AED' })({ price, illustration: null }), ApiError);
  assert.throws(() => decodeSimulation(request)({ price, illustration: { ...illustration, feeEvents: [{ ...illustration.feeEvents[0],
    components: [{ ...illustration.feeEvents[0].components[0], amount: null }] }] } }), ApiError);
});

test('试算失败说明：命中失败带错误码与 commercial 原因；业务拒绝显示原因；其他交给通用通知', () => {
  const notFound = simulationErrorMessage(new ApiError(422, 'PRICE_NOT_FOUND', 'r', 'AE001/USD/CARD 下没有价格'));
  assert.equal(notFound, `${priceErrorLabels.PRICE_NOT_FOUND}。commercial：AE001/USD/CARD 下没有价格（PRICE_NOT_FOUND）`);
  assert.match(simulationErrorMessage(new ApiError(409, 'PRICE_AMBIGUOUS')) ?? '', /歧义.*（PRICE_AMBIGUOUS）$/);
  assert.equal(simulationErrorMessage(new ApiError(400, 'BUSINESS_CHECK_FAILED', 'r', '版本不是时点适用版本')), '业务校验未通过：版本不是时点适用版本');
  assert.equal(simulationErrorMessage(new ApiError(404, 'NOT_FOUND')), '条款版本不存在。');
  assert.equal(simulationErrorMessage(new ApiError(503, 'DEPENDENCY_UNAVAILABLE')), null);
  assert.equal(simulationErrorMessage(new Error('x')), null);
});

test('试算字段错误定位：Owner 字段映射到表单字段，维度事实不定位', () => {
  assert.equal(simulationFieldName('termVersionId'), 'termVersionId');
  assert.equal(simulationFieldName('clientId'), 'ownerId');
  assert.equal(simulationFieldName('localPartnerId'), 'ownerId');
  assert.equal(simulationFieldName('paymentFacts.paymentMethodCode'), 'paymentMethodCode');
  assert.equal(simulationFieldName('paymentFacts.dimensions'), null);
});
