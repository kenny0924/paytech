import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import type { ConsoleResource } from '../api/types.ts';
import { pageCapabilities } from '../navigation/pageCapabilities.ts';
import { pageRoutePaths } from '../navigation/menu.ts';
import {
  advancedBeforeRejection, browseStartSequence, catchUpDisplayKind, catchUpOutcome, factUnavailableCause, listFailureNotice, decodeCatchUpResult, decodePostingPage, decodePostingProgress, decodePostingSummary, decodeRebuildResult,
  gapOf, hasPostingOpsAction, postingOpsActions, postingOpsPaths, postingOpsRoute, postingOpsSearch, postingStreamRoute, readPostingOpsQuery,
  readStreamBrowseQuery, rebuildBody, rebuildOutcome, recallBookLabel, rememberBookLabel, remainingAfterCatchUp, streamBrowseSearch, stuckSequenceOf,
} from './postingOps.ts';

const ledgerBookId = '900000000000100001';
const at = '2026-10-30T10:24:31.000Z';
const stuck = {
  postingId: '900000000012345678', postingSequence: '1511', sourceType: 'ECONOMIC_FACT', factId: '880000000000123390', factType: 'PAYMENT',
  factApplyStatus: 'APPLIED', postedTime: '2026-10-30T08:40:12.551000Z', publishedTime: '2026-10-30T08:40:12.902000Z', entryCount: '4',
};
const stream = (overrides: Record<string, unknown> = {}) => ({
  streamId: 'OE.AE001.USD.0', entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD', shardNo: '0', accountMaxSequence: '1530',
  accountLastPostedTime: '2026-10-30T09:12:03.120000Z', ledgerConsumedSequence: '1510',
  lag: '20', status: 'BEHIND', stuckPosting: stuck, ...overrides,
});
const progress = (overrides: Record<string, unknown> = {}) => ({
  ledgerBookId, entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD',
  factSegment: { status: 'BEHIND', billingPendingCount: '2', billingMaxPendingFactId: '880000000000123460', billingPublishedCount: '1520',
    billingMaxPublishedFactId: '880000000000123458', accountAppliedCount: '1518', accountMaxAppliedFactId: '880000000000123401' },
  streamSegment: { status: 'BEHIND', streams: [stream()] },
  ...overrides,
});
const invalid = (run: () => unknown) => assert.throws(run, (error: unknown) => error instanceof ApiError && error.status === 502 && error.code === 'INVALID_RESPONSE');
const button = (resourceCode: string) => ({ resourceCode, resourceType: 'BUTTON' }) as ConsoleResource;

test('B1 decoder accepts the technique 7.3 sample, an unavailable billing segment and a Finance Entity book with an OE stream', () => {
  const decoded = decodePostingProgress(progress(), ledgerBookId);
  assert.equal(decoded.streamSegment.streams[0].stuckPosting?.postingSequence, '1511');
  const unavailable = decodePostingProgress(progress({ factSegment: { status: 'UNAVAILABLE', billingPendingCount: null } }), ledgerBookId);
  assert.equal(unavailable.factSegment.billingPublishedCount, null);
  const fe = decodePostingProgress(progress({ entityType: 'FINANCE_ENTITY', entityId: '8001', streamSegment: { status: 'CONSISTENT', streams: [
    stream({ streamId: 'FE.8001.USD.0', entityType: 'FINANCE_ENTITY', entityId: '8001', status: 'CONSISTENT', lag: '0', ledgerConsumedSequence: '1530', stuckPosting: null }),
    stream({ status: 'CONSISTENT', lag: '0', ledgerConsumedSequence: '1530', accountLastPostedTime: null, stuckPosting: undefined }),
  ] } }), ledgerBookId);
  assert.deepEqual(fe.streamSegment.streams.map(item => item.stuckPosting), [null, null]);
  assert.equal(fe.streamSegment.streams[1].accountLastPostedTime, null);
  // 已删除字段（technique 7.1 阶段 4 修订）不进入页面模型
  assert.equal('ledgerLastConsumedTime' in decodePostingProgress(progress({ streamSegment: { status: 'BEHIND', streams: [stream({ ledgerLastConsumedTime: at })] } }), ledgerBookId).streamSegment.streams[0], false);
  // 账户不可用：没有流
  assert.equal(decodePostingProgress(progress({ streamSegment: { status: 'UNAVAILABLE', streams: [] } }), ledgerBookId).streamSegment.streams.length, 0);
});

