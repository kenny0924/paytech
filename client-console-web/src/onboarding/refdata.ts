import type { CatalogItem, ReferenceCountry } from "./model.ts";

/**
 * Platform 参考数据的展示规则（B7）：后端只给编码、英文名与图标地址，名称由前端按界面语言决定
 * <li>国家、币种：`Intl.DisplayNames` → 后端英文名 → 编码。</li>
 * <li>行政区（及以后的行业、MCC）：i18n `refdata.<type>.<CODE>` → 后端英文名 → 编码。</li>
 * <li>选项只来自 BFF 下发的 Platform ACTIVE 数据，不内置任何国家、币种或行政区列表。</li>
 */
export const REF_CURRENCY = "REF_CURRENCY";
export const REF_COUNTRY_SUBDIVISION = "REF_COUNTRY_SUBDIVISION";
/** Platform 发布的参考数据命名空间（数据所有，不内置英文基准）。 */
export const REFDATA_NAMESPACE = "refdata";
export const subdivisionKey = (code: string) => "subdivision." + code;

/** 可搜索下拉的一项：label 为当前语言名称，display 为选中后触发按钮的文字（默认同 label），terms 为额外可匹配的文本（英文名、编码）。 */
export type RefOption = { code: string; label: string; display?: string; terms: string[]; logo: string | null };

const displayNames = new Map<string, Intl.DisplayNames | null>();
function names(locale: string, type: "region" | "currency"): Intl.DisplayNames | null {
  const key = locale + "\u0000" + type;
  if (!displayNames.has(key)) {
    try { displayNames.set(key, new Intl.DisplayNames([locale], { type, fallback: "none" })); } catch { displayNames.set(key, null); }
  }
  return displayNames.get(key)!;
}
function intlName(code: string, locale: string, type: "region" | "currency"): string | null {
  try { return names(locale, type)?.of(code) ?? null; } catch { return null; }
}

/** 国家名称：Intl → 后端英文名 → 编码；空编码为空串。 */
export function countryName(code: string | null | undefined, locale: string, englishName?: string | null): string {
  if (!code) return "";
  return intlName(code, locale, "region") ?? (englishName || code);
}

/** 币种名称（不含编码）：Intl → 后端英文名 → 编码。 */
export function currencyName(code: string, locale: string, englishName?: string | null): string {
  return intlName(code, locale, "currency") ?? (englishName || code);
}

/** 可选：只接受 http(s) 与站内绝对路径的图标地址，其余视为没有图标（显示占位）。 */
export function safeLogo(value: unknown): string | null {
  return typeof value === "string" && /^(https?:\/\/|\/(?!\/))/i.test(value) ? value : null;
}

/** 按当前语言名称排序。 */
function sorted(options: RefOption[], locale: string): RefOption[] {
  return [...options].sort((a, b) => a.label.localeCompare(b.label, locale) || a.code.localeCompare(b.code));
}

/** 国家选项：本地化名称、英文名与编码都可搜索，按本地化名称排序。 */
export function countryOptions(countries: ReferenceCountry[], locale: string): RefOption[] {
  return sorted(countries.map((country) => ({
    code: country.code, label: countryName(country.code, locale, country.name),
    terms: [country.name ?? "", country.code], logo: country.logoPublicUrl,
  })), locale);
}

/** 币种选项：列表显示本地化名称（编码在右列），选中后显示"本地化名称 (CODE)"；英文名与编码可搜索，保持后端顺序（按编码）。 */
export function currencyOptions(items: CatalogItem[], locale: string): RefOption[] {
  return items.map((item) => ({
    code: item.itemCode, label: currencyName(item.itemCode, locale, item.name),
    display: currencyName(item.itemCode, locale, item.name) + " (" + item.itemCode + ")",
    terms: [item.name ?? "", item.itemCode], logo: item.logoPublicUrl ?? null,
  }));
}

/**
 * 行政区选项：只取地址国家的行政区；名称由 lookup 按 `refdata.subdivision.<CODE>` 解析，缺失时回退英文名再回退编码
 * <li>没有国家时没有选项；该国没有收录行政区时返回空数组，由控件改为文本输入。</li>
 */
export function subdivisionOptions(items: CatalogItem[], countryCode: string | null | undefined, locale: string,
  lookup: (key: string) => string | null): RefOption[] {
  if (!countryCode) return [];
  return sorted(items.filter((item) => item.countryCode === countryCode).map((item) => ({
    code: item.itemCode, label: subdivisionName(item, lookup), terms: [item.name ?? "", item.itemCode], logo: null,
  })), locale);
}

/** 单个行政区名称：refdata 文案 → 英文名 → 编码。 */
export function subdivisionName(item: Pick<CatalogItem, "itemCode" | "name">, lookup: (key: string) => string | null): string {
  return lookup(subdivisionKey(item.itemCode)) ?? (item.name || item.itemCode);
}

/** 不区分大小写与变音符号。 */
export function normalize(text: string): string {
  return text.normalize("NFD").replace(/[̀-ͯ]/g, "").toLocaleLowerCase();
}

/** 搜索：本地化名称、英文名或编码包含查询词即匹配；编码前缀匹配排在前面。 */
export function filterOptions(options: RefOption[], query: string): RefOption[] {
  const wanted = normalize(query.trim());
  if (!wanted) return options;
  const matches = options.filter((option) => [option.label, ...option.terms].some((text) => normalize(text).includes(wanted)));
  const exact = matches.filter((option) => normalize(option.code).startsWith(wanted));
  return [...exact, ...matches.filter((option) => !exact.includes(option))];
}
