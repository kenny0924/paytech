import test from 'node:test';
import type { FlowEdge } from './decisionFlow.ts';
import assert from 'node:assert/strict';
import { initialGraph, removeNode, reorderExit, toWireGraph, decodeGraph, parseFlowNavigation, flowSearch } from './decisionFlow.ts';

test('new flow has no implicit default decision; graph wire excludes editor state', () => {
  const graph = initialGraph(); assert.equal(graph.nodes[1]!.decisionCode, null);
  const wire = toWireGraph({ ...graph, viewport: { zoom: 2 } } as typeof graph);
  assert.deepEqual(Object.keys(wire), ['schemaVersion', 'nodes', 'edges']);
  assert.deepEqual(decodeGraph(wire), graph);
  assert.throws(() => decodeGraph({ ...wire, viewport: {} }));
  assert.throws(() => decodeGraph({ ...wire, nodes: [{ ...wire.nodes[0], position: { x: 0, y: '0' } }] }));
});
test('remove node drops incident edges without rewiring and order is explicit', () => {
  const graph = initialGraph(); assert.equal(removeNode(graph, 'end').edges.length, 0);
  assert.deepEqual(removeNode(graph, 'start'), graph);
  const edges:FlowEdge[] = [1, 2].map(n => ({ ...graph.edges[0]!, edgeKey: `e${n}`, type: 'CONDITIONAL' as const, priority: n }));
  edges.push({ ...graph.edges[0]!, edgeKey: 'else', type: 'ELSE', priority: 0 });
  const next = reorderExit({ ...graph, edges }, 'e2', -1);
  assert.equal(next.edges.find(e => e.edgeKey === 'e2')!.priority, 1);
  assert.equal(next.edges.find(e => e.edgeKey === 'else')!.priority, 0);
});
test('URL preserves list filters and uses explicit version coordinates', () => {
  const query = '?pointCode=CHECKOUT_PREPARE&keyword=demo&page=2&size=20&view=version&flowId=11&flowVersionId=22&nodeKey=end';
  const nav = parseFlowNavigation(query); assert.equal(nav.view, 'version'); assert.equal(nav.page, 2);
  assert.deepEqual(parseFlowNavigation(flowSearch(nav)), nav);
  assert.equal(parseFlowNavigation('?view=edit&flowId=bad').view, 'list');
});
test('deleting a branch target keeps remaining condition priorities consecutive without adding a default', () => {
  const graph = initialGraph();
  graph.nodes.push({...graph.nodes[1]!,nodeKey:'kept'});
  graph.edges = [1,2,3].map(n=>({...graph.edges[0]!,edgeKey:`e${n}`,type:'CONDITIONAL',priority:n,targetNodeKey:n===2?'end':'kept'}));
  const next=removeNode(graph,'end');
  assert.deepEqual(next.edges.map(e=>[e.edgeKey,e.priority]),[['e1',1],['e3',2]]);
  assert.equal(next.edges.some(e=>e.type==='ELSE'),false);
});
