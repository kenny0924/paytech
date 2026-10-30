import { isInteger, isOneOf, isOptionalText, isRecord, isText, isTextList, orNull, require } from './dataQueryCommon.ts';
import { keyRoles, keyValueTypes, matchesPattern } from './keyTypeModel.ts';
import type { KeyRole, KeyValueType } from './keyTypeModel.ts';
import { nextRowKey } from './templateModel.ts';
import type { ColumnRow, ParameterRow } from './templateModel.ts';
import { translator } from '../i18n/index.ts';

const t = translator('data-query');

// ---------- J1 设置 ----------

export interface EntryKeyType {
  keyTypeCode: string; keyTypeName: string; groupName: string; valueType: KeyValueType; valuePattern: string | null; inputHint: string | null; sortNo: number;
}
export interface JointGroup { groupName: string; rank: number }
export interface JointLimits {
  maxEntryValues: number; maxValuesPerKey: number; maxRowsPerTemplate: number; maxExecutionsPerStep: number;
  maxDepth: number; maxTemplateExecutions: number; maxTotalSeconds: number;
}
export interface JointSettings { keyTypes: EntryKeyType[]; groups: JointGroup[]; limits: JointLimits }

const limitKeys: readonly (keyof JointLimits)[] = ['maxEntryValues', 'maxValuesPerKey', 'maxRowsPerTemplate', 'maxExecutionsPerStep', 'maxDepth',
  'maxTemplateExecutions', 'maxTotalSeconds'];

function decodeEntryKeyType(value: unknown): EntryKeyType {
  require(isRecord(value) && isText(value.keyTypeCode) && isText(value.keyTypeName) && isText(value.groupName) && isOneOf(keyValueTypes, value.valueType)
    && isOptionalText(value.valuePattern) && isOptionalText(value.inputHint) && isInteger(value.sortNo));
  return { keyTypeCode: value.keyTypeCode as string, keyTypeName: value.keyTypeName as string, groupName: value.groupName as string,
    valueType: value.valueType as KeyValueType, valuePattern: orNull(value.valuePattern as string | null) || null,
    inputHint: orNull(value.inputHint as string | null) || null, sortNo: value.sortNo as number };
}
function decodeGroup(value: unknown): JointGroup {
  require(isRecord(value) && isText(value.groupName) && isInteger(value.rank));
  return { groupName: value.groupName as string, rank: value.rank as number };
}

/** J1：入口键类型 entryKeyTypes（ACTIVE 且可作入口）、分组与护栏；兼容早期草案的 keyTypes。 */
export function decodeJointSettings(value: unknown): JointSettings {
  require(isRecord(value) && isRecord(value.limits) && Array.isArray(value.groups) && Array.isArray(value.keyTypes ?? value.entryKeyTypes));
  const limits = value.limits as Record<string, unknown>;
  require(limitKeys.every(key => isInteger(limits[key], 1)));
  return {
    keyTypes: ((value.keyTypes ?? value.entryKeyTypes) as unknown[]).map(decodeEntryKeyType).sort((a, b) => a.sortNo - b.sortNo),
    groups: (value.groups as unknown[]).map(decodeGroup).sort((a, b) => a.rank - b.rank),
    limits: Object.fromEntries(limitKeys.map(key => [key, limits[key]])) as unknown as JointLimits,
  };
}

// ---------- J2 可达预览 ----------

export interface CoverageTemplate {
  templateCode: string; templateName: string; dataSourceCode: string; subjectKeyType: string; triggerKeyType: string; minSteps: number; linkKeyTypes: string[];
}
export interface CoverageGroup { groupName: string; rank: number; templates: CoverageTemplate[] }
export interface JointCoverage {
  groups: CoverageGroup[]; breaks: { keyType: string; reason: string }[]; excluded: { templateCode: string; reasonCode: string; reason: string }[];
}

