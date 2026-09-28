import assert from 'node:assert/strict';
import { register } from 'node:module';
import test from 'node:test';
import type { RiskDecisionApi } from './riskDecisionApi.ts';
import type { RequestClient, SuccessDecoder } from '../api/session.ts';
import * as decoders from './riskResponseValidation.ts';
import { authoringContext, candidateDetail, candidatePage, createCandidateResponse, createRuleSetResponse,
  freezeRuleSetResponse, ruleSetDetail, ruleSetPage, validateCandidateResponse, createRuleSetRequest,
  freezeRuleSetRequest, createCandidateRequest, validateCandidateRequest } from './riskFixtures.ts';

// Observe named decoder identity while delegating to every real decoder. This also
// observes which exact named function the paging closure invokes.
const decoderCalls: (keyof typeof decoders)[] = [];
const observed = Object.fromEntries(Object.entries(decoders).map(([name, decode]) =>
  [name, (value: unknown) => { decoderCalls.push(name as keyof typeof decoders); return decode(value); }]));
const probeKey = Symbol.for('risk-api-decoder-contract');
(globalThis as unknown as Record<symbol, unknown>)[probeKey] = observed;
const apiUrl = new URL('./riskDecisionApi.ts?decoder-contract', import.meta.url).href;
const probeSource = Object.keys(decoders).map(name =>
  `export const ${name} = globalThis[Symbol.for('risk-api-decoder-contract')].${name};`).join('\n');
// register is available throughout the project's supported Node range; the
// resolver affects only this isolated API import and delegates all other loads.
const probeUrl = `data:text/javascript,${encodeURIComponent(probeSource)}`;
const loaderSource = `export function resolve(specifier, context, nextResolve) {
  if (context.parentURL === ${JSON.stringify(apiUrl)} && specifier === './riskResponseValidation.ts') {
    return { url: ${JSON.stringify(probeUrl)}, shortCircuit: true };
  }
  return nextResolve(specifier, context);
}`;
register(`data:text/javascript,${encodeURIComponent(loaderSource)}`, import.meta.url);
let createApi: typeof import('./riskDecisionApi.ts').createRiskDecisionApi;
try { createApi = (await import(apiUrl)).createRiskDecisionApi; }
finally { delete (globalThis as unknown as Record<symbol, unknown>)[probeKey]; }

