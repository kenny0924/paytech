import { ApiError, session } from '../api/session.ts';
import { resolveTimeRange } from '../components/timeRange.ts';
import type { TimeRangeValue } from '../components/timeRange.ts';

/** Vault 管理接口前缀（BFF）。 */
export const vaultAdminPath = '/vault-admin/v1';

export interface VaultDataType {
  dataTypeCode: string; tokenMode: 'REFERENCE' | 'STABLE'; dataClass: 'STANDARD' | 'SECRET'; description: string;
  manualProtectEnabled: boolean; manualRevealEnabled: boolean; status: 'ACTIVE' | 'DISABLED'; version: number; updateTime: string;
}
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

export const tokenModeLabels: Record<string, string> = { REFERENCE: '每次新 Token', STABLE: '同值同 Token' };
export const dataClassLabels: Record<string, string> = { STANDARD: '普通敏感', SECRET: '密钥' };
export const statusLabels: Record<string, string> = { ACTIVE: '启用', DISABLED: '停用' };
export const actionLabels: Record<string, string> = { PROTECT: '加密', REVEAL: '解密' };
export const outcomeLabels: Record<string, string> = { SUCCESS: '成功', DENIED: '拒绝', FAILED: '失败' };
export const changeTypeLabels: Record<string, string> = {
  SEED: '初始化', DATA_TYPE_CREATE: '新建数据类型', DATA_TYPE_UPDATE: '修改数据类型', RULE_CREATE: '新建访问规则', RULE_UPDATE: '修改访问规则',
};
export const targetTypeLabels: Record<string, string> = { POLICY: '整体策略', DATA_TYPE: '数据类型', ACCESS_RULE: '访问规则' };
export const reasonCodeLabels: Record<string, string> = {
  CUSTOMER_VERIFICATION: '核对客户信息', DATA_REPAIR: '数据修复', CHANNEL_CONFIGURATION: '渠道配置',
  INCIDENT_INVESTIGATION: '事件调查', OTHER: '其他',
};
export const snapshotFieldLabels: Record<string, string> = {
  dataTypeCode: '编码', tokenMode: 'Token 模式', dataClass: '数据级别', description: '用途说明', manualProtectEnabled: '允许人工加密',
  manualRevealEnabled: '允许人工解密', status: '状态', callerService: '调用服务', action: '动作', purposeCode: '用途',
};

export const codePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
export const callerPattern = /^[a-z][a-z0-9-]{0,127}$/;
export const ticketPattern = /^[A-Za-z0-9._#-]{1,64}$/;
export const tokenPattern = /^[A-Za-z0-9_-]{43}$/;
/** 解密结果在页面上可见的最长时间。 */
export const revealVisibleSeconds = 30;

const version = (v: unknown) => Number.isInteger(v) && Number(v) > 0 && Number(v) <= 2147483647;
const invalid = () => new ApiError(502, 'INVALID_RESPONSE');

/** 校验数据类型条目的必要字段。 */
function isDataType(v: VaultDataType | undefined): boolean {
  return !!v && codePattern.test(v.dataTypeCode) && ['REFERENCE', 'STABLE'].includes(v.tokenMode)
    && ['STANDARD', 'SECRET'].includes(v.dataClass) && ['ACTIVE', 'DISABLED'].includes(v.status)
    && typeof v.manualProtectEnabled === 'boolean' && typeof v.manualRevealEnabled === 'boolean' && version(v.version);
}

/** 数据类型列表响应。 */
export function decodeDataTypeList(value: unknown): VaultDataTypeList {
  const v = value as VaultDataTypeList;
  if (!v || !Number.isInteger(v.currentPolicyVersion) || !Array.isArray(v.items) || !v.items.every(isDataType)) throw invalid();
  return v;
}

/** 数据类型保存结果；编码必须与提交一致。 */
export function decodeDataTypeMutation(value: unknown, expectedCode: string): VaultDataTypeMutation {
  const v = value as VaultDataTypeMutation;
  if (!v || !isDataType(v.dataType) || v.dataType.dataTypeCode !== expectedCode || !version(v.policyVersion)) throw invalid();
  return v;
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
  const text = (value: unknown) => value === undefined || value === null ? '—' : typeof value === 'boolean' ? (value ? '是' : '否') : String(value);
  return fields.map(field => {
    const b = text(before?.[field]); const a = text(after?.[field]);
    return { field, label: snapshotFieldLabels[field] ?? field, before: b, after: a, changed: b !== a };
  });
}

export interface ManualEventFilterValues { actorId?: string; action?: string; dataTypeCode?: string; timeRange?: TimeRangeValue }

/** 人工操作记录筛选：时间范围转为 UTC RFC 3339，结束时间不含。 */
export function manualEventFilters(values: ManualEventFilterValues, now = new Date()): Record<string, string> {
  const filters: Record<string, string> = {};
  const range = resolveTimeRange(values.timeRange, now);
  if (values.timeRange?.preset === 'CUSTOM' && (!range.start || !range.end)) throw new Error('请选择完整的开始和结束时间');
  for (const [key, value] of [['from', range.start], ['to', range.end]] as const) {
    if (!value) continue;
    const instant = new Date(value);
    if (!Number.isFinite(instant.getTime())) throw new Error('请输入有效时间');
    filters[key] = instant.toISOString();
  }
  if (filters.from && filters.to && filters.from >= filters.to) throw new Error('结束时间必须晚于开始时间');
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
