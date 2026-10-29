import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  canRevise, checkLines, decisionBody, decisionFieldOf, decisionFormOf, decisionIssues, decodeHistory, decodePreview, decodeStatistics, decodeSummaryPage,
  decodeUnderwriting, decodeWorkspace, defaultDecisionForm, groupDecimal, listFilters, newPageMode, parseListQuery, paymentMethodCodes, reviewAccess,
  reviewCommentIssue, riskDayParts, riskDaysText, settlementDaysOf, submitActions, underwritingPaths,
} from './underwriting.ts';
import type { Workspace } from './underwriting.ts';

const input = {
  clientDisplayName: 'Gulf Bytes FZ-LLC', clientNo: 'CL-0042', clientType: 'ORGANIZATION', jurisdictionCode: 'AE', crr: 'HIGH', crrMissing: false, ddTier: 'ENHANCED',
  mccCode: '5967', industryCode: 'ONLINE_SUBSCRIPTION', networkHighRiskMcc: true, expectedMonthlyVolume: '120000.00000000', volumeCurrency: 'USD',
  expectedTicket: '49', fulfillmentType: 'PRE_ORDER', fulfillmentDays: 45, websiteUrl: 'https://gulfbytes.ae', refundPolicyUrl: 'https://gulfbytes.ae/refund-policy',
  expectedPaymentMethods: 'CARD,APPLE_PAY,STC_PAY', sourceCaseId: '241533316505808896', sourceCaseVersion: '7', sourceApplicationId: '241533316505808001',
  sourceDraftRevision: '12', sourceClassificationConfirmedAt: '2026-10-28T08:02:00.000000Z',
};
const decision = {
  observationDays: 60, observationTransactions: 300, requireDeposit: true, depositAmount: '20000', depositCurrency: 'USD', depositBlocksCollection: true,
  suggestedSettlementDays: 7, highRiskMcc: true, highRiskMccReason: null, limitNote: 'Long pre-order', underwritingNote: null, handoverNote: null,
};
const exposure = { amount: '208000.00', currency: 'USD', riskDays: 52, settlementDaysAssumed: false, formula: '120000 × (45 + 7) / 30' };
const record = (overrides: Record<string, unknown> = {}) => ({
  underwritingId: '300000000000000001', clientId: '200000000000000042', versionNo: 1, status: 'SUBMITTED', decisionSource: 'MANUAL', ruleVersionNo: null,
  reviewLevel: 'HIGH', input, decision, estimatedExposure: exposure, exposureUnavailableReason: null, declineReason: null, reviewComment: null, revisionReason: null,
  supersedesUnderwritingId: null, createdBy: '900000000000000101', createdByLabel: 'Lin Ke', submittedBy: '900000000000000101', submittedByLabel: 'Lin Ke',
  submittedAt: '2026-10-29T06:20:00.000000Z', decidedBy: null, decidedByLabel: null, decidedAt: null, supersededAt: null, version: 2, ...overrides,
});
const checks = (failed: string[] = []) => ['RULE_CONFIGURED', 'CRR_LOW', 'VOLUME_PROVIDED', 'CURRENCY_CONFIGURED', 'VOLUME_WITHIN_THRESHOLD',
  'MCC_NOT_NETWORK_HIGH_RISK', 'MCC_NOT_EXCLUDED', 'CLASSIFICATION_CONFIRMED'].map(code => ({ code, passed: !failed.includes(code) }));
const preview = (failed: string[] = []) => ({ ruleVersionNo: 1, autoEligible: failed.length === 0, checks: checks(failed), estimatedExposure: exposure,
  exposureUnavailableReason: null, reviewLevel: 'LOW' });
const workspace = (overrides: Record<string, unknown> = {}) => ({
  client: { clientId: '200000000000000042', clientNo: 'CL-0042', displayName: 'Gulf Bytes FZ-LLC', clientType: 'ORGANIZATION', jurisdictionCode: 'AE' },
  inputStatus: 'READY', input, preview: preview(), current: { effective: null, open: null, latest: null }, canSubmit: true, ...overrides,
});

