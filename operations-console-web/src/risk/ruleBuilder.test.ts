import assert from 'node:assert/strict';
import test from 'node:test';
import type { OperatorCode, RuleConditionNode, RuleDefinition } from './riskApiModels.ts';
import { ruleSetDetail } from './riskFixtures.ts';
import { decodeRuleSetDetail } from './riskResponseValidation.ts';
import {
  canonicalDecimal, canonicalFieldValue, detailToEditor, editorToCreateRequest, operatorsForField,
} from './ruleBuilder.ts';
import type { EditorCondition, EditorRule, RuleSetBase, RuleSetEditorModel } from './ruleBuilder.ts';

type Predicate = Extract<EditorCondition, { kind: 'CONDITION' }>;
const predicate = (overrides: Partial<Predicate> = {}): Predicate => ({
  id: 'local-condition', kind: 'CONDITION', fieldSide: 'LEFT', fieldCode: 'country_code',
  operator: 'EQ', values: ['AE'], ...overrides,
});
const editorRule = (condition: EditorCondition = predicate(), index = 0): EditorRule => ({
  id: `local-rule-${index}`, ruleCode: `RULE_${index}`, ruleName: `Rule ${index}`, enabled: true,
  reasonCode: '', condition, actionCode: 'ALLOW_CHECKOUT', actionVersionNo: 1,
});
const editor = (condition: EditorCondition = predicate()): RuleSetEditorModel => ({
  pointCode: 'CHECKOUT_PREPARE', ruleSetCode: 'RULE_SET', ruleSetName: 'Rule set',
  changeReason: 'Initial version', rules: [editorRule(condition)],
});
const convert = (condition: EditorCondition) => editorToCreateRequest(editor(condition), null);

for (const [input, expected] of [
  ['1', '1.00000000'], ['1.0', '1.00000000'], ['1.00000000', '1.00000000'], ['0', '0.00000000'],
  ['0.00000000', '0.00000000'], ['-1', '-1.00000000'], ['0.1', '0.10000000'],
  ['-0.00000001', '-0.00000001'], ['9007199254740993.12345678', '9007199254740993.12345678'],
  ['9999999999999999.99999999', '9999999999999999.99999999'],
  ['-9999999999999999.99999999', '-9999999999999999.99999999'],
] as const) test(`decimal preserves exact value ${input}`, () => {
  assert.equal(canonicalDecimal(input), expected);
});

for (const input of [
  '-0', '-0.0', '-0.00000000', '+1', '01', '-01', '00.1', '1e0', '1E0', '.1', '-.1', '1.',
  '1.000000001', '0.000000001', '10000000000000000', '-10000000000000000', '', ' ', ' 1', '1 ',
  '1\n', '1\r', '1\u2028', 'NaN', 'Infinity', '١', 1, 0, -0, NaN, Infinity, null, undefined, ['1'], {},
]) test(`decimal rejects noncanonical token ${JSON.stringify(input)}`, () => {
  assert.throws(() => canonicalDecimal(input as string));
});

