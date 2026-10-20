import { ApiError } from '../api/session.ts';
import type { PagingFilters } from '../api/paging.ts';
import type { PagingResponseBody } from '../api/types.ts';
import {
  agreementStatuses, agreementTypes, componentSummary, currencyPattern, decodePrice, decodeVersion, entityNoPattern, idPattern, versionStatuses,
} from './commercial.ts';
import type { AgreementStatus, AgreementType, Price, PriceComponent, TermVersion, VersionStatus } from './commercial.ts';

// ---------- 路径与页面路由（第二切片 I2） ----------

/** I2 新增的 BFF 只读路径。 */
export const consolePaths = {
  agreements: '/commercial-admin/v1/agreements',
  plans: '/commercial-admin/v1/commercial-terms/plans',
  versions: '/commercial-admin/v1/commercial-terms/versions',
  versionDiff: (baseVersionId: string, targetVersionId: string) => `/commercial-admin/v1/commercial-terms/versions/${baseVersionId}/diff/${targetVersionId}`,
  products: '/commercial-admin/v1/products',
  capabilities: '/commercial-admin/v1/products/capabilities',
  feeEvents: '/commercial-admin/v1/products/fee-events',
  businessChanges: '/commercial-admin/v1/business-changes',
} as const;

/** 聚合根类型（变更历史筛选）。 */
export const aggregateRootTypes = ['AGREEMENT', 'COMMERCIAL_TERM_PLAN', 'PAYMENT_METHOD_PROFILE'] as const;
export type AggregateRootType = typeof aggregateRootTypes[number];
export const aggregateRootTypeLabels: Record<AggregateRootType, string> = {
  AGREEMENT: '合同', COMMERCIAL_TERM_PLAN: '条款方案', PAYMENT_METHOD_PROFILE: '定价画像',
};

/** 页面路由：版本对比与带筛选的变更历史。 */
export const consoleRoutes = {
  compare: (baseVersionId: string, targetVersionId: string) => `/commercial/terms/compare/${baseVersionId}/${targetVersionId}`,
  history: (type: AggregateRootType, id: string) => `/commercial/history?aggregateRootType=${type}&aggregateRootId=${id}`,
} as const;

// ---------- 读模型 ----------

export interface AgreementListItem {
  agreementId: string; agreementNo: string; agreementName: string; agreementType: AgreementType; status: AgreementStatus; businessMode: string | null;
  fundMode: string | null; clientId: string | null; localPartnerId: string | null; entityNo: string | null; effectiveFrom: string; effectiveTo: string | null;
  signedTime: string | null; activatedTime: string | null; changeVersion: number; updateTime: string;
}
export interface TermPlanListItem {
  planId: string; planCode: string; termType: string; agreementId: string | null; entityNo: string | null; clientId: string | null;
  localPartnerId: string | null; status: string; changeVersion: string; activeVersionId: string | null; activeVersionNo: string | null;
  activeEffectiveFrom: string | null; scheduledVersionId: string | null; scheduledVersionNo: string | null; scheduledEffectiveFrom: string | null;
  createTime: string; updateTime: string;
}
export interface TermVersionListItem {
  versionId: string; planId: string; planCode: string; termType: string; agreementId: string | null; entityNo: string | null; clientId: string | null;
  localPartnerId: string | null; versionNo: string; status: VersionStatus; effectiveFrom: string; effectiveTo: string | null; roundingMode: string;
  pricingModel: string; feeTaxTreatment: string; makerAccountId: string | null; checkerAccountId: string | null; submittedTime: string | null;
  priceCount: number; changeVersion: string; updateTime: string;
}
export interface CatalogProduct { productId: string; productCode: string; productName: string; businessModeScope: string; baseProduct: boolean; status: string }
export interface CatalogCapability {
  productCapabilityId: string; productCode: string; capabilityCode: string; capabilityName: string; capabilityKind: string; paymentMethodCode: string | null;
  dimensionCode: string | null; dimensionValue: string | null; status: string;
}
export interface CatalogFeeEvent { productFeeEventId: string; productCode: string; feeEventCode: string; termType: string; billingFactType: string }
export type ChangeComparison = 'FIRST_VERSION' | 'COMPARED' | 'NOT_COMPARED';
export interface BusinessChangeEntry {
  historyId: string; recordType: string; recordId: string; versionNo: number; operationType: string; createBy: string; createTime: string;
  meta: { triggerType: string; triggerId: string; reasonCode: string } | null; data: Record<string, unknown>; comparison: ChangeComparison;
  previousVersionNo: number | null; previousData: Record<string, unknown> | null; changedFields: string[] | null;
}
export interface FieldDifference { field: string; baseValue: string | null; targetValue: string | null }
export interface PriceChange { entityNo: string; currency: string; pricingProfileCode: string; base: Price; target: Price; differences: FieldDifference[] }
export interface VersionDiff {
  base: TermVersion; target: TermVersion; versionDifferences: FieldDifference[]; added: Price[]; removed: Price[]; changed: PriceChange[]; unchanged: Price[];
}

