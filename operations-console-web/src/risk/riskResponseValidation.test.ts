import assert from 'node:assert/strict';
import test from 'node:test';
import type { RuleConditionNode, RuleDefinition, ValueType } from './riskApiModels.ts';
import * as decoders from './riskResponseValidation.ts';
import { atPath, candidateDetail, candidatePage, changed, createCandidateResponse, omitted, predicateRule,
  ruleSet, ruleSetDetail, ruleSetPage, validateCandidateResponse } from './riskFixtures.ts';
import type { WirePath } from './riskFixtures.ts';
import { invalidHashes, invalidIds, invalidInstants, literalMatrix, operatorMatrix, paths, successRoots, validInstants } from './riskContractFixtures.ts';

function historicalDetailWithProjection() {
  const raw = structuredClone(ruleSetDetail);
  return { ...raw, operandFormat: 'LEGACY', currentVariables: [], valuesAsOf: raw.createTime, variableIssues: [],
    rules: raw.rules.map(rule => ({ ...rule, conditionNodes: rule.conditionNodes.map(node => ({ ...node,
      operands: node.operands.map(operand => ({ ...operand, variableCode: null, numericUnit: null,
        currencyCode: null, currencyVariableCode: null })),
    })) })),
  };
}

test('legacy rule detail with current projection remains readable without changing its saved operands or hash', () => {
  const raw = historicalDetailWithProjection();
  const before = structuredClone(raw);
  const detail = decoders.decodeRuleSetDetail(raw);
  assert.equal(detail.operandFormat, 'LEGACY');
  assert.equal(detail.contentHash, raw.contentHash);
  assert.equal(detail.ruleSetVersionId, raw.ruleSetVersionId);
  assert.equal(detail.rules[0]!.conditionNodes[0]!.operands[0]!.sourceType, 'CONTEXT');
  assert.equal(detail.rules[0]!.conditionNodes[0]!.operands[0]!.fieldCode, 'country_code');
  assert.equal(detail.rules[0]!.conditionNodes[0]!.operands[1]!.literalValue,
    raw.rules[0]!.conditionNodes[0]!.operands[1]!.literalValue);
  assert.deepEqual(raw, before);
  const mixed = structuredClone(raw);
  Object.assign(mixed.rules[0]!.conditionNodes[0]!.operands[0]!, {
    sourceType: 'VARIABLE', fieldCode: null, variableCode: 'INPUT_COUNTRY_CODE',
  });
  assert.throws(() => decoders.decodeRuleSetDetail(mixed));
});

test('unknown rule operand formats remain invalid even when the detail shape is valid', () => {
  for (const operandFormat of ['FUTURE_FORMAT', 'legacy', '']) {
    assert.throws(() => decoders.decodeRuleSetDetail({ ...historicalDetailWithProjection(), operandFormat }));
  }
});

for (const [name, decode, fixture] of successRoots.filter(([name]) => ['rule page', 'rule detail', 'create rule', 'freeze rule'].includes(name))) {
  test(`${name}: absent legacy composition is an explicit null, never a missing or fabricated ID`, () => {
    const path: WirePath = name === 'rule page' ? ['items', '0', 'pointVersionId'] : ['pointVersionId'];
    const raw = changed(fixture, path, null);
    assert.deepEqual(decode(raw), raw);
    assert.throws(() => decode(omitted(raw, path)));
    for (const value of ['', '0', 0, true, {}, []]) assert.throws(() => decode(changed(raw, path, value)));
  });
}

