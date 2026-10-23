import assert from 'node:assert/strict';
import test from 'node:test';
import type { PagingResponseBody } from '../api/types.ts';
import { createRiskDecisionApi } from './riskDecisionApi.ts';
import type { RiskDecisionApi } from './riskDecisionApi.ts';
import type { RuleSetDetail, RuleSetListItem } from './riskApiModels.ts';
import { ruleSet, ruleSetDetail } from './riskFixtures.ts';
import { decodeRuleSetDetail, decodeRuleSetPage } from './riskResponseValidation.ts';
import { loadObservedRuleSetHead, ObservedHeadChangedError, requireSelectedObservedHead, requireUnusedRuleSetCode } from './ruleSetHead.ts';

const pointCode = 'CHECKOUT_PREPARE';
const ruleSetCode = 'RULE_SET';
const safeMessage = 'The rule set version has changed. Refresh and try again.';
const page = (entries: RuleSetListItem[], total = entries.length, pageNo = 1): PagingResponseBody<RuleSetListItem> =>
  decodeRuleSetPage({ items: entries, page: pageNo, size: 10, total });

function headApi(pages: readonly PagingResponseBody<RuleSetListItem>[], detail: RuleSetDetail = ruleSetDetail,
  hooks: { listed?: (page: number) => void; detailed?: () => void } = {}) {
  const listCalls: Parameters<RiskDecisionApi['ruleSets']>[] = [];
  const detailCalls: Parameters<RiskDecisionApi['ruleSet']>[] = [];
  const api: RiskDecisionApi = {
    ...createRiskDecisionApi({ request: async () => { throw new Error('Unexpected API operation'); } }),
    ruleSets: async (...args) => {
      listCalls.push(args);
      const result = pages[listCalls.length - 1];
      assert.ok(result, 'No additional page may be requested after the complete stream or a drift');
      hooks.listed?.(args[0].page);
      return result;
    },
    ruleSet: async (...args) => { detailCalls.push(args); hooks.detailed?.(); return detail; },
  };
  return { api, listCalls, detailCalls };
}

function changed(error: unknown): boolean {
  assert.ok(error instanceof ObservedHeadChangedError);
  assert.equal(error.message, safeMessage);
  assert.equal('status' in error, false);
  assert.equal('cause' in error, false);
  return true;
}

for (const count of [0, 1]) {
  test(`reads ${count} current configuration with one exact query`, async () => {
    const source = headApi([page(count ? [ruleSet] : [])]);
    const signal = new AbortController().signal;
    assert.equal(await loadObservedRuleSetHead(source.api, pointCode, ruleSetCode, signal), count ? ruleSetDetail : null);
    assert.deepEqual(source.listCalls, [[{ page: 1, size: 10 }, { pointCode, ruleSetCode }, signal]]);
    assert.deepEqual(source.detailCalls, count ? [[ruleSet.ruleSetVersionId, signal]] : []);
  });
}

for (const broken of [
  { total: 2, items: [ruleSet, { ...ruleSet, ruleSetId: '99', ruleSetVersionId: '99' }] },
  { total: 1, items: [] }, { page: 2 }, { size: 200 }, { total: -1 },
  { items: [{ ...ruleSet, pointCode: 'OTHER_POINT' }] },
  { items: [{ ...ruleSet, ruleSetCode: 'OTHER_SET' }] },
  { items: [{ ...ruleSet, status: 'RETIRED' as const }] },
]) {
  test(`rejects ambiguous or misaligned current configuration ${JSON.stringify(broken)}`, async () => {
    const source = headApi([{ ...page([ruleSet]), ...broken }]);
    await assert.rejects(loadObservedRuleSetHead(source.api, pointCode, ruleSetCode, new AbortController().signal), changed);
    assert.equal(source.listCalls.length, 1);
    assert.equal(source.detailCalls.length, 0);
  });
}

const changedCoordinates: readonly Partial<RuleSetDetail>[] = [
  { ruleSetVersionId: '999' }, { versionNo: 9 }, { pointVersionId: '999' },
  { pointCode: 'PRIVATE_OTHER_POINT' }, { ruleSetCode: 'PRIVATE_OTHER_STREAM' }, { contentHash: 'b'.repeat(64) },
];
for (const coordinate of changedCoordinates) {
  test(`rejects head-detail drift in ${Object.keys(coordinate)[0]}`, async () => {
    const source = headApi([page([ruleSet])], decodeRuleSetDetail({ ...ruleSetDetail, ...coordinate }));
    await assert.rejects(loadObservedRuleSetHead(source.api, pointCode, ruleSetCode, new AbortController().signal), changed);
  });
  test(`rejects selected-detail drift in ${Object.keys(coordinate)[0]} without substituting a new base`, () => {
    const selected = decodeRuleSetDetail({ ...ruleSetDetail, ...coordinate });
    const before = structuredClone(selected);
    assert.throws(() => requireSelectedObservedHead(selected, ruleSetDetail), changed);
    assert.deepEqual(selected, before);
  });
}

test('returns the observed detail only for identical selected stream coordinates', () => {
  const observed = decodeRuleSetDetail(ruleSetDetail);
  assert.equal(requireSelectedObservedHead(structuredClone(observed), observed), observed);
  assert.throws(() => requireSelectedObservedHead(observed, null), changed);
  assert.doesNotThrow(() => requireUnusedRuleSetCode(null));
  assert.throws(() => requireUnusedRuleSetCode(observed), changed);
});

test('a new rule without legacy composition remains a usable observed edit base', async () => {
  const selected = decodeRuleSetDetail({ ...ruleSetDetail, pointVersionId: null });
  const source = headApi([page([{ ...ruleSet, pointVersionId: null }])], selected);
  const observed = await loadObservedRuleSetHead(source.api, pointCode, ruleSetCode, new AbortController().signal);
  assert.equal(observed, selected);
  assert.equal(requireSelectedObservedHead(selected, observed), observed);
});

for (const when of ['before request', 'before detail', 'after detail'] as const) {
  test(`aborts ${when} without returning a usable head`, async () => {
    const controller = new AbortController();
    if (when === 'before request') controller.abort();
    const source = headApi([page([ruleSet])], ruleSetDetail, {
      listed: () => { if (when === 'before detail') controller.abort(); },
      detailed: () => { if (when === 'after detail') controller.abort(); },
    });
    await assert.rejects(loadObservedRuleSetHead(source.api, pointCode, ruleSetCode, controller.signal), { name: 'AbortError' });
    assert.equal(source.listCalls.length, when === 'before request' ? 0 : 1);
    assert.equal(source.detailCalls.length, when === 'after detail' ? 1 : 0);
  });
}

test('propagates query failures without converting them into an unused-code decision', async () => {
  const source = headApi([page([])]);
  const unavailable = new Error('Safe transport failure');
  source.api.ruleSets = async () => { throw unavailable; };
  await assert.rejects(loadObservedRuleSetHead(source.api, pointCode, ruleSetCode, new AbortController().signal), error => error === unavailable);
});