test('one decoded golden rule becomes an independent editor model and complete create request', () => {
  const detail = decodeRuleSetDetail(ruleSetDetail);
  const model = detailToEditor(detail);
  assert.deepEqual(model, {
    pointCode: 'CHECKOUT_PREPARE', ruleSetCode: 'RULE_SET', ruleSetName: 'Rule set', changeReason: 'Initial version',
    rules: [{ id: 'RULE_ONE', ruleCode: 'RULE_ONE', ruleName: 'Rule one', enabled: true, reasonCode: '', reasonMessage: '', additionalActions: [],
      condition: { id: '1', kind: 'CONDITION', fieldSide: 'LEFT', fieldCode: 'country_code', operator: 'EQ', values: ['AE'] },
      actionCode: 'ALLOW_CHECKOUT', actionVersionNo: 1 }],
  });
  const request = editorToCreateRequest(model, null);
  assert.deepEqual(request, {
    pointCode: 'CHECKOUT_PREPARE', ruleSetCode: 'RULE_SET', ruleSetName: 'Rule set',
    baseRuleSetVersionId: null, baseContentHash: null, changeReason: 'Initial version',
    rules: [{ ruleCode: 'RULE_ONE', ruleName: 'Rule one', priorityNo: 10, enabledFlag: true, reasonCode: null, reasonMessage: null, additionalActions: [],
      conditionNodes: [{ nodeKey: 'n1', parentNodeKey: null, sequenceNo: 1, nodeType: 'PREDICATE',
        groupOperator: null, comparisonOperator: 'EQ', operands: [
          { operandRole: 'LEFT', valueSequence: 1, sourceType: 'CONTEXT', fieldCode: 'country_code', valueType: 'STRING', literalValue: null },
          { operandRole: 'RIGHT', valueSequence: 1, sourceType: 'LITERAL', fieldCode: null, valueType: 'STRING', literalValue: 'AE' },
        ] }], action: { actionCode: 'ALLOW_CHECKOUT', actionVersionNo: 1 } }],
  });
  assert.deepEqual(JSON.parse(JSON.stringify(request)), request, 'serialization must not omit any field');
  const condition = model.rules[0]!.condition;
  assert.equal(condition.kind, 'CONDITION');
  if (condition.kind === 'CONDITION') condition.values[0] = 'US';
  assert.equal(detail.rules[0]!.conditionNodes[0]!.operands[1]!.literalValue, 'AE');
  assert.equal(request.rules[0]!.conditionNodes[0]!.operands[1]!.literalValue, 'AE');
});

function wirePredicate(side: 'LEFT' | 'RIGHT', operator: OperatorCode, values: string[]): RuleConditionNode {
  return { nodeKey: '987654321', parentNodeKey: null, sequenceNo: 1, nodeType: 'PREDICATE', groupOperator: null,
    comparisonOperator: operator, operands: [
      ...values.map((literalValue, index) => ({ operandRole: side === 'LEFT' ? 'RIGHT' as const : 'LEFT' as const,
        valueSequence: index + 1, sourceType: 'LITERAL' as const, fieldCode: null, valueType: 'NUMBER' as const, literalValue })),
      { operandRole: side, valueSequence: 1, sourceType: 'CONTEXT', fieldCode: 'net_amount', valueType: 'NUMBER', literalValue: null },
    ] };
}

function prioritizedDirectionalRules(): RuleDefinition[] {
  const allowFirst: RuleDefinition = { ...structuredClone(ruleSetDetail.rules[0]!),
    ruleCode: 'ALLOW_FIRST', ruleName: 'Allow first', priorityNo: 10,
    conditionNodes: [{ ...wirePredicate('RIGHT', 'GT', ['10.00000000']), nodeKey: 'allow-root' }],
    action: { actionCode: 'ALLOW_CHECKOUT', actionVersionNo: 1 },
  };
  const denySecond: RuleDefinition = { ...structuredClone(ruleSetDetail.rules[0]!),
    ruleCode: 'DENY_SECOND', ruleName: 'Deny second', priorityNo: 20, reasonCode: 'MANUAL_REASON', reasonMessage: 'Manual explanation',
    conditionNodes: [{ ...wirePredicate('LEFT', 'GT', ['20.00000000']), nodeKey: 'deny-root' }],
    action: { actionCode: 'DENY_CHECKOUT', actionVersionNo: 1 },
  };
  return [allowFirst, denySecond];
}

