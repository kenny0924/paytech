import assert from 'node:assert/strict';
import test from 'node:test';
import {
  DEFAULT_RETRY_MILLIS, DETAIL_PRIORITY, DetailQueue, DetailRequestCancelled, MAX_RETRY_MILLIS, PREFETCH_LIMIT, planBatchFill, planPrefetch,
  retryAfterMillis,
} from './prefetchModel.ts';

test('plan skips selected and cached, dedupes and caps at the limit in order', () => {
  assert.equal(PREFETCH_LIMIT, 5);
  assert.deepEqual(planPrefetch(['a', 'b', 'a', 'c', 'd', 'e', 'f', 'g', 'h'], 'b', new Set(['c'])), ['a', 'd', 'e', 'f', 'g']);
  assert.deepEqual(planPrefetch(['a', 'b'], null, new Set(), 1), ['a']);
  assert.deepEqual(planPrefetch([], 'a', new Set()), []);
});

test('batch fill puts checked instances first, dedupes, skips cached and failed, no cap', () => {
  assert.deepEqual(planBatchFill(['x', 'c', 'x'], ['a', 'b', 'c', 'd', 'e', 'f', 'g'], new Set(['a']), new Set(['b'])),
    ['x', 'c', 'd', 'e', 'f', 'g']);
  assert.deepEqual(planBatchFill([], [], new Set(), new Set()), []);
});

test('Retry-After accepts seconds or HTTP date, defaults and caps', () => {
  const now = Date.parse('2026-10-22T10:00:00Z');
  assert.equal(retryAfterMillis('3', now), 3000);
  assert.equal(retryAfterMillis('Thu, 22 Oct 2026 10:00:02 GMT', now), 2000);
  assert.equal(retryAfterMillis('Thu, 22 Oct 2026 09:59:00 GMT', now), 0);
  assert.equal(retryAfterMillis(null, now), DEFAULT_RETRY_MILLIS);
  assert.equal(retryAfterMillis('soon', now), DEFAULT_RETRY_MILLIS);
  assert.equal(retryAfterMillis('3600', now), MAX_RETRY_MILLIS);
});

/** 手动放行的加载桩：记录调用顺序与并发峰值。 */
function harness(fail: (id: string, attempt: number) => unknown = () => null) {
  const calls: string[] = []; let inFlight = 0; let peak = 0;
  const releases: (() => void)[] = [];
  const attempts = new Map<string, number>();
  const load = (id: string) => new Promise<string>((resolve, reject) => {
    calls.push(id); inFlight++; peak = Math.max(peak, inFlight);
    const attempt = (attempts.get(id) ?? 0) + 1; attempts.set(id, attempt);
    releases.push(() => { inFlight--; const error = fail(id, attempt); if (error) reject(error); else resolve(`detail-${id}`); });
  });
  const release = async () => { releases.shift()!(); await new Promise(resolve => setTimeout(resolve, 0)); };
  return { calls, load, release, peak: () => peak };
}

test('queue runs one request at a time, selected first then batch then prefetch, FIFO within a priority', async () => {
  const h = harness();
  const queue = new DetailQueue(h.load, { retryDelay: () => null });
  const results: string[] = [];
  const track = (promise: Promise<string>) => void promise.then(value => results.push(value));
  track(queue.request('p1', DETAIL_PRIORITY.PREFETCH, 'prefetch'));
  track(queue.request('p2', DETAIL_PRIORITY.PREFETCH, 'prefetch'));
  track(queue.request('b1', DETAIL_PRIORITY.BATCH, 'batch'));
  track(queue.request('s1', DETAIL_PRIORITY.SELECTED, 'selected'));
  assert.equal(queue.running(), 'p1');
  assert.deepEqual(queue.pending(), ['s1', 'b1', 'p2']);
  for (let index = 0; index < 4; index++) await h.release();
  assert.deepEqual(h.calls, ['p1', 's1', 'b1', 'p2']);
  assert.equal(h.peak(), 1);
  assert.deepEqual(results, ['detail-p1', 'detail-s1', 'detail-b1', 'detail-p2']);
});

test('queue merges the same instance, raises its priority and shares the result with the in-flight request', async () => {
  const h = harness();
  const queue = new DetailQueue(h.load, { retryDelay: () => null });
  const first = queue.request('a', DETAIL_PRIORITY.PREFETCH, 'prefetch');
  const prefetch = queue.request('b', DETAIL_PRIORITY.PREFETCH, 'prefetch');
  queue.request('c', DETAIL_PRIORITY.BATCH, 'batch').catch(() => undefined);
  const promoted = queue.request('b', DETAIL_PRIORITY.SELECTED, 'selected');
  const shared = queue.request('a', DETAIL_PRIORITY.SELECTED, 'selected');
  assert.deepEqual(queue.pending(), ['b', 'c']);
  await h.release(); await h.release(); await h.release();
  assert.deepEqual(h.calls, ['a', 'b', 'c']);
  assert.deepEqual(await Promise.all([first, shared, prefetch, promoted]), ['detail-a', 'detail-a', 'detail-b', 'detail-b']);
});

test('cancel removes queued waiters of a tag without aborting the in-flight request', async () => {
  const h = harness();
  const queue = new DetailQueue(h.load, { retryDelay: () => null });
  const running = queue.request('a', DETAIL_PRIORITY.PREFETCH, 'prefetch');
  const queued = queue.request('b', DETAIL_PRIORITY.PREFETCH, 'prefetch');
  const kept = queue.request('b', DETAIL_PRIORITY.BATCH, 'batch');
  const dropped = queue.request('c', DETAIL_PRIORITY.PREFETCH, 'prefetch');
  queue.cancel('prefetch');
  await assert.rejects(running, DetailRequestCancelled);
  await assert.rejects(queued, DetailRequestCancelled);
  await assert.rejects(dropped, DetailRequestCancelled);
  assert.equal(queue.running(), 'a');
  assert.deepEqual(queue.pending(), ['b']);
  await h.release(); await h.release();
  assert.deepEqual(h.calls, ['a', 'b']);
  assert.equal(await kept, 'detail-b');
});

test('429 is retried once after the injected delay; a second failure rejects and the queue continues', async () => {
  const tooMany = Object.assign(new Error('429'), { status: 429 });
  const h = harness((id, attempt) => id === 'x' || id === 'y' && attempt === 1 ? tooMany : id === 'z' ? new Error('500') : null);
  const sleeps: number[] = [];
  const queue = new DetailQueue(h.load, {
    retryDelay: error => (error as { status?: number }).status === 429 ? 250 : null,
    sleep: async millis => { sleeps.push(millis); },
  });
  const y = queue.request('y', DETAIL_PRIORITY.SELECTED, 's');
  const x = queue.request('x', DETAIL_PRIORITY.BATCH, 'b').then(() => null, (error: Error) => error.message);
  const z = queue.request('z', DETAIL_PRIORITY.BATCH, 'b').then(() => null, (error: Error) => error.message);
  for (let index = 0; index < 5; index++) await h.release();
  assert.equal(await y, 'detail-y');
  assert.equal(await x, '429');
  assert.equal(await z, '500');
  assert.deepEqual(h.calls, ['y', 'y', 'x', 'x', 'z']);
  assert.deepEqual(sleeps, [250, 250]);
  assert.equal(h.peak(), 1);
});