for (const [name, decode, fixture] of successRoots) {
  test(`${name}: complete valid root returns independent nested copies`, () => {
    const raw = structuredClone(fixture); const result = decode(raw);
    assert.deepEqual(result, raw);
    for (const path of paths(raw)) {
      const value = atPath(raw, path);
      if (value && typeof value === 'object') assert.notEqual(atPath(result, path), value, path.join('.'));
    }
  });
  test(`${name}: exact keys, required nulls, shapes and scalar tokens at every path`, () => {
    for (const invalid of [null, [], 1, true, 'root']) assert.throws(() => decode(invalid));
    for (const path of paths(fixture)) {
      const value = atPath(fixture, path); const label = path.join('.') || 'root';
      if (value && typeof value === 'object' && !Array.isArray(value)) {
        assert.throws(() => decode(path.length ? changed(fixture, path, { ...value, unexpected: true }) : { ...fixture, unexpected: true }), `extra key at ${label}`);
      }
      if (!path.length) continue;
      if (!Array.isArray(atPath(fixture, path.slice(0, -1)))) assert.throws(() => decode(omitted(fixture, path)), `missing ${label}`);
      const invalidTokens = value === null ? [undefined, {}, []] : Array.isArray(value) ? [null, {}, '[]']
        : typeof value === 'object' ? [null, [], '{}'] : typeof value === 'boolean' ? [null, 0, 1, 'true', 'false']
          : typeof value === 'number' ? [null, '1', true, 1.5, -1, Number.MAX_SAFE_INTEGER + 1] : [null, 1, true, {}, []];
      const nullablePoint = ['rule page', 'rule detail', 'create rule', 'freeze rule'].includes(name) && path.at(-1) === 'pointVersionId';
      for (const token of invalidTokens.filter(token => !(token === null && (path.at(-1) === 'action' || nullablePoint)))) assert.throws(() => decode(changed(fixture, path, token)), `${label}: ${JSON.stringify(token)}`);
    }
  });
  test(`${name}: lexical IDs/hashes/times and positive integer boundaries on every applicable field`, () => {
    for (const path of paths(fixture)) {
      const key = String(path.at(-1)); const value = atPath(fixture, path);
      const invalid = /^(ruleSetVersionId|pointVersionId|releaseId|decisionPointId|auditEventId|actorId|createdBy|frozenBy)$/.test(key)
        ? invalidIds : /Hash$|^contentHash$|^triggerId$/.test(key) ? invalidHashes
          : /^(createTime|frozenTime|eventTime)$/.test(key) ? invalidInstants
            : typeof value === 'number' && key !== 'total' ? [0, -0] : [];
      for (const token of invalid) assert.throws(() => decode(changed(fixture, path, token)), `${path.join('.')}: ${JSON.stringify(token)}`);
    }
  });
  test(`${name}: closed tokens and bounded code/NFC text fields`, () => {
    for (const path of paths(fixture)) {
      const key = String(path.at(-1)); const value = atPath(fixture, path);
      if (value === null && !/^(reasonCode|errorCode|fieldCode)$/.test(key)) continue;
      let invalid: readonly unknown[] = [];
      if (/^(status|environmentCode|nodeType|groupOperator|comparisonOperator|sourceType|operandRole|valueType|fromStatus|toStatus|triggerType|checkStatus|severity)$/.test(key)) invalid = name === 'authoring' && key === 'status' ? ['', 'inactive', 'A'.repeat(65)] : ['UNKNOWN', '', String(value).toLowerCase()];
      else if (/^(ruleSetCode|ruleCode|reasonCode|actionCode|pointCode|checkCode|errorCode)$/.test(key)) invalid = ['', 'a', '1BAD', 'HAS SPACE', 'A\n', 'A\r', 'A'.repeat(65)];
      else if (key === 'fieldCode') invalid = ['', 'A', '1bad', 'has space', 'a\n', 'a\r', 'a'.repeat(65)];
      else if (/^(ruleSetName|ruleName|pointName|actionName|changeReason|errorSummary)$/.test(key)) {
        const maximum = key === 'changeReason' || key === 'errorSummary' ? 500 : 255;
        invalid = ['', ' ', ' leading', 'trailing ', 'e\u0301', 'x'.repeat(maximum + 1)];
        assert.doesNotThrow(() => decode(changed(fixture, path, 'x'.repeat(maximum))), `${key} maximum`);
      }
      for (const token of invalid) assert.throws(() => decode(changed(fixture, path, token)), `${path.join('.')}: ${JSON.stringify(token)}`);
    }
  });
}

