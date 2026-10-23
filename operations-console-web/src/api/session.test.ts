import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import type { IncomingMessage, ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import test from 'node:test';
import { ApiError, OperationsSession } from './session.ts';
import type { SuccessDecoder } from './session.ts';
import { candidateDetail, changed, ruleSetDetail } from '../risk/riskFixtures.ts';
import { decodeCandidateDetail, decodeRuleSetDetail } from '../risk/riskResponseValidation.ts';

function barrier() {
  let resolve!: () => void;
  const promise = new Promise<void>(done => { resolve = done; });
  return { promise, resolve };
}
function json(response: ServerResponse, value: unknown, status = 200) {
  response.writeHead(status, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify(value));
}
function legacy(response: ServerResponse, code: string, message: string, status: number,
  requestId = 'legacy-request', timestamp = '2026-09-06T10:20:30.123Z') {
  response.writeHead(status, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify({ code, message, requestId, timestamp }));
}
function problem(response: ServerResponse, value: Record<string, unknown>, status = 400,
  requestId = 'problem-request', contentType = 'application/problem+json') {
  response.writeHead(status, { 'Content-Type': contentType, 'X-Request-Id': requestId });
  response.end(JSON.stringify(value));
}
const springStatusTitles = new Map<number, string>([
  [400, 'Bad Request'], [401, 'Unauthorized'], [402, 'Payment Required'], [403, 'Forbidden'],
  [404, 'Not Found'], [405, 'Method Not Allowed'], [406, 'Not Acceptable'],
  [408, 'Request Timeout'], [409, 'Conflict'], [410, 'Gone'],
  [411, 'Length Required'], [412, 'Precondition Failed'], [413, 'Content Too Large'],
  [414, 'URI Too Long'], [415, 'Unsupported Media Type'], [416, 'Requested range not satisfiable'],
  [417, 'Expectation Failed'], [418, "I'm a teapot"], [421, 'Misdirected Request'],
  [422, 'Unprocessable Content'], [423, 'Locked'], [424, 'Failed Dependency'], [425, 'Too Early'],
  [426, 'Upgrade Required'], [428, 'Precondition Required'], [429, 'Too Many Requests'],
  [431, 'Request Header Fields Too Large'], [451, 'Unavailable For Legal Reasons'],
  [500, 'Internal Server Error'], [501, 'Not Implemented'], [502, 'Bad Gateway'],
  [503, 'Service Unavailable'], [504, 'Gateway Timeout'], [505, 'HTTP Version not supported'],
  [506, 'Variant Also Negotiates'], [507, 'Insufficient Storage'], [508, 'Loop Detected'], [509, 'Bandwidth Limit Exceeded'],
  [510, 'Not Extended'], [511, 'Network Authentication Required'],
]);
function validProblem(status = 400, code = 'BUSINESS_CHECK_FAILED', detail = '已验证的安全说明',
  requestId = 'problem-request'): Record<string, unknown> {
  return {
    type: 'about:blank', title: springStatusTitles.get(status) ?? 'Bad Request', detail, code, requestId,
    timestamp: '2026-09-06T10:20:30.123456Z',
  };
}
const tokens = (value: string) => ({
  accessToken: value, tokenType: 'Bearer', expiresIn: 900,
  refreshToken: `refresh-${value}`, refreshExpiresIn: 28800,
});

function invalidResponse(requestId: string) {
  return (error: unknown) => error instanceof ApiError && error.status === 502
    && error.code === 'INVALID_RESPONSE' && error.requestId === requestId && error.cause === undefined
    && !error.message.includes('private decoder failure');
}
for (const reject of [false, true]) test(`GET refresh preserves the original decoder and second-response ID (${reject ? 'reject' : 'accept'})`, async () => {
  let gets = 0; let refreshes = 0; const observed: unknown[] = [];
  const decoder: SuccessDecoder<{ decoded: boolean }> = value => {
    observed.push(value); if (reject) throw new Error('private decoder failure');
    return { decoded: true };
  };
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('old')); return; }
    if (request.url === '/auth/v1/context') { json(response, { accountId: '3' }); return; }
    if (request.url === '/auth/v1/refresh') { refreshes++; json(response, tokens('new')); return; }
    assert.equal(request.url, '/decoded'); assert.equal(request.method, 'GET');
    if (++gets === 1) { legacy(response, 'OPERATIONS_SESSION_INVALID', 'expired', 401, 'first-401'); return; }
    assert.equal(request.headers.authorization, 'Bearer new');
    response.writeHead(200, { 'Content-Type': 'application/json', 'X-Request-Id': 'second-200' }); response.end('{"second":true}');
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    const result = f.session.request('/decoded', {}, decoder);
    if (reject) await assert.rejects(result, invalidResponse('second-200'));
    else assert.deepEqual(await result, { decoded: true });
    assert.deepEqual(observed, [{ second: true }]); assert.equal(gets, 2); assert.equal(refreshes, 1);
  } finally { await f.close(); }
});

for (const header of ['invalid:request', '-leading', 'a'.repeat(65), 'two, values']) test(`malformed success request ID is discarded: ${header}`, async () => {
  let calls = 0;
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('valid')); return; }
    response.writeHead(200, { 'Content-Type': 'application/json', 'X-Request-Id': header }); response.end('{}');
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    await assert.rejects(f.session.request('/decoded', {}, () => { calls++; throw new Error('private decoder failure'); }), invalidResponse(''));
    assert.equal(calls, 1);
  } finally { await f.close(); }
});
for (const status of [204, 200]) for (const reject of [false, true]) test(`${status} empty success invokes decoder with undefined (${reject ? 'reject' : 'accept'})`, async () => {
  const values: unknown[] = [];
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('valid')); return; }
    response.writeHead(status, { 'Content-Length': '0', 'X-Request-Id': `empty-${status}` }); response.end();
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    const result = f.session.request('/empty', {}, value => { values.push(value); if (reject) throw new Error('private decoder failure'); return 'decoded empty'; });
    if (reject) await assert.rejects(result, invalidResponse(`empty-${status}`)); else assert.equal(await result, 'decoded empty');
    assert.deepEqual(values, [undefined]);
    assert.equal(await f.session.request('/empty'), undefined);
  } finally { await f.close(); }
});

test('ApiError owns a frozen defensive copy of valid field errors', () => {
  const fields = [{ field: 'rules[0].ruleName', message: 'Name is required' }];
  const error = new ApiError(422, 'VALIDATION_ERROR', 'fields-422', undefined, fields);
  assert.deepEqual(error.fieldErrors, fields); assert.notEqual(error.fieldErrors, fields); assert.notEqual(error.fieldErrors[0], fields[0]);
  fields[0]!.message = 'mutated'; fields.push({ field: 'other', message: 'other' });
  assert.deepEqual(error.fieldErrors, [{ field: 'rules[0].ruleName', message: 'Name is required' }]);
  assert.ok(Object.isFrozen(error.fieldErrors)); assert.ok(Object.isFrozen(error.fieldErrors[0]));
  assert.equal(Reflect.set(error.fieldErrors[0]!, 'message', 'changed'), false);
  assert.equal(Reflect.set(error.fieldErrors, '0', { field: 'other', message: 'changed' }), false);
});
test('ApiError never exposes malformed constructor field errors', () => {
  for (const fields of [undefined, null, [], {}, [null], [{ field: 'name' }], [{ field: 'name', message: 'required', extra: true }]]) {
    const error = new ApiError(422, 'VALIDATION_ERROR', 'fields-422', undefined, fields as never);
    assert.deepEqual(error.fieldErrors, []); assert.ok(Object.isFrozen(error.fieldErrors));
  }
});
test('Problem field errors are frozen and every malformed errors shape yields a frozen empty array', async () => {
  const fields = [{ field: 'rules[0].ruleName', message: 'Name is required' }];
  const valid = await rawError('application/problem+json', JSON.stringify({ ...validProblem(), errors: fields }));
  assert.deepEqual(valid.fieldErrors, fields); assert.ok(Object.isFrozen(valid.fieldErrors)); assert.ok(Object.isFrozen(valid.fieldErrors[0]));
  for (const errors of [undefined, null, [], {}, [null], [{ field: 'name' }], [{ message: 'required' }], [{ field: '', message: 'required' }], [{ field: 'name', message: ' ' }], [{ field: 1, message: 'required' }], [{ field: 'name', message: false }], [{ ...fields[0], extra: true }], [...fields, { field: 'name', message: null }]]) {
    const error = await rawError('application/problem+json', JSON.stringify({ ...validProblem(), errors }));
    assert.deepEqual(error.fieldErrors, []); assert.ok(Object.isFrozen(error.fieldErrors));
  }
});
for (const [name, decode, value] of [
  ['rule AST', decodeRuleSetDetail, changed(ruleSetDetail, ['rules', 0, 'conditionNodes', 0, 'operands', 1, 'operandRole'], 'LEFT')],
  ['candidate states', decodeCandidateDetail, changed(candidateDetail('rejected'), ['stateChanges', 2, 'actorId'], '88')],
] as const) test(`${name} semantic rejection happens inside the response-local transport`, async () => {
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('valid')); return; }
    response.writeHead(200, { 'Content-Type': 'application/json', 'X-Request-Id': 'detail-200' }); response.end(JSON.stringify(value));
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    await assert.rejects(f.session.request('/detail', {}, decode as SuccessDecoder<unknown>), invalidResponse('detail-200'));
  } finally { await f.close(); }
});
const sessionStorageKey = 'paytech.operations.session.v1';
function memoryStorage(): Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> {
  const values = new Map<string, string>();
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => { values.set(key, value); },
    removeItem: key => { values.delete(key); },
  };
}
const savedTokens = (value: string) => ({
  version: 1, accessToken: value, refreshToken: `refresh-${value}`,
  accessExpiresAt: Date.now() + 60_000, refreshExpiresAt: Date.now() + 120_000,
});
async function body(request: IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = [];
  for await (const chunk of request) chunks.push(Buffer.from(chunk));
  return JSON.parse(Buffer.concat(chunks).toString());
}
function changedSession(error: unknown) {
  return error instanceof ApiError && error.code === 'SESSION_CHANGED';
}
async function fixture(handler: (request: IncomingMessage, response: ServerResponse) => void | Promise<void>,
  storage = memoryStorage()) {
  const server = createServer((request, response) => {
    assert.equal(request.headers.cookie, undefined);
    assert.equal(request.headers['x-csrf-token'], undefined);
    void handler(request, response);
  });
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
  const baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  const session = new OperationsSession(baseUrl, storage);
  return { session, storage, baseUrl, reload: () => new OperationsSession(baseUrl, storage),
    close: () => new Promise<void>((resolve, reject) => {
      server.close(error => error ? reject(error) : resolve()); server.closeAllConnections();
    }) };
}

