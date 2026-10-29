import type {
  ActionConfig, ActionType, DataQueryConfig, DataQueryOutput, DataQueryOutputType, DataQueryTemplateOption, FileUploadConfig, Problem, FormDefinition, ManualConfirmConfig, PageTaskConfig, TemplateReferences, VersionBody,
} from './workflowTypes.ts';
import { cleanFieldRules, fieldProblems } from './formModel.ts';
import { uploadRequired } from './fileModel.ts';
import { isInternalRoute } from './runtimeModel.ts';
import { keyPattern } from './graphModel.ts';
import { translator } from '../i18n/index.ts';

const t = translator('workflow');

export const templateCodePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
export const instructionLimit = 2000;

export function defaultConfig(type: ActionType): ActionConfig {
  switch (type) {
    case 'FORM': return { fields: [] } satisfies FormDefinition;
    case 'FILE_UPLOAD': return { required: true, instruction: '' } satisfies FileUploadConfig;
    case 'PAGE_TASK': return { pageRoute: '', parameters: [], instruction: '' } satisfies PageTaskConfig;
    case 'MANUAL_CONFIRM': return { instruction: '' } satisfies ManualConfirmConfig;
    case 'DATA_QUERY': return { queryCode: '', parameters: [], outputs: [], descriptionKey: '', instruction: '' } satisfies DataQueryConfig;
  }
}

/** 按类型校验 config（与后端 ActionConfigJsonReader 的允许键与规则对齐），返回问题列表。 */
export function configProblems(type: ActionType, config: ActionConfig): string[] {
  const problems: string[] = [];
  switch (type) {
    case 'FORM': {
      const form = config as FormDefinition;
      if (!form.fields?.length) problems.push(t('config.formNoFields'));
      problems.push(...fieldProblems(form).values());
      break;
    }
    case 'FILE_UPLOAD': {
      const file = config as FileUploadConfig;
      if ((file.instruction ?? '').length > instructionLimit) problems.push(t('config.instructionTooLong', { max: instructionLimit }));
      break;
    }
    case 'PAGE_TASK': {
      const page = config as PageTaskConfig;
      if (!page.pageRoute) problems.push(t('config.pageRouteRequired'));
      else if (!isInternalRoute(page.pageRoute)) problems.push(t('config.pageRouteInternal'));
      const keys = (page.parameters ?? []).map(item => item.key);
      if (keys.some(key => !/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(key))) problems.push(t('config.parameterKeyPattern'));
      if (new Set(keys).size !== keys.length) problems.push(t('config.parameterKeyDuplicate'));
      if ((page.parameters ?? []).some(item => !item.label.trim())) problems.push(t('config.parameterLabelRequired'));
      break;
    }
    case 'MANUAL_CONFIRM':
      if (!(config as ManualConfirmConfig).instruction?.trim()) problems.push(t('config.instructionRequired'));
      break;
    case 'DATA_QUERY':
      problems.push(...dataQueryProblems(config as DataQueryConfig));
      break;
  }
  return problems;
}

// ---------- 数据查询（REQ-05 technique 8.4） ----------
export const queryCodePattern = /^[A-Z][A-Z0-9_]{1,63}$/;
export const descriptionKeyPattern = /^[a-z][A-Za-z0-9_.-]{0,127}$/;
export const dataQueryLimits = { outputs: 20 };
export const dataQueryOutputTypes: DataQueryOutputType[] = ['TEXT', 'NUMBER', 'BOOLEAN', 'ENUM', 'DATE'];

/** 参数名列表；旧配置的参数为对象（{ key, … }），只取 key。 */
export function dataQueryParameters(config: Pick<DataQueryConfig, 'parameters'> | null | undefined): string[] {
  return (config?.parameters ?? []).map(item => typeof item === 'string' ? item : (item as { key?: unknown } | null)?.key)
    .filter((name): name is string => typeof name === 'string' && name.length > 0);
}

/** 输出列表；缺少名称或类型的旧项丢弃。 */
export function dataQueryOutputs(config: Pick<DataQueryConfig, 'outputs'> | null | undefined): DataQueryOutput[] {
  return (Array.isArray(config?.outputs) ? config.outputs : []).filter(item => item && typeof item.name === 'string' && typeof item.type === 'string');
}

