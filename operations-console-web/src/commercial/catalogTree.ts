import { enumLabel, tOr, translator } from '../i18n/index.ts';
import type { CatalogCapability, CatalogFeeEvent, CatalogProduct } from './commercialConsole.ts';

const t = translator('commercial');

// ---------- 目录页标签 ----------

/** 目录页条款类型标签：enum.termType 之外补齐目录里登记的出款、外汇、争议与平台费类型（enum.catalogTermType），未知值返回原代码。 */
export function catalogTermTypeLabel(code: string): string {
  return tOr(t, 'enum.termType.' + code, enumLabel(t, 'enum.catalogTermType', code));
}

/** 可开通项种类标签（enum.capabilityKind），未知值返回原代码。 */
export function capabilityKindLabel(code: string): string {
  return enumLabel(t, 'enum.capabilityKind', code);
}

/** 产品适用业务模式范围标签。 */
export const businessModeScopeLabels: Readonly<Record<string, string>> = { BOTH: 'MOR / PSP', MOR_ONLY: 'MOR', PSP_ONLY: 'PSP' };

/** 页面已知、目录可能尚未登记事件的条款类型；筛选项里始终保留，用于暴露缺口。 */
const knownTermTypes = ['PARTNER_BUY_RATE'] as const;

/** 取标签，未知值返回原代码。 */
export function catalogLabel(labels: Readonly<Record<string, string>>, code: string): string {
  return labels[code] ?? code;
}

// ---------- 分组模型 ----------

/** 合并后的计费事件行：同产品同事件代码一行，条款类型按目录出现顺序，计费事实类型去重；feeEvents 为合并前的各条登记（挂接开通项按登记维护）。 */
export interface CatalogEventRow {
  key: string; productCode: string; feeEventCode: string; termTypes: string[]; billingFactTypes: string[]; feeEvents: CatalogFeeEvent[];
}

/** 产品节点：该产品下的可开通项与（筛选后的）合并事件行；totalEventCount 为不筛选时的合并行数。 */
export interface CatalogProductNode {
  product: CatalogProduct; capabilities: CatalogCapability[]; events: CatalogEventRow[]; eventCount: number; totalEventCount: number; gap: boolean;
}

/** 条款类型筛选项：eventCount 为含该类型的合并事件行数，0 也保留。 */
export interface CatalogTermTypeOption { termType: string; label: string; eventCount: number }

/** 目录缺口：非基础产品有可开通项但没有计费事件。 */
export interface CatalogGap { productCode: string; productName: string; capabilityCount: number }

/** 按产品 × 事件代码合并计费事件登记，保持目录顺序。 */
export function mergeFeeEvents(feeEvents: readonly CatalogFeeEvent[]): CatalogEventRow[] {
  const rows = new Map<string, CatalogEventRow>();
  for (const event of feeEvents) {
    const key = `${event.productCode}:${event.feeEventCode}`;
    const row = rows.get(key) ?? { key, productCode: event.productCode, feeEventCode: event.feeEventCode, termTypes: [], billingFactTypes: [], feeEvents: [] };
    rows.set(key, row);
    row.feeEvents.push(event);
    if (!row.termTypes.includes(event.termType)) row.termTypes.push(event.termType);
    if (!row.billingFactTypes.includes(event.billingFactType)) row.billingFactTypes.push(event.billingFactType);
  }
  return [...rows.values()];
}

/** 是否缺口：非基础产品有可开通项，但目录里没有任何计费事件。 */
function isGap(product: CatalogProduct, capabilityCount: number, eventCount: number): boolean {
  return !product.baseProduct && capabilityCount > 0 && eventCount === 0;
}

/**
 * 按产品分组目录：每个产品一项，按接口返回顺序。
 * <li>termType 非空时事件只留含该类型的行，产品只留有命中事件的；可开通项不受筛选影响。</li>
 * <li>缺口与 totalEventCount 始终按全部目录计算，跨产品同名事件不合并。</li>
 */
