import assert from 'node:assert/strict';
import test from 'node:test';
import { variableContext as configurationContext, variableRuleSetDetail as ruleSetDetail } from './variableFixtures.ts';
import { decodeRuleAuthoringContext, decodeRuleSetDetail } from './riskResponseValidation.ts';
import { matchAuthoringContext } from './riskAuthoringCatalog.ts';
import { detailToEditor, editorToCreateRequest } from './ruleBuilder.ts';
import type { EditorAdditionalAction } from './ruleBuilder.ts';

function catalog(context = configurationContext) {
  const result = matchAuthoringContext(decodeRuleAuthoringContext(context));
  assert.equal(result.editable, true); if (!result.editable) throw new Error('metadata unavailable'); return result.catalog;
}
const cooldown = (): EditorAdditionalAction => ({ id: 'cooldown', actionCode: 'APPLY_COOLDOWN', actionVersionNo: 1, targetBindingCode: 'CURRENT_CLIENT_V1', subjectVariableCode:'REGISTERED_FIELD_0', parameters: [{ parameterCode: 'DURATION', valueType: 'DURATION', values: ['1800000'] }] });
const tags = (): EditorAdditionalAction => ({ id: 'tag', actionCode: 'ADD_RISK_TAG', actionVersionNo: 1, targetBindingCode: 'CURRENT_CLIENT_V1', subjectVariableCode:'REGISTERED_FIELD_0', parameters: [{ parameterCode: 'TAG', valueType: 'STRING', values: ['SECOND', 'FIRST', 'SECOND'] }] });

test('current metadata exposes only approved targets and keeps client ID out of condition selectors', () => {
  const result = catalog();
  assert.equal(result.additionalActions.length, 2); assert.equal(result.fields.length, 0);
  assert.ok(!result.fields.some(field => field.fieldCode === 'client_id'));
  assert.deepEqual(result.targetBindings.map(target => target.bindingCode), ['CURRENT_CLIENT_V1']);
  const dynamic = structuredClone(configurationContext);
  dynamic.targetBindings = [{ bindingCode: 'ENGINE_DEFINED_V9', displayName: '工程批准的主体', subjectType: 'APPROVED_SUBJECT', requiredFields: [], subjectSemanticRole:'CLIENT_ID' }];
  const model = detailToEditor(ruleSetDetail); const action = cooldown(); action.targetBindingCode = 'ENGINE_DEFINED_V9'; model.rules[0]!.additionalActions = [action];
  assert.equal(editorToCreateRequest(model, null, catalog(dynamic)).rules[0]!.additionalActions[0]!.targetBindingCode, 'ENGINE_DEFINED_V9');
  const missing = { ...dynamic, fields: dynamic.fields.filter(field => field.fieldCode !== 'client_id') };
  assert.equal(matchAuthoringContext(decodeRuleAuthoringContext(missing)).editable, true); // 候选由变量库查询，M01 不拥有固定字段清单。
});
test('optional terminal plus multiple actions round-trip independently and normalize tag codes', () => {
  const model = detailToEditor(ruleSetDetail); model.rules[0]!.actionCode = ''; model.rules[0]!.additionalActions = [cooldown(), tags()];
  const before = structuredClone(model); const request = editorToCreateRequest(model, null, catalog());
  assert.equal(request.rules[0]!.action, null);
  assert.deepEqual(request.rules[0]!.additionalActions[1]!.parameters.map(parameter => parameter.literalValue), ['FIRST', 'SECOND']);
  assert.deepEqual(model, before);
  const detail = decodeRuleSetDetail({ ...ruleSetDetail, rules: request.rules });
  assert.deepEqual(editorToCreateRequest(detailToEditor(detail), null, catalog()), request);
});
test('dynamic duration limits and unsupported targets fail before save; no numeric coercion', () => {
  for (const value of ['0', '-1', '01', '1.5', '31536000001', '1e3']) {
    const model = detailToEditor(ruleSetDetail); const action = cooldown(); action.parameters[0]!.values = [value]; model.rules[0]!.additionalActions = [action];
    assert.throws(() => editorToCreateRequest(model, null, catalog()));
  }
  const context = structuredClone(configurationContext); context.actionParameters = context.actionParameters.map(parameter => parameter.parameterCode === 'DURATION' ? { ...parameter, maximumValue: '1000' } : parameter);
  const model = detailToEditor(ruleSetDetail); model.rules[0]!.additionalActions = [cooldown()]; assert.throws(() => editorToCreateRequest(model, null, catalog(context)));
  model.rules[0]!.additionalActions[0]!.targetBindingCode = 'CURRENT_CONSUMER_V1'; assert.throws(() => editorToCreateRequest(model, null, catalog()));
});
test('new reject requires manual code and message while historical missing message remains readable', () => {
  const historical = decodeRuleSetDetail({ ...ruleSetDetail, rules: [{ ...ruleSetDetail.rules[0], action: { actionCode: 'DENY_CHECKOUT', actionVersionNo: 1 }, reasonCode: 'HISTORICAL', reasonMessage: null }] });
  const model = detailToEditor(historical); assert.equal(model.rules[0]!.reasonMessage, '');
  assert.throws(() => editorToCreateRequest(model, null, catalog()));
  model.rules[0]!.reasonMessage = '人工说明'; assert.equal(editorToCreateRequest(model, null, catalog()).rules[0]!.reasonMessage, '人工说明');
  model.rules[0]!.reasonCode = ''; assert.throws(() => editorToCreateRequest(model, null, catalog()));
  const allowed = detailToEditor({ ...ruleSetDetail, rules: [{ ...ruleSetDetail.rules[0]!, reasonCode: 'HISTORICAL_ALLOW' }] });
  assert.equal(editorToCreateRequest(allowed, null, catalog()).rules[0]!.reasonCode, 'HISTORICAL_ALLOW');
});
test('empty actions round trip; malformed additional response fields remain rejected', () => {
  const model = detailToEditor(ruleSetDetail); model.rules[0]!.actionCode = '';
  const request = editorToCreateRequest(model, null, catalog());
  assert.equal(request.rules[0]!.action, null); assert.deepEqual(request.rules[0]!.additionalActions, []);
  assert.deepEqual(decodeRuleSetDetail({ ...ruleSetDetail, rules: request.rules }).rules, request.rules);
  for (const additionalActions of [null, [{}], [{ sequenceNo: 1, actionCode: 'APPLY_COOLDOWN', actionVersionNo: 1, targetBindingCode: 'CURRENT_CLIENT_V1', subjectVariableCode:'REGISTERED_FIELD_0', parameters: [] }]]) {
    assert.throws(() => decodeRuleSetDetail({ ...ruleSetDetail, rules: [{ ...ruleSetDetail.rules[0], additionalActions }] }));
  }
});