// ---------- 解码 ----------

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const array = (v: unknown): v is unknown[] => Array.isArray(v);
const id = (v: unknown) => typeof v === 'string' && idPattern.test(v);
const text = (v: unknown, max = 256) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const optional = (check: (v: unknown) => boolean) => (v: unknown) => v === undefined || v === null || check(v);
const oneOf = (values: readonly string[]) => (v: unknown) => typeof v === 'string' && values.includes(v);
const entity = (v: unknown) => typeof v === 'string' && entityNoPattern.test(v);
const orNull = <T,>(v: T | undefined | null): T | null => v ?? null;
/** 解码失败只给通用错误，不回显响应内容。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

/** 分页外壳：items 逐条解码，page / size / total 原样保留。 */
export function decodePage<T>(decodeItem: (value: unknown) => T) {
  return (value: unknown): PagingResponseBody<T> => {
    const v = value as PagingResponseBody<unknown>;
    require(object(v) && array(v.items) && Number.isSafeInteger(v.page) && Number.isSafeInteger(v.size) && Number.isSafeInteger(v.total) && v.total >= 0);
    return { items: v.items.map(decodeItem), page: v.page, size: v.size, total: v.total };
  };
}

export function decodeAgreementListItem(value: unknown): AgreementListItem {
  const v = value as AgreementListItem;
  require(object(v) && id(v.agreementId) && text(v.agreementNo, 64) && text(v.agreementName, 255) && oneOf(agreementTypes)(v.agreementType)
    && oneOf(agreementStatuses)(v.status) && optional(id)(v.clientId) && optional(id)(v.localPartnerId) && optional(entity)(v.entityNo)
    && instant(v.effectiveFrom) && optional(instant)(v.effectiveTo) && Number.isSafeInteger(v.changeVersion) && instant(v.updateTime));
  return { ...v, businessMode: orNull(v.businessMode), fundMode: orNull(v.fundMode), clientId: orNull(v.clientId), localPartnerId: orNull(v.localPartnerId),
    entityNo: orNull(v.entityNo), effectiveTo: orNull(v.effectiveTo), signedTime: orNull(v.signedTime), activatedTime: orNull(v.activatedTime) };
}

export function decodeTermPlanListItem(value: unknown): TermPlanListItem {
  const v = value as TermPlanListItem;
  require(object(v) && id(v.planId) && text(v.planCode, 64) && text(v.termType, 64) && optional(id)(v.agreementId) && text(v.status, 32)
    && id(v.changeVersion) && optional(id)(v.activeVersionId) && optional(id)(v.scheduledVersionId) && instant(v.createTime));
  return { ...v, agreementId: orNull(v.agreementId), entityNo: orNull(v.entityNo), clientId: orNull(v.clientId), localPartnerId: orNull(v.localPartnerId),
    activeVersionId: orNull(v.activeVersionId), activeVersionNo: orNull(v.activeVersionNo), activeEffectiveFrom: orNull(v.activeEffectiveFrom),
    scheduledVersionId: orNull(v.scheduledVersionId), scheduledVersionNo: orNull(v.scheduledVersionNo),
    scheduledEffectiveFrom: orNull(v.scheduledEffectiveFrom) };
}

export function decodeTermVersionListItem(value: unknown): TermVersionListItem {
  const v = value as TermVersionListItem;
  require(object(v) && id(v.versionId) && id(v.planId) && text(v.planCode, 64) && text(v.termType, 64) && optional(id)(v.agreementId)
    && text(v.versionNo, 19) && oneOf(versionStatuses)(v.status) && instant(v.effectiveFrom) && optional(instant)(v.effectiveTo)
    && optional(id)(v.makerAccountId) && optional(id)(v.checkerAccountId) && optional(instant)(v.submittedTime) && Number.isInteger(v.priceCount)
    && v.priceCount >= 0 && id(v.changeVersion));
  return { ...v, agreementId: orNull(v.agreementId), entityNo: orNull(v.entityNo), clientId: orNull(v.clientId), localPartnerId: orNull(v.localPartnerId),
    effectiveTo: orNull(v.effectiveTo), makerAccountId: orNull(v.makerAccountId), checkerAccountId: orNull(v.checkerAccountId),
    submittedTime: orNull(v.submittedTime) };
}

