import { ApiError, session } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { configurationCommand } from '../configuration/configurationModel.ts';
import type { ConfigurationCommand } from '../configuration/configurationModel.ts';

// ---------- 路径、路由与按钮 ----------

const base = '/channel-admin/v1';
const versionPath = (planCode: string, costVer: string) => `${base}/channel-cost-plans/${planCode}/versions/${costVer}`;

/** Operations BFF 渠道路径（/channel-admin/v1，经 Gateway /api/operations 前缀）。 */
export const channelPaths = {
  channels: `${base}/channels`,
  channel: (channelId: string) => `${base}/channels/${channelId}`,
  channelStatus: (channelId: string) => `${base}/channels/${channelId}/status`,
  accounts: `${base}/channel-accounts`,
  account: (accountId: string) => `${base}/channel-accounts/${accountId}`,
  accountConfiguration: (accountId: string) => `${base}/channel-accounts/${accountId}/configuration`,
  accountStatus: (accountId: string) => `${base}/channel-accounts/${accountId}/status`,
  mids: `${base}/mids`,
  mid: (midId: string) => `${base}/mids/${midId}`,
  midAccount: (midId: string) => `${base}/mids/${midId}/account`,
  midStatus: (midId: string) => `${base}/mids/${midId}/status`,
  capabilityDefinitions: `${base}/mid-capabilities/definitions`,
  capabilities: `${base}/mid-capabilities`,
  capabilityDetails: (capabilityId: string) => `${base}/mid-capabilities/${capabilityId}/details`,
  capabilityStatus: (capabilityId: string) => `${base}/mid-capabilities/${capabilityId}/status`,
  changes: `${base}/config-changes`,
  costPlans: `${base}/channel-cost-plans`,
  costPlan: (planCode: string) => `${base}/channel-cost-plans/${planCode}`,
  costVersions: (planCode: string) => `${base}/channel-cost-plans/${planCode}/versions`,
  costVersion: versionPath,
  costVersionAction: (planCode: string, costVer: string, action: CostVersionAction) => `${versionPath(planCode, costVer)}/${action}`,
  costVersionDiff: (planCode: string, baseCostVer: string, targetCostVer: string) => `${versionPath(planCode, baseCostVer)}/diff/${targetCostVer}`,
} as const;

/** 页面路由（与 Console Resource frontend_route 及 pageRoutePaths 一致）。 */
export const channelRoutes = {
  channels: '/channel/channels',
  channel: (channelId: string) => `/channel/channels/${channelId}`,
  mids: '/channel/mids',
  mid: (midId: string) => `/channel/mids/${midId}`,
  costPlans: '/channel/cost-plans',
  costPlan: (planCode: string) => `/channel/cost-plans/${planCode}`,
  costVersion: (planCode: string, costVer: string) => `/channel/cost-plans/${planCode}/versions/${costVer}`,
  newCostVersion: (planCode: string) => `/channel/cost-plans/${planCode}/versions/new`,
  /** 对比页复用版本详情路由：路径为对比版本，Query compareWith 为基准版本。 */
  costCompare: (planCode: string, baseCostVer: string, targetCostVer: string) => `/channel/cost-plans/${planCode}/versions/${targetCostVer}?compareWith=${baseCostVer}`,
} as const;

/** 按钮资源（块 92）：渠道维护、账号凭据、MID 维护、成本维护与成本审批。 */
export const channelButtons = {
  channelManage: 'OPERATIONS_CHANNEL_CHANNEL_MANAGE',
  accountCredential: 'OPERATIONS_CHANNEL_ACCOUNT_CREDENTIAL',
  midManage: 'OPERATIONS_CHANNEL_MID_MANAGE',
  costManage: 'OPERATIONS_CHANNEL_COST_MANAGE',
  costApprove: 'OPERATIONS_CHANNEL_COST_APPROVE',
} as const;

/** 当前会话是否拥有某个按钮资源。 */
export function hasButton(resources: readonly { resourceType: string; resourceCode: string }[], code: string): boolean {
  return resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === code);
}

// ---------- 枚举与文案 ----------

export const lifecycleStatuses = ['DRAFT', 'ACTIVE', 'SUSPENDED', 'CLOSED'] as const;
export const capabilityStatuses = ['ENABLED', 'DISABLED'] as const;
export const planStatuses = ['DRAFT', 'ACTIVE', 'INACTIVE'] as const;
export const costVersionStatuses = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'INACTIVE', 'REJECTED'] as const;
export const valueTypes = ['BOOLEAN', 'DECIMAL', 'CODE'] as const;
export const changeTargetTypes = ['CHANNEL', 'CHANNEL_ACCOUNT', 'CHANNEL_MID', 'MID_CAPABILITY', 'CHANNEL_COST_VERSION'] as const;
export const changeTypes = ['CREATE', 'UPDATE', 'STATUS', 'CONFIGURATION', 'REBIND', 'CANCEL'] as const;

export type LifecycleStatus = typeof lifecycleStatuses[number];
export type CapabilityStatus = typeof capabilityStatuses[number];
export type PlanStatus = typeof planStatuses[number];
export type CostVersionStatus = typeof costVersionStatuses[number];
export type ValueType = typeof valueTypes[number];
export type ChangeTargetType = typeof changeTargetTypes[number];
export type CostVersionAction = 'submit' | 'approve' | 'reject' | 'cancel';

export const lifecycleStatusLabels: Record<LifecycleStatus, string> = { DRAFT: '草稿', ACTIVE: '生效', SUSPENDED: '暂停', CLOSED: '已关闭' };
export const capabilityStatusLabels: Record<CapabilityStatus, string> = { ENABLED: '启用', DISABLED: '停用' };
export const planStatusLabels: Record<PlanStatus, string> = { DRAFT: '草稿', ACTIVE: '生效', INACTIVE: '失效' };
export const costVersionStatusLabels: Record<CostVersionStatus, string> = {
  DRAFT: '草稿', PENDING_APPROVAL: '待审批', APPROVED: '已批准', ACTIVE: '生效', INACTIVE: '失效', REJECTED: '已驳回',
};
export const changeTargetLabels: Record<ChangeTargetType, string> = {
  CHANNEL: '渠道', CHANNEL_ACCOUNT: '技术账号', CHANNEL_MID: 'MID', MID_CAPABILITY: '能力组', CHANNEL_COST_VERSION: '成本版本',
};
export const changeTypeLabels: Readonly<Record<string, string>> = {
  CREATE: '创建', UPDATE: '修改', STATUS: '状态变更', CONFIGURATION: '配置写入', REBIND: '改绑账号', CANCEL: '作废',
};
/** 状态色：生效 / 启用为绿，草稿与失效为灰，暂停为金，待审批橙，已批准青，驳回与关闭为红灰。 */
export const channelStatusColors: Readonly<Record<string, string>> = {
  ACTIVE: 'green', ENABLED: 'green', APPROVED: 'cyan', PENDING_APPROVAL: 'orange', SUSPENDED: 'gold', DRAFT: 'default', DISABLED: 'default',
  INACTIVE: 'default', CLOSED: 'default', REJECTED: 'red',
};