test('B1 decoder rejects drift: other book, numbers on an unavailable segment, stream ID mismatch, stuck batch on a consistent stream, bad status', () => {
  invalid(() => decodePostingProgress(progress(), '900000000000100002'));
  invalid(() => decodePostingProgress(progress({ factSegment: { status: 'UNAVAILABLE', billingPendingCount: '2' } }), ledgerBookId));
  invalid(() => decodePostingProgress(progress({ factSegment: { ...progress().factSegment, billingPublishedCount: 1520 } }), ledgerBookId));
  invalid(() => decodePostingProgress(progress({ streamSegment: { status: 'UNAVAILABLE', streams: [stream()] } }), ledgerBookId));
  invalid(() => decodePostingProgress(progress({ streamSegment: { status: 'BEHIND', streams: [stream({ streamId: 'OE.AE001.USD.1' })] } }), ledgerBookId));
  invalid(() => decodePostingProgress(progress({ streamSegment: { status: 'BEHIND', streams: [stream({ status: 'CONSISTENT' })] } }), ledgerBookId));
  invalid(() => decodePostingProgress(progress({ streamSegment: { status: 'LAGGING', streams: [] } }), ledgerBookId));
  invalid(() => decodePostingProgress(progress({ streamSegment: { status: 'BEHIND', streams: [stream({ lag: '-1' })] } }), ledgerBookId));
});

test('batch summary: fact batches need a fact ID and type, bucket movements have none', () => {
  assert.equal(decodePostingSummary({ ...stuck, publishedTime: undefined }).publishedTime, null);
  const movement = decodePostingSummary({ ...stuck, sourceType: 'BALANCE_MOVEMENT', factId: null, factType: null, factApplyStatus: null });
  assert.equal(movement.factId, null);
  invalid(() => decodePostingSummary({ ...stuck, factId: null }));
  invalid(() => decodePostingSummary({ ...stuck, sourceType: 'BALANCE_MOVEMENT' }));
  invalid(() => decodePostingSummary({ ...stuck, factApplyStatus: 'FAILED' }));
  invalid(() => decodePostingSummary({ ...stuck, entryCount: 4 }));
});

test('B2 page: ascending from the start sequence, next start after the page', () => {
  const second = { ...stuck, postingId: '900000000012345679', postingSequence: '1512' };
  assert.deepEqual(decodePostingPage({ items: [stuck, second], nextFromSequence: '1513' }, '1511').nextFromSequence, '1513');
  assert.equal(decodePostingPage({ items: [], nextFromSequence: null }, '1').items.length, 0);
  invalid(() => decodePostingPage({ items: [second, stuck], nextFromSequence: null }, '1511'));
  invalid(() => decodePostingPage({ items: [stuck], nextFromSequence: null }, '1512'));
  invalid(() => decodePostingPage({ items: [stuck], nextFromSequence: '1511' }, '1511'));
  invalid(() => decodePostingPage({ postings: [] }, '1'));
});

test('B3 / B4 results', () => {
  assert.deepEqual(decodeCatchUpResult({ processedCount: '200', lastSequence: '1710', caughtUp: false }), { processedCount: '200', lastSequence: '1710', caughtUp: false });
  invalid(() => decodeCatchUpResult({ processedCount: '200', lastSequence: '1710', caughtUp: 'false' }));
  assert.equal(decodeRebuildResult({ accountCount: '6', changedAccountCount: '1', entryCount: '3120', maxPostingSequence: '1530' }).changedAccountCount, '1');
  assert.equal(decodeRebuildResult({ accountCount: '6', entryCount: '3120', maxPostingSequence: '1530' }).changedAccountCount, null);
  invalid(() => decodeRebuildResult({ accountCount: '6', changedAccountCount: 'one', entryCount: '3120', maxPostingSequence: '1530' }));
});