test('decoded rules use priority order before editor renumbering preserves first-terminal semantics', () => {
  const [allowFirst, denySecond] = prioritizedDirectionalRules();
  const detail = decodeRuleSetDetail({ ...ruleSetDetail, rules: [denySecond, allowFirst] });
  const before = structuredClone(detail);
  const model = detailToEditor(detail);

  assert.deepEqual(model.rules.map(rule => [rule.ruleCode, rule.actionCode,
    rule.condition.kind === 'CONDITION' ? rule.condition.fieldSide : null]), [
    ['ALLOW_FIRST', 'ALLOW_CHECKOUT', 'RIGHT'],
    ['DENY_SECOND', 'DENY_CHECKOUT', 'LEFT'],
  ]);
  const request = editorToCreateRequest(model, null);
  assert.deepEqual(request.rules.map(rule => [rule.ruleCode, rule.action?.actionCode, rule.priorityNo]), [
    ['ALLOW_FIRST', 'ALLOW_CHECKOUT', 10],
    ['DENY_SECOND', 'DENY_CHECKOUT', 20],
  ]);
  assert.deepEqual(detail, before, 'conversion must not reorder or mutate the decoded owner detail');
});

test('decoded rules already in priority order remain stable and deterministic', () => {
  const detail = decodeRuleSetDetail({ ...ruleSetDetail, rules: prioritizedDirectionalRules() });
  const before = structuredClone(detail);
  const firstModel = detailToEditor(detail);
  const secondModel = detailToEditor(detail);

  assert.deepEqual(firstModel.rules.map(rule => [rule.ruleCode, rule.actionCode]), [
    ['ALLOW_FIRST', 'ALLOW_CHECKOUT'],
    ['DENY_SECOND', 'DENY_CHECKOUT'],
  ]);
  assert.deepEqual(secondModel, firstModel);
  const firstRequest = editorToCreateRequest(firstModel, null);
  assert.deepEqual(firstRequest.rules.map(rule => [rule.ruleCode, rule.priorityNo]), [
    ['ALLOW_FIRST', 10],
    ['DENY_SECOND', 20],
  ]);
  assert.deepEqual(editorToCreateRequest(secondModel, null), firstRequest);
  assert.deepEqual(detail, before, 'conversion must leave an already sorted owner detail unchanged');
});

for (const side of ['LEFT', 'RIGHT'] as const) for (const [operator, values] of [
  ['GT', ['10.00000000']], ['LT', ['10.00000000']], ['IN', ['9.00000000', '2.00000000']],
  ['BETWEEN', ['20.00000000', '3.00000000']], ['IS_NULL', []], ['IS_NOT_NULL', []],
] as const) test(`decoded ${side} context ${operator} preserves direction and literal order`, () => {
  const detail = decodeRuleSetDetail({ ...ruleSetDetail, rules: [{ ...ruleSetDetail.rules[0],
    conditionNodes: [wirePredicate(side, operator, [...values])] }] });
  const model = detailToEditor(detail);
  assert.deepEqual(model.rules[0]!.condition, { id: '987654321', kind: 'CONDITION', fieldSide: side,
    fieldCode: 'net_amount', operator, values: [...values] });
  const request = editorToCreateRequest(model, { ruleSetVersionId: detail.ruleSetVersionId, contentHash: detail.contentHash });
  assert.equal(request.baseRuleSetVersionId, '1');
  assert.equal(request.baseContentHash, 'a'.repeat(64));
  const node = request.rules[0]!.conditionNodes[0]!;
  assert.equal(node.comparisonOperator, operator);
  assert.deepEqual(node.operands, [
    { operandRole: side, valueSequence: 1, sourceType: 'CONTEXT', fieldCode: 'net_amount', valueType: 'NUMBER', literalValue: null },
    ...values.map((literalValue, index) => ({ operandRole: side === 'LEFT' ? 'RIGHT' : 'LEFT', valueSequence: index + 1,
      sourceType: 'LITERAL', fieldCode: null, valueType: 'NUMBER', literalValue })),
  ]);
});