/** 与后端 ActionConfigValidator 同口径的本地检查（模板是否存在、列是否声明 / 屏蔽以保存时的服务端结果为准）。 */
export function dataQueryProblems(config: DataQueryConfig): string[] {
  const problems: string[] = [];
  const code = config.queryCode?.trim() ?? '';
  if (!code) problems.push(t('config.queryCodeRequired'));
  else if (!queryCodePattern.test(code)) problems.push(t('config.queryCodePattern'));
  const outputs = dataQueryOutputs(config);
  if (outputs.length > dataQueryLimits.outputs) problems.push(t('config.outputsTooMany', { max: dataQueryLimits.outputs }));
  const seen = new Set<string>();
  for (const output of outputs) {
    const name = output.name.trim();
    if (!keyPattern.test(name)) problems.push(t('config.outputNamePattern', { name: name || '—' }));
    else if (seen.has(name.toLowerCase())) problems.push(t('config.outputNameDuplicate', { name }));
    seen.add(name.toLowerCase());
    const values = (output.enumValues ?? []).map(value => value.trim()).filter(Boolean);
    if (output.type === 'ENUM' && (!values.length || new Set(values).size !== values.length)) problems.push(t('config.outputEnumRequired', { name: name || '—' }));
  }
  const descriptionKey = config.descriptionKey?.trim() ?? '';
  if (descriptionKey && !descriptionKeyPattern.test(descriptionKey)) problems.push(t('config.descriptionKeyPattern'));
  if ((config.instruction ?? '').length > instructionLimit) problems.push(t('config.instructionTooLong', { max: instructionLimit }));
  return problems;
}

/** 保存的配置：只写五个允许键；空说明不写；enumValues 只留在 ENUM 输出上。 */
export function normalizeDataQuery(config: DataQueryConfig): DataQueryConfig {
  const descriptionKey = config.descriptionKey?.trim() ?? ''; const instruction = config.instruction?.trim() ?? '';
  return { queryCode: config.queryCode?.trim() ?? '', parameters: dataQueryParameters(config),
    outputs: dataQueryOutputs(config).map(output => ({ name: output.name.trim(), type: output.type,
      ...(output.type === 'ENUM' ? { enumValues: (output.enumValues ?? []).map(value => value.trim()).filter(Boolean) } : {}) })),
    ...(descriptionKey ? { descriptionKey } : {}), ...(instruction ? { instruction } : {}) };
}

/** 选择查询模板：编码与参数取模板声明（全部参数，必填与否在流程节点上体现）。 */
export function selectQueryTemplate(config: DataQueryConfig, template: Pick<DataQueryTemplateOption, 'templateCode' | 'parameters'>): DataQueryConfig {
  return { ...config, queryCode: template.templateCode, parameters: template.parameters.map(item => item.name) };
}

/** 已保存的参数与模板当前声明不一致（模板参数改过）。 */
export function parametersOutdated(config: DataQueryConfig, template: Pick<DataQueryTemplateOption, 'parameters'> | undefined): boolean {
  if (!template) return false;
  return JSON.stringify([...dataQueryParameters(config)].sort()) !== JSON.stringify(template.parameters.map(item => item.name).sort());
}

/** 校验问题按位置分组（接口 action-templates/validation）：模板选择处、参数行、输出行，其余（含约定列 satisfied / missing）放顶部。 */
export interface DataQueryProblemGroups {
  template: Problem[]; parameters: Map<string, Problem[]>; outputs: Map<string, Problem[]>; general: Problem[];
}

/** 按问题码与 itemKey 定位：TEMPLATE_* → 模板选择；PARAMETER_* 与指向参数的格式问题 → 参数行；OUTPUT_* 与指向输出的格式问题 → 输出行。 */
export function groupDataQueryProblems(problems: readonly Problem[], config: DataQueryConfig): DataQueryProblemGroups {
  const groups: DataQueryProblemGroups = { template: [], parameters: new Map(), outputs: new Map(), general: [] };
  const outputs = new Set(dataQueryOutputs(config).map(output => output.name));
  const parameters = new Set(dataQueryParameters(config));
  const push = (map: Map<string, Problem[]>, key: string, problem: Problem) => map.set(key, [...(map.get(key) ?? []), problem]);
  for (const problem of problems) {
    const key = problem.itemKey;
    if (problem.code.startsWith('DATA_QUERY_TEMPLATE_')) groups.template.push(problem);
    else if (key && problem.code.startsWith('DATA_QUERY_PARAMETER_')) push(groups.parameters, key, problem);
    else if (key && problem.code.startsWith('DATA_QUERY_OUTPUT_') && outputs.has(key)) push(groups.outputs, key, problem);
    else if (key && problem.code === 'ACTION_CONFIG_INVALID' && outputs.has(key)) push(groups.outputs, key, problem);
    else if (key && problem.code === 'ACTION_CONFIG_INVALID' && parameters.has(key)) push(groups.parameters, key, problem);
    else groups.general.push(problem);
  }
  return groups;
}