test('parallel authenticated queries consume only one refresh generation', async () => {
  let rotations = 0;
  let loggedIn = false;
  const started = barrier(); const release = barrier();
  const f = await fixture(async (request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('access-old')); return; }
    if (request.url === '/auth/v1/refresh') {
      rotations++;
      assert.equal(request.headers.authorization, undefined);
      assert.deepEqual(await body(request), { refreshToken: 'refresh-access-old' });
      started.resolve(); await release.promise; json(response, tokens('access-one')); return;
    }
    if (!loggedIn) { json(response, { accountId: 'one' }); return; }
    if (request.headers.authorization === 'Bearer access-old') {
      legacy(response, 'OPERATIONS_SESSION_INVALID', 'private legacy diagnostic', 401); return;
    }
    assert.equal(request.headers.authorization, 'Bearer access-one');
    json(response, { accountId: 'one' });
  });
  try {
    await f.session.login('operator', 'synthetic-password'); loggedIn = true;
    const first = f.session.context(); const second = f.session.context();
    await started.promise;
    assert.equal(f.storage.getItem(sessionStorageKey), null);
    await assert.rejects(f.reload().context(), (error: unknown) => error instanceof ApiError && error.status === 401);
    assert.equal(rotations, 1);
    release.resolve();
    const result = await Promise.all([first, second]);
    assert.equal(rotations, 1); assert.equal(result[0].accountId, 'one');
  } finally { await f.close(); }
});

test('query rate limiting preserves the session without an automatic refresh or retry', async () => {
  let rotations = 0; let expired = 0;
  const queryTokens: Array<string | undefined> = [];
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('original')); return; }
    if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
    if (request.url === '/auth/v1/refresh') { rotations++; json(response, tokens('unexpected')); return; }
    assert.equal(request.url, '/authorization/v1/navigation');
    queryTokens.push(request.headers.authorization);
    if (queryTokens.length === 1) { legacy(response, 'RATE_LIMITED', 'private legacy diagnostic', 429); return; }
    json(response, { resources: [] });
  });
  f.session.onExpired = () => { expired++; };
  try {
    await f.session.login('operator', 'synthetic-password');
    const saved = f.storage.getItem(sessionStorageKey);
    await assert.rejects(f.session.request('/authorization/v1/navigation'), (error: unknown) =>
      error instanceof ApiError && error.status === 429 && error.message === "Too many requests. Please try again later.");
    assert.deepEqual(queryTokens, ['Bearer original']);
    assert.equal(rotations, 0); assert.equal(expired, 0);
    assert.deepEqual(await f.session.request('/authorization/v1/navigation'), { resources: [] });
    assert.deepEqual(queryTokens, ['Bearer original', 'Bearer original']);
    assert.equal(rotations, 0); assert.equal(expired, 0);
    assert.equal(f.storage.getItem(sessionStorageKey), saved);
  } finally { await f.close(); }
});

test('refresh rate limiting preserves both tokens and allows a later explicit rotation', async () => {
  let expired = 0;
  const refreshTokens: unknown[] = [];
  const f = await fixture(async (request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('original')); return; }
    if (request.url === '/auth/v1/context') {
      json(response, { accountId: request.headers.authorization }); return;
    }
    assert.equal(request.url, '/auth/v1/refresh');
    assert.equal(request.headers.authorization, undefined);
    refreshTokens.push((await body(request)).refreshToken);
    if (refreshTokens.length === 1) { legacy(response, 'RATE_LIMITED', 'private legacy diagnostic', 429); return; }
    json(response, tokens(refreshTokens.length === 2 ? 'rotated' : 'next'));
  });
  f.session.onExpired = () => { expired++; };
  try {
    await f.session.login('operator', 'synthetic-password');
    const saved = f.storage.getItem(sessionStorageKey);
    await assert.rejects(f.session.refresh(), (error: unknown) => error instanceof ApiError && error.status === 429);
    assert.deepEqual(refreshTokens, ['refresh-original']);
    assert.equal(f.storage.getItem(sessionStorageKey), saved);
    assert.equal((await f.reload().context()).accountId, 'Bearer original');
    assert.equal((await f.session.context()).accountId, 'Bearer original');
    assert.equal(expired, 0);
    await f.session.refresh();
    assert.equal((await f.session.context()).accountId, 'Bearer rotated');
    await f.session.refresh();
    assert.deepEqual(refreshTokens, ['refresh-original', 'refresh-original', 'refresh-rotated']);
    assert.equal((await f.session.context()).accountId, 'Bearer next');
    assert.equal(expired, 0);
  } finally { await f.close(); }
});

test('logout sends the token pair and its late response cannot erase a later login', async () => {
  const logoutStarted = barrier(); const finishLogout = barrier();
  let loginCount = 0;
  const f = await fixture(async (request, response) => {
    if (request.url === '/auth/v1/login') {
      assert.equal(request.headers.authorization, undefined);
      json(response, tokens(`account-${++loginCount}`)); return;
    }
    if (request.url === '/auth/v1/logout') {
      assert.equal(request.headers.authorization, 'Bearer account-1');
      assert.deepEqual(await body(request), { refreshToken: 'refresh-account-1' });
      logoutStarted.resolve(); await finishLogout.promise;
      response.writeHead(204); response.end(); return;
    }
    if (request.url === '/auth/v1/context') {
      json(response, { accountId: request.headers.authorization }); return;
    }
    assert.fail('unexpected refresh after a successful login');
  });
  try {
    await f.session.login('first', 'synthetic-password');
    const logout = f.session.logout(); await logoutStarted.promise;
    assert.equal(f.storage.getItem(sessionStorageKey), null);
    await f.session.login('second', 'synthetic-password');
    const saved = f.storage.getItem(sessionStorageKey);
    finishLogout.resolve(); await logout;
    assert.equal((await f.session.context()).accountId, 'Bearer account-2');
    assert.equal(f.storage.getItem(sessionStorageKey), saved);
    assert.equal((await f.reload().context()).accountId, 'Bearer account-2');
  } finally { await f.close(); }
});

test('a successful old query is discarded after the session is cleared', async () => {
  const queryStarted = barrier(); const finishQuery = barrier(); let delayed = false;
  const f = await fixture(async (request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('old-access')); return; }
    if (delayed) { queryStarted.resolve(); await finishQuery.promise; }
    json(response, { accountId: 'old-account' });
  });
  try {
    await f.session.login('operator', 'synthetic-password'); delayed = true;
    const query = f.session.context(); const rejected = assert.rejects(query, (error: unknown) => error instanceof ApiError && error.code === 'SESSION_CHANGED');
    await queryStarted.promise; f.session.clear(); finishQuery.resolve(); await rejected;
  } finally { await f.close(); }
});