export const decodeAgreementPage = decodePage(decodeAgreementListItem);
export const decodeTermPlanPage = decodePage(decodeTermPlanListItem);
export const decodeTermVersionPage = decodePage(decodeTermVersionListItem);

export function decodeProducts(value: unknown): CatalogProduct[] {
  const v = value as { products: CatalogProduct[] };
  require(object(v) && array(v.products) && v.products.every(p => object(p) && id(p.productId) && text(p.productCode, 64) && text(p.productName, 128)
    && typeof p.baseProduct === 'boolean' && text(p.status, 32)));
  return v.products;
}

export function decodeCapabilities(value: unknown): CatalogCapability[] {
  const v = value as { capabilities: CatalogCapability[] };
  require(object(v) && array(v.capabilities) && v.capabilities.every(c => object(c) && id(c.productCapabilityId) && text(c.productCode, 64)
    && text(c.capabilityCode, 64) && text(c.capabilityName, 128) && text(c.status, 32)));
  return v.capabilities.map(c => ({ ...c, paymentMethodCode: orNull(c.paymentMethodCode), dimensionCode: orNull(c.dimensionCode),
    dimensionValue: orNull(c.dimensionValue) }));
}

export function decodeFeeEvents(value: unknown): CatalogFeeEvent[] {
  const v = value as { feeEvents: CatalogFeeEvent[] };
  require(object(v) && array(v.feeEvents) && v.feeEvents.every(e => object(e) && id(e.productFeeEventId) && text(e.productCode, 64)
    && text(e.feeEventCode, 64) && text(e.termType, 64) && text(e.billingFactType, 64)));
  return v.feeEvents;
}

export function decodeBusinessChange(value: unknown): BusinessChangeEntry {
  const v = value as BusinessChangeEntry;
  require(object(v) && id(v.historyId) && text(v.recordType, 64) && typeof v.recordId === 'string' && Number.isSafeInteger(v.versionNo)
    && text(v.operationType, 16) && typeof v.createBy === 'string' && instant(v.createTime) && object(v.data)
    && oneOf(['FIRST_VERSION', 'COMPARED', 'NOT_COMPARED'])(v.comparison) && optional(object)(v.meta) && optional(object)(v.previousData)
    && (v.changedFields === null || (array(v.changedFields) && v.changedFields.every(f => typeof f === 'string')))
    && (v.comparison === 'NOT_COMPARED') === (v.changedFields === null));
  return { ...v, meta: orNull(v.meta), previousVersionNo: orNull(v.previousVersionNo), previousData: orNull(v.previousData) };
}

export const decodeBusinessChangePage = decodePage(decodeBusinessChange);

function decodeDifference(value: unknown): FieldDifference {
  const v = value as FieldDifference;
  require(object(v) && text(v.field, 256) && optional((x: unknown) => typeof x === 'string')(v.baseValue)
    && optional((x: unknown) => typeof x === 'string')(v.targetValue));
  return { field: v.field, baseValue: orNull(v.baseValue), targetValue: orNull(v.targetValue) };
}

/** 版本对比：回执的两个版本必须是请求的版本且属于同一方案。 */
export function decodeVersionDiff(baseVersionId: string, targetVersionId: string) {
  return (value: unknown): VersionDiff => {
    const v = value as VersionDiff;
    require(object(v) && array(v.versionDifferences) && array(v.added) && array(v.removed) && array(v.changed) && array(v.unchanged));
    const base = decodeVersion(baseVersionId)(v.base);
    const target = decodeVersion(targetVersionId)(v.target);
    require(base.planId === target.planId);
    const changed = v.changed.map(item => {
      require(object(item) && entity(item.entityNo) && typeof item.currency === 'string' && currencyPattern.test(item.currency)
        && text(item.pricingProfileCode, 64) && array(item.differences) && item.differences.length > 0);
      return { entityNo: item.entityNo, currency: item.currency, pricingProfileCode: item.pricingProfileCode, base: decodePrice(item.base),
        target: decodePrice(item.target), differences: item.differences.map(decodeDifference) };
    });
    return { base, target, versionDifferences: v.versionDifferences.map(decodeDifference), added: v.added.map(decodePrice),
      removed: v.removed.map(decodePrice), changed, unchanged: v.unchanged.map(decodePrice) };
  };
}

// ---------- 筛选 ----------

