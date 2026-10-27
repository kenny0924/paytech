import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { formatCountry, i18n, tOr } from '../i18n/index.ts';
import type { Translate } from '../i18n/index.ts';

/**
 * Platform 参考数据（国家、行政区、行业、MCC）的 BFF 路径、页面模型、解码与显示名
 * <li>后端只返回英文 name；显示名顺序（需求 B7）：国家与币种用 Intl.DisplayNames，行业、MCC、行政区用 refdata 文案，取不到时回退 name，再回退编码。</li>
 * <li>refdata 命名空间只放数据名称（refdata.industry.CODE、refdata.subdivision.CODE、refdata.mcc.CODE），由 Platform 生成脚本导入与发布，
 * 不随前端内置；首次 useT('refdata') 时按需加载语言包，没有发布时回退后端 name。</li>
 */
export const referenceDataApi = '/reference-data-admin/v1';
export const referenceDataRoute = '/reference-data';
export const referenceDataPaths = {
  countries: `${referenceDataApi}/countries`,
  subdivisions: `${referenceDataApi}/country-subdivisions`,
  industries: `${referenceDataApi}/industries`,
  mccs: `${referenceDataApi}/mccs`,
} as const;

export const referenceStatuses = ['DRAFT', 'ACTIVE', 'INACTIVE'] as const;
export type ReferenceStatus = typeof referenceStatuses[number];
/** 状态标签颜色；显示名为 `reference-data.enum.status.<值>`。 */
export const referenceStatusColors: Readonly<Record<ReferenceStatus, string>> = { DRAFT: 'gold', ACTIVE: 'green', INACTIVE: 'default' };

export interface RefCountry {
  countryCode: string; alpha3Code: string | null; numericCode: string | null; name: string | null; regionCode: string | null;
  callingCode: string | null; defaultCurrencyCode: string | null; logoFileAddress: string | null; logoPublicUrl: string | null;
  status: ReferenceStatus; version: number;
}
export interface RefSubdivision {
  subdivisionCode: string; countryCode: string; parentSubdivisionCode: string | null; category: string | null; name: string | null;
  localName: string | null; status: ReferenceStatus; version: number;
}
export interface RefIndustry {
  industryCode: string; parentIndustryCode: string | null; level: 1 | 2; name: string | null; description: string | null;
  defaultMccCode: string | null; logoFileAddress: string | null; logoPublicUrl: string | null; sortNo: number; status: ReferenceStatus; version: number;
}
export interface RefMcc { mccCode: string; name: string | null; description: string | null; networkHighRisk: boolean; status: ReferenceStatus; version: number }
/** 审核分类页的行业大类，带 ACTIVE 细类。 */
export interface IndustryCategory { industryCode: string; name: string | null; logoPublicUrl: string | null; children: RefIndustry[] }

// ---------- 解码（失败只给通用错误，不回显响应内容） ----------

const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
const list = (v: unknown, max: number): v is unknown[] => Array.isArray(v) && v.length <= max;
const nullableText = (v: unknown, max = 512): v is string | null => v === null || v === undefined || (typeof v === 'string' && v.length <= max);
const opt = <T>(v: T | undefined): T | null => (v === undefined ? null : v);
const status = (v: unknown): v is ReferenceStatus => typeof v === 'string' && (referenceStatuses as readonly string[]).includes(v);
const positive = (v: unknown) => Number.isSafeInteger(v) && Number(v) > 0;
const fileAddress = (v: unknown) => v === null || v === undefined || (typeof v === 'string' && /^\/files\/v1\/[1-9][0-9]{0,18}$/.test(v));
const httpsUrl = (v: unknown) => {
  if (v === null || v === undefined) return true;
  if (typeof v !== 'string') return false;
  try { const url = new URL(v); return url.protocol === 'https:' || url.protocol === 'http:'; } catch { return false; }
};
function require(valid: unknown): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
function items(value: unknown, max: number): unknown[] {
  require(object(value) && list(value.items, max)); return value.items as unknown[];
}
/** 编码在列表内不得重复。 */
function unique<T>(values: T[], code: (value: T) => string): T[] {
  require(new Set(values.map(code)).size === values.length); return values;
}

export function decodeCountry(value: unknown): RefCountry {
  const v = value as Record<string, unknown>;
  require(object(v) && typeof v.countryCode === 'string' && /^[A-Z]{2}$/.test(v.countryCode) && status(v.status) && positive(v.version)
    && nullableText(v.alpha3Code, 3) && nullableText(v.numericCode, 3) && nullableText(v.name, 128) && nullableText(v.regionCode, 32)
    && nullableText(v.callingCode, 8) && nullableText(v.defaultCurrencyCode, 3) && fileAddress(v.logoFileAddress) && httpsUrl(v.logoPublicUrl));
  return { countryCode: v.countryCode, alpha3Code: opt(v.alpha3Code as string | null | undefined), numericCode: opt(v.numericCode as string | null | undefined),
    name: opt(v.name as string | null | undefined), regionCode: opt(v.regionCode as string | null | undefined),
    callingCode: opt(v.callingCode as string | null | undefined), defaultCurrencyCode: opt(v.defaultCurrencyCode as string | null | undefined),
    logoFileAddress: opt(v.logoFileAddress as string | null | undefined), logoPublicUrl: opt(v.logoPublicUrl as string | null | undefined),
    status: v.status, version: v.version as number };
}
export function decodeCountries(value: unknown): RefCountry[] {
  return unique(items(value, 400).map(decodeCountry), c => c.countryCode);
}