const signal = new AbortController().signal;
const commandBodies = {
  createRuleSetVersion: JSON.stringify(createRuleSetRequest, null, 2),
  freezeRuleSetVersion: JSON.stringify(freezeRuleSetRequest, null, 2),
  createCandidate: JSON.stringify(createCandidateRequest, null, 2),
  validateCandidate: JSON.stringify(validateCandidateRequest, null, 2),
};
const page = { page: 2, size: 10 };
const cases: {
  name: keyof RiskDecisionApi; path: string; method: 'GET' | 'POST'; raw: unknown;
  decoder: keyof typeof decoders; list?: boolean; call: (api: RiskDecisionApi) => Promise<unknown>;
}[] = [
  { name: 'authoringContext', path: '/decision-rule-sets/v1/authoring-context/CHECKOUT%20%2F%3F%23', method: 'GET',
    raw: authoringContext, decoder: 'decodeRuleAuthoringContext', call: api => api.authoringContext('CHECKOUT /?#', signal) },
  { name: 'ruleSets', path: '/decision-rule-sets/v1/rule-sets?pointCode=CHECKOUT_PREPARE&ruleSetCode=SET+%2F%26&status=DRAFT&page=2&size=10', method: 'GET', list: true,
    raw: { ...ruleSetPage, page: 2, total: 11 }, decoder: 'decodeRuleSetPage', call: api => api.ruleSets(page, { pointCode: 'CHECKOUT_PREPARE', ruleSetCode: 'SET /&', status: 'DRAFT' }, signal) },
  { name: 'ruleSet', path: '/decision-rule-sets/v1/rule-sets/id%20%2F%3F%23', method: 'GET',
    raw: ruleSetDetail, decoder: 'decodeRuleSetDetail', call: api => api.ruleSet('id /?#', signal) },
  { name: 'createRuleSetVersion', path: '/decision-rule-sets/v1/rule-sets', method: 'POST',
    raw: createRuleSetResponse, decoder: 'decodeCreateRuleSetVersionResponse', call: api => api.createRuleSetVersion(commandBodies.createRuleSetVersion, 'createRuleSetVersion-key') },
  { name: 'freezeRuleSetVersion', path: '/decision-rule-sets/v1/rule-sets/id%20%2F%3F%23/freeze', method: 'POST',
    raw: freezeRuleSetResponse, decoder: 'decodeFreezeRuleSetVersionResponse', call: api => api.freezeRuleSetVersion('id /?#', commandBodies.freezeRuleSetVersion, 'freezeRuleSetVersion-key') },
  { name: 'candidates', path: '/decision-releases/v1/candidates?pointCode=CHECKOUT_PREPARE&status=VALIDATING&page=2&size=10', method: 'GET', list: true,
    raw: { ...candidatePage, page: 2, total: 11 }, decoder: 'decodeCandidatePage', call: api => api.candidates(page, { pointCode: 'CHECKOUT_PREPARE', status: 'VALIDATING' }, signal) },
  { name: 'candidate', path: '/decision-releases/v1/candidates/id%20%2F%3F%23', method: 'GET',
    raw: candidateDetail(), decoder: 'decodeCandidateDetail', call: api => api.candidate('id /?#', signal) },
  { name: 'createCandidate', path: '/decision-releases/v1/candidates', method: 'POST',
    raw: createCandidateResponse, decoder: 'decodeCreateCandidateResponse', call: api => api.createCandidate(commandBodies.createCandidate, 'createCandidate-key') },
  { name: 'validateCandidate', path: '/decision-releases/v1/candidates/id%20%2F%3F%23/validate', method: 'POST',
    raw: validateCandidateResponse(), decoder: 'decodeValidateCandidateResponse', call: api => api.validateCandidate('id /?#', commandBodies.validateCandidate, 'validateCandidate-key') },
];

for (const row of cases) test(`${row.name}: exact method/path/query/signal/headers/body and named decoder, one request`, async () => {
  decoderCalls.length = 0;
  const body = commandBodies[row.name as keyof typeof commandBodies]; const key = `${row.name}-key`;
  const journal: { path: string; options: RequestInit; decoder: unknown }[] = [];
  let boundaryValue: unknown;
  const client: RequestClient = {
    async request<T>(path: string, options: RequestInit = {}, decode?: SuccessDecoder<T>): Promise<T> {
      journal.push({ path, options, decoder: decode });
      assert.equal(typeof decode, 'function');
      boundaryValue = decode!(structuredClone(row.raw));
      return boundaryValue as T;
    },
  };
  const result = await row.call(createApi(client));
  assert.equal(journal.length, 1);
  const call = journal[0]!;
  assert.equal(call.path, row.path);
  assert.equal(call.options.method ?? 'GET', row.method);
  assert.equal(call.options.signal, row.method === 'GET' ? signal : undefined);
  assert.deepEqual(call.options, row.method === 'GET' ? { signal } : {
    method: 'POST', body, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key },
  });
  assert.deepEqual([...new Headers(call.options.headers)], row.method === 'POST'
    ? [['content-type', 'application/json'], ['idempotency-key', key]] : []);
  assert.equal(call.options.body, row.method === 'POST' ? body : undefined);
  assert.deepEqual(decoderCalls, [row.decoder]);
  if (!row.list) assert.equal(call.decoder, observed[row.decoder]);
  assert.equal(result, boundaryValue, 'decoded object is returned directly');
  assert.deepEqual(result, row.raw);
});

for (const row of cases) test(`${row.name}: malformed success is rejected inside request with no retry`, async () => {
  let requests = 0; let rejectedInsideRequest = false;
  const client: RequestClient = { async request<T>(_path: string, _options?: RequestInit, decode?: SuccessDecoder<T>) {
    requests++;
    try { return decode!({ ...row.raw as object, unexpected: true }); }
    catch (error) { rejectedInsideRequest = true; throw error; }
  } };
  await assert.rejects(row.call(createApi(client)));
  assert.equal(rejectedInsideRequest, true); assert.equal(requests, 1);
});