test('decoded nested detail reconstructs sibling order and semantic round trip without reusing wire keys', () => {
  const group = (nodeKey: string, parentNodeKey: string | null, sequenceNo: number, groupOperator: 'AND' | 'OR' | 'NOT'): RuleConditionNode =>
    ({ nodeKey, parentNodeKey, sequenceNo, nodeType: 'GROUP', groupOperator, comparisonOperator: null, operands: [] });
  const detail = decodeRuleSetDetail({ ...ruleSetDetail, rules: [{ ...ruleSetDetail.rules[0], reasonCode: 'REVIEW_REQUIRED', enabledFlag: false,
    action: { actionCode: 'REVIEW_CHECKOUT', actionVersionNo: 1 }, conditionNodes: [
      { ...wirePredicate('RIGHT', 'IS_NULL', []), nodeKey: '60', parentNodeKey: '20', sequenceNo: 2 },
      group('10', null, 1, 'AND'), group('30', '20', 1, 'NOT'), group('20', '10', 1, 'OR'),
      { ...wirePredicate('LEFT', 'GT', ['10.00000000']), nodeKey: '50', parentNodeKey: '30' },
      { ...wirePredicate('RIGHT', 'GT', ['10.00000000']), nodeKey: '70', parentNodeKey: '10', sequenceNo: 2 },
    ] }] });
  const before = structuredClone(detail);
  const model = detailToEditor(detail);
  assert.deepEqual(model.rules[0]!.condition, { id: '10', kind: 'ALL', children: [
    { id: '20', kind: 'ANY', children: [
      { id: '30', kind: 'NOT', children: [{ id: '50', kind: 'CONDITION', fieldSide: 'LEFT', fieldCode: 'net_amount', operator: 'GT', values: ['10.00000000'] }] },
      { id: '60', kind: 'CONDITION', fieldSide: 'RIGHT', fieldCode: 'net_amount', operator: 'IS_NULL', values: [] },
    ] },
    { id: '70', kind: 'CONDITION', fieldSide: 'RIGHT', fieldCode: 'net_amount', operator: 'GT', values: ['10.00000000'] },
  ] });
  const request = editorToCreateRequest(model, null);
  const result = request.rules[0]!;
  assert.equal(result.enabledFlag, false);
  assert.equal(result.reasonCode, 'REVIEW_REQUIRED');
  assert.deepEqual(result.action, { actionCode: 'REVIEW_CHECKOUT', actionVersionNo: 1 });
  assert.deepEqual(result.conditionNodes.map(node => [node.nodeKey, node.parentNodeKey, node.sequenceNo, node.groupOperator]), [
    ['n1', null, 1, 'AND'], ['n2', 'n1', 1, 'OR'], ['n3', 'n2', 1, 'NOT'],
    ['n4', 'n3', 1, null], ['n5', 'n2', 2, null], ['n6', 'n1', 2, null],
  ]);
  for (const node of result.conditionNodes.filter(node => node.nodeType === 'GROUP')) {
    assert.equal(node.comparisonOperator, null);
    assert.deepEqual(node.operands, []);
  }
  const semanticRule = (rule: RuleDefinition) => ({ ...rule, priorityNo: 10,
    conditionNodes: rule.conditionNodes.map(({ nodeKey: _key, parentNodeKey: _parent, ...node }) => node) });
  const expectedPreorder = ['10', '20', '30', '50', '60', '70'];
  const orderedDetail = { ...detail.rules[0]!, conditionNodes: expectedPreorder.map(key => {
    const node = detail.rules[0]!.conditionNodes.find(node => node.nodeKey === key)!;
    return { ...node, operands: [...node.operands].sort((left, right) => left.sourceType === right.sourceType ? left.valueSequence - right.valueSequence : left.sourceType === 'CONTEXT' ? -1 : 1) };
  }) };
  assert.deepEqual(semanticRule(result), semanticRule(orderedDetail));
  assert.deepEqual(detail, before, 'conversion must not sort or mutate the decoded detail');
});