export function decodeJointCoverage(value: unknown): JointCoverage {
  require(isRecord(value) && Array.isArray(value.groups) && Array.isArray(value.breaks ?? []) && Array.isArray(value.excluded ?? []));
  const groups = (value.groups as unknown[]).map(group => {
    require(isRecord(group) && isText(group.groupName) && isInteger(group.rank) && Array.isArray(group.templates));
    return { groupName: group.groupName as string, rank: group.rank as number, templates: (group.templates as unknown[]).map(item => {
      require(isRecord(item) && isText(item.templateCode) && isText(item.templateName) && isText(item.dataSourceCode) && isText(item.subjectKeyType)
        && isText(item.triggerKeyType) && isInteger(item.minSteps) && isTextList(item.linkKeyTypes ?? []));
      return { ...(item as unknown as CoverageTemplate), linkKeyTypes: (item.linkKeyTypes ?? []) as string[] };
    }) };
  }).sort((a, b) => a.rank - b.rank);
  const breaks = ((value.breaks ?? []) as unknown[]).map(item => {
    require(isRecord(item) && isText(item.keyType) && isText(item.reason));
    return { keyType: item.keyType as string, reason: item.reason as string };
  });
  const excluded = ((value.excluded ?? []) as unknown[]).map(item => {
    require(isRecord(item) && isText(item.templateCode) && isText(item.reasonCode) && isText(item.reason));
    return { templateCode: item.templateCode as string, reasonCode: item.reasonCode as string, reason: item.reason as string };
  });
  return { groups, breaks, excluded };
}

// ---------- J3 执行一层 ----------

export const nodeStatuses = ['SUCCEEDED', 'FAILED', 'SKIPPED'] as const;
export type NodeStatus = typeof nodeStatuses[number];
export const serverTruncationRules = ['MAX_VALUES_PER_KEY', 'STEP_EXECUTION_LIMIT', 'STEP_BUDGET'] as const;

export interface JointColumn { name: string; label: string | null; keyType: string | null; keyRole: KeyRole | null }
export type JointCell = string | boolean | null;
export interface JointNode {
  nodeId: string; templateCode: string; templateName: string; versionNo: number | null; dataSourceCode: string;
  subjectKeyType: string; groupName: string; laneRank: number; subjectSortNo: number; triggerKeyType: string; keyParameter: string;
  /** 键参数是否为集合（服务端提供时）；未提供时由加载更多按触发值个数推断。 */
  keyParameterList: boolean | null;
  triggerValues: string[]; status: NodeStatus; errorCode: string | null; errorMessage: string | null; pagingMode: string;
  truncated: boolean; elapsedMillis: number; columns: JointColumn[]; rows: JointCell[][]; warnings: string[];
  /** 游标分页的下一页游标（服务端提供时）。 */
  nextCursor: string | null;
}
export interface JointLink { nodeId: string; rowIndex: number; column: string; keyType: string; value: string }
export interface StepKeys { keyType: string; values: string[] }
export interface Truncation { rule: string; keyType: string | null; dropped: number | null }
/** 因对象已加载而未执行的“按自身 ID 查”视图（technique 8.7），可在节点明细中按需查看。 */
export interface AlreadyLoaded { templateCode: string; templateName: string; keyType: string; values: string[] }
export interface JointStepResponse {
  depth: number; nodes: JointNode[]; links: JointLink[]; discoveredKeys: StepKeys[]; truncations: Truncation[]; elapsedMillis: number;
  alreadyLoaded: AlreadyLoaded[];
}
/** loadedSubjects：已加载对象（键类型 ≤ 30、每类值 ≤ 200），只在第 1 跳起的展开请求中发送。 */
export interface JointStepRequest { depth: number; keys: StepKeys[]; templateCodes?: string[]; loadedSubjects?: StepKeys[] }

const isCell = (v: unknown) => v === null || typeof v === 'string' || typeof v === 'boolean';