export function decodeSubdivision(value: unknown): RefSubdivision {
  const v = value as Record<string, unknown>;
  require(object(v) && typeof v.subdivisionCode === 'string' && /^[A-Z]{2}-[A-Z0-9]{1,3}$/.test(v.subdivisionCode)
    && typeof v.countryCode === 'string' && v.subdivisionCode.startsWith(v.countryCode + '-') && status(v.status) && positive(v.version)
    && nullableText(v.parentSubdivisionCode, 6) && nullableText(v.category, 32) && nullableText(v.name, 128) && nullableText(v.localName, 128));
  return { subdivisionCode: v.subdivisionCode, countryCode: v.countryCode, parentSubdivisionCode: opt(v.parentSubdivisionCode as string | null | undefined),
    category: opt(v.category as string | null | undefined), name: opt(v.name as string | null | undefined),
    localName: opt(v.localName as string | null | undefined), status: v.status, version: v.version as number };
}
export function decodeSubdivisions(value: unknown, countryCode?: string): RefSubdivision[] {
  const result = unique(items(value, 5000).map(decodeSubdivision), s => s.subdivisionCode);
  require(!countryCode || result.every(s => s.countryCode === countryCode));
  return result;
}

export function decodeIndustry(value: unknown): RefIndustry {
  const v = value as Record<string, unknown>;
  const code = (c: unknown): c is string => typeof c === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(c);
  require(object(v) && code(v.industryCode) && (v.level === 1 || v.level === 2) && status(v.status) && positive(v.version)
    && (v.level === 1 ? (v.parentIndustryCode ?? null) === null : code(v.parentIndustryCode))
    && (v.defaultMccCode === null || v.defaultMccCode === undefined || (typeof v.defaultMccCode === 'string' && /^[0-9]{4}$/.test(v.defaultMccCode)))
    && Number.isSafeInteger(v.sortNo) && nullableText(v.name, 128) && nullableText(v.description, 512)
    && fileAddress(v.logoFileAddress) && httpsUrl(v.logoPublicUrl));
  return { industryCode: v.industryCode, parentIndustryCode: opt(v.parentIndustryCode as string | null | undefined), level: v.level,
    name: opt(v.name as string | null | undefined), description: opt(v.description as string | null | undefined),
    defaultMccCode: opt(v.defaultMccCode as string | null | undefined), logoFileAddress: opt(v.logoFileAddress as string | null | undefined),
    logoPublicUrl: opt(v.logoPublicUrl as string | null | undefined), sortNo: v.sortNo as number, status: v.status, version: v.version as number };
}
export function decodeIndustries(value: unknown): RefIndustry[] {
  return unique(items(value, 2000).map(decodeIndustry), i => i.industryCode);
}

export function decodeMcc(value: unknown): RefMcc {
  const v = value as Record<string, unknown>;
  require(object(v) && typeof v.mccCode === 'string' && /^[0-9]{4}$/.test(v.mccCode) && status(v.status) && positive(v.version)
    && typeof v.networkHighRisk === 'boolean' && nullableText(v.name, 255) && nullableText(v.description, 512));
  return { mccCode: v.mccCode, name: opt(v.name as string | null | undefined), description: opt(v.description as string | null | undefined),
    networkHighRisk: v.networkHighRisk, status: v.status, version: v.version as number };
}
/** MCC 分页：页码、条数与总数自洽。 */
export function decodeMccPage(value: unknown): PagingResponseBody<RefMcc> {
  const v = value as Record<string, unknown>;
  require(object(v) && list(v.items, 200) && positive(v.page) && positive(v.size) && Number.isSafeInteger(v.total) && Number(v.total) >= 0);
  return { items: unique((v.items as unknown[]).map(decodeMcc), m => m.mccCode), page: v.page as number, size: v.size as number, total: v.total as number };
}

export function decodeIndustryCategory(value: unknown): IndustryCategory {
  const v = value as Record<string, unknown>;
  require(object(v) && typeof v.industryCode === 'string' && /^[A-Z][A-Z0-9_]{0,63}$/.test(v.industryCode) && nullableText(v.name, 128)
    && httpsUrl(v.logoPublicUrl) && list(v.children, 500));
  const children = (v.children as unknown[]).map(decodeIndustry);
  require(children.every(child => child.level === 2 && child.parentIndustryCode === v.industryCode));
  return { industryCode: v.industryCode, name: opt(v.name as string | null | undefined), logoPublicUrl: opt(v.logoPublicUrl as string | null | undefined), children };
}

