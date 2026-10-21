import { acceptanceMethods, offsetInstant, versionActions } from './commercial.ts';
import type { AcceptanceMethod, AgreementParty, VersionStatus } from './commercial.ts';

// ---------- 版本流程：内部复核 → 客户签署 → 排期 → 生效 ----------

export interface TermVersionPermissions { canManage: boolean; canApprove: boolean; canSign: boolean }

/**
 * 版本按钮（在 versionActions 之上加签署步骤）：
 * <li>合同方案：APPROVED 且非历史免签时登记客户签署；排期要求 CLIENT_SIGNED，或 APPROVED 且历史免签。</li>
 * <li>标准价目（template=true）：无签署，APPROVED 即可排期；DRAFT 不提供「从标准价目补齐」。</li>
 */
export function termVersionActions(version: { status: VersionStatus; signatureExempt: boolean }, permissions: TermVersionPermissions, template = false) {
  const base = versionActions(version.status, permissions);
  const signed = version.status === 'CLIENT_SIGNED' || (version.status === 'APPROVED' && (template || version.signatureExempt));
  return {
    ...base,
    sign: !template && permissions.canSign && version.status === 'APPROVED' && !version.signatureExempt,
    schedule: permissions.canManage && signed,
    fillFromTemplate: !template && base.editPrices,
  };
}

/** 签署列 / 状态：历史免签、客户已签署、待客户签署（已批准未签）；其余为空。 */
export type SignatureState = 'EXEMPT' | 'SIGNED' | 'PENDING' | null;
export const signatureStateLabels: Record<Exclude<SignatureState, null>, string> = { EXEMPT: '历史免签', SIGNED: '客户已签署', PENDING: '待客户签署' };
export const signatureStateColors: Record<Exclude<SignatureState, null>, string> = { EXEMPT: 'default', SIGNED: 'cyan', PENDING: 'orange' };

export function signatureState(item: { status: VersionStatus; signatureExempt: boolean; hasAcceptance?: boolean; acceptance?: unknown }): SignatureState {
  if (item.signatureExempt) return 'EXEMPT';
  if (item.hasAcceptance || item.acceptance || item.status === 'CLIENT_SIGNED') return 'SIGNED';
  return item.status === 'APPROVED' ? 'PENDING' : null;
}

/** 列表破标列：数量 > 0 为红色「破标 N」，0 为「—」，旧 BFF 无字段为 null。 */
export function belowStandardText(count: number | null): string | null {
  if (count === null) return null;
  return count > 0 ? `破标 ${count}` : '—';
}

// ---------- 新建版本：复制上一版本 / 基于标准价目 / 空白 ----------

export const generationModes = ['COPY', 'TEMPLATE', 'BLANK'] as const;
export type GenerationMode = typeof generationModes[number];
export const generationModeLabels: Record<GenerationMode, string> = { COPY: '复制上一版本', TEMPLATE: '基于标准价目', BLANK: '空白' };

/** 可选生成方式：Partner 条款与标准价目自身不能基于模板生成；没有来源版本时不能复制。 */
export function availableGenerationModes(options: { partner: boolean; template: boolean; hasPrevious: boolean }): GenerationMode[] {
  return generationModes.filter(mode => (mode !== 'TEMPLATE' || (!options.partner && !options.template)) && (mode !== 'COPY' || options.hasPrevious));
}

/** 默认生成方式：有上一版本则复制，否则 Client 条款基于标准价目，其余空白。 */
export function defaultGenerationMode(modes: readonly GenerationMode[]): GenerationMode {
  return modes.includes('COPY') ? 'COPY' : modes.includes('TEMPLATE') ? 'TEMPLATE' : 'BLANK';
}

export interface CreateVersionValues {
  mode: GenerationMode; copyFromVersionId?: string; sourceTemplateVersionId?: string; agreementId?: string; pricingModel?: string; feeTaxTreatment?: string;
}

/** 新建版本请求：三种方式互斥（复制带 copyFromVersionId，基于模板带 sourceTemplateVersionId，空白两者为空）；口径空值为沿用来源。 */
export function createVersionBody(expectedChangeVersion: string, values: CreateVersionValues, partner: boolean) {
  const trim = (value: string | undefined) => value?.trim() || null;
  return {
    expectedChangeVersion, copyFromVersionId: values.mode === 'COPY' ? trim(values.copyFromVersionId) : null,
    sourceTemplateVersionId: values.mode === 'TEMPLATE' ? trim(values.sourceTemplateVersionId) : null,
    agreementId: partner ? trim(values.agreementId) : null, pricingModel: values.pricingModel || null, feeTaxTreatment: values.feeTaxTreatment || null,
  };
}

/** 空白版本必须给出定价模型与含税口径；复制与基于模板可沿用来源。 */
export const basisRequired = (mode: GenerationMode) => mode === 'BLANK';

/** 从标准价目补齐请求。 */
export function fillFromTemplateBody(expectedChangeVersion: string, templateVersionId: string) {
  return { expectedChangeVersion, templateVersionId: templateVersionId.trim() };
}

// ---------- 客户签署登记 ----------

export interface SignatureFormValues {
  partyId: string; acceptanceMethod: AcceptanceMethod; acceptedAt: string; acceptanceReference: string; fileId: string; contentHash: string;
}

/** 签署方：Client 方案取合同 CLIENT 参与方，Partner 方案取 LOCAL_PARTNER 参与方；同一 Party 只列一次。 */
export function signatureParties(parties: readonly AgreementParty[], partner: boolean): AgreementParty[] {
  const role = partner ? 'LOCAL_PARTNER' : 'CLIENT';
  const seen = new Set<string>();
  return parties.filter(party => party.partyRole === role && !seen.has(party.partyId) && seen.add(party.partyId));
}

/** 签署登记请求：附件文件 ID + SHA-256（小写）+ 方式 + 时刻（带偏移）+ 编号。 */
export function signatureBody(expectedChangeVersion: string, values: SignatureFormValues) {
  return { expectedChangeVersion, partyId: values.partyId.trim(), acceptanceMethod: values.acceptanceMethod, acceptedAt: offsetInstant(values.acceptedAt),
    acceptanceReference: values.acceptanceReference.trim(), fileId: values.fileId.trim(), contentHash: values.contentHash.trim().toLowerCase() };
}

/** 签署表单前置检查：返回问题列表（字段规则之外的整体校验，最终以 commercial 为准）。 */
export function signatureIssues(values: Partial<SignatureFormValues>, parties: readonly AgreementParty[]): string[] {
  const issues: string[] = [];
  if (!values.partyId || !parties.some(party => party.partyId === values.partyId)) issues.push('签署方必须是合同的签约参与方');
  if (!values.acceptanceMethod || !(acceptanceMethods as readonly string[]).includes(values.acceptanceMethod)) issues.push('请选择签署方式');
  if (!values.acceptedAt || !Number.isFinite(Date.parse(values.acceptedAt))) issues.push('请选择签署时刻');
  else if (Date.parse(values.acceptedAt) > Date.now() + 60_000) issues.push('签署时刻不能晚于现在');
  if (!values.acceptanceReference?.trim() || !/^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/.test(values.acceptanceReference.trim())) issues.push('签署编号格式不正确');
  if (!values.fileId?.trim() || !/^[1-9][0-9]{0,18}$/.test(values.fileId.trim())) issues.push('价格附件文件 ID 必须是正十进制整数');
  if (!values.contentHash?.trim() || !/^[0-9a-fA-F]{64}$/.test(values.contentHash.trim())) issues.push('SHA-256 必须是 64 位十六进制');
  return issues;
}
