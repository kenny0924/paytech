import type { FieldType, FormDefinition, FormField } from './workflowTypes.ts';
import { keyPattern, graphLimits } from './graphModel.ts';
import { fileAddressesOf } from './fileModel.ts';
import { pinyin } from 'pinyin-pro';

export const textLimit = 255;
export const longTextLimit = 4000;
const decimalPattern = /^-?\d+(\.\d+)?$/;
const datePattern = /^\d{4}-\d{2}-\d{2}$/;

/** 新字段默认值；key 按类型前缀自动编号，用户可改。 */
export function newField(form: FormDefinition, type: FieldType): FormField {
  const prefix = { TEXT: 'text', LONG_TEXT: 'longText', NUMBER: 'number', BOOLEAN: 'flag', ENUM: 'choice', DATE: 'date', FILE: 'files' }[type];
  const used = new Set(form.fields.map(field => field.key));
  let index = 1; while (used.has(`${prefix}${index}`)) index++;
  // 只保留必填与枚举选项（需求 E1）；不再写长度、范围、文件数量 / 类型等校验项。
  const rules = type === 'ENUM' ? { options: [{ value: 'OPTION_1', label: '选项 1' }] } : undefined;
  return { key: `${prefix}${index}`, label: '', type, required: false, ...(rules ? { rules } : {}) };
}

export function addField(form: FormDefinition, type: FieldType): { form: FormDefinition; index: number } {
  return { form: { fields: [...form.fields, newField(form, type)] }, index: form.fields.length };
}

/** 在指定位置插入新字段（从字段类型拖到画布某个字段之前）；index 超出范围时加到末尾。 */
export function insertField(form: FormDefinition, type: FieldType, index: number): { form: FormDefinition; index: number } {
  const at = Math.max(0, Math.min(index, form.fields.length));
  const fields = [...form.fields]; fields.splice(at, 0, newField(form, type));
  return { form: { fields }, index: at };
}

/**
 * 按显示名称建议字段键：汉字先转为不带声调的拼音，再取英文与数字转为小写下划线
 * （如 “Company Name” → company_name、“公司名称” → gong_si_ming_cheng），以字母开头、最长 64 位，
 * 与其他字段重复时加 _2、_3。名称中没有可用字符时保留 fallback（按类型编号的默认键）。
 */
export function fieldKeyFromLabel(label: string, otherKeys: ReadonlySet<string>, fallback: string): string {
  const spelled = label.replace(/\p{Script=Han}+/gu, han => ` ${pinyin(han, { toneType: 'none', separator: ' ', v: true })} `);
  const words = spelled.match(/[A-Za-z0-9]+/g);
  if (!words) return fallback;
  let base = words.join('_').toLowerCase();
  if (!/^[a-z]/.test(base)) base = `f_${base}`;
  base = base.slice(0, 60);
  if (!otherKeys.has(base)) return base;
  let index = 2; while (otherKeys.has(`${base}_${index}`)) index++;
  return `${base}_${index}`;
}

export function updateField(form: FormDefinition, index: number, patch: Partial<FormField>): FormDefinition {
  return { fields: form.fields.map((field, current) => current === index ? { ...field, ...patch } : field) };
}

export function removeField(form: FormDefinition, index: number): FormDefinition {
  return { fields: form.fields.filter((_, current) => current !== index) };
}

export function moveField(form: FormDefinition, from: number, to: number): FormDefinition {
  if (to < 0 || to >= form.fields.length || from === to) return form;
  const fields = [...form.fields]; const [item] = fields.splice(from, 1); fields.splice(to, 0, item);
  return { fields };
}

export function duplicateField(form: FormDefinition, index: number): FormDefinition {
  const source = form.fields[index];
  if (!source) return form;
  const used = new Set(form.fields.map(field => field.key));
  let copy = 2; while (used.has(`${source.key}${copy}`)) copy++;
  const fields = [...form.fields];
  fields.splice(index + 1, 0, { ...structuredClone(source), key: `${source.key}${copy}`, label: `${source.label}（副本）` });
  return { fields };
}

/** 字段定义问题：key 格式与重复、标签必填、枚举选项、数量上限（字段设计器标红用）。 */
export function fieldProblems(form: FormDefinition): Map<number, string> {
  const problems = new Map<number, string>();
  const counts = new Map<string, number>();
  for (const field of form.fields) counts.set(field.key, (counts.get(field.key) ?? 0) + 1);
  form.fields.forEach((field, index) => {
    const problem = !keyPattern.test(field.key) ? '字段键以字母开头，只能包含字母、数字和下划线'
      : (counts.get(field.key) ?? 0) > 1 ? `字段键 ${field.key} 重复，保存前需要改名`
      : !field.label.trim() ? '请填写显示名称'
      : field.type === 'ENUM' && !(field.rules?.options?.length) ? '枚举至少需要一个选项'
      : field.type === 'ENUM' && new Set(field.rules!.options!.map(option => option.value)).size !== field.rules!.options!.length ? '枚举选项值重复'
      : null;
    if (problem) problems.set(index, problem);
  });
  if (form.fields.length > graphLimits.formFields) problems.set(graphLimits.formFields, `每个表单最多 ${graphLimits.formFields} 个字段`);
  return problems;
}

