import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeContext, decodeFeatureCatalog, defaultVelocity, featureCatalog, listDerived, normalizeVelocity, velocityAllowsCurrent, velocityDerived, velocityMetricOptions, velocityPreview, velocityProblem, velocityWindowLabel, velocityWindowOptions } from './riskVariable.ts';
import type { FeatureCatalog, VelocityParameters } from './riskVariable.ts';

const all = [600, 3600, 86400, 604800];
const highVolume = [{ metric: 'COUNT', windowSeconds: [600, 3600, 86400], includeCurrentAllowed: true }, { metric: 'SUM_AMOUNT', windowSeconds: [600, 3600], includeCurrentAllowed: true }, { metric: 'LAST_AMOUNT', windowSeconds: [600, 3600, 86400], includeCurrentAllowed: false }, { metric: 'SECONDS_SINCE_LAST', windowSeconds: [600, 3600, 86400], includeCurrentAllowed: false }];
const full = ['COUNT', 'SUM_AMOUNT', 'DISTINCT_COUNT', 'LAST_AMOUNT', 'SECONDS_SINCE_LAST'].map(metric => ({ metric, windowSeconds: all, includeCurrentAllowed: metric === 'COUNT' || metric === 'SUM_AMOUNT' }));
/** Risk 工程登记目录的测试副本，形状同 design.md 第 2.1 节。 */
const checkout: FeatureCatalog = { pointCode: 'CHECKOUT_PREPARE', currencyVariableCode: 'INPUT_CURRENCY',
  velocityEvents: [{ eventCode: 'CHECKOUT_ASSESSED', eventName: 'Checkout assessed', includeCurrentAllowed: true }, { eventCode: 'CHECKOUT_REJECTED', eventName: 'Checkout blocked (review or reject)', includeCurrentAllowed: false }],
  velocityAnchors: [{ subjectType: 'CLIENT', metrics: highVolume }, { subjectType: 'OPERATING_ENTITY', metrics: highVolume }], distinctSubjects: [], listSubjects: ['CLIENT', 'OPERATING_ENTITY'] };
const payment: FeatureCatalog = { pointCode: 'PAYMENT_ATTEMPT_RISK', currencyVariableCode: 'PAY_INPUT_CURRENCY',
  velocityEvents: [{ eventCode: 'PAYMENT_ATTEMPT_ASSESSED', eventName: 'Payment attempt assessed', includeCurrentAllowed: true }, { eventCode: 'PAYMENT_ATTEMPT_REJECTED', eventName: 'Payment attempt blocked (review or reject)', includeCurrentAllowed: false }],
  velocityAnchors: [{ subjectType: 'CLIENT', metrics: highVolume }, { subjectType: 'OPERATING_ENTITY', metrics: highVolume }, ...['CONSUMER', 'IP_ADDRESS', 'CARD_FINGERPRINT'].map(subjectType => ({ subjectType, metrics: full }))],
  distinctSubjects: ['CLIENT', 'OPERATING_ENTITY', 'CONSUMER', 'IP_ADDRESS', 'CARD_FINGERPRINT'], listSubjects: ['CLIENT', 'OPERATING_ENTITY', 'CONSUMER', 'IP_ADDRESS', 'CARD_FINGERPRINT', 'CARD_BIN'] };
const base: VelocityParameters = { eventCode: 'PAYMENT_ATTEMPT_ASSESSED', anchorSubjectType: 'CARD_FINGERPRINT', metric: 'COUNT', distinctSubjectType: null, windowSeconds: 600, includeCurrent: false };

test('payment velocity preview reads like the business definition', () => {
  assert.equal(velocityPreview(payment, base), 'Count of Payment attempt assessed for the same Card Fingerprint within 10 minutes (excluding this one)');
  assert.equal(velocityPreview(payment, { ...base, includeCurrent: true }), 'Count of Payment attempt assessed for the same Card Fingerprint within 10 minutes (including this one)');
  assert.equal(velocityPreview(payment, { ...base, anchorSubjectType: 'CONSUMER', metric: 'DISTINCT_COUNT', distinctSubjectType: 'CARD_FINGERPRINT', windowSeconds: 86400 }), 'Distinct Card Fingerprint count of Payment attempt assessed for the same Consumer within 1 day');
});

