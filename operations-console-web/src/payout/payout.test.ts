import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { builtin } from '../i18n/builtin.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import {
  availableCommands, balanceSegments, checkerMayApprove, closeReasons, compareNames, decodeAccount, decodeDetail, decodeOwnerAccounts,
  decodeReveal, decodeReviewPage, displayState, earliestCoolingEnd, fieldIssues, fourEyesBlock, latestAction, payoutActions,
  payoutRoutes, positive, readOwnerQuery, rejectReasons, reviewQuery, reviewStageOf, submitFields, suspendReasons,
  countStagesToFetch, loadStageCounts,
} from './payout.ts';
import type { BeneficiaryAccount, Requirement } from './payout.ts';

const repo = (path: string) => readFileSync(new URL(`../../../${path}`, import.meta.url), 'utf8');
const javaEnum = (path: string) => [...repo(path).matchAll(/^\s{4}([A-Z][A-Z_]+)[,;]/gm)].map(match => match[1]);
const enums = 'paytech/payout/paytech-payout-api/src/main/java/com/paytech/payout/api/enums/';

const account = (patch: Partial<BeneficiaryAccount> = {}): BeneficiaryAccount => ({
  beneficiaryAccountId: '900100', ownerType: 'CLIENT', ownerId: '80014', accountCode: 'BA-0002', railType: 'LOCAL', bankCountry: 'AE',
  currency: 'USD', bankName: 'Emirates NBD', bankCode: null, holderNameMasked: 'A**** T****** LLC', accountLast4: '4321',
  contentHash: 'a'.repeat(64), status: 'PENDING_REVIEW', reasonCode: null, submittedByType: 'CLIENT_MEMBER', submittedBy: '70001',
  reviewMakerId: null, reviewMakerDecision: null, checkerId: null, checkerDecision: null, decisionTime: null, coolingUntil: null,
  usable: false, defaultAccount: false, sharedOwners: null, reasonNote: null, reviewMakerNote: null, version: '1', createTime: '2026-10-30T06:05:00Z',
  updateTime: '2026-10-30T06:05:00Z', events: null, ...patch,
});

test('controlled catalogs match the payout api enums', () => {
  assert.deepEqual([...rejectReasons], javaEnum(enums + 'BeneficiaryRejectReason.java'));
  assert.deepEqual([...closeReasons], javaEnum(enums + 'BeneficiaryCloseReason.java'));
  assert.deepEqual([...suspendReasons], javaEnum(enums + 'BeneficiarySuspendReason.java').filter(code => code !== 'PAYOUT_FAILURE'));
});

test('page capabilities register both pages with their Platform button codes', () => {
  const pages = Object.fromEntries(pageCapabilities.map(page => [page.componentKey, page]));
  assert.equal(pages.OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_REVIEWS.frontendRoute, payoutRoutes.reviews);
  assert.equal(pages.OPERATIONS_FINANCE_PAYOUT_OWNERS.frontendRoute, payoutRoutes.owners);
  assert.deepEqual(pages.OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_REVIEWS.actions.map(action => action.resourceCode),
    [payoutActions.review, payoutActions.reveal]);
  assert.deepEqual(pages.OPERATIONS_FINANCE_PAYOUT_OWNERS.actions.map(action => action.resourceCode),
    [payoutActions.submit, payoutActions.manage]);
});

test('every payout enum shown on the pages has an English label', () => {
  const groups: Record<string, string[]> = {
    displayState: ['PENDING_REVIEW', 'COOLING', 'USABLE', 'REJECTED', 'SUSPENDED', 'CLOSED'],
    stage: ['MAKER', 'CHECKER', 'DONE'],
    rejectReason: [...rejectReasons], suspendReason: javaEnum(enums + 'BeneficiarySuspendReason.java'), closeReason: [...closeReasons],
    blockReason: javaEnum(enums + 'BeneficiaryBlockReason.java'), railType: javaEnum(enums + 'BeneficiaryRailType.java'),
    ownerType: javaEnum(enums + 'BeneficiaryOwnerType.java'), eventAction: javaEnum(enums + 'BeneficiaryEventAction.java'),
    actorType: javaEnum(enums + 'BeneficiaryActorType.java'), latestAction: ['SUBMITTED', 'MAKER_REVIEWED', 'APPROVED', 'REJECTED', 'SUSPENDED', 'CLOSED'],
    nameMatch: ['EXACT', 'NORMALIZED', 'MISMATCH', 'UNKNOWN'], field: ['holderName', 'iban', 'accountNumber', 'bankCode', 'swiftBic', 'bankName', 'accountType'],
  };
  const missing = Object.entries(groups).flatMap(([group, codes]) => codes.map(code => `payout.enum.${group}.${code}`))
    .filter(key => !Object.hasOwn(builtin.payout, key));
  assert.deepEqual(missing, []);
});