test('remaining after catch-up uses the refreshed account max sequence; stuck and browse start sequences', () => {
  const refreshed = decodePostingProgress(progress({ streamSegment: { status: 'BEHIND', streams: [stream({ accountMaxSequence: '1830', ledgerConsumedSequence: '1710', lag: '120' })] } }), ledgerBookId);
  assert.equal(remainingAfterCatchUp(refreshed, 'OE.AE001.USD.0', '1710'), 120n);
  assert.equal(remainingAfterCatchUp(refreshed, 'OE.AE001.USD.0', '1900'), 0n);
  assert.equal(remainingAfterCatchUp(refreshed, 'OE.SA001.SAR.0', '1710'), null);
  assert.equal(remainingAfterCatchUp(null, 'OE.AE001.USD.0', '1710'), null);
  assert.equal(gapOf('1520', '1518'), 2n);
  assert.equal(gapOf(null, '1518'), 0n);
  const behind = decodePostingProgress(progress(), ledgerBookId).streamSegment.streams[0];
  assert.equal(stuckSequenceOf(behind), '1511');
  assert.equal(stuckSequenceOf({ ...behind, status: 'CONSISTENT' }), null);
  assert.equal(browseStartSequence(behind), '1501');
  assert.equal(browseStartSequence({ ...behind, status: 'CONSISTENT', accountMaxSequence: '1530' }), '1481');
  assert.equal(browseStartSequence({ ...behind, status: 'CONSISTENT', accountMaxSequence: '12' }), '1');
  assert.equal(browseStartSequence({ ...behind, ledgerConsumedSequence: '0' }), '1');
});

test('catch-up result bar states (P3): done, partial, 409, 400 rule rejection, 504 timeout, each with operator and time', () => {
  const by = 'o***@example.test';
  assert.equal(catchUpOutcome({ processedCount: '20', lastSequence: '1530', caughtUp: true }, at, by)?.kind, 'done');
  assert.equal(catchUpOutcome({ processedCount: '200', lastSequence: '1710', caughtUp: false }, at, by)?.kind, 'partial');
  assert.deepEqual(catchUpOutcome(new ApiError(409, 'NO_OPEN_PERIOD'), at, by), { kind: 'conflict', at, by });
  assert.deepEqual(catchUpOutcome(new ApiError(400, 'LEDGER_RULE_NOT_FOUND', '', 'No active rule'), at, by, '1510'),
    { kind: 'rejected', code: 'LEDGER_RULE_NOT_FOUND', detail: 'No active rule', consumedBefore: '1510', at, by });
  assert.deepEqual(catchUpOutcome(new ApiError(504, 'DEPENDENCY_TIMEOUT'), at, by), { kind: 'timeout', at, by });
  assert.equal(catchUpOutcome(new ApiError(503, 'DEPENDENCY_UNAVAILABLE'), at, by), null);
  assert.equal(catchUpOutcome(new Error('network'), at, by), null);
});

test('a full page that reaches the end of the stream shows as caught up, not "0 left"', () => {
  const streamId = 'OE.AE001.USD.0';
  const exact = catchUpOutcome({ processedCount: '200', lastSequence: '1710', caughtUp: false }, at, 'ops')!;
  const atEnd = decodePostingProgress(progress({ streamSegment: { status: 'CONSISTENT', streams: [
    stream({ accountMaxSequence: '1710', ledgerConsumedSequence: '1710', lag: '0', status: 'CONSISTENT', stuckPosting: null })] } }), ledgerBookId);
  assert.equal(catchUpDisplayKind(exact, atEnd, streamId), 'done');
  const more = decodePostingProgress(progress({ streamSegment: { status: 'BEHIND', streams: [
    stream({ accountMaxSequence: '1830', ledgerConsumedSequence: '1710', lag: '120' })] } }), ledgerBookId);
  assert.equal(catchUpDisplayKind(exact, more, streamId), 'partial');
  assert.equal(catchUpDisplayKind(exact, null, streamId), 'partial', '进度未知时保留「继续追平」');
  assert.equal(catchUpDisplayKind(catchUpOutcome(new ApiError(409, 'CONCURRENT_REQUEST'), at, 'ops')!, atEnd, streamId), 'conflict');
});