function decodeNode(value: unknown): JointNode {
  require(isRecord(value) && isText(value.nodeId) && isText(value.templateCode) && isText(value.templateName) && isText(value.subjectKeyType)
    && isText(value.groupName) && isInteger(value.laneRank) && isInteger(value.subjectSortNo) && isText(value.triggerKeyType) && isText(value.keyParameter)
    && isTextList(value.triggerValues) && isOneOf(nodeStatuses, value.status) && isOptionalText(value.errorCode) && isOptionalText(value.errorMessage)
    && typeof value.truncated === 'boolean' && Array.isArray(value.columns ?? []) && Array.isArray(value.rows ?? [])
    && isTextList(value.warnings ?? []) && isOptionalText(value.nextCursor) && isOptionalText(value.pagingMode) && isOptionalText(value.dataSourceCode));
  const columns = ((value.columns ?? []) as unknown[]).map(column => {
    require(isRecord(column) && isText(column.name) && isOptionalText(column.label) && isOptionalText(column.keyType)
      && (column.keyRole == null || isOneOf(keyRoles, column.keyRole)));
    return { name: column.name as string, label: orNull(column.label as string | null), keyType: orNull(column.keyType as string | null),
      keyRole: orNull(column.keyRole as KeyRole | null) };
  });
  const rows = (value.rows ?? []) as unknown[];
  require(rows.every(row => Array.isArray(row) && row.length === columns.length && row.every(isCell)));
  return {
    nodeId: value.nodeId as string, templateCode: value.templateCode as string, templateName: value.templateName as string,
    versionNo: isInteger(value.versionNo, 1) ? value.versionNo as number : null, dataSourceCode: (value.dataSourceCode ?? '') as string,
    subjectKeyType: value.subjectKeyType as string, groupName: value.groupName as string, laneRank: value.laneRank as number,
    subjectSortNo: value.subjectSortNo as number, triggerKeyType: value.triggerKeyType as string, keyParameter: value.keyParameter as string,
    keyParameterList: typeof value.keyParameterList === 'boolean' ? value.keyParameterList : null,
    triggerValues: value.triggerValues as string[], status: value.status as NodeStatus, errorCode: orNull(value.errorCode as string | null),
    errorMessage: orNull(value.errorMessage as string | null), pagingMode: (value.pagingMode ?? 'NONE') as string, truncated: value.truncated as boolean,
    elapsedMillis: isInteger(value.elapsedMillis) ? value.elapsedMillis as number : 0, columns, rows: rows as JointCell[][],
    warnings: (value.warnings ?? []) as string[], nextCursor: orNull(value.nextCursor as string | null),
  };
}
function decodeKeys(value: unknown): StepKeys {
  require(isRecord(value) && isText(value.keyType) && isTextList(value.values));
  return { keyType: value.keyType as string, values: value.values as string[] };
}

export function decodeJointStep(value: unknown): JointStepResponse {
  require(isRecord(value) && isInteger(value.depth) && Array.isArray(value.nodes) && Array.isArray(value.links ?? []) && Array.isArray(value.discoveredKeys ?? [])
    && Array.isArray(value.truncations ?? []) && Array.isArray(value.alreadyLoaded ?? []));
  const links = ((value.links ?? []) as unknown[]).map(link => {
    require(isRecord(link) && isText(link.nodeId) && isInteger(link.rowIndex) && isText(link.column) && isText(link.keyType) && isText(link.value));
    return link as unknown as JointLink;
  });
  const truncations = ((value.truncations ?? []) as unknown[]).map(item => {
    require(isRecord(item) && isText(item.rule) && isOptionalText(item.keyType) && (item.dropped == null || isInteger(item.dropped)));
    return { rule: item.rule as string, keyType: orNull(item.keyType as string | null), dropped: orNull(item.dropped as number | null) };
  });
  const alreadyLoaded = ((value.alreadyLoaded ?? []) as unknown[]).map(item => {
    require(isRecord(item) && isText(item.templateCode) && isText(item.templateName) && isText(item.keyType) && isTextList(item.values));
    return { templateCode: item.templateCode as string, templateName: item.templateName as string, keyType: item.keyType as string, values: item.values as string[] };
  });
  return { depth: value.depth as number, nodes: (value.nodes as unknown[]).map(decodeNode), links,
    discoveredKeys: ((value.discoveredKeys ?? []) as unknown[]).map(decodeKeys), truncations,
    elapsedMillis: isInteger(value.elapsedMillis) ? value.elapsedMillis as number : 0, alreadyLoaded };
}

