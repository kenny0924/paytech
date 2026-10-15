import test from 'node:test';
import assert from 'node:assert/strict';
import {
  authenticationLabel, conclusionTitle, plainDecimal, conditionLines, decodeEvaluation, decodeExplanation, evaluationQuerySearch, exitEdgeLabel, failureLabel, formatUtc, groupRules,
  INPUT_FIELDS, inputRows, isCurrentValue, normalizeId, NOT_PROVIDED, parseEvaluationQuery, PATH_UNAVAILABLE, predicateMark, predicateText, resultColor, resultLabel,
  ruleOutcome, stepName, stepSummary, variableBasisText, variableNameLookup, variableValueText,
} from './riskEvaluation.ts';
import type { ConditionNode } from './riskEvaluation.ts';
import { evaluationWire, explanationWire } from './riskEvaluationFixtures.ts';

test('URL 查询条件：类型只认 attempt/evaluation，ID 去空白；写回地址只含类型与 ID', () => {
  assert.deepEqual(parseEvaluationQuery('?type=evaluation&id=%20236600000000000001%20'), { type: 'evaluation', id: '236600000000000001' });
  assert.deepEqual(parseEvaluationQuery('?type=other&id=12'), { type: 'attempt', id: '12' });
  assert.deepEqual(parseEvaluationQuery(''), { type: 'attempt', id: '' });
  assert.equal(evaluationQuerySearch({ type: 'attempt', id: '991015000000000002' }), '?type=attempt&id=991015000000000002');
});

test('ID 必须是 1～19 位正整数且不超过 Long 上限；前导零被去掉', () => {
  assert.equal(normalizeId('1'), '1');
  assert.equal(normalizeId(' 0012 '), '12');
  assert.equal(normalizeId('9223372036854775807'), '9223372036854775807');
  for (const bad of ['', '0', '000', '-1', '1.5', 'abc', '12a', '9223372036854775808', '12345678901234567890']) assert.equal(normalizeId(bad), null, bad);
});

test('结论文案与颜色：放行绿、审核橙、拒绝红、失败/超时灰；放行需 3DS 写进标题', () => {
  assert.deepEqual(['ALLOW', 'REVIEW', 'DENY', 'FAILED', 'TIMED_OUT', 'NEW_RESULT'].map(resultColor), ['green', 'orange', 'red', 'default', 'default', 'default']);
  assert.deepEqual(['ALLOW', 'REVIEW', 'DENY', 'FAILED', 'TIMED_OUT', 'NEW_RESULT'].map(resultLabel), ['放行', '审核', '拒绝', '失败', '超时', 'NEW_RESULT']);
  assert.equal(conclusionTitle({ result: 'ALLOW', outputs: { AUTHENTICATION_REQUIREMENT: 'THREE_DS_REQUIRED' } }), '放行（需要 3DS）');
  assert.equal(conclusionTitle({ result: 'ALLOW', outputs: { AUTHENTICATION_REQUIREMENT: 'NOT_REQUIRED' } }), '放行');
  assert.equal(conclusionTitle({ result: 'DENY', outputs: {} }), '拒绝');
  assert.equal(authenticationLabel({ AUTHENTICATION_REQUIREMENT: 'THREE_DS_REQUIRED' }), '需要 3DS');
  assert.equal(authenticationLabel({}), null);
  assert.equal(failureLabel('FEATURE_ERROR'), 'FEATURE_ERROR（变量计算失败）');
  assert.equal(failureLabel(null), '—');
  assert.equal(formatUtc('2026-10-15T16:01:52.383274Z'), '2026-10-15 16:01:52 UTC');
});

