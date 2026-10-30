import { enumLabel, tOr, translator } from '../i18n/index.ts';
import type {
  Agreement, AgreementCapability, CapabilityExecutionStatus, CapabilityPrerequisites, PrerequisiteCheck, PrerequisiteCheckResult,
} from './commercial.ts';
import { joinList } from './commercial.ts';
import type { BusinessChangeEntry } from './commercialConsole.ts';

/** 非组件代码在调用时通过 translator 取文案。 */
const t = translator('commercial');

/** 可「暂停」的执行状态：除手动暂停外的全部执行状态。 */
const suspendableStatuses: readonly string[] = ['PENDING_PREREQUISITES', 'ENABLED', 'SUSPENDED_BY_PREREQUISITE'];

export interface CapabilityGateActions {
  /** 行可展开「还差什么」（展开即核对） */
  expandable: boolean;
  /** 行操作「暂停」 */
  suspend: boolean;
  /** 行操作「恢复」 */
  resume: boolean;
}

/** 前置门禁只对 CLIENT 合同的成员（status=ACTIVE）开通项生效。 */
export function isGateMember(agreement: Pick<Agreement, 'agreementType'>, capability: Pick<AgreementCapability, 'status'>): boolean {
  return agreement.agreementType === 'CLIENT' && capability.status === 'ACTIVE';
}

/**
 * 开通项行上的前置门禁操作（2026-10-28 用户确认的权限）
 * <li>canView：有合同查看权限即可展开「还差什么」与「检查前置」（核对只做系统本会自动做的迁移）。</li>
 * <li>canOperate：合同处于可维护开通项的状态且有维护权限（agreementActions.capabilities）；暂停与恢复依赖它，只读用户不显示。</li>
 */
export function capabilityGateActions(agreement: Pick<Agreement, 'agreementType'>, capability: Pick<AgreementCapability, 'status' | 'executionStatus'>,
  canView: boolean, canOperate: boolean): CapabilityGateActions {
  const member = isGateMember(agreement, capability);
  const execution = capability.executionStatus ?? '';
  return {
    expandable: member && canView,
    suspend: member && canOperate && suspendableStatuses.includes(execution),
    resume: member && canOperate && execution === 'SUSPENDED_MANUALLY',
  };
}

/**
 * 仍然有效的核对结果（按开通项 ID）：结果的合同版本不低于页面当前版本；之后发生过写入（暂停、恢复、编辑）则视为过期，返回空。
 */
export function freshPrerequisites(result: PrerequisiteCheckResult | null, changeVersion: number): Map<string, CapabilityPrerequisites> {
  if (!result || result.changeVersion < changeVersion) return new Map();
  return new Map(result.capabilities.map(item => [item.agreementCapabilityId, item]));
}

/** 行内显示的执行状态：有效核对结果优先（核对可能刚迁移状态），否则取合同详情。 */
export function effectiveExecutionStatus(capability: Pick<AgreementCapability, 'agreementCapabilityId' | 'executionStatus'>,
  fresh: ReadonlyMap<string, CapabilityPrerequisites>): CapabilityExecutionStatus | null {
  const checked = fresh.get(capability.agreementCapabilityId);
  return checked ? checked.executionStatus : capability.executionStatus;
}

/** 产品区头的数量摘要：开通项总数、已开通、等前置、暂停（前置失效与手动两种合计）。 */
export interface SectionCounts { total: number; enabled: number; pending: number; suspended: number }

export function sectionCounts(capabilities: readonly AgreementCapability[], fresh: ReadonlyMap<string, CapabilityPrerequisites>): SectionCounts {
  const counts: SectionCounts = { total: capabilities.length, enabled: 0, pending: 0, suspended: 0 };
  for (const capability of capabilities) {
    const execution = capability.status === 'ACTIVE' ? effectiveExecutionStatus(capability, fresh) : null;
    if (execution === 'ENABLED') counts.enabled++;
    else if (execution === 'PENDING_PREREQUISITES') counts.pending++;
    else if (execution === 'SUSPENDED_BY_PREREQUISITE' || execution === 'SUSPENDED_MANUALLY') counts.suspended++;
  }
  return counts;
}