/** 保存前整理：去掉空参数行，只写仍生效的键（需求 E1：表单只留必填与枚举选项，上传只留 required，人工确认不写 noteRequired）。 */
export function normalizeConfig(type: ActionType, config: ActionConfig): ActionConfig {
  switch (type) {
    case 'FORM': return cleanFieldRules(config as FormDefinition);
    case 'FILE_UPLOAD': { const file = config as FileUploadConfig;
      return { required: uploadRequired(file), instruction: file.instruction ?? '' }; }
    case 'PAGE_TASK': { const page = config as PageTaskConfig;
      return { pageRoute: page.pageRoute, parameters: (page.parameters ?? []).filter(item => item.key), instruction: page.instruction ?? '' }; }
    case 'MANUAL_CONFIRM': { const manual = config as ManualConfirmConfig;
      return { instruction: manual.instruction ?? '' }; }
    case 'DATA_QUERY': return normalizeDataQuery(config as DataQueryConfig);
  }
}

// ---------- 引用与影响 ----------
export interface ReferenceSummary { definitions: number; versions: number; outdated: number; running: number; runningOnOutdated: number }

/** 接口 6 返回的已发布版本引用汇总：流程数、版本数、用旧配置的版本数、运行中实例数。 */
export function referenceSummary(references: TemplateReferences): ReferenceSummary {
  const outdated = references.items.filter(item => !item.sameAsCurrent);
  return { definitions: new Set(references.items.map(item => item.definitionId)).size, versions: references.items.length, outdated: outdated.length,
    running: references.items.reduce((sum, item) => sum + item.runningInstanceCount, 0),
    runningOnOutdated: outdated.reduce((sum, item) => sum + item.runningInstanceCount, 0) };
}

/** 版本冻结快照里该模板的配置（用于与当前配置对比）。 */
export function frozenConfig(version: VersionBody, templateId: string): ActionConfig | null {
  return version.actionSnapshot?.templates.find(item => item.templateId === templateId)?.config ?? null;
}

const sameJson = (left: unknown, right: unknown) => JSON.stringify(left) === JSON.stringify(right);

export interface ConfigChange { kind: 'added' | 'removed' | 'changed'; label: string; before: string; after: string }

/** 冻结配置与当前配置的差异：表单按字段比较，其他类型按顶层键比较。 */
export function configDiff(type: ActionType, before: ActionConfig | null, after: ActionConfig | null): ConfigChange[] {
  if (!before || !after) return [];
  if (type === 'FORM') {
    const left = (before as FormDefinition).fields ?? []; const right = (after as FormDefinition).fields ?? [];
    const describe = (field: FormDefinition['fields'][number]) => `${field.label} ${field.key} · ${field.type}${field.required ? ' · ' + t('config.diffRequired') : ''}`;
    return [
      ...right.filter(field => !left.some(item => item.key === field.key)).map(field => ({ kind: 'added' as const, label: field.label || field.key, before: '—', after: describe(field) })),
      ...left.filter(field => !right.some(item => item.key === field.key)).map(field => ({ kind: 'removed' as const, label: field.label || field.key, before: describe(field), after: '—' })),
      ...right.flatMap(field => {
        const old = left.find(item => item.key === field.key);
        return old && !sameJson(old, field) ? [{ kind: 'changed' as const, label: field.label || field.key, before: JSON.stringify({ required: old.required, rules: old.rules ?? {} }),
          after: JSON.stringify({ required: field.required, rules: field.rules ?? {} }) }] : [];
      }),
    ];
  }
  const left = before as unknown as Record<string, unknown>; const right = after as unknown as Record<string, unknown>;
  return [...new Set([...Object.keys(left), ...Object.keys(right)])].filter(key => !sameJson(left[key], right[key]))
    .map(key => ({ kind: left[key] === undefined ? 'added' : right[key] === undefined ? 'removed' : 'changed', label: key,
      before: left[key] === undefined ? '—' : JSON.stringify(left[key]), after: right[key] === undefined ? '—' : JSON.stringify(right[key]) }));
}

/** 每次检查的超时：取模板超时与流程上限（5 秒）中较小者；模板未给出时为上限。 */
export const dataQueryTimeoutCap = 5;
export function effectiveTimeout(templateSeconds: number | null | undefined): { seconds: number; capped: boolean } {
  if (!templateSeconds || templateSeconds <= 0) return { seconds: dataQueryTimeoutCap, capped: false };
  return { seconds: Math.min(templateSeconds, dataQueryTimeoutCap), capped: templateSeconds !== Math.min(templateSeconds, dataQueryTimeoutCap) };
}