test('editor order determines rule priorities; generated node keys restart per rule and ignore local IDs', () => {
  const model = editor();
  model.rules = [editorRule(predicate({ id: 'n900' }), 2), editorRule(predicate({ fieldSide: 'RIGHT' }), 0), editorRule(predicate(), 1)];
  const before = structuredClone(model);
  const request = editorToCreateRequest(model, null);
  assert.deepEqual(request.rules.map(rule => [rule.ruleCode, rule.priorityNo]), [['RULE_2', 10], ['RULE_0', 20], ['RULE_1', 30]]);
  assert.deepEqual(request.rules.map(rule => rule.conditionNodes[0]!.nodeKey), ['n1', 'n1', 'n1']);
  assert.deepEqual(editorToCreateRequest(model, null), request, 'conversion is deterministic');
  assert.deepEqual(model, before);
});

for (const kind of ['ALL', 'ANY', 'NOT'] as const) for (const count of [0, 1, 2]) {
  test(`${kind} with ${count} children uses its exact cardinality`, () => {
    const condition: EditorCondition = { id: 'group', kind, children: Array.from({ length: count }, () => predicate()) };
    const valid = kind === 'NOT' ? count === 1 : count >= 2;
    (valid ? assert.doesNotThrow : assert.throws)(() => convert(condition));
  });
}
function depthCondition(depth: number): EditorCondition {
  let condition: EditorCondition = predicate();
  for (let index = 1; index < depth; index++) condition = { id: `depth-${index}`, kind: 'NOT', children: [condition] };
  return condition;
}
test('depth twelve is accepted; thirteen and extremely deep editor inputs fail without stack overflow', () => {
  assert.equal(convert(depthCondition(12)).rules[0]!.conditionNodes.length, 12);
  for (const depth of [13, 50_000]) assert.throws(() => convert(depthCondition(depth)), error => error instanceof Error && !(error instanceof RangeError));
});

test('zero rules cannot save; 200 rules save with complete priorities; 201 are rejected', () => {
  const model = editor(); model.rules = [];
  assert.throws(() => editorToCreateRequest(model, null));
  model.rules = Array.from({ length: 200 }, (_, index) => editorRule(predicate(), index));
  const request = editorToCreateRequest(model, null);
  assert.equal(request.rules.length, 200);
  assert.equal(request.rules.at(-1)!.priorityNo, 2000);
  model.rules.push(editorRule(predicate(), 200));
  assert.throws(() => editorToCreateRequest(model, null));
});
const wideGroup = (nodes: number): EditorCondition => ({ id: 'wide', kind: 'ALL',
  children: Array.from({ length: nodes - 1 }, (_, index) => predicate({ id: `leaf-${index}` })),
});
test('the generated node budget is 2000 across one rule or the whole document', () => {
  assert.equal(convert(wideGroup(2000)).rules[0]!.conditionNodes.length, 2000);
  assert.throws(() => convert(wideGroup(2001)));
  const model = editor(); model.rules = [editorRule(wideGroup(1000), 0), editorRule(wideGroup(1000), 1)];
  assert.equal(editorToCreateRequest(model, null).rules.reduce((count, rule) => count + rule.conditionNodes.length, 0), 2000);
  model.rules[1]!.condition = wideGroup(1001);
  assert.throws(() => editorToCreateRequest(model, null));
});
test('the generated operand budget includes contexts: 10000 passes, 10001 fails', () => {
  const model = editor();
  model.rules = Array.from({ length: 100 }, (_, index) => editorRule(predicate({ operator: 'IN', values: Array(99).fill('AE') }), index));
  assert.equal(editorToCreateRequest(model, null).rules.reduce((count, rule) => count + rule.conditionNodes[0]!.operands.length, 0), 10000);
  model.rules[99]!.condition = predicate({ operator: 'IN', values: Array(100).fill('AE') });
  assert.throws(() => editorToCreateRequest(model, null));
});