test('paths are the ops-bff same-path forwards of technique 9.4', () => {
  assert.equal(underwritingPaths.workspace('42'), '/risk-underwriting/v1/clients/42/workspace');
  assert.equal(underwritingPaths.submission('7'), '/risk-underwriting/v1/underwritings/7/submission');
  assert.equal(underwritingPaths.auto('42'), '/risk-underwriting/v1/clients/42/auto-underwritings');
  assert.equal(underwritingPaths.statistics, '/risk-underwriting/v1/underwriting-statistics');
});

test('detail decoding keeps frozen inputs, labels and exposure; tolerates missing label fields; rejects broken shapes', () => {
  const decoded = decodeUnderwriting(record(), '300000000000000001');
  assert.equal(decoded.input?.crr, 'HIGH');
  assert.equal(decoded.input?.fulfillmentDays, 45);
  assert.equal(decoded.submittedByLabel, 'Lin Ke');
  assert.deepEqual(decoded.estimatedExposure, { amount: '208000.00', currency: 'USD', riskDays: 52, settlementDaysAssumed: false });
  const { createdByLabel: _c, submittedByLabel: _s, decidedByLabel: _d, ...unlabelled } = record();
  assert.equal(decodeUnderwriting(unlabelled).submittedByLabel, null);
  assert.equal(decodeUnderwriting(record({ input: { ...input, crr: null, crrMissing: true } })).input?.crrMissing, true);
  assert.throws(() => decodeUnderwriting(record(), '300000000000000002'), 'other id');
  assert.throws(() => decodeUnderwriting(record({ status: 'DECLINED' })), 'declined without reason');
  assert.throws(() => decodeUnderwriting(record({ decisionSource: 'AUTO_RULE', ruleVersionNo: null })), 'auto without rule version');
  assert.throws(() => decodeUnderwriting(record({ estimatedExposure: { ...exposure, amount: 208000 } })), 'number amount');
  assert.throws(() => decodeUnderwriting(record({ decision: { ...decision, suggestedSettlementDays: 91 } })), 'T+N out of range');
  assert.throws(() => decodeUnderwriting(record({ input: { ...input, mccCode: '596' } })), 'MCC format');
});

