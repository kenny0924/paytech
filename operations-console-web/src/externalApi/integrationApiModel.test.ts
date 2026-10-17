import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ApiError } from '../api/session.ts';
import {
  emptyDraft, fixedKeySet, issueTab, moveItem, nextScriptUseId, pathVariableHints, pathVariables, responseReadPaths, toDefinition, toDraft,
} from './integrationApiModel.ts';
import { decodeCapabilities, decodeScript, decodeValidation, scriptObjects } from './editorModel.ts';
import type { ApiField } from './externalApiModel.ts';

const field = (fieldLocation: ApiField['fieldLocation'], fieldPath: string): ApiField =>
  ({ fieldLocation, fieldPath, fieldName: fieldPath, dataType: 'STRING', required: true, description: null });
const definition = {
  params: [{ name: 'signingKey', dataType: 'PROTECTED_HANDLE', required: true }],
  fixedValues: { 'HEADER:Content-Type': { kind: 'EXPRESSION', expression: "'application/json'" } },
  transport: { protocol: 'HTTP', method: 'POST', endpointCode: 'GW', pathTemplate: '/outlets/{outletRef}/orders', connectTimeoutMs: 2000, responseTimeoutMs: 10000 },
  requestEncoding: 'JSON', responseDecoding: 'JSON',
  scripts: [
    { id: 'verify', scriptCode: 'VERIFY', stage: 'RESPONSE', params: {} },
    { id: 'sign', scriptCode: 'SIGN', stage: 'REQUEST', params: { key: { kind: 'PATH', path: 'params.signingKey' } } },
  ],
  statusRules: [{ condition: 'http.responseStatus == 201', executeStatus: 'SUCCESS' }],
  outputs: { SUCCESS: [{ name: 'ref', dataType: 'STRING', required: true, value: { kind: 'PATH', path: 'http.responseBody.reference' } }], FAIL: [] },
};
const invalid = (fn: () => unknown) => assert.throws(fn, (error: unknown) => error instanceof ApiError && error.status === 502);

test('定义与编辑态互转：固定值成为有序行，脚本按阶段分组后 REQUEST 在前', () => {
  const draft = toDraft(definition);
  assert.deepEqual(draft.fixedValues, [{ key: 'HEADER:Content-Type', expression: "'application/json'" }]);
  assert.deepEqual(draft.requestScripts.map(use => use.id), ['sign']);
  assert.deepEqual(draft.responseScripts.map(use => use.id), ['verify']);
  const back = toDefinition(draft);
  assert.deepEqual(Object.keys(back), ['params', 'fixedValues', 'transport', 'requestEncoding', 'responseDecoding', 'scripts', 'statusRules', 'outputs']);
  assert.deepEqual((back.scripts as { id: string }[]).map(use => use.id), ['sign', 'verify']);
  assert.deepEqual(back.fixedValues, definition.fixedValues);
  assert.deepEqual(back.outputs, definition.outputs);
});

test('新建默认定义可直接序列化，参数说明为空时省略', () => {
  const draft = { ...emptyDraft('GW'), params: [{ name: ' key ', dataType: 'STRING' as const, required: false, description: ' ' }] };
  const back = toDefinition(draft);
  assert.deepEqual(back.params, [{ name: 'key', dataType: 'STRING', required: false }]);
  assert.deepEqual(back.transport, { protocol: 'HTTP', method: 'POST', endpointCode: 'GW', pathTemplate: '/', connectTimeoutMs: 2000, responseTimeoutMs: 10000 });
});

test('路径变量与 PATH 字段双向提示', () => {
  assert.deepEqual(pathVariables('/a/{x}/b/{y}/{x}'), ['x', 'y']);
  assert.deepEqual(pathVariableHints('/outlets/{storeRef}/orders', [field('PATH', 'outletRef'), field('BODY', 'storeRef')]),
    ['路径变量 {storeRef} 没有对应的 PATH 字段', 'PATH 字段 outletRef 未出现在路径模板中']);
  assert.deepEqual(pathVariableHints('/outlets/{outletRef}', [field('PATH', 'outletRef')]), []);
});

test('固定值键按 HEADER 名称小写比较', () => {
  assert.deepEqual([...fixedKeySet([{ key: 'HEADER:Content-Type', expression: "'x'" }, { key: 'BODY:a.b', expression: '1' }])],
    ['HEADER:content-type', 'BODY:a.b']);
});

test('校验问题位置映射到编辑页签', () => {
  assert.equal(issueTab('definition.transport.pathTemplate'), 'interface');
  assert.equal(issueTab('requestFields[2].fieldPath'), 'request');
  assert.equal(issueTab('responseFields[0].dataType'), 'response');
  assert.equal(issueTab('definition.fixedValues.HEADER:X-Unknown'), 'params');
  assert.equal(issueTab('definition.params[1].name'), 'params');
  assert.equal(issueTab('definition.scripts[0].stage'), 'scripts');
  assert.equal(issueTab('definition.statusRules[1].condition'), 'rules');
  assert.equal(issueTab('definition.outputs.SUCCESS[0].value.path'), 'outputs');
  assert.equal(issueTab('apiName'), 'interface');
});