test('payment dependent parameters are normalized: distinct subject only for DISTINCT_COUNT and include-current only for assessed count or sum', () => {
  assert.equal(normalizeVelocity(payment, { ...base, distinctSubjectType: 'CONSUMER' }).distinctSubjectType, null);
  assert.equal(normalizeVelocity(payment, { ...base, metric: 'DISTINCT_COUNT', distinctSubjectType: 'CARD_FINGERPRINT' }).distinctSubjectType, null);
  assert.equal(normalizeVelocity(payment, { ...base, metric: 'DISTINCT_COUNT', distinctSubjectType: 'IP_ADDRESS' }).distinctSubjectType, 'IP_ADDRESS');
  assert.equal(normalizeVelocity(payment, { ...base, metric: 'LAST_AMOUNT', includeCurrent: true }).includeCurrent, false);
  assert.equal(normalizeVelocity(payment, { ...base, eventCode: 'PAYMENT_ATTEMPT_REJECTED', includeCurrent: true }).includeCurrent, false);
  assert.equal(velocityAllowsCurrent(payment, { eventCode: 'PAYMENT_ATTEMPT_ASSESSED', anchorSubjectType: 'CARD_FINGERPRINT', metric: 'SUM_AMOUNT' }), true);
  assert.equal(velocityProblem({ ...base, metric: 'DISTINCT_COUNT' }), 'Distinct Subjects needs a subject to count.');
  assert.equal(velocityProblem(base), null);
  assert.deepEqual(velocityWindowOptions(payment, base), all);
});

test('payment derived structure matches Risk constraints for feature-backed variables', () => {
  assert.deepEqual({ ...velocityDerived(payment, base) }, { sourceType: 'VELOCITY', valueType: 'NUMBER', pointCode: 'PAYMENT_ATTEMPT_RISK', formatCode: 'NONE', numericUnit: 'SCALAR', currencyCode: null, currencyVariableCode: null, allowedValuesVariableCode: null, currentValue: null, sourceContractCode: null, sourceContractVersion: null, sourceField: null });
  const money = velocityDerived(payment, { ...base, metric: 'SUM_AMOUNT' });
  assert.equal(money.numericUnit, 'MONEY');
  assert.equal(money.currencyVariableCode, 'PAY_INPUT_CURRENCY');
  assert.equal(listDerived('PAYMENT_ATTEMPT_RISK').valueType, 'BOOLEAN');
  assert.equal(listDerived('PAYMENT_ATTEMPT_RISK').pointCode, 'PAYMENT_ATTEMPT_RISK');
  assert.equal(listDerived(null).formatCode, 'NONE');
});

test('checkout defaults to checkout events and Client with checkout currency variable', () => {
  const p = defaultVelocity(checkout);
  assert.deepEqual(p, { eventCode: 'CHECKOUT_ASSESSED', anchorSubjectType: 'CLIENT', metric: 'COUNT', distinctSubjectType: null, windowSeconds: 600, includeCurrent: false });
  assert.equal(velocityPreview(checkout, { ...p, windowSeconds: 3600 }), 'Count of Checkout assessed for the same Client within 1 hour (excluding this one)');
  const money = velocityDerived(checkout, { ...p, metric: 'SUM_AMOUNT' });
  assert.equal(money.pointCode, 'CHECKOUT_PREPARE');
  assert.equal(money.currencyVariableCode, 'INPUT_CURRENCY');
});

test('checkout rejected event cannot include current; Client has no distinct count and sum has no one-day window', () => {
  assert.equal(velocityAllowsCurrent(checkout, { eventCode: 'CHECKOUT_REJECTED', anchorSubjectType: 'CLIENT', metric: 'COUNT' }), false);
  assert.equal(velocityAllowsCurrent(checkout, { eventCode: 'CHECKOUT_ASSESSED', anchorSubjectType: 'CLIENT', metric: 'COUNT' }), true);
  assert.equal(normalizeVelocity(checkout, { ...defaultVelocity(checkout), eventCode: 'CHECKOUT_REJECTED', includeCurrent: true }).includeCurrent, false);
  assert.equal(velocityMetricOptions(checkout, 'CLIENT').some(m => m.metric === 'DISTINCT_COUNT'), false);
  assert.deepEqual(velocityWindowOptions(checkout, { anchorSubjectType: 'CLIENT', metric: 'SUM_AMOUNT' }), [600, 3600]);
  assert.equal(normalizeVelocity(checkout, { ...defaultVelocity(checkout), windowSeconds: 86400, metric: 'SUM_AMOUNT' }).windowSeconds, 3600);
  assert.equal(normalizeVelocity(checkout, { ...defaultVelocity(checkout), metric: 'DISTINCT_COUNT', distinctSubjectType: 'OPERATING_ENTITY' }).metric, 'COUNT');
});

test('a window no longer allowed falls back to the longest allowed window not exceeding it, else the shortest', () => {
  const oneDayCount = normalizeVelocity(checkout, { ...defaultVelocity(checkout), windowSeconds: 86400 });
  assert.equal(oneDayCount.windowSeconds, 86400);
  assert.equal(normalizeVelocity(checkout, { ...oneDayCount, metric: 'SUM_AMOUNT' }).windowSeconds, 3600);
  assert.equal(normalizeVelocity(checkout, { ...oneDayCount, windowSeconds: 300 }).windowSeconds, 600);
});

