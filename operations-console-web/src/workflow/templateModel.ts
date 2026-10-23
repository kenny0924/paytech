import type {
  ActionConfig, ActionType, DataQueryConfig, FileUploadConfig, FormDefinition, ManualConfirmConfig, PageTaskConfig, TemplateReferences, VersionBody,
} from './workflowTypes.ts';
import { cleanFieldRules, fieldProblems } from './formModel.ts';
import { uploadRequired } from './fileModel.ts';
import { isInternalRoute } from './runtimeModel.ts';

export const templateCodePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
export const instructionLimit = 2000;

export function defaultConfig(type: ActionType): ActionConfig {
  switch (type) {
    case 'FORM': return { fields: [] } satisfies FormDefinition;
    case 'FILE_UPLOAD': return { required: true, instruction: '' } satisfies FileUploadConfig;
    case 'PAGE_TASK': return { pageRoute: '', parameters: [], instruction: '' } satisfies PageTaskConfig;
    case 'MANUAL_CONFIRM': return { instruction: '' } satisfies ManualConfirmConfig;
    case 'DATA_QUERY': return { queryCode: '', parameters: [], passRule: { column: '', operator: 'GTE', value: '' }, evidenceColumns: [] } satisfies DataQueryConfig;
  }
}

/** 按类型校验 config（与后端 ActionConfigJsonReader 的允许键与规则对齐），返回问题列表。 */
export function configProblems(type: ActionType, config: ActionConfig): string[] {
  const problems: string[] = [];
  switch (type) {
    case 'FORM': {
      const form = config as FormDefinition;
      if (!form.fields?.length) problems.push('表单至少需要一个字段');
      problems.push(...fieldProblems(form).values());
      break;
    }
    case 'FILE_UPLOAD': {
      const file = config as FileUploadConfig;
      if ((file.instruction ?? '').length > instructionLimit) problems.push(`执行说明不能超过 ${instructionLimit} 字`);
      break;
    }
    case 'PAGE_TASK': {
      const page = config as PageTaskConfig;
      if (!page.pageRoute) problems.push('请选择或填写目标页面');
      else if (!isInternalRoute(page.pageRoute)) problems.push('目标页面只允许以 / 开头的控制台内部路由，不接受外部地址');
      const keys = (page.parameters ?? []).map(item => item.key);
      if (keys.some(key => !/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(key))) problems.push('跳转参数名以字母开头，只能包含字母、数字和下划线');
      if (new Set(keys).size !== keys.length) problems.push('跳转参数名重复');
      if ((page.parameters ?? []).some(item => !item.label.trim())) problems.push('请填写跳转参数的说明');
      break;
    }
    case 'MANUAL_CONFIRM':
      if (!(config as ManualConfirmConfig).instruction?.trim()) problems.push('请填写执行说明');
      break;
    case 'DATA_QUERY':
      if (!(config as DataQueryConfig).queryCode?.trim()) problems.push('请填写查询编码');
      break;
  }
  return problems;
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
    case 'DATA_QUERY': return config;
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
    const describe = (field: FormDefinition['fields'][number]) => `${field.label} ${field.key} · ${field.type}${field.required ? ' · 必填' : ''}`;
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
