// 历史目录读取兼容性；当前编辑合同另由 riskVariable.test.ts 验证。
import assert from 'node:assert/strict';
import test from 'node:test';
import { CHECKOUT_PREPARE_AUTHORING_V1, matchAuthoringContext } from './legacyRiskAuthoringCatalog.ts';
import { atPath, authoringContext, changed, omitted } from './riskFixtures.ts';
import { decodeRuleAuthoringContext } from './riskResponseValidation.ts';
import type { WirePath } from './riskFixtures.ts';
import { paths } from './riskContractFixtures.ts';

test('matches the owner wire contract and projects the independent display order', () => {
  const result = matchAuthoringContext(authoringContext);
  assert.equal(result.editable, true);
  if (!result.editable) return;
  assert.deepEqual(result.catalog, CHECKOUT_PREPARE_AUTHORING_V1);
  assert.deepEqual(result.catalog.fields.map(field => field.fieldCode), ['country_code', 'locale', 'industry_code', 'mcc_code', 'currency', 'merchandise_amount', 'discount_amount', 'net_amount', 'entity_source_type']);
});

function readOnly(raw: unknown) {
  let decoded: ReturnType<typeof decodeRuleAuthoringContext>;
  try { decoded = decodeRuleAuthoringContext(raw); } catch { return; }
  const result = matchAuthoringContext(decoded);
  assert.equal(result.editable, false);
  if (!result.editable) assert.ok(result.reason.trim());
}
for (const valueType of ['STRING', 'NUMBER', 'BOOLEAN', 'DATETIME', 'DURATION'] as const) {
  test(`authoring decoder accepts ${valueType}; catalog separately enforces current field types`, () => {
    const raw = changed(authoringContext, ['fields', 0, 'valueType'], valueType);
    const decoded = decodeRuleAuthoringContext(raw);
    assert.equal(decoded.fields[0]!.valueType, valueType);
    if (valueType === 'STRING') assert.equal(matchAuthoringContext(decoded).editable, true); else readOnly(raw);
  });
}
test('every public authoring field is required; implementation fingerprints and extension arrays are forbidden', () => {
  for (const path of paths(authoringContext)) if (path.length && !Array.isArray(atPath(authoringContext, path.slice(0, -1)))) {
    assert.throws(() => decodeRuleAuthoringContext(omitted(authoringContext, path)), path.join('.'));
  }
  for (const name of ['handlerCode', 'implementationVersion', 'implementationHash']) {
    assert.throws(() => decodeRuleAuthoringContext(changed(authoringContext, ['actions', 0], { ...authoringContext.actions[0], [name]: 'internal' })), name);
  }
  for (const name of ['features', 'actionParameters', 'obligations', 'limitBindings']) for (const value of [[{}], ['UNSUPPORTED'], null, {}]) {
    assert.throws(() => decodeRuleAuthoringContext(changed(authoringContext, [name], value)), name);
  }
});
test('authoring rejects every duplicate and adjacent wire order inversion without sorting', () => {
  for (const name of ['fields', 'actions', 'operators'] as const) {
    const items = authoringContext[name];
    for (let index = 0; index < items.length; index++) {
      assert.throws(() => decodeRuleAuthoringContext(changed(authoringContext, [name], [...items.slice(0, index), items[index], ...items.slice(index)])), `${name} duplicate ${index}`);
      if (index + 1 < items.length) {
        const swapped: unknown[] = [...items]; [swapped[index], swapped[index + 1]] = [swapped[index + 1], swapped[index]];
        assert.throws(() => decodeRuleAuthoringContext(changed(authoringContext, [name], swapped)), `${name} order ${index}`);
      }
    }
  }
  assert.throws(() => decodeRuleAuthoringContext(changed(authoringContext, ['actions'], [authoringContext.actions[0], { ...authoringContext.actions[0], versionNo: 2 }, ...authoringContext.actions.slice(1)])), 'same action code at another version');
});
test('catalog rejects every missing/unknown field and action; decoder rejects missing/unknown operators', () => {
  for (const name of ['fields', 'actions'] as const) for (let index = 0; index < authoringContext[name].length; index++) {
    readOnly(changed(authoringContext, [name], authoringContext[name].filter((_, i) => i !== index)));
  }
  readOnly(changed(authoringContext, ['fields'], [...authoringContext.fields, { ...authoringContext.fields[0], fieldCode: 'unknown_field' }]));
  readOnly(changed(authoringContext, ['actions'], [...authoringContext.actions, { ...authoringContext.actions[0], actionCode: 'UNKNOWN_ACTION' }]));
  for (let index = 0; index < authoringContext.operators.length; index++) {
    assert.throws(() => decodeRuleAuthoringContext(changed(authoringContext, ['operators'], authoringContext.operators.filter((_, i) => i !== index))));
    assert.throws(() => decodeRuleAuthoringContext(changed(authoringContext, ['operators', index], 'UNKNOWN_OPERATOR')));
  }
});
test('catalog rejects each descriptor/point/field/action semantic deviation after successful decoding', () => {
  const mutations: [WirePath, unknown][] = [[['descriptorCode'], 'CHECKOUT_PREPARE_AUTHORING_V2']];
  for (const [key, value] of Object.entries(authoringContext.point)) mutations.push([['point', key], typeof value === 'number' ? value + 1 : key === 'pointName' ? 'Other Point' : 'OTHER']);
  for (let index = 0; index < authoringContext.fields.length; index++) {
    for (const [key, value] of [['requiredFlag', false], ['ruleReferenceFlag', false], ['actionParameterFlag', true], ['dataClassification', 'PUBLIC'], ['valueType', authoringContext.fields[index]!.valueType === 'STRING' ? 'NUMBER' : 'STRING']] as const) mutations.push([['fields', index, key], value]);
  }
  for (let index = 0; index < authoringContext.actions.length; index++) {
    for (const [key, value] of [['actionName', 'Other action'], ['actionCategory', 'OTHER'], ['versionNo', 2], ['terminalFlag', false], ['decisionCode', 'OTHER'], ['mergePolicy', 'OTHER'], ['shadowSupportedFlag', false], ['status', 'INACTIVE']] as const) mutations.push([['actions', index, key], value]);
  }
  for (const [path, value] of mutations) readOnly(changed(authoringContext, path, value));
});
test('editable catalog supplies the exact frozen Chinese displays, help text and widgets', () => {
  const result = matchAuthoringContext(decodeRuleAuthoringContext(authoringContext));
  assert.equal(result.editable, true); if (!result.editable) return;
  assert.deepEqual(result.catalog.fields.map(({ fieldCode, label, helpText, widget }) => [fieldCode, label, helpText, widget]), [
    ['country_code', 'Country/Region', 'ISO 3166-1 two uppercase letters, e.g. AE', 'COUNTRY_CODE'],
    ['locale', 'Locale', 'Canonical BCP 47 tag, e.g. ar-AE', 'LOCALE'],
    ['industry_code', 'Industry Code', 'Platform industry code of 1–64 uppercase characters', 'UPPER_CODE'],
    ['mcc_code', 'MCC', 'Four-digit decimal code with leading zeros kept', 'MCC'],
    ['currency', 'Currency', 'ISO 4217 three uppercase letters, e.g. USD', 'CURRENCY'],
    ['merchandise_amount', 'Merchandise Amount', 'At most 16 integer digits and 8 decimal places', 'DECIMAL'],
    ['discount_amount', 'Discount Amount', 'At most 16 integer digits and 8 decimal places', 'DECIMAL'],
    ['net_amount', 'Net Amount', 'At most 16 integer digits and 8 decimal places', 'DECIMAL'],
    ['entity_source_type', 'Operating Entity Source', 'PLATFORM, CLIENT or PARTNER only', 'ENTITY_SOURCE'],
  ]);
  assert.deepEqual(result.catalog.actions, [{ actionCode: 'ALLOW_CHECKOUT', label: 'Pass (PASS)' }, { actionCode: 'REVIEW_CHECKOUT', label: 'Review (REVIEW)' }, { actionCode: 'DENY_CHECKOUT', label: 'Reject (REJECT)' }]);
  assert.deepEqual(result.catalog.operators.map(item => item.label), ['Equals', 'Not Equal To', 'Greater Than', 'Greater Than or Equal To', 'Less Than', 'Less Than or Equal To', 'In', 'Not In', 'Between', 'Is Empty', 'Is Not Empty']);
  for (const path of paths(result.catalog)) {
    const value = atPath(result.catalog, path); if (value && typeof value === 'object') assert.ok(Object.isFrozen(value), path.join('.'));
  }
});