test('review stage follows status and Maker opinion', () => {
  assert.equal(reviewStageOf(account()), 'MAKER');
  assert.equal(reviewStageOf(account({ reviewMakerDecision: 'APPROVE' })), 'CHECKER');
  assert.equal(reviewStageOf(account({ status: 'VERIFIED', reviewMakerDecision: 'APPROVE' })), 'DONE');
});

test('four eyes blocks the Maker and an Operations submitter, not a Client member with the same id', () => {
  assert.equal(fourEyesBlock(account({ reviewMakerId: '20002' }), '20002'), 'MAKER');
  assert.equal(fourEyesBlock(account({ submittedByType: 'OPERATIONS', submittedBy: '20003' }), '20003'), 'SUBMITTER');
  assert.equal(fourEyesBlock(account({ submittedBy: '20003' }), '20003'), null);
  assert.equal(fourEyesBlock(account({ reviewMakerId: '20002' }), '20004'), null);
  assert.equal(checkerMayApprove(account({ reviewMakerDecision: 'REJECT' })), false);
  assert.equal(checkerMayApprove(account({ reviewMakerDecision: 'APPROVE' })), true);
});

test('holder name comparison ignores case, spaces and punctuation only', () => {
  assert.equal(compareNames('Atlas Trading LLC', 'Atlas Trading LLC'), 'EXACT');
  assert.equal(compareNames('Atlas Trading LLC', 'ATLAS TRADING L.L.C'), 'NORMALIZED');
  assert.equal(compareNames('Atlas Trading LLC', 'ATLAS TRADING FZE'), 'MISMATCH');
  assert.equal(compareNames(null, 'ATLAS'), 'UNKNOWN');
  assert.equal(compareNames('Atlas', ''), 'UNKNOWN');
});

test('display state splits VERIFIED by the server-side usable flag; commands follow status', () => {
  assert.equal(displayState(account({ status: 'VERIFIED', usable: false })), 'COOLING');
  assert.equal(displayState(account({ status: 'VERIFIED', usable: true })), 'USABLE');
  assert.deepEqual(availableCommands(account({ status: 'VERIFIED' })), ['suspend', 'close']);
  assert.deepEqual(availableCommands(account({ status: 'SUSPENDED' })), ['resume', 'close']);
  assert.deepEqual(availableCommands(account({ status: 'REJECTED' })), []);
});

test('latest action uses the decision for reviewed accounts and the Maker for pending checker review', () => {
  assert.deepEqual(latestAction(account({ status: 'VERIFIED', decisionTime: '2026-10-30T06:32:00Z', checkerId: '20005' })),
    { action: 'APPROVED', time: '2026-10-30T06:32:00Z', actorId: '20005' });
  assert.deepEqual(latestAction(account({ reviewMakerDecision: 'APPROVE', reviewMakerId: '20002', updateTime: '2026-10-30T06:18:00Z' })),
    { action: 'MAKER_REVIEWED', time: '2026-10-30T06:18:00Z', actorId: '20002' });
  assert.equal(latestAction(account()).action, 'SUBMITTED');
});

test('balance segments and cooling end', () => {
  assert.deepEqual(balanceSegments({ pending: '25.00', available: '75.00', locked: '0.00' }), { pending: 0.25, available: 0.75, locked: 0 });
  assert.deepEqual(balanceSegments({ pending: '0', available: '0.00', locked: '0' }), { pending: 0, available: 0, locked: 0 });
  assert.equal(positive('210.00'), true);
  assert.equal(positive('0.00'), false);
  const cooling = [account({ status: 'VERIFIED', usable: false, coolingUntil: '2026-10-31T06:32:00Z' }),
    account({ status: 'VERIFIED', usable: false, coolingUntil: '2026-10-31T05:00:00Z' }), account({ currency: 'AED', status: 'VERIFIED', coolingUntil: '2026-10-30T00:00:00Z' })];
  assert.equal(earliestCoolingEnd(cooling, 'USD'), '2026-10-31T05:00:00Z');
  assert.equal(earliestCoolingEnd(cooling, 'SAR'), null);
});

test('owner and queue query strings', () => {
  assert.deepEqual(readOwnerQuery('?ownerType=CLIENT&ownerId=80014'), { ownerType: 'CLIENT', ownerId: '80014' });
  assert.equal(readOwnerQuery('?ownerType=PAYEE&ownerId=1'), null);
  assert.equal(readOwnerQuery('?ownerType=CLIENT&ownerId=0'), null);
  assert.equal(reviewQuery('MAKER', null, 1, 20), 'stage=MAKER&page=1&size=20');
  assert.equal(reviewQuery('DONE', { ownerType: 'LOCAL_PARTNER', ownerId: '3002' }, 2, 10),
    'stage=DONE&ownerType=LOCAL_PARTNER&ownerId=3002&page=2&size=10');
});

