import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { testT } from '../i18n/testing.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import {
  activeStepIndex, decodeTaxDownload, decodeTaxOrderPage, normalizeOrderNo, rateNumber, readRecentSearches, reasonOwner, recentFrom,
  recentSearchKey, recentSearchLimit, rememberSearch, shortRequestId, signedAmount, stepFactKey, taxPaths,
} from './tax.ts';
import type { TaxOrderPage } from './tax.ts';

const t = testT('tax');
const source = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8');
const orderNo = 'ORD-20261029-000184';
const step = (key: string, state: string, extra: object = {}) => ({ key, state, amount: null, countryCode: null, documentNumber: null,
  reportingCurrency: null, occurredAt: null, ...extra });
/** BFF 页面模型（原型 16：已登记、已开票、底稿缺 SAR 汇率）。 */
const registered = {
  orderNo, pageStatus: 'ISSUED', businessMode: 'MOR', buyerClientId: '80001', entityNo: 'AE001', taxSubjectPartyId: '880000000002', currency: 'USD',
  steps: [step('QUOTE', 'DONE', { amount: '5.00', countryCode: 'AE', occurredAt: '2026-10-29T06:31:00Z' }),
    step('RECALCULATION', 'DONE', { amount: '15.00', countryCode: 'SA' }), step('PAYMENT', 'DONE', { amount: '115.00' }),
    step('REGISTRATION', 'DONE', { documentNumber: 'SA-RCPT-2026-000031' }), step('REPORTING', 'ATTENTION', { reportingCurrency: 'SAR' })],
  callout: { kind: 'REPORTING_FX_MISSING', tone: 'WARNING', taxAmount: '15.00', reportingCurrency: 'SAR', expiresAt: null },
  amounts: { calculationId: '912000184002', revision: 2, stage: 'FINAL', status: 'READY', taxAmountSource: 'COMPUTED', netAmount: '100.00',
    taxAmount: '15.00', grossAmount: '115.00', expiresAt: '2026-10-30T06:42:00Z' },
  explanation: { mixed: false, jurisdictionCode: 'SA', taxType: 'VAT', treatment: 'TAXABLE', rate: '0.15', evidenceType: 'BILLING_COUNTRY',
    countryCode: 'SA', schemeCode: 'NON_RESIDENT', packageCode: 'DEMO_SA_SAAS', policyRevision: 1 },
  managedBy: 'PLATFORM_OPERATIONS',
  lines: [{ lineNo: 1, lineRef: 'nimbus-pro-annual', categoryCode: 'SAAS_AUTOMATED', description: 'Nimbus Notes Pro · Annual', quantity: '1', netAmount: '80.00', taxAmount: '12.00',
    rate: '0.15', treatment: 'TAXABLE', ruleCode: 'SA_REMOTE_B2C_STANDARD', componentCount: 1 }],
  quotes: [{ calculationId: '912000184002', revision: 2, stage: 'FINAL', status: 'READY', current: true, taxAmount: '15.00', evidenceType: 'BILLING_COUNTRY',
    countryCode: 'SA', previousCountryCode: 'AE', taxDelta: '10.00', calculatedAt: '2026-10-29T06:42:00Z' },
  { calculationId: '912000184001', revision: 1, stage: 'PROVISIONAL', status: 'READY', current: false, taxAmount: '5.00', evidenceType: 'DECLARED_COUNTRY',
    countryCode: 'AE', previousCountryCode: null, taxDelta: null, calculatedAt: '2026-10-29T06:31:00Z' }],
  blockReasons: [], matching: null,
  receipt: { documentId: '913000031001', documentKind: 'RECEIPT', documentNumber: 'SA-RCPT-2026-000031', issueStatus: 'ISSUED',
    issuedAt: '2026-10-29T06:43:09Z', downloadable: true },
  registration: { transactionId: '920000184001', registeredAt: '2026-10-29T06:43:09Z', taxPointDate: '2026-10-29', legalTimezone: 'Asia/Riyadh',
    sourceFactType: 'ORDER_PAYMENT_SUCCEEDED', sourceFactId: '912000184', calculationRevision: 2 },
  reporting: { status: 'PENDING', blockReason: 'FX_RATE_MISSING', filingFrequency: 'MONTHLY', periodStart: null, periodEnd: null, filingDueDate: null,
    taxAmount: '15.00', reportingCurrency: 'SAR', reportingTaxAmount: null },
  basis: { packageCode: 'DEMO_SA_SAAS', policyRevision: 1, jurisdictionCode: 'SA', schemeCode: 'NON_RESIDENT', marketEnabled: true,
    roundingMethod: 'HALF_UP', roundingScale: 2 },
  notManaged: null,
};
const clone = () => JSON.parse(JSON.stringify(registered)) as typeof registered;
/** 内存存储；throwing 模拟隐私模式下访问即抛错。 */
function memoryStorage(throwing = false) {
  const values = new Map<string, string>();
  return {
    values,
    getItem: (key: string) => { if (throwing) throw new Error('blocked'); return values.get(key) ?? null; },
    setItem: (key: string, value: string) => { if (throwing) throw new Error('blocked'); values.set(key, value); },
  };
}