test('Rule summary/detail nullable frozen fields, timestamp years and non-null reason codes', () => {
  for (const status of ['FROZEN']) {
    const item = { ...ruleSet, status, frozenBy: '9', frozenTime: validInstants[1] };
    assert.doesNotThrow(() => decoders.decodeRuleSetPage({ ...ruleSetPage, items: [item] }));
    for (const field of ['frozenBy', 'frozenTime']) assert.throws(() => decoders.decodeRuleSetPage({ ...ruleSetPage, items: [{ ...item, [field]: null }] }));
  }
  for (const createTime of validInstants) assert.doesNotThrow(() => decoders.decodeRuleSetPage({ ...ruleSetPage, items: [{ ...ruleSet, createTime }] }));
  for (const reasonCode of [null, 'A', 'COUNTRY_ALLOWED', 'A'.repeat(64)]) {
    assert.doesNotThrow(() => decodeRule({ ...predicateRule('STRING', 'EQ', 'LEFT', ['AE']), reasonCode }));
  }
});

const decodeRule = (rule: RuleDefinition) => decoders.decodeRuleSetDetail({ ...ruleSetDetail, rules: [rule] });
function groupRule(operator: 'AND' | 'OR' | 'NOT', children: number): RuleDefinition {
  const rule = predicateRule('STRING', 'EQ', 'LEFT', ['AE']); const predicate = rule.conditionNodes[0]!;
  return { ...rule, conditionNodes: [{ nodeKey: 'root', parentNodeKey: null, sequenceNo: 1, nodeType: 'GROUP', groupOperator: operator, comparisonOperator: null, operands: [] },
    ...Array.from({ length: children }, (_, index) => ({ ...structuredClone(predicate), nodeKey: `n${index}`, parentNodeKey: 'root', sequenceNo: index + 1 }))] };
}
function depthRule(depth: number): RuleDefinition {
  const rule = predicateRule('STRING', 'EQ', 'LEFT', ['AE']);
  const nodes: RuleConditionNode[] = Array.from({ length: depth - 1 }, (_, index) => ({ nodeKey: `n${index}`, parentNodeKey: index ? `n${index - 1}` : null, sequenceNo: 1, nodeType: 'GROUP', groupOperator: 'NOT', comparisonOperator: null, operands: [] }));
  nodes.push({ ...rule.conditionNodes[0]!, nodeKey: `n${depth - 1}`, parentNodeKey: depth === 1 ? null : `n${depth - 2}` });
  return { ...rule, conditionNodes: nodes };
}
test('AST: root/reachability/parent/sequence/group/predicate/action invariants', () => {
  for (const operator of ['AND', 'OR', 'NOT'] as const) for (const count of [0, 1, 2]) {
    const valid = operator === 'NOT' ? count === 1 : count === 2;
    (valid ? assert.doesNotThrow : assert.throws)(() => decodeRule(groupRule(operator, count)), `${operator}/${count}`);
  }
  assert.doesNotThrow(() => decodeRule(depthRule(12))); assert.throws(() => decodeRule(depthRule(13)));
  const valid = { ...ruleSetDetail, rules: [groupRule('AND', 2)] };
  const mutations: [WirePath, unknown][] = [
    [['rules', 0, 'conditionNodes'], []], [['rules', 0, 'conditionNodes', 0, 'sequenceNo'], 2],
    [['rules', 0, 'conditionNodes', 1, 'nodeKey'], 'root'], [['rules', 0, 'conditionNodes', 1, 'parentNodeKey'], 'absent'],
    [['rules', 0, 'conditionNodes', 1, 'parentNodeKey'], null], [['rules', 0, 'conditionNodes', 2, 'sequenceNo'], 1],
    [['rules', 0, 'conditionNodes', 2, 'sequenceNo'], 3], [['rules', 0, 'conditionNodes', 0, 'comparisonOperator'], 'EQ'],
    [['rules', 0, 'conditionNodes', 0, 'groupOperator'], null], [['rules', 0, 'conditionNodes', 0, 'operands'], valid.rules[0]!.conditionNodes[1]!.operands],
    [['rules', 0, 'conditionNodes', 1, 'groupOperator'], 'AND'], [['rules', 0, 'conditionNodes', 1, 'comparisonOperator'], null],
    [['rules', 0, 'conditionNodes', 2, 'parentNodeKey'], 'n0'], [['rules', 0, 'action', 'actionVersionNo'], 0],
  ];
  for (const [path, replacement] of mutations) assert.throws(() => decoders.decodeRuleSetDetail(changed(valid, path, replacement)), path.join('.'));
  const cycle = depthRule(3); const nodes = [...structuredClone(cycle.conditionNodes)];
  nodes[0]!.parentNodeKey = 'n1'; nodes.push(...predicateRule('STRING', 'EQ', 'LEFT', ['AE']).conditionNodes);
  assert.throws(() => decodeRule({ ...cycle, conditionNodes: nodes }), 'disconnected cycle');
  for (const field of ['ruleCode', 'priorityNo'] as const) {
    const second = { ...structuredClone(valid.rules[0]!), ruleCode: 'SECOND', priorityNo: 2, [field]: valid.rules[0]![field] };
    assert.throws(() => decoders.decodeRuleSetDetail({ ...valid, rules: [valid.rules[0], second] }), `duplicate ${field}`);
  }
});

