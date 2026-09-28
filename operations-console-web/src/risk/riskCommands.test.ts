import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../api/session.ts';
import { commandFailureSettlement, randomCommand, saveSizeState, settlePendingCommand, utf8ByteLength, validateKey } from './riskCommands.ts';

function exactJsonBytes(bytes: number, character: string, characterBytes: number): string {
  // {"value":""} is twelve ASCII bytes; the filler covers the remaining byte remainder.
  const contentBytes = bytes - 12;
  return `{"value":"${character.repeat(Math.floor(contentBytes / characterBytes))}${'a'.repeat(contentBytes % characterBytes)}"}`;
}

for (const [character, characterBytes, label] of [['a', 1, 'ASCII'], ['中', 3, 'Chinese'], ['😀', 4, 'emoji']] as const) {
  for (const [bytes, state] of [[49_151, 'NORMAL'], [49_152, 'WARNING'], [65_536, 'WARNING'], [65_537, 'TOO_LARGE']] as const) {
    test(`${label} JSON at exactly ${bytes} UTF-8 bytes is ${state}`, () => {
      const payload = exactJsonBytes(bytes, character, characterBytes);
      assert.equal(Buffer.byteLength(payload, 'utf8'), bytes);
      assert.doesNotThrow(() => JSON.parse(payload));
      assert.equal(utf8ByteLength(payload), bytes);
      assert.equal(saveSizeState(payload), state);
      if (characterBytes > 1) assert.notEqual(payload.length, bytes);
    });
  }
}

test('byte counting preserves whitespace, escaped text and normalization differences in the actual envelope', () => {
  for (const [payload, bytes] of [['', 0], [' {"v":"中😀"}\n', 17], ['{"v":"\\u4e2d"}', 14], ['é', 2], ['e\u0301', 3]] as const) {
    assert.equal(utf8ByteLength(payload), bytes);
  }
});

test('random commands reuse only identical path and payload and preserve the original envelope', context => {
  const keys = [
    '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222',
    '33333333-3333-4333-8333-333333333333', '44444444-4444-4444-8444-444444444444',
    '55555555-5555-4555-8555-555555555555',
  ];
  let nextKey = 0;
  context.mock.method(globalThis.crypto, 'randomUUID', () => keys[nextKey++]!);
  const path = '/decision-rule-sets/v1/rule-sets';
  const payload = '{"reason":"规则😀","rules":[]}';
  const first = randomCommand(path, payload, null);
  assert.deepEqual(first, { path, payload, key: keys[0] });
  assert.deepEqual(randomCommand(path, payload, first), first);
  const changedBody = randomCommand(path, `${payload}\n`, first);
  assert.deepEqual(changedBody, { path, payload: `${payload}\n`, key: keys[1] });
  const changedPath = randomCommand(`${path}/1/freeze`, payload, first);
  assert.deepEqual(changedPath, { path: `${path}/1/freeze`, payload, key: keys[2] });
  const changedBoth = randomCommand(`${path}/2/freeze`, '{}', first);
  assert.deepEqual(changedBoth, { path: `${path}/2/freeze`, payload: '{}', key: keys[3] });
  assert.deepEqual(randomCommand(path, payload, null), { path, payload, key: keys[4] });
  assert.deepEqual(first, { path, payload, key: keys[0] });
  assert.equal(nextKey, 5);
});

test('semantically equivalent JSON and Unicode do not reuse a byte-distinct command key', context => {
  const keys = ['11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222', '33333333-3333-4333-8333-333333333333'];
  let nextKey = 0;
  context.mock.method(globalThis.crypto, 'randomUUID', () => keys[nextKey++]!);
  const first = randomCommand('/command', '{"a":"é","b":1}', null);
  assert.equal(randomCommand('/command', '{"b":1,"a":"é"}', first).key, keys[1]);
  assert.equal(randomCommand('/command', '{"a":"e\u0301","b":1}', first).key, keys[2]);
});

test('a production random command uses Web Crypto UUID and retains immutable strings', () => {
  const first = randomCommand('/command', '{"v":"中😀"}', null);
  assert.match(first.key, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  assert.equal(first.path, '/command');
  assert.equal(first.payload, '{"v":"中😀"}');
  assert.ok(Object.isFrozen(first));
  assert.throws(() => Object.assign(first, { payload: 'overwritten' }), TypeError);
});

const pending = Object.freeze({ path: '/command', payload: '{"v":"中😀"}', key: '11111111-1111-4111-8111-111111111111' });

test('confirmed success clears only pending command state', () => {
  assert.equal(settlePendingCommand(pending, 'SUCCESS'), null);
  assert.deepEqual(pending, { path: '/command', payload: '{"v":"中😀"}', key: '11111111-1111-4111-8111-111111111111' });
});

for (const status of [400, 401, 403, 404, 409, 413, 422, 429]) {
  test(`HTTP ${status} is a confirmed failure and clears the key`, () => {
    const settlement = commandFailureSettlement(new ApiError(status, 'REQUEST_FAILED'));
    assert.equal(settlement, 'DETERMINISTIC_HTTP_FAILURE');
    assert.equal(settlePendingCommand(pending, settlement), null);
  });
}

for (const status of [500, 502, 503, 504, 408, 418, 425, 499, 200]) {
  test(`HTTP ${status} outside the deterministic failure allowlist retains the exact command`, () => {
    const settlement = commandFailureSettlement(new ApiError(status, 'REQUEST_FAILED'));
    assert.equal(settlement, 'UNKNOWN_RESULT');
    assert.equal(settlePendingCommand(pending, settlement), pending);
  });
}

for (const [label, error] of [
  ['timeout', new DOMException('Timed out', 'TimeoutError')],
  ['disconnect', new TypeError('Failed to fetch')],
  ['abort', new DOMException('Aborted', 'AbortError')],
  ['unrecognized Error', new Error('private payload')],
  ['unrecognized object with status', { status: 400, message: 'private payload' }],
  ['undefined', undefined], ['null', null], ['string', '400'],
] as const) {
  test(`${label} retains the exact command without relying on error text`, () => {
    const settlement = commandFailureSettlement(error);
    assert.equal(settlement, 'UNKNOWN_RESULT');
    assert.equal(settlePendingCommand(pending, settlement), pending);
  });
}

for (const [releaseId, expectedVersion, key] of [
  ['4', 1, 'risk-validate-v1-4-1'], ['4', 2, 'risk-validate-v1-4-2'],
  ['999999999999999999999999999999', 1, 'risk-validate-v1-999999999999999999999999999999-1'],
] as const) {
  test(`Validate key is deterministic for release ${releaseId} version ${expectedVersion}`, context => {
    context.mock.method(globalThis.crypto, 'randomUUID', () => { throw new Error('Validate must never use a random key'); });
    assert.equal(validateKey(releaseId, expectedVersion), key);
    assert.equal(validateKey(releaseId, expectedVersion), key);
  });
}

test('Validate key cannot be supplied by an external override', () => {
  const runtimeCall = validateKey as (...args: unknown[]) => string;
  assert.equal(runtimeCall('4', 1, 'caller-supplied-random-key'), 'risk-validate-v1-4-1');
});
