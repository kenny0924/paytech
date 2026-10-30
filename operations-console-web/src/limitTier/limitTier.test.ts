import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  approvalAccess, commentIssue, decisionPath, decodeHistory, decodeStatistics, decodeSummaryPage, decodeTierDetail, decodeWorkspace, emptyManualState, exhausted,
  goLiveReady, groupDecimal, limitTierPaths, limitTierRoutes, listFilters, listSearch, parseListQuery, percent, permissionsOf, quotaRows, requestBody, requestIssues,
  requestableTargets, restoreCeiling, rowAction,
} from './limitTier.ts';
import type { ConsoleResource } from '../api/types.ts';
import type { Permissions, RequestForm } from './limitTier.ts';

const clientId = '200000000000000042';
const summary = (overrides: Record<string, unknown> = {}) => ({
  clientId, clientDisplayName: 'Gulf Bytes FZ-LLC', clientNo: 'CL-0042', jurisdictionCode: 'AE', clientType: 'ORGANIZATION', crr: 'HIGH', tier: 'OBSERVATION',
  effectiveTime: '2026-10-02T10:15:00.000000Z', sourceType: 'REQUEST', sourceReason: 'Go-live review approved', observationStartedTime: '2026-10-02T10:15:00.000000Z',
  observationDaysElapsed: 30, observationTargetDays: 30, observationAllowedTransactions: 86, observationTargetTransactions: 100, observationDue: true,
  pendingChange: null, updatedAt: '2026-10-29T10:20:00.000000Z', ...overrides,
});
const detail = (overrides: Record<string, unknown> = {}) => ({
  ...summary(), version: 3, previousTier: null,
  observation: { startedTime: '2026-10-02T10:15:00.000000Z', daysElapsed: 30, targetDays: 30, allowedTransactions: 86, targetTransactions: 100, due: true },
  quotas: [{ currency: 'USD', single: '5000', day: '10000', last30Days: '100000', usedToday: '4200', usedLast30Days: '61000.50' },
    { currency: 'AED', single: '18000', day: '36000', last30Days: '360000', usedToday: '0', usedLast30Days: '9000' }],
  unconfiguredCurrencies: ['SAR', 'EGP'], unconfiguredCurrencyPolicy: 'DENY', ...overrides,
});
const change = (overrides: Record<string, unknown> = {}) => ({
  changeId: '500000000000000002', clientId, changeType: 'REQUEST', fromTier: 'VERIFICATION', toTier: 'OBSERVATION', status: 'APPROVED', approvalLevel: 'ELEVATED',
  crrSnapshot: 'HIGH', reason: 'Production verified', goLiveNote: '3 production payments succeeded', checklist: null, observationTargetDays: 30,
  observationTargetTransactions: 100, observationDaysAtRequest: null, observationAllowedAtRequest: null, requestedBy: '900000000000000101', requestedByLabel: 'Lin Ke',
  requestedTime: '2026-10-02T09:00:00.000000Z', decidedBy: '900000000000000102', decidedByLabel: 'Zhou Lan', decidedTime: '2026-10-02T10:15:00.000000Z',
  decisionComment: null, version: 2, ...overrides,
});
const init = change({ changeId: '500000000000000001', changeType: 'UNDERWRITING_INIT', fromTier: null, toTier: 'VERIFICATION', approvalLevel: null, crrSnapshot: null,
  reason: 'Underwriting approved', goLiveNote: null, observationTargetDays: null, observationTargetTransactions: null, requestedBy: null, requestedByLabel: null,
  requestedTime: '2026-09-28T16:40:00.000000Z', decidedBy: null, decidedByLabel: null, decidedTime: null, version: 1 });
const pending = change({ changeId: '500000000000000003', fromTier: 'OBSERVATION', toTier: 'STANDARD', status: 'PENDING', goLiveNote: null, reason: '30 days, no chargebacks',
  observationDaysAtRequest: 30, observationAllowedAtRequest: 86, requestedTime: '2026-10-29T10:20:00.000000Z', decidedBy: null, decidedByLabel: null, decidedTime: null, version: 1 });