const isEmpty = (value: unknown) => value === undefined || value === null || value === '' || Array.isArray(value) && value.length === 0;

/**
 * 字段值预检（需求 E1 / E7）：必填，加平台技术保护（类型正确、文本长度不超过平台上限、文件地址有效且不重复）。
 * 旧配置里的 maxLength、min / max / scale、minFiles / maxFiles 不再检查。返回字段 key → 问题。
 */
export function valueProblems(form: FormDefinition, values: Record<string, unknown>): Record<string, string> {
  const problems: Record<string, string> = {};
  for (const field of form.fields) {
    const value = values[field.key];
    if (isEmpty(value)) { if (field.required) problems[field.key] = '必填'; continue; }
    let problem: string | null = null;
    switch (field.type) {
      case 'TEXT': case 'LONG_TEXT': {
        const limit = field.type === 'TEXT' ? textLimit : longTextLimit;
        problem = typeof value !== 'string' ? '必须是文本' : value.length > limit ? `不能超过 ${limit} 个字符` : null; break;
      }
      case 'NUMBER': problem = typeof value === 'string' && decimalPattern.test(value) ? null : '必须是数字'; break;
      case 'BOOLEAN': problem = typeof value === 'boolean' ? null : '必须是是或否'; break;
      case 'ENUM': problem = (field.rules?.options ?? []).some(option => option.value === value) ? null : '不是可选值'; break;
      case 'DATE': problem = typeof value === 'string' && datePattern.test(value) ? null : '必须是 YYYY-MM-DD 日期'; break;
      case 'FILE': {
        const addresses = fileAddressesOf(value);
        problem = !Array.isArray(value) || addresses.length !== value.length ? '文件地址无效，请重新上传'
          : new Set(addresses).size !== addresses.length ? '文件重复' : null; break;
      }
    }
    if (problem) problems[field.key] = problem;
  }
  return problems;
}

/** 只保留仍生效的字段配置：键、名称、类型、必填与枚举选项；去掉旧的长度、范围、文件规则。 */
export function cleanFieldRules(form: FormDefinition): FormDefinition {
  return { fields: form.fields.map(field => {
    const { rules, ...rest } = field;
    return field.type === 'ENUM' ? { ...rest, rules: { options: rules?.options ?? [] } } : rest;
  }) };
}

/** 表单值 → 提交 JSON：去掉未填的可选字段，数字保持十进制字符串。 */
export function outputOf(form: FormDefinition, values: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(form.fields.filter(field => !isEmpty(values[field.key])).map(field => [field.key, values[field.key]]));
}


// ---------- 两轮对比 ----------
export interface OutputChange { key: string; kind: 'added' | 'removed' | 'changed'; before: unknown; after: unknown }

const same = (left: unknown, right: unknown) => JSON.stringify(left ?? null) === JSON.stringify(right ?? null);

/** 比较两轮 output_json（退回修改后的差异，technique 第 8 节交互规则）。 */
export function diffOutputs(previous: Record<string, unknown> | null | undefined, current: Record<string, unknown> | null | undefined): OutputChange[] {
  const before = previous ?? {}; const after = current ?? {};
  const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])];
  return keys.flatMap<OutputChange>(key => {
    const left = before[key]; const right = after[key];
    if (same(left, right)) return [];
    if (isEmpty(left)) return [{ key, kind: 'added', before: left, after: right }];
    if (isEmpty(right)) return [{ key, kind: 'removed', before: left, after: right }];
    return [{ key, kind: 'changed', before: left, after: right }];
  });
}

/** 文件数组的增减：“文件 +1 −1”。 */
export function fileDelta(before: unknown, after: unknown): { added: string[]; removed: string[] } {
  const left = Array.isArray(before) ? before.map(String) : []; const right = Array.isArray(after) ? after.map(String) : [];
  return { added: right.filter(id => !left.includes(id)), removed: left.filter(id => !right.includes(id)) };
}

/** 对比摘要：“确认说明已修改 · 文件 +1 −1”。 */
export function changeSummary(changes: OutputChange[], labels: Record<string, string> = {}): string {
  if (!changes.length) return '与上一轮相同';
  return changes.map(change => {
    if (Array.isArray(change.before) || Array.isArray(change.after)) {
      const delta = fileDelta(change.before, change.after);
      return `${labels[change.key] ?? change.key} +${delta.added.length} −${delta.removed.length}`;
    }
    const label = labels[change.key] ?? change.key;
    return change.kind === 'added' ? `${label}已填写` : change.kind === 'removed' ? `${label}已清空` : `${label}已修改`;
  }).join(' · ');
}

/** 展示值：布尔是/否，数组逗号分隔，空为“—”。 */
export function displayValue(value: unknown, field?: FormField): string {
  if (isEmpty(value)) return '—';
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (Array.isArray(value)) return field?.type === 'FILE' ? `${value.length} 个文件` : value.join(', ');
  if (field?.type === 'ENUM') return field.rules?.options?.find(option => option.value === value)?.label ?? String(value);
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}