// ---------- 读模型 ----------

export interface Channel { channelId: string; channelCode: string; channelName: string; status: LifecycleStatus; createTime: string; updateTime: string }
export interface ChannelSummary { channelId: string; channelCode: string; channelName: string; status: LifecycleStatus; accountCount: number; midCount: number; updateTime: string }
export interface ChannelAccountSummary {
  channelAccountId: string; channelId: string; entityNo: string; accountName: string; status: LifecycleStatus; configured: boolean;
  vaultTokenSuffix: string | null; updateTime: string;
}
export interface ChannelAccount extends ChannelAccountSummary { remark: string | null; boundMidCount: number; createTime: string }
export interface CapabilityDetail { capabilityCode: string; valueType: ValueType; booleanValue: boolean | null; decimalValue: string | null; codeValue: string | null }
export interface MidCapability {
  midCapabilityId: string; midId: string; paymentMethodCode: string; schemeCode: string; countryCode: string; currency: string; mccCode: string;
  status: CapabilityStatus; createTime: string; updateTime: string; details: CapabilityDetail[];
}
export interface ChannelMidSummary {
  midId: string; channelId: string; channelCode: string; channelAccountId: string; accountName: string; externalMidCode: string; entityNo: string;
  status: LifecycleStatus; capabilityGroupCount: number; enabledCapabilityGroupCount: number; activatedAt: string | null; updateTime: string;
}
export interface ChannelMid {
  midId: string; channelId: string; channelAccountId: string; externalMidCode: string; entityNo: string; status: LifecycleStatus;
  activatedAt: string | null; closedAt: string | null; createTime: string; updateTime: string; capabilities: MidCapability[];
}
export interface CapabilityDefinition { capabilityCode: string; valueType: ValueType; allowedCodes: string[] }
export interface ConfigChange {
  changeId: string; targetType: ChangeTargetType; targetId: string; changeType: string; fromStatus: string | null; toStatus: string | null;
  operatorId: string; reason: string | null; createTime: string;
}
export interface CostPlanSummary {
  planId: string; planCode: string; channelId: string; channelCode: string; channelName: string; status: PlanStatus; currentCostVer: string | null;
  currentEffectiveFrom: string | null; pendingApprovalCount: number; updateTime: string;
}
export interface CostVersionSummary {
  /** 版本主键，变更记录以它作为 CHANNEL_COST_VERSION 的对象 ID。 */
  costVerId: string;
  /** 范围数与组成总数：版本摘要（方案详情、对比头部）带出；版本详情不带，为 null。 */
  scopeCount: number | null; componentCount: number | null;
  costVer: string; status: CostVersionStatus; effectiveFrom: string; effectiveTo: string | null; contentHash: string; makerId: string; checkerId: string | null;
}
export interface CostPlan { planCode: string; channelId: string; status: PlanStatus; currentCostVer: string | null; versions: CostVersionSummary[] }
export interface CostComponent {
  componentId?: string; componentCode: string; componentType: string; calculationBase: string; rateValue: string | null; fixedAmount: string | null;
  currency: string | null; minAmount: string | null; maxAmount: string | null; roundingMode: string;
}
export interface CostScope {
  scopeNo: number; midId: string; entityNo: string; channelAgreementRef: string | null; countryCode: string; currency: string; paymentMethodCode: string;
  actionType: string; components: CostComponent[];
}
export interface CostVersion extends CostVersionSummary { planCode: string; scopes: CostScope[] }
export interface FieldDifference { field: string; baseValue: string | null; targetValue: string | null }
export interface ComponentChange { componentCode: string; base: CostComponent; target: CostComponent; differences: FieldDifference[] }
export interface ScopeChange {
  base: CostScope; target: CostScope; differences: FieldDifference[]; addedComponents: CostComponent[]; removedComponents: CostComponent[];
  changedComponents: ComponentChange[];
}
export interface CostVersionDiff {
  base: CostVersionSummary; target: CostVersionSummary; added: CostScope[]; removed: CostScope[]; changed: ScopeChange[]; unchanged: CostScope[];
}

// ---------- 解码（失败只给通用错误，不回显响应） ----------

export const idPattern = /^[1-9][0-9]{0,18}$/;
export const entityNoPattern = /^[A-Z]{2}[0-9]{3}$/;
export const channelCodePattern = /^[A-Z][A-Z0-9_]{1,63}$/;
export const codePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
export const planCodePattern = codePattern;
export const configKeyPattern = /^[A-Za-z][A-Za-z0-9_.-]{0,63}$/;
export const mccPattern = /^[0-9]{4}$/;
export const countryPattern = /^[A-Z]{2}$/;
export const currencyPattern = /^[A-Z]{3}$/;
export const paymentMethodPattern = /^[A-Z0-9][A-Z0-9_.-]{0,63}$/;
/** 费率：最多 8 位整数、10 位小数的普通十进制。 */
export const ratePattern = /^(0|[1-9][0-9]{0,7})(\.[0-9]{1,10})?$/;
/** 金额：主单位普通十进制，最多 8 位小数。 */
export const amountPattern = /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?$/;
/** 能力明细 DECIMAL：允许负号，最多 8 位小数。 */
export const capabilityDecimalPattern = /^-?(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?$/;

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const id = (v: unknown) => typeof v === 'string' && idPattern.test(v);
const text = (v: unknown, max = 512) => typeof v === 'string' && v.length > 0 && v.length <= max;
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const oneOf = (values: readonly string[]) => (v: unknown) => typeof v === 'string' && values.includes(v);
const optional = (check: (v: unknown) => boolean) => (v: unknown) => v === undefined || v === null || check(v);
const count = (v: unknown) => Number.isSafeInteger(v) && Number(v) >= 0;
const array = (v: unknown): v is unknown[] => Array.isArray(v);
const orNull = <T,>(v: T | undefined | null): T | null => v ?? null;
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };
const entityNo = (v: unknown) => typeof v === 'string' && entityNoPattern.test(v);