test('malformed successful responses never expose response fragments', async () => {
  const f = await fixture((_request, response) => { response.writeHead(200); response.end('sensitive-response-fragment'); });
  try {
    await assert.rejects(f.session.login('operator', 'synthetic-password'), (error: unknown) => {
      assert.ok(error instanceof ApiError); assert.equal(error.code, 'INVALID_RESPONSE');
      assert.equal(error.message.includes('sensitive-response-fragment'), false); return true;
    });
  } finally { await f.close(); }
});

test('management commands do not automatically retry after authentication failures', async () => {
  let writes = 0;
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('access')); return; }
    if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
    assert.equal(request.url, '/iam/v1/accounts');
    assert.equal(request.headers['idempotency-key'], 'command-key-must-be-preserved');
    writes++; legacy(response, 'OPERATIONS_SESSION_INVALID', 'private legacy diagnostic', 401);
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    await assert.rejects(f.session.request('/iam/v1/accounts', {
      method: 'POST', body: '{}', headers: new Headers({ 'Idempotency-Key': 'command-key-must-be-preserved' }),
    }), ApiError);
    assert.equal(writes, 1);
  } finally { await f.close(); }
});

test('an empty session requires login without sending browser credentials', async () => {
  let requests = 0;
  const f = await fixture((_request, response) => { requests++; response.writeHead(500); response.end(); });
  try {
    await assert.rejects(f.session.context(), (error: unknown) => error instanceof ApiError && error.status === 401);
    await assert.rejects(f.session.refresh(), ApiError);
    assert.equal(requests, 0);
  } finally { await f.close(); }
});

test('reload restores the token pair without extending its absolute expiry or refreshing valid access', async t => {
  const now = Date.now();
  t.mock.timers.enable({ apis: ['Date'], now });
  const storage = memoryStorage(); const saved = savedTokens('original');
  storage.setItem(sessionStorageKey, JSON.stringify(saved));
  const requests: string[] = [];
  const f = await fixture((request, response) => {
    requests.push(request.url!);
    assert.equal(request.url, '/auth/v1/context');
    json(response, { accountId: request.headers.authorization });
  }, storage);
  try {
    assert.equal((await f.session.context()).accountId, 'Bearer original');
    t.mock.timers.setTime(now + 30_000);
    const reloaded = f.reload();
    assert.equal((await reloaded.context()).accountId, 'Bearer original');
    assert.deepEqual(JSON.parse(storage.getItem(sessionStorageKey)!), saved);
    t.mock.timers.setTime(saved.refreshExpiresAt);
    await assert.rejects(reloaded.context(), (error: unknown) => error instanceof ApiError && error.status === 401);
    assert.equal(storage.getItem(sessionStorageKey), null);
    assert.deepEqual(requests, ['/auth/v1/context', '/auth/v1/context']);
  } finally { await f.close(); }
});

test('restored expired access rotates with a valid refresh token and saves the new pair', async () => {
  const storage = memoryStorage();
  storage.setItem(sessionStorageKey, JSON.stringify({ ...savedTokens('original'), accessExpiresAt: Date.now() - 1 }));
  let rotations = 0;
  const f = await fixture(async (request, response) => {
    if (request.url === '/auth/v1/refresh') {
      rotations++;
      assert.equal(storage.getItem(sessionStorageKey), null);
      assert.deepEqual(await body(request), { refreshToken: 'refresh-original' });
      json(response, tokens('rotated')); return;
    }
    assert.equal(request.url, '/auth/v1/context');
    json(response, { accountId: request.headers.authorization });
  }, storage);
  try {
    const started = Date.now();
    assert.equal((await f.session.context()).accountId, 'Bearer rotated');
    const saved = JSON.parse(storage.getItem(sessionStorageKey)!);
    assert.equal(saved.version, 1); assert.equal(saved.accessToken, 'rotated');
    assert.equal(saved.refreshToken, 'refresh-rotated');
    assert.ok(saved.accessExpiresAt >= started + 900_000 && saved.accessExpiresAt <= Date.now() + 900_000);
    assert.ok(saved.refreshExpiresAt >= started + 28_800_000 && saved.refreshExpiresAt <= Date.now() + 28_800_000);
    assert.equal((await f.reload().context()).accountId, 'Bearer rotated');
    assert.deepEqual(JSON.parse(storage.getItem(sessionStorageKey)!), saved);
    assert.equal(rotations, 1);
  } finally { await f.close(); }
});

test('expired or invalid saved sessions are removed without requests or unrelated storage changes', async () => {
  let requests = 0;
  const f = await fixture((_request, response) => { requests++; json(response, {}, 500); });
  f.storage.setItem('unrelated-preference', 'keep');
  const valid = savedTokens('original');
  try {
    for (const raw of ['', '{', 'null', '[]', 'false', '{}',
      JSON.stringify({ ...valid, version: 2 }), JSON.stringify({ ...valid, refreshToken: '' }),
      JSON.stringify({ ...valid, accessToken: null }), JSON.stringify({ ...valid, accessExpiresAt: '123' }),
      JSON.stringify({ ...valid, accessExpiresAt: 1.5 }), JSON.stringify({ ...valid, refreshExpiresAt: Date.now() - 1 }),
    ]) {
      f.storage.setItem(sessionStorageKey, raw);
      await assert.rejects(f.reload().context(), (error: unknown) => error instanceof ApiError && error.status === 401);
      assert.equal(f.storage.getItem(sessionStorageKey), null, `invalid snapshot retained: ${raw}`);
      assert.equal(f.storage.getItem('unrelated-preference'), 'keep');
    }
    assert.equal(requests, 0);
  } finally { await f.close(); }
});

test('password commands and refresh authentication rejection remove the saved session', async () => {
  for (const path of ['/auth/v1/password', '/auth/v1/bootstrap/administrator', '/auth/v1/refresh']) {
    let expired = 0;
    const f = await fixture((request, response) => {
      if (request.url === '/auth/v1/login') { json(response, tokens('original')); return; }
      if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
      assert.equal(request.url, path);
      if (path === '/auth/v1/refresh') { legacy(response, 'OPERATIONS_SESSION_INVALID', 'private legacy diagnostic', 401); return; }
      response.writeHead(204); response.end();
    });
    f.session.onExpired = () => { expired++; };
    try {
      await f.session.login('operator', 'synthetic-password');
      assert.notEqual(f.storage.getItem(sessionStorageKey), null);
      if (path === '/auth/v1/refresh') {
        await assert.rejects(f.session.refresh(), (error: unknown) => error instanceof ApiError && error.status === 401);
      } else await f.session.request(path, { method: 'POST', body: '{}' });
      assert.equal(f.storage.getItem(sessionStorageKey), null);
      assert.equal(expired, 1);
      await assert.rejects(f.reload().context(), ApiError);
    } finally { await f.close(); }
  }
});

test('an uncertain refresh failure keeps current memory but cannot restore an old generation after reload', async () => {
  let rotations = 0; let expired = 0;
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('original')); return; }
    if (request.url === '/auth/v1/context') {
      json(response, { accountId: request.headers.authorization }); return;
    }
    assert.equal(request.url, '/auth/v1/refresh');
    rotations++; legacy(response, 'SERVICE_UNAVAILABLE', 'private legacy diagnostic', 503);
  });
  f.session.onExpired = () => { expired++; };
  try {
    await f.session.login('operator', 'synthetic-password');
    await assert.rejects(f.session.refresh(), (error: unknown) => error instanceof ApiError && error.status === 503);
    assert.equal((await f.session.context()).accountId, 'Bearer original');
    assert.equal(f.storage.getItem(sessionStorageKey), null);
    await assert.rejects(f.reload().context(), ApiError);
    assert.equal(rotations, 1); assert.equal(expired, 0);
  } finally { await f.close(); }
});

test('failed storage writes keep the current rotated token usable without leaving old saved credentials', async () => {
  const backing = memoryStorage();
  backing.setItem(sessionStorageKey, JSON.stringify(savedTokens('original')));
  const storage = { ...backing, setItem: () => { throw new DOMException('Storage full', 'QuotaExceededError'); } };
  const requests: string[] = [];
  const f = await fixture(async (request, response) => {
    requests.push(request.url!);
    if (request.url === '/auth/v1/refresh') {
      assert.deepEqual(await body(request), { refreshToken: 'refresh-original' });
      json(response, tokens('rotated')); return;
    }
    assert.equal(request.url, '/auth/v1/context');
    json(response, { accountId: request.headers.authorization });
  }, storage);
  try {
    await f.session.refresh();
    assert.equal(backing.getItem(sessionStorageKey), null);
    assert.equal((await f.session.context()).accountId, 'Bearer rotated');
    await assert.rejects(f.reload().context(), ApiError);
    assert.deepEqual(requests, ['/auth/v1/refresh', '/auth/v1/context']);
  } finally { await f.close(); }
});