const checks = (status: Record<string, string> = {}) => ['QUALIFICATION', 'UNDERWRITING', 'AGREEMENT', 'CAPABILITIES'].map(code => ({ code, status: status[code] ?? 'OK' }));
const workspace = (overrides: Record<string, unknown> = {}) => ({
  client: { clientId, clientNo: 'CL-0042', displayName: 'Gulf Bytes FZ-LLC', clientType: 'ORGANIZATION', jurisdictionCode: 'AE' },
  tier: detail(), history: { items: [change(), init] },
  underwriting: { underwritingId: '300000000000000002', versionNo: 2, crr: 'HIGH', observationDays: 30, observationTransactions: 100, suggestedSettlementDays: 14, decisionSource: 'MANUAL' },
  checklist: { items: checks(), facts: { qualificationStatus: 'APPROVED', qualificationValidTo: '2027-10-01T00:00:00Z', underwritingVersionNo: 2, crr: 'HIGH',
    agreementId: '700000000000000001', agreementStatus: 'ACTIVE', enabledPaymentCapabilities: ['CARD', 'APPLE_PAY'], pendingPaymentCapabilities: [] } },
  ...overrides,
});
const resource = (resourceCode: string): ConsoleResource => ({ resourceId: '1', resourceCode, resourceType: 'BUTTON', parentResourceId: null, displayName: resourceCode,
  frontendRoute: null, componentKey: null, sortOrder: 1 });
const perms = (overrides: Partial<Permissions> = {}): Permissions => ({ request: false, approve: false, approveElevated: false, ...overrides });

test('paths are the ops-bff forwards of technique 9.4 and the page routes', () => {
  assert.equal(limitTierPaths.list, '/risk-limit-tiers/v1/client-tiers');
  assert.equal(limitTierPaths.workspace('42'), '/risk-limit-tiers/v1/clients/42/workspace');
  assert.equal(limitTierPaths.request('42'), '/risk-limit-tiers/v1/clients/42/tier-changes');
  assert.equal(limitTierPaths.withdrawal('7'), '/risk-limit-tiers/v1/tier-changes/7/withdrawal');
  assert.equal(decisionPath('7', 'APPROVE', false), '/risk-limit-tiers/v1/tier-changes/7/approval');
  assert.equal(decisionPath('7', 'APPROVE', true), '/risk-limit-tiers/v1/tier-changes/7/elevated-approval');
  assert.equal(decisionPath('7', 'REJECT', false), '/risk-limit-tiers/v1/tier-changes/7/rejection');
  assert.equal(decisionPath('7', 'REJECT', true), '/risk-limit-tiers/v1/tier-changes/7/elevated-rejection');
  assert.equal(limitTierRoutes.detail('42'), '/risk/limit-tiers/42');
});

test('list page, statistics and summary decoding are strict about tiers, observation and pending requests', () => {
  const page = decodeSummaryPage({ items: [summary(), summary({ clientId: '200000000000000038', tier: 'VERIFICATION', observationStartedTime: null, observationDaysElapsed: null,
    observationTargetDays: null, observationAllowedTransactions: null, observationTargetTransactions: null, observationDue: false })], page: 1, size: 20, total: 58 });
  assert.equal(page.items[0].observationAllowedTransactions, 86);
  assert.equal(page.items[1].observationDue, false);
  const { observationDue: _due, ...withoutDue } = summary({ tier: 'STANDARD', observationStartedTime: null, observationDue: false });
  assert.equal(decodeSummaryPage({ items: [withoutDue], page: 1, size: 20, total: 1 }).items[0].observationDue, false, 'missing due defaults to false');
  assert.throws(() => decodeSummaryPage({ items: [summary({ tier: 'GOLD' })], page: 1, size: 20, total: 1 }), 'unknown tier');
  assert.throws(() => decodeSummaryPage({ items: [summary({ tier: 'STANDARD' })], page: 1, size: 20, total: 1 }), 'due outside Observation');
  assert.throws(() => decodeSummaryPage({ items: [summary({ pendingChange: { changeId: '5', fromTier: 'VERIFICATION', toTier: 'OBSERVATION', approvalLevel: 'STANDARD',
    requestedTime: '2026-10-29T10:20:00Z' } })], page: 1, size: 20, total: 1 }), 'pending request from another tier');
  assert.throws(() => decodeSummaryPage({ items: [summary({ effectiveTime: '2026-10-02 10:15' })], page: 1, size: 20, total: 1 }), 'non-ISO time');
  assert.deepEqual(decodeStatistics({ verification: 6, observation: 9, standard: 41, restricted: 1, pendingApproval: 2, observationDue: 3 }),
    { verification: 6, observation: 9, standard: 41, restricted: 1, pendingApproval: 2, observationDue: 3 });
  assert.throws(() => decodeStatistics({ verification: 6, observation: 9, standard: 41, restricted: 1, pendingApproval: 2 }), 'missing statistic');
});

