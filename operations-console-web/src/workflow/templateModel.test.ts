import assert from 'node:assert/strict';
import test from 'node:test';
import {
  configProblems, dataQueryParameters, defaultConfig, effectiveTimeout, groupDataQueryProblems, normalizeConfig, parametersOutdated, selectQueryTemplate,
} from './templateModel.ts';
import type { DataQueryConfig } from './workflowTypes.ts';
import { testT } from '../i18n/testing.ts';

const t = testT('workflow');

const template = { templateCode: 'GOLIVE_AGREEMENT_DRAFTED', versionNo: 3, timeoutSeconds: 10, parameters: [{ name: 'clientId', type: 'INTEGER', required: true }, { name: 'asOf', type: 'DATE', required: false }] };
const valid: DataQueryConfig = { queryCode: 'GOLIVE_AGREEMENT_DRAFTED', parameters: ['clientId', 'asOf'], outputs: [
  { name: 'agreement_id', type: 'TEXT' }, { name: 'business_mode', type: 'ENUM', enumValues: ['MOR', 'PSP'] }],
  descriptionKey: 'workflow.dataQuery.criteria.GOLIVE_AGREEMENT_DRAFTED', instruction: '合同已起草且价格版本已批准' };

test('数据查询新建默认配置：没有模板与输出，只缺查询模板', () => {
  assert.deepEqual(configProblems('DATA_QUERY', defaultConfig('DATA_QUERY')), [t('config.queryCodeRequired')]);
  assert.deepEqual(configProblems('DATA_QUERY', valid), []);
});

test('数据查询本地校验与后端同口径：编码格式、输出名格式与重复（不区分大小写）、枚举可选值、文案键格式、说明长度、输出上限', () => {
  const problems = configProblems('DATA_QUERY', { ...valid, queryCode: 'golive', outputs: [
    { name: '1bad', type: 'TEXT' }, { name: 'Mode', type: 'TEXT' }, { name: 'mode', type: 'NUMBER' },
    { name: 'kind', type: 'ENUM', enumValues: [] }, { name: 'tier', type: 'ENUM', enumValues: ['A', 'A'] }],
    descriptionKey: 'Workflow.bad key', instruction: 'x'.repeat(2001) });
  assert.deepEqual(problems, [t('config.queryCodePattern'), t('config.outputNamePattern', { name: '1bad' }), t('config.outputNameDuplicate', { name: 'mode' }),
    t('config.outputEnumRequired', { name: 'kind' }), t('config.outputEnumRequired', { name: 'tier' }), t('config.descriptionKeyPattern'),
    t('config.instructionTooLong', { max: 2000 })]);
  const many = { ...valid, outputs: Array.from({ length: 21 }, (_, index) => ({ name: `o${index}`, type: 'TEXT' as const })) };
  assert.deepEqual(configProblems('DATA_QUERY', many), [t('config.outputsTooMany', { max: 20 })]);
});

test('数据查询保存只写五个允许键：空说明不写，enumValues 只留在枚举输出，旧对象参数取 key', () => {
  assert.deepEqual(normalizeConfig('DATA_QUERY', { ...valid, outputs: [{ name: ' amount ', type: 'NUMBER', enumValues: ['x'] },
    { name: 'mode', type: 'ENUM', enumValues: [' MOR ', '', 'PSP'] }], descriptionKey: ' ', instruction: '' }),
  { queryCode: 'GOLIVE_AGREEMENT_DRAFTED', parameters: ['clientId', 'asOf'], outputs: [{ name: 'amount', type: 'NUMBER' }, { name: 'mode', type: 'ENUM', enumValues: ['MOR', 'PSP'] }] });
  assert.deepEqual(Object.keys(normalizeConfig('DATA_QUERY', valid)), ['queryCode', 'parameters', 'outputs', 'descriptionKey', 'instruction']);
  // 预留期的旧配置：参数为 { key, source, value }，passRule / evidenceColumns 不再写出。
  const legacy = { queryCode: 'OLD_QUERY', parameters: [{ key: 'channelCode', source: 'VARIABLE', value: 'start.channelCode' }],
    passRule: { column: 'c', operator: 'GTE', value: '1' }, evidenceColumns: ['c'] } as unknown as DataQueryConfig;
  assert.deepEqual(dataQueryParameters(legacy), ['channelCode']);
  assert.deepEqual(normalizeConfig('DATA_QUERY', legacy), { queryCode: 'OLD_QUERY', parameters: ['channelCode'], outputs: [] });
});