test('a rule rejection says whether this run processed batches only after progress is refreshed', () => {
  const streamId = 'OE.AE001.USD.0';
  const rejected = catchUpOutcome(new ApiError(400, 'LEDGER_RULE_NOT_FOUND', '', 'No active rule'), at, 'ops', '1510');
  assert.equal(rejected?.kind, 'rejected');
  if (rejected?.kind !== 'rejected') return;
  const later = '2026-10-30T10:24:33.000Z';
  const unchanged = decodePostingProgress(progress(), ledgerBookId);
  assert.equal(advancedBeforeRejection(rejected, unchanged, later, streamId), 0n);
  assert.equal(advancedBeforeRejection(rejected, unchanged, '2026-10-30T10:20:00.000Z', streamId), null, '拒绝前的进度不下结论');
  const advanced = decodePostingProgress(progress({ streamSegment: { status: 'BEHIND', streams: [
    stream({ ledgerConsumedSequence: '1515', lag: '15' })] } }), ledgerBookId);
  assert.equal(advancedBeforeRejection(rejected, advanced, later, streamId), 5n);
  assert.equal(advancedBeforeRejection({ ...rejected, consumedBefore: null }, advanced, later, streamId), null);
});

test('rebuild result bar states (P5): corrected, unchanged, recalculated without the optional count, no stream, 409, timeout', () => {
  const by = 'ops';
  const result = (changedAccountCount: string | null) => ({ accountCount: '6', changedAccountCount, entryCount: '3120', maxPostingSequence: '1530' });
  assert.equal(rebuildOutcome(result('1'), at, by)?.kind, 'corrected');
  assert.equal(rebuildOutcome(result('0'), at, by)?.kind, 'unchanged');
  assert.equal(rebuildOutcome(result(null), at, by)?.kind, 'recalculated');
  assert.equal(rebuildOutcome(result('1'), at, by)?.by, by);
  assert.deepEqual(rebuildOutcome(new ApiError(400, 'STREAM_NOT_FOUND'), at, by), { kind: 'noStream', at, by });
  assert.equal(rebuildOutcome(new ApiError(400, 'INVALID_REQUEST'), at, by), null);
  assert.deepEqual(rebuildOutcome(new ApiError(409, 'CONCURRENT_REQUEST'), at, by), { kind: 'conflict', at, by });
  assert.deepEqual(rebuildOutcome(new ApiError(504, 'DEPENDENCY_TIMEOUT'), at, by), { kind: 'timeout', at, by });
  assert.equal(rebuildOutcome(new ApiError(503, 'DEPENDENCY_UNAVAILABLE'), at, by), null);
  assert.deepEqual(rebuildBody(decodePostingProgress(progress(), ledgerBookId)), { entityType: 'OPERATING_ENTITY', entityId: 'AE001', currency: 'USD' });
});

test('execute buttons follow the two BUTTON resources; read-only accounts see neither', () => {
  const readOnly: ConsoleResource[] = [];
  assert.equal(hasPostingOpsAction(readOnly, postingOpsActions.catchUp), false);
  assert.equal(hasPostingOpsAction(readOnly, postingOpsActions.rebuild), false);
  const catchUpOnly = [button(postingOpsActions.catchUp)];
  assert.equal(hasPostingOpsAction(catchUpOnly, postingOpsActions.catchUp), true);
  assert.equal(hasPostingOpsAction(catchUpOnly, postingOpsActions.rebuild), false);
  assert.equal(hasPostingOpsAction([{ ...button(postingOpsActions.rebuild), resourceType: 'MENU' } as ConsoleResource], postingOpsActions.rebuild), false);
  const page = pageCapabilities.find(item => item.componentKey === 'OPERATIONS_FINANCE_POSTING_OPS');
  assert.equal(page?.frontendRoute, postingOpsRoute);
  assert.deepEqual(page?.actions.map(action => action.resourceCode), [postingOpsActions.catchUp, postingOpsActions.rebuild]);
});