// ---------- 模板联合查询配置（joint，technique 8.3） ----------

export interface JointConfigColumn { name: string; keyType: string; keyRole: KeyRole }
export interface JointConfig { subjectKeyType: string; keyParameter: { name: string; keyType: string }; columns: JointConfigColumn[] }
export interface JointParticipation { participating: boolean; reasonCode: string | null; reason: string | null }

export function decodeJointConfig(value: unknown): JointConfig | null {
  if (value === undefined || value === null) return null;
  require(isRecord(value) && isOptionalText(value.subjectKeyType) && (value.keyParameter == null || isRecord(value.keyParameter))
    && Array.isArray(value.columns ?? []));
  const parameter = (value.keyParameter ?? {}) as Record<string, unknown>;
  require(isOptionalText(parameter.name) && isOptionalText(parameter.keyType));
  const columns = ((value.columns ?? []) as unknown[]).map(column => {
    require(isRecord(column) && isText(column.name) && isOptionalText(column.keyType) && (column.keyRole == null || isOneOf(keyRoles, column.keyRole)));
    return column as unknown as JointConfigColumn;
  });
  return { subjectKeyType: value.subjectKeyType as string, keyParameter: { name: parameter.name as string, keyType: parameter.keyType as string }, columns };
}
export function decodeJointParticipation(value: unknown): JointParticipation | null {
  if (value === undefined || value === null) return null;
  require(isRecord(value) && typeof value.participating === 'boolean' && isOptionalText(value.reasonCode) && isOptionalText(value.reason));
  return { participating: value.participating as boolean, reasonCode: orNull(value.reasonCode as string | null), reason: orNull(value.reason as string | null) };
}

export interface JointColumnRow { key: string; name: string; keyType: string | null; keyRole: KeyRole }
/**
 * 编辑器中的联合查询配置。关闭参与时保留已填内容（再打开可恢复），请求体发送 null。
 */
export interface JointDraft {
  enabled: boolean; subjectKeyType: string | null; keyParameterName: string | null; keyParameterKeyType: string | null; columns: JointColumnRow[];
}

export const emptyJointDraft = (): JointDraft => ({ enabled: false, subjectKeyType: null, keyParameterName: null, keyParameterKeyType: null, columns: [] });

export function jointDraft(config: JointConfig | null): JointDraft {
  if (!config) return emptyJointDraft();
  return {
    enabled: true, subjectKeyType: config.subjectKeyType ?? null, keyParameterName: config.keyParameter?.name ?? null,
    keyParameterKeyType: config.keyParameter?.keyType ?? null,
    columns: config.columns.map(column => ({ key: nextRowKey(), name: column.name, keyType: column.keyType ?? null, keyRole: column.keyRole ?? 'LINK' })),
  };
}

/** 请求体：未参与为 null；参与时原样提交（不完整的项由服务端定位到 joint.* 字段）。 */
export function jointBody(draft: JointDraft): unknown {
  if (!draft.enabled) return null;
  return {
    subjectKeyType: draft.subjectKeyType, keyParameter: { name: draft.keyParameterName, keyType: draft.keyParameterKeyType },
    columns: draft.columns.map(column => ({ name: column.name.trim(), keyType: column.keyType, keyRole: column.keyRole })),
  };
}

/** 编辑器使用的键类型信息（取自 K1，含停用项以便显示已引用的旧值）。 */
export interface KeyTypeInfo { keyTypeCode: string; keyTypeName: string; groupName: string; valueType: KeyValueType; defaultRole: KeyRole; status: string; sortNo: number }

export interface JointProblem { level: 'ERROR' | 'WARNING'; field: string; message: string }

