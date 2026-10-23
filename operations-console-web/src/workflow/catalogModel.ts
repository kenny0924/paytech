import type { ActionConfig, ActionTemplate, ActionType, TemplateReferences } from './workflowTypes.ts';
import { defaultConfig, normalizeConfig, templateCodePattern } from './templateModel.ts';
import { translator } from '../i18n/index.ts';

const t = translator('workflow');

// ---------- 动作目录列表（原型 06） ----------

/** 列表页签：全部 + 四个可新建的类型；数据查询只在已有动作时出现（需求 C1、C3）。 */
export type CatalogTab = ActionType | 'ALL';
export const creatableTypes: ActionType[] = ['FORM', 'FILE_UPLOAD', 'PAGE_TASK', 'MANUAL_CONFIRM'];

export interface CatalogTabCount { key: CatalogTab; count: number }

/** 页签与数量：按类型分组计数；数据查询没有动作时不出现。 */
export function catalogTabs(items: readonly Pick<ActionTemplate, 'actionType'>[]): CatalogTabCount[] {
  const count = (type: ActionType) => items.filter(item => item.actionType === type).length;
  const tabs: CatalogTabCount[] = [{ key: 'ALL', count: items.length }, ...creatableTypes.map(type => ({ key: type, count: count(type) }))];
  return count('DATA_QUERY') > 0 ? [...tabs, { key: 'DATA_QUERY', count: count('DATA_QUERY') }] : tabs;
}

/** 按页签与关键字（名称、编码、描述，不区分大小写）过滤，保持原顺序。 */
export function filterTemplates<T extends Pick<ActionTemplate, 'actionType' | 'templateName' | 'templateCode' | 'description'>>(
  items: readonly T[], tab: CatalogTab, keyword: string): T[] {
  const text = keyword.trim().toLowerCase();
  return items.filter(item => (tab === 'ALL' || item.actionType === tab)
    && (!text || `${item.templateName} ${item.templateCode} ${item.description ?? ''}`.toLowerCase().includes(text)));
}

/** 「被引用」列：N 个流程；0 或后端未给出时为「—」。 */
export function referenceText(count: number | null | undefined): string {
  return typeof count === 'number' && count > 0 ? t('catalog.referenceCount', { count }) : '—';
}

/** 引用列表按流程定义分组：每个流程取当前发布版本那一行，另计只在历史版本中引用的版本数。 */
export function currentReferences(references: TemplateReferences | null | undefined) {
  const items = references?.items ?? [];
  const current = items.filter(item => item.currentVersion);
  return { current, historical: items.length - current.length };
}

// ---------- 动作编辑（原型 08） ----------

export interface TemplateDraft { templateCode: string; templateName: string; description: string; config: ActionConfig }

/** 已保存动作的编辑起点；新建时为空白名称与该类型默认配置。 */
export function draftOf(template: Pick<ActionTemplate, 'templateCode' | 'templateName' | 'description' | 'config'> | null, type: ActionType): TemplateDraft {
  if (!template) return { templateCode: '', templateName: '', description: '', config: defaultConfig(type) };
  return { templateCode: template.templateCode, templateName: template.templateName, description: template.description ?? '',
    config: template.config ?? defaultConfig(type) };
}

/** 两稿是否不同：名称与描述按去首尾空白比较，配置按保存时的整理结果比较（旧校验键、空参数行不算修改）。新建时编码随名称自动建议，不计入。 */
export function isDirty(type: ActionType, baseline: TemplateDraft, draft: TemplateDraft): boolean {
  const comparable = (value: TemplateDraft) => JSON.stringify([value.templateName.trim(), value.description.trim(), normalizeConfig(type, value.config)]);
  return comparable(baseline) !== comparable(draft);
}

/** 离开编辑器时是否需要确认：有未保存修改，且不是刚保存成功后的跳转。 */
export const needsLeaveConfirm = (dirty: boolean, canManage: boolean) => dirty && canManage;