test('URL state: book on the progress page, book + start + drawer on the browse sub-route', () => {
  assert.deepEqual(readPostingOpsQuery(new URLSearchParams(`?ledgerBookId=${ledgerBookId}`)), { ledgerBookId });
  assert.deepEqual(readPostingOpsQuery(new URLSearchParams('?ledgerBookId=abc')), { ledgerBookId: null });
  assert.equal(postingOpsSearch(ledgerBookId), `?ledgerBookId=${ledgerBookId}`);
  assert.equal(postingOpsSearch(null), '');
  const search = streamBrowseSearch(ledgerBookId, '1501', stuck.postingId);
  assert.deepEqual(readStreamBrowseQuery(new URLSearchParams(search)), { ledgerBookId, fromSequence: '1501', posting: stuck.postingId });
  assert.deepEqual(readStreamBrowseQuery(new URLSearchParams('?fromSequence=0&posting=x')), { ledgerBookId: null, fromSequence: '1', posting: null });
  assert.equal(postingStreamRoute('OE.AE001.USD.0'), '/finance/posting-ops/streams/OE.AE001.USD.0');
  const menu = { resourceCode: 'OPERATIONS_FINANCE_POSTING_OPS', componentKey: 'OPERATIONS_FINANCE_POSTING_OPS', frontendRoute: postingOpsRoute } as ConsoleResource;
  assert.deepEqual(pageRoutePaths(menu), [postingOpsRoute, `${postingOpsRoute}/streams/:streamId`]);
});

test('BFF paths (technique 7.3)', () => {
  assert.equal(postingOpsPaths.progress(ledgerBookId), `/finance/posting-progress/v1/ledger-books/${ledgerBookId}`);
  assert.equal(postingOpsPaths.streamPostings('OE.AE001.USD.0', '1501', 50), '/finance/posting-streams/v1/OE.AE001.USD.0/postings?fromSequence=1501&limit=50');
  assert.equal(postingOpsPaths.catchUp('OE.AE001.USD.0'), '/finance/posting-streams/v1/OE.AE001.USD.0/catch-up');
  assert.equal(postingOpsPaths.rebuild, '/finance/posting-rebuilds/v1');
});

test('segment ① names the dependency that is actually unavailable', () => {
  const ok = decodePostingProgress(progress(), ledgerBookId);
  assert.equal(factUnavailableCause(ok), null);
  const billingDown = decodePostingProgress(progress({ factSegment: { status: 'UNAVAILABLE' } }), ledgerBookId);
  assert.equal(factUnavailableCause(billingDown), 'BILLING');
  const accountDown = decodePostingProgress(progress({ factSegment: { status: 'UNAVAILABLE' }, streamSegment: { status: 'UNAVAILABLE', streams: [] } }),
    ledgerBookId);
  assert.equal(factUnavailableCause(accountDown), 'ACCOUNT');
});

test('book and entity list failures are folded into the page error instead of extra notifications', () => {
  assert.equal(listFailureNotice(new Error('ledger down'), 'books failed', 'entities failed'), null);
  assert.equal(listFailureNotice(null, '', 'entities failed'), 'entities failed');
  assert.equal(listFailureNotice(null, 'books failed', 'entities failed'), 'books failed');
  assert.equal(listFailureNotice(null, '', ''), null);
});

test('the selected book keeps its "entity · currency" label when ledger cannot list books', () => {
  const values = new Map<string, string>();
  const storage = { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => { values.set(key, value); } };
  assert.equal(recallBookLabel(storage, ledgerBookId), null);
  rememberBookLabel(storage, ledgerBookId, 'ZQ344 · USD');
  assert.equal(recallBookLabel(storage, ledgerBookId), 'ZQ344 · USD');
  for (let i = 0; i < 60; i++) rememberBookLabel(storage, String(900000000000200000n + BigInt(i)), `B${i} · USD`);
  assert.equal(recallBookLabel(storage, ledgerBookId), null, '最多记 50 本，最早的先淘汰');
  assert.equal(recallBookLabel(storage, '900000000000200059'), 'B59 · USD');
  const broken = { getItem: (): string | null => { throw new Error('denied'); }, setItem: () => { throw new Error('denied'); } };
  assert.equal(recallBookLabel(broken, ledgerBookId), null);
  rememberBookLabel(broken, ledgerBookId, 'ZQ344 · USD');
  assert.equal(recallBookLabel(undefined, ledgerBookId), null);
  values.set('paytech.operations.postingOps.bookLabels', 'not json');
  assert.equal(recallBookLabel(storage, ledgerBookId), null);
});