test('AST: Rule Set Detail requires at least one rule', () => {
  assert.doesNotThrow(() => decodeRule(predicateRule('STRING', 'EQ', 'LEFT', ['AE'])));
  assert.throws(() => decoders.decodeRuleSetDetail({ ...ruleSetDetail, rules: [] }), 'empty Rule array');
});

for (const [limit, good, bad] of [
  ['rules', 200, 201], ['nodes', 2000, 2001], ['operands', 10000, 10001],
] as const) test(`AST: whole-document ${good}/${bad} ${limit} boundary`, () => {
  function fixture(count: number) {
    if (limit === 'rules') return { ...ruleSetDetail, rules: Array.from({ length: count }, (_, index) => ({ ...predicateRule('STRING', 'EQ', 'LEFT', ['AE']), ruleCode: `RULE_${index}`, priorityNo: index + 1 })) };
    if (limit === 'nodes') return { ...ruleSetDetail, rules: [groupRule('AND', 999), { ...groupRule('AND', count - 1001), ruleCode: 'SECOND', priorityNo: 2 }] };
    return { ...ruleSetDetail, rules: Array.from({ length: 100 }, (_, index) => ({ ...predicateRule('STRING', 'IN', 'LEFT', Array(index === 99 ? count - 9901 : 99).fill('AE')), ruleCode: `RULE_${index}`, priorityNo: index + 1 })) };
  }
  assert.doesNotThrow(() => decoders.decodeRuleSetDetail(fixture(good)));
  assert.throws(() => decoders.decodeRuleSetDetail(fixture(bad)), `${bad} aggregate ${limit}`);
});
test('AST: one-rule node boundary is 2000', () => {
  assert.doesNotThrow(() => decodeRule(groupRule('AND', 1999)));
  assert.throws(() => decodeRule(groupRule('AND', 2000)));
});

for (const [name, decode, fixture, identity] of [
  ['rule', decoders.decodeRuleSetPage, ruleSetPage, 'ruleSetVersionId'],
  ['candidate', decoders.decodeCandidatePage, candidatePage, 'releaseId'],
] as const) test(`${name} page: exact windows, empty pages and unique identities`, () => {
  for (const [page, size, total, count, valid] of [[1, 10, 0, 0, true], [2, 10, 11, 1, true], [3, 10, 11, 0, true], [1, 10, 11, 1, false], [2, 10, 11, 0, false], [2, 10, 10, 1, false], [1, 10, -1, 0, false]] as const) {
    (valid ? assert.doesNotThrow : assert.throws)(() => decode({ ...fixture, items: count ? fixture.items : [], page, size, total }), `${page}/${size}/${total}/${count}`);
  }
  assert.throws(() => decode({ ...fixture, items: [fixture.items[0], fixture.items[0]], total: 2 }), `duplicate ${identity}`);
  assert.throws(() => decode({ ...fixture, items: [], page: Number.MAX_SAFE_INTEGER, size: 200, total: 0 }));
});