export function groupCatalog(products: readonly CatalogProduct[], capabilities: readonly CatalogCapability[], feeEvents: readonly CatalogFeeEvent[],
  termType?: string): CatalogProductNode[] {
  const rows = mergeFeeEvents(feeEvents);
  const nodes = products.map(product => {
    const productCapabilities = capabilities.filter(item => item.productCode === product.productCode);
    const productRows = rows.filter(row => row.productCode === product.productCode);
    const events = termType ? productRows.filter(row => row.termTypes.includes(termType)) : productRows;
    return { product, capabilities: productCapabilities, events, eventCount: events.length, totalEventCount: productRows.length,
      gap: isGap(product, productCapabilities.length, productRows.length) };
  });
  return termType ? nodes.filter(node => node.eventCount > 0) : nodes;
}

/** 条款类型筛选项：目录中出现的类型（按出现顺序）加页面已知类型，各带合并后的事件行数。 */
export function catalogTermTypeOptions(feeEvents: readonly CatalogFeeEvent[]): CatalogTermTypeOption[] {
  const rows = mergeFeeEvents(feeEvents);
  const termTypes = [...new Set([...feeEvents.map(event => event.termType), ...knownTermTypes])];
  return termTypes.map(termType => ({ termType, label: catalogTermTypeLabel(termType),
    eventCount: rows.filter(row => row.termTypes.includes(termType)).length }));
}

/** 目录缺口：非基础产品有可开通项但没有计费事件，按产品顺序。 */
export function catalogGaps(products: readonly CatalogProduct[], capabilities: readonly CatalogCapability[],
  feeEvents: readonly CatalogFeeEvent[]): CatalogGap[] {
  return groupCatalog(products, capabilities, feeEvents).filter(node => node.gap)
    .map(node => ({ productCode: node.product.productCode, productName: node.product.productName, capabilityCount: node.capabilities.length }));
}

/** 顶部汇总：产品数、可开通项数与合并后的计费事件行数。 */
export function catalogSummary(nodes: readonly CatalogProductNode[]) {
  return { products: nodes.length, capabilities: nodes.reduce((sum, node) => sum + node.capabilities.length, 0),
    events: nodes.reduce((sum, node) => sum + node.eventCount, 0) };
}

// ---------- 展开规则与分组标题 ----------

/** 分组键：产品行与两组平级分组各自独立折叠。 */
export const catalogKeys = {
  product: (productCode: string) => `product:${productCode}`,
  capabilities: (productCode: string) => `capabilities:${productCode}`,
  events: (productCode: string) => `events:${productCode}`,
} as const;

/** 产品默认展开：有任一子项或有缺口；按全部目录判断，筛选不改变默认值。 */
export function productExpandedByDefault(node: CatalogProductNode): boolean {
  return node.capabilities.length > 0 || node.totalEventCount > 0 || node.gap;
}

/** 分组默认展开：有数据展开，数量为 0 收起。 */
export function groupExpandedByDefault(count: number): boolean {
  return count > 0;
}

/** 分组标题旁的说明；warning 为黄色缺口提示。 */
export interface GroupNote { text: string; warning: boolean }

/** 可开通项为空时的原因：基础产品默认具备，非基础产品暂无。 */
export function capabilityGroupNote(node: CatalogProductNode): GroupNote | null {
  if (node.capabilities.length > 0) return null;
  return { text: node.product.baseProduct ? t('catalog.note.baseProductDefault') : t('catalog.note.noCapabilities'), warning: false };
}

/** 计费事件为空时的原因：基础产品无事件费用；非基础产品有可开通项为缺口，否则暂无。 */
export function eventGroupNote(node: CatalogProductNode): GroupNote | null {
  if (node.events.length > 0) return null;
  if (node.product.baseProduct) return { text: t('catalog.note.noEventFees'), warning: false };
  if (node.capabilities.length > 0) return { text: t('catalog.note.eventsMissing'), warning: true };
  return { text: t('catalog.note.noEvents'), warning: false };
}

/** 全部展开 / 全部收起：产品行与两组分组的键一次性置为同一值。 */
export function catalogExpansion(nodes: readonly CatalogProductNode[], expanded: boolean): Record<string, boolean> {
  return Object.fromEntries(nodes.flatMap(node => {
    const code = node.product.productCode;
    return [[catalogKeys.product(code), expanded], [catalogKeys.capabilities(code), expanded], [catalogKeys.events(code), expanded]];
  }));
}
