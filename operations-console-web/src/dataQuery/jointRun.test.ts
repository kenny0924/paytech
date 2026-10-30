import assert from 'node:assert/strict';
import test from 'node:test';
import { chunkKeys, initialRun, loadedSubjects, mergeStep, nextKeys, normalizeKeyValue, retryNode, runJointQuery, subjectValues, visitKey } from './jointRun.ts';
import type { DriveOptions, JointRunState } from './jointRun.ts';
import type { JointLimits, JointNode, JointStepRequest, JointStepResponse, StepKeys } from './jointModel.ts';

const limits: JointLimits = { maxEntryValues: 20, maxValuesPerKey: 50, maxRowsPerTemplate: 100, maxExecutionsPerStep: 20, maxDepth: 6,
  maxTemplateExecutions: 60, maxTotalSeconds: 60 };

function node(depth: number, templateCode: string, triggerKeyType: string, triggerValues: string[], patch: Partial<JointNode> = {}): JointNode {
  return { nodeId: `${depth}:${templateCode}:${triggerKeyType}`, templateCode, templateName: templateCode, versionNo: 1, dataSourceCode: 'DS', subjectKeyType: triggerKeyType,
    groupName: 'G', laneRank: 10, subjectSortNo: 10, triggerKeyType, keyParameter: 'ids', keyParameterList: null, triggerValues, status: 'SUCCEEDED', errorCode: null,
    errorMessage: null, pagingMode: 'OFFSET', truncated: false, elapsedMillis: 5, columns: [], rows: [], warnings: [], nextCursor: null, ...patch };
}
const response = (depth: number, nodes: JointNode[], discoveredKeys: StepKeys[] = [], links: JointStepResponse['links'] = []): JointStepResponse =>
  ({ depth, nodes, links, discoveredKeys, truncations: [], elapsedMillis: 10, alreadyLoaded: [] });

/** 退款单 → 订单 → 订单的全部退款（含已访问的退款单 R1 与新的 R2）→ 退款 R2 再回到订单（已访问）。 */
function refundChain(request: JointStepRequest): JointStepResponse {
  const key = request.keys[0];
  if (key.keyType === 'REFUND_NO') return response(request.depth, [node(request.depth, 'REFUND_BY_NO', 'REFUND_NO', key.values)], [{ keyType: 'ORDER_ID', values: ['100'] }],
    [{ nodeId: `${request.depth}:REFUND_BY_NO:REFUND_NO`, rowIndex: 0, column: 'order_id', keyType: 'ORDER_ID', value: '100' }]);
  if (key.keyType === 'ORDER_ID') return response(request.depth, [node(request.depth, 'REFUNDS_BY_ORDER', 'ORDER_ID', key.values)],
    [{ keyType: 'REFUND_NO', values: ['R1', 'R2'] }, { keyType: 'ORDER_ID', values: ['100'] }]);
  return response(request.depth, [node(request.depth, 'REFUND_BY_NO', 'REFUND_NO', key.values)], [{ keyType: 'ORDER_ID', values: ['100'] }]);
}

function harness(step: (request: JointStepRequest) => JointStepResponse | Promise<JointStepResponse>, patch: Partial<JointLimits> = {}, clock = { now: 0 }) {
  const requests: JointStepRequest[] = []; const updates: JointRunState[] = [];
  const controller = new AbortController();
  const options: DriveOptions = { limits: { ...limits, ...patch }, signal: controller.signal, now: () => clock.now, onUpdate: state => updates.push(state),
    step: async request => { requests.push(request); return step(request); } };
  return { requests, updates, controller, options, clock };
}