/** 参数是否可绑定该值类型的键（B3）：INTEGER 键可绑 INTEGER 或 STRING（含 LIST），STRING 键只绑 STRING（含 LIST）。 */
export function parameterFitsKey(parameter: Pick<ParameterRow, 'type' | 'elementType'>, valueType: KeyValueType): boolean {
  const type = parameter.type === 'LIST' ? parameter.elementType : parameter.type;
  return valueType === 'INTEGER' ? type === 'INTEGER' || type === 'STRING' : type === 'STRING';
}

const nameOf = (keyTypes: ReadonlyMap<string, KeyTypeInfo>, code: string | null) => code ? keyTypes.get(code)?.keyTypeName ?? code : '';

/**
 * 编辑器就地检查（与服务端 8.4 同口径，服务端为准）：错误阻止参与；提醒不阻止保存。
 * 字段路径与服务端一致（joint.subjectKeyType、joint.keyParameter.*、joint.columns[i].*）。
 */
export function jointProblems(draft: JointDraft, parameters: readonly ParameterRow[], columns: readonly ColumnRow[],
  keyTypes: ReadonlyMap<string, KeyTypeInfo>): JointProblem[] {
  if (!draft.enabled) return [];
  const problems: JointProblem[] = [];
  const inactive = (code: string | null) => !!code && keyTypes.size > 0 && keyTypes.get(code)?.status !== 'ACTIVE';
  if (!draft.subjectKeyType) problems.push({ level: 'ERROR', field: 'joint.subjectKeyType', message: t('joint.problem.subjectRequired') });
  else if (inactive(draft.subjectKeyType)) problems.push({ level: 'ERROR', field: 'joint.subjectKeyType', message: t('joint.problem.keyTypeInactive', { name: nameOf(keyTypes, draft.subjectKeyType) }) });
  const parameter = parameters.find(row => row.name.trim() === draft.keyParameterName);
  if (!draft.keyParameterName) problems.push({ level: 'ERROR', field: 'joint.keyParameter.name', message: t('joint.problem.parameterRequired') });
  else if (!parameter) problems.push({ level: 'ERROR', field: 'joint.keyParameter.name', message: t('joint.problem.parameterMissing', { name: draft.keyParameterName }) });
  if (!draft.keyParameterKeyType) problems.push({ level: 'ERROR', field: 'joint.keyParameter.keyType', message: t('joint.problem.parameterKeyTypeRequired') });
  else if (inactive(draft.keyParameterKeyType)) problems.push({ level: 'ERROR', field: 'joint.keyParameter.keyType', message: t('joint.problem.keyTypeInactive', { name: nameOf(keyTypes, draft.keyParameterKeyType) }) });
  const parameterKey = draft.keyParameterKeyType ? keyTypes.get(draft.keyParameterKeyType) : undefined;
  if (parameter && parameterKey && !parameterFitsKey(parameter, parameterKey.valueType)) {
    problems.push({ level: 'ERROR', field: 'joint.keyParameter.name', message: t('joint.problem.parameterType', { name: parameter.name.trim(), keyType: parameterKey.keyTypeName }) });
  }
  if (parameter && !parameter.required) problems.push({ level: 'WARNING', field: 'joint.keyParameter.name', message: t('joint.problem.parameterNotRequired', { name: parameter.name.trim() }) });
  const others = parameters.filter(row => row !== parameter && row.required && !row.defaultText.trim() && row.name.trim());
  if (parameter && others.length) problems.push({ level: 'WARNING', field: 'joint.keyParameter.name', message: t('joint.problem.otherRequired', { names: others.map(row => row.name.trim()).join(', ') }) });
  const seen = new Set<string>();
  draft.columns.forEach((column, index) => {
    const field = `joint.columns[${index}]`; const name = column.name.trim();
    if (!name) { problems.push({ level: 'ERROR', field: `${field}.name`, message: t('joint.problem.columnRequired') }); return; }
    if (seen.has(name.toLowerCase())) problems.push({ level: 'ERROR', field: `${field}.name`, message: t('joint.problem.columnDuplicate', { name }) });
    seen.add(name.toLowerCase());
    if (!column.keyType) { problems.push({ level: 'ERROR', field: `${field}.keyType`, message: t('joint.problem.columnKeyTypeRequired', { name }) }); return; }
    if (inactive(column.keyType)) problems.push({ level: 'ERROR', field: `${field}.keyType`, message: t('joint.problem.keyTypeInactive', { name: nameOf(keyTypes, column.keyType) }) });
    if (column.keyRole !== 'LINK') return;
    const declared = columns.find(row => row.name.trim().toLowerCase() === name.toLowerCase());
    if (declared && declared.mask !== 'NONE') problems.push({ level: 'ERROR', field: `${field}.keyRole`, message: t('joint.problem.linkMasked', { name }) });
    if (keyTypes.get(column.keyType)?.defaultRole === 'DIMENSION') problems.push({ level: 'WARNING', field: `${field}.keyRole`, message: t('joint.problem.linkDimension', { name }) });
  });
  return problems;
}