test('结论解码：可空字段缺省或 null 均可，影子评估带基线；ID 格式错误视为无效响应', () => {
  const primary = decodeEvaluation(evaluationWire);
  assert.equal(primary.mode, 'PRIMARY');
  assert.deepEqual(primary.outputs, { AUTHENTICATION_REQUIREMENT: 'THREE_DS_REQUIRED' });
  const { reasonCode: _r, baselineEvaluationId: _b, ...withoutNulls } = evaluationWire;
  assert.equal(decodeEvaluation(withoutNulls).reasonCode, null);
  const shadow = decodeEvaluation({ ...evaluationWire, evaluationId: '236600000000000007', mode: 'SHADOW', baselineEvaluationId: '236600000000000001', shadowRunId: '236600000000000100' });
  assert.equal(shadow.baselineEvaluationId, '236600000000000001');
  assert.throws(() => decodeEvaluation({ ...evaluationWire, evaluationId: 'abc' }));
  assert.throws(() => decodeEvaluation({ ...evaluationWire, pathAvailable: 'yes' }));
});

test('路径：步骤名称缺省用节点类型，规则集写编码版本与命中，结束节点写结论；pathAvailable=false 给出提示文案', () => {
  const explanation = decodeExplanation(explanationWire);
  const [start, block, auth, end] = explanation.path;
  assert.equal(stepName(start!), '开始');
  assert.equal(exitEdgeLabel(start!), 'e1');
  assert.equal(stepSummary(block!), '规则集 PAY_BLOCK v2 · 未命中');
  assert.equal(stepSummary(auth!), '规则集 PAY_AUTH v1 · 命中 · 放行');
  assert.equal(auth!.reused, true);
  assert.equal(stepSummary(end!), '结束 · 放行');
  assert.equal(exitEdgeLabel(end!), null);
  const legacy = decodeExplanation({ ...explanationWire, pathAvailable: false, path: [] });
  assert.equal(legacy.pathAvailable, false);
  assert.equal(PATH_UNAVAILABLE, '路径未记录（该评估早于路径记录功能上线）');
});

test('规则按规则集分组并保持执行顺序；命中、终态与动作写在结果里', () => {
  const explanation = decodeExplanation(explanationWire);
  const groups = groupRules(explanation.rules, explanation.path);
  assert.deepEqual(groups.map(g => [g.title, g.rules.map(r => r.ruleCode)]), [['PAY_BLOCK v2', ['BLOCKED_CARD', 'LARGE_AMOUNT']], ['PAY_AUTH v1', ['THREE_DS_AMOUNT']]]);
  assert.equal(ruleOutcome(groups[1]!.rules[0]!), '命中 · 终态 · ALLOW_PAYMENT');
  assert.equal(ruleOutcome(groups[0]!.rules[1]!), '未命中 · REVIEW_PAYMENT');
  assert.equal(groupRules(explanation.rules, [])[0]!.title, '236500000000000002', '路径缺失时用规则集版本 ID');
});

test('条件树转为缩进行：分组写满足方式，谓词写变量名、运算符与带币种的值，并标出成立/不成立/未求值', () => {
  const explanation = decodeExplanation(explanationWire);
  const nameOf = variableNameLookup(explanation.variables);
  const auth = explanation.rules[2]!;
  assert.deepEqual(conditionLines(auth.condition!, nameOf).map(l => [l.depth, l.text, l.result]), [
    [0, '全部满足', true], [1, '支付金额 大于 500（支付币种）', true], [1, '不满足', true], [2, '卡指纹在黑名单 等于 是', false],
  ]);
  const unevaluated = explanation.rules[1]!;
  assert.equal(conditionLines(unevaluated.condition!, nameOf)[0]!.result, null);
  assert.deepEqual([true, false, null].map(r => predicateMark(r).label), ['成立', '不成立', '未求值']);
  assert.deepEqual([true, false, null].map(r => predicateMark(r).mark), ['✓', '✗', '—']);
});

