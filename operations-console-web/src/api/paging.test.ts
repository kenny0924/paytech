import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import type { IncomingMessage, ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import test from 'node:test';
import { ApiError, OperationsSession } from './session.ts';
import { changePaging, defaultPaging, pageSizeOptions, requestAllPages, requestPage } from './paging.ts';
import * as pagingModule from './paging.ts';
import { decodeRuleSetPage } from '../risk/riskResponseValidation.ts';
import { ruleSet } from '../risk/riskFixtures.ts';

function invalidPage(requestId: string) {
  return (error: unknown) => error instanceof ApiError && error.status === 502
    && error.code === 'INVALID_RESPONSE' && error.requestId === requestId;
}
function riskRows(count: number, start = 1) {
  return Array.from({ length: count }, (_, index) => ({ ...ruleSet, ruleSetVersionId: String(start + index), ruleSetId: String(start + index), ruleSetCode: `SET_${start + index}` }));
}

function json(response: ServerResponse, value: unknown, status = 200) {
  response.writeHead(status, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(value));
}
function barrier() {
  let resolve!: () => void;
  const promise = new Promise<void>(done => { resolve = done; });
  return { promise, resolve };
}
async function fixture(handler: (request: IncomingMessage, response: ServerResponse) => void | Promise<void>) {
  const requests: string[] = [];
  const server = createServer((request, response) => {
    if (request.url === '/auth/v1/login') {
      json(response, { accessToken: 'paging-access', refreshToken: 'paging-refresh', tokenType: 'Bearer',
        expiresIn: 900, refreshExpiresIn: 28800 }); return;
    }
    if (request.url === '/auth/v1/context') { json(response, { accountId: 'operator' }); return; }
    assert.equal(request.method, 'GET'); assert.equal(request.headers.authorization, 'Bearer paging-access');
    requests.push(request.url!); void handler(request, response);
  });
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
  const client = new OperationsSession(`http://127.0.0.1:${(server.address() as AddressInfo).port}`);
  await client.login('operator', 'synthetic-password');
  return { client, requests, close: () => new Promise<void>((resolve, reject) => {
    server.close(error => error ? reject(error) : resolve()); server.closeAllConnections();
  }) };
}

test('lists start with ten rows and expose the five approved page sizes', () => {
  assert.deepEqual(defaultPaging, { page: 1, size: 10 });
  assert.deepEqual(pageSizeOptions, [10, 20, 50, 100, 200]);
});

for (const kind of ['extra root', 'extra item', 'requested page', 'changed total', 'truncated final'] as const) {
  test(`strict paging ${kind} fails with the exact rejected response ID`, async () => {
    const allPages = kind === 'changed total' || kind === 'truncated final';
    const rejectedId = kind === 'changed total' ? 'paging-total-200' : kind === 'truncated final' ? 'paging-final-200' : 'paging-200';
    let decoderCalls = 0;
    const f = await fixture((request, response) => {
      const page = Number(new URL(request.url!, 'http://localhost').searchParams.get('page'));
      const first = allPages && page === 1;
      const value = first ? { items: riskRows(200), page: 1, size: 200, total: 201 }
        : kind === 'changed total' ? { items: riskRows(2, 201), page: 2, size: 200, total: 202 }
          : kind === 'truncated final' ? { items: [], page: 2, size: 200, total: 201 }
            : { items: riskRows(1), page: kind === 'requested page' ? 2 : 1, size: 10, total: kind === 'requested page' ? 11 : 1 };
      if (kind === 'extra root') Object.assign(value, { extra: true });
      if (kind === 'extra item') Object.assign(value.items[0]!, { extra: true });
      response.writeHead(200, { 'Content-Type': 'application/json', 'X-Request-Id': first ? 'first-page-200' : rejectedId }); response.end(JSON.stringify(value));
    });
    const decode: typeof decodeRuleSetPage = value => { decoderCalls++; return decodeRuleSetPage(value); };
    try {
      await assert.rejects(allPages ? requestAllPages('/risk', new AbortController().signal, f.client, {}, decode)
        : requestPage('/risk', defaultPaging, undefined, f.client, {}, decode), invalidPage(rejectedId));
      assert.equal(decoderCalls, allPages ? 2 : 1); assert.equal(f.requests.length, allPages ? 2 : 1);
    } finally { await f.close(); }
  });
}

test('requestPage and every requestAllPages page invoke the named decoder and forward encoded filters', async () => {
  const decodedPages: number[] = []; const filters = { pointCode: 'CHECKOUT_PREPARE', ruleSetCode: 'SET /&', status: 'DRAFT' };
  const f = await fixture((request, response) => {
    const query = new URL(request.url!, 'http://localhost').searchParams;
    const page = Number(query.get('page')); const size = Number(query.get('size'));
    json(response, { items: riskRows(Math.min(size, 405 - (page - 1) * size), (page - 1) * size + 1), page, size, total: 405 });
  });
  const decode: typeof decodeRuleSetPage = value => {
    const result = decodeRuleSetPage(value); decodedPages.push(result.page);
    return { ...result, items: result.items.map(item => ({ ...item, ruleSetName: 'Decoded name' })) };
  };
  try {
    const single = await requestPage('/risk', { page: 2, size: 10 }, undefined, f.client, filters, decode);
    assert.equal(single.items[0]!.ruleSetName, 'Decoded name');
    const all = await requestAllPages('/risk', new AbortController().signal, f.client, filters, decode);
    assert.equal(all.length, 405); assert.ok(all.every(item => item.ruleSetName === 'Decoded name'));
    assert.deepEqual(decodedPages, [2, 1, 2, 3]);
    assert.deepEqual(f.requests, [
      '/risk?pointCode=CHECKOUT_PREPARE&ruleSetCode=SET+%2F%26&status=DRAFT&page=2&size=10',
      '/risk?pointCode=CHECKOUT_PREPARE&ruleSetCode=SET+%2F%26&status=DRAFT&page=1&size=200',
      '/risk?pointCode=CHECKOUT_PREPARE&ruleSetCode=SET+%2F%26&status=DRAFT&page=2&size=200',
      '/risk?pointCode=CHECKOUT_PREPARE&ruleSetCode=SET+%2F%26&status=DRAFT&page=3&size=200',
    ]);
  } finally { await f.close(); }
});

test('usePaging loader keeps its decoder in the real request chain for navigation, filters and failures', async () => {
  const decoderInputs: unknown[] = []; const controller = new AbortController();
  const f = await fixture((request, response) => {
    const query = new URL(request.url!, 'http://localhost').searchParams;
    const page = Number(query.get('page')); const size = Number(query.get('size'));
    response.writeHead(200, { 'Content-Type': 'application/json', 'X-Request-Id': 'hook-200' });
    response.end(JSON.stringify({ items: riskRows(1, (page - 1) * size + 1), page, size, total: (page - 1) * size + 1 }));
  });
  try {
    assert.equal(typeof pagingModule.createPagingLoader, 'function', 'hook loading boundary must be executable without a DOM');
    const decode: typeof decodeRuleSetPage = value => { decoderInputs.push(value); return decodeRuleSetPage(value); };
    const load = pagingModule.createPagingLoader('/risk', decode, f.client);
    const first = await load(defaultPaging, controller.signal, {});
    const next = await load({ page: 2, size: 20 }, controller.signal, { status: 'DRAFT' });
    assert.equal(first.items[0]!.ruleSetVersionId, '1'); assert.equal(next.items[0]!.ruleSetVersionId, '21');
    assert.equal(decoderInputs.length, 2);
    assert.deepEqual(f.requests, ['/risk?page=1&size=10', '/risk?status=DRAFT&page=2&size=20']);
    const incompatible = pagingModule.createPagingLoader('/risk', () => { throw new Error('invalid hook root'); }, f.client);
    await assert.rejects(incompatible(defaultPaging, controller.signal, {}), invalidPage('hook-200'));
    controller.abort();
    await assert.rejects(load(defaultPaging, controller.signal, {}), error => error instanceof Error && error.name === 'AbortError');
    assert.equal(f.requests.length, 3);
  } finally { await f.close(); }
});

test('changing page size returns to the first page while ordinary navigation keeps the selected page', () => {
  const current = { page: 4, size: 10 };
  assert.deepEqual(changePaging(current, 5, 10), { page: 5, size: 10 });
  for (const size of [20, 50, 100, 200]) {
    assert.deepEqual(changePaging(current, 4, size), { page: 1, size });
  }
  assert.deepEqual(current, { page: 4, size: 10 });
});

test('all three lists send server page and size parameters with the shared default', async () => {
  const f = await fixture((request, response) => {
    const query = new URL(request.url!, 'http://localhost').searchParams;
    const page = Number(query.get('page'));
    json(response, { items: page === 1 ? ['row'] : [], page, size: Number(query.get('size')), total: 1 });
  });
  try {
    for (const path of ['/iam/v1/accounts', '/audit/v1/events', '/iam/v1/roles']) {
      assert.deepEqual(await requestPage(path, defaultPaging, undefined, f.client),
        { items: ['row'], page: 1, size: 10, total: 1 });
    }
    await requestPage('/iam/v1/accounts', { page: 3, size: 50 }, undefined, f.client);
    assert.deepEqual(f.requests, ['/iam/v1/accounts?page=1&size=10', '/audit/v1/events?page=1&size=10',
      '/iam/v1/roles?page=1&size=10', '/iam/v1/accounts?page=3&size=50']);
  } finally { await f.close(); }
});

test('a non-empty later page cannot fall beyond the response total', async () => {
  const f = await fixture((_request, response) => {
    json(response, { items: ['orphan-row'], page: 2, size: 20, total: 1 });
  });
  try {
    await assert.rejects(requestPage('/gateway-admin/v1/targets', { page: 2, size: 20 }, undefined, f.client),
      (error: unknown) => error instanceof ApiError && error.status === 502 && error.code === 'INVALID_RESPONSE');
  } finally { await f.close(); }
});

test('an empty page is valid only after its offset reaches the response total', async () => {
  const f = await fixture((request, response) => {
    const page = Number(new URL(request.url!, 'http://localhost').searchParams.get('page'));
    json(response, { items: [], page, size: 20, total: page === 2 ? 1 : 100 });
  });
  try {
    assert.deepEqual(await requestPage('/gateway-admin/v1/routes', { page: 2, size: 20 }, undefined, f.client),
      { items: [], page: 2, size: 20, total: 1 });
    await assert.rejects(requestPage('/gateway-admin/v1/routes', { page: 1, size: 20 }, undefined, f.client),
      (error: unknown) => error instanceof ApiError && error.status === 502 && error.code === 'INVALID_RESPONSE');
  } finally { await f.close(); }
});

test('paging response validation rejects an offset outside JavaScript safe integer arithmetic', async () => {
  const unsafeOffsetPage = Math.floor(Number.MAX_SAFE_INTEGER / 200) + 2;
  const f = await fixture((request, response) => {
    const page = Number(new URL(request.url!, 'http://localhost').searchParams.get('page'));
    json(response, { items: [], page, size: 200, total: 0 });
  });
  try {
    await assert.rejects(requestPage('/gateway-admin/v1/routes', { page: unsafeOffsetPage, size: 200 }, undefined, f.client),
      (error: unknown) => error instanceof ApiError && error.status === 502 && error.code === 'INVALID_RESPONSE');
  } finally { await f.close(); }
});

test('role options load every page beyond two hundred before returning the full collection', async () => {
  const roles = Array.from({ length: 405 }, (_, index) => ({ roleId: `role-${index + 1}` }));
  const f = await fixture((request, response) => {
    const query = new URL(request.url!, 'http://localhost').searchParams;
    const page = Number(query.get('page')); const size = Number(query.get('size'));
    json(response, { items: roles.slice((page - 1) * size, page * size), page, size, total: roles.length });
  });
  try {
    assert.deepEqual(await requestAllPages('/iam/v1/roles', new AbortController().signal, f.client), roles);
    assert.deepEqual(f.requests, ['/iam/v1/roles?page=1&size=200', '/iam/v1/roles?page=2&size=200',
      '/iam/v1/roles?page=3&size=200']);
  } finally { await f.close(); }
});

test('filtered pages encode exact conditions without allowing filters to override the page contract', async () => {
  const filters = { occurredFrom: '2026-09-04T00:00:00.000Z', actorId: 'UNKNOWN', requestId: 'trace + / 123', page: '999' };
  const f = await fixture((request, response) => {
    const query = new URL(request.url!, 'http://localhost').searchParams;
    const page = Number(query.get('page'));
    json(response, { items: page === 1 ? ['filtered'] : [], page, size: Number(query.get('size')), total: 1 });
  });
  try {
    for (const page of [1, 2]) await requestPage('/audit/v1/events', { page, size: 20 }, undefined, f.client, filters);
    for (const [index, path] of f.requests.entries()) {
      const query = new URL(path, 'http://localhost').searchParams;
      assert.equal(query.get('page'), String(index + 1)); assert.equal(query.get('size'), '20');
      assert.equal(query.get('requestId'), filters.requestId); assert.equal(query.get('actorId'), 'UNKNOWN');
      assert.equal(query.get('occurredFrom'), filters.occurredFrom); assert.equal(query.getAll('page').length, 1);
    }
    assert.equal(filters.page, '999');
  } finally { await f.close(); }
});

test('a failed or incomplete later options page rejects without returning a partial collection', async () => {
  for (const failure of ['unavailable', 'incomplete', 'changed-total']) {
    const f = await fixture((request, response) => {
      if (request.url === '/iam/v1/roles?page=1&size=200') {
        json(response, { items: Array.from({ length: 200 }, (_, roleId) => ({ roleId })), page: 1, size: 200, total: 201 });
      } else if (failure === 'unavailable') json(response, { code: 'SERVICE_UNAVAILABLE' }, 503);
      else json(response, { items: [], page: 2, size: 200, total: failure === 'changed-total' ? 200 : 201 });
    });
    try {
      await assert.rejects(requestAllPages('/iam/v1/roles', new AbortController().signal, f.client), error =>
        error instanceof ApiError && error.status === (failure === 'unavailable' ? 503 : 502));
      assert.equal(f.requests.length, 2);
    } finally { await f.close(); }
  }
});

test('closing role selection cancels a pending page and never publishes partially loaded options', { timeout: 5_000 }, async () => {
  const started = barrier(); const release = barrier(); const controller = new AbortController();
  const f = await fixture(async (request, response) => {
    if (request.url === '/iam/v1/roles?page=1&size=200') {
      json(response, { items: Array.from({ length: 200 }, (_, roleId) => ({ roleId })), page: 1, size: 200, total: 201 }); return;
    }
    started.resolve(); await release.promise;
    json(response, { items: [{ roleId: 201 }], page: 2, size: 200, total: 201 });
  });
  try {
    const pending = requestAllPages('/iam/v1/roles', controller.signal, f.client);
    const rejected = assert.rejects(pending, (error: unknown) => error instanceof Error && error.name === 'AbortError');
    await started.promise; controller.abort(); release.resolve(); await rejected;
    assert.equal(f.requests.length, 2);
  } finally { release.resolve(); await f.close(); }
});

test('a canceled old page cannot complete after the newly selected page', { timeout: 5_000 }, async () => {
  const started = barrier(); const release = barrier(); const previous = new AbortController();
  const f = await fixture(async (request, response) => {
    const page = Number(new URL(request.url!, 'http://localhost').searchParams.get('page'));
    if (page === 1) { started.resolve(); await release.promise; }
    json(response, { items: Array.from({ length: 10 }, () => `page-${page}`), page, size: 10, total: 20 });
  });
  try {
    const oldPage = requestPage('/iam/v1/accounts', { page: 1, size: 10 }, previous.signal, f.client);
    const rejected = assert.rejects(oldPage, (error: unknown) => error instanceof Error && error.name === 'AbortError');
    await started.promise; previous.abort();
    const current = await requestPage('/iam/v1/accounts', { page: 2, size: 10 }, undefined, f.client);
    release.resolve(); await rejected;
    assert.deepEqual(current.items, Array.from({ length: 10 }, () => 'page-2')); assert.equal(current.page, 2);
    assert.equal(f.requests.length, 2);
  } finally { release.resolve(); await f.close(); }
});

test('an already canceled options load sends no request', async () => {
  const f = await fixture((_request, response) => json(response, {}, 500));
  try {
    const controller = new AbortController(); controller.abort();
    await assert.rejects(requestAllPages('/iam/v1/roles', controller.signal, f.client),
      (error: unknown) => error instanceof Error && error.name === 'AbortError');
    assert.deepEqual(f.requests, []);
  } finally { await f.close(); }
});

test('a whole-page decoder and page window failure keep the rejected response request ID', async () => {
  const f = await fixture((_request, response) => {
    response.writeHead(200, { 'Content-Type': 'application/json', 'X-Request-Id': 'paging-200' });
    response.end(JSON.stringify({ items: [{ id: '1', extra: true }], page: 1, size: 10, total: 1 }));
  });
  try {
    await assert.rejects(requestPage('/risk', { page: 1, size: 10 }, undefined, f.client, {}, value => {
      const page = value as { items?: unknown[]; page?: unknown; size?: unknown; total?: unknown };
      if (!Array.isArray(page.items) || page.items.some(item => Object.keys(item as object).length !== 1)) throw new Error('strict item');
      return page as never;
    }), (error: unknown) => error instanceof ApiError && error.code === 'INVALID_RESPONSE' && error.requestId === 'paging-200');
  } finally { await f.close(); }
});

test('a changed total without a page decoder still fails inside the second response boundary', async () => {
  const f = await fixture((request, response) => {
    const page = Number(new URL(request.url!, 'http://localhost').searchParams.get('page'));
    response.writeHead(200, { 'Content-Type': 'application/json', 'X-Request-Id': page === 1 ? 'paging-total-200' : 'paging-final-200' });
    response.end(JSON.stringify({ items: page === 1 ? Array.from({ length: 200 }, (_, index) => index) : [200, 201], page, size: 200, total: page === 1 ? 201 : 202 }));
  });
  try {
    await assert.rejects(requestAllPages('/risk', new AbortController().signal, f.client),
      (error: unknown) => error instanceof ApiError && error.code === 'INVALID_RESPONSE' && error.requestId === 'paging-final-200');
  } finally { await f.close(); }
});