const stringOperators: OperatorCode[] = ['EQ', 'NE', 'IN', 'NOT_IN', 'IS_NULL', 'IS_NOT_NULL'];
const numberOperators: OperatorCode[] = ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NOT_IN', 'BETWEEN', 'IS_NULL', 'IS_NOT_NULL'];
for (const side of ['LEFT', 'RIGHT'] as const) for (const fieldCode of ['country_code', 'net_amount']) {
  for (const operator of numberOperators) test(`${side} ${fieldCode} ${operator} applies type matrix, literal arity and role sequence`, () => {
    const values = fieldCode === 'net_amount' ? ['10', '2'] : ['AE', 'US'];
    const make = (count: number) => predicate({ fieldSide: side, fieldCode, operator, values: Array.from({ length: count }, (_, i) => values[i % 2]!) });
    if (fieldCode === 'country_code' && !stringOperators.includes(operator)) {
      assert.throws(() => convert(make(operator === 'BETWEEN' ? 2 : 1))); return;
    }
    const set = operator === 'IN' || operator === 'NOT_IN';
    const nullary = operator === 'IS_NULL' || operator === 'IS_NOT_NULL';
    const count = nullary ? 0 : operator === 'BETWEEN' ? 2 : 1;
    for (const size of set ? [1, 100] : [count]) {
      const node = convert(make(size)).rules[0]!.conditionNodes[0]!;
      assert.equal(node.operands.length, size + 1);
      assert.equal(node.operands[0]!.operandRole, side);
      assert.equal(node.operands[0]!.valueSequence, 1);
      assert.deepEqual(node.operands.slice(1).map(operand => [operand.operandRole, operand.valueSequence, operand.literalValue]),
        Array.from({ length: size }, (_, index) => [side === 'LEFT' ? 'RIGHT' : 'LEFT', index + 1,
          fieldCode === 'net_amount' ? index % 2 === 0 ? '10.00000000' : '2.00000000' : values[index % 2]]));
    }
    for (const size of nullary ? [1, 2] : set ? [0, 101] : operator === 'BETWEEN' ? [0, 1, 3] : [0, 2]) assert.throws(() => convert(make(size)));
  });
}

