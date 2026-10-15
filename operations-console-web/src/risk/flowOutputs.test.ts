import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeGraph, initialGraph, outputDeclarations, outputsForDecision, outputValue, PAYMENT_OUTPUT_FALLBACK, toWireGraph } from './decisionFlow.ts';
import type { FlowGraph } from './decisionFlow.ts';
import { decodeFlowContext } from './decisionFlowApi.ts';

function allowEnd(outputs?:Record<string,string>):FlowGraph {
  const graph = initialGraph();
  return { ...graph, nodes: graph.nodes.map(n => n.type === 'END' ? { ...n, decisionCode: 'ALLOW' as const, ...(outputs ? { outputs } : {}) } : n) };
}

test('allow end node outputs round-trip; nodes without outputs keep the original wire shape', () => {
  const withOutputs = toWireGraph(allowEnd({ AUTHENTICATION_REQUIREMENT: 'THREE_DS_REQUIRED' }));
  assert.deepEqual(withOutputs.nodes[1]!.outputs, { AUTHENTICATION_REQUIREMENT: 'THREE_DS_REQUIRED' });
  assert.deepEqual(decodeGraph(withOutputs).nodes[1]!.outputs, { AUTHENTICATION_REQUIREMENT: 'THREE_DS_REQUIRED' });
  const plain = toWireGraph(allowEnd());
  assert.equal('outputs' in plain.nodes[1]!, false);
  assert.equal('outputs' in plain.nodes[0]!, false);
  assert.equal('outputs' in toWireGraph(allowEnd({})).nodes[1]!, false);
});

test('output decoding rejects empty, oversized and non-code entries', () => {
  const wire = toWireGraph(allowEnd({ AUTHENTICATION_REQUIREMENT: 'THREE_DS_REQUIRED' }));
  const node = (outputs:unknown) => ({ ...wire, nodes: [wire.nodes[0], { ...wire.nodes[1], outputs }] });
  assert.throws(() => decodeGraph(node({})));
  assert.throws(() => decodeGraph(node({ authentication: 'THREE_DS_REQUIRED' })));
  assert.throws(() => decodeGraph(node({ AUTHENTICATION_REQUIREMENT: true })));
  assert.throws(() => decodeGraph(node(Object.fromEntries(Array.from({ length: 9 }, (_, i) => [`K${i}`, 'V'])))));
});

test('changing decision keeps only outputs allowed for the new decision', () => {
  const outputs = { AUTHENTICATION_REQUIREMENT: 'THREE_DS_REQUIRED' };
  assert.deepEqual(outputsForDecision(outputs, 'ALLOW', PAYMENT_OUTPUT_FALLBACK), outputs);
  assert.equal(outputsForDecision(outputs, 'DENY', PAYMENT_OUTPUT_FALLBACK), undefined);
  assert.equal(outputsForDecision(outputs, 'REVIEW', PAYMENT_OUTPUT_FALLBACK), undefined);
  assert.equal(outputValue({}, PAYMENT_OUTPUT_FALLBACK[0]!), 'NOT_REQUIRED');
  assert.equal(outputValue({ outputs }, PAYMENT_OUTPUT_FALLBACK[0]!), 'THREE_DS_REQUIRED');
});

const baseContext = { supportedPoints: [{ pointCode: 'PAYMENT_ATTEMPT_RISK', pointName: '支付尝试' }], pointCode: 'PAYMENT_ATTEMPT_RISK', inputContractCode: 'PAYMENT_ATTEMPT', inputContractVersion: 1, graphSchemaVersion: 1, fields: [], nodeOutputs: [],
  limits: { bodyBytes: 65536, nodes: 50, edges: 100, conditionBytes: 8192, conditionNodes: 32, conditionDepth: 8 } };

test('authoring context declarations come from Risk when present, otherwise payment falls back and checkout has none', () => {
  const declared = decodeFlowContext({ ...baseContext, outputs: [{ outputCode: 'AUTHENTICATION_REQUIREMENT', name: '认证要求',
    allowedValues: [{ value: 'NOT_REQUIRED', name: '无需认证' }, { value: 'THREE_DS_REQUIRED', name: '需要 3DS' }], defaultValue: 'NOT_REQUIRED', allowedEndDecisions: ['ALLOW'] }] });
  assert.equal(outputDeclarations(declared)[0]!.allowedValues[1]!.name, '需要 3DS');
  assert.deepEqual(outputDeclarations(decodeFlowContext(baseContext)), PAYMENT_OUTPUT_FALLBACK);
  assert.deepEqual(outputDeclarations(decodeFlowContext({ ...baseContext, pointCode: 'CHECKOUT_PREPARE' })), []);
  assert.throws(() => decodeFlowContext({ ...baseContext, outputs: [{ outputCode: 'AUTHENTICATION_REQUIREMENT', name: '认证要求', allowedValues: [], defaultValue: 'NOT_REQUIRED', allowedEndDecisions: ['ALLOW'] }] }));
  assert.throws(() => decodeFlowContext({ ...baseContext, outputs: [{ outputCode: 'AUTHENTICATION_REQUIREMENT', name: '认证要求',
    allowedValues: [{ value: 'NOT_REQUIRED', name: '无需认证' }], defaultValue: 'THREE_DS_REQUIRED', allowedEndDecisions: ['ALLOW'] }] }));
});