test('page registration: Tax Lookup sits under Finance at /finance/tax, read-only, with a nav title', () => {
  const page = pageCapabilities.find(item => item.componentKey === 'OPERATIONS_FINANCE_TAX');
  assert.ok(page);
  assert.equal(page.frontendRoute, '/finance/tax');
  assert.deepEqual(page.actions, []);
  assert.equal(testT('nav')('resource.OPERATIONS_FINANCE_TAX'), 'Tax Lookup');
  assert.match(source('../navigation/ConsolePage.tsx'), /OPERATIONS_FINANCE_TAX: lazy\(\(\) => import\('\.\.\/pages\/FinanceTax'\)/);
});

test('paths: order detail and receipt download are encoded under /tax/v1/orders', () => {
  assert.equal(taxPaths.order(orderNo), '/tax/v1/orders/ORD-20261029-000184');
  assert.equal(taxPaths.download('A.b_1', '913000031001'), '/tax/v1/orders/A.b_1/documents/913000031001/download');
});

test('order number: trimmed, same pattern as the tax contract, rejects separators and overlong input', () => {
  assert.equal(normalizeOrderNo('  ORD-20261029-000184 '), orderNo);
  for (const bad of ['', '-ORD', 'ORD 1', 'ORD/1', 'a'.repeat(65), null, undefined]) assert.equal(normalizeOrderNo(bad), null, String(bad));
});

test('page model decodes the registered order and fails closed on drift', () => {
  const page = decodeTaxOrderPage(clone(), orderNo);
  assert.equal(page.pageStatus, 'ISSUED');
  assert.equal(page.buyerClientName, null, 'names are optional and default to null');
  assert.equal(decodeTaxOrderPage({ ...clone(), buyerClientName: 'Nimbus Notes', entityName: 'Demo PayFac OE', taxSubjectName: null }, orderNo)
    .buyerClientName, 'Nimbus Notes');
  assert.equal(page.steps.length, 5);
  assert.throws(() => decodeTaxOrderPage(clone(), 'ORD-OTHER'), ApiError, 'order number must match the request');
  for (const mutate of [
    (v: ReturnType<typeof clone>) => { v.pageStatus = 'PAID'; },
    (v: ReturnType<typeof clone>) => { v.steps[0].state = 'SKIPPED'; },
    (v: ReturnType<typeof clone>) => { v.callout.kind = 'SOMETHING'; },
    (v: ReturnType<typeof clone>) => { v.amounts.netAmount = '1e2'; },
    (v: ReturnType<typeof clone>) => { (v as unknown as { buyerClientName: unknown }).buyerClientName = 42; },
    (v: ReturnType<typeof clone>) => { v.quotes = []; },
    (v: ReturnType<typeof clone>) => { v.lines[0].rate = '15%'; },
    (v: ReturnType<typeof clone>) => { (v.receipt as { downloadable: unknown }).downloadable = 'yes'; },
  ]) {
    const value = clone(); mutate(value);
    assert.throws(() => decodeTaxOrderPage(value, orderNo), (error: unknown) => error instanceof ApiError && error.status === 502);
  }
});

test('page model: optional sections may be absent and are normalized to null', () => {
  const value = clone() as Partial<typeof registered>;
  delete value.receipt; delete value.registration; delete value.reporting; delete value.basis; delete value.explanation;
  const page = decodeTaxOrderPage(value, orderNo);
  assert.equal(page.receipt, null); assert.equal(page.registration, null); assert.equal(page.basis, null); assert.equal(page.explanation, null);
});

test('download grant must match the document and use HTTPS', () => {
  const grant = { documentId: '913000031001', documentNumber: 'SA-RCPT-2026-000031', url: 'https://storage.local.test/r?sig=1', expiresAt: '2026-10-29T06:50:00Z' };
  assert.equal(decodeTaxDownload(grant, '913000031001').url, grant.url);
  assert.throws(() => decodeTaxDownload(grant, '913000031002'), ApiError);
  assert.throws(() => decodeTaxDownload({ ...grant, url: 'http://storage.local.test/r' }, '913000031001'), ApiError);
});

test('recent searches: newest first, de-duplicated, capped, and silently empty when storage is unavailable or corrupt', () => {
  const storage = memoryStorage();
  const page = decodeTaxOrderPage(clone(), orderNo) as TaxOrderPage;
  const entry = recentFrom(page);
  assert.deepEqual([entry.countryCode, entry.taxAmount, entry.pageStatus], ['SA', '15.00', 'ISSUED']);
  for (let index = 0; index < recentSearchLimit + 2; index += 1) rememberSearch({ ...entry, orderNo: `ORD-${index}` }, storage);
  const list = rememberSearch(entry, storage);
  assert.equal(list.length, recentSearchLimit);
  assert.equal(list[0].orderNo, orderNo);
  assert.equal(rememberSearch(entry, storage).filter(item => item.orderNo === orderNo).length, 1);
  storage.values.set(recentSearchKey, '{not json');
  assert.deepEqual(readRecentSearches(storage), []);
  storage.values.set(recentSearchKey, JSON.stringify([{ orderNo: 'bad order', pageStatus: 'ISSUED', currency: 'USD' }]));
  assert.deepEqual(readRecentSearches(storage), []);
  const blocked = memoryStorage(true);
  assert.deepEqual(readRecentSearches(blocked), []);
  assert.equal(rememberSearch(entry, blocked).length, 1, 'a failed write still returns the in-memory list');
  assert.deepEqual(readRecentSearches(undefined), []);
});

test('formatting helpers: rate, signed delta, short request ID and active step', () => {
  assert.equal(rateNumber('0.15'), 0.15);
  assert.equal(rateNumber(null), null);
  assert.equal(rateNumber('15%'), null);
  assert.equal(signedAmount('10.00'), '+10.00');
  assert.equal(signedAmount('-2.50'), '-2.50');
  assert.equal(signedAmount('0.00'), '0.00');
  assert.equal(shortRequestId('7c1e2f3a4b5c6d7e8f909a02'), '7c1e…9a02');
  assert.equal(shortRequestId('abc'), 'abc');
  const page = decodeTaxOrderPage(clone(), orderNo);
  assert.equal(activeStepIndex(page.steps), 4);
  assert.equal(activeStepIndex(page.steps.map(item => ({ ...item, state: 'DONE' as const }))), -1);
});

test('W1 change 2: line description, managedBy and not-managed reason decode and have English text', () => {
  const page = decodeTaxOrderPage(clone(), orderNo);
  assert.equal(page.managedBy, 'PLATFORM_OPERATIONS');
  assert.equal(page.lines[0].description, 'Nimbus Notes Pro · Annual');
  const withoutDescription = clone(); delete (withoutDescription.lines[0] as { description?: string }).description;
  assert.equal(decodeTaxOrderPage(withoutDescription, orderNo).lines[0].description, null);
  const tooLong = clone(); tooLong.lines[0].description = 'x'.repeat(256);
  assert.throws(() => decodeTaxOrderPage(tooLong, orderNo), ApiError);
  assert.equal(t('enum.ManagedBy.PLATFORM_OPERATIONS'), 'Operated by platform operations');
  assert.equal(t('enum.ManagedBy.SUBJECT_HOLDER'), 'Operated by the holder');
  assert.equal(t('lines.itemValue', { item: 'Nimbus Notes Pro · Annual', quantity: '1' }), 'Nimbus Notes Pro · Annual × 1');
  assert.match(t('callout.CLIENT_RESPONSIBLE.body', { reason: 'TAX_RESPONSIBLE_UNRESOLVED' }), /^The acquiring entity is supplied by the Client/);
  assert.match(t('callout.CLIENT_RESPONSIBLE.body', { reason: 'SUBJECT_NOT_REGISTERED' }), /^Under a PSP agreement/);
  for (const reason of ['SUBJECT_NOT_REGISTERED', 'TAX_RESPONSIBLE_UNRESOLVED']) assert.ok(t.has(`enum.TaxNotManagedReason.${reason}`), reason);
  assert.match(source('./TaxOrderView.tsx'), /item: line\.description \?\? line\.lineRef/, 'falls back to the item ID without a description');
});

test('change 3: free-text categories (spaces, non-ASCII, markup-like) decode and render as literal text', () => {
  const category = 'Game key <b>\u6e38\u620f</b> {x}';
  const value = clone(); value.lines[0].categoryCode = category;
  assert.equal(decodeTaxOrderPage(value, orderNo).lines[0].categoryCode, category);
  const tooLong = clone(); tooLong.lines[0].categoryCode = 'x'.repeat(65);
  assert.throws(() => decodeTaxOrderPage(tooLong, orderNo), ApiError);
  // ICU 只把参数当数据插入，不再解析大括号；React 以文本节点输出，标记字符不会成为 HTML。
  assert.equal(t('reason.UNKNOWN_TAX_CATEGORY.body', { lineNo: 1, category }),
    `Line 1 uses category ${category}, which isn't registered; the system won't fall back to a default category.`);
  for (const file of ['./TaxOrderView.tsx', '../pages/FinanceTax.tsx', './useTaxDownload.tsx']) {
    assert.doesNotMatch(source(file), /dangerouslySetInnerHTML|innerHTML/, file);
  }
});

test('block reasons map to who handles them', () => {
  assert.equal(reasonOwner('MARKET_NOT_ENABLED'), 'PLATFORM');
  assert.equal(reasonOwner('UNKNOWN_TAX_CATEGORY'), 'CLIENT');
  assert.equal(reasonOwner('MISSING_EVIDENCE'), 'CONSUMER');
  for (const code of ['NO_RULE', 'AMBIGUOUS_RULE', 'POLICY_INVALID', 'FUTURE_CODE']) assert.equal(reasonOwner(code), 'TAX_OPERATIONS');
  for (const code of ['MARKET_NOT_ENABLED', 'UNKNOWN_TAX_CATEGORY', 'MISSING_EVIDENCE', 'NO_RULE', 'AMBIGUOUS_RULE', 'POLICY_INVALID']) {
    assert.ok(t.has(`reason.${code}.title`) && t.has(`reason.${code}.body`), code);
  }
  assert.match(t('reason.UNKNOWN_TAX_CATEGORY.body', { lineNo: 2, category: 'GAME_KEY' }), /^Line 2 uses category GAME_KEY/);
});

test('step text: a superseded blocked quote reads "Estimate · Blocked · country" instead of a dash amount', () => {
  const page = decodeTaxOrderPage(clone(), orderNo);
  assert.equal(stepFactKey(page.steps[0], page), 'step.QUOTE.DONE');
  assert.equal(stepFactKey(page.steps[1], page), 'step.RECALCULATION.DONE');
  /** 夹具的 taxAmount 推断为 string；被阻断的报价没有税额，这里放宽为页面模型的原始形态。 */
  const loose = () => clone() as unknown as { steps: unknown[]; quotes: Record<string, unknown>[] };
  const value = loose();
  value.steps[0] = step('QUOTE', 'DONE', { countryCode: 'US' });
  value.quotes[1] = { ...value.quotes[1], status: 'BLOCKED', taxAmount: null, countryCode: 'US' };
  const supersededBlocked = decodeTaxOrderPage(value, orderNo);
  const quoteKey = stepFactKey(supersededBlocked.steps[0], supersededBlocked);
  assert.equal(quoteKey, 'step.QUOTE.DONE_BLOCKED');
  assert.equal(t(quoteKey, { country: 'US' }), 'Estimate · Blocked · US');
  assert.equal(stepFactKey(supersededBlocked.steps[1], supersededBlocked), 'step.RECALCULATION.DONE');
  const earlierFinalBlocked = loose();
  earlierFinalBlocked.quotes[0] = { ...earlierFinalBlocked.quotes[0], status: 'BLOCKED', taxAmount: null };
  earlierFinalBlocked.steps[1] = step('RECALCULATION', 'DONE', { countryCode: 'KW' });
  const decoded = decodeTaxOrderPage(earlierFinalBlocked, orderNo);
  assert.equal(stepFactKey(decoded.steps[1], decoded), 'step.RECALCULATION.DONE_BLOCKED');
  assert.equal(t('step.RECALCULATION.DONE_BLOCKED', { country: 'KW' }), 'Final · Blocked · KW');
  // 当前被阻断的步骤（状态 BLOCKED）沿用原文案；不由平台计税仍用 notManaged 前缀。
  const blockedNow = decodeTaxOrderPage({ ...clone(), steps: [step('QUOTE', 'BLOCKED', { countryCode: 'KW' }), ...clone().steps.slice(1)] }, orderNo);
  assert.equal(stepFactKey(blockedNow.steps[0], blockedNow), 'step.QUOTE.BLOCKED');
  assert.equal(stepFactKey(page.steps[0], { ...page, pageStatus: 'NOT_MANAGED' }), 'step.notManaged.QUOTE.DONE');
});

test('every step, callout and page status the BFF can return has English text', () => {
  for (const kind of ['CLIENT_RESPONSIBLE', 'PAYMENT_BLOCKED', 'REPORTING_FX_MISSING', 'DOCUMENT_PENDING', 'REGISTERED', 'ESTIMATE_ONLY', 'AWAITING_PAYMENT']) {
    assert.ok(t.has(`callout.${kind}.title`) && t.has(`callout.${kind}.body`), kind);
  }
  for (const status of ['NOT_MANAGED', 'BLOCKED', 'ESTIMATED', 'QUOTED', 'REGISTERED', 'ISSUED']) {
    assert.ok(t.has(`enum.TaxPageStatus.${status}`) && t.has(`recent.status.${status}`), status);
  }
  for (const key of ['QUOTE', 'RECALCULATION', 'PAYMENT', 'REGISTRATION', 'REPORTING']) assert.ok(t.has(`step.${key}.title`), key);
  for (const key of ['QUOTE', 'PAYMENT', 'REGISTRATION']) assert.ok(t.has(`step.notManaged.${key}.title`), key);
  assert.equal(t('step.PAYMENT.title', { state: 'DONE' }), 'Payment Succeeded');
  assert.equal(t('step.PAYMENT.title', { state: 'PENDING' }), 'Payment');
  assert.match(t('callout.REPORTING_FX_MISSING.body', { reportingCurrency: 'SAR', taxAmount: '15.00', currency: 'USD' }),
    /reports in SAR .* tax of 15\.00 USD/);
  assert.match(t('explanation.single', { jurisdiction: 'Saudi Arabia', taxType: 'VAT', rate: '15%', evidenceType: 'BILLING_COUNTRY',
    country: 'Saudi Arabia', scheme: 'non-resident', packageCode: 'DEMO_SA_SAAS', revision: 1 }),
  /^Taxed at Saudi Arabia VAT 15%: the consumer's billing address is in Saudi Arabia, .* DEMO_SA_SAAS r1 matched\.$/);
});

test('page wiring: one status pill, no page tabs, download only for downloadable receipts, recent searches guarded', () => {
  const page = source('../pages/FinanceTax.tsx');
  const view = source('./TaxOrderView.tsx');
  assert.doesNotMatch(page + view, /<Tabs\b/, 'the result page has no tabs');
  assert.equal((view.match(/<StatusPill status=\{page\.pageStatus\}/g) ?? []).length, 1, 'only the title carries a status badge');
  assert.match(view, /\{receipt\.downloadable && <Button type="primary"/);
  assert.match(page, /error\.status === 404 && error\.code === 'TAX_ORDER_NOT_FOUND'/);
  assert.match(page, /t\('notFound\.title', \{ orderNo: outcome\.orderNo \}\)/);
  assert.match(page, /setParams\(\{ orderNo: next \}\)/, 'the order number lives in the URL');
  const download = source('./useTaxDownload.tsx');
  assert.match(download, /'Content-Type': 'application\/json'/, 'the body-less POST still declares JSON');
  assert.match(download, /t\('download\.retry'\)/);
  assert.match(download, /copyRequestId/);
  assert.match(source('./tax.ts'), /try \{ return globalThis\.localStorage \?\? undefined; \} catch \{ return undefined; \}/);
});