test('an old login response cannot replace a newer login', async () => {
  const firstStarted = barrier(); const finishFirst = barrier();
  const f = await fixture(async (request, response) => {
    if (request.url === '/auth/v1/login') {
      const credentials = await body(request);
      if (credentials.loginIdentifier === 'first') {
        firstStarted.resolve(); await finishFirst.promise;
      }
      json(response, tokens(String(credentials.loginIdentifier))); return;
    }
    json(response, { accountId: request.headers.authorization });
  });
  try {
    const first = f.session.login('first', 'synthetic-password');
    const rejected = assert.rejects(first, changedSession);
    await firstStarted.promise;
    await f.session.login('second', 'synthetic-password');
    const saved = f.storage.getItem(sessionStorageKey);
    finishFirst.resolve(); await rejected;
    assert.equal((await f.session.context()).accountId, 'Bearer second');
    assert.equal(f.storage.getItem(sessionStorageKey), saved);
    assert.equal((await f.reload().context()).accountId, 'Bearer second');
  } finally { await f.close(); }
});

test('a new session never joins or accepts the old session refresh flight', async () => {
  const oldStarted = barrier(); const finishOld = barrier();
  let loginCount = 0; const refreshes: unknown[] = [];
  const f = await fixture(async (request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens(`account-${++loginCount}`)); return; }
    if (request.url === '/auth/v1/refresh') {
      const input = await body(request); refreshes.push(input.refreshToken);
      if (input.refreshToken === 'refresh-account-1') {
        oldStarted.resolve(); await finishOld.promise; json(response, tokens('old-late')); return;
      }
      assert.equal(input.refreshToken, 'refresh-account-2');
      json(response, tokens('current-rotated')); return;
    }
    json(response, { accountId: request.headers.authorization });
  });
  try {
    await f.session.login('first', 'synthetic-password');
    const old = f.session.refresh(); const rejected = assert.rejects(old, changedSession);
    await oldStarted.promise;
    await f.session.login('second', 'synthetic-password');
    await f.session.refresh();
    const saved = f.storage.getItem(sessionStorageKey);
    finishOld.resolve(); await rejected;
    assert.deepEqual(refreshes, ['refresh-account-1', 'refresh-account-2']);
    assert.equal((await f.session.context()).accountId, 'Bearer current-rotated');
    assert.equal(f.storage.getItem(sessionStorageKey), saved);
    assert.equal((await f.reload().context()).accountId, 'Bearer current-rotated');
  } finally { await f.close(); }
});

test('an old refresh failure stays silent after a new login', async () => {
  const started = barrier(); const finish = barrier();
  let refreshes = 0; let expired = 0;
  const f = await fixture(async (request, response) => {
    if (request.url === '/auth/v1/login') {
      json(response, tokens(String((await body(request)).loginIdentifier))); return;
    }
    if (request.url === '/auth/v1/context') {
      json(response, { accountId: request.headers.authorization }); return;
    }
    assert.equal(request.url, '/auth/v1/refresh');
    assert.deepEqual(await body(request), { refreshToken: 'refresh-first' });
    refreshes++; started.resolve(); await finish.promise;
    legacy(response, 'SERVICE_UNAVAILABLE', 'private legacy diagnostic', 503, 'old-refresh');
  });
  f.session.onExpired = () => { expired++; };
  try {
    await f.session.login('first', 'synthetic-password');
    const rejected = assert.rejects(f.session.refresh(), changedSession);
    await started.promise;
    await f.session.login('second', 'synthetic-password');
    const saved = f.storage.getItem(sessionStorageKey);
    finish.resolve(); await rejected;
    assert.equal(refreshes, 1); assert.equal(expired, 0);
    assert.equal(f.storage.getItem(sessionStorageKey), saved);
    assert.equal((await f.session.context()).accountId, 'Bearer second');
    assert.equal((await f.reload().context()).accountId, 'Bearer second');
  } finally { finish.resolve(); await f.close(); }
});

for (const failure of ['503', 'network']) {
  test(`an old logout ${failure} failure stays silent after a new login`, async () => {
    const started = barrier(); const finish = barrier();
    let logouts = 0; let expired = 0;
    const f = await fixture(async (request, response) => {
      if (request.url === '/auth/v1/login') {
        json(response, tokens(String((await body(request)).loginIdentifier))); return;
      }
      if (request.url === '/auth/v1/context') {
        json(response, { accountId: request.headers.authorization }); return;
      }
      assert.equal(request.url, '/auth/v1/logout');
      assert.equal(request.headers.authorization, 'Bearer first');
      assert.deepEqual(await body(request), { refreshToken: 'refresh-first' });
      logouts++; started.resolve(); await finish.promise;
      if (failure === 'network') response.destroy();
      else legacy(response, 'SERVICE_UNAVAILABLE', 'private legacy diagnostic', 503, 'old-logout');
    });
    f.session.onExpired = () => { expired++; };
    try {
      await f.session.login('first', 'synthetic-password');
      const rejected = assert.rejects(f.session.logout(), changedSession);
      await started.promise;
      assert.equal(f.storage.getItem(sessionStorageKey), null);
      await f.session.login('second', 'synthetic-password');
      const saved = f.storage.getItem(sessionStorageKey);
      finish.resolve(); await rejected;
      assert.equal(logouts, 1); assert.equal(expired, 1);
      assert.equal(f.storage.getItem(sessionStorageKey), saved);
      assert.equal((await f.session.context()).accountId, 'Bearer second');
      assert.equal((await f.reload().context()).accountId, 'Bearer second');
    } finally { finish.resolve(); await f.close(); }
  });

  test(`the current logout ${failure} failure remains reportable after local cleanup`, async () => {
    let logouts = 0; let expired = 0;
    const f = await fixture((request, response) => {
      if (request.url === '/auth/v1/login') { json(response, tokens('current')); return; }
      if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
      assert.equal(request.url, '/auth/v1/logout');
      logouts++;
      if (failure === 'network') response.destroy();
      else legacy(response, 'SERVICE_UNAVAILABLE', 'private legacy diagnostic', 503, 'current-logout');
    });
    f.session.onExpired = () => { expired++; };
    try {
      await f.session.login('operator', 'synthetic-password');
      await assert.rejects(f.session.logout(), (error: unknown) => failure === 'network'
        ? error instanceof TypeError
        : error instanceof ApiError && error.status === 503 && error.code === 'SERVICE_UNAVAILABLE'
          && error.requestId === 'current-logout');
      assert.equal(logouts, 1); assert.equal(expired, 1);
      assert.equal(f.storage.getItem(sessionStorageKey), null);
      await assert.rejects(f.session.context(), (error: unknown) =>
        error instanceof ApiError && error.code === 'OPERATIONS_SESSION_INVALID');
    } finally { await f.close(); }
  });
}

for (const path of ['/auth/v1/password', '/auth/v1/bootstrap/administrator']) {
  test(`${path} success prevents an older refresh response from restoring the session`, async () => {
    const refreshStarted = barrier(); const finishRefresh = barrier();
    const f = await fixture(async (request, response) => {
      if (request.url === '/auth/v1/login') { json(response, tokens('old')); return; }
      if (request.url === '/auth/v1/refresh') {
        refreshStarted.resolve(); await finishRefresh.promise; json(response, tokens('late')); return;
      }
      if (request.url === path) { response.writeHead(204); response.end(); return; }
      json(response, { accountId: 'old' });
    });
    try {
      await f.session.login('operator', 'synthetic-password');
      const refresh = f.session.refresh(); const rejected = assert.rejects(refresh, changedSession);
      await refreshStarted.promise;
      await f.session.request(path, { method: 'POST', body: '{}' });
      finishRefresh.resolve(); await rejected;
      await assert.rejects(f.session.context(), ApiError);
      assert.equal(f.storage.getItem(sessionStorageKey), null);
      await assert.rejects(f.reload().context(), ApiError);
    } finally { await f.close(); }
  });
}

test('logout waits for an in-flight rotation and revokes with the resulting pair', async () => {
  const refreshStarted = barrier(); const finishRefresh = barrier();
  const f = await fixture(async (request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('old')); return; }
    if (request.url === '/auth/v1/refresh') {
      refreshStarted.resolve(); await finishRefresh.promise; json(response, tokens('rotated')); return;
    }
    if (request.url === '/auth/v1/logout') {
      assert.equal(request.headers.authorization, 'Bearer rotated');
      assert.deepEqual(await body(request), { refreshToken: 'refresh-rotated' });
      response.writeHead(204); response.end(); return;
    }
    json(response, { accountId: 'operator' });
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    const refresh = f.session.refresh(); await refreshStarted.promise;
    const logout = f.session.logout(); finishRefresh.resolve();
    await Promise.all([refresh, logout]);
    await assert.rejects(f.session.context(), ApiError);
    assert.equal(f.storage.getItem(sessionStorageKey), null);
  } finally { await f.close(); }
});