/** 底部一句话：保存后的效果；有错误时以“修正 n 处后”开头。 */
export function jointEffect(draft: JointDraft, keyTypes: ReadonlyMap<string, KeyTypeInfo>, errors: number): string {
  if (!draft.enabled) return t('joint.effect.off');
  const trigger = nameOf(keyTypes, draft.keyParameterKeyType) || t('joint.effect.unsetKey');
  const links = [...new Set(draft.columns.filter(column => column.keyRole === 'LINK' && column.keyType).map(column => nameOf(keyTypes, column.keyType)))];
  const effect = links.length ? t('joint.effect.withLinks', { trigger, links: links.join(t('joint.effect.separator')) }) : t('joint.effect.noLinks', { trigger });
  return errors ? t('joint.effect.afterFix', { count: errors, effect }) : effect;
}

export const emptyJointColumn = (): JointColumnRow => ({ key: nextRowKey(), name: '', keyType: null, keyRole: 'LINK' });

// ---------- 入口输入 ----------

export interface EntryLine { line: number; text: string; valid: boolean }
export interface EntryCheck { values: string[]; lines: EntryLine[]; invalid: number[]; tooMany: boolean; empty: boolean; valueLines: number[] }

const maxLong = '9223372036854775807';
/** INTEGER 值：十进制数字且在 64 位范围内（去前导零后比较）。 */
export function isLongText(value: string): boolean {
  if (!/^[0-9]+$/.test(value)) return false;
  const digits = value.replace(/^0+(?=\d)/, '');
  return digits.length < maxLong.length || (digits.length === maxLong.length && digits <= maxLong);
}

/**
 * 多行入口（AC-V1、B13）：每行一个值，去掉行首尾空白，空行忽略；INTEGER 校验 64 位整数，再按键类型 valuePattern 整串匹配。
 * values 为合法且去重后的值，valueLines 为每个值对应的行号（用于把 422 的 keys[0].values[i] 定位回行）。
 */
export function checkEntry(text: string, keyType: Pick<EntryKeyType, 'valueType' | 'valuePattern'> | undefined, maxEntryValues: number): EntryCheck {
  const lines: EntryLine[] = [];
  text.split('\n').forEach((raw, index) => {
    const value = raw.trim();
    if (!value) return;
    const valid = !keyType || ((keyType.valueType !== 'INTEGER' || isLongText(value)) && matchesPattern(keyType.valuePattern, value));
    lines.push({ line: index + 1, text: value, valid });
  });
  const values: string[] = []; const valueLines: number[] = [];
  for (const line of lines.filter(item => item.valid)) {
    if (values.includes(line.text)) continue;
    values.push(line.text); valueLines.push(line.line);
  }
  return { values, lines, invalid: lines.filter(line => !line.valid).map(line => line.line), tooMany: values.length > maxEntryValues,
    empty: lines.length === 0, valueLines };
}