test('逐层驱动：扣除已访问的 (键类型, 值)，没有新键时结束', async () => {
  const run = harness(refundChain);
  const state = await runJointQuery({ keyType: 'REFUND_NO', values: ['R1'] }, run.options);
  assert.deepEqual(run.requests.map(request => [request.depth, request.keys]), [
    [0, [{ keyType: 'REFUND_NO', values: ['R1'] }]],
    [1, [{ keyType: 'ORDER_ID', values: ['100'] }]],
    [2, [{ keyType: 'REFUND_NO', values: ['R2'] }]],
  ]);
  assert.equal(state.status, 'done');
  assert.equal(state.executions, 3);
  assert.deepEqual(state.nodes.map(item => [item.id, item.depth]), [['0:REFUND_BY_NO:REFUND_NO', 0], ['1:REFUNDS_BY_ORDER:ORDER_ID', 1], ['2:REFUND_BY_NO:REFUND_NO', 2]]);
  assert.ok(state.visited.has(visitKey('REFUND_NO', 'R2')) && state.visited.has(visitKey('ORDER_ID', '100')));
  assert.deepEqual(state.links.map(link => [link.sourceId, link.depth, link.value]), [['0:REFUND_BY_NO:REFUND_NO', 0, '100'], ['2:REFUND_BY_NO:REFUND_NO', 2, '100']]);
  assert.equal(run.updates.at(-1), state);
});

test('页面护栏：层数、累计执行数、总时长与用户停止都保留已得结果并记录待查键', async () => {
  const deep = harness(refundChain, { maxDepth: 2 });
  const byDepth = await runJointQuery({ keyType: 'REFUND_NO', values: ['R1'] }, deep.options);
  assert.equal(byDepth.status, 'stopped');
  assert.deepEqual(byDepth.stop, { rule: 'MAX_DEPTH', depth: 2, pending: [{ keyType: 'REFUND_NO', values: ['R2'] }] });
  assert.equal(byDepth.nodes.length, 2);

  const counted = harness(refundChain, { maxTemplateExecutions: 1 });
  assert.equal((await runJointQuery({ keyType: 'REFUND_NO', values: ['R1'] }, counted.options)).stop?.rule, 'MAX_TEMPLATE_EXECUTIONS');
  assert.equal(counted.requests.length, 1);

  const clock = { now: 0 };
  const timed = harness(request => { clock.now += 31_000; return refundChain(request); }, { maxTotalSeconds: 60 }, clock);
  const byTime = await runJointQuery({ keyType: 'REFUND_NO', values: ['R1'] }, timed.options);
  assert.equal(byTime.stop?.rule, 'MAX_TOTAL_SECONDS');
  assert.equal(byTime.elapsedMillis, 62_000);

  const stopped = harness(request => { if (request.depth === 1) stopped.controller.abort(); return refundChain(request); });
  const byUser = await runJointQuery({ keyType: 'REFUND_NO', values: ['R1'] }, stopped.options);
  assert.equal(byUser.stop?.rule, 'USER_STOPPED');
  assert.equal(byUser.nodes.length, 2);
});

test('单步跳过的节点不计入执行数；请求失败保留之前结果并标记 failed', async () => {
  const skipped = harness(request => response(request.depth, [node(0, 'A', 'K', ['1']), node(0, 'B', 'K', ['1'], { status: 'SKIPPED', errorCode: 'STEP_BUDGET_EXHAUSTED' })]));
  assert.equal((await runJointQuery({ keyType: 'K', values: ['1'] }, skipped.options)).executions, 1);
  const failing = harness(request => { if (request.depth === 1) throw new Error('boom'); return refundChain(request); });
  const state = await runJointQuery({ keyType: 'REFUND_NO', values: ['R1'] }, failing.options);
  assert.equal(state.status, 'failed');
  assert.equal((state.error as Error).message, 'boom');
  assert.equal(state.nodes.length, 1);
});

