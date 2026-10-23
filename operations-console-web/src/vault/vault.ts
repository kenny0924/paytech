import { ApiError, session } from '../api/session.ts';
import { resolveTimeRange } from '../components/timeRange.ts';
import type { TimeRangeValue } from '../components/timeRange.ts';
import { enumLabel, translator } from '../i18n/index.ts';

const t = translator('vault');

/** Vault 管理接口前缀（BFF）。 */
export const vaultAdminPath = '/vault-admin/v1';

export interface VaultDataType {
  dataTypeCode: string; tokenMode: 'REFERENCE' | 'STABLE'; dataClass: 'STANDARD' | 'SECRET'; description: string;
  manualProtectEnabled: boolean; manualRevealEnabled: boolean; status: 'ACTIVE' | 'DISABLED'; version: number; updateTime: string;
  /** 值格式；旧响应缺失时按 TEXT。 */
  valueFormat: VaultValueFormat;
  /** 正则（整值匹配），仅 REGEX 时出现。 */
  valuePattern?: string;
}
export type VaultValueFormat = 'TEXT' | 'JSON' | 'REGEX';
export interface VaultDataTypeList { currentPolicyVersion: number; items: VaultDataType[] }
export interface VaultDataTypeMutation { dataType: VaultDataType; policyVersion: number }
export interface VaultAccessRule {
  accessRuleId: string; callerService: string; action: 'PROTECT' | 'REVEAL'; dataTypeCode: string; purposeCode: string;
  status: 'ACTIVE' | 'DISABLED'; version: number; updateTime: string;
}
export interface VaultAccessRuleMutation { accessRule: VaultAccessRule; policyVersion: number }
export interface VaultPolicyChange {
  policyVersionNo: number; changeType: string; targetType: string; targetKey: string; actorId: string; reason: string;
  createTime: string; beforeSnapshot?: Record<string, unknown> | null; afterSnapshot?: Record<string, unknown> | null;
}
export interface VaultManualEvent {
  requestId: string; actorId: string; action: 'PROTECT' | 'REVEAL'; dataTypeCode: string; reasonCode: string; ticketRef: string;
  outcome: 'SUCCESS' | 'DENIED' | 'FAILED'; resultCode: string; createTime: string;
}

/**
 * 显示名均在 `vault` 命名空间，用 enumLabel 取：`enum.tokenMode`、`enum.dataClass`、`enum.valueFormat`、`enum.status`、`enum.action`、
 * `enum.outcome`、`enum.changeType`、`enum.targetType`、`enum.reasonCode`、`snapshot.field`；这里只保留代码与顺序。
 */
export const tokenModes = ['REFERENCE', 'STABLE'] as const;
export const dataClasses = ['STANDARD', 'SECRET'] as const;
export const valueFormats: readonly VaultValueFormat[] = ['TEXT', 'JSON', 'REGEX'];
export const vaultStatuses = ['ACTIVE', 'DISABLED'] as const;
export const vaultActions = ['PROTECT', 'REVEAL'] as const;
export const manualOutcomes = ['SUCCESS', 'DENIED', 'FAILED'] as const;
export const changeTypes = ['SEED', 'DATA_TYPE_CREATE', 'DATA_TYPE_UPDATE', 'RULE_CREATE', 'RULE_UPDATE'] as const;
export const targetTypes = ['POLICY', 'DATA_TYPE', 'ACCESS_RULE'] as const;
export const reasonCodes = ['CUSTOMER_VERIFICATION', 'DATA_REPAIR', 'CHANNEL_CONFIGURATION', 'INCIDENT_INVESTIGATION', 'OTHER'] as const;
/** Vault 值格式相关错误码；说明文字是固定文案（`valueFormatError.<code>`），不回显原值、样例或正则编译器报错。 */
export const valueFormatErrorCodes: readonly string[] = ['VALUE_NOT_JSON_OBJECT', 'VALUE_PATTERN_MISMATCH', 'VALUE_PATTERN_INVALID', 'SAMPLE_VALUE_INVALID'];

/** 值格式下拉选项：调用时按当前语言取名称与说明。 */
export function valueFormatOptions(): { value: VaultValueFormat; label: string; description: string }[] {
  return valueFormats.map(value => ({ value, label: enumLabel(t, 'enum.valueFormat', value), description: t('valueFormat.description.' + value) }));
}