test('starting logout prevents a new rotation from racing its captured refresh token', async () => {
  let logoutCalls = 0;
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('current')); return; }
    if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
    assert.equal(request.url, '/auth/v1/logout');
    logoutCalls++; response.writeHead(204); response.end();
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    const logout = f.session.logout();
    await assert.rejects(f.session.refresh(), changedSession);
    await logout;
    assert.equal(logoutCalls, 1);
  } finally { await f.close(); }
});

test('GET authentication rejection retries once and clears credentials only for a final 401', async () => {
  for (const status of [401, 429, 503]) {
    let rotations = 0; let queries = 0; let expired = 0;
    const f = await fixture((request, response) => {
      if (request.url === '/auth/v1/login') { json(response, tokens('old')); return; }
      if (request.url === '/auth/v1/context') { json(response, { accountId: request.headers.authorization }); return; }
      if (request.url === '/auth/v1/refresh') { rotations++; json(response, tokens('rotated')); return; }
      assert.equal(request.url, '/iam/v1/accounts');
      queries++; legacy(response, 'OPERATIONS_SESSION_INVALID', 'private legacy diagnostic', queries === 1 ? 401 : status);
    });
    f.session.onExpired = () => { expired++; };
    try {
      await f.session.login('operator', 'synthetic-password');
      await assert.rejects(f.session.request('/iam/v1/accounts'), (error: unknown) =>
        error instanceof ApiError && error.status === status);
      assert.equal(rotations, 1); assert.equal(queries, 2);
      assert.equal(expired, status === 401 ? 1 : 0);
      if (status === 401) {
        assert.equal(f.storage.getItem(sessionStorageKey), null);
        await assert.rejects(f.reload().context(), ApiError);
      } else assert.equal((await f.reload().context()).accountId, 'Bearer rotated');
    } finally { await f.close(); }
  }
});

test('a late retried query 401 cannot clear a newer login or its saved credentials', async () => {
  const retryStarted = barrier(); const finishRetry = barrier();
  let queries = 0; let rotations = 0; let expired = 0;
  const f = await fixture(async (request, response) => {
    if (request.url === '/auth/v1/login') {
      json(response, tokens(String((await body(request)).loginIdentifier))); return;
    }
    if (request.url === '/auth/v1/context') { json(response, { accountId: request.headers.authorization }); return; }
    if (request.url === '/auth/v1/refresh') { rotations++; json(response, tokens('rotated')); return; }
    assert.equal(request.url, '/iam/v1/accounts');
    if (++queries === 2) { retryStarted.resolve(); await finishRetry.promise; }
    legacy(response, 'OPERATIONS_SESSION_INVALID', 'private legacy diagnostic', 401);
  });
  f.session.onExpired = () => { expired++; };
  try {
    await f.session.login('first', 'synthetic-password');
    const query = f.session.request('/iam/v1/accounts');
    const rejected = assert.rejects(query, changedSession);
    await retryStarted.promise;
    await f.session.login('second', 'synthetic-password');
    const saved = f.storage.getItem(sessionStorageKey);
    finishRetry.resolve(); await rejected;
    assert.equal(rotations, 1); assert.equal(queries, 2);
    assert.equal(expired, 0); assert.equal(f.storage.getItem(sessionStorageKey), saved);
    assert.equal((await f.reload().context()).accountId, 'Bearer second');
  } finally { await f.close(); }
});

test('both tokens must be present before an authentication response becomes usable', async () => {
  const f = await fixture((_request, response) => json(response, {
    accessToken: 'partial-access', tokenType: 'Bearer', expiresIn: 900,
  }));
  try {
    await assert.rejects(f.session.login('operator', 'synthetic-password'),
      (error: unknown) => error instanceof ApiError && error.code === 'INVALID_AUTHENTICATION_RESPONSE');
    await assert.rejects(f.session.context(), ApiError);
  } finally { await f.close(); }
});

test('independent storage instances do not share tokens or logout state', async () => {
  const f = await fixture(async (request, response) => {
    if (request.url === '/auth/v1/login') {
      json(response, tokens(String((await body(request)).loginIdentifier))); return;
    }
    json(response, { accountId: request.headers.authorization });
  });
  const second = await fixture((_request, response) => json(response, { accountId: 'unexpected' }));
  try {
    await f.session.login('first', 'synthetic-password');
    await assert.rejects(second.session.context(), ApiError);
    second.session.clear();
    assert.equal((await f.session.context()).accountId, 'Bearer first');
  } finally { await Promise.all([f.close(), second.close()]); }
});

test('common business errors preserve safe messages for form commands', async () => {
  for (const failure of [
    { path: '/auth/v1/password', method: 'POST', status: 400, code: 'INVALID_REQUEST', message: '新密码不能与当前密码相同' },
    { path: '/auth/v1/bootstrap/administrator', method: 'POST', status: 400, code: 'BUSINESS_CHECK_FAILED', message: '请先完成初始化账号改密' },
    { path: '/iam/v1/accounts/operator/disable', method: 'POST', status: 400, code: 'BUSINESS_CHECK_FAILED', message: '不能停用当前登录账号' },
    { path: '/iam/v1/accounts/operator/roles', method: 'PUT', status: 409, code: 'CONCURRENT_REQUEST', message: '人员角色已更新，请重新加载后分配' },
  ]) {
    const f = await fixture((request, response) => {
      if (request.url === '/auth/v1/login') { json(response, tokens('operator')); return; }
      if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
      legacy(response, failure.code, failure.message, failure.status, 'business-request');
    });
    try {
      await f.session.login('operator', 'synthetic-password');
      await assert.rejects(f.session.request(failure.path, { method: failure.method, body: '{}' }), (error: unknown) =>
        error instanceof ApiError && error.status === failure.status && error.code === failure.code
        && error.message === failure.message && error.requestId === 'business-request');
      assert.equal((await f.session.context()).accountId, 'operator');
    } finally { await f.close(); }
  }
});

test('unknown codes, mismatched statuses and system failures never expose remote messages', async () => {
  for (const failure of [
    { status: 400, code: 'UNRECOGNIZED_ERROR', expected: "The action didn't complete. Check your input and try again." },
    { status: 400, code: 'CONCURRENT_REQUEST', expected: "The action didn't complete. Check your input and try again." },
    { status: 409, code: 'BUSINESS_CHECK_FAILED', expected: "The data has changed. Refresh and try again." },
    { status: 401, code: 'BUSINESS_CHECK_FAILED', expected: "Your session has expired. Please sign in again." },
    { status: 403, code: 'INVALID_REQUEST', expected: "You don't have permission to perform this action." },
    { status: 500, code: 'BUSINESS_CHECK_FAILED', expected: "The service is temporarily unavailable. Please try again later." },
    { status: 502, code: 'INVALID_REQUEST', expected: "The service is temporarily unavailable. Please try again later." },
    { status: 503, code: 'CONCURRENT_REQUEST', expected: "The service is temporarily unavailable. Please try again later." },
  ]) {
    const f = await fixture((request, response) => {
      if (request.url === '/auth/v1/login') { json(response, tokens('operator')); return; }
      if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
      legacy(response, failure.code, 'internal-sensitive-detail', failure.status);
    });
    try {
      await f.session.login('operator', 'synthetic-password');
      await assert.rejects(f.session.request('/iam/v1/accounts', { method: 'POST', body: '{}' }), (error: unknown) =>
        error instanceof ApiError && error.status === failure.status && error.message === failure.expected);
    } finally { await f.close(); }
  }
});

test('empty, malformed and invalid-shaped error bodies safely fall back to an ApiError', async () => {
  for (const payload of [
    '', '{', 'null', '[]', '"remote-message"', 'false', '{}',
    '[{"code":"INVALID_REQUEST","message":"remote-message"}]',
    '{"code":123,"requestId":{},"message":"remote-message"}',
    '{"code":"INVALID_REQUEST","message":null}',
    '{"code":"INVALID_REQUEST","message":{}}',
    '{"code":"INVALID_REQUEST","message":""}',
    '{"code":"INVALID_REQUEST","message":" \\n "}',
  ]) {
    const f = await fixture((request, response) => {
      if (request.url === '/auth/v1/login') { json(response, tokens('operator')); return; }
      if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
      response.writeHead(400, { 'Content-Type': 'application/json' }); response.end(payload);
    });
    try {
      await f.session.login('operator', 'synthetic-password');
      await assert.rejects(f.session.request('/iam/v1/accounts', { method: 'POST', body: '{}' }), (error: unknown) =>
        error instanceof ApiError && error.status === 400 && error.requestId === ''
        && error.message === "The action didn't complete. Check your input and try again.");
    } finally { await f.close(); }
  }
});