test('tier detail decoding: no tier is null, quotas keep strings, unconfigured currencies are distinct from configured ones', () => {
  assert.equal(decodeTierDetail({ clientId, tier: null }, clientId), null);
  assert.equal(decodeTierDetail(null, clientId), null);
  const decoded = decodeTierDetail(detail(), clientId)!;
  assert.equal(decoded.quotas[0].usedLast30Days, '61000.50');
  assert.deepEqual(decoded.unconfiguredCurrencies, ['SAR', 'EGP']);
  assert.equal(decoded.observation?.due, true);
  assert.equal(decodeTierDetail(detail({ quotas: [{ currency: 'USD', single: null, day: '1', last30Days: null, usedToday: '0', usedLast30Days: '0' }] }), clientId)!.quotas[0].single, null);
  assert.throws(() => decodeTierDetail(detail(), '200000000000000043'), 'other client');
  assert.throws(() => decodeTierDetail(detail({ quotas: [{ currency: 'USD', single: 5000, day: null, last30Days: null, usedToday: '0', usedLast30Days: '0' }] }), clientId), 'number amount');
  assert.throws(() => decodeTierDetail(detail({ unconfiguredCurrencies: ['USD'] }), clientId), 'currency both configured and unconfigured');
  assert.throws(() => decodeTierDetail(detail({ unconfiguredCurrencyPolicy: 'MAYBE' }), clientId), 'unknown policy');
  assert.throws(() => decodeTierDetail(detail({ tier: 'STANDARD', observationStartedTime: null, observationDue: false }), clientId), 'observation outside Observation');
});

test('history decoding: newest first, one pending at most, rejection needs a comment, request needs an approval level', () => {
  const items = decodeHistory({ items: [pending, change(), init] }, clientId);
  assert.equal(items[0].status, 'PENDING');
  assert.equal(items[2].requestedBy, null);
  assert.throws(() => decodeHistory({ items: [init, change()] }, clientId), 'oldest first');
  assert.throws(() => decodeHistory({ items: [pending, { ...pending, changeId: '500000000000000004', requestedTime: '2026-10-29T10:00:00Z' }] }, clientId), 'two pending');
  assert.throws(() => decodeHistory({ items: [change({ status: 'REJECTED', decisionComment: null })] }, clientId), 'rejected without comment');
  assert.throws(() => decodeHistory({ items: [change({ approvalLevel: null })] }, clientId), 'request without level');
  assert.throws(() => decodeHistory({ items: [change({ clientId: '200000000000000043' })] }, clientId), 'other client');
  const frozen = decodeHistory({ items: [change({ checklist: { qualificationStatus: 'APPROVED', enabledPaymentCapabilities: 'CARD, APPLE_PAY,CARD', channelRegistrationConfirmed: true } })] }, clientId);
  assert.deepEqual(frozen[0].checklist?.enabledPaymentCapabilities, ['CARD', 'APPLE_PAY'], 'comma string accepted and de-duplicated');
});

test('workspace decoding requires the four system checks once each and a consistent pending request', () => {
  const decoded = decodeWorkspace(workspace(), clientId);
  assert.deepEqual(decoded.checks.map(check => check.code), ['QUALIFICATION', 'UNDERWRITING', 'AGREEMENT', 'CAPABILITIES']);
  assert.equal(decoded.facts.enabledPaymentCapabilities.length, 2);
  assert.equal(decoded.underwriting?.suggestedSettlementDays, 14);
  const reordered = decodeWorkspace(workspace({ checklist: { items: checks().reverse(), facts: null } }), clientId);
  assert.equal(reordered.checks[0].code, 'QUALIFICATION');
  assert.deepEqual(reordered.facts.enabledPaymentCapabilities, []);
  const noTier = decodeWorkspace(workspace({ tier: { clientId, tier: null }, history: { items: [] }, underwriting: null }), clientId);
  assert.equal(noTier.tier, null);
  assert.throws(() => decodeWorkspace(workspace({ checklist: { items: checks().slice(1), facts: null } }), clientId), 'missing system check');
  assert.throws(() => decodeWorkspace(workspace({ checklist: { items: [...checks().slice(1), checks()[1]], facts: null } }), clientId), 'duplicate system check');
  assert.throws(() => decodeWorkspace(workspace({ checklist: { items: checks({ AGREEMENT: 'PASSED' }), facts: null } }), clientId), 'unknown status');
  assert.throws(() => decodeWorkspace(workspace({ history: { items: [pending, change(), init] } }), clientId), 'history pending but detail has none');
  const pendingSummary = { changeId: pending.changeId, fromTier: 'OBSERVATION', toTier: 'STANDARD', approvalLevel: 'ELEVATED', requestedBy: pending.requestedBy, requestedTime: pending.requestedTime };
  assert.equal(decodeWorkspace(workspace({ tier: detail({ pendingChange: pendingSummary }), history: { items: [pending, change(), init] } }), clientId).history[0].status, 'PENDING');
  assert.throws(() => decodeWorkspace(workspace({ client: { clientId: '1' } }), clientId), 'other client');
});