/** 前置项一行的标记：✔ 满足、✘ 不满足、! 暂时无法核对、– 不适用或等待价格。 */
export type PrerequisiteMark = 'satisfied' | 'unsatisfied' | 'unknown' | 'skipped';
const marks: Readonly<Record<string, PrerequisiteMark>> = {
  SATISFIED: 'satisfied', UNSATISFIED: 'unsatisfied', UNKNOWN: 'unknown', SKIPPED: 'skipped',
};

/** 前置行的跳转：Client 详情（资格、经营分类）、OE 详情（资格）、本合同定价、按 OE 筛选的 MID 列表（渠道身份）、重新检查（检查失败）。 */
export type PrerequisiteLink = { kind: 'client'; clientId: string } | { kind: 'entity'; entityNo: string } | { kind: 'pricing' }
  | { kind: 'mids'; entityNo: string } | { kind: 'recheck' };

/** 渠道身份不满足、需要到 MID 页补配置的原因（REQ-02）。 */
const midReasons: readonly string[] = ['MID_NOT_FOUND', 'PAYMENT_METHOD_NOT_SUPPORTED', 'MCC_NOT_COVERED', 'CURRENCY_NOT_SUPPORTED', 'PAYMENT_ACTION_NOT_SUPPORTED'];

export interface PrerequisiteLine {
  code: string; mark: PrerequisiteMark;
  /** 前置项名称，如「资格」 */
  title: string;
  /** 结论名称，如「不满足」（读屏与悬停提示用） */
  result: string;
  /** 说明：满足时为满足说明，其余为原因（ICU 文案 + 参数，未知原因码显示原值） */
  detail: string;
  link: PrerequisiteLink | null;
}

/** 文案参数的页面上下文：合同 Client、开通项 OE 与合同状态，用于补齐 commercial 没有给出的参数。 */
export interface PrerequisiteContext { clientId: string | null; entityNo: string; agreementStatus: string }

/**
 * ICU 参数：先取 commercial 的 params（subjectType + subjectId 指明主体），缺失时用页面上下文补齐；
 * prerequisite 为前置项代码（同一原因码在不同前置项下文案不同时用 select 区分，如 CHECK_UNAVAILABLE），未给出时为 other。
 */
function reasonValues(params: Readonly<Record<string, string>>, context: PrerequisiteContext, prerequisiteCode?: string) {
  const subject = (type: string) => params.subjectType === type && params.subjectId ? params.subjectId : null;
  return {
    clientId: params.clientId ?? subject('CLIENT') ?? context.clientId ?? '—',
    entityNo: params.entityNo ?? subject('OPERATING_ENTITY') ?? context.entityNo,
    termType: enumLabel(t, 'enum.termType', params.termType ?? 'CLIENT_PRICING'),
    agreementStatus: enumLabel(t, 'enum.agreementStatus', params.agreementStatus ?? context.agreementStatus),
    // 价格未覆盖 / 渠道不就绪的币种（commercial 逗号分隔）；没有时为 none，文案走不带币种的分支
    currencies: params.currencies ? joinList(params.currencies.split(',')) : 'none',
    paymentMethodCode: params.paymentMethodCode ?? '—',
    mccCode: params.mccCode ?? '—',
    prerequisite: prerequisiteCode ?? 'other',
  };
}

/**
 * 原因说明：commercial.enum.CapabilityPrerequisiteReason.<CODE> 的 ICU 文案，参数固定为 clientId、entityNo、termType、agreementStatus、currencies、
 * paymentMethodCode、mccCode、prerequisite；未知原因码显示原值。
 */
export function prerequisiteReason(reasonCode: string, params: Readonly<Record<string, string>>, context: PrerequisiteContext,
  prerequisiteCode?: string): string {
  return tOr(t, 'enum.CapabilityPrerequisiteReason.' + reasonCode, reasonCode, reasonValues(params, context, prerequisiteCode));
}