test('list, history, statistics and preview decoders', () => {
  const summary = { underwritingId: '300000000000000001', clientId: '200000000000000042', clientDisplayName: 'Gulf Bytes FZ-LLC', clientNo: 'CL-0042',
    jurisdictionCode: 'AE', clientType: 'ORGANIZATION', crr: null, mccCode: '5967', networkHighRiskMcc: true, versionNo: 1, status: 'DRAFT', reviewLevel: 'MEDIUM',
    decisionSource: null, ruleVersionNo: null, estimatedExposure: null, exposureUnavailableReason: 'FULFILLMENT_TYPE_MISSING', fulfillmentType: null,
    suggestedSettlementDays: null, requireDeposit: false, submittedBy: null, submittedByLabel: null, decidedBy: null, decidedByLabel: null, updatedAt: '2026-10-28T02:15:00.000000Z' };
  const page = decodeSummaryPage({ items: [summary], page: 1, size: 20, total: 36 });
  assert.equal(page.items[0].exposureUnavailableReason, 'FULFILLMENT_TYPE_MISSING');
  assert.throws(() => decodeSummaryPage({ items: [{ ...summary, status: 'ACTIVE' }], page: 1, size: 20, total: 1 }));

  const v2 = record({ underwritingId: '300000000000000002', versionNo: 2, status: 'APPROVED', revisionReason: 'T+2 to T+3', decidedBy: '900000000000000102' });
  const v1 = record({ status: 'SUPERSEDED', supersededAt: '2026-10-28T10:42:00.000000Z' });
  assert.deepEqual(decodeHistory({ items: [v2, v1] }, '200000000000000042').map(item => item.versionNo), [2, 1]);
  assert.throws(() => decodeHistory({ items: [v1, v2] }, '200000000000000042'), 'not descending');
  assert.throws(() => decodeHistory({ items: [v2] }, '200000000000000099'), 'other client');

  assert.deepEqual(decodeStatistics({ effective: 31, draft: 3, submitted: 2, approvedThisMonth: 18, declinedThisMonth: 1 }).submitted, 2);
  assert.throws(() => decodeStatistics({ effective: 31, draft: 3, submitted: -1, approvedThisMonth: 18, declinedThisMonth: 1 }));

  assert.equal(decodePreview(preview()).autoEligible, true);
  assert.throws(() => decodePreview({ ...preview(['CRR_LOW']), autoEligible: true }), 'eligible must match the checks');
  assert.throws(() => decodePreview({ ...preview(), checks: [] }), 'no checks cannot be eligible');
  assert.deepEqual([decodePreview(preview()).thresholdAmount, decodePreview(preview()).thresholdCurrency], [null, null], 'threshold absent → null');
  const withThreshold = decodePreview({ ...preview(), thresholdAmount: '50000.00000000', thresholdCurrency: 'USD' });
  assert.deepEqual([withThreshold.thresholdAmount, withThreshold.thresholdCurrency], ['50000.00000000', 'USD']);
  assert.equal(decodePreview({ ...preview(['CURRENCY_CONFIGURED', 'VOLUME_WITHIN_THRESHOLD']), thresholdAmount: null, thresholdCurrency: null }).thresholdAmount, null);
  assert.throws(() => decodePreview({ ...preview(), thresholdAmount: '50000', thresholdCurrency: null }), 'amount without currency');
  assert.throws(() => decodePreview({ ...preview(), thresholdAmount: 50000, thresholdCurrency: 'USD' }), 'number amount');
});

test('workspace decoding checks the client, slot statuses and canSubmit consistency', () => {
  const decoded = decodeWorkspace(workspace(), '200000000000000042');
  assert.equal(decoded.preview?.ruleVersionNo, 1);
  assert.equal(decodeWorkspace(workspace({ preview: null }), '200000000000000042').preview, null, 'no MANAGE: no preview');
  assert.throws(() => decodeWorkspace(workspace(), '200000000000000043'));
  assert.throws(() => decodeWorkspace(workspace({ inputStatus: 'DD_NOT_APPROVED' }), '200000000000000042'), 'canSubmit needs READY');
  assert.throws(() => decodeWorkspace(workspace({ current: { effective: record(), open: null, latest: null } }), '200000000000000042'), 'effective must be APPROVED');
});

test('list filters: tab is the status, awaiting-my-review lists SUBMITTED via ops-bff, blank filters are not sent', () => {
  assert.deepEqual(listFilters({ tab: 'SUPERSEDED', keyword: '  Gulf ', crr: 'HIGH', decisionSource: '', pendingReviewForMe: false }),
    { status: 'SUPERSEDED', keyword: 'Gulf', crr: 'HIGH' });
  assert.deepEqual(listFilters({ tab: 'DRAFT', keyword: '', crr: '', decisionSource: 'AUTO_RULE', pendingReviewForMe: true }),
    { pendingReviewForMe: 'true', decisionSource: 'AUTO_RULE' });
  assert.deepEqual(parseListQuery(new URLSearchParams('status=BOGUS&crr=NONE&decisionSource=MANUAL')),
    { tab: 'ALL', keyword: '', crr: '', decisionSource: 'MANUAL', pendingReviewForMe: false });
});

test('money keeps decimal strings exact and grouped', () => {
  assert.equal(groupDecimal('208000.00', 2), '208,000.00');
  assert.equal(groupDecimal('120000.00000000'), '120,000');
  assert.equal(groupDecimal('49.50000000'), '49.5');
  assert.equal(groupDecimal('1234567.5', 2), '1,234,567.50');
});