test('requestable targets follow B3, and restore never goes above the tier before restriction', () => {
  assert.deepEqual(requestableTargets('VERIFICATION', null), ['OBSERVATION', 'RESTRICTED']);
  assert.deepEqual(requestableTargets('OBSERVATION', null), ['STANDARD', 'OBSERVATION', 'RESTRICTED']);
  assert.deepEqual(requestableTargets('STANDARD', null), ['RESTRICTED']);
  assert.deepEqual(requestableTargets('RESTRICTED', 'OBSERVATION'), ['VERIFICATION', 'OBSERVATION']);
  assert.deepEqual(requestableTargets('RESTRICTED', 'STANDARD'), ['VERIFICATION', 'OBSERVATION', 'STANDARD']);
  assert.deepEqual(requestableTargets('RESTRICTED', null), ['VERIFICATION'], 'unknown previous tier only restores to Verification');
  assert.equal(restoreCeiling('OBSERVATION'), 'OBSERVATION');
  assert.equal(restoreCeiling(null), 'VERIFICATION');
});

test('approval access: four eyes, senior approval for ELEVATED, standard approvers fall back to the elevated route only with that permission', () => {
  const actor = '900000000000000102';
  const elevated = { status: 'PENDING' as const, requestedBy: '900000000000000101', approvalLevel: 'ELEVATED' as const };
  const standard = { ...elevated, approvalLevel: 'STANDARD' as const };
  assert.deepEqual(approvalAccess(elevated, actor, perms({ approveElevated: true })), { kind: 'approve', elevated: true });
  assert.deepEqual(approvalAccess(elevated, actor, perms({ approve: true })), { kind: 'insufficient' });
  assert.deepEqual(approvalAccess(elevated, actor, perms()), { kind: 'none' });
  assert.deepEqual(approvalAccess(standard, actor, perms({ approve: true, approveElevated: true })), { kind: 'approve', elevated: false });
  assert.deepEqual(approvalAccess(standard, actor, perms({ approveElevated: true })), { kind: 'approve', elevated: true });
  assert.deepEqual(approvalAccess(elevated, '900000000000000101', perms({ approveElevated: true, request: true })), { kind: 'self', canWithdraw: true });
  assert.deepEqual(approvalAccess(elevated, '900000000000000101', perms({ approveElevated: true })), { kind: 'self', canWithdraw: false });
  assert.deepEqual(approvalAccess({ ...elevated, status: 'APPROVED' }, actor, perms({ approveElevated: true })), { kind: 'none' });
  assert.equal(commentIssue('REJECT', '  '), 'required');
  assert.equal(commentIssue('APPROVE', ''), null);
  assert.equal(commentIssue('APPROVE', 'x'.repeat(501)), 'tooLong');
  const buttons = permissionsOf([resource('OPERATIONS_RISK_LIMIT_TIER_REQUEST'), resource('OPERATIONS_RISK_LIMIT_TIER_APPROVE_ELEVATED')]);
  assert.deepEqual(buttons, { request: true, approve: false, approveElevated: true });
});

test('go-live checklist: submit only when all system checks are OK, every manual item ticked and the note is filled', () => {
  const all = { CHANNEL: true, INTEGRATION: true, PRODUCTION: true, OPEN_ISSUES: true };
  const ok = checks().map(check => ({ status: check.status as 'OK' }));
  assert.equal(goLiveReady(ok, all, 'Production verified'), true);
  assert.equal(goLiveReady(ok, { ...all, PRODUCTION: false }, 'Production verified'), false);
  assert.equal(goLiveReady(ok, all, '   '), false);
  assert.equal(goLiveReady(ok, all, 'x'.repeat(1001)), false);
  assert.equal(goLiveReady([...ok.slice(1), { status: 'UNAVAILABLE' }], all, 'note'), false);
  assert.equal(goLiveReady(ok.slice(1), all, 'note'), false);
  const form: RequestForm = { toTier: 'OBSERVATION', reason: '', goLiveNote: ' 3 production payments ', targetDays: '', targetTransactions: '', manual: all };
  assert.deepEqual(requestBody(form, 'VERIFICATION', 4), { expectedTierVersion: 4, toTier: 'OBSERVATION', reason: '3 production payments', goLiveNote: '3 production payments',
    checklist: { channelRegistrationConfirmed: true, integrationConfirmed: true, productionVerified: true, openIssuesCleared: true } });
  assert.deepEqual(requestIssues({ ...form, manual: emptyManualState }, 'VERIFICATION'), ['checklist']);
});