for (const state of ['draft', 'pending', 'passed', 'rejected'] as const) test(`Candidate ${state}: exhaustive root tuple and ordered transition matrix`, () => {
  const fixture = candidateDetail(state);
  for (const status of ['DRAFT', 'VALIDATING', 'REJECTED']) for (const version of [1, 2, 3]) for (const passed of [null, true, false]) for (const waiting of [false, true]) {
    const valid = status === fixture.status && version === fixture.version && passed === fixture.allStaticChecksPassed && waiting === fixture.waitingForShadow;
    (valid ? assert.doesNotThrow : assert.throws)(() => decoders.decodeCandidateDetail({ ...fixture, status, version, allStaticChecksPassed: passed, waitingForShadow: waiting }), `${status}/${version}/${passed}/${waiting}`);
  }
  assert.match(fixture.stateChanges[0]!.triggerId, /^[0-9a-f]{64}$/); assert.match(fixture.stateChanges[0]!.triggerId, /[a-f]/);
  for (let index = 0; index < fixture.stateChanges.length; index++) {
    for (const [field, value] of [['fromStatus', 'REJECTED'], ['toStatus', 'REJECTED'], ['reasonCode', 'OTHER'], ['aggregateVersion', 4], ['triggerType', 'JOB']] as const) {
      if (fixture.stateChanges[index]![field] !== value) assert.throws(() => decoders.decodeCandidateDetail(changed(fixture, ['stateChanges', index, field], value)), `${index}.${field}`);
    }
    assert.throws(() => decoders.decodeCandidateDetail(changed(fixture, ['stateChanges'], fixture.stateChanges.filter((_, i) => i !== index))), `missing event ${index}`);
  }
  assert.throws(() => decoders.decodeCandidateDetail(changed(fixture, ['stateChanges', 0, 'actorId'], '22')), 'creation actor');
  if (fixture.stateChanges.length > 1) {
    assert.throws(() => decoders.decodeCandidateDetail(changed(fixture, ['stateChanges', 1, 'auditEventId'], fixture.stateChanges[0]!.auditEventId)), 'duplicate audit');
    assert.throws(() => decoders.decodeCandidateDetail(changed(fixture, ['stateChanges', 1, 'eventTime'], '2026-01-01T00:00:00.000000Z')), 'backwards time');
    assert.throws(() => decoders.decodeCandidateDetail(changed(fixture, ['stateChanges'], [...fixture.stateChanges].reverse())), 'event order');
  }
  if (state === 'rejected') for (const [field, value] of [['actorId', '88'], ['triggerId', 'c'.repeat(64)]] as const) {
    assert.throws(() => decoders.decodeCandidateDetail(changed(fixture, ['stateChanges', 2, field], value)), `rejection ${field}`);
  }
  if (state === 'draft' || state === 'pending') assert.throws(() => decoders.decodeCandidateDetail(changed(fixture, ['staticChecks'], candidateDetail('passed').staticChecks)));
});