test('invalid error bodies retain only a separately valid response header for diagnostic correlation', async () => {
  for (const failure of [
    { body: '{"code":"BUSINESS_CHECK_FAILED","requestId":"body-request"}', header: 'header-request' },
    { body: '{"code":"BUSINESS_CHECK_FAILED","requestId":"invalid id"}', header: 'header-request' },
    { body: '<html>proxy failure</html>', header: 'header-request' },
    { body: '{"code":"BUSINESS_CHECK_FAILED","requestId":"body-request"}', header: 'bad header id' },
  ]) {
    const f = await fixture((_request, response) => {
      response.writeHead(400, { 'Content-Type': 'application/problem+json', 'X-Request-Id': failure.header });
      response.end(failure.body);
    });
    try {
      await assert.rejects(f.session.login('operator', 'synthetic-password'), (error: unknown) =>
        error instanceof ApiError && error.code === 'REQUEST_FAILED'
        && error.requestId === (failure.header === 'header-request' ? 'header-request' : ''));
    } finally { await f.close(); }
  }
});

test('invalid successful JSON preserves header correlation without exposing the response body', async () => {
  const f = await fixture((_request, response) => {
    response.writeHead(200, { 'X-Request-Id': 'invalid-response-request' });
    response.end('sensitive-response-fragment');
  });
  try {
    await assert.rejects(f.session.login('operator', 'synthetic-password'), (error: unknown) =>
      error instanceof ApiError && error.code === 'INVALID_RESPONSE'
      && error.requestId === 'invalid-response-request' && !error.message.includes('sensitive-response-fragment'));
  } finally { await f.close(); }
});

test('login exposes safe input errors but never the credential rejection message', async () => {
  for (const failure of [
    { status: 400, code: 'INVALID_REQUEST', message: '请输入有效邮箱', expected: '请输入有效邮箱' },
    { status: 400, code: 'BUSINESS_CHECK_FAILED', message: '请输入登录账号', expected: '请输入登录账号' },
    { status: 401, code: 'OPERATIONS_SESSION_INVALID', message: 'synthetic-account-does-not-exist', expected: "Your session has expired. Please sign in again." },
    { status: 401, code: 'BUSINESS_CHECK_FAILED', message: 'synthetic-account-disabled', expected: "Your session has expired. Please sign in again." },
  ]) {
    const f = await fixture((_request, response) =>
      legacy(response, failure.code, failure.message, failure.status));
    try {
      await assert.rejects(f.session.login('operator', 'synthetic-password'), (error: unknown) =>
        error instanceof ApiError && error.status === failure.status && error.message === failure.expected);
    } finally { await f.close(); }
  }
});

test('a valid Problem response with extensions becomes one safe ApiError', async () => {
  const f = await fixture((_request, response) => problem(response, {
    ...validProblem(400, 'BUSINESS_CHECK_FAILED', '可以安全展示的业务说明', 'problem-42'),
    errors: [{ field: 'password', message: '可以安全展示的字段说明' }],
    futureDiagnostic: { never: 'rendered' },
  }, 400, 'problem-42', 'application/problem+json; charset=utf-8'));
  try {
    await assert.rejects(f.session.login('operator', 'synthetic-password'), (error: unknown) =>
      error instanceof ApiError && error.status === 400 && error.code === 'BUSINESS_CHECK_FAILED'
      && error.requestId === 'problem-42' && error.message === '可以安全展示的业务说明');
  } finally { await f.close(); }
});

test('a valid legacy application/json error converts immediately to the same safe ApiError', async () => {
  const f = await fixture((_request, response) => {
    response.writeHead(409, { 'Content-Type': 'application/json; charset=utf-8', 'X-Request-Id': 'unrelated-header' });
    response.end(JSON.stringify({ code: 'CONCURRENT_REQUEST', message: '可以安全展示的旧说明',
      requestId: ':legacy.request-42', timestamp: '2026-09-06T10:20:30.123Z' }));
  });
  try {
    await assert.rejects(f.session.login('operator', 'synthetic-password'), (error: unknown) =>
      error instanceof ApiError && error.status === 409 && error.code === 'CONCURRENT_REQUEST'
      && error.requestId === ':legacy.request-42' && error.message === '可以安全展示的旧说明');
  } finally { await f.close(); }
});

test('invalid Problem and legacy responses never expose remote content or let an invalid body ID win', async () => {
  const invalidProblems: Array<{ name: string; contentType: string; body: string; header?: string }> = [
    { name: 'empty', contentType: 'application/problem+json', body: '' },
    { name: 'malformed', contentType: 'application/problem+json', body: '{' },
    { name: 'wrong media type', contentType: 'text/plain', body: JSON.stringify(validProblem()) },
    { name: 'new body under legacy media type', contentType: 'application/json', body: JSON.stringify(validProblem()) },
    { name: 'legacy body under Problem media type', contentType: 'application/problem+json',
      body: JSON.stringify({ code: 'BUSINESS_CHECK_FAILED', message: 'remote-secret', requestId: 'body-request', timestamp: '2026-09-06T10:20:30Z' }) },
    { name: 'bad type', contentType: 'application/problem+json', body: JSON.stringify({ ...validProblem(), type: 'https://example.invalid/problem' }) },
    { name: 'bad title', contentType: 'application/problem+json', body: JSON.stringify({ ...validProblem(), title: 'Business failure' }) },
    { name: 'blank detail', contentType: 'application/problem+json', body: JSON.stringify({ ...validProblem(), detail: ' \n ' }) },
    { name: 'missing body ID', contentType: 'application/problem+json',
      body: JSON.stringify({ type: 'about:blank', title: 'Bad Request', detail: 'remote-secret',
        code: 'BUSINESS_CHECK_FAILED', timestamp: '2026-09-06T10:20:30.123456Z' }) },
    { name: 'bad body ID', contentType: 'application/problem+json', body: JSON.stringify({ ...validProblem(), requestId: 'bad id' }) },
    { name: 'bad timestamp', contentType: 'application/problem+json', body: JSON.stringify({ ...validProblem(), timestamp: '2026-09-06T10:20:30.123Z' }) },
    { name: 'forbidden status', contentType: 'application/problem+json', body: JSON.stringify({ ...validProblem(), status: 400 }) },
    { name: 'forbidden instance', contentType: 'application/problem+json', body: JSON.stringify({ ...validProblem(), instance: '/private' }) },
    { name: 'forbidden legacy message', contentType: 'application/problem+json', body: JSON.stringify({ ...validProblem(), message: 'remote-secret' }) },
    { name: 'empty errors', contentType: 'application/problem+json', body: JSON.stringify({ ...validProblem(), errors: [] }) },
    { name: 'invalid errors', contentType: 'application/problem+json', body: JSON.stringify({ ...validProblem(), errors: [{ field: ' ', message: 'remote-secret', extra: true }] }) },
    { name: 'ID mismatch', contentType: 'application/problem+json', body: JSON.stringify(validProblem(400, 'BUSINESS_CHECK_FAILED', 'remote-secret', 'body-request')) },
  ];
  for (const failure of invalidProblems) {
    const f = await fixture((_request, response) => {
      response.writeHead(400, { 'Content-Type': failure.contentType, 'X-Request-Id': failure.header ?? 'header-request' });
      response.end(failure.body);
    });
    try {
      await assert.rejects(f.session.login('operator', 'synthetic-password'), (error: unknown) =>
        error instanceof ApiError && error.code === 'REQUEST_FAILED' && error.requestId === 'header-request'
        && error.message === "The action didn't complete. Check your input and try again.", failure.name);
    } finally { await f.close(); }
  }
});

test('Problem headers must be one valid exact body correlation ID', async () => {
  for (const header of ['', 'bad header id', 'problem-request, second-request']) {
    const f = await fixture((_request, response) => problem(response, validProblem(), 400, header));
    try {
      await assert.rejects(f.session.login('operator', 'synthetic-password'), (error: unknown) =>
        error instanceof ApiError && error.code === 'REQUEST_FAILED' && error.requestId === '');
    } finally { await f.close(); }
  }
});

for (const requestId of ['a', `a${'b'.repeat(63)}`]) {
  test(`Problem accepts request-ID boundary ${requestId.length}`, async () => {
    const error = await rawError('application/problem+json',
      JSON.stringify(validProblem(400, 'BUSINESS_CHECK_FAILED', 'safe detail', requestId)), 400, requestId);
    assert.equal(error.code, 'BUSINESS_CHECK_FAILED');
    assert.equal(error.requestId, requestId);
  });
}
for (const requestId of ['_leading', '-leading', 'contains:colon', `a${'b'.repeat(64)}`]) {
  test(`Problem rejects invalid request-ID ${JSON.stringify(requestId)}`, async () => {
    const error = await rawError('application/problem+json',
      JSON.stringify(validProblem(400, 'BUSINESS_CHECK_FAILED', 'safe detail', requestId)), 400, requestId);
    assert.equal(error.code, 'REQUEST_FAILED');
    assert.equal(error.requestId, '');
  });
}