/** 短原因（执行状态徽标下方小字、卡住原因汇总）：prerequisite.shortReason.<CODE>，未知码显示原值。 */
export function prerequisiteShortReason(reasonCode: string, params: Readonly<Record<string, string>>, context: PrerequisiteContext,
  prerequisiteCode?: string): string {
  return tOr(t, 'prerequisite.shortReason.' + reasonCode, reasonCode, reasonValues(params, context, prerequisiteCode));
}

/**
 * 不满足时的跳转：资格与 Client 经营分类去 Client / OE 详情，价格类去本合同定价，渠道身份去按 OE 筛选的 MID 列表；检查失败可重新检查。
 * 满足时只有渠道身份部分币种缺 MID（CHANNEL_IDENTITY_PARTIAL）给 MID 列表；等待价格（SKIPPED）等其余结论没有跳转。
 */
function lineLink(check: PrerequisiteCheck, context: PrerequisiteContext): PrerequisiteLink | null {
  if (check.result === 'UNKNOWN') return { kind: 'recheck' };
  const values = reasonValues(check.params, context);
  // 部分币种缺 MID：仍为满足，但提醒去补 MID
  if (check.result === 'SATISFIED' && check.reasonCode === 'CHANNEL_IDENTITY_PARTIAL') return { kind: 'mids', entityNo: values.entityNo };
  if (check.result !== 'UNSATISFIED') return null;
  if ((check.reasonCode === 'CLIENT_NOT_QUALIFIED' || check.reasonCode === 'CLIENT_MCC_MISSING') && values.clientId !== '—') {
    return { kind: 'client', clientId: values.clientId };
  }
  if (check.reasonCode && midReasons.includes(check.reasonCode)) return { kind: 'mids', entityNo: values.entityNo };
  if (check.reasonCode === 'OPERATING_ENTITY_NOT_QUALIFIED') return { kind: 'entity', entityNo: values.entityNo };
  if (check.reasonCode === 'TERM_VERSION_NOT_ACTIVE' || check.reasonCode === 'PRICE_NOT_COVERED') return { kind: 'pricing' };
  return null;
}

/** 逐项结论转为展示行（保持 commercial 给出的顺序）。 */
export function prerequisiteLines(checks: readonly PrerequisiteCheck[], context: PrerequisiteContext): PrerequisiteLine[] {
  return checks.map(check => ({
    code: check.code,
    mark: marks[check.result] ?? 'unknown',
    title: enumLabel(t, 'enum.CapabilityPrerequisiteCode', check.code),
    result: enumLabel(t, 'enum.CapabilityPrerequisiteResult', check.result),
    detail: check.reasonCode ? prerequisiteReason(check.reasonCode, check.params, context, check.code)
      : tOr(t, 'prerequisite.satisfied.' + check.code, enumLabel(t, 'enum.CapabilityPrerequisiteResult', check.result), reasonValues(check.params, context)),
    link: lineLink(check, context),
  }));
}

/** 展开区头部摘要的计数：满足（含按满足处理的不适用 / 等待价格）、未满足、暂时无法核对。 */
export function lineCounts(lines: readonly PrerequisiteLine[]) {
  return {
    satisfied: lines.filter(line => line.mark === 'satisfied' || line.mark === 'skipped').length,
    unsatisfied: lines.filter(line => line.mark === 'unsatisfied').length,
    unknown: lines.filter(line => line.mark === 'unknown').length,
  };
}

/** 一个没有开通的成员开通项为什么卡住；needsPricing 表示原因在本合同定价（「去定价」）。 */
export interface BlockedCapability { agreementCapabilityId: string; capabilityCode: string; text: string; needsPricing: boolean }