/** 表单值 → 列表筛选：去空白、丢弃空值（BFF 拒绝空 Query），数组去重。 */
export function listFilters(values: Readonly<Record<string, string | readonly string[] | undefined | null>>): PagingFilters {
  const filters: Record<string, string | readonly string[]> = {};
  for (const [key, value] of Object.entries(values)) {
    if (Array.isArray(value)) {
      const items = [...new Set(value.map(item => item.trim()).filter(Boolean))];
      if (items.length) filters[key] = items;
    } else if (typeof value === 'string' && value.trim()) {
      filters[key] = value.trim();
    }
  }
  return filters;
}

/** 合同号精确换成合同 ID：前缀查询结果里取编号完全相同的一条；没有返回 null。 */
export function exactAgreement(items: readonly AgreementListItem[], agreementNo: string): AgreementListItem | null {
  return items.find(item => item.agreementNo === agreementNo.trim()) ?? null;
}

// ---------- 目录下拉 ----------

export interface CodeOption { value: string; label: string }

/** 可订阅产品：启用且非基础产品（基础产品随合同默认具备，不在订阅中维护）。 */
export function subscribableProductOptions(products: readonly CatalogProduct[]): CodeOption[] {
  return products.filter(p => p.status === 'ACTIVE' && !p.baseProduct).map(p => ({ value: p.productCode, label: `${p.productCode} · ${p.productName}` }));
}

/** 可开通项下拉：启用的项；给出产品代码时只列这些产品下的项。 */
export function capabilityOptions(capabilities: readonly CatalogCapability[], productCodes?: readonly string[]): CodeOption[] {
  return capabilities.filter(c => c.status === 'ACTIVE' && (!productCodes || productCodes.includes(c.productCode)))
    .map(c => ({ value: c.capabilityCode, label: `${c.capabilityCode} · ${c.capabilityName}` }));
}

/** 计费事件下拉：事件代码去重并保持目录顺序（同一事件可在多个产品下登记）。 */
export function feeEventOptions(events: readonly CatalogFeeEvent[]): CodeOption[] {
  const codes = [...new Set(events.map(e => e.feeEventCode))];
  return codes.map(code => ({ value: code, label: `${code} · ${events.filter(e => e.feeEventCode === code).map(e => e.productCode).join(' / ')}` }));
}

// ---------- 版本对比展示 ----------

export type DiffChange = 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED';
export const diffChangeLabels: Record<DiffChange, string> = { ADDED: '新增', REMOVED: '删除', CHANGED: '修改', UNCHANGED: '未变' };
export const diffChangeColors: Record<DiffChange, string> = { ADDED: 'green', REMOVED: 'red', CHANGED: 'orange', UNCHANGED: 'default' };

/** 对比表的一行：OE × 币种 × 画像 × 事件 × 组件；两侧组件摘要与变化类型。 */
export interface DiffRow {
  key: string; entityNo: string; currency: string; pricingProfileCode: string; triggerCode: string; componentCode: string; componentType: string;
  baseSummary: string | null; targetSummary: string | null; change: DiffChange; fields: FieldDifference[];
}

function componentRows(price: Price, side: 'base' | 'target', change: DiffChange): DiffRow[] {
  return price.components.map(component => ({
    key: `${price.entityNo}|${price.currency}|${price.pricingProfileCode}|${component.componentCode}`, entityNo: price.entityNo, currency: price.currency,
    pricingProfileCode: price.pricingProfileCode, triggerCode: component.triggerCode, componentCode: component.componentCode,
    componentType: component.componentType, baseSummary: side === 'base' ? componentSummary(component) : null,
    targetSummary: side === 'target' ? componentSummary(component) : null, change, fields: [],
  }));
}

/** 修改的价格按组件展开：组件字段有差异或整体增删时为修改 / 新增 / 删除，否则未变；价格级差异挂在第一行。 */
function changedRows(change: PriceChange): DiffRow[] {
  const baseByCode = new Map(change.base.components.map(c => [c.componentCode, c] as const));
  const targetByCode = new Map(change.target.components.map(c => [c.componentCode, c] as const));
  const codes = [...new Set([...targetByCode.keys(), ...baseByCode.keys()])];
  const priceLevel = change.differences.filter(d => !d.field.startsWith('components['));
  return codes.map((code, index) => {
    const base: PriceComponent | undefined = baseByCode.get(code); const target: PriceComponent | undefined = targetByCode.get(code);
    const fields = change.differences.filter(d => d.field === `components[${code}]` || d.field.startsWith(`components[${code}].`));
    const kind: DiffChange = !base ? 'ADDED' : !target ? 'REMOVED' : fields.length ? 'CHANGED' : 'UNCHANGED';
    const shown = (target ?? base)!;
    return {
      key: `${change.entityNo}|${change.currency}|${change.pricingProfileCode}|${code}`, entityNo: change.entityNo, currency: change.currency,
      pricingProfileCode: change.pricingProfileCode, triggerCode: shown.triggerCode, componentCode: code, componentType: shown.componentType,
      baseSummary: base ? componentSummary(base) : null, targetSummary: target ? componentSummary(target) : null,
      change: index === 0 && priceLevel.length && kind === 'UNCHANGED' ? 'CHANGED' : kind, fields: index === 0 ? [...priceLevel, ...fields] : fields,
    };
  });
}