export function decodeChannel(value: unknown, channelId?: string): Channel {
  const v = value as Channel;
  require(object(v) && id(v.channelId) && (!channelId || v.channelId === channelId) && text(v.channelCode, 64) && text(v.channelName, 255)
    && oneOf(lifecycleStatuses)(v.status) && instant(v.createTime) && instant(v.updateTime));
  return v;
}
export function decodeChannelSummary(value: unknown): ChannelSummary {
  const v = value as ChannelSummary;
  require(object(v) && id(v.channelId) && text(v.channelCode, 64) && text(v.channelName, 255) && oneOf(lifecycleStatuses)(v.status)
    && count(v.accountCount) && count(v.midCount) && instant(v.updateTime));
  return v;
}
export function decodeAccountSummary(value: unknown): ChannelAccountSummary {
  const v = value as ChannelAccountSummary;
  require(object(v) && id(v.channelAccountId) && id(v.channelId) && entityNo(v.entityNo) && text(v.accountName, 128) && oneOf(lifecycleStatuses)(v.status)
    && typeof v.configured === 'boolean' && optional(value => text(value, 16))(v.vaultTokenSuffix) && instant(v.updateTime));
  return { ...v, vaultTokenSuffix: orNull(v.vaultTokenSuffix) };
}
export function decodeAccount(value: unknown, accountId?: string): ChannelAccount {
  const summary = decodeAccountSummary(value); const v = value as ChannelAccount;
  require((!accountId || v.channelAccountId === accountId) && optional(text)(v.remark) && count(v.boundMidCount) && instant(v.createTime));
  return { ...summary, remark: orNull(v.remark), boundMidCount: v.boundMidCount, createTime: v.createTime };
}
function decodeDetail(value: unknown): CapabilityDetail {
  const v = value as CapabilityDetail;
  require(object(v) && text(v.capabilityCode, 128) && oneOf(valueTypes)(v.valueType) && optional(b => typeof b === 'boolean')(v.booleanValue)
    && optional(d => typeof d === 'string' && capabilityDecimalPattern.test(d))(v.decimalValue) && optional(c => text(c, 128))(v.codeValue));
  return { ...v, booleanValue: orNull(v.booleanValue), decimalValue: orNull(v.decimalValue), codeValue: orNull(v.codeValue) };
}
export function decodeCapability(value: unknown): MidCapability {
  const v = value as MidCapability;
  require(object(v) && id(v.midCapabilityId) && id(v.midId) && text(v.paymentMethodCode, 64) && text(v.schemeCode, 64) && text(v.countryCode, 2)
    && text(v.currency, 3) && typeof v.mccCode === 'string' && mccPattern.test(v.mccCode) && oneOf(capabilityStatuses)(v.status)
    && instant(v.createTime) && instant(v.updateTime) && array(v.details));
  return { ...v, details: v.details.map(decodeDetail) };
}
export function decodeMidSummary(value: unknown): ChannelMidSummary {
  const v = value as ChannelMidSummary;
  require(object(v) && id(v.midId) && id(v.channelId) && text(v.channelCode, 64) && id(v.channelAccountId) && text(v.accountName, 128)
    && text(v.externalMidCode, 128) && entityNo(v.entityNo) && oneOf(lifecycleStatuses)(v.status) && count(v.capabilityGroupCount)
    && count(v.enabledCapabilityGroupCount) && optional(instant)(v.activatedAt) && instant(v.updateTime));
  return { ...v, activatedAt: orNull(v.activatedAt) };
}
export function decodeMid(value: unknown, midId?: string): ChannelMid {
  const v = value as ChannelMid;
  require(object(v) && id(v.midId) && (!midId || v.midId === midId) && id(v.channelId) && id(v.channelAccountId) && text(v.externalMidCode, 128)
    && entityNo(v.entityNo) && oneOf(lifecycleStatuses)(v.status) && optional(instant)(v.activatedAt) && optional(instant)(v.closedAt)
    && instant(v.createTime) && instant(v.updateTime) && array(v.capabilities));
  return { ...v, activatedAt: orNull(v.activatedAt), closedAt: orNull(v.closedAt), capabilities: v.capabilities.map(decodeCapability) };
}
export function decodeDefinitions(value: unknown): CapabilityDefinition[] {
  require(object(value) && array(value.definitions));
  return (value as { definitions: unknown[] }).definitions.map(item => {
    const v = item as CapabilityDefinition;
    require(object(v) && text(v.capabilityCode, 128) && oneOf(valueTypes)(v.valueType) && array(v.allowedCodes) && v.allowedCodes.every(c => text(c, 128)));
    return v;
  });
}
export function decodeChange(value: unknown): ConfigChange {
  const v = value as ConfigChange;
  require(object(v) && id(v.changeId) && oneOf(changeTargetTypes)(v.targetType) && id(v.targetId) && text(v.changeType, 64)
    && optional(s => text(s, 64))(v.fromStatus) && optional(s => text(s, 64))(v.toStatus) && id(v.operatorId) && optional(text)(v.reason)
    && instant(v.createTime));
  return { ...v, fromStatus: orNull(v.fromStatus), toStatus: orNull(v.toStatus), reason: orNull(v.reason) };
}
export function decodePlanSummary(value: unknown): CostPlanSummary {
  const v = value as CostPlanSummary;
  require(object(v) && id(v.planId) && text(v.planCode, 64) && id(v.channelId) && text(v.channelCode, 64) && text(v.channelName, 255)
    && oneOf(planStatuses)(v.status) && optional(id)(v.currentCostVer) && optional(instant)(v.currentEffectiveFrom) && count(v.pendingApprovalCount)
    && instant(v.updateTime));
  return { ...v, currentCostVer: orNull(v.currentCostVer), currentEffectiveFrom: orNull(v.currentEffectiveFrom) };
}
function decodeVersionSummary(value: unknown): CostVersionSummary {
  const v = value as CostVersionSummary;
  require(object(v) && id(v.costVerId) && id(v.costVer) && oneOf(costVersionStatuses)(v.status) && instant(v.effectiveFrom)
    && optional(instant)(v.effectiveTo) && typeof v.contentHash === 'string' && /^[0-9a-f]{64}$/.test(v.contentHash) && id(v.makerId)
    && optional(id)(v.checkerId) && optional(count)(v.scopeCount) && optional(count)(v.componentCount));
  return { costVerId: v.costVerId, scopeCount: orNull(v.scopeCount), componentCount: orNull(v.componentCount), costVer: v.costVer, status: v.status, effectiveFrom: v.effectiveFrom, effectiveTo: orNull(v.effectiveTo), contentHash: v.contentHash,
    makerId: v.makerId, checkerId: orNull(v.checkerId) };
}
export function decodePlan(planCode: string) {
  return (value: unknown): CostPlan => {
    const v = value as CostPlan;
    require(object(v) && v.planCode === planCode && id(v.channelId) && oneOf(planStatuses)(v.status) && optional(id)(v.currentCostVer) && array(v.versions));
    return { ...v, currentCostVer: orNull(v.currentCostVer), versions: v.versions.map(decodeVersionSummary) };
  };
}
function decodeComponent(value: unknown): CostComponent {
  const v = value as CostComponent;
  require(object(v) && text(v.componentCode, 64) && text(v.componentType, 64) && text(v.calculationBase, 64) && optional(text)(v.rateValue)
    && optional(text)(v.fixedAmount) && optional(text)(v.currency) && optional(text)(v.minAmount) && optional(text)(v.maxAmount) && text(v.roundingMode, 64));
  return { ...v, rateValue: orNull(v.rateValue), fixedAmount: orNull(v.fixedAmount), currency: orNull(v.currency), minAmount: orNull(v.minAmount),
    maxAmount: orNull(v.maxAmount) };
}
function decodeScope(value: unknown): CostScope {
  const v = value as CostScope;
  require(object(v) && Number.isSafeInteger(v.scopeNo) && id(v.midId) && entityNo(v.entityNo) && optional(text)(v.channelAgreementRef)
    && text(v.countryCode, 2) && text(v.currency, 3) && text(v.paymentMethodCode, 64) && text(v.actionType, 64) && array(v.components));
  return { ...v, channelAgreementRef: orNull(v.channelAgreementRef), components: v.components.map(decodeComponent) };
}
export function decodeCostVersion(planCode: string, costVer?: string) {
  return (value: unknown): CostVersion => {
    const summary = decodeVersionSummary(value); const v = value as CostVersion;
    require(v.planCode === planCode && (!costVer || v.costVer === costVer) && array(v.scopes));
    return { ...summary, planCode, scopes: v.scopes.map(decodeScope) };
  };
}
function decodeDifferences(value: unknown): FieldDifference[] {
  require(array(value));
  return (value as unknown[]).map(item => {
    const v = item as FieldDifference;
    require(object(v) && text(v.field, 64) && optional(s => typeof s === 'string')(v.baseValue) && optional(s => typeof s === 'string')(v.targetValue));
    return { field: v.field, baseValue: orNull(v.baseValue), targetValue: orNull(v.targetValue) };
  });
}
export function decodeVersionDiff(baseCostVer: string, targetCostVer: string) {
  return (value: unknown): CostVersionDiff => {
    const v = value as CostVersionDiff;
    require(object(v) && array(v.added) && array(v.removed) && array(v.changed) && array(v.unchanged));
    const baseVersion = decodeVersionSummary(v.base); const targetVersion = decodeVersionSummary(v.target);
    require(baseVersion.costVer === baseCostVer && targetVersion.costVer === targetCostVer);
    return {
      base: baseVersion, target: targetVersion, added: v.added.map(decodeScope), removed: v.removed.map(decodeScope), unchanged: v.unchanged.map(decodeScope),
      changed: v.changed.map(item => {
        const c = item as ScopeChange;
        require(object(c) && array(c.addedComponents) && array(c.removedComponents) && array(c.changedComponents));
        return {
          base: decodeScope(c.base), target: decodeScope(c.target), differences: decodeDifferences(c.differences),
          addedComponents: c.addedComponents.map(decodeComponent), removedComponents: c.removedComponents.map(decodeComponent),
          changedComponents: c.changedComponents.map(component => {
            const cc = component as ComponentChange;
            require(object(cc) && text(cc.componentCode, 64));
            return { componentCode: cc.componentCode, base: decodeComponent(cc.base), target: decodeComponent(cc.target), differences: decodeDifferences(cc.differences) };
          }),
        };
      }),
    };
  };
}

