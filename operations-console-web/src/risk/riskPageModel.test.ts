import assert from 'node:assert/strict';
import test from 'node:test';
import type { ConsoleResource } from '../api/types.ts';
import { hasRiskAction, parseRiskTab, requireRiskAction, RiskActionUnavailableError, riskTabSearch } from './riskPageModel.ts';
import type { RiskAction } from './riskPageModel.ts';
import { classifyCommandFailure, reduceRiskCommandResult, commandFailureMessage, submitRiskCommand, riskCommandPath, riskFieldErrorLabel } from './riskPageModel.ts';
import type { RiskPageCommandState } from './riskPageModel.ts';
import { ApiError } from '../api/session.ts';
import { detailToEditor } from './ruleBuilder.ts';
import { ruleSetDetail, createRuleSetResponse, freezeRuleSetResponse, createCandidateResponse, validateCandidateResponse } from './riskFixtures.ts';
import { createRiskDecisionApi } from './riskDecisionApi.ts';
import type { SuccessDecoder } from '../api/session.ts';

const pending = Object.freeze({ path: '/decision-rule-sets/v1/rule-sets', payload: '{"exact":"body"}', key: 'exact-command-key' });
function pageState(): RiskPageCommandState {
  return { activeTab: 'rule-sets', editor: detailToEditor(ruleSetDetail),
    confirmation: Object.freeze({ action: 'FREEZE', ruleSetVersionId: '1', payload: 'unchanged' }),
    selectedRuleSetVersionId: '1', selectedCandidateReleaseId: '4', pendingCommand: pending,
    fieldErrors: Object.freeze([{ field: 'old', message: 'old' }]) };
}

function button(resourceCode: string): ConsoleResource {
  return { resourceId: '1', resourceCode, resourceType: 'BUTTON', parentResourceId: null,
    displayName: '保存为新版本', frontendRoute: null, componentKey: null, sortOrder: 0 };
}
const codes: Record<RiskAction, string> = {
  DELETE: 'OPERATIONS_RISK_RULE_SET_DELETE',
  SAVE: 'OPERATIONS_RISK_RULE_SET_SAVE', FREEZE: 'OPERATIONS_RISK_RULE_SET_FREEZE',
  CREATE_CANDIDATE: 'OPERATIONS_RISK_TEST_CANDIDATE_CREATE', VALIDATE_CANDIDATE: 'OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE',
};
test('tab parsing defaults closed and search drops every extra key', () => {
  for (const search of ['', '?tab=unknown', '?tab=', '?tab=test-candidates&tab=rule-sets']) assert.equal(parseRiskTab(search), 'rule-sets');
  assert.equal(parseRiskTab('?secret=discard&tab=test-candidates'), 'test-candidates');
  assert.equal(riskTabSearch('rule-sets'), '?tab=rule-sets');
  assert.equal(riskTabSearch('test-candidates'), '?tab=test-candidates');
});
test('actions require only their exact Button resource; names, parents and substrings confer no authority', () => {
  for (const action of Object.keys(codes) as RiskAction[]) {
    assert.equal(hasRiskAction([button(codes[action])], action), true);
    assert.doesNotThrow(() => requireRiskAction([button(codes[action])], action));
    for (const resource of [button(`${codes[action]}_OTHER`), button('RISK_RULE_SET_SAVE'),
      { ...button(codes[action]), resourceType: 'MENU' as const },
      { ...button('OTHER'), parentResourceId: codes[action], displayName: codes[action] }]) {
      assert.equal(hasRiskAction([resource], action), false);
      assert.throws(() => requireRiskAction([resource], action), RiskActionUnavailableError);
    }
  }
});
test('submit-time revocation preserves content, refreshes authorization and emits zero HTTP requests', async () => {
  let resources = [button(codes.SAVE)];
  const editor = { ruleSetName: 'in-memory' }; const before = structuredClone(editor);
  assert.equal(hasRiskAction(resources, 'SAVE'), true);
  resources = []; let requests = 0; let refreshes = 0;
  try { requireRiskAction(resources, 'SAVE'); requests++; }
  catch (error) { if (error instanceof RiskActionUnavailableError) refreshes++; else throw error; }
  assert.equal(requests, 0); assert.equal(refreshes, 1); assert.deepEqual(editor, before);
});