test('submit-on-behalf fields follow the requirement: required, format, one-of and normalisation', () => {
  const swift: Requirement = { railType: 'SWIFT', bankCountry: '*', currency: 'USD', fields: [
    { code: 'holderName', required: true, oneOfGroup: null, pattern: null, maxLength: 140, labelKey: 'holderName' },
    { code: 'iban', required: false, oneOfGroup: 'ACCOUNT', pattern: '[A-Z]{2}[0-9A-Z]{13,32}', maxLength: 42, labelKey: 'iban' },
    { code: 'accountNumber', required: false, oneOfGroup: 'ACCOUNT', pattern: null, maxLength: 42, labelKey: 'accountNumber' },
    { code: 'swiftBic', required: true, oneOfGroup: null, pattern: '[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?', maxLength: 11, labelKey: 'swiftBic' },
  ] };
  assert.deepEqual(fieldIssues(swift, { holderName: 'Atlas', iban: 'gb29 nwbk 6016 1331 9268 19', swiftBic: 'nwbkgb2l' }), []);
  assert.deepEqual(fieldIssues(swift, { holderName: ' ', swiftBic: 'BAD' }),
    [{ code: 'holderName', issue: 'REQUIRED' }, { code: 'swiftBic', issue: 'FORMAT' }, { code: 'iban', issue: 'ONE_OF' }]);
  assert.deepEqual(fieldIssues(swift, { holderName: 'A', iban: 'GB29NWBK60161331926819', accountNumber: '1', swiftBic: 'NWBKGB2L' }),
    [{ code: 'iban', issue: 'ONE_OF' }]);
  assert.deepEqual(submitFields(swift, { holderName: ' Atlas ', iban: 'gb29 nwbk 6016 1331 9268 19', swiftBic: 'nwbkgb2l', bankName: 'ignored' }),
    { holderName: 'Atlas', iban: 'GB29NWBK60161331926819', swiftBic: 'NWBKGB2L' });
});

test('decoders reject foreign or incomplete responses', () => {
  const invalid = (run: () => unknown) => assert.throws(run, (error: unknown) => error instanceof ApiError && error.status === 502);
  assert.equal(decodeAccount(account(), '900100').accountCode, 'BA-0002');
  invalid(() => decodeAccount(account(), '900101'));
  invalid(() => decodeAccount({ ...account(), version: '' }));
  const detail = decodeDetail('900100')({ account: account({ events: [] }), owner: null, operatorLabels: { 20002: 'Lina' } });
  assert.equal(detail.operatorLabels['20002'], 'Lina');
  invalid(() => decodeDetail('900100')({ account: account(), owner: null }));
  invalid(() => decodeOwnerAccounts('CLIENT', '80014')({ ownerType: 'CLIENT', ownerId: '80014', items: [account({ ownerId: '1' })] }));
  assert.equal(decodeReviewPage({ items: [{ account: account(), owner: null }], page: 1, size: 20, total: 1 }).items[0].owner, null);
  invalid(() => decodeReveal('900100')({ beneficiaryAccountId: '900101' }));
});

test('tab counts: the active tab is not re-queried and the others load one at a time', async () => {
  assert.deepEqual(countStagesToFetch('MAKER'), ['CHECKER']);
  assert.deepEqual(countStagesToFetch('DONE'), ['MAKER', 'CHECKER']);
  let inFlight = 0, maxInFlight = 0; const asked: string[] = [];
  const counts = await loadStageCounts(['MAKER', 'CHECKER'], async stage => {
    inFlight++; maxInFlight = Math.max(maxInFlight, inFlight); asked.push(stage);
    await new Promise(resolve => setTimeout(resolve, 5)); inFlight--;
    return stage === 'MAKER' ? 3 : 2;
  }, {}, new AbortController().signal);
  assert.deepEqual(counts, { MAKER: 3, CHECKER: 2 });
  assert.deepEqual(asked, ['MAKER', 'CHECKER']);
  assert.equal(maxInFlight, 1);
});

test('tab counts: 429 keeps the previous count, other failures drop it, abort stops further requests', async () => {
  const limited = await loadStageCounts(['CHECKER'], async () => { throw new ApiError(429, 'RATE_LIMITED'); }, { MAKER: 3, CHECKER: 2 },
    new AbortController().signal);
  assert.deepEqual(limited, { MAKER: 3, CHECKER: 2 });
  const failed = await loadStageCounts(['CHECKER'], async () => { throw new ApiError(503, 'DEPENDENCY_UNAVAILABLE'); }, { CHECKER: 2 },
    new AbortController().signal);
  assert.deepEqual(failed, {});
  const controller = new AbortController(); const asked: string[] = [];
  await loadStageCounts(['MAKER', 'CHECKER'], async stage => { asked.push(stage); controller.abort(); return 1; }, {}, controller.signal);
  assert.deepEqual(asked, ['MAKER']);
});