/** 分页解码：逐条校验元素，分页元数据交给公共分页加载器核对。 */
export function pageDecoder<T>(decode: (value: unknown) => T) {
  return (value: unknown): PagingResponseBody<T> => {
    require(object(value) && array(value.items));
    const page = value as unknown as PagingResponseBody<unknown>;
    return { ...page, items: page.items.map(decode) };
  };
}
export const decodeChannelPage = pageDecoder(decodeChannelSummary);
export const decodeAccountPage = pageDecoder(decodeAccountSummary);
export const decodeMidPage = pageDecoder(decodeMidSummary);
export const decodeChangePage = pageDecoder(decodeChange);
export const decodePlanPage = pageDecoder(decodePlanSummary);

// ---------- 状态按钮可见性 ----------

export interface LifecycleAction { targetStatus: Exclude<LifecycleStatus, 'DRAFT'>; label: string; danger: boolean }
/** 渠道 / 账号 / MID 共用流转：DRAFT → ACTIVE、ACTIVE ⇄ SUSPENDED、非 CLOSED → CLOSED；CLOSED 无操作；无维护权限不显示。 */
export function lifecycleActions(status: LifecycleStatus, canManage: boolean): LifecycleAction[] {
  if (!canManage) return [];
  const close: LifecycleAction = { targetStatus: 'CLOSED', label: '关闭', danger: true };
  switch (status) {
    case 'DRAFT': return [{ targetStatus: 'ACTIVE', label: '激活', danger: false }, close];
    case 'ACTIVE': return [{ targetStatus: 'SUSPENDED', label: '暂停', danger: false }, close];
    case 'SUSPENDED': return [{ targetStatus: 'ACTIVE', label: '恢复', danger: false }, close];
    default: return [];
  }
}
/** 列表行只放主动作（激活 / 暂停 / 恢复），关闭进入详情执行。 */
export function primaryLifecycleAction(status: LifecycleStatus, canManage: boolean): LifecycleAction | null {
  return lifecycleActions(status, canManage).find(action => action.targetStatus !== 'CLOSED') ?? null;
}
/** 已关闭对象不可编辑、配置、改绑或维护能力组。 */
export function editable(status: LifecycleStatus, canManage: boolean): boolean {
  return canManage && status !== 'CLOSED';
}
export function capabilityToggle(capability: MidCapability, midStatus: LifecycleStatus, canManage: boolean): CapabilityStatus | null {
  if (!editable(midStatus, canManage)) return null;
  return capability.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
}