test('other requests: reason required, extension targets optional and validated, no checklist sent', () => {
  const base: RequestForm = { toTier: 'STANDARD', reason: ' Observation clean ', goLiveNote: '', targetDays: '', targetTransactions: '', manual: emptyManualState };
  assert.deepEqual(requestBody(base, 'OBSERVATION', 3), { expectedTierVersion: 3, toTier: 'STANDARD', reason: 'Observation clean' });
  assert.deepEqual(requestIssues({ ...base, reason: '' }, 'OBSERVATION'), ['reasonRequired']);
  assert.deepEqual(requestIssues({ ...base, reason: 'x'.repeat(1001) }, 'OBSERVATION'), ['reasonTooLong']);
  const extend = { ...base, toTier: 'OBSERVATION' as const, targetDays: '45', targetTransactions: '' };
  assert.deepEqual(requestBody(extend, 'OBSERVATION', 3), { expectedTierVersion: 3, toTier: 'OBSERVATION', reason: 'Observation clean', observationTargetDays: 45 });
  assert.deepEqual(requestIssues({ ...extend, targetDays: '366', targetTransactions: '0' }, 'OBSERVATION'), ['targetDays', 'targetTransactions']);
  assert.deepEqual(requestBody({ ...base, toTier: 'RESTRICTED' }, 'STANDARD', 9), { expectedTierVersion: 9, toTier: 'RESTRICTED', reason: 'Observation clean' });
});

test('quota rows, bars and amount display', () => {
  const rows = quotaRows(decodeTierDetail(detail(), clientId)!);
  assert.deepEqual(rows.map(row => row.kind === 'configured' ? row.quota.currency : `-${row.currency}`), ['USD', 'AED', '-SAR', '-EGP']);
  assert.equal(percent('4200', '10000'), 42);
  assert.equal(percent('12000', '10000'), 100, 'capped');
  assert.equal(percent('1', null), null);
  assert.equal(percent('1', '0'), null);
  assert.equal(percent(30, 30), 100);
  assert.equal(exhausted('10000', '10000'), true);
  assert.equal(exhausted('9999.99', '10000'), false);
  assert.equal(exhausted('5', null), false);
  assert.equal(groupDecimal('61000.50'), '61,000.5');
  assert.equal(groupDecimal('1000000'), '1,000,000');
});

test('list query round-trips through the address bar; row action follows approval access', () => {
  const query = parseListQuery(new URLSearchParams('tier=OBSERVATION&keyword=Gulf&observationDue=true&pendingApproval=true&page=2'));
  assert.deepEqual(query, { tab: 'OBSERVATION', keyword: 'Gulf', observationDue: true, pendingApproval: true });
  assert.deepEqual(listFilters(query), { tier: 'OBSERVATION', keyword: 'Gulf', observationDue: 'true', pendingApproval: 'true' });
  assert.equal(listSearch(query, { page: 2, size: 20 }), 'tier=OBSERVATION&keyword=Gulf&observationDue=true&pendingApproval=true&page=2&size=20');
  assert.deepEqual(parseListQuery(new URLSearchParams('tier=GOLD&observationDue=yes')), { tab: 'ALL', keyword: '', observationDue: false, pendingApproval: false });
  const actor = '900000000000000102';
  const pendingRow = { tier: 'OBSERVATION' as const, pendingChange: { changeId: '5', fromTier: 'OBSERVATION' as const, toTier: 'STANDARD' as const, approvalLevel: 'ELEVATED' as const,
    requestedBy: '900000000000000101', requestedByLabel: null, requestedTime: '2026-10-29T10:20:00Z' } };
  assert.equal(rowAction(pendingRow, actor, perms({ approveElevated: true })), 'handle');
  assert.equal(rowAction(pendingRow, actor, perms({ approve: true })), 'view');
  assert.equal(rowAction(pendingRow, '900000000000000101', perms({ approveElevated: true })), 'view');
  assert.equal(rowAction({ tier: 'VERIFICATION', pendingChange: null }, actor, perms({ request: true })), 'goLive');
  assert.equal(rowAction({ tier: 'VERIFICATION', pendingChange: null }, actor, perms()), 'view');
});