/** 对比结果展开为组件行，按 OE、币种、画像、事件排序。 */
export function diffRows(diff: VersionDiff): DiffRow[] {
  const rows = [
    ...diff.changed.flatMap(changedRows),
    ...diff.added.flatMap(price => componentRows(price, 'target', 'ADDED')),
    ...diff.removed.flatMap(price => componentRows(price, 'base', 'REMOVED')),
    ...diff.unchanged.flatMap(price => componentRows(price, 'target', 'UNCHANGED')).map(row => ({ ...row, baseSummary: row.targetSummary })),
  ];
  return rows.sort((a, b) => a.entityNo.localeCompare(b.entityNo) || a.currency.localeCompare(b.currency)
    || a.pricingProfileCode.localeCompare(b.pricingProfileCode) || a.triggerCode.localeCompare(b.triggerCode)
    || a.componentCode.localeCompare(b.componentCode));
}

/** 对比摘要：修改 / 新增 / 删除 / 未变的价格条数。 */
export function diffCounts(diff: VersionDiff): Record<DiffChange, number> {
  return { CHANGED: diff.changed.length, ADDED: diff.added.length, REMOVED: diff.removed.length, UNCHANGED: diff.unchanged.length };
}

// ---------- 变更历史展示 ----------

/** 快照值的显示文本：null 为 —，数组与对象为紧凑 JSON。 */
export function snapshotValue(value: unknown): string {
  if (value === null || value === undefined) return '—';
  return typeof value === 'string' ? value : JSON.stringify(value);
}

/** 一条快照的一行摘要：变化字段的前后值（最多 3 个），首版列出字段名，未比较说明原因。 */
export function changeSummary(entry: BusinessChangeEntry): string {
  if (entry.comparison === 'NOT_COMPARED') return '上一快照超出回溯范围，未比较';
  const fields = entry.changedFields ?? [];
  if (entry.comparison === 'FIRST_VERSION') return `首个快照：${fields.slice(0, 6).join('、')}${fields.length > 6 ? ' …' : ''}`;
  if (!fields.length) return '字段无变化';
  const parts = fields.slice(0, 3).map(field => `${field} ${snapshotValue(entry.previousData?.[field])} → ${snapshotValue(entry.data[field])}`);
  return `${parts.join('；')}${fields.length > 3 ? ` 等 ${fields.length} 项` : ''}`;
}

// ---------- 最近使用的 Client ----------

const recentClientsKey = 'operations.commercial.recentClients';
const recentClientsLimit = 10;
export interface KeyValueStorage { getItem(key: string): string | null; setItem(key: string, value: string): void }

/** 本机最近使用的 Client ID（新的在前，最多 10 个）；存储不可用或内容损坏时为空。 */
export function recentClients(storage: KeyValueStorage | undefined): string[] {
  try {
    const parsed: unknown = JSON.parse(storage?.getItem(recentClientsKey) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string' && idPattern.test(v)).slice(0, recentClientsLimit) : [];
  } catch {
    return [];
  }
}

/** 记住一个 Client ID：移到最前、去重、截断到 10 个；非法 ID 不记录，存储失败静默忽略。 */
export function rememberClient(storage: KeyValueStorage | undefined, clientId: string): string[] {
  const value = clientId.trim();
  if (!idPattern.test(value)) return recentClients(storage);
  const next = [value, ...recentClients(storage).filter(item => item !== value)].slice(0, recentClientsLimit);
  try { storage?.setItem(recentClientsKey, JSON.stringify(next)); } catch { /* 隐私模式等无法写入时只影响便利功能 */ }
  return next;
}

/** 浏览器 localStorage；不可访问时为 undefined。 */
export function browserStorage(): KeyValueStorage | undefined {
  try { return globalThis.localStorage ?? undefined; } catch { return undefined; }
}