export interface CostVersionPermissions { canManage: boolean; canApprove: boolean }
/** 成本版本按钮：草稿可提交；待审批可批准 / 驳回（审批权限）；未到生效时间的已批准可作废；对比需要基准版本存在。 */
export function costVersionActions(version: Pick<CostVersionSummary, 'status' | 'effectiveFrom'>, permissions: CostVersionPermissions, now: number) {
  const { canManage, canApprove } = permissions;
  return {
    submit: canManage && version.status === 'DRAFT',
    approve: canApprove && version.status === 'PENDING_APPROVAL',
    reject: canApprove && version.status === 'PENDING_APPROVAL',
    cancel: canManage && version.status === 'APPROVED' && Date.parse(version.effectiveFrom) > now,
  };
}
/** 新建版本：方案下已有 DRAFT 或 PENDING_APPROVAL 版本时 Owner 会拒绝，按钮先行隐藏。 */
export function canCreateVersion(plan: CostPlan, canManage: boolean): boolean {
  return canManage && !plan.versions.some(version => version.status === 'DRAFT' || version.status === 'PENDING_APPROVAL');
}
/** 默认对比基准：当前 ACTIVE 版本；本身就是当前版本时取上一个版本号最大的版本。 */
export function defaultCompareBase(plan: CostPlan, costVer: string): string | null {
  if (plan.currentCostVer && plan.currentCostVer !== costVer) return plan.currentCostVer;
  const older = plan.versions.map(v => v.costVer).filter(v => Number(v) < Number(costVer)).sort((a, b) => Number(b) - Number(a));
  return older[0] ?? null;
}

// ---------- 表单校验与请求体 ----------

const blank = (value: string | null | undefined) => { const v = value?.trim(); return v ? v : null; };

export interface ConfigEntry { key: string; value: string }
/** 参数 / 凭据键值行：键格式与唯一、值 1–8192、各 ≤64 项；返回首个问题，无问题为 null。 */
export function configEntryIssues(entries: readonly ConfigEntry[], label: string): string | null {
  if (entries.length > 64) return `${label}最多 64 项`;
  const keys = new Set<string>();
  for (const [index, entry] of entries.entries()) {
    const row = `${label}第 ${index + 1} 行`;
    if (!configKeyPattern.test(entry.key)) return `${row}名称须以字母开头，只含字母、数字、_ . -，最长 64`;
    if (keys.has(entry.key)) return `${row}名称 ${entry.key} 重复`;
    keys.add(entry.key);
    if (entry.value.length === 0 || entry.value.length > 8192) return `${row}值长度须为 1–8192`;
  }
  return null;
}
export function configurationBody(parameters: readonly ConfigEntry[], credentials: readonly ConfigEntry[]) {
  return { parameters: Object.fromEntries(parameters.map(e => [e.key, e.value])), credentials: Object.fromEntries(credentials.map(e => [e.key, e.value])) };
}
export function tokenSuffixLabel(account: Pick<ChannelAccountSummary, 'configured' | 'vaultTokenSuffix'>): string {
  return account.configured ? `Token …${account.vaultTokenSuffix ?? ''}` : '未配置';
}

export interface DetailDraft { capabilityCode: string; booleanValue?: boolean; decimalValue?: string; codeValue?: string }
/** 明细按目录类型只取一个值；未注册代码、空值、DECIMAL 格式与 CODE 越界返回问题。 */
export function capabilityDetailIssues(drafts: readonly DetailDraft[], definitions: readonly CapabilityDefinition[]): string | null {
  if (drafts.length === 0) return '能力明细至少一项';
  if (drafts.length > 64) return '能力明细最多 64 项';
  const seen = new Set<string>();
  for (const draft of drafts) {
    const definition = definitions.find(d => d.capabilityCode === draft.capabilityCode);
    if (!definition) return `能力代码 ${draft.capabilityCode || '（空）'} 未在目录中注册`;
    if (seen.has(draft.capabilityCode)) return `能力代码 ${draft.capabilityCode} 重复`;
    seen.add(draft.capabilityCode);
    if (definition.valueType === 'BOOLEAN' && typeof draft.booleanValue !== 'boolean') return `${draft.capabilityCode} 需要布尔值`;
    if (definition.valueType === 'DECIMAL' && !capabilityDecimalPattern.test(draft.decimalValue?.trim() ?? '')) {
      return `${draft.capabilityCode} 须为最多 8 位小数的十进制`;
    }
    if (definition.valueType === 'CODE' && !definition.allowedCodes.includes(draft.codeValue ?? '')) {
      return `${draft.capabilityCode} 只能取 ${definition.allowedCodes.join(' / ')}`;
    }
  }
  return null;
}
export function capabilityDetailsBody(drafts: readonly DetailDraft[], definitions: readonly CapabilityDefinition[]) {
  return drafts.map(draft => {
    const type = definitions.find(d => d.capabilityCode === draft.capabilityCode)?.valueType;
    return { capabilityCode: draft.capabilityCode, booleanValue: type === 'BOOLEAN' ? draft.booleanValue ?? null : null,
      decimalValue: type === 'DECIMAL' ? blank(draft.decimalValue) : null, codeValue: type === 'CODE' ? blank(draft.codeValue) : null };
  });
}
export function detailDrafts(details: readonly CapabilityDetail[]): DetailDraft[] {
  return details.map(d => ({ capabilityCode: d.capabilityCode, booleanValue: d.booleanValue ?? undefined, decimalValue: d.decimalValue ?? undefined,
    codeValue: d.codeValue ?? undefined }));
}
export function detailValue(detail: Pick<CapabilityDetail, 'valueType' | 'booleanValue' | 'decimalValue' | 'codeValue'>): string {
  if (detail.valueType === 'BOOLEAN') return detail.booleanValue === null ? '—' : String(detail.booleanValue);
  return (detail.valueType === 'DECIMAL' ? detail.decimalValue : detail.codeValue) ?? '—';
}
/** 能力组明细摘要：ACTION.* 显示 ✓ / ✗，金额上下限合并为区间，其余为 代码=值。 */
export function capabilitySummary(details: readonly CapabilityDetail[]): string {
  const actions = details.filter(d => d.capabilityCode.startsWith('ACTION.') && d.valueType === 'BOOLEAN')
    .map(d => `${d.capabilityCode.slice(7)} ${d.booleanValue ? '✓' : '✗'}`);
  const min = details.find(d => d.capabilityCode === 'AMOUNT.MIN')?.decimalValue;
  const max = details.find(d => d.capabilityCode === 'AMOUNT.MAX')?.decimalValue;
  const range = min || max ? [`${min ?? '0'}–${max ?? '∞'}`] : [];
  const others = details.filter(d => !d.capabilityCode.startsWith('ACTION.') && d.capabilityCode !== 'AMOUNT.MIN' && d.capabilityCode !== 'AMOUNT.MAX')
    .map(d => `${d.capabilityCode}=${detailValue(d)}`);
  return [actions.join(' '), ...range, ...others].filter(Boolean).join(' · ') || '—';
}