for (const rejected of [false, true]) for (const target of ['detail', 'validate'] as const) {
  test(`${target}: ${rejected ? 'REJECTED/false rejects an all-PASSED' : 'VALIDATING/true rejects a FAILED'} valid check vector`, () => {
    const fixture = target === 'detail' ? candidateDetail(rejected ? 'rejected' : 'passed') : validateCandidateResponse(rejected);
    const opposite = target === 'detail' ? candidateDetail(rejected ? 'passed' : 'rejected') : validateCandidateResponse(!rejected);
    const decode = target === 'detail' ? decoders.decodeCandidateDetail : decoders.decodeValidateCandidateResponse;
    const key = target === 'detail' ? 'staticChecks' : 'checks';
    assert.doesNotThrow(() => decode(fixture), 'original aggregate state is valid');
    assert.doesNotThrow(() => decode(opposite), 'replacement vector is valid under its matching aggregate state');
    assert.throws(() => decode(changed(fixture, [key], atPath(opposite, [key]))), 'only aggregate state/check correlation differs');
  });
  test(`${target} ${rejected ? 'rejected' : 'passed'}: all eight check identities/order/status/error tuples`, () => {
    const fixture = target === 'detail' ? candidateDetail(rejected ? 'rejected' : 'passed') : validateCandidateResponse(rejected);
    const decode = target === 'detail' ? decoders.decodeCandidateDetail : decoders.decodeValidateCandidateResponse;
    const key = target === 'detail' ? 'staticChecks' : 'checks'; const checks = atPath(fixture, [key]) as Record<string, unknown>[];
    for (let index = 0; index < 8; index++) {
      assert.throws(() => decode(changed(fixture, [key], checks.filter((_, i) => i !== index))), `missing check ${index}`);
      assert.throws(() => decode(changed(fixture, [key, index, 'checkCode'], 'UNKNOWN_CHECK')), `unknown check ${index}`);
      const swapped = [...checks]; [swapped[index], swapped[(index + 1) % 8]] = [swapped[(index + 1) % 8]!, swapped[index]!];
      assert.throws(() => decode(changed(fixture, [key], swapped)), `check order ${index}`);
      for (const [field, value] of checks[index]!.checkStatus === 'PASSED'
        ? [['severity', 'ERROR'], ['errorCode', 'FAILED'], ['errorSummary', 'Failure']]
        : [['severity', 'INFO'], ['errorCode', null], ['errorSummary', null]]) {
        assert.throws(() => decode(changed(fixture, [key, index, String(field)], value)), `${index}.${field}`);
      }
    }
    for (const value of [[], [...checks, checks[0]], [...checks.slice(0, 7), checks[0]]]) assert.throws(() => decode(changed(fixture, [key], value)));
    if (target === 'detail') assert.throws(() => decode(changed(fixture, [key, 1, 'auditEventId'], checks[0]!.auditEventId)));
    if (rejected) {
      for (let index = 0; index < 8; index++) {
        const alternate = candidateDetail('passed').staticChecks.map((check, i) => i === index ? { ...check, checkStatus: 'FAILED', severity: 'ERROR', errorCode: 'FAILED', errorSummary: 'Failure' } : check);
        const values = target === 'detail' ? alternate : alternate.map(({ auditEventId: _id, eventTime: _time, ...check }) => check);
        assert.doesNotThrow(() => decode(changed(fixture, [key], values)), `failure at check ${index}`);
      }
    }
  });
}
test('Candidate page/create/validate roots reject illegal status/version/result combinations', () => {
  for (const status of ['DRAFT', 'VALIDATING', 'REJECTED']) for (const version of [1, 2, 3]) for (const passed of [null, false, true]) {
    const valid = status === 'DRAFT' ? version === 1 && passed === null : status === 'VALIDATING' ? version === 2 && passed !== false : version === 3 && passed === false;
    (valid ? assert.doesNotThrow : assert.throws)(() => decoders.decodeCandidatePage({ ...candidatePage, items: [{ ...candidatePage.items[0], status, version, allStaticChecksPassed: passed }] }));
  }
  for (const [path, value] of [[['status'], 'VALIDATING'], [['version'], 2], [['initialStateChange', 'reasonCode'], 'OTHER'], [['initialStateChange', 'actorId'], '9']] as const) assert.throws(() => decoders.decodeCreateCandidateResponse(changed(createCandidateResponse, path, value)));
  for (const rejected of [false, true]) for (const [path, value] of [[['status'], rejected ? 'VALIDATING' : 'REJECTED'], [['version'], rejected ? 2 : 3], [['allStaticChecksPassed'], rejected]] as const) assert.throws(() => decoders.decodeValidateCandidateResponse(changed(validateCandidateResponse(rejected), path, value)));
});

