import {
  activeStatuses, isId, isInstant, isInteger, isOneOf, isOptionalInteger, isOptionalText, isRecord, isText, isTextList, orNull, pageDecoder, require,
} from './dataQueryCommon.ts';
import type { ActiveStatus } from './dataQueryCommon.ts';
import { enumLabel, translator } from '../i18n/index.ts';
import { decodeJointConfig, decodeJointParticipation, emptyJointDraft, jointBody, jointDraft } from './jointModel.ts';
import type { JointConfig, JointDraft, JointParticipation } from './jointModel.ts';

const t = translator('data-query');

// ---------- 枚举 ----------

export const parameterTypes = ['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'ENUM', 'FRAGMENT', 'LIST'] as const;
export type ParameterType = typeof parameterTypes[number];
/** LIST 的元素类型：不能是 FRAGMENT 或 LIST，可以是 OBJECT（字段另行声明）。 */
export const elementTypes = ['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'ENUM', 'OBJECT'] as const;
export type ElementType = typeof elementTypes[number];
/** OBJECT 字段的类型：标量与枚举。 */
export const fieldTypes = ['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'ENUM'] as const;
export type FieldType = typeof fieldTypes[number];
export const pagingModes = ['NONE', 'OFFSET', 'CURSOR'] as const;
export type PagingMode = typeof pagingModes[number];
/** 分页方式与掩码方式的显示名为 `data-query.enum.pagingMode.<值>`、`data-query.enum.mask.<值>`，调用时取。 */
export const pagingModeLabel = (value: string) => enumLabel(t, 'enum.pagingMode', value);
export const maskModes = ['NONE', 'HIDE', 'FULL', 'KEEP_EDGES'] as const;
export type MaskMode = typeof maskModes[number];
export const maskLabel = (value: string) => enumLabel(t, 'enum.mask', value);
/** INFO 只出现在保存响应（例如“参与联合查询”），technique 8.3。 */
export const problemLevels = ['ERROR', 'WARNING', 'INFO'] as const;
export type ProblemLevel = typeof problemLevels[number];

/** 硬上限（data-query.limits 默认值）：表单提示用，最终以服务端校验为准。 */
export const templateLimits = { maxRows: 10000, maxTimeoutSeconds: 8, maxBodyBytes: 48 * 1024, maxColumns: 200, maxCursorColumns: 8, maxKeep: 64 } as const;
export const systemParameters = ['__operator.accountId', '__cursor'] as const;
export const templateTags = ['#if', '#elseif', '#else', '/if', '#each', '/each', '#join', '/join', '#part', '/part'] as const;

// ---------- 合同类型 ----------

export interface QueryConstraints {
  maxRows: number; timeoutSeconds: number; pagingMode: PagingMode; maxPageSize?: number | null; countAllowed?: boolean | null; cursorColumns?: string[] | null;
}
export interface QueryColumn { name: string; label?: string | null; mask?: MaskMode | null; keepStart?: number | null; keepEnd?: number | null }
/** parameters_json 一项（technique 3.2）；default 为任意 JSON。 */
export interface ParameterDeclaration {
  name: string; type: string; elementType?: string | null; required?: boolean | null; default?: unknown;
  maxLength?: number | null; min?: string | number | null; max?: string | number | null; allowedValues?: string[] | null;
  maxSize?: number | null; choices?: Record<string, string> | null; fields?: ParameterDeclaration[] | null;
}
export interface ParameterSummary { name: string; type: string; elementType: string | null; required: boolean }
export interface TemplateSummary {
  templateId: string; templateCode: string; templateName: string; description: string | null; dataSourceId: string; dataSourceCode: string;
  dataSourceName: string; status: ActiveStatus; currentVersionNo: number; pagingMode: PagingMode; parameters: ParameterSummary[];
  version: number; updatedBy: string; updateTime: string;
}
export interface TemplateVersion {
  templateId: string; versionNo: number; dataSourceId: string; dataSourceCode: string; dataSourceName: string; dataSourceStatus: string;
  body: string; parameters: ParameterDeclaration[]; columns: QueryColumn[]; constraints: QueryConstraints; restoredFromVersionNo: number | null;
  changeNote: string | null; createdBy: string; createTime: string;
  /** 联合查询配置（joint_json）；null 表示该版本不参与。 */
  joint: JointConfig | null;
  /** 参与情况（服务端评估）；旧服务端不返回时为 null。 */
  jointQuery: JointParticipation | null;
}
export interface TemplateDetail {
  templateId: string; templateCode: string; templateName: string; description: string | null; status: ActiveStatus; currentVersionNo: number;
  version: number; createdBy: string; updatedBy: string; createTime: string; updateTime: string; currentVersion: TemplateVersion;
  jointQuery: JointParticipation | null;
}
export interface VersionSummary {
  versionNo: number; dataSourceId: string; dataSourceCode: string; restoredFromVersionNo: number | null; changeNote: string | null; createdBy: string; createTime: string;
}
export interface TemplateProblem { level: ProblemLevel; line: number | null; column: number | null; field: string | null; message: string }
export interface ValidationResult { valid: boolean; problems: TemplateProblem[] }
export interface SaveResult { templateId: string; templateCode: string; status: ActiveStatus; version: number; currentVersionNo: number; warnings: TemplateProblem[] }
export interface ResultColumn { name: string; label: string | null }
export type ResultCell = string | boolean | null;
export interface TrialResult {
  versionNo: number; columns: ResultColumn[]; rows: ResultCell[][]; hasMore: boolean; total: number | null; nextCursor: string | null;
  elapsedMillis: number; sql: string; bindings: string[]; countSql: string | null;
}

// ---------- 解码 ----------

function decodeConstraints(value: unknown): QueryConstraints {
  require(isRecord(value) && isInteger(value.maxRows, 1) && isInteger(value.timeoutSeconds, 1) && isOneOf(pagingModes, value.pagingMode)
    && isOptionalInteger(value.maxPageSize, 1) && (value.countAllowed == null || typeof value.countAllowed === 'boolean')
    && (value.cursorColumns == null || isTextList(value.cursorColumns)));
  return value as unknown as QueryConstraints;
}
function decodeColumn(value: unknown): QueryColumn {
  require(isRecord(value) && isText(value.name) && isOptionalText(value.label) && (value.mask == null || isOneOf(maskModes, value.mask))
    && isOptionalInteger(value.keepStart) && isOptionalInteger(value.keepEnd));
  return value as unknown as QueryColumn;
}
function decodeDeclaration(value: unknown): ParameterDeclaration {
  require(isRecord(value) && isText(value.name) && isText(value.type) && (value.fields == null || Array.isArray(value.fields)));
  const fields = value.fields as unknown[] | null | undefined;
  return { ...(value as unknown as ParameterDeclaration), ...(fields ? { fields: fields.map(decodeDeclaration) } : {}) };
}
function decodeProblem(value: unknown): TemplateProblem {
  require(isRecord(value) && isOneOf(problemLevels, value.level) && isOptionalInteger(value.line, 1) && isOptionalInteger(value.column, 1)
    && isOptionalText(value.field) && isText(value.message));
  return { level: value.level as ProblemLevel, line: orNull(value.line as number | null), column: orNull(value.column as number | null),
    field: orNull(value.field as string | null), message: value.message as string };
}
function decodeParameterSummary(value: unknown): ParameterSummary {
  require(isRecord(value) && isText(value.name) && isText(value.type) && isOptionalText(value.elementType) && typeof value.required === 'boolean');
  return { name: value.name as string, type: value.type as string, elementType: orNull(value.elementType as string | null), required: value.required as boolean };
}

export function decodeTemplateSummary(value: unknown): TemplateSummary {
  require(isRecord(value) && isId(value.templateId) && isText(value.templateCode) && isText(value.templateName) && isOptionalText(value.description)
    && isId(value.dataSourceId) && isText(value.dataSourceCode) && isText(value.dataSourceName) && isOneOf(activeStatuses, value.status)
    && isInteger(value.currentVersionNo, 1) && isOneOf(pagingModes, value.pagingMode) && Array.isArray(value.parameters)
    && isInteger(value.version, 1) && isText(value.updatedBy) && isInstant(value.updateTime));
  return { ...(value as unknown as TemplateSummary), description: orNull(value.description as string | null),
    parameters: (value.parameters as unknown[]).map(decodeParameterSummary) };
}
export const decodeTemplatePage = pageDecoder(decodeTemplateSummary);

export function decodeTemplateVersion(value: unknown): TemplateVersion {
  require(isRecord(value) && isId(value.templateId) && isInteger(value.versionNo, 1) && isId(value.dataSourceId) && isText(value.dataSourceCode)
    && isText(value.dataSourceName) && isText(value.dataSourceStatus) && isText(value.body)
    && (value.parameters == null || Array.isArray(value.parameters)) && (value.columns == null || Array.isArray(value.columns))
    && isOptionalInteger(value.restoredFromVersionNo, 1) && isOptionalText(value.changeNote) && isText(value.createdBy) && isInstant(value.createTime));
  return {
    ...(value as unknown as TemplateVersion), parameters: ((value.parameters ?? []) as unknown[]).map(decodeDeclaration),
    columns: ((value.columns ?? []) as unknown[]).map(decodeColumn), constraints: decodeConstraints(value.constraints),
    restoredFromVersionNo: orNull(value.restoredFromVersionNo as number | null), changeNote: orNull(value.changeNote as string | null),
    joint: decodeJointConfig(value.joint), jointQuery: decodeJointParticipation(value.jointQuery),
  };
}

export function decodeTemplateDetail(value: unknown, templateId?: string): TemplateDetail {
  require(isRecord(value) && isId(value.templateId) && (templateId === undefined || value.templateId === templateId) && isText(value.templateCode)
    && isText(value.templateName) && isOptionalText(value.description) && isOneOf(activeStatuses, value.status) && isInteger(value.currentVersionNo, 1)
    && isInteger(value.version, 1) && isText(value.createdBy) && isText(value.updatedBy) && isInstant(value.createTime) && isInstant(value.updateTime));
  const currentVersion = decodeTemplateVersion(value.currentVersion);
  require(currentVersion.versionNo === value.currentVersionNo);
  const jointQuery = decodeJointParticipation(value.jointQuery) ?? currentVersion.jointQuery;
  return { ...(value as unknown as TemplateDetail), description: orNull(value.description as string | null), currentVersion, jointQuery };
}

export function decodeVersionSummary(value: unknown): VersionSummary {
  require(isRecord(value) && isInteger(value.versionNo, 1) && isId(value.dataSourceId) && isText(value.dataSourceCode)
    && isOptionalInteger(value.restoredFromVersionNo, 1) && isOptionalText(value.changeNote) && isText(value.createdBy) && isInstant(value.createTime));
  return { ...(value as unknown as VersionSummary), restoredFromVersionNo: orNull(value.restoredFromVersionNo as number | null),
    changeNote: orNull(value.changeNote as string | null) };
}
export const decodeVersionPage = pageDecoder(decodeVersionSummary);

export function decodeValidation(value: unknown): ValidationResult {
  require(isRecord(value) && typeof value.valid === 'boolean' && Array.isArray(value.problems ?? []));
  return { valid: value.valid as boolean, problems: ((value.problems ?? []) as unknown[]).map(decodeProblem) };
}

export function decodeSaveResult(value: unknown, templateId?: string): SaveResult {
  require(isRecord(value) && isId(value.templateId) && (templateId === undefined || value.templateId === templateId) && isText(value.templateCode)
    && isOneOf(activeStatuses, value.status) && isInteger(value.version, 1) && isInteger(value.currentVersionNo, 1) && Array.isArray(value.warnings ?? []));
  return { ...(value as unknown as SaveResult), warnings: ((value.warnings ?? []) as unknown[]).map(decodeProblem) };
}

const isCell = (v: unknown) => v === null || typeof v === 'string' || typeof v === 'boolean';
export function decodeTrialResult(value: unknown): TrialResult {
  require(isRecord(value) && isInteger(value.versionNo) && Array.isArray(value.columns) && Array.isArray(value.rows)
    && typeof value.hasMore === 'boolean' && isOptionalInteger(value.total) && isOptionalText(value.nextCursor) && isInteger(value.elapsedMillis)
    && isText(value.sql) && isTextList(value.bindings ?? []) && isOptionalText(value.countSql));
  const columns = (value.columns as unknown[]).map(column => {
    require(isRecord(column) && isText(column.name) && isOptionalText(column.label));
    return { name: column.name as string, label: orNull(column.label as string | null) };
  });
  const rows = value.rows as unknown[];
  require(rows.every(row => Array.isArray(row) && row.length === columns.length && row.every(isCell)));
  return {
    versionNo: value.versionNo as number, columns, rows: rows as ResultCell[][], hasMore: value.hasMore as boolean,
    total: orNull(value.total as number | null), nextCursor: orNull(value.nextCursor as string | null), elapsedMillis: value.elapsedMillis as number,
    sql: value.sql as string, bindings: (value.bindings ?? []) as string[], countSql: orNull(value.countSql as string | null),
  };
}

// ---------- 参数摘要 ----------

export interface SummaryTag { text: string; required: boolean; kind: 'value' | 'list' | 'fragment' }

/** 参数摘要标签：必填加 *，集合加 []，片段写成 ⟨片段⟩（`summary.fragmentSuffix`）。 */
export function parameterSummaryTags(parameters: readonly Pick<ParameterSummary, 'name' | 'type' | 'required'>[]): SummaryTag[] {
  return parameters.map(parameter => {
    const kind = parameter.type === 'LIST' ? 'list' : parameter.type === 'FRAGMENT' ? 'fragment' : 'value';
    const suffix = kind === 'list' ? '[]' : kind === 'fragment' ? t('summary.fragmentSuffix') : '';
    return { text: `${parameter.name}${suffix}${parameter.required ? '*' : ''}`, required: parameter.required, kind };
  });
}

/** 类型展示：LIST 写成 LIST<元素类型>。 */
export function typeLabel(type: string, elementType?: string | null): string {
  return type === 'LIST' && elementType ? `LIST<${elementType}>` : type;
}

// ---------- 参数声明 ↔ 表单 ----------

let keySequence = 0;
/** 行的本地键：拖动排序与 React 列表使用，不进入请求。 */
export const nextRowKey = () => `row-${++keySequence}`;

export interface ChoiceRow { key: string; value: string; fragment: string }
export interface FieldRow {
  key: string; name: string; type: FieldType; required: boolean; maxLength: number | null; min: string; max: string; allowedValues: string[];
}
export interface ParameterRow {
  key: string; name: string; type: ParameterType; elementType: ElementType | null; required: boolean;
  /** 默认值文本：标量原文；BOOLEAN 为 true/false；LIST 为逗号分隔；LIST<OBJECT> 为 JSON。 */
  defaultText: string;
  maxLength: number | null; min: string; max: string; allowedValues: string[]; maxSize: number | null; choices: ChoiceRow[]; fields: FieldRow[];
  /** 按正文自动添加且未被修改过：正文不再引用时自动删除（见 templateSync.ts），不进入请求。 */
  auto?: boolean;
}

const boundText = (value: string | number | null | undefined) => value === null || value === undefined ? '' : String(value);

function defaultText(declaration: ParameterDeclaration): string {
  const value = declaration.default;
  if (value === undefined || value === null) return '';
  if (declaration.type === 'LIST') {
    if (declaration.elementType === 'OBJECT' || !Array.isArray(value) || value.some(item => isRecord(item) || Array.isArray(item))) return JSON.stringify(value);
    return value.map(String).join(', ');
  }
  return typeof value === 'string' ? value : JSON.stringify(value);
}

function fieldRow(declaration: ParameterDeclaration): FieldRow {
  return {
    key: nextRowKey(), name: declaration.name, type: (fieldTypes as readonly string[]).includes(declaration.type) ? declaration.type as FieldType : 'STRING',
    required: declaration.required === true, maxLength: declaration.maxLength ?? null, min: boundText(declaration.min), max: boundText(declaration.max),
    allowedValues: declaration.allowedValues ?? [],
  };
}

/** 参数声明 → 表单行；未知类型按 STRING 展示（服务端仍会拒绝原值）。 */
export function parameterRows(declarations: readonly ParameterDeclaration[]): ParameterRow[] {
  return declarations.map(declaration => ({
    key: nextRowKey(), name: declaration.name,
    type: (parameterTypes as readonly string[]).includes(declaration.type) ? declaration.type as ParameterType : 'STRING',
    elementType: declaration.elementType && (elementTypes as readonly string[]).includes(declaration.elementType) ? declaration.elementType as ElementType : null,
    required: declaration.required === true, defaultText: defaultText(declaration), maxLength: declaration.maxLength ?? null,
    min: boundText(declaration.min), max: boundText(declaration.max), allowedValues: declaration.allowedValues ?? [], maxSize: declaration.maxSize ?? null,
    choices: Object.entries(declaration.choices ?? {}).map(([value, fragment]) => ({ key: nextRowKey(), value, fragment })),
    fields: (declaration.fields ?? []).map(fieldRow),
  }));
}

export function emptyParameterRow(name = ''): ParameterRow {
  return { key: nextRowKey(), name, type: 'STRING', elementType: null, required: false, defaultText: '', maxLength: null, min: '', max: '',
    allowedValues: [], maxSize: null, choices: [], fields: [] };
}
export function emptyFieldRow(): FieldRow {
  return { key: nextRowKey(), name: '', type: 'STRING', required: false, maxLength: null, min: '', max: '', allowedValues: [] };
}

/** 某类型（或 LIST 的元素类型）适用的约束项。 */
export function constraintKinds(type: string | null): { length: boolean; range: boolean; allowed: boolean } {
  return {
    length: type === 'STRING', range: type === 'INTEGER' || type === 'DECIMAL' || type === 'DATE' || type === 'DATETIME',
    allowed: type === 'STRING' || type === 'ENUM',
  };
}

function scalarConstraints(type: string | null, row: { maxLength: number | null; min: string; max: string; allowedValues: string[] }) {
  const kinds = constraintKinds(type);
  return {
    ...(kinds.length && row.maxLength ? { maxLength: row.maxLength } : {}),
    ...(kinds.range && row.min.trim() ? { min: row.min.trim() } : {}),
    ...(kinds.range && row.max.trim() ? { max: row.max.trim() } : {}),
    ...(kinds.allowed && row.allowedValues.length ? { allowedValues: [...row.allowedValues] } : {}),
  };
}

function defaultValue(row: ParameterRow): unknown {
  const text = row.defaultText.trim();
  if (!text) return undefined;
  if (row.type === 'BOOLEAN') return text === 'true' ? true : text === 'false' ? false : text;
  if (row.type !== 'LIST') return text;
  if (row.elementType === 'OBJECT' || text.startsWith('[')) {
    try { return JSON.parse(text); } catch { return text; }
  }
  return text.split(',').map(item => item.trim()).filter(Boolean)
    .map(item => row.elementType === 'BOOLEAN' ? item === 'true' : item);
}

/** 表单行 → parameters_json：只输出当前类型适用的属性，空约束不输出；不可解析的默认值原样交给服务端报错。 */
export function parameterDeclarations(rows: readonly ParameterRow[]): ParameterDeclaration[] {
  return rows.map(row => {
    const value = defaultValue(row);
    const constrained = row.type === 'LIST' ? row.elementType : row.type;
    return {
      name: row.name.trim(), type: row.type,
      ...(row.type === 'LIST' && row.elementType ? { elementType: row.elementType } : {}),
      ...(row.required ? { required: true } : {}),
      ...(value !== undefined ? { default: value } : {}),
      ...(row.type === 'FRAGMENT' ? {} : scalarConstraints(constrained, row)),
      ...(row.type === 'LIST' && row.maxSize ? { maxSize: row.maxSize } : {}),
      ...(row.type === 'FRAGMENT' ? { choices: Object.fromEntries(row.choices.filter(choice => choice.value.trim()).map(choice => [choice.value.trim(), choice.fragment])) } : {}),
      ...(row.type === 'LIST' && row.elementType === 'OBJECT' ? { fields: row.fields.map(field => ({
        name: field.name.trim(), type: field.type, ...(field.required ? { required: true } : {}), ...scalarConstraints(field.type, field),
      })) } : {}),
    };
  });
}

/** 表格“默认 / 约束”列的简短说明。 */
export function parameterHint(row: ParameterRow): string {
  const parts: string[] = [];
  const constrained = row.type === 'LIST' ? row.elementType : row.type;
  if (row.defaultText.trim()) parts.push(t('hint.default', { value: row.defaultText.trim() }));
  if (row.type === 'FRAGMENT') parts.push(t('hint.choices', { count: row.choices.length }));
  if (row.type === 'LIST' && row.maxSize) parts.push(t('hint.maxSize', { count: row.maxSize }));
  if (row.type === 'LIST' && row.elementType === 'OBJECT') parts.push(t('hint.fields', { count: row.fields.length }));
  if (constrained !== 'FRAGMENT' && constraintKinds(constrained).length && row.maxLength) parts.push(t('hint.maxLength', { length: row.maxLength }));
  if (constraintKinds(constrained).range && row.min.trim()) parts.push(`≥ ${row.min.trim()}`);
  if (constraintKinds(constrained).range && row.max.trim()) parts.push(`≤ ${row.max.trim()}`);
  if (constraintKinds(constrained).allowed && row.allowedValues.length) parts.push(row.allowedValues.join(' / '));
  return parts.join(' · ');
}

export function moveRow<T>(rows: readonly T[], from: number, to: number): T[] {
  if (from === to || from < 0 || to < 0 || from >= rows.length || to >= rows.length) return [...rows];
  const next = [...rows]; const [item] = next.splice(from, 1); next.splice(to, 0, item);
  return next;
}

// ---------- 约束与列 ----------

export function defaultConstraints(): QueryConstraints {
  return { maxRows: 1000, timeoutSeconds: 5, pagingMode: 'OFFSET', maxPageSize: 100, countAllowed: true, cursorColumns: [] };
}

/** 约束请求体：只输出当前分页方式适用的字段。 */
export function constraintsBody(constraints: QueryConstraints): QueryConstraints {
  const base = { maxRows: constraints.maxRows, timeoutSeconds: constraints.timeoutSeconds, pagingMode: constraints.pagingMode };
  if (constraints.pagingMode === 'OFFSET') return { ...base, maxPageSize: constraints.maxPageSize ?? null, countAllowed: constraints.countAllowed === true };
  if (constraints.pagingMode === 'CURSOR') return { ...base, maxPageSize: constraints.maxPageSize ?? null, cursorColumns: constraints.cursorColumns ?? [] };
  return base;
}

export interface ColumnRow {
  key: string; name: string; label: string; mask: MaskMode; keepStart: number | null; keepEnd: number | null;
  /** 按正文或试运行结果自动添加且未被修改过，不进入请求。 */
  auto?: boolean;
}
export const columnRows = (columns: readonly QueryColumn[]): ColumnRow[] => columns.map(column => ({
  key: nextRowKey(), name: column.name, label: column.label ?? '', mask: column.mask ?? 'NONE', keepStart: column.keepStart ?? null, keepEnd: column.keepEnd ?? null,
}));
export const emptyColumnRow = (): ColumnRow => ({ key: nextRowKey(), name: '', label: '', mask: 'NONE', keepStart: null, keepEnd: null });
export function columnsBody(rows: readonly ColumnRow[]): QueryColumn[] {
  return rows.map(row => ({
    name: row.name.trim(), ...(row.label.trim() ? { label: row.label.trim() } : {}), mask: row.mask,
    ...(row.mask === 'KEEP_EDGES' ? { keepStart: row.keepStart ?? 0, keepEnd: row.keepEnd ?? 0 } : {}),
  }));
}

// ---------- 草稿与请求体 ----------

export interface TemplateDraft {
  templateCode: string; templateName: string; description: string; dataSourceId: string | null; body: string;
  parameters: ParameterRow[]; columns: ColumnRow[]; constraints: QueryConstraints;
  /** 联合查询页签；与参数、列分开保存为 joint。 */
  joint: JointDraft;
}

export function emptyDraft(): TemplateDraft {
  return { templateCode: '', templateName: '', description: '', dataSourceId: null, body: 'SELECT\n  \nFROM\n  ', parameters: [], columns: [], constraints: defaultConstraints(),
    joint: emptyJointDraft() };
}

export function draftFromDetail(detail: TemplateDetail, version: TemplateVersion = detail.currentVersion): TemplateDraft {
  return {
    templateCode: detail.templateCode, templateName: detail.templateName, description: detail.description ?? '', dataSourceId: version.dataSourceId,
    body: version.body, parameters: parameterRows(version.parameters), columns: columnRows(version.columns), constraints: { ...version.constraints },
    joint: jointDraft(version.joint),
  };
}

/** 校验与试运行共用的内容字段。 */
export function contentBody(draft: TemplateDraft) {
  return { dataSourceId: draft.dataSourceId, body: draft.body, parameters: parameterDeclarations(draft.parameters),
    columns: columnsBody(draft.columns), constraints: constraintsBody(draft.constraints) };
}
const changeNoteOf = (note: string) => note.trim() ? note.trim() : null;
export function createTemplateBody(draft: TemplateDraft, changeNote: string) {
  return { templateCode: draft.templateCode.trim(), templateName: draft.templateName.trim(), description: draft.description.trim() || null,
    ...contentBody(draft), joint: jointBody(draft.joint), changeNote: changeNoteOf(changeNote) };
}
export function updateTemplateBody(draft: TemplateDraft, expectedVersion: number, changeNote: string) {
  return { templateName: draft.templateName.trim(), description: draft.description.trim() || null, ...contentBody(draft),
    joint: jointBody(draft.joint), expectedVersion, changeNote: changeNoteOf(changeNote) };
}
/** 未保存判断：与基线草稿的请求内容比较，行键不参与。 */
export const draftFingerprint = (draft: TemplateDraft) => JSON.stringify({ ...createTemplateBody(draft, ''), templateCode: draft.templateCode.trim() });

/** 正文 UTF-8 字节数（上限见 templateLimits.maxBodyBytes）。 */
export const bodyBytes = (body: string) => new TextEncoder().encode(body).length;

// ---------- 问题 → 编辑器标记与定位 ----------

/** Platform 校验信息的位置前缀"第 N 行第 M 列："（服务端数据，按原文匹配；汉字写成转义以免被中文扫描误判）。 */
const positionPrefix = /^\u7b2c\s*(\d+)\s*\u884c\u7b2c\s*(\d+)\s*\u5217[:\uff1a]\s*/;

/** 422 字段错误 → 问题：body 的“第 N 行第 M 列：”前缀解析为行列。 */
export function fieldErrorProblems(errors: readonly { field: string; message: string }[]): TemplateProblem[] {
  return errors.map(error => {
    const match = error.field === 'body' ? positionPrefix.exec(error.message) : null;
    return match
      ? { level: 'ERROR', line: Number(match[1]), column: Number(match[2]), field: 'body', message: error.message.slice(match[0].length) }
      : { level: 'ERROR', line: null, column: null, field: error.field, message: error.message };
  });
}

export interface EditorDiagnostic { from: number; to: number; severity: 'error' | 'warning'; message: string }

/** 从列位置起的标记长度：标签到 }} 为止，值与片段到 } 为止，标识符到词尾，其余 1 个字符。 */
function markLength(lineText: string, offset: number): number {
  const rest = lineText.slice(offset);
  const tag = /^\{\{[^}]*\}\}/.exec(rest) ?? /^[#@]\{[^}]*\}/.exec(rest) ?? /^[A-Za-z_][A-Za-z0-9_.]*/.exec(rest);
  return tag ? tag[0].length : Math.min(1, rest.length);
}

/**
 * 服务端问题（1 起始的行、列）→ 编辑器诊断（文档偏移）。没有行号的问题只进问题面板；行号越界的问题标在最后一行；
 * 列越界时标整行。空行至少标 0 长度位置（行号旁仍有标记）。
 */
export function problemDiagnostics(body: string, problems: readonly TemplateProblem[]): EditorDiagnostic[] {
  const lines = body.split('\n');
  const starts: number[] = []; let offset = 0;
  for (const line of lines) { starts.push(offset); offset += line.length + 1; }
  return problems.filter(problem => problem.line !== null).map(problem => {
    const lineIndex = Math.min(Math.max(problem.line! - 1, 0), lines.length - 1);
    const lineText = lines[lineIndex];
    const column = problem.column ?? 1;
    const inLine = column - 1 <= lineText.length;
    const from = starts[lineIndex] + (inLine ? Math.max(column - 1, 0) : 0);
    const length = inLine ? markLength(lineText, Math.max(column - 1, 0)) : lineText.length;
    return { from, to: from + length, severity: problem.level === 'ERROR' ? 'error' : 'warning', message: problem.message };
  });
}

export type ProblemTarget =
  | { kind: 'body'; line: number; column: number }
  | { kind: 'parameter'; index: number }
  | { kind: 'constraints'; field: string }
  | { kind: 'columns'; index: number }
  | { kind: 'joint'; field: string }
  | { kind: 'dataSource' }
  | { kind: 'none' };

/** 点击问题时的定位目标：有行号定位正文，否则按 field 前缀切换右侧标签页。 */
export function problemTarget(problem: TemplateProblem): ProblemTarget {
  if (problem.line !== null) return { kind: 'body', line: problem.line, column: problem.column ?? 1 };
  const field = problem.field ?? '';
  const indexed = /^(parameters|columns)\[(\d+)]/.exec(field);
  if (indexed) return indexed[1] === 'parameters' ? { kind: 'parameter', index: Number(indexed[2]) } : { kind: 'columns', index: Number(indexed[2]) };
  if (field === 'parameters') return { kind: 'parameter', index: 0 };
  if (field === 'columns') return { kind: 'columns', index: 0 };
  if (field === 'joint' || field.startsWith('joint.')) return { kind: 'joint', field };
  if (field.startsWith('constraints')) return { kind: 'constraints', field: field.replace(/^constraints\.?/, '') };
  if (field === 'dataSourceId') return { kind: 'dataSource' };
  return { kind: 'none' };
}

export function problemLocation(problem: TemplateProblem): string {
  if (problem.line !== null) return `${problem.line} : ${problem.column ?? 1}`;
  return problem.field ?? '';
}

// ---------- 自动补全 ----------

export interface CompletionWord { label: string; detail: string; kind: 'value' | 'fragment' | 'tag' | 'system' }

/** 补全词：参数名（#{} 中使用）、片段名（@{} 中使用）、标签与系统参数。 */
export function completionWords(rows: readonly ParameterRow[]): CompletionWord[] {
  const named = rows.filter(row => row.name.trim());
  return [
    ...named.filter(row => row.type !== 'FRAGMENT').map(row => ({ label: row.name.trim(), detail: typeLabel(row.type, row.elementType), kind: 'value' as const })),
    ...named.filter(row => row.type === 'LIST' && row.elementType === 'OBJECT')
      .flatMap(row => row.fields.filter(field => field.name.trim()).map(field => ({ label: `${row.name.trim()}.${field.name.trim()}`, detail: t('completion.loopField', { type: field.type }), kind: 'value' as const }))),
    ...named.filter(row => row.type === 'FRAGMENT').map(row => ({ label: row.name.trim(), detail: t('completion.fragment'), kind: 'fragment' as const })),
    ...templateTags.map(tag => ({ label: tag, detail: t('completion.tag'), kind: 'tag' as const })),
    ...systemParameters.map(name => ({ label: name, detail: t('completion.system'), kind: 'system' as const })),
  ];
}

// ---------- 试运行表单 ----------

export type TrialControl = 'text' | 'integer' | 'decimal' | 'boolean' | 'date' | 'datetime' | 'select' | 'tags' | 'json';
export interface TrialField { name: string; control: TrialControl; required: boolean; options: string[]; label: string; placeholder: string }

/** 按参数声明生成试运行输入：集合为标签输入（OBJECT 集合为 JSON），片段与枚举为下拉，日期与时间为选择器。 */
export function trialFields(rows: readonly ParameterRow[]): TrialField[] {
  return rows.filter(row => row.name.trim()).map(row => {
    const name = row.name.trim();
    const label = typeLabel(row.type, row.elementType);
    const placeholder = row.defaultText.trim() ? t('hint.default', { value: row.defaultText.trim() }) : t('trial.empty');
    const base = { name, required: row.required, label, placeholder, options: [] as string[] };
    switch (row.type) {
      case 'FRAGMENT': return { ...base, control: 'select', options: row.choices.map(choice => choice.value.trim()).filter(Boolean) };
      case 'ENUM': return { ...base, control: 'select', options: [...row.allowedValues] };
      case 'STRING': return row.allowedValues.length ? { ...base, control: 'select', options: [...row.allowedValues] } : { ...base, control: 'text' };
      case 'LIST': return row.elementType === 'OBJECT' ? { ...base, control: 'json', placeholder: t('trial.jsonPlaceholder') }
        : { ...base, control: 'tags', options: [...row.allowedValues] };
      case 'INTEGER': return { ...base, control: 'integer' };
      case 'DECIMAL': return { ...base, control: 'decimal' };
      case 'BOOLEAN': return { ...base, control: 'boolean' };
      case 'DATE': return { ...base, control: 'date' };
      case 'DATETIME': return { ...base, control: 'datetime' };
      default: return { ...base, control: 'text' };
    }
  });
}

export type TrialValue = string | number | boolean | string[] | null | undefined;

export interface TrialParamsResult { params: Record<string, unknown>; errors: { name: string; message: string }[] }

/**
 * 试运行输入 → params：未填写的参数不传（由服务端取默认值）；数字按字符串传，避免精度丢失；
 * JSON 输入必须是数组。其余类型与范围校验以服务端为准。
 */
export function trialParams(fields: readonly TrialField[], values: Readonly<Record<string, TrialValue>>): TrialParamsResult {
  const params: Record<string, unknown> = {}; const errors: { name: string; message: string }[] = [];
  for (const field of fields) {
    const value = values[field.name];
    if (value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)) continue;
    if (field.control === 'json') {
      try {
        const parsed: unknown = JSON.parse(String(value));
        if (Array.isArray(parsed)) params[field.name] = parsed; else errors.push({ name: field.name, message: t('trial.jsonArrayRequired') });
      } catch { errors.push({ name: field.name, message: t('trial.jsonInvalid') }); }
      continue;
    }
    if (field.control === 'integer' || field.control === 'decimal') { params[field.name] = String(value).trim(); continue; }
    params[field.name] = value;
  }
  return { params, errors };
}

export interface TrialPaging { page?: number; size?: number; withTotal: boolean; cursor?: string }

/** 分页字段：只发送当前分页方式适用的字段；总数仅偏移分页且允许时可选。 */
export function trialPaging(constraints: QueryConstraints, input: { page?: number; size?: number; withTotal?: boolean; cursor?: string | null }): TrialPaging {
  if (constraints.pagingMode === 'OFFSET') {
    return { page: input.page ?? 1, size: input.size ?? constraints.maxPageSize ?? undefined, withTotal: constraints.countAllowed === true && input.withTotal === true };
  }
  if (constraints.pagingMode === 'CURSOR') {
    return { size: input.size ?? constraints.maxPageSize ?? undefined, withTotal: false, ...(input.cursor ? { cursor: input.cursor } : {}) };
  }
  return { withTotal: false };
}

/** 结果单元格展示：null 显示为 NULL，布尔显示 true/false。 */
export const cellText = (cell: ResultCell) => cell === null ? 'NULL' : String(cell);