for (const status of [400, 401, 403, 404, 409, 413, 422, 429] as const) {
  test(`deterministic ${status} clears key, preserves editor and follows confirmation/detail rules`, () => {
    const state = pageState(); const original = structuredClone(state);
    const error = new ApiError(status, 'BUSINESS_CHECK_FAILED', 'request-400', 'Safe business detail', [{ field: 'rules', message: 'Invalid rules' }]);
    const result = classifyCommandFailure('SAVE', pending, error);
    assert.equal(result.kind, 'HTTP_FAILURE');
    if (result.kind !== 'HTTP_FAILURE') return;
    assert.equal(result.code, error.code); assert.equal(result.message, error.message); assert.equal(result.requestId, error.requestId);
    assert.deepEqual(result.fieldErrors, []); assert.ok(Object.isFrozen(result.fieldErrors));
    const next = reduceRiskCommandResult(state, result);
    assert.equal(next.editor, state.editor); assert.equal(next.pendingCommand, null);
    assert.equal(next.confirmation, [404, 422].includes(status) ? null : state.confirmation);
    assert.equal(next.selectedRuleSetVersionId, status === 404 ? null : '1');
    assert.deepEqual(next.fieldErrors, []); assert.deepEqual(state, original);
    if (status === 400) assert.equal(commandFailureMessage(result), 'Safe business detail');
    if (status === 413) {
      assert.match(commandFailureMessage(result), /64 KiB/);
      assert.match(commandFailureMessage({ ...result, action: 'FREEZE' }), /16 KiB/);
    }
    if (status === 422) assert.doesNotMatch(commandFailureMessage(result), /Safe business detail/);
  });
}
test('only 422 VALIDATION_ERROR projects immutable validated fields and never general message', () => {
  const error = new ApiError(422, 'VALIDATION_ERROR', 'validation-422', 'Never show this detail', [{ field: 'rules[0].ruleName', message: '填写规则名称' }]);
  const result = classifyCommandFailure('SAVE', pending, error);
  assert.equal(result.kind, 'HTTP_FAILURE'); if (result.kind !== 'HTTP_FAILURE') return;
  assert.equal(result.fieldErrors, error.fieldErrors); assert.ok(Object.isFrozen(result.fieldErrors[0]));
  const next = reduceRiskCommandResult(pageState(), result);
  assert.equal(next.fieldErrors, result.fieldErrors); assert.equal(next.confirmation, null); assert.equal(next.pendingCommand, null);
  assert.doesNotMatch(commandFailureMessage(result), /Never show/);
  for (const status of [400, 409]) {
    const other = classifyCommandFailure('SAVE', pending, new ApiError(status, 'VALIDATION_ERROR', '', undefined, error.fieldErrors));
    assert.equal(other.kind, 'HTTP_FAILURE'); if (other.kind === 'HTTP_FAILURE') assert.deepEqual(other.fieldErrors, []);
  }
});
for (const error of [new ApiError(502, 'INVALID_RESPONSE', 'semantic-200'), new ApiError(503, 'UNAVAILABLE', 'service-503'),
  new ApiError(500, 'INTERNAL_ERROR'), new ApiError(504, 'TIMEOUT'), new TypeError('private payload'), new Error('disconnect'), new DOMException('timeout', 'TimeoutError')]) {
  test(`unknown ${error.name}/${error instanceof ApiError ? error.status : 'network'} retains exact command and safe request ID`, () => {
    const state = pageState(); const result = classifyCommandFailure('SAVE', pending, error);
    assert.equal(result.kind, 'UNKNOWN_RESULT'); if (result.kind !== 'UNKNOWN_RESULT') return;
    assert.equal(result.pendingCommand, pending); assert.equal(result.requestId, error instanceof ApiError ? error.requestId : '');
    assert.deepEqual(Object.keys(result).sort(), ['action', 'kind', 'pendingCommand', 'requestId']);
    const next = reduceRiskCommandResult(state, result);
    assert.equal(next.editor, state.editor); assert.equal(next.confirmation, state.confirmation); assert.equal(next.pendingCommand, pending);
    assert.deepEqual(next.fieldErrors, []);
  });
}
test('SAVE/FREEZE/VALIDATE success selects returned coordinate; REJECTED remains a successful validation', () => {
  const state = pageState();
  for (const action of ['SAVE', 'FREEZE'] as const) {
    const next = reduceRiskCommandResult(state, { kind: 'SUCCESS', action, ruleSetVersionId: '999' });
    assert.equal(next.selectedRuleSetVersionId, '999'); assert.equal(next.activeTab, 'rule-sets');
    assert.equal(next.confirmation, null); assert.equal(next.pendingCommand, null); assert.deepEqual(next.fieldErrors, []);
    assert.equal(next.editor, action === 'SAVE' ? null : state.editor);
  }
  const next = reduceRiskCommandResult({ ...state, activeTab: 'test-candidates' }, { kind: 'SUCCESS', action: 'VALIDATE_CANDIDATE', releaseId: '999' });
  assert.equal(next.selectedCandidateReleaseId, '999'); assert.equal(next.activeTab, 'test-candidates');
});
test('Create Candidate switches canonical tab and targets returned detail even absent from first page', () => {
  const state = pageState();
  const next = reduceRiskCommandResult(state, { kind: 'SUCCESS', action: 'CREATE_CANDIDATE', releaseId: '999' });
  const firstPage = ['4']; const requested: string[] = [];
  if (next.selectedCandidateReleaseId) requested.push(next.selectedCandidateReleaseId);
  assert.equal(next.activeTab, 'test-candidates'); assert.equal(riskTabSearch(next.activeTab), '?tab=test-candidates');
  assert.equal(firstPage.includes(next.selectedCandidateReleaseId!), false); assert.deepEqual(requested, ['999']);
  assert.equal(next.confirmation, null); assert.equal(next.pendingCommand, null); assert.deepEqual(next.fieldErrors, []);
});
test('production command dispatcher rechecks every action immediately before API, with zero HTTP after revocation', async () => {
  const requests: unknown[] = []; let refreshes = 0;
  const api = createRiskDecisionApi({ async request<T>(path: string, options?: RequestInit, decode?: SuccessDecoder<T>) {
    requests.push({ path, options }); return decode!({});
  } });
  for (const target of [{ action: 'SAVE' }, { action: 'FREEZE', ruleSetVersionId: '1' },
    { action: 'CREATE_CANDIDATE' }, { action: 'VALIDATE_CANDIDATE', releaseId: '4' }] as const) {
    await assert.rejects(submitRiskCommand(api, target, { ...pending, path: riskCommandPath(target) }, () => [], () => refreshes++), RiskActionUnavailableError);
  }
  assert.equal(requests.length, 0); assert.equal(refreshes, 4);
});
test('production dispatcher sends exact envelope once and accepts REJECTED as successful data', async () => {
  const journal: { path: string; options?: RequestInit }[] = [];
  const replies = [createRuleSetResponse, freezeRuleSetResponse, createCandidateResponse, validateCandidateResponse(true)];
  const api = createRiskDecisionApi({ async request<T>(path: string, options?: RequestInit, decode?: SuccessDecoder<T>) {
    journal.push({ path, options }); return decode!(replies.shift());
  } });
  for (const target of [{ action: 'SAVE' }, { action: 'FREEZE', ruleSetVersionId: '1' },
    { action: 'CREATE_CANDIDATE' }, { action: 'VALIDATE_CANDIDATE', releaseId: '4' }] as const) {
    const envelope = { ...pending, path: riskCommandPath(target) };
    const result = await submitRiskCommand(api, target, envelope, () => [button(codes[target.action])], () => assert.fail('unexpected refresh'));
    assert.equal(result.kind, 'SUCCESS'); assert.equal(result.action, target.action);
    const request = journal.at(-1)!; assert.equal(request.path, envelope.path); assert.equal(request.options?.body, envelope.payload);
    assert.equal(new Headers(request.options?.headers).get('Idempotency-Key'), envelope.key);
  }
  assert.equal(journal.length, 4);
});
test('dispatcher refreshes authorization on authoritative 403 and never automatically replays any failure', async () => {
  for (const error of [new ApiError(403, 'FORBIDDEN', 'denied'), new ApiError(401, 'UNAUTHORIZED'), new ApiError(502, 'INVALID_RESPONSE', 'semantic-200'), new TypeError('network')]) {
    let calls = 0; let refreshes = 0;
    const api = createRiskDecisionApi({ async request<T>(): Promise<T> { calls++; throw error; } });
    const result = await submitRiskCommand(api, { action: 'SAVE' }, pending, () => [button(codes.SAVE)], () => refreshes++);
    assert.equal(calls, 1); assert.equal(refreshes, error instanceof ApiError && error.status === 403 ? 1 : 0);
    assert.equal(result.kind, error instanceof ApiError && error.status < 500 ? 'HTTP_FAILURE' : 'UNKNOWN_RESULT');
  }
});
test('validated error paths render user labels without exposing wire structure names', () => {
  assert.equal(riskFieldErrorLabel('ruleSetName'), 'Rule Set Name');
  assert.equal(riskFieldErrorLabel('rules[0].ruleName'), 'Rule 1 · Rule Name');
  assert.equal(riskFieldErrorLabel('rules[1].conditionNodes[2].nodeKey'), 'Rule 2 · Condition');
  assert.equal(riskFieldErrorLabel('rules[199].action.actionCode'), 'Rule 200 · Terminal Decision');
  for (const field of ['unknown.field', 'nodeKey', 'rules[999999999].conditionNodes', 'parentNodeKey']) assert.equal(riskFieldErrorLabel(field), 'Rule Configuration');
});