const fieldVectors: [string, readonly string[], readonly unknown[]][] = [
  ['country_code', ['AE', 'US'], ['', 'A', 'ARE', 'ae', 'A1', 'AE\n', ' AE']],
  ['industry_code', ['A', 'RETAIL_2', 'A'.repeat(64)], ['', '_A', '1A', 'retail', 'HAS-SPACE', 'A'.repeat(65), 'A\r']],
  ['mcc_code', ['0000', '0123', '9999'], ['', '123', '12345', '12A4', '１２３４', '1234\n', 1234]],
  ['currency', ['USD', 'AED'], ['', 'US', 'USDD', 'usd', 'US1', 'USD\n']],
  ['entity_source_type', ['PLATFORM', 'CLIENT', 'PARTNER'], ['', 'platform', 'OTHER', 'CLIENT\n', ' CLIENT']],
  ['locale', ['en', 'en-US', 'ar-AE'], ['', ' ', 'en_US', 'en,ar', 'en ar', ' en', 'en\n', ['en'], ['en', 'ar'], {}, 'not_a_locale']],
];
for (const [fieldCode, valid, invalid] of fieldVectors) test(`${fieldCode} validates widget grammar on both field sides`, () => {
  for (const value of valid) for (const fieldSide of ['LEFT', 'RIGHT'] as const) {
    assert.equal(canonicalFieldValue(fieldCode, value), value);
    const output = convert(predicate({ fieldCode, fieldSide, values: [value] }));
    assert.equal(output.rules[0]!.conditionNodes[0]!.operands[1]!.literalValue, value);
  }
  for (const value of [...invalid, null, undefined, true]) for (const fieldSide of ['LEFT', 'RIGHT'] as const) {
    assert.throws(() => canonicalFieldValue(fieldCode, value as string));
    assert.throws(() => convert(predicate({ fieldCode, fieldSide, values: [value as string] })));
  }
  assert.deepEqual(operatorsForField(fieldCode), stringOperators);
});
test('locale normalization follows canonical BCP 47 casing through the real editor conversion', () => {
  for (const [value, expected] of [['en-us', 'en-US'], ['zh-hans-cn', 'zh-Hans-CN']] as const) {
    assert.equal(canonicalFieldValue('locale', value), expected);
    assert.equal(convert(predicate({ fieldCode: 'locale', values: [value] })).rules[0]!.conditionNodes[0]!.operands[1]!.literalValue, expected);
  }
});
for (const fieldCode of ['merchandise_amount', 'discount_amount', 'net_amount']) test(`${fieldCode} retains exact string precision on either side`, () => {
  assert.deepEqual(operatorsForField(fieldCode), numberOperators);
  for (const fieldSide of ['LEFT', 'RIGHT'] as const) {
    assert.equal(canonicalFieldValue(fieldCode, '9007199254740993.12'), '9007199254740993.12000000');
    assert.equal(convert(predicate({ fieldCode, fieldSide, values: ['9007199254740993.12'] })).rules[0]!.conditionNodes[0]!.operands[1]!.literalValue, '9007199254740993.12000000');
    for (const value of [-1, 1, 0, '-0', '+1', '01', '1e0', '10000000000000000', '1.000000001']) {
      assert.throws(() => convert(predicate({ fieldCode, fieldSide, values: [value as string] })));
    }
  }
});
test('unknown fields/operators/sides fail even for zero-literal or disabled conditions', () => {
  for (const fieldCode of ['unknown_field', 'constructor', '__proto__']) {
    assert.throws(() => canonicalFieldValue(fieldCode, 'AE'));
    assert.throws(() => operatorsForField(fieldCode));
    assert.throws(() => convert(predicate({ fieldCode, operator: 'IS_NULL', values: [] })));
  }
  for (const operator of ['UNKNOWN', 'eq', 'EQ\n']) assert.throws(() => convert(predicate({ operator })));
  assert.throws(() => convert(predicate({ fieldSide: 'MIDDLE' as 'LEFT' })));
  const model = editor(predicate({ operator: 'GT' })); model.rules[0]!.enabled = false;
  assert.throws(() => editorToCreateRequest(model, null));
});