test('legacy JSON requires all old producer fields, its old ID grammar, and a parseable Instant', async () => {
  for (const value of [
    {},
    { code: 'BUSINESS_CHECK_FAILED', message: 'remote-secret', requestId: 'legacy-request' },
    { code: 'BUSINESS_CHECK_FAILED', message: 'remote-secret', requestId: 'bad id', timestamp: '2026-09-06T10:20:30Z' },
    { code: 'BUSINESS_CHECK_FAILED', message: 'remote-secret', requestId: 'legacy-request', timestamp: 'not-an-instant' },
    { code: 'BUSINESS_CHECK_FAILED', message: ' ', requestId: 'legacy-request', timestamp: '2026-09-06T10:20:30Z' },
  ]) {
    const f = await fixture((_request, response) => {
      response.writeHead(400, { 'Content-Type': 'application/json', 'X-Request-Id': 'header-request' });
      response.end(JSON.stringify(value));
    });
    try {
      await assert.rejects(f.session.login('operator', 'synthetic-password'), (error: unknown) =>
        error instanceof ApiError && error.code === 'REQUEST_FAILED' && error.requestId === 'header-request'
        && error.message === "The action didn't complete. Check your input and try again.");
    } finally { await f.close(); }
  }
});

async function rawError(contentType: string, bodyText: string, status = 400, requestId = 'problem-request') {
  const f = await fixture((_request, response) => {
    response.writeHead(status, { 'Content-Type': contentType, 'X-Request-Id': requestId });
    response.end(bodyText);
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    assert.fail('expected a failed response');
  } catch (error) {
    assert.ok(error instanceof ApiError);
    return error;
  } finally { await f.close(); }
}

test('Problem media type accepts legal case-insensitive parameters and quoted escapes', async () => {
  const error = await rawError('APPLICATION/PROBLEM+JSON; charset=UTF-8; note="a\\"b, c"',
    JSON.stringify(validProblem(400, 'BUSINESS_CHECK_FAILED', 'safe detail')), 400);
  assert.equal(error.code, 'BUSINESS_CHECK_FAILED');
  assert.equal(error.message, 'safe detail');
});

for (const contentType of [
  'application/problem+json, application/json',
  'application/problem+json; charset=utf-8, application/json',
  'application/problem+json; charset',
  'application/problem+json; charset=',
  'application/problem+json; charset=utf-8; CHARSET=utf-8',
  'application/problem+json; note="unterminated',
  'application/problem+json; note="valid"junk',
  'application/problem+json; note==value',
]) {
  test(`Problem media type rejects malformed or coalesced value ${JSON.stringify(contentType)}`, async () => {
    const error = await rawError(contentType, JSON.stringify(validProblem()));
    assert.equal(error.code, 'REQUEST_FAILED');
  });
}

test('legacy media type accepts legal parameters without accepting a Problem body', async () => {
  const error = await rawError('Application/JSON; charset="utf-8"; note="a\\\\b"', JSON.stringify({
    code: 'CONCURRENT_REQUEST', message: 'safe legacy detail', requestId: ':legacy-42',
    timestamp: '2026-09-06T10:20:30.123456789Z',
  }), 409);
  assert.equal(error.code, 'CONCURRENT_REQUEST');
  assert.equal(error.message, 'safe legacy detail');
});

for (const timestamp of ['2026-09-06T10:20:30Z', '2026-09-06T10:20:30.1Z', '2026-09-06T10:20:30.123456789Z']) {
  test(`legacy JSON accepts the actual old producer timestamp ${timestamp}`, async () => {
    const error = await rawError('application/json', JSON.stringify({ code: 'BUSINESS_CHECK_FAILED',
      message: 'safe legacy detail', requestId: 'legacy-request', timestamp }));
    assert.equal(error.code, 'BUSINESS_CHECK_FAILED');
    assert.equal(error.message, 'safe legacy detail');
  });
}

test('duplicate JSON keys are rejected before Problem or legacy shape validation', async () => {
  const valid = '{"type":"about:blank","title":"Bad Request","detail":"safe detail","code":"BUSINESS_CHECK_FAILED","requestId":"problem-request","timestamp":"2026-09-06T10:20:30.123456Z"}';
  const duplicateBodies = [
    valid.replace('"type":"about:blank",', '"type":"about:blank","type":"about:blank",'),
    valid.replace('"code":"BUSINESS_CHECK_FAILED",', '"code":"BUSINESS_CHECK_FAILED","\\u0063ode":"DUPLICATE",'),
    valid.replace('}', ',"extension":{"literal":true,"literal":false}}'),
    valid.replace('}', ',"extension":[{"number":1,"number":2}]}'),
    valid.replace('}', ',"errors":[{"field":"password","\\u0066ield":"duplicate","message":"safe"}]}'),
    '{"code":"BUSINESS_CHECK_FAILED","\\u0063ode":"DUPLICATE","message":"safe detail","requestId":"legacy-request","timestamp":"2026-09-06T10:20:30Z"}',
  ];
  for (const [index, bodyText] of duplicateBodies.entries()) {
    const contentType = index === duplicateBodies.length - 1 ? 'application/json' : 'application/problem+json';
    const error = await rawError(contentType, bodyText);
    assert.equal(error.code, 'REQUEST_FAILED', `duplicate vector ${index}`);
    assert.equal(error.requestId, 'problem-request', `duplicate vector ${index}`);
  }
});

test('deep otherwise-valid JSON fails closed before object decoding', async () => {
  let nested = 'true';
  for (let depth = 0; depth < 80; depth++) nested = `{"level${depth}":${nested}}`;
  const raw = '{"type":"about:blank","title":"Bad Request","detail":"safe detail","code":"BUSINESS_CHECK_FAILED","requestId":"problem-request","timestamp":"2026-09-06T10:20:30.123456Z","extension":' + nested + '}';
  const error = await rawError('application/problem+json', raw);
  assert.equal(error.code, 'REQUEST_FAILED');
});

function nestedProblem(depth: number) {
  let nested = 'true';
  for (let level = 0; level < depth; level++) nested = `{"level${level}":${nested}}`;
  return '{"type":"about:blank","title":"Bad Request","detail":"safe detail","code":"BUSINESS_CHECK_FAILED","requestId":"problem-request","timestamp":"2026-09-06T10:20:30.123456Z","extension":' + nested + '}';
}
function problemAtLength(length: number) {
  const prefix = '{"type":"about:blank","title":"Bad Request","detail":"safe detail","code":"BUSINESS_CHECK_FAILED","requestId":"problem-request","timestamp":"2026-09-06T10:20:30.123456Z","extension":"';
  const suffix = '"}';
  assert.ok(length >= prefix.length + suffix.length);
  return prefix + 'a'.repeat(length - prefix.length - suffix.length) + suffix;
}
test('scanner accepts the maximum supported object nesting and JSON length', async () => {
  const nested = await rawError('application/problem+json', nestedProblem(63));
  assert.equal(nested.code, 'BUSINESS_CHECK_FAILED');
  const longest = await rawError('application/problem+json', problemAtLength(1_000_000));
  assert.equal(longest.code, 'BUSINESS_CHECK_FAILED');
});
test('scanner rejects one level or one byte beyond its JSON bounds', async () => {
  const nested = await rawError('application/problem+json', nestedProblem(64));
  assert.equal(nested.code, 'REQUEST_FAILED');
  const tooLong = await rawError('application/problem+json', problemAtLength(1_000_001));
  assert.equal(tooLong.code, 'REQUEST_FAILED');
});

for (const [status, title] of springStatusTitles) {
  if (status === 421) continue;
  test(`valid Problem accepts the Spring HttpStatus title for ${status}`, async () => {
    const requestId = `status-${status}`;
    const error = await rawError('application/problem+json',
      JSON.stringify(validProblem(status, 'STATUS_VALID', 'remote detail', requestId)), status, requestId);
    assert.equal(error.status, status);
    assert.equal(error.code, 'STATUS_VALID');
    assert.equal(error.requestId, requestId);
    assert.equal(title, springStatusTitles.get(status));
  });
}

test('a reused loopback GET retries 421 once on another connection before the Problem reader sees it', async () => {
  const getPorts: number[] = [];
  let gets = 0;
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('operator')); return; }
    if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
    assert.equal(request.url, '/iam/v1/misdirected');
    getPorts.push(request.socket.remotePort!);
    gets++;
    problem(response, validProblem(421, 'MISDIRECTED', 'safe detail', 'misdirected-421'), 421, 'misdirected-421');
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    await f.session.context();
    await assert.rejects(f.session.request('/iam/v1/misdirected'), (error: unknown) =>
      error instanceof ApiError && error.status === 421 && error.code === 'MISDIRECTED'
      && error.requestId === 'misdirected-421');
    assert.equal(gets, 2);
    assert.notEqual(getPorts[0], getPorts[1]);
  } finally { await f.close(); }
});