test('谓词表达：IN 列出全部值、BETWEEN 写区间、为空类无右值、固定币种与缺少变量名时显示编码', () => {
  const variable = (code: string, name: string | null = null) => ({ operandRole: 'LEFT', sourceType: 'VARIABLE', valueType: 'STRING', variableCode: code, variableName: name, literalValue: null, numericUnit: null, currencyCode: null, currencyVariableCode: null });
  const literal = (value: string, extra: Partial<ConditionNode['operands'][number]> = {}) => ({ operandRole: 'RIGHT', sourceType: 'LITERAL', valueType: 'STRING', variableCode: null, variableName: null, literalValue: value, numericUnit: null, currencyCode: null, currencyVariableCode: null, ...extra });
  const predicate = (operator: string, operands: ConditionNode['operands']): ConditionNode => ({ nodeKey: '1', nodeType: 'PREDICATE', groupOperator: null, comparisonOperator: operator, operands, result: null, children: [] });
  assert.equal(predicateText(predicate('IN', [variable('PAY_INPUT_COUNTRY', '国家'), literal('AE'), literal('SA')])), '国家 属于 AE、SA');
  assert.equal(predicateText(predicate('BETWEEN', [variable('AMOUNT'), literal('1', { numericUnit: 'MONEY', currencyCode: 'USD' }), literal('9', { numericUnit: 'MONEY', currencyCode: 'USD' })])), 'AMOUNT 介于 1（USD） 至 9（USD）');
  assert.equal(predicateText(predicate('GT', [variable('AMOUNT'), literal('5000.00000000', { valueType: 'NUMBER', numericUnit: 'MONEY', currencyCode: 'USD' })])), 'AMOUNT 大于 5000（USD）');
  assert.equal(predicateText(predicate('GT', [variable('COUNT'), literal('0.50000000', { valueType: 'NUMBER' })])), 'COUNT 大于 0.5');
  assert.equal(plainDecimal('12'), '12');
  assert.equal(plainDecimal('AE'), 'AE');
  assert.equal(predicateText(predicate('IS_NULL', [variable('CARD_BIN', '卡 BIN')])), '卡 BIN 为空');
});

test('变量：CURRENT 标注当前值，Feature 失败显示失败码且取值为“—”，集合列出成员', () => {
  const variables = decodeExplanation(explanationWire).variables;
  const byCode = (code: string) => variables.find(v => v.variableCode === code)!;
  assert.equal(variableBasisText(byCode('PAY_INPUT_AMOUNT')), '评估时');
  assert.equal(variableBasisText(byCode('CARD_COUNT_1H')), '评估时 2 ms');
  assert.equal(isCurrentValue(byCode('CARD_LIMIT')), true);
  assert.equal(variableBasisText(byCode('CARD_LIMIT')), '当前值');
  assert.equal(variableValueText(byCode('HIGH_RISK_COUNTRIES')), 'AE、SA');
  assert.equal(variableValueText(byCode('CARD_BLOCKED')), '—');
  assert.equal(variableBasisText(byCode('CARD_BLOCKED')), '失败 FEATURE_ERROR');
});

test('输入：17 个字段按合同顺序，缺失、null 与空串都显示为未提供', () => {
  assert.equal(INPUT_FIELDS.length, 17);
  const rows = inputRows(decodeExplanation(explanationWire).inputs);
  assert.equal(rows.length, 17);
  assert.equal(rows.find(r => r.key === 'clientIpAddress')!.value, '203.0.113.9');
  assert.equal(rows.find(r => r.key === 'consumerReference')!.value, null);
  assert.equal(rows.find(r => r.key === 'cardBin')!.value, null);
  assert.equal(rows.find(r => r.key === 'userAgent')!.value, null);
  assert.equal(NOT_PROVIDED, '未提供');
  assert.equal(inputRows({ newField: 'x' }).at(-1)!.label, 'newField', 'Risk 新增字段按原键追加');
});

test('解释解码：条件不可用的规则不带条件树；影子评估列表与变化标记保留', () => {
  const explanation = decodeExplanation({ ...explanationWire, rules: [{ ...explanationWire.rules[0], conditionAvailable: false, condition: null }] });
  assert.equal(explanation.rules[0]!.conditionAvailable, false);
  assert.equal(explanation.rules[0]!.condition, null);
  assert.deepEqual(decodeExplanation(explanationWire).shadows.map(s => [s.result, s.decisionChanged, s.outputChanged]), [['ALLOW', false, true]]);
  assert.throws(() => decodeExplanation({ ...explanationWire, inputs: { clientIpAddress: 7 } }));
  assert.throws(() => decodeExplanation({ ...explanationWire, rules: [{ ...explanationWire.rules[0], matched: 'yes' }] }));
});