test('outer names and change reason normalize trimmed NFC text; blank reason becomes explicit null', () => {
  const model = editor(); model.ruleSetName = '  Cafe\u0301  规则  '; model.changeReason = '  Re\u0301vision  ';
  model.rules[0]!.ruleName = '  Re\u0301vision  rule  '; model.rules[0]!.reasonCode = ' \t ';
  const request = editorToCreateRequest(model, null);
  assert.equal(request.ruleSetName, 'Café  规则'); assert.equal(request.changeReason, 'Révision');
  assert.equal(request.rules[0]!.ruleName, 'Révision  rule'); assert.equal(request.rules[0]!.reasonCode, null);
});
test('outer code/name/reason limits and duplicate rule codes are checked before serialization', () => {
  for (const code of ['', 'lower', '1CODE', 'A'.repeat(65), 'A\n', ' A']) {
    const model = editor(); model.ruleSetCode = code; assert.throws(() => editorToCreateRequest(model, null));
    model.ruleSetCode = 'RULE_SET'; model.rules[0]!.ruleCode = code; assert.throws(() => editorToCreateRequest(model, null));
    if (code !== '') { model.rules[0]!.ruleCode = 'RULE_0'; model.rules[0]!.reasonCode = code; assert.throws(() => editorToCreateRequest(model, null)); }
  }
  for (const field of ['ruleSetName', 'changeReason', 'ruleName'] as const) {
    const maximum = field === 'changeReason' ? 500 : 255;
    for (const value of ['', '  ', 'a'.repeat(maximum + 1), '😀'.repeat(Math.floor(maximum / 2) + 1)]) {
      const model = editor(); if (field === 'ruleName') model.rules[0]!.ruleName = value; else model[field] = value;
      assert.throws(() => editorToCreateRequest(model, null));
    }
    const model = editor(); if (field === 'ruleName') model.rules[0]!.ruleName = 'a'.repeat(maximum); else model[field] = 'a'.repeat(maximum);
    assert.doesNotThrow(() => editorToCreateRequest(model, null));
  }
  const model = editor(); model.rules.push(editorRule());
  assert.throws(() => editorToCreateRequest(model, null));
  model.rules = [editorRule()]; model.ruleSetCode = 'A'.repeat(64); model.rules[0]!.ruleCode = 'B'.repeat(64); model.rules[0]!.reasonCode = 'C'.repeat(64);
  assert.doesNotThrow(() => editorToCreateRequest(model, null));
});
test('action must match an available code at version one, with no coercion or arbitrary point', () => {
  for (const actionCode of ['ALLOW_CHECKOUT', 'REVIEW_CHECKOUT', 'DENY_CHECKOUT']) {
    const model = editor(); model.rules[0]!.actionCode = actionCode;
    if (actionCode === 'DENY_CHECKOUT') { model.rules[0]!.reasonCode = 'MANUAL_REASON'; model.rules[0]!.reasonMessage = 'Manual explanation'; }
    assert.deepEqual(editorToCreateRequest(model, null).rules[0]!.action, { actionCode, actionVersionNo: 1 });
  }
  for (const [actionCode, actionVersionNo] of [['OTHER', 1], ['allow_checkout', 1], ['ALLOW_CHECKOUT', 2], ['ALLOW_CHECKOUT', 0], ['ALLOW_CHECKOUT', 1.5], ['ALLOW_CHECKOUT', '1']] as const) {
    const model = editor(); Object.assign(model.rules[0]!, { actionCode, actionVersionNo });
    assert.throws(() => editorToCreateRequest(model, null));
  }
  const model = editor(); Object.assign(model, { pointCode: 'OTHER' });
  assert.throws(() => editorToCreateRequest(model, null));
  const malformed = editor(); Object.assign(malformed.rules[0]!, { enabled: 'false' });
  assert.throws(() => editorToCreateRequest(malformed, null));
});
test('base coordinates are explicit, exact and copied without accepting partial or malformed identities', () => {
  const base = { ruleSetVersionId: '123456789012345678901234567890', contentHash: '0123456789abcdef'.repeat(4) };
  const request = editorToCreateRequest(editor(), base);
  assert.equal(request.baseRuleSetVersionId, base.ruleSetVersionId); assert.equal(request.baseContentHash, base.contentHash);
  for (const ruleSetVersionId of ['', '0', '01', '-1', '1e0', '1\n', '1'.repeat(31), 1, null]) {
    assert.throws(() => editorToCreateRequest(editor(), { ...base, ruleSetVersionId } as RuleSetBase));
  }
  for (const contentHash of ['', 'a'.repeat(63), 'a'.repeat(65), 'A'.repeat(64), 'g'.repeat(64), 'a'.repeat(64) + '\n', null]) {
    assert.throws(() => editorToCreateRequest(editor(), { ...base, contentHash } as RuleSetBase));
  }
  for (const partial of [{ ruleSetVersionId: '1' }, { contentHash: base.contentHash }, undefined]) {
    assert.throws(() => editorToCreateRequest(editor(), partial as unknown as RuleSetBase));
  }
});
test('invalid literal diagnostics do not echo rule content', () => {
  const sensitive = 'UNAPPROVED_LITERAL_SENTINEL';
  assert.throws(() => convert(predicate({ values: [sensitive] })), error => error instanceof Error && !error.message.includes(sensitive));
});
