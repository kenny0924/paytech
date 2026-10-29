import test from 'node:test';
import assert from 'node:assert/strict';
import { testT } from '../i18n/testing.ts';
import { fieldLabel, valueView } from './schemaLabel.ts';

/**
 * REQ-12 业务信息补采在审核"申请"页签与 Client 详情的显示：client-master 资料 values 的 business 组
 * <li>旧版 Schema 冻结的申请四键都为空（"未采集"）；新版申请空的可选项为"未填写"；非预售不显示天数行。</li>
 */
const tReview = testT('client-review'), tSchema = testT('onboarding-schema');
const v3Organization = { businessModelCode: 'SUBSCRIPTION', websiteUrl: 'https://gulfbytes.ae', expectedPaymentMethods: 'APPLE_PAY,CARD,STC_PAY',
  refundPolicyUrl: 'https://gulfbytes.ae/refund-policy', fulfillmentType: 'PRE_ORDER', fulfillmentDays: '30' };
const view = (fields: Record<string, string | null>, field: string) => valueView(tSchema, 'business', field, fields[field] ?? null, 'AE', fields);

test('expected payment methods show one tag per code with its name; unknown codes show the code only', () => {
  assert.deepEqual(view(v3Organization, 'expectedPaymentMethods'), { kind: 'tags', tags: [
    { code: 'APPLE_PAY', label: 'Apple Pay' }, { code: 'CARD', label: tSchema('paymentMethod.CARD') }, { code: 'STC_PAY', label: 'STC Pay' }] });
  assert.deepEqual(view({ ...v3Organization, expectedPaymentMethods: 'CARD,NEW_WALLET' }, 'expectedPaymentMethods'),
    { kind: 'tags', tags: [{ code: 'CARD', label: 'Cards' }, { code: 'NEW_WALLET', label: 'NEW_WALLET' }] });
});

test('refund policy and website are links opened in a new tab; non-web values stay text', () => {
  assert.deepEqual(view(v3Organization, 'refundPolicyUrl'), { kind: 'link', href: 'https://gulfbytes.ae/refund-policy', text: 'https://gulfbytes.ae/refund-policy' });
  assert.deepEqual(view(v3Organization, 'websiteUrl'), { kind: 'link', href: 'https://gulfbytes.ae', text: 'https://gulfbytes.ae' });
  assert.deepEqual(view({ ...v3Organization, refundPolicyUrl: 'javascript:alert(1)' }, 'refundPolicyUrl'), { kind: 'text', text: 'javascript:alert(1)' });
});

test('fulfillment shows the catalog name as a tag and days as a count', () => {
  assert.deepEqual(view(v3Organization, 'fulfillmentType'), { kind: 'tag', text: tSchema('catalog.FULFILLMENT_TYPE.PRE_ORDER') });
  assert.deepEqual(view(v3Organization, 'fulfillmentDays'), { kind: 'text', text: '30 days' });
  assert.deepEqual(view({ ...v3Organization, fulfillmentDays: '1' }, 'fulfillmentDays'), { kind: 'text', text: '1 day' });
});

test('applications on an older Schema show "Not collected" for all four; a v3 application shows "Not filled in" for an empty optional item', () => {
  const legacy = { businessModelCode: 'SERVICES', websiteUrl: null, expectedPaymentMethods: null, refundPolicyUrl: null, fulfillmentType: null, fulfillmentDays: null };
  for (const field of ['expectedPaymentMethods', 'refundPolicyUrl', 'fulfillmentType', 'fulfillmentDays'])
    assert.deepEqual(view(legacy, field), { kind: 'empty', text: tReview('application.notCollected') }, field);
  assert.deepEqual(view(legacy, 'websiteUrl'), { kind: 'empty', text: '—' }, 'other empty fields keep the dash');
  const individual = { ...v3Organization, refundPolicyUrl: null, fulfillmentType: 'INSTANT', fulfillmentDays: null };
  assert.deepEqual(view(individual, 'refundPolicyUrl'), { kind: 'empty', text: tReview('application.notFilled') });
  assert.equal(view(individual, 'fulfillmentDays'), null, 'no days row unless pre-order');
  assert.deepEqual(view({ ...v3Organization, fulfillmentDays: null }, 'fulfillmentDays'), { kind: 'empty', text: tReview('application.notFilled') });
});

test('the four fields have reviewer-facing names', () => {
  assert.deepEqual(['expectedPaymentMethods', 'refundPolicyUrl', 'fulfillmentType', 'fulfillmentDays'].map(field => fieldLabel(tReview, tSchema, 'business', field)),
    ['Expected Payment Methods', 'Refund Policy', 'Fulfillment', 'Delivery Time']);
  assert.equal(tReview('config.enum.widget.MULTI_SELECT'), 'Multi-select');
});