export interface CapabilityFormValues { paymentMethodCode: string; schemeCode: string; countryCode: string; currency: string; mccCode: string }
/** 六维条件：支付方式精确代码；体系 / 国家 / 币种可为 *；MCC 四位数字。 */
export function capabilityConditionIssues(values: Partial<CapabilityFormValues>): Partial<Record<keyof CapabilityFormValues, string>> {
  const issues: Partial<Record<keyof CapabilityFormValues, string>> = {};
  if (!paymentMethodPattern.test(values.paymentMethodCode ?? '')) issues.paymentMethodCode = '支付方式须为大写目录代码';
  if (!(values.schemeCode === '*' || paymentMethodPattern.test(values.schemeCode ?? ''))) issues.schemeCode = '体系须为目录代码或 *';
  if (!(values.countryCode === '*' || countryPattern.test(values.countryCode ?? ''))) issues.countryCode = '国家须为两位大写代码或 *';
  if (!(values.currency === '*' || currencyPattern.test(values.currency ?? ''))) issues.currency = '币种须为三位大写代码或 *';
  if (!mccPattern.test(values.mccCode ?? '')) issues.mccCode = 'MCC 须为四位数字';
  return issues;
}

export interface ComponentDraft {
  componentCode: string; componentType: string; calculationBase: string; rateValue?: string; fixedAmount?: string; currency?: string; minAmount?: string;
  maxAmount?: string; roundingMode: string;
}
export interface ScopeDraft {
  midId: string; entityNo: string; channelAgreementRef?: string; countryCode: string; currency: string; paymentMethodCode: string; actionType: string;
  components: ComponentDraft[];
}
const scopeKey = (s: Pick<ScopeDraft, 'midId' | 'entityNo' | 'countryCode' | 'currency' | 'paymentMethodCode' | 'actionType'>) =>
  [s.midId, s.entityNo, s.countryCode, s.currency, s.paymentMethodCode, s.actionType].join('/');