test('响应可读路径：BODY 非数组字段、HEADER 字段与非受控句柄参数', () => {
  assert.deepEqual(responseReadPaths([field('BODY', 'reference'), field('BODY', 'items[].sku'), field('HEADER', 'X-Trace')],
    [{ name: 'signingKey', dataType: 'PROTECTED_HANDLE', required: true }, { name: 'apiVersion', dataType: 'STRING', required: false }]),
    ['http.responseStatus', 'http.responseBody.reference', 'http.responseHeaders.X-Trace', 'params.apiVersion']);
});

test('脚本引用默认 id 避开已有值；上移下移越界不变', () => {
  assert.equal(nextScriptUseId([{ id: 'script1', scriptCode: 'A', stage: 'REQUEST', params: {} }]), 'script2');
  assert.deepEqual(moveItem(['a', 'b', 'c'], 2, -1), ['a', 'c', 'b']);
  assert.deepEqual(moveItem(['a', 'b'], 0, -1), ['a', 'b']);
});

test('校验结果：valid 必须与是否存在 ERROR 一致', () => {
  const issue = { severity: 'WARNING', code: 'VERSION_STALE', location: 'expectedVersion', message: '旧版本' };
  assert.equal(decodeValidation({ valid: true, issues: [issue] }).issues.length, 1);
  invalid(() => decodeValidation({ valid: true, issues: [{ ...issue, severity: 'ERROR' }] }));
  invalid(() => decodeValidation({ valid: false, issues: [] }));
});

test('能力目录与脚本详情解码', () => {
  const capabilities = decodeCapabilities({ enums: { methods: ['GET'] }, limits: { flowNodes: 16 },
    functions: [{ functionCode: 'MONEY_MINOR_UNITS', kind: 'PURE', params: [{ name: 'amount', dataType: 'STRING', required: true }],
      returns: 'INTEGER', returnRoots: {}, stages: ['FLOW_NODE'] }] });
  assert.equal(capabilities.functions[0].functionCode, 'MONEY_MINOR_UNITS');
  invalid(() => decodeCapabilities({ enums: { methods: [1] }, limits: {}, functions: [] }));
  const script = { scriptCode: 'SIGN', scriptName: '签名', executionStage: 'REQUEST', parameterDefinition: [{ name: 'key', dataType: 'PROTECTED_HANDLE', required: true }],
    sourceCode: 'x', description: null, referencedApiCodes: ['CREATE_ORDER'], version: 1, updateTime: '2026-10-13T06:00:00Z' };
  assert.equal(decodeScript(script, 'SIGN').parameterDefinition[0].dataType, 'PROTECTED_HANDLE');
  invalid(() => decodeScript(script, 'OTHER'));
});

test('脚本可用对象按阶段区分读写', () => {
  assert.deepEqual(scriptObjects('REQUEST').filter(item => item.access === '读写').map(item => item.path), ['http.requestHeaders', 'http.requestBody']);
  assert.ok(scriptObjects('RESPONSE').some(item => item.path === 'http.responseStatus' && item.access === '只读'));
});

test('decimalEncoding：缺省按 STRING 编辑且保存时省略，NUMBER 原样保存', () => {
  assert.equal(toDraft(definition).decimalEncoding, 'STRING');
  assert.equal('decimalEncoding' in toDefinition(toDraft(definition)), false);
  const back = toDefinition(toDraft({ ...definition, decimalEncoding: 'NUMBER' }));
  assert.deepEqual(Object.keys(back), ['params', 'fixedValues', 'transport', 'requestEncoding', 'responseDecoding', 'decimalEncoding', 'scripts', 'statusRules', 'outputs']);
  assert.equal(back.decimalEncoding, 'NUMBER');
});

test('cardFields：没有卡字段时保存省略，已有卡字段原样保留', () => {
  assert.deepEqual(toDraft(definition).cardFields, []);
  assert.equal('cardFields' in toDefinition(toDraft(definition)), false);
  const cardFields = ['BODY:data.card.number', 'BODY:data.card.cvv'];
  const back = toDefinition(toDraft({ ...definition, decimalEncoding: 'NUMBER', cardFields }));
  assert.deepEqual(Object.keys(back), ['params', 'fixedValues', 'transport', 'requestEncoding', 'responseDecoding', 'decimalEncoding', 'cardFields', 'scripts', 'statusRules', 'outputs']);
  assert.deepEqual(back.cardFields, cardFields);
});

test('credentialFields：没有凭据字段时保存省略，已有凭据字段原样保留在 cardFields 之后', () => {
  assert.deepEqual(toDraft(definition).credentialFields, []);
  assert.equal('credentialFields' in toDefinition(toDraft(definition)), false);
  const credentialFields = ['BODY:apiKey'];
  const back = toDefinition(toDraft({ ...definition, cardFields: ['BODY:data.card.number'], credentialFields }));
  assert.deepEqual(Object.keys(back), ['params', 'fixedValues', 'transport', 'requestEncoding', 'responseDecoding', 'cardFields', 'credentialFields', 'scripts', 'statusRules', 'outputs']);
  assert.deepEqual(back.credentialFields, credentialFields);
});