// ---------- 显示名 ----------

/** refdata 类型：与 Platform 生成脚本导出的键前缀一致。 */
export type RefdataType = 'industry' | 'subdivision' | 'mcc';

/** 行业、行政区、MCC 的显示名：refdata 文案 → 后端英文 name → 编码。tRef 为 useT('refdata') / translator('refdata')。 */
export function refdataName(tRef: Translate, type: RefdataType, code: string, name: string | null | undefined): string {
  return tOr(tRef, `${type}.${code}`, name || code);
}
/** 行业选择说明：refdata `industry.<CODE>.description` → 后端英文 description；都没有时为 null。 */
export function industryDescription(tRef: Translate, code: string, description: string | null | undefined): string | null {
  const key = `industry.${code}.description`;
  return tRef.has(key) ? tRef(key) : description || null;
}
/** 当前语言下没有专门译文、显示的就是英文 name（用于"暂无中文"提示）；英文界面恒为 false。 */
export function untranslated(tRef: Translate, type: RefdataType, code: string, name: string | null | undefined, locale = i18n.locale()): boolean {
  if (locale === 'en' || !name) return false;
  return refdataName(tRef, type, code, name) === name;
}
/** 国家名：Intl.DisplayNames；浏览器不认识的编码回退后端 name，再回退编码。 */
export function countryName(code: string, name: string | null | undefined, locale = i18n.locale()): string {
  const localized = formatCountry(code, locale);
  return localized !== code ? localized : name || code;
}
/** 币种名：Intl.DisplayNames（currency）；不认识时回退后端 name，再回退编码。 */
export function currencyName(code: string, name: string | null | undefined, locale = i18n.locale()): string {
  try {
    const localized = new Intl.DisplayNames([locale], { type: 'currency', fallback: 'none' }).of(code);
    if (localized && localized !== code) return localized;
  } catch { /* fall through */ }
  return name || code;
}

// ---------- 列表筛选与组装 ----------

const fold = (value: string | null | undefined) => (value ?? '').toLocaleLowerCase();
/** 关键字匹配：编码、三位码、数字码、英文名或当前语言名称（不区分大小写的包含匹配）；空关键字全部匹配。 */
export function matchesKeyword(keyword: string, ...values: (string | null | undefined)[]): boolean {
  const needle = fold(keyword.trim());
  return !needle || values.some(value => fold(value).includes(needle));
}

/** 行业树：大类按排序号，其下细类按排序号紧随；上级不在列表中的细类挂在末尾的"未归类"组里（编码原样显示）。 */
export function industryTree(industries: readonly RefIndustry[]): { category: RefIndustry | null; children: RefIndustry[] }[] {
  const bySort = (a: RefIndustry, b: RefIndustry) => a.sortNo - b.sortNo || a.industryCode.localeCompare(b.industryCode);
  const categories = industries.filter(i => i.level === 1).sort(bySort);
  const known = new Set(categories.map(c => c.industryCode));
  const groups = categories.map(category => ({ category: category as RefIndustry | null,
    children: industries.filter(i => i.level === 2 && i.parentIndustryCode === category.industryCode).sort(bySort) }));
  const orphans = industries.filter(i => i.level === 2 && !known.has(i.parentIndustryCode ?? '')).sort(bySort);
  return orphans.length ? [...groups, { category: null, children: orphans }] : groups;
}

/** MCC → 把它作为建议 MCC 的细类编码（按编码排序）。 */
export function mccUsage(industries: readonly RefIndustry[]): Map<string, string[]> {
  const usage = new Map<string, string[]>();
  for (const industry of industries) {
    if (industry.level !== 2 || !industry.defaultMccCode) continue;
    usage.set(industry.defaultMccCode, [...(usage.get(industry.defaultMccCode) ?? []), industry.industryCode].sort());
  }
  return usage;
}

/** 行政区按国家计数，国家按编码排序。 */
export function subdivisionCountries(subdivisions: readonly RefSubdivision[]): { countryCode: string; count: number }[] {
  const counts = new Map<string, number>();
  subdivisions.forEach(s => counts.set(s.countryCode, (counts.get(s.countryCode) ?? 0) + 1));
  return [...counts.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([countryCode, count]) => ({ countryCode, count }));
}

/** 文件地址缩写：/files/v1/7Kq2…e9 样式，完整地址放在 title。 */
export function shortFileAddress(address: string): string {
  const id = address.slice('/files/v1/'.length);
  return id.length <= 8 ? address : `/files/v1/${id.slice(0, 4)}…${id.slice(-2)}`;
}

/** MCC 搜索 Query：关键字去除首尾空白，空关键字不带；状态 ALL 不带。 */
export function mccQuery(keyword: string, statusFilter: ReferenceStatus | 'ALL'): Record<string, string> {
  const result: Record<string, string> = {};
  if (keyword.trim()) result.keyword = keyword.trim();
  if (statusFilter !== 'ALL') result.status = statusFilter;
  return result;
}