/** 版本编制校验：至少一个范围、≤200；每个范围六维格式与唯一、1–20 个组成；组成代码唯一、费率 / 金额十进制、固定费与上下限需币种。 */
export function costVersionIssues(effectiveFrom: string | undefined, scopes: readonly ScopeDraft[]): string[] {
  const issues: string[] = [];
  if (!effectiveFrom || !Number.isFinite(Date.parse(effectiveFrom))) issues.push('生效时间必填');
  if (scopes.length === 0) issues.push('至少添加一个范围');
  if (scopes.length > 200) issues.push('范围最多 200 个');
  const keys = new Set<string>();
  scopes.forEach((scope, index) => {
    const at = `范围 ${index + 1}`;
    if (!idPattern.test(scope.midId ?? '')) issues.push(`${at}：请选择 MID`);
    if (!entityNoPattern.test(scope.entityNo ?? '')) issues.push(`${at}：OE 须为两位大写字母加三位数字`);
    if (!countryPattern.test(scope.countryCode ?? '')) issues.push(`${at}：国家须为两位大写代码`);
    if (!currencyPattern.test(scope.currency ?? '')) issues.push(`${at}：币种须为三位大写代码`);
    if (!codePattern.test(scope.paymentMethodCode ?? '')) issues.push(`${at}：支付方式须为大写代码`);
    if (!codePattern.test(scope.actionType ?? '')) issues.push(`${at}：动作须为大写代码`);
    if (blank(scope.channelAgreementRef) === null) issues.push(`${at}：合同引用必填`);
    if ((scope.channelAgreementRef?.length ?? 0) > 128) issues.push(`${at}：合同引用最长 128`);
    const key = scopeKey(scope);
    if (keys.has(key)) issues.push(`${at}：与前面的范围六维相同`);
    keys.add(key);
    if (!scope.components?.length) issues.push(`${at}：至少添加一个组成`);
    if ((scope.components?.length ?? 0) > 20) issues.push(`${at}：组成最多 20 个`);
    const codes = new Set<string>();
    (scope.components ?? []).forEach((component, componentIndex) => {
      const where = `${at} 组成 ${componentIndex + 1}`;
      if (!codePattern.test(component.componentCode ?? '')) issues.push(`${where}：组成代码须为大写代码`);
      if (codes.has(component.componentCode)) issues.push(`${where}：组成代码 ${component.componentCode} 重复`);
      codes.add(component.componentCode);
      if (!blank(component.componentType)) issues.push(`${where}：类型必填`);
      if (!blank(component.calculationBase)) issues.push(`${where}：计算基数必填`);
      if (!blank(component.roundingMode)) issues.push(`${where}：舍入方式必填`);
      const rate = blank(component.rateValue);
      if (rate !== null && !ratePattern.test(rate)) issues.push(`${where}：费率须为最多 10 位小数的十进制（如 0.0290）`);
      const amounts = [component.fixedAmount, component.minAmount, component.maxAmount].map(blank);
      if (amounts.some(a => a !== null && !amountPattern.test(a))) issues.push(`${where}：金额须为最多 8 位小数的十进制`);
      if (rate === null && amounts[0] === null) issues.push(`${where}：费率与固定费至少填一项`);
      const currency = blank(component.currency);
      if (amounts.some(a => a !== null) && currency === null) issues.push(`${where}：填写金额时需要币种`);
      if (currency !== null && !currencyPattern.test(currency)) issues.push(`${where}：币种须为三位大写代码`);
    });
  });
  return issues;
}
export function costVersionBody(effectiveFrom: string, scopes: readonly ScopeDraft[]) {
  return {
    effectiveFrom,
    scopes: scopes.map((scope, index) => ({
      scopeNo: index + 1, midId: scope.midId, entityNo: scope.entityNo, channelAgreementRef: (scope.channelAgreementRef ?? '').trim(), countryCode: scope.countryCode,
      currency: scope.currency, paymentMethodCode: scope.paymentMethodCode, actionType: scope.actionType,
      components: scope.components.map(c => ({ componentCode: c.componentCode, componentType: c.componentType.trim(), calculationBase: c.calculationBase.trim(),
        rateValue: blank(c.rateValue), fixedAmount: blank(c.fixedAmount), currency: blank(c.currency), minAmount: blank(c.minAmount), maxAmount: blank(c.maxAmount),
        roundingMode: c.roundingMode.trim() })),
    })),
  };
}
/** 已有版本 → 编制草稿（复制为新版本）。 */
export function scopeDrafts(version: Pick<CostVersion, 'scopes'>): ScopeDraft[] {
  return version.scopes.map(scope => ({ midId: scope.midId, entityNo: scope.entityNo, channelAgreementRef: scope.channelAgreementRef ?? undefined,
    countryCode: scope.countryCode, currency: scope.currency, paymentMethodCode: scope.paymentMethodCode, actionType: scope.actionType,
    components: scope.components.map(c => ({ componentCode: c.componentCode, componentType: c.componentType, calculationBase: c.calculationBase,
      rateValue: c.rateValue ?? undefined, fixedAmount: c.fixedAmount ?? undefined, currency: c.currency ?? undefined, minAmount: c.minAmount ?? undefined,
      maxAmount: c.maxAmount ?? undefined, roundingMode: c.roundingMode })) }));
}
export function newComponentDraft(currency = ''): ComponentDraft {
  return { componentCode: '', componentType: '', calculationBase: 'TRANSACTION_AMOUNT', currency: currency || undefined, roundingMode: 'HALF_UP' };
}

// ---------- 展示 ----------

