import test from 'node:test';
import assert from 'node:assert/strict';
import { listDerived, normalizeVelocity, velocityAllowsCurrent, velocityDerived, velocityPreview, velocityProblem } from './riskVariable.ts';
import type { VelocityParameters } from './riskVariable.ts';

const base: VelocityParameters = { eventCode: 'PAYMENT_ATTEMPT_ASSESSED', anchorSubjectType: 'CARD_FINGERPRINT', metric: 'COUNT', distinctSubjectType: null, windowSeconds: 600, includeCurrent: false };

test('velocity preview reads like the business definition', () => {
  assert.equal(velocityPreview(base), '同一卡指纹 10 分钟内的支付尝试评估次数（不含本次）');
  assert.equal(velocityPreview({ ...base, includeCurrent: true }), '同一卡指纹 10 分钟内的支付尝试评估次数（含本次）');
  assert.equal(velocityPreview({ ...base, anchorSubjectType: 'CONSUMER', metric: 'DISTINCT_COUNT', distinctSubjectType: 'CARD_FINGERPRINT', windowSeconds: 86400 }), '同一消费者 1 天内的支付尝试评估不同卡指纹数');
});

test('dependent parameters are normalized: distinct subject only for DISTINCT_COUNT and include-current only for assessed count or sum', () => {
  assert.equal(normalizeVelocity({ ...base, distinctSubjectType: 'CONSUMER' }).distinctSubjectType, null);
  assert.equal(normalizeVelocity({ ...base, metric: 'DISTINCT_COUNT', distinctSubjectType: 'CARD_FINGERPRINT' }).distinctSubjectType, null);
  assert.equal(normalizeVelocity({ ...base, metric: 'LAST_AMOUNT', includeCurrent: true }).includeCurrent, false);
  assert.equal(normalizeVelocity({ ...base, eventCode: 'PAYMENT_ATTEMPT_REJECTED', includeCurrent: true }).includeCurrent, false);
  assert.equal(velocityAllowsCurrent({ eventCode: 'PAYMENT_ATTEMPT_ASSESSED', metric: 'SUM_AMOUNT' }), true);
  assert.equal(velocityProblem({ ...base, metric: 'DISTINCT_COUNT' }), '不同主体数需要选择去重主体');
  assert.equal(velocityProblem(base), null);
});

test('derived structure matches Risk constraints for feature-backed variables', () => {
  assert.deepEqual({ ...velocityDerived(base) }, { sourceType: 'VELOCITY', valueType: 'NUMBER', pointCode: 'PAYMENT_ATTEMPT_RISK', formatCode: 'NONE', numericUnit: 'SCALAR', currencyCode: null, currencyVariableCode: null, allowedValuesVariableCode: null, currentValue: null, sourceContractCode: null, sourceContractVersion: null, sourceField: null });
  const money = velocityDerived({ ...base, metric: 'SUM_AMOUNT' });
  assert.equal(money.numericUnit, 'MONEY');
  assert.equal(money.currencyVariableCode, 'PAY_INPUT_CURRENCY');
  assert.equal(listDerived().valueType, 'BOOLEAN');
  assert.equal(listDerived().formatCode, 'NONE');
});