export const codePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
export const callerPattern = /^[a-z][a-z0-9-]{0,127}$/;
export const ticketPattern = /^[A-Za-z0-9._#-]{1,64}$/;
export const tokenPattern = /^[A-Za-z0-9_-]{43}$/;
/** 解密结果在页面上可见的最长时间。 */
export const revealVisibleSeconds = 30;

const version = (v: unknown) => Number.isInteger(v) && Number(v) > 0 && Number(v) <= 2147483647;
const invalid = () => new ApiError(502, 'INVALID_RESPONSE');

/** 校验数据类型条目的必要字段；值格式缺失视为 TEXT，正则缺失或为 null（BFF 序列化空字段的方式）视为无，REGEX 必须带正则。 */
function isDataType(v: VaultDataType | undefined): boolean {
  return !!v && codePattern.test(v.dataTypeCode) && ['REFERENCE', 'STABLE'].includes(v.tokenMode)
    && ['STANDARD', 'SECRET'].includes(v.dataClass) && ['ACTIVE', 'DISABLED'].includes(v.status)
    && typeof v.manualProtectEnabled === 'boolean' && typeof v.manualRevealEnabled === 'boolean' && version(v.version)
    && (v.valueFormat === undefined || ['TEXT', 'JSON', 'REGEX'].includes(v.valueFormat))
    && (v.valuePattern == null || typeof v.valuePattern === 'string') && (v.valueFormat !== 'REGEX' || typeof v.valuePattern === 'string');
}
const withValueFormat = ({ valuePattern, ...v }: VaultDataType): VaultDataType => ({ ...v, valueFormat: v.valueFormat ?? 'TEXT', ...(valuePattern == null ? {} : { valuePattern }) });

/** 数据类型列表响应。 */
export function decodeDataTypeList(value: unknown): VaultDataTypeList {
  const v = value as VaultDataTypeList;
  if (!v || !Number.isInteger(v.currentPolicyVersion) || !Array.isArray(v.items) || !v.items.every(isDataType)) throw invalid();
  return { ...v, items: v.items.map(withValueFormat) };
}

/** 数据类型保存结果；编码必须与提交一致。 */
export function decodeDataTypeMutation(value: unknown, expectedCode: string): VaultDataTypeMutation {
  const v = value as VaultDataTypeMutation;
  if (!v || !isDataType(v.dataType) || v.dataType.dataTypeCode !== expectedCode || !version(v.policyVersion)) throw invalid();
  return { ...v, dataType: withValueFormat(v.dataType) };
}

/**
 * 保存时是否必须带测试样例：结果为 REGEX，且属于新建、从其他格式切换过来或正则有变化。
 */
export function sampleValuesRequired(previous: VaultDataType | null, valueFormat: VaultValueFormat, valuePattern: string | undefined): boolean {
  if (valueFormat !== 'REGEX') return false;
  if (!previous || previous.valueFormat !== 'REGEX') return true;
  return previous.valuePattern !== valuePattern;
}

/** 数据类型保存请求中的值格式部分：总带 valueFormat；仅 REGEX 带正则，样例非空时才带（空输入框忽略）。 */
export function valueFormatBody(valueFormat: VaultValueFormat, valuePattern: string | undefined, sampleValues: readonly (string | undefined)[] | undefined) {
  if (valueFormat !== 'REGEX') return { valueFormat };
  const samples = (sampleValues ?? []).filter((sample): sample is string => typeof sample === 'string' && sample !== '');
  return { valueFormat, valuePattern, ...(samples.length ? { sampleValues: samples } : {}) };
}

/** 前端预检：整个值是 JSON 对象（根为 {}，不能是数组或 null）；重复键由 Vault 判断。 */
export function isJsonObjectText(text: string): boolean {
  try {
    const parsed: unknown = JSON.parse(text);
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed);
  } catch { return false; }
}

/** 值格式错误码换成固定说明，其余错误原样返回。 */
export function withValueFormatMessage(error: unknown): unknown {
  if (!(error instanceof ApiError) || !valueFormatErrorCodes.includes(error.code)) return error;
  const replaced = new ApiError(error.status, error.code, error.requestId, undefined, error.fieldErrors);
  replaced.message = t('valueFormatError.' + error.code);
  return replaced;
}