/** 十进制费率字符串 → 百分比，按字符串移位避免浮点误差：0.0290 → 2.90%。 */
export function percent(rate: string | null): string {
  if (!rate || !ratePattern.test(rate)) return rate ?? '—';
  const [integer, fraction = ''] = rate.split('.');
  const padded = fraction.padEnd(2, '0');
  const whole = String(Number(integer + padded.slice(0, 2)));
  const rest = padded.slice(2);
  return `${whole}${rest ? `.${rest}` : ''}%`;
}
export function componentSummary(c: Pick<CostComponent, 'rateValue' | 'fixedAmount' | 'currency' | 'minAmount' | 'maxAmount' | 'roundingMode'>): string {
  const money = (amount: string) => `${amount}${c.currency ? ` ${c.currency}` : ''}`;
  const head = c.rateValue && c.fixedAmount ? `${percent(c.rateValue)} + ${money(c.fixedAmount)}`
    : c.rateValue ? percent(c.rateValue) : c.fixedAmount ? `固定 ${money(c.fixedAmount)}` : '—';
  return [head, c.minAmount ? `下限 ${money(c.minAmount)}` : null, c.maxAmount ? `上限 ${money(c.maxAmount)}` : null, c.roundingMode]
    .filter(Boolean).join(' · ');
}
export function scopeLabel(s: Pick<CostScope, 'midId' | 'entityNo' | 'countryCode' | 'currency' | 'paymentMethodCode' | 'actionType'>, midNames?: Readonly<Record<string, string>>) {
  return [midNames?.[s.midId] ?? s.midId, s.entityNo, s.countryCode, s.currency, s.paymentMethodCode, s.actionType].join(' / ');
}
export function shortHash(hash: string): string {
  return hash.length > 8 ? `${hash.slice(0, 4)}…${hash.slice(-4)}` : hash;
}
export function componentCount(version: Pick<CostVersion, 'scopes'>): number {
  return version.scopes.reduce((sum, scope) => sum + scope.components.length, 0);
}
export function localTime(value: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return value;
  const two = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())} ${two(date.getHours())}:${two(date.getMinutes())}`;
}
/** 本地 datetime 输入 → UTC ISO（Z 结尾、去掉 .000）。 */
export function utcInstant(local: string): string {
  const date = new Date(local);
  return Number.isFinite(date.getTime()) ? date.toISOString().replace(/\.000Z$/, 'Z') : local;
}

// ---------- 版本对比 ----------

export type DiffChange = 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED';
export const diffChangeLabels: Record<DiffChange, string> = { ADDED: '新增', REMOVED: '删除', CHANGED: '修改', UNCHANGED: '未变' };
export const diffChangeColors: Record<DiffChange, string> = { ADDED: 'green', REMOVED: 'red', CHANGED: 'orange', UNCHANGED: 'default' };
export interface DiffRow {
  key: string; change: DiffChange; scope: CostScope; componentCode: string; baseSummary: string | null; targetSummary: string | null;
  differences: FieldDifference[];
}
/** 展开为 范围 × 组成 行：修改范围中未变的组成标为未变，范围级差异（合同引用）挂在该范围每一行。 */
export function diffRows(diff: CostVersionDiff): DiffRow[] {
  const rows: DiffRow[] = [];
  const whole = (scope: CostScope, change: DiffChange) => scope.components.forEach(c => rows.push({
    key: `${change}:${scopeKey(scope)}:${c.componentCode}`, change, scope, componentCode: c.componentCode,
    baseSummary: change === 'ADDED' ? null : componentSummary(c), targetSummary: change === 'REMOVED' ? null : componentSummary(c), differences: [] }));
  diff.unchanged.forEach(scope => whole(scope, 'UNCHANGED'));
  for (const changed of diff.changed) {
    const key = scopeKey(changed.target);
    const touched = new Set([...changed.addedComponents, ...changed.removedComponents].map(c => c.componentCode)
      .concat(changed.changedComponents.map(c => c.componentCode)));
    changed.target.components.filter(c => !touched.has(c.componentCode)).forEach(c => rows.push({ key: `UNCHANGED:${key}:${c.componentCode}`,
      change: changed.differences.length ? 'CHANGED' : 'UNCHANGED', scope: changed.target, componentCode: c.componentCode, baseSummary: componentSummary(c),
      targetSummary: componentSummary(c), differences: changed.differences }));
    changed.changedComponents.forEach(c => rows.push({ key: `CHANGED:${key}:${c.componentCode}`, change: 'CHANGED', scope: changed.target,
      componentCode: c.componentCode, baseSummary: componentSummary(c.base), targetSummary: componentSummary(c.target),
      differences: [...changed.differences, ...c.differences] }));
    changed.addedComponents.forEach(c => rows.push({ key: `ADDED:${key}:${c.componentCode}`, change: 'ADDED', scope: changed.target,
      componentCode: c.componentCode, baseSummary: null, targetSummary: componentSummary(c), differences: changed.differences }));
    changed.removedComponents.forEach(c => rows.push({ key: `REMOVED:${key}:${c.componentCode}`, change: 'REMOVED', scope: changed.target,
      componentCode: c.componentCode, baseSummary: componentSummary(c), targetSummary: null, differences: changed.differences }));
  }
  diff.added.forEach(scope => whole(scope, 'ADDED'));
  diff.removed.forEach(scope => whole(scope, 'REMOVED'));
  const order: Record<DiffChange, number> = { CHANGED: 0, ADDED: 1, REMOVED: 2, UNCHANGED: 3 };
  return rows.sort((a, b) => scopeKey(a.scope).localeCompare(scopeKey(b.scope)) || order[a.change] - order[b.change]
    || a.componentCode.localeCompare(b.componentCode));
}
export interface DiffSummary { baseScopes: number; targetScopes: number; addedScopes: number; removedScopes: number; baseComponents: number;
  targetComponents: number; addedComponents: number; removedComponents: number; changedComponents: number }
/** 头部统计：范围数与组成数的前后对比及新增 / 删除 / 修改计数。 */
export function diffSummary(diff: CostVersionDiff): DiffSummary {
  const components = (scopes: readonly CostScope[]) => scopes.reduce((sum, s) => sum + s.components.length, 0);
  const baseComponents = components([...diff.removed, ...diff.unchanged, ...diff.changed.map(c => c.base)]);
  const targetComponents = components([...diff.added, ...diff.unchanged, ...diff.changed.map(c => c.target)]);
  return {
    baseScopes: diff.removed.length + diff.unchanged.length + diff.changed.length,
    targetScopes: diff.added.length + diff.unchanged.length + diff.changed.length,
    addedScopes: diff.added.length, removedScopes: diff.removed.length, baseComponents, targetComponents,
    addedComponents: components(diff.added) + diff.changed.reduce((sum, c) => sum + c.addedComponents.length, 0),
    removedComponents: components(diff.removed) + diff.changed.reduce((sum, c) => sum + c.removedComponents.length, 0),
    changedComponents: diff.changed.reduce((sum, c) => sum + c.changedComponents.length, 0),
  };
}

// ---------- 错误与命令 ----------

/** BFF 原因未透传时的公共说明（Foundation BUSINESS_CHECK_FAILED 默认消息）。 */
const genericBusinessDetail = '业务校验未通过';
/**
 * 渠道写命令的错误说明：400 BUSINESS_CHECK_FAILED 与 404 直接显示 Owner 透传的 detail（例如「Platform 不存在同编码接入方 NI」）；
 * 409 为并发冲突。其余返回 null，由调用方走通用通知。
 */
export function channelErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null;
  if (error.status === 409) return '数据已被其他操作修改，请重新加载后再试。';
  const owner = error.detail && error.detail !== genericBusinessDetail ? error.detail : null;
  if (error.status === 404) return owner ?? '目标不存在。';
  if (error.status !== 400 || error.code !== 'BUSINESS_CHECK_FAILED') return null;
  return owner ? `操作被拒绝：${owner}` : '操作被拒绝：当前状态或关联对象不满足规则（BUSINESS_CHECK_FAILED）';
}

export type CommandMethod = 'POST' | 'PUT';
/** 无 Body 命令（提交审批）的 body 为 undefined：不发送请求体。 */
export function channelCommand(path: string, method: CommandMethod, body: unknown, previous?: ConfigurationCommand): ConfigurationCommand {
  return configurationCommand(path, method, body ?? null, previous);
}
export function sendChannelCommand<T>(command: ConfigurationCommand, decode: (value: unknown) => T, withoutBody = false): Promise<T> {
  return session.request<T>(command.path, { method: command.method, ...(withoutBody ? {} : { body: command.body }),
    headers: { ...(withoutBody ? {} : { 'Content-Type': 'application/json' }), 'Idempotency-Key': command.key } }, decode);
}
/** scopes[0].components[1].rateValue → ['scopes', 0, 'components', 1, 'rateValue']。 */
export function fieldPath(field: string): (string | number)[] {
  return field.split('.').flatMap(part => {
    const match = /^([A-Za-z][A-Za-z0-9]*)\[(\d+)]$/.exec(part);
    return match ? [match[1], Number(match[2])] : [part];
  });
}
export function isId(value: string | undefined): value is string {
  return typeof value === 'string' && idPattern.test(value);
}
export function isPlanCode(value: string | undefined): value is string {
  return typeof value === 'string' && planCodePattern.test(value);
}
/** 只把非空筛选值放进 Query。 */
export function listFilters(values: Readonly<Record<string, string | undefined>>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).map(([k, v]) => [k, v?.trim() ?? '']).filter(([, v]) => v !== ''));
}
