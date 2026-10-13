import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  mappingRows, mappingStates, nextUnassigned, suggestMatches, typeHint, variableTree, variableType,
} from './mappingModel.ts';
import type { MappingRow, MappingTarget } from './mappingModel.ts';
import type { VariableOptions } from './flowModel.ts';
import type { Binding } from './integrationApiModel.ts';

const body = (path: string, dataType = 'STRING', required = true): MappingTarget =>
  ({ key: `BODY:${path}`, group: 'BODY', path, nested: true, dataType, required, fixed: false, handle: false });
const path = (value: string): Binding => ({ kind: 'PATH', path: value });
const variables: VariableOptions = {
  paths: ['input.paymentContext', 'input.paymentContext.amount', 'input.paymentContext.currency', 'input.consumer.currency',
    'resources.channelAccount.parameters', 'steps.createOrder.executeStatus', 'steps.createOrder.outputs.transactionRef'],
  unavailableSteps: ['queryOrder'], handlePaths: ['resources.channelAccount.credentials.'],
  types: { 'input.paymentContext': 'OBJECT', 'input.paymentContext.amount': 'STRING', 'input.paymentContext.currency': 'STRING',
    'input.consumer.currency': 'INTEGER', 'steps.createOrder.executeStatus': 'STRING', 'steps.createOrder.outputs.transactionRef': 'STRING' },
  mapRoots: { 'resources.channelAccount.parameters': 'STRING' },
};
const describe = (rows: MappingRow[]) => rows.map(row => row.kind === 'group' ? `#${row.group}(${row.count})`
  : row.kind === 'object' ? `${'  '.repeat(row.depth)}[${row.name}]` : `${'  '.repeat(row.depth)}${row.name}:${row.state}`);

test('映射状态：固定值、已赋值、祖先整体赋值、必填与非必填未赋值', () => {
  const targets = [{ ...body('Content-Type'), group: 'HEADER', nested: false, key: 'HEADER:content-type', fixed: true },
    body('amount', 'OBJECT'), body('amount.value', 'INTEGER'), body('memo', 'STRING', false), body('reference')];
  const states = mappingStates(targets, new Map([['BODY:amount', path('input.paymentContext')]]));
  assert.deepEqual([...states.values()], ['FIXED', 'ASSIGNED', 'INHERITED', 'OPTIONAL', 'MISSING']);
});

test('映射行：分组排序、父在子前并补对象行，固定值不列出，过滤后仍保留上级对象行', () => {
  const targets = [body('billing.country'), { ...body('outletRef'), group: 'PATH', nested: false, key: 'PATH:outletRef' },
    body('amount.value', 'INTEGER'), body('reference'), body('amount.currency'), body('billing.name', 'STRING', false),
    { ...body('Accept'), group: 'HEADER', nested: false, key: 'HEADER:accept', fixed: true }];
  const states = mappingStates(targets, new Map([['BODY:amount.value', path('input.paymentContext.amount')]]));
  assert.deepEqual(describe(mappingRows(targets, states, () => true)), ['#PATH(1)', 'outletRef:MISSING', '#BODY(5)', '[billing]',
    '  country:MISSING', '  name:OPTIONAL', '[amount]', '  value:ASSIGNED', '  currency:MISSING', 'reference:MISSING']);
  assert.deepEqual(describe(mappingRows(targets, states, (_, state) => state === 'MISSING', 'cur')), ['#BODY(1)', '[amount]', '  currency:MISSING']);
});

test('变量类型与类型提示：初始化结果按根类型，路径类型不同才提示', () => {
  assert.equal(variableType('resources.channelAccount.parameters.outletId', variables), 'STRING');
  assert.equal(variableType('input.unknown', variables), null);
  assert.equal(typeHint(body('amount.value', 'INTEGER'), path('input.paymentContext.amount'), variables), '类型不一致：变量是 STRING，字段是 INTEGER');
  assert.equal(typeHint(body('amount.currency'), path('input.paymentContext.currency'), variables), null);
  assert.equal(typeHint(body('amount.value', 'INTEGER'), { kind: 'EXPRESSION', expression: '1' }, variables), null);
});

test('同名自动匹配：只给未赋值目标；同名多个时按类型唯一确定，否则不建议', () => {
  const targets = [body('amount.currency'), body('currencyCount', 'INTEGER'), body('transactionRef'), body('amount', 'OBJECT'),
    { ...body('order.currency', 'DECIMAL') }, { ...body('signing'), handle: true }];
  const states = mappingStates(targets, new Map([['BODY:amount', path('input.paymentContext')]]));
  assert.deepEqual([...suggestMatches(targets, states, variables)], [['BODY:transactionRef', 'steps.createOrder.outputs.transactionRef']]);
  const unbound = mappingStates(targets, new Map());
  assert.deepEqual(suggestMatches(targets, unbound, variables).get('BODY:amount.currency'), 'input.paymentContext.currency');
  assert.equal(suggestMatches(targets, unbound, variables).has('BODY:order.currency'), false);
});

test('下一个未赋值目标：向后查找并循环，只剩当前时为空', () => {
  const targets = [body('a'), body('b'), body('c', 'STRING', false)];
  const rows = mappingRows(targets, mappingStates(targets, new Map([['BODY:b', path('input.x')]])), () => true);
  assert.equal(nextUnassigned(rows, 'BODY:a'), 'BODY:c');
  assert.equal(nextUnassigned(rows, 'BODY:c'), 'BODY:a');
  assert.equal(nextUnassigned(rows, null), 'BODY:a');
  const single = mappingRows([body('a')], mappingStates([body('a')], new Map()), () => true);
  assert.equal(nextUnassigned(single, 'BODY:a'), null);
});

test('变量树：input 分层、初始化结果需补全名称、steps 按节点、受控句柄单列；搜索时平铺', () => {
  const tree = variableTree(variables).map(node => `${'  '.repeat(node.depth)}${node.folder ? '▾' : ''}${node.label}${node.insert ? ` → ${node.insert}` : ''}${node.type ? ` (${node.type})` : ''}`);
  assert.deepEqual(tree, ['▾input', '  ▾paymentContext → input.paymentContext (OBJECT)', '    amount → input.paymentContext.amount (STRING)',
    '    currency → input.paymentContext.currency (STRING)', '  ▾consumer', '    currency → input.consumer.currency (INTEGER)',
    '▾resources.channelAccount', '  parameters.<名称> → resources.channelAccount.parameters. (STRING)', '▾steps.createOrder',
    '  executeStatus → steps.createOrder.executeStatus (STRING)', '  outputs.transactionRef → steps.createOrder.outputs.transactionRef (STRING)',
    'resources.channelAccount.credentials.* → resources.channelAccount.credentials. (PROTECTED_HANDLE)']);
  assert.deepEqual(variableTree(variables, 'CURR').map(node => node.insert), ['input.paymentContext.currency', 'input.consumer.currency']);
});