test('重试这一项：只执行该模板，结果替换原节点并继续展开新发现的键', async () => {
  let attempt = 0;
  const run = harness(request => {
    if (request.templateCodes) {
      attempt++;
      return response(0, [node(0, 'INTENT', 'ORDER_ID', ['100'])], [{ keyType: 'INTENT_ID', values: ['300'] }, { keyType: 'CHECKOUT_ID', values: ['7'] }],
        [{ nodeId: '0:INTENT:ORDER_ID', rowIndex: 0, column: 'intent_id', keyType: 'INTENT_ID', value: '300' }]);
    }
    if (request.depth === 0) return response(0, [node(0, 'ORDER', 'ORDER_ID', ['100']), node(0, 'INTENT', 'ORDER_ID', ['100'], { status: 'FAILED', errorCode: 'DATA_QUERY_TIMEOUT' })],
      [{ keyType: 'CHECKOUT_ID', values: ['7'] }]);
    return response(request.depth, request.keys.map(key => node(request.depth, `BY_${key.keyType}`, key.keyType, key.values)));
  });
  const first = await runJointQuery({ keyType: 'ORDER_ID', values: ['100'] }, run.options);
  const failed = first.nodes.find(item => item.status === 'FAILED')!;
  run.requests.length = 0;
  const retried = await retryNode(first, failed, run.options);
  assert.equal(attempt, 1);
  assert.deepEqual(run.requests.map(request => [request.depth, request.keys, request.templateCodes]), [
    [0, [{ keyType: 'ORDER_ID', values: ['100'] }], ['INTENT']],
    [1, [{ keyType: 'INTENT_ID', values: ['300'] }], undefined],
  ]);
  assert.equal(retried.status, 'done');
  // 原节点位置不变、状态更新；已访问的 CHECKOUT_ID 不再查；续查节点与已有 1 层节点不冲突
  assert.deepEqual(retried.nodes.map(item => [item.id, item.status]), [['0:ORDER:ORDER_ID', 'SUCCEEDED'], ['0:INTENT:ORDER_ID', 'SUCCEEDED'],
    ['1:BY_CHECKOUT_ID:CHECKOUT_ID', 'SUCCEEDED'], ['1:BY_INTENT_ID:INTENT_ID', 'SUCCEEDED']]);
  assert.deepEqual(retried.links.map(link => link.sourceId), ['0:INTENT:ORDER_ID']);
});

test('合并：同层同 nodeId 的续查结果另起本地 id；键扣除、分块与规范化', () => {
  const state = mergeStep(initialRun({ keyType: 'K', values: ['1'] }, 0), response(1, [node(1, 'T', 'K', ['2'])]));
  const again = mergeStep(state, response(1, [node(1, 'T', 'K', ['3'])]));
  assert.deepEqual(again.nodes.map(item => item.id), ['1:T:K', '1:T:K#2']);
  const visited = new Set([visitKey('A', '1')]);
  assert.deepEqual(nextKeys([{ keyType: 'A', values: ['1', '2'] }, { keyType: 'A', values: ['2', '3'] }, { keyType: 'B', values: ['1'] }, { keyType: 'C', values: ['1'] }], visited, 2),
    [{ keyType: 'A', values: ['2', '3'] }, { keyType: 'B', values: ['1'] }, { keyType: 'C', values: ['1'] }]);
  assert.deepEqual(nextKeys([{ keyType: 'A', values: ['1'] }], visited, 50), []);
  const many = Array.from({ length: 45 }, (_, i) => ({ keyType: `K${i}`, values: ['1'] }));
  assert.deepEqual(chunkKeys(many).map(chunk => chunk.length), [20, 20, 5]);
  assert.equal(normalizeKeyValue('INTEGER', '000123'), '123');
  assert.equal(normalizeKeyValue('INTEGER', '000'), '0');
  assert.equal(normalizeKeyValue('STRING', '007'), '007');
});

test('同层超过 20 个键类型时拆成多次请求，各自合并', async () => {
  const keys = Array.from({ length: 25 }, (_, i) => `K${i}`);
  const run = harness(request => request.depth === 0
    ? response(0, [node(0, 'ROOT', 'ROOT', ['1'])], keys.map(keyType => ({ keyType, values: ['9'] })))
    : response(request.depth, request.keys.map(key => node(request.depth, 'T', key.keyType, key.values))));
  const state = await runJointQuery({ keyType: 'ROOT', values: ['1'] }, run.options);
  assert.deepEqual(run.requests.map(request => [request.depth, request.keys.length]), [[0, 1], [1, 20], [1, 5]]);
  assert.equal(state.nodes.length, 26);
});