test('switching point resets parameters outside the catalog to allowed values', () => {
  const fromPayment = normalizeVelocity(checkout, { ...base, windowSeconds: 604800 });
  assert.deepEqual(fromPayment, { eventCode: 'CHECKOUT_ASSESSED', anchorSubjectType: 'CLIENT', metric: 'COUNT', distinctSubjectType: null, windowSeconds: 86400, includeCurrent: false });
  assert.equal(normalizeVelocity(payment, { ...base, anchorSubjectType: 'CLIENT', windowSeconds: 604800 }).windowSeconds, 86400);
});

const context = { supportedPoints: [], sources: [], valueTypes: [], formatProfiles: [], numericUnits: [], limits: { memberCount: 1000, memberBodyBytes: 65536, literalSetCount: 100 }, contracts: [] };

test('authoring context decodes feature catalogs strictly', () => {
  const decoded = decodeContext({ ...context, featureCatalogs: [checkout, payment] });
  assert.deepEqual(decoded.featureCatalogs, [checkout, payment]);
  assert.equal(featureCatalog(decoded, 'CHECKOUT_PREPARE')?.currencyVariableCode, 'INPUT_CURRENCY');
  assert.equal(featureCatalog(decoded, null), null);
  assert.throws(() => decodeContext(context), /doesn't match the protocol/);
  assert.throws(() => decodeContext({ ...context, featureCatalogs: [checkout], extra: 1 }), /doesn't match the protocol/);
  assert.throws(() => decodeContext({ ...context, featureCatalogs: [checkout, checkout] }), /doesn't match the protocol/);
});

test('feature catalog rejects extra keys, unknown codes and unregistered windows', () => {
  assert.throws(() => decodeFeatureCatalog({ ...checkout, extra: true }), /doesn't match the protocol/);
  assert.throws(() => decodeFeatureCatalog({ ...checkout, velocityEvents: [{ ...checkout.velocityEvents[0], extra: 1 }] }), /doesn't match the protocol/);
  assert.throws(() => decodeFeatureCatalog({ ...checkout, listSubjects: ['NOT_A_SUBJECT'] }), /doesn't match the protocol/);
  assert.throws(() => decodeFeatureCatalog({ ...checkout, velocityAnchors: [{ subjectType: 'CLIENT', metrics: [{ metric: 'COUNT', windowSeconds: [1200], includeCurrentAllowed: true }] }] }), /doesn't match the protocol/);
  assert.throws(() => decodeFeatureCatalog({ ...checkout, velocityAnchors: [{ subjectType: 'CLIENT', metrics: [{ metric: 'COUNT', windowSeconds: [3600, 600], includeCurrentAllowed: true }] }] }), /doesn't match the protocol/);
  assert.throws(() => decodeFeatureCatalog({ ...checkout, currencyVariableCode: 'bad' }), /doesn't match the protocol/);
});

test('DAILY_SUM_AMOUNT (REQ-04): decoded from the catalog, money-valued, with UTC calendar-day window labels', () => {
  const daily = { metric: 'DAILY_SUM_AMOUNT', windowSeconds: [86400, 2592000], includeCurrentAllowed: true };
  const catalog = decodeFeatureCatalog({ ...payment, velocityEvents: [...payment.velocityEvents, { eventCode: 'PAYMENT_ATTEMPT_ALLOWED', eventName: 'Payment attempt allowed', includeCurrentAllowed: true }],
    velocityAnchors: [{ subjectType: 'CLIENT', metrics: [...highVolume, daily] }] });
  assert.deepEqual(velocityWindowOptions(catalog, { anchorSubjectType: 'CLIENT', metric: 'DAILY_SUM_AMOUNT' }), [86400, 2592000]);
  const p: VelocityParameters = { eventCode: 'PAYMENT_ATTEMPT_ALLOWED', anchorSubjectType: 'CLIENT', metric: 'DAILY_SUM_AMOUNT', distinctSubjectType: null, windowSeconds: 86400, includeCurrent: true };
  assert.equal(velocityDerived(catalog, p).numericUnit, 'MONEY');
  assert.equal(velocityWindowLabel(86400, 'DAILY_SUM_AMOUNT'), 'Today (UTC)');
  assert.equal(velocityWindowLabel(2592000, 'DAILY_SUM_AMOUNT'), 'Last 30 days (UTC, incl. today)');
  assert.equal(velocityWindowLabel(86400, 'SUM_AMOUNT'), '1 day');
  assert.match(velocityPreview(catalog, p), /Today \(UTC\)/);
  assert.throws(() => decodeFeatureCatalog({ ...payment, velocityAnchors: [{ subjectType: 'CLIENT', metrics: [{ ...daily, windowSeconds: [172800] }] }] }), 'unregistered window');
});