test('exposure risk days follow B10 per fulfillment type; the formula only expands when the parts add up', () => {
  assert.deepEqual(riskDayParts('PRE_ORDER', 45, 7), [45, 7]);
  assert.deepEqual(riskDayParts('SUBSCRIPTION', null, null), [30, 0]);
  assert.deepEqual(riskDayParts('INSTANT', null, 2), [2]);
  assert.deepEqual(riskDayParts('SERVICE', null, 3), [3]);
  assert.equal(riskDayParts('PRE_ORDER', null, 7), null);
  assert.equal(riskDayParts(null, null, 7), null);
  assert.equal(riskDaysText([45, 7], 52), '(45 + 7)');
  assert.equal(riskDaysText([2], 2), '2');
  assert.equal(riskDaysText([45, 7], 60), '60', 'server value wins');
});

test('auto check lines: three grouped conditions, prerequisites only when failing, first failing code as the reason', () => {
  assert.deepEqual(checkLines(checks()), [
    { key: 'crr', passed: true, failedCode: null }, { key: 'volume', passed: true, failedCode: null }, { key: 'mcc', passed: true, failedCode: null }]);
  assert.deepEqual(checkLines(checks(['CRR_LOW', 'CURRENCY_CONFIGURED', 'VOLUME_WITHIN_THRESHOLD', 'MCC_NOT_EXCLUDED', 'CLASSIFICATION_CONFIRMED'])), [
    { key: 'CLASSIFICATION_CONFIRMED', passed: false, failedCode: 'CLASSIFICATION_CONFIRMED' },
    { key: 'crr', passed: false, failedCode: 'CRR_LOW' }, { key: 'volume', passed: false, failedCode: 'CURRENCY_CONFIGURED' },
    { key: 'mcc', passed: false, failedCode: 'MCC_NOT_EXCLUDED' }]);
  assert.deepEqual(checkLines([{ code: 'RULE_CONFIGURED', passed: false }]), [{ key: 'RULE_CONFIGURED', passed: false, failedCode: 'RULE_CONFIGURED' }]);
  assert.deepEqual(checkLines([...checks(), { code: 'NEW_CHECK', passed: false }]).at(-1), { key: 'NEW_CHECK', passed: false, failedCode: 'NEW_CHECK' });
});

test('decision form: B5 defaults, validation per mode and normalized body', () => {
  const form = defaultDecisionForm(true, 'USD');
  assert.equal(form.observationDays, '30'); assert.equal(form.observationTransactions, '100'); assert.equal(form.highRiskMcc, true);
  assert.deepEqual(decisionIssues(form, true, 'submit'), {});
  assert.deepEqual(decisionIssues({ ...form, observationDays: '' }, true, 'save'), {}, 'drafts may leave fields empty');
  assert.deepEqual(decisionIssues({ ...form, observationDays: '' }, true, 'submit'), { observationDays: 'required' });
  assert.deepEqual(decisionIssues({ ...form, observationDays: '366', observationTransactions: '0', suggestedSettlementDays: '91' }, true, 'save'),
    { observationDays: 'observationDaysRange', observationTransactions: 'observationTransactionsRange', suggestedSettlementDays: 'settlementDaysRange' });
  assert.deepEqual(decisionIssues({ ...form, requireDeposit: true, depositAmount: '0', depositCurrency: 'usd' }, true, 'submit'),
    { depositAmount: 'depositAmount', depositCurrency: 'depositCurrency' });
  assert.deepEqual(decisionIssues({ ...form, requireDeposit: true, depositAmount: '' }, true, 'submit'), { depositAmount: 'required' });
  assert.deepEqual(decisionIssues({ ...form, highRiskMcc: false }, true, 'save'), { highRiskMccReason: 'reasonRequired' }, 'changing the MCC default needs a reason');
  assert.deepEqual(decisionIssues({ ...form, handoverNote: 'x'.repeat(501) }, true, 'save'), { handoverNote: 'tooLong' });

  const body = decisionBody({ ...form, depositAmount: '500', depositBlocksCollection: true, suggestedSettlementDays: ' 7 ', highRiskMccReason: 'stale', limitNote: '  ' }, true);
  assert.deepEqual(body, { observationDays: 30, observationTransactions: 100, requireDeposit: false, depositAmount: null, depositCurrency: null,
    depositBlocksCollection: false, suggestedSettlementDays: 7, highRiskMcc: true, highRiskMccReason: null, limitNote: null, underwritingNote: null, handoverNote: null });
  assert.equal(decisionBody({ ...form, requireDeposit: true, depositAmount: '20000', depositBlocksCollection: true }, true).depositBlocksCollection, true);
  assert.equal(decisionBody({ ...form, highRiskMcc: false, highRiskMccReason: ' Card-not-present only ' }, true).highRiskMccReason, 'Card-not-present only');

  const draft = decisionFormOf({ ...decision, observationDays: null }, true, 'USD');
  assert.equal(draft.observationDays, '30', 'empty draft value falls back to the default');
  assert.equal(draft.suggestedSettlementDays, '7');
  assert.equal(settlementDaysOf('7'), 7); assert.equal(settlementDaysOf(''), null); assert.equal(settlementDaysOf('T+7'), null);
});