for (const [status, wrongTitle] of [[416, 'Range Not Satisfiable'], [505, 'HTTP Version Not Supported']] as const) {
  test(`Problem rejects non-Spring title ${JSON.stringify(wrongTitle)} for ${status}`, async () => {
    const requestId = `title-${status}`;
    const error = await rawError('application/problem+json',
      JSON.stringify({ ...validProblem(status, 'STATUS_VALID', 'remote detail', requestId), title: wrongTitle }), status, requestId);
    assert.equal(error.code, 'REQUEST_FAILED');
  });
}

const requiredProblemViolations: Array<[string, (value: Record<string, unknown>) => void]> = [
  ['missing type', value => { delete value.type; }], ['blank type', value => { value.type = ' '; }], ['wrong type', value => { value.type = 1; }],
  ['missing title', value => { delete value.title; }], ['blank title', value => { value.title = ' '; }], ['wrong title type', value => { value.title = 1; }],
  ['missing detail', value => { delete value.detail; }], ['blank detail', value => { value.detail = ' '; }], ['wrong detail type', value => { value.detail = 1; }],
  ['missing code', value => { delete value.code; }], ['blank code', value => { value.code = ' '; }], ['wrong code type', value => { value.code = 1; }],
  ['missing request ID', value => { delete value.requestId; }], ['blank request ID', value => { value.requestId = ' '; }], ['wrong request ID type', value => { value.requestId = 1; }],
  ['missing timestamp', value => { delete value.timestamp; }], ['blank timestamp', value => { value.timestamp = ' '; }], ['wrong timestamp type', value => { value.timestamp = 1; }],
];
for (const [name, violate] of requiredProblemViolations) {
  test(`Problem rejects ${name} as one independent cause`, async () => {
    const value = validProblem(); violate(value);
    const error = await rawError('application/problem+json', JSON.stringify(value));
    assert.equal(error.code, 'REQUEST_FAILED');
  });
}

test('Problem permits an absent errors member', async () => {
  const error = await rawError('application/problem+json', JSON.stringify(validProblem()));
  assert.equal(error.code, 'BUSINESS_CHECK_FAILED');
});

const errorsViolations: Array<[string, (value: Record<string, unknown>) => void]> = [
  ['null', value => { value.errors = null; }], ['empty array', value => { value.errors = []; }], ['non-array', value => { value.errors = {}; }],
  ['null item', value => { value.errors = [null]; }], ['missing field', value => { value.errors = [{ message: 'safe' }]; }],
  ['missing message', value => { value.errors = [{ field: 'name' }]; }], ['blank field', value => { value.errors = [{ field: ' ', message: 'safe' }]; }],
  ['blank message', value => { value.errors = [{ field: 'name', message: ' ' }]; }], ['extra member', value => { value.errors = [{ field: 'name', message: 'safe', code: 'NO' }]; }],
];
for (const [name, violate] of errorsViolations) {
  test(`Problem rejects errors with ${name} as one independent cause`, async () => {
    const value = validProblem(); violate(value);
    const error = await rawError('application/problem+json', JSON.stringify(value));
    assert.equal(error.code, 'REQUEST_FAILED');
  });
}

for (const timestamp of ['2026-01-01T00:00:00.000000Z', '2026-09-06T10:20:30.123456Z', '2026-12-31T23:59:59.999999Z']) {
  test(`Problem accepts canonical microsecond timestamp ${timestamp}`, async () => {
    const error = await rawError('application/problem+json', JSON.stringify({ ...validProblem(), timestamp }));
    assert.equal(error.code, 'BUSINESS_CHECK_FAILED');
  });
}
for (const timestamp of ['2026-02-30T10:20:30Z', '2026-09-06T24:20:30Z',
  '2026-09-06T10:20:30+00:00', '+12026-09-06T10:20:30Z', '2026-09-06T10:20:60Z']) {
  test(`legacy JSON rejects timestamp outside the actual old producer rule ${timestamp}`, async () => {
    const error = await rawError('application/json', JSON.stringify({ code: 'BUSINESS_CHECK_FAILED',
      message: 'remote detail', requestId: 'legacy-request', timestamp }));
    assert.equal(error.code, 'REQUEST_FAILED');
  });
}
for (const timestamp of ['2026-02-30T10:20:30.123456Z', '2026-09-06T24:20:30.123456Z',
  '2026-09-06T10:20:30.12345Z', '2026-09-06T10:20:30.1234567Z', '2026-09-06T10:20:30.123456+00:00']) {
  test(`Problem rejects invalid canonical timestamp ${timestamp}`, async () => {
    const error = await rawError('application/problem+json', JSON.stringify({ ...validProblem(), timestamp }));
    assert.equal(error.code, 'REQUEST_FAILED');
  });
}

for (const [status, code, expected] of [
  [400, 'BUSINESS_CHECK_FAILED', 'safe Problem detail'], [400, 'INVALID_REQUEST', 'safe Problem detail'], [409, 'CONCURRENT_REQUEST', 'safe Problem detail'],
  [401, 'BUSINESS_CHECK_FAILED', "Your session has expired. Please sign in again."], [403, 'INVALID_REQUEST', "You don't have permission to perform this action."],
  [500, 'BUSINESS_CHECK_FAILED', "The service is temporarily unavailable. Please try again later."], [400, 'UNKNOWN_CODE', "The action didn't complete. Check your input and try again."],
  [409, 'BUSINESS_CHECK_FAILED', "The data has changed. Refresh and try again."], [400, 'CONCURRENT_REQUEST', "The action didn't complete. Check your input and try again."],
] as const) {
  test(`Problem safe detail display is bounded for ${status}/${code}`, async () => {
    const requestId = `safe-${status}-${code}`;
    const error = await rawError('application/problem+json',
      JSON.stringify(validProblem(status, code, 'safe Problem detail', requestId)), status, requestId);
    assert.equal(error.message, expected);
  });
}

test('a malformed 401 query still refreshes once and retries without exposing its body', async () => {
  let refreshes = 0; let queries = 0;
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('old')); return; }
    if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
    if (request.url === '/auth/v1/refresh') { refreshes++; json(response, tokens('new')); return; }
    assert.equal(request.url, '/iam/v1/accounts');
    if (++queries === 1) {
      response.writeHead(401, { 'Content-Type': 'application/problem+json', 'X-Request-Id': 'malformed-401' });
      response.end('{"code":"OPERATIONS_SESSION_INVALID"}'); return;
    }
    assert.equal(request.headers.authorization, 'Bearer new'); json(response, { items: [] });
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    assert.deepEqual(await f.session.request('/iam/v1/accounts'), { items: [] });
    assert.equal(refreshes, 1); assert.equal(queries, 2);
  } finally { await f.close(); }
});

test('a successful response decoder fails inside the request-id boundary', async () => {
  let decoderCalls = 0;
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('decoder-access')); return; }
    response.writeHead(200, { 'Content-Type': 'application/json', 'X-Request-Id': 'semantic-200' }); response.end('{"safe":true}');
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    await assert.rejects(f.session.request('/decoded', {}, () => { decoderCalls++; throw new Error('semantic rejection'); }),
      (error: unknown) => error instanceof ApiError && error.status === 502 && error.code === 'INVALID_RESPONSE' && error.requestId === 'semantic-200');
    assert.equal(decoderCalls, 1);
  } finally { await f.close(); }
});

test('duplicate successful JSON keys fail before a supplied decoder can observe a value', async () => {
  let decoderCalls = 0;
  const f = await fixture((request, response) => {
    if (request.url === '/auth/v1/login') { json(response, tokens('duplicate-access')); return; }
    if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
    response.writeHead(200, { 'Content-Type': 'application/json', 'X-Request-Id': 'duplicate-200' }); response.end('{"item":1,"item":2}');
  });
  try {
    await f.session.login('operator', 'synthetic-password');
    await assert.rejects(f.session.request('/duplicate', {}, value => { decoderCalls++; return value; }),
      (error: unknown) => error instanceof ApiError && error.code === 'INVALID_RESPONSE' && error.requestId === 'duplicate-200');
    assert.equal(decoderCalls, 0);
  } finally { await f.close(); }
});