// ---------- 已加载对象（technique 8.7） ----------

const subjectColumns = (subject: string) => [{ name: 'id', label: null, keyType: subject, keyRole: 'LINK' as const }, { name: 'client_id', label: null, keyType: 'CLIENT_ID', keyRole: 'DIMENSION' as const }];

test('已加载对象：入口层不发送；第 1 跳起带上已成功节点的行主体值；重试这一项不发送，续查照常发送', async () => {
  const run = harness(request => {
    if (request.templateCodes) return response(request.depth, [node(request.depth, 'INTENT_BY_ORDER', 'ORDER_ID', ['100'], { subjectKeyType: 'INTENT_ID',
      columns: subjectColumns('INTENT_ID'), rows: [['300', '9']] })], [{ keyType: 'INTENT_ID', values: ['300'] }]);
    if (request.depth === 0) return response(0, [
      node(0, 'ORDER_BY_NO', 'ORDER_NO', ['ord_1'], { subjectKeyType: 'ORDER_ID', columns: subjectColumns('ORDER_ID'), rows: [['100', '9'], [null, '9']] }),
      node(0, 'INTENT_BY_ORDER', 'ORDER_NO', ['ord_1'], { status: 'FAILED', subjectKeyType: 'INTENT_ID', columns: subjectColumns('INTENT_ID'), rows: [['999', '9']] }),
    ], [{ keyType: 'ORDER_ID', values: ['100'] }]);
    if (request.depth === 1) return { ...response(1, [node(1, 'SESSION_BY_ORDER', 'ORDER_ID', ['100'], { subjectKeyType: 'SESSION_ID', columns: subjectColumns('SESSION_ID'), rows: [['700', '9']] })]),
      alreadyLoaded: [{ templateCode: 'ORDER_BY_ID', templateName: 'Order by ID', keyType: 'ORDER_ID', values: ['100'] }] };
    return response(request.depth, []);
  });
  const state = await runJointQuery({ keyType: 'ORDER_NO', values: ['ord_1'] }, run.options);
  assert.equal(run.requests[0].loadedSubjects, undefined);
  // FAILED 节点的行与空值不算已加载
  assert.deepEqual(run.requests[1].loadedSubjects, [{ keyType: 'ORDER_ID', values: ['100'] }]);
  assert.deepEqual([...state.loaded.entries()], [['ORDER_ID', ['100']], ['SESSION_ID', ['700']]]);
  assert.deepEqual(state.alreadyLoaded, [{ templateCode: 'ORDER_BY_ID', templateName: 'Order by ID', keyType: 'ORDER_ID', values: ['100'], depth: 1 }]);
  run.requests.length = 0;
  await retryNode(state, state.nodes.find(item => item.status === 'FAILED')!, run.options);
  assert.equal(run.requests[0].templateCodes?.[0], 'INTENT_BY_ORDER');
  assert.equal(run.requests[0].loadedSubjects, undefined);
  assert.deepEqual(run.requests[1].loadedSubjects, [{ keyType: 'ORDER_ID', values: ['100'] }, { keyType: 'SESSION_ID', values: ['700'] }, { keyType: 'INTENT_ID', values: ['300'] }]);
});