/**
 * 「下一步 · 开通项生效」卡片的卡住原因：CLIENT 合同中执行状态不是已开通的成员开通项，按合同顺序逐条
 * <li>等前置 / 前置失效暂停：有效核对结果中第一条不满足（其次检查失败）的短原因；没有结果时只给状态。</li>
 * <li>手动暂停：固定说明需要手动恢复。</li>
 */
export function blockedCapabilities(agreement: Pick<Agreement, 'agreementType' | 'clientId' | 'status' | 'capabilities'>,
  fresh: ReadonlyMap<string, CapabilityPrerequisites>): BlockedCapability[] {
  if (agreement.agreementType !== 'CLIENT') return [];
  const result: BlockedCapability[] = [];
  for (const capability of agreement.capabilities) {
    const execution = capability.status === 'ACTIVE' ? effectiveExecutionStatus(capability, fresh) : null;
    if (!execution || execution === 'ENABLED') continue;
    const checks = fresh.get(capability.agreementCapabilityId)?.checks ?? [];
    const blocking = checks.find(check => check.result === 'UNSATISFIED') ?? checks.find(check => check.result === 'UNKNOWN');
    const context = { clientId: agreement.clientId, entityNo: capability.entityNo, agreementStatus: agreement.status };
    const reason = blocking?.reasonCode ? prerequisiteShortReason(blocking.reasonCode, blocking.params, context, blocking.code) : null;
    const code = capability.capabilityCode;
    const text = execution === 'SUSPENDED_MANUALLY' ? t('nextStep.blocked.manual', { code })
      : reason ? t(execution === 'PENDING_PREREQUISITES' ? 'nextStep.blocked.pendingReason' : 'nextStep.blocked.suspendedReason', { code, reason })
        : t('nextStep.blocked.status', { code, status: enumLabel(t, 'enum.AgreementCapabilityExecutionStatus', execution) });
    result.push({ agreementCapabilityId: capability.agreementCapabilityId, capabilityCode: code, text,
      needsPricing: blocking?.reasonCode === 'TERM_VERSION_NOT_ACTIVE' || blocking?.reasonCode === 'PRICE_NOT_COVERED' });
  }
  return result;
}

/** 卡住原因连成一句（分句连接）。 */
export const blockedSummary = (blocked: readonly BlockedCapability[]) => joinList(blocked.map(item => item.text), 'clauses');

/** 进入页面时是否自动核对一次：CLIENT 合同存在等前置或前置失效暂停的成员开通项（卡住原因需要核对结果）。 */
export function needsInitialCheck(agreement: Pick<Agreement, 'agreementType' | 'capabilities'>): boolean {
  return agreement.agreementType === 'CLIENT' && agreement.capabilities.some(capability => capability.status === 'ACTIVE'
    && (capability.executionStatus === 'PENDING_PREREQUISITES' || capability.executionStatus === 'SUSPENDED_BY_PREREQUISITE'));
}

/** 最近一次手动暂停：操作人（ID 与 BFF 补全的显示名）、时间与原因（取自开通项 BCR 快照 data.suspensionReason）。 */
export interface SuspensionRecord { actorId: string; actorLabel: string | null; time: string; reason: string | null }

/**
 * 从合同变更历史（最新在前）找到该开通项最近一次「手动暂停」快照（reasonCode=AGREEMENT_CAPABILITY_SUSPENDED）；没有时返回 null。
 */
export function lastSuspension(entries: readonly Pick<BusinessChangeEntry, 'recordType' | 'recordId' | 'createBy' | 'createByLabel' | 'createTime' | 'meta' | 'data'>[],
  agreementCapabilityId: string): SuspensionRecord | null {
  const entry = entries.find(item => item.recordType === 'AGREEMENT_CAPABILITY' && item.recordId === agreementCapabilityId
    && item.meta?.reasonCode === 'AGREEMENT_CAPABILITY_SUSPENDED');
  if (!entry) return null;
  const reason = entry.data.suspensionReason;
  return { actorId: entry.createBy, actorLabel: entry.createByLabel, time: entry.createTime, reason: typeof reason === 'string' && reason.trim() ? reason : null };
}