for (const side of ['LEFT', 'RIGHT'] as const) {
  for (const [valueType, matrix] of Object.entries(literalMatrix) as [ValueType, typeof literalMatrix[ValueType]][]) {
    test(`${side} context ${valueType}: complete positive/negative literal grammar`, () => {
      for (const value of matrix.valid) assert.doesNotThrow(() => decodeRule(predicateRule(valueType, 'EQ', side, [value])), JSON.stringify(value));
      for (const value of matrix.invalid) assert.throws(() => decodeRule(predicateRule(valueType, 'EQ', side, [value])), JSON.stringify(value));
    });
    for (const [operator, count, allowed] of operatorMatrix) {
      test(`${side} context ${valueType}/${operator}: types, roles, sequences and cardinality`, () => {
        const value = matrix.valid[0]; const make = (size: number) => predicateRule(valueType, operator, side, Array(size).fill(value));
        if (!(allowed as readonly string[]).includes(valueType)) { assert.throws(() => decodeRule(make(count))); return; }
        const set = operator === 'IN' || operator === 'NOT_IN';
        for (const size of set ? [1, 100] : [count]) assert.doesNotThrow(() => decodeRule(make(size)), `count ${size}`);
        for (const size of count === 0 ? [1, 2] : operator === 'BETWEEN' ? [0, 1, 3] : set ? [0, 101] : [0, 2]) assert.throws(() => decodeRule(make(size)), `count ${size}`);
        const fixture = { ...ruleSetDetail, rules: [make(count)] }; const operands = fixture.rules[0]!.conditionNodes[0]!.operands;
        const violations: [string, unknown][] = [
          ['no context', operands.slice(1)], ['two contexts', [operands[0], ...operands]],
          ['context sequence', [{ ...operands[0], valueSequence: 2 }, ...operands.slice(1)]],
          ['context literal', [{ ...operands[0], literalValue: value }, ...operands.slice(1)]],
          ['context field null', [{ ...operands[0], fieldCode: null }, ...operands.slice(1)]],
          ['unknown role', [{ ...operands[0], operandRole: 'OTHER' }, ...operands.slice(1)]],
          ['unknown source', [{ ...operands[0], sourceType: 'FEATURE' }, ...operands.slice(1)]],
        ];
        if (count) violations.push(
          ['same roles', [operands[0], ...operands.slice(1).map(item => ({ ...item, operandRole: side }))]],
          ['literal sequence gap', [operands[0], ...operands.slice(1).map(item => ({ ...item, valueSequence: item.valueSequence + 1 }))]],
          ['literal field', [operands[0], { ...operands[1], fieldCode: 'field_name' }, ...operands.slice(2)]],
          ['type mismatch', [operands[0], { ...operands[1], valueType: valueType === 'STRING' ? 'NUMBER' : 'STRING' }, ...operands.slice(2)]],
        );
        for (const [label, replacement] of violations) assert.throws(() => decoders.decodeRuleSetDetail(changed(fixture, ['rules', 0, 'conditionNodes', 0, 'operands'], replacement)), label);
        if (set || operator === 'BETWEEN') {
          const many = { ...ruleSetDetail, rules: [make(2)] }; const ops = many.rules[0]!.conditionNodes[0]!.operands;
          for (const sequence of [1, 3]) assert.throws(() => decoders.decodeRuleSetDetail(changed(many, ['rules', 0, 'conditionNodes', 0, 'operands', 2, 'valueSequence'], sequence)));
          assert.throws(() => decoders.decodeRuleSetDetail(changed(many, ['rules', 0, 'conditionNodes', 0, 'operands'], [ops[0], ops[2], ops[1]])));
        }
      });
    }
  }
}