test('已加载对象上限：最近更新的 30 个键类型，每类最近的 200 个值；重新出现的值移到最近', () => {
  let state = initialRun({ keyType: 'K', values: ['1'] }, 0);
  for (let i = 0; i < 32; i++) state = mergeStep(state, response(1, [node(1, `T${i}`, 'X', ['1'], { subjectKeyType: `S${i}`, columns: subjectColumns(`S${i}`), rows: [[String(i), null]] })]));
  const many = Array.from({ length: 250 }, (_, i) => [String(i), null]);
  state = mergeStep(state, response(2, [node(2, 'BIG', 'X', ['1'], { subjectKeyType: 'S0', columns: subjectColumns('S0'), rows: many })]));
  const subjects = loadedSubjects(state.loaded)!;
  assert.equal(subjects.length, 30);
  assert.equal(subjects.some(item => item.keyType === 'S1'), false, 'oldest key types are dropped');
  const s0 = subjects.find(item => item.keyType === 'S0')!;
  assert.equal(subjects.at(-1)!.keyType, 'S0');
  assert.equal(s0.values.length, 200);
  assert.deepEqual([s0.values[0], s0.values.at(-1)], ['50', '249']);
  assert.equal(loadedSubjects(new Map()), undefined);
  assert.deepEqual(subjectValues({ status: 'SKIPPED', subjectKeyType: 'S', columns: subjectColumns('S'), rows: [['1', null]] }), []);
});

test('已加载对象被服务端拒绝（422 loadedSubjects.*）时不带它重发一次；其他 422 照常失败；超长值不发送', async () => {
  let calls = 0;
  const run = harness(request => {
    calls++;
    if (request.depth === 0) return response(0, [node(0, 'A', 'K', ['1'], { subjectKeyType: 'S', columns: subjectColumns('S'), rows: [['1', null], ['x'.repeat(129), null]] })], [{ keyType: 'K2', values: ['2'] }]);
    if (request.loadedSubjects) throw Object.assign(new Error('422'), { status: 422, fieldErrors: [{ field: 'loadedSubjects[0].values[0]', message: 'bad' }] });
    return response(request.depth, [node(request.depth, 'B', 'K2', ['2'])]);
  });
  const state = await runJointQuery({ keyType: 'K', values: ['1'] }, run.options);
  assert.equal(state.status, 'done');
  assert.deepEqual(run.requests.map(request => [request.depth, request.loadedSubjects ?? null]), [[0, null], [1, [{ keyType: 'S', values: ['1'] }]], [1, null]]);
  assert.equal(calls, 3);
  const other = harness(request => {
    if (request.depth === 0) return response(0, [node(0, 'A', 'K', ['1'], { subjectKeyType: 'S', columns: subjectColumns('S'), rows: [['1', null]] })], [{ keyType: 'K2', values: ['2'] }]);
    throw Object.assign(new Error('422'), { status: 422, fieldErrors: [{ field: 'keys[0].values[0]', message: 'bad' }] });
  });
  assert.equal((await runJointQuery({ keyType: 'K', values: ['1'] }, other.options)).status, 'failed');
  assert.equal(other.requests.length, 2);
});

test('旧 ops-bff 不认识 loadedSubjects（400 INVALID_REQUEST，无字段错误）时不带它重发一次；其他 400 照常失败', async () => {
  const run = harness(request => {
    if (request.depth === 0) return response(0, [node(0, 'A', 'K', ['1'], { subjectKeyType: 'S', columns: subjectColumns('S'), rows: [['1', null]] })], [{ keyType: 'K2', values: ['2'] }]);
    if (request.loadedSubjects) throw Object.assign(new Error('400'), { status: 400, code: 'INVALID_REQUEST', fieldErrors: [] });
    return response(request.depth, [node(request.depth, 'B', 'K2', ['2'])]);
  });
  assert.equal((await runJointQuery({ keyType: 'K', values: ['1'] }, run.options)).status, 'done');
  assert.deepEqual(run.requests.map(request => [request.depth, !!request.loadedSubjects]), [[0, false], [1, true], [1, false]]);
  const other = harness(request => {
    if (request.depth === 0) return response(0, [node(0, 'A', 'K', ['1'], { subjectKeyType: 'S', columns: subjectColumns('S'), rows: [['1', null]] })], [{ keyType: 'K2', values: ['2'] }]);
    throw Object.assign(new Error('400'), { status: 400, code: 'BUSINESS_CHECK_FAILED', fieldErrors: [] });
  });
  assert.equal((await runJointQuery({ keyType: 'K', values: ['1'] }, other.options)).status, 'failed');
  assert.equal(other.requests.length, 2);
});