test('选择查询模板：编码与全部声明参数取自模板；模板参数改过时提示更新', () => {
  const picked = selectQueryTemplate({ ...valid, queryCode: '', parameters: [] }, template);
  assert.equal(picked.queryCode, 'GOLIVE_AGREEMENT_DRAFTED');
  assert.deepEqual(picked.parameters, ['clientId', 'asOf']);
  assert.deepEqual(picked.outputs, valid.outputs);
  assert.equal(parametersOutdated(valid, template), false);
  assert.equal(parametersOutdated({ ...valid, parameters: ['asOf', 'clientId'] }, template), false);
  assert.equal(parametersOutdated({ ...valid, parameters: ['clientId'] }, template), true);
  assert.equal(parametersOutdated(valid, undefined), false);
});

test('校验问题按问题码与 itemKey 定位：模板选择、参数行、输出行；约定列与无 itemKey 的格式问题放顶部', () => {
  const problem = (code: string, itemKey: string | null) => ({ code, nodeKey: null, edgeKey: null, itemKey, message: `${code}:${itemKey}` });
  const groups = groupDataQueryProblems([problem('DATA_QUERY_TEMPLATE_INACTIVE', null), problem('DATA_QUERY_PARAMETER_MISSING', 'clientId'),
    problem('DATA_QUERY_PARAMETER_UNDECLARED', 'legacyKey'), problem('DATA_QUERY_OUTPUT_UNDECLARED', 'agreement_id'), problem('DATA_QUERY_OUTPUT_MASKED', 'business_mode'),
    problem('DATA_QUERY_OUTPUT_MASKED', 'satisfied'), problem('DATA_QUERY_OUTPUT_UNDECLARED', 'missing'), problem('ACTION_CONFIG_INVALID', 'business_mode'),
    problem('ACTION_CONFIG_INVALID', 'asOf'), problem('ACTION_CONFIG_INVALID', null)], valid);
  assert.deepEqual(groups.template.map(item => item.code), ['DATA_QUERY_TEMPLATE_INACTIVE']);
  assert.deepEqual([...groups.parameters].map(([key, list]) => [key, list.map(item => item.code)]),
    [['clientId', ['DATA_QUERY_PARAMETER_MISSING']], ['legacyKey', ['DATA_QUERY_PARAMETER_UNDECLARED']], ['asOf', ['ACTION_CONFIG_INVALID']]]);
  assert.deepEqual([...groups.outputs].map(([key, list]) => [key, list.map(item => item.code)]),
    [['agreement_id', ['DATA_QUERY_OUTPUT_UNDECLARED']], ['business_mode', ['DATA_QUERY_OUTPUT_MASKED', 'ACTION_CONFIG_INVALID']]]);
  assert.deepEqual(groups.general.map(item => item.message), ['DATA_QUERY_OUTPUT_MASKED:satisfied', 'DATA_QUERY_OUTPUT_UNDECLARED:missing', 'ACTION_CONFIG_INVALID:null']);
  const empty = groupDataQueryProblems([], valid);
  assert.deepEqual([empty.template.length, empty.parameters.size, empty.outputs.size, empty.general.length], [0, 0, 0, 0]);
});

test('每次检查的超时取模板与 5 秒上限的较小者；模板更短时不标注', () => {
  assert.deepEqual(effectiveTimeout(10), { seconds: 5, capped: true });
  assert.deepEqual(effectiveTimeout(5), { seconds: 5, capped: false });
  assert.deepEqual(effectiveTimeout(3), { seconds: 3, capped: false });
  assert.deepEqual(effectiveTimeout(null), { seconds: 5, capped: false });
});
