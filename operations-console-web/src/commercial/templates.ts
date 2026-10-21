import { ApiError } from '../api/session.ts';
import { decodePrice, decodeVersion, decodeWorkspace, entityNoPattern, idPattern, versionStatuses } from './commercial.ts';
import type { Price, TermVersion, TermWorkspace, VersionAction, VersionStatus } from './commercial.ts';

// ---------- 标准价目（平台模板：按 OE、命名、多套；版本化、独立权限复核；不参与运行时取价） ----------

/**
 * 模板专用 BFF 路由：写与复核按模板权限授权，与合同价目路由互不通用。
 * 模板版本的读取复用合同价目的版本读（commercialPaths.version），按 ownerType 区分。
 */
export const templatePaths = {
  templates: '/commercial-admin/v1/commercial-terms/templates',
  versions: (planId: string) => `/commercial-admin/v1/commercial-terms/templates/${planId}/versions`,
  prices: (versionId: string) => `/commercial-admin/v1/commercial-terms/template-versions/${versionId}/prices`,
  versionAction: (versionId: string, action: VersionAction) => `/commercial-admin/v1/commercial-terms/template-versions/${versionId}/${action}`,
} as const;

/** 页面路由：列表与版本工作区同一菜单，工作区用查询参数定位版本。 */
export const templateRoutes = {
  list: '/commercial/templates',
  version: (versionId: string) => `/commercial/templates?versionId=${versionId}`,
} as const;

/** 模板可用的条款类型：Client 类（Partner 条款作用在 Client 交易上，没有标准价目）。 */
export const templateTermTypes = ['CLIENT_PRICING', 'PAYOUT_FEE', 'FX_SPREAD', 'DISPUTE_FEE', 'PLATFORM_FEE'] as const;

export interface TemplatePlan {
  planId: string; planCode: string; templateName: string; termType: string; entityNo: string; status: string; changeVersion: string;
  activeVersionId: string | null; activeVersionNo: string | null; latestVersionId: string | null; latestVersionNo: string | null;
  latestVersionStatus: VersionStatus | null;
}

/** 模板版本工作区：版本 + 模板方案摘要（BFF template 字段，缺失时为 null）+ 价格；模板没有合同，价格只属于模板的 OE。 */
export interface TemplateWorkspace { version: TermVersion; template: TemplatePlan | null; prices: Price[] }

/** 版本读的两种结果：合同价目（带合同范围）或标准价目（agreement 为 null）。 */
export type VersionWorkspace = { kind: 'AGREEMENT'; workspace: TermWorkspace } | { kind: 'TEMPLATE'; workspace: TemplateWorkspace };

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const array = (v: unknown): v is unknown[] => Array.isArray(v);
const id = (v: unknown) => typeof v === 'string' && idPattern.test(v);
const text = (v: unknown, max = 256) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;
const optional = (check: (v: unknown) => boolean) => (v: unknown) => v === undefined || v === null || check(v);
const orNull = <T,>(v: T | undefined | null): T | null => v ?? null;
const versionNo = (v: unknown) => text(v, 19) || Number.isSafeInteger(v);
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

export function decodeTemplatePlan(value: unknown): TemplatePlan {
  const v = value as TemplatePlan;
  require(object(v) && id(v.planId) && text(v.planCode, 64) && text(v.termType, 64) && typeof v.entityNo === 'string' && entityNoPattern.test(v.entityNo)
    && text(v.status, 32) && (id(v.changeVersion) || Number.isSafeInteger(v.changeVersion)) && optional(text)(v.templateName)
    && optional(id)(v.activeVersionId) && optional(versionNo)(v.activeVersionNo) && optional(id)(v.latestVersionId) && optional(versionNo)(v.latestVersionNo)
    && optional((s: unknown) => typeof s === 'string' && (versionStatuses as readonly string[]).includes(s))(v.latestVersionStatus));
  const no = (value: unknown) => value === null || value === undefined ? null : String(value);
  return { planId: v.planId, planCode: v.planCode, templateName: v.templateName ?? v.planCode, termType: v.termType, entityNo: v.entityNo, status: v.status,
    changeVersion: String(v.changeVersion), activeVersionId: orNull(v.activeVersionId), activeVersionNo: no(v.activeVersionNo),
    latestVersionId: orNull(v.latestVersionId), latestVersionNo: no(v.latestVersionNo), latestVersionStatus: orNull(v.latestVersionStatus) };
}

/** 模板列表：兼容分页外壳 {items} 与 {templates} 两种形态。 */
export function decodeTemplateList(value: unknown): TemplatePlan[] {
  const v = value as Record<string, unknown>;
  require(object(v) && (array(v.items) || array(v.templates)));
  const items = (array(v.items) ? v.items : v.templates) as unknown[];
  return items.map(decodeTemplatePlan);
}

/**
 * 版本读（GET /commercial-terms/versions/{id}）：version.ownerType=PLATFORM_TEMPLATE 时为标准价目——没有合同、agreement 为 null，
 * template 为模板方案摘要（方案 ID 必须与版本一致），价格只属于模板 OE；其余按合同价目工作区严格解码（版本与合同一致、价格属于合同 OE）。
 * 旧 BFF 无 ownerType 时以「无合同 + agreement 为 null」识别模板。
 */
export function decodeVersionWorkspace(versionId: string) {
  return (value: unknown): VersionWorkspace => {
    const v = value as Record<string, unknown>;
    require(object(v) && array(v.prices));
    const version = decodeVersion(versionId)(v.version);
    const isTemplate = version.ownerType === 'PLATFORM_TEMPLATE' || (version.ownerType === null && version.agreementId === null && !v.agreement);
    if (!isTemplate) return { kind: 'AGREEMENT', workspace: decodeWorkspace(versionId)(value) };
    require(version.agreementId === null && (v.agreement === null || v.agreement === undefined));
    const template = v.template === null || v.template === undefined ? null : decodeTemplatePlan(v.template);
    const prices = (v.prices as unknown[]).map(decodePrice);
    require(!template || (template.planId === version.planId && prices.every(price => price.entityNo === template.entityNo)));
    return { kind: 'TEMPLATE', workspace: { version, template, prices } };
  };
}

export interface TemplateFormValues { planCode: string; templateName: string; termType: string; entityNo: string }

/** 新建模板请求：编号 / OE 去空白，OE 转大写。 */
export function templateBody(values: TemplateFormValues) {
  return { planCode: values.planCode.trim(), templateName: values.templateName.trim(), termType: values.termType, entityNo: values.entityNo.trim().toUpperCase() };
}

/** 模板显示名：标准价 · AE001 v3；版本号缺失时只显示名称。 */
export function templateLabel(plan: Pick<TemplatePlan, 'templateName'>, version?: string | null): string {
  return version ? `${plan.templateName} v${version}` : plan.templateName;
}

/** 新建版本 / 补齐时可选的模板：同 OE、同条款类型、已有版本的模板。 */
export function templateChoices(templates: readonly TemplatePlan[], entityNos: readonly string[], termType: string): TemplatePlan[] {
  return templates.filter(item => entityNos.includes(item.entityNo) && item.termType === termType && (item.activeVersionId || item.latestVersionId));
}