/** 新建时的编码建议：名称中的英文与数字转为大写下划线；没有可用字符时用「类型_序号」，避开已存在的编码。 */
export function suggestTemplateCode(name: string, type: ActionType, existing: ReadonlySet<string>): string {
  const words = name.match(/[A-Za-z0-9]+/g) ?? [];
  let base = words.join('_').toUpperCase().slice(0, 56);
  if (!base || !/^[A-Z]/.test(base)) base = base ? `${type}_${base}`.slice(0, 56) : type;
  if (words.length && templateCodePattern.test(base) && !existing.has(base)) return base;
  for (let index = 1; ; index++) {
    const candidate = `${base}_${index}`;
    if (!existing.has(candidate)) return candidate;
  }
}

export type EditorStep = 'basic' | 'config' | 'references';
export interface EditorStepItem { key: EditorStep; label: string; count?: number }

/** 左侧步骤：基本信息 / 字段设计（表单）或说明（其他类型）/ 引用 N（仅已保存的动作）。 */
export function editorSteps(type: ActionType, isNew: boolean, referenceCount?: number): EditorStepItem[] {
  const steps: EditorStepItem[] = [{ key: 'basic', label: t('templateEditor.step.basic') },
    { key: 'config', label: type === 'FORM' ? t('templateEditor.step.fields') : type === 'DATA_QUERY' ? t('templateEditor.step.query') : t('templateEditor.step.instruction') }];
  return isNew ? steps : [...steps, { key: 'references', label: t('templateEditor.step.references'), ...(referenceCount === undefined ? {} : { count: referenceCount }) }];
}

/** 路由 /workflow/action-templates/new?type= 的类型：只接受可新建的四类，其余为 null（回列表重新选择）。 */
export function newTemplateType(value: string | null): ActionType | null {
  return creatableTypes.includes(value as ActionType) ? value as ActionType : null;
}

// ---------- 字段设计三栏布局（AC-04） ----------

/** 画布中单个字段至少需要的宽度；低于该宽度改单列，避免逐字换行。 */
export const minFieldWidth = 240;
export interface DesignerLayout {
  palette: number; properties: number; canvas: number; columns: 1 | 2;
  /** 字段类型改为顶部一行（画布 | 属性两栏）。 */
  stacked: boolean;
  /** 画布与属性也放不下并排：三块上下排列。 */
  single: boolean;
}
const canvasPadding = 64;
const fits = (canvas: number) => canvas - canvasPadding >= minFieldWidth;

/**
 * 字段设计三栏宽度：容器宽时为 200 | 画布 | 340（原型 08）；中等宽度收窄两侧为 168 | 画布 | 300；
 * 画布放不下一个完整字段时字段类型改为顶部一行，仍放不下时三块上下排列。画布能放下两个字段时两列，否则单列。
 */
export function designerLayout(width: number, gap = 12): DesignerLayout {
  const wide = width >= 1100;
  const palette = wide ? 200 : 168; const properties = wide ? 340 : 300;
  const columns = (canvas: number): 1 | 2 => canvas - canvasPadding >= minFieldWidth * 2 + 16 ? 2 : 1;
  const side = width - palette - properties - 2 * gap;
  if (fits(side)) return { palette, properties, canvas: side, columns: columns(side), stacked: false, single: false };
  const stacked = width - 300 - gap;
  if (fits(stacked)) return { palette: 0, properties: 300, canvas: stacked, columns: columns(stacked), stacked: true, single: false };
  return { palette: 0, properties: 0, canvas: width, columns: 1, stacked: true, single: true };
}

/** 画布中每个字段的实际宽度（扣除画布内边距与列间距）。 */
export function fieldWidth(layout: DesignerLayout): number {
  return (layout.canvas - canvasPadding - (layout.columns - 1) * 16) / layout.columns;
}
