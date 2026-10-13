import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ApiError } from '../api/session.ts';
import {
  availableStandardApis, cleanFilters, errorRows, decodeImplementationList, decodeIntegration, decodeMutation, decodePage,
  decodeStandardApi, decodeStandardApiSummary, fieldDepth, fieldHints, replaceQuery, toFieldBodies,
} from './externalApiModel.ts';
import type { ApiField, ImplementationSummary, StandardApiSummary } from './externalApiModel.ts';

const T = '2026-10-13T06:00:00Z';
const field = (fieldLocation: ApiField['fieldLocation'], fieldPath: string, dataType: ApiField['dataType'] = 'STRING'): ApiField =>
  ({ fieldLocation, fieldPath, fieldName: fieldPath, dataType, required: true, description: null });
const summary = (standardApiCode: string): StandardApiSummary => ({ standardApiCode, standardApiName: standardApiCode,
  requestFieldCount: 0, responseFieldCount: 0, implementedBy: [], version: 1, updateTime: T });
const implementation = (standardApiCode: string): ImplementationSummary => ({ standardApiCode, standardApiName: standardApiCode,
  nodeCount: 1, returnCount: 1, initializerIds: [], timeoutMs: 1000, version: 1, updateTime: T });
const invalid = (fn: () => unknown) => assert.throws(fn, (error: unknown) => error instanceof ApiError && error.status === 502);

test('标准 API 详情只接受请求的编码，字段逐项解码', () => {
  const wire = { standardApiCode: 'PAY', standardApiName: '支付', description: null, requestFields: [field('BODY', 'orderId')],
    responseFields: [], implementations: [{ integrationCode: 'DEMO', integrationName: 'DEMO' }], version: 2,
    createTime: T, updateTime: T, updatedBy: '10001', extra: 'ignored' };
  const detail = decodeStandardApi(wire, 'PAY');
  assert.equal(detail.requestFields[0].fieldPath, 'orderId');
  assert.equal('extra' in detail, false);
  invalid(() => decodeStandardApi(wire, 'REFUND'));
  invalid(() => decodeStandardApi({ ...wire, requestFields: [{ ...field('BODY', 'x'), dataType: 'DATE' }] }, 'PAY'));
});

test('接入方详情保留出站地址的引用方；列表与分页校验条目身份', () => {
  const detail = decodeIntegration({ integrationCode: 'DEMO', integrationName: 'D', description: null,
    endpoints: [{ endpointCode: 'GW', baseUrl: 'https://a.test', referencedApiCodes: ['CREATE_ORDER'] }], apiCount: 1,
    scriptCount: 0, implementationCount: 0, version: 1, createTime: T, updateTime: T, updatedBy: '1' }, 'DEMO');
  assert.deepEqual(detail.endpoints[0].referencedApiCodes, ['CREATE_ORDER']);
  const page = { items: [summary('PAY'), summary('PAY')], page: 1, size: 10, total: 2 };
  invalid(() => decodePage(page, decodeStandardApiSummary, item => item.standardApiCode));
  invalid(() => decodePage({ ...page, items: [summary('PAY')], size: 15 }, decodeStandardApiSummary, item => item.standardApiCode));
  invalid(() => decodeImplementationList({ items: [implementation('PAY'), implementation('PAY')] }));
});

test('写结果必须对应本次对象与类型', () => {
  assert.deepEqual(decodeMutation({ objectType: 'STANDARD_API', objectCode: 'PAY', version: 2 }, 'STANDARD_API', 'PAY'),
    { objectType: 'STANDARD_API', objectCode: 'PAY', version: 2 });
  invalid(() => decodeMutation({ objectType: 'STANDARD_API', objectCode: 'REFUND', version: 2 }, 'STANDARD_API', 'PAY'));
  invalid(() => decodeMutation({ objectType: 'INTEGRATION', objectCode: 'PAY', version: 2 }, 'STANDARD_API', 'PAY'));
  invalid(() => decodeMutation({ objectType: 'STANDARD_API', objectCode: 'PAY', version: 0 }, 'STANDARD_API', 'PAY'));
});

test('BODY 路径按点分段缩进，其他位置为顶层', () => {
  assert.equal(fieldDepth(field('BODY', 'paymentContext')), 0);
  assert.equal(fieldDepth(field('BODY', 'paymentContext.items[].sku')), 2);
  assert.equal(fieldDepth(field('HEADER', 'X-Trace.Id')), 0);
});

test('字段即时提示：位置、路径格式、标量类型、重复与父字段', () => {
  const hints = fieldHints([
    field('BODY', 'payment.amount'),
    field('HEADER', 'X-Trace', 'OBJECT'),
    field('BODY', 'bad path'),
    field('QUERY', 'status'),
    field('HEADER', 'x-trace'),
    field('BODY', 'items', 'ARRAY'),
    field('BODY', 'items[].sku'),
  ], ['HEADER', 'BODY']);
  assert.deepEqual(hints.map(hint => `${hint.index}:${hint.column}`), [
    '0:fieldPath', '1:dataType', '2:fieldPath', '3:fieldLocation', '4:fieldPath']);
  assert.equal(hints[0].message, '父字段 payment 需先定义为对象');
  const orders = fieldHints([field('BODY', 'orders', 'OBJECT'), field('BODY', 'orders[].id')], ['BODY']);
  assert.deepEqual(orders.map(hint => `${hint.index}:${hint.message}`), ['1:父字段 orders 需先定义为数组']);
});

test('新建实现只列出尚未实现的标准 API，按编码排序', () => {
  assert.deepEqual(availableStandardApis([summary('REFUND'), summary('PAY'), summary('AUTHORIZE')], [implementation('PAY')])
    .map(item => item.standardApiCode), ['AUTHORIZE', 'REFUND']);
});

test('筛选与查询串：去空白、删除空值并保留其他参数', () => {
  assert.deepEqual(cleanFilters({ keyword: '  PA ', executionStage: '' }), { keyword: 'PA' });
  assert.equal(replaceQuery('?tab=apis&page=2', { page: null, keyword: 'CRE' }), '?tab=apis&keyword=CRE');
  assert.equal(replaceQuery('?tab=apis', { tab: null }), '');
});

test('字段提交时去除首尾空白，空说明为 null', () => {
  assert.deepEqual(toFieldBodies([{ ...field('BODY', ' orderId '), fieldName: ' 订单号 ', description: '  ' }]),
    [{ fieldLocation: 'BODY', fieldPath: 'orderId', fieldName: '订单号', dataType: 'STRING', required: true, description: null }]);
});

test('422 字段路径按数组提取出错行，其他数组与根字段不混入', () => {
  const errors = [{ field: 'requestFields[2].fieldPath' }, { field: 'requestFields[2].dataType' },
    { field: 'responseFields[0].fieldLocation' }, { field: 'requestFields' }, { field: 'endpoints[1].baseUrl' }];
  assert.deepEqual(errorRows(errors, 'requestFields'), [2]);
  assert.deepEqual(errorRows(errors, 'responseFields'), [0]);
  assert.deepEqual(errorRows(errors, 'endpoints'), [1]);
});