test('four-eyes: submitter sees disabled review, others with APPROVE review, others see nothing', () => {
  assert.equal(reviewAccess({ status: 'SUBMITTED', submittedBy: '1' }, '1', true), 'self');
  assert.equal(reviewAccess({ status: 'SUBMITTED', submittedBy: '1' }, '2', true), 'review');
  assert.equal(reviewAccess({ status: 'SUBMITTED', submittedBy: '1' }, '2', false), 'none');
  assert.equal(reviewAccess({ status: 'APPROVED', submittedBy: '1' }, '2', true), 'none');
  assert.equal(reviewCommentIssue('DECLINE', ' '), 'required');
  assert.equal(reviewCommentIssue('APPROVE', ''), null);
  assert.equal(reviewCommentIssue('APPROVE', 'x'.repeat(501)), 'tooLong');
});

test('revision and new-page routing', () => {
  assert.equal(canRevise({ status: 'APPROVED' }, [{ status: 'APPROVED' }, { status: 'SUPERSEDED' }], true), true);
  assert.equal(canRevise({ status: 'APPROVED' }, [{ status: 'DRAFT' }, { status: 'APPROVED' }], true), false, 'one open version at a time');
  assert.equal(canRevise({ status: 'SUPERSEDED' }, [], true), false);
  assert.equal(canRevise({ status: 'APPROVED' }, [], false), false);

  const ws = (overrides: Record<string, unknown>) => decodeWorkspace(workspace(overrides), '200000000000000042') as Workspace;
  assert.deepEqual(newPageMode(ws({}), true, false), { kind: 'auto' });
  assert.deepEqual(newPageMode(ws({}), true, true), { kind: 'manual' });
  assert.deepEqual(newPageMode(ws({ preview: preview(['CRR_LOW']) }), true, false), { kind: 'manual' });
  assert.deepEqual(newPageMode(ws({}), false, false), { kind: 'readonly' });
  assert.deepEqual(newPageMode(ws({ current: { effective: null, open: record({ status: 'DRAFT' }), latest: null } }), true, false),
    { kind: 'redirect', underwritingId: '300000000000000001' });
  assert.deepEqual(newPageMode(ws({ current: { effective: record({ status: 'APPROVED', versionNo: 2 }), open: null, latest: null } }), true, false),
    { kind: 'effective', underwritingId: '300000000000000001', versionNo: 2 });
  assert.deepEqual(newPageMode(ws({ inputStatus: 'DD_NOT_APPROVED', canSubmit: false }), true, false), { kind: 'manual' }, 'not ready: manual draft only');
});

test('HIGH submits for review without decline; LOW / MEDIUM submit decides and may decline', () => {
  assert.deepEqual(submitActions('HIGH'), { submitKey: 'submitForReview', canDecline: false });
  assert.deepEqual(submitActions('MEDIUM'), { submitKey: 'submit', canDecline: true });
});