/** 访问规则保存结果。 */
export function decodeAccessRuleMutation(value: unknown): VaultAccessRuleMutation {
  const v = value as VaultAccessRuleMutation;
  if (!v?.accessRule || !/^[1-9][0-9]{0,18}$/.test(v.accessRule.accessRuleId) || !version(v.accessRule.version)
    || !version(v.policyVersion)) throw invalid();
  return v;
}

/** 人工加密结果。 */
export function decodeProtect(value: unknown): { token: string } {
  const v = value as { token: string };
  if (!v || !tokenPattern.test(v.token)) throw invalid();
  return { token: v.token };
}

/** 人工解密结果。 */
export function decodeReveal(value: unknown): { dataTypeCode: string; value: string } {
  const v = value as { dataTypeCode: string; value: string };
  if (!v || !codePattern.test(v.dataTypeCode) || typeof v.value !== 'string') throw invalid();
  return { dataTypeCode: v.dataTypeCode, value: v.value };
}

/** 策略变更详情；版本号必须与请求一致。 */
export function decodePolicyChange(value: unknown, expectedVersion: number): VaultPolicyChange {
  const v = value as VaultPolicyChange;
  if (!v || v.policyVersionNo !== expectedVersion || typeof v.targetKey !== 'string') throw invalid();
  return v;
}

/**
 * 人工操作可选的数据类型：ACTIVE、开启对应人工开关；没有密钥类权限时不出现 SECRET。
 */
export function manualDataTypes(items: readonly VaultDataType[], kind: 'protect' | 'reveal', canSecret: boolean): VaultDataType[] {
  return items.filter(item => item.status === 'ACTIVE'
    && (kind === 'protect' ? item.manualProtectEnabled : item.manualRevealEnabled)
    && (canSecret || item.dataClass === 'STANDARD'));
}

/** 人工加/解密调用的路由：有密钥类权限时走密钥路由（允许 STANDARD 与 SECRET）。 */
export function manualPath(kind: 'protect' | 'reveal', canSecret: boolean): string {
  return `${vaultAdminPath}/manual/${canSecret ? `secret-${kind}` : kind}`;
}

export interface SnapshotRow { field: string; label: string; before: string; after: string; changed: boolean }

/** 变更前后快照逐字段对比；新建时变更前为空。 */
export function snapshotRows(before: Record<string, unknown> | null | undefined, after: Record<string, unknown> | null | undefined): SnapshotRow[] {
  const fields = [...new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})])];
  const text = (value: unknown) => value === undefined || value === null ? '—' : typeof value === 'boolean' ? (value ? t('snapshot.yes') : t('snapshot.no')) : String(value);
  return fields.map(field => {
    const b = text(before?.[field]); const a = text(after?.[field]);
    return { field, label: enumLabel(t, 'snapshot.field', field), before: b, after: a, changed: b !== a };
  });
}

export interface ManualEventFilterValues { actorId?: string; action?: string; dataTypeCode?: string; timeRange?: TimeRangeValue }

/** 人工操作记录筛选：时间范围转为 UTC RFC 3339，结束时间不含。 */
export function manualEventFilters(values: ManualEventFilterValues, now = new Date()): Record<string, string> {
  const filters: Record<string, string> = {};
  const range = resolveTimeRange(values.timeRange, now);
  if (values.timeRange?.preset === 'CUSTOM' && (!range.start || !range.end)) throw new Error(t('filters.incompleteRange'));
  for (const [key, value] of [['from', range.start], ['to', range.end]] as const) {
    if (!value) continue;
    const instant = new Date(value);
    if (!Number.isFinite(instant.getTime())) throw new Error(t('filters.invalidTime'));
    filters[key] = instant.toISOString();
  }
  if (filters.from && filters.to && filters.from >= filters.to) throw new Error(t('filters.endBeforeStart'));
  for (const key of ['actorId', 'action', 'dataTypeCode'] as const) {
    const value = values[key]?.trim();
    if (value) filters[key] = value;
  }
  return filters;
}

/** 每次打开人工加密表单生成一次提交 ID；重复提交返回同一 Token。 */
export function newSubmissionId(): string {
  return crypto.randomUUID();
}

/**
 * 发出 Vault 管理写请求；不带 Idempotency-Key（Vault 路由不经 Gateway 请求幂等，人工加密以 submissionId 防重）。
 */
export function vaultCommand<T>(path: string, method: 'POST' | 'PUT', body: unknown, decode: (value: unknown) => T): Promise<T> {
  return session.request<T>(path, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }, decode);
}