test('422 field paths map to decision fields; payment methods are de-duplicated codes', () => {
  assert.equal(decisionFieldOf('decision.depositAmount'), 'depositAmount');
  assert.equal(decisionFieldOf('input.mccCode'), null);
  assert.equal(decisionFieldOf('decision.unknown'), null);
  assert.deepEqual(paymentMethodCodes('CARD, APPLE_PAY,CARD,'), ['CARD', 'APPLE_PAY']);
  assert.deepEqual(paymentMethodCodes(null), []);
});

test('dynamic text keys exist for every status, source, check, exposure reason, fulfillment type, crumb, error and list action', async () => {
  const { builtin } = await import('../i18n/builtin.ts');
  const { checkCodes, exposureUnavailableReasons, fulfillmentTypes, inputStatuses, knownErrors, statuses } = await import('./underwriting.ts');
  const risk = builtin.risk as Record<string, unknown>;
  const keys = [
    ...statuses.flatMap(status => [`status.${status}`, `crumb.${status}`]), 'status.effective', 'status.new',
    'source.MANUAL', 'source.AUTO_RULE', ...checkCodes.flatMap(code => [`auto.check.${code}`, `auto.reason.${code}`]), 'auto.group.crr', 'auto.group.volume', 'auto.group.volumeThreshold', 'auto.group.mcc',
    ...exposureUnavailableReasons.flatMap(reason => [`exposure.unavailableReason.${reason}`, `exposure.short.${reason}`]),
    ...fulfillmentTypes.map(type => `exposure.riskDays.${type}`), ...inputStatuses.map(status => `inputStatus.${status}`),
    ...knownErrors.map(code => `error.${code}`), 'action.view', 'action.review', 'action.continue', 'submit', 'submitForReview',
    ...['effective', 'draft', 'submitted', 'approvedThisMonth', 'declinedThisMonth'].map(key => `stats.${key}`),
    ...Object.keys(defaultDecisionForm(false, null)).map(field => `field.${field}`),
    ...['limitNote', 'underwritingNote', 'handoverNote'].map(field => `decision.${field}`),
  ].map(key => 'risk.underwriting.' + key);
  assert.deepEqual(keys.filter(key => !Object.hasOwn(risk, key)), []);
});

test('menu registers the list, new and version routes for the underwriting page', async () => {
  const { pageRoutePaths } = await import('../navigation/menu.ts');
  assert.deepEqual(pageRoutePaths({ resourceId: '1', resourceCode: 'OPERATIONS_RISK_UNDERWRITING', resourceType: 'MENU', parentResourceId: null, displayName: 'Underwriting',
    frontendRoute: '/risk/underwriting', componentKey: 'OPERATIONS_RISK_UNDERWRITING', sortOrder: 90 }),
  ['/risk/underwriting', '/risk/underwriting/new', '/risk/underwriting/:underwritingId']);
});

test('rq03-backend ops-bff shapes: workspace with sources / canManage and input without crrMissing; detail with createdAt / updatedAt and formula', () => {
  const { crrMissing: _m, ...bareInput } = input;
  const ws = decodeWorkspace(workspace({ input: { ...bareInput, crr: null }, sources: { dueDiligenceCase: 'AVAILABLE', application: 'AVAILABLE', classification: 'AVAILABLE', mcc: 'NOT_APPLICABLE' },
    canManage: true, current: { effective: null, open: record({ status: 'DRAFT', createdAt: '2026-10-29T06:00:00.000000Z', updatedAt: '2026-10-29T06:10:00.000000Z' }), latest: null } }),
  '200000000000000042');
  assert.equal(ws.input?.crrMissing, true);
  assert.equal(ws.current.open?.status, 'DRAFT');
  assert.equal(decodeUnderwriting(record({ estimatedExposure: { ...exposure, formula: '30000.00 × 15 ÷ 30' }, createdAt: '2026-10-29T06:00:00.000000Z' })).estimatedExposure?.riskDays, 52);
});
