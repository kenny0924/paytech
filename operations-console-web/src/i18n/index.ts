import { createI18n } from './runtime.ts';
import type { LocaleInfo, Params } from './runtime.ts';
import { builtin } from './builtin.ts';

export type { Params, LocaleInfo } from './runtime.ts';
export { OPS_NAMESPACES } from './namespaces.ts';

/**
 * Locales this build knows before (or without) a manifest. Native names are data, written as escapes so the
 * CJK scan of src/i18n stays meaningful; the manifest's nativeName wins once it loads.
 */
export const appLocales: LocaleInfo[] = [
  { code: 'en', nativeName: 'English', direction: 'LTR' },
  { code: 'zh-CN', nativeName: '\u7b80\u4f53\u4e2d\u6587', direction: 'LTR' },
];

const browser = typeof window !== 'undefined';
function browserStore(name: 'localStorage' | 'sessionStorage'): Storage | null {
  try { return browser ? window[name] : null; } catch { return null; }
}

/**
 * Application-wide instance for the whole Operations Console (the root I18nGate in main.tsx starts it before the
 * login page renders). English is the default; the language is `?lang=` (this tab) > the stored choice > `en`, and
 * only locales the manifest actually publishes are used and offered. Published manifests and bundles are cached in
 * localStorage, so a reload renders from the cache and checks for a newer release in the background. Switching the
 * language reloads the page (see LanguageSwitcher), so antd, dayjs and module-level values are fixed per page load.
 */
export const i18n = createI18n({
  builtin,
  locales: appLocales,
  defaultLocale: 'en',
  manifestUrl: '/api/operations/i18n/v1/manifest',
  storage: browserStore('localStorage'),
  session: browserStore('sessionStorage'),
  cache: browserStore('localStorage'),
  warn: import.meta.env?.DEV ? (message: string) => console.warn(message) : undefined,
  document: browser ? window.document : null,
  publishedOnly: true,
});

let started: Promise<void> | null = null;
/** Starts the runtime once. Browser languages are deliberately ignored: the console defaults to English. */
export function ensureStarted(): Promise<void> {
  started ??= i18n.start({ search: browser ? window.location.search : '', navigatorLanguages: [] });
  return started;
}

/** `t(key, params)` bound to one namespace, plus `has(key)` to test whether a key resolves. */
export type Translate = ((key: string, params?: Params) => string) & { has: (key: string) => boolean };
/** Builds a namespace-bound translator over any runtime instance (used by `useT` and `translator`). */
export function bindTranslate(instance: Pick<typeof i18n, 't' | 'has'>, namespace: string): Translate {
  return Object.assign((key: string, params?: Params) => instance.t(namespace, key, params),
    { has: (key: string) => instance.has(namespace, key) });
}
/**
 * Translator bound to one namespace, for code outside React components (models, validators, API helpers).
 * Call `t(...)` when the text is needed, never while a module loads: a module-level `const label = t('...')`
 * would be evaluated before the runtime has started.
 */
export function translator(namespace: string): Translate {
  return bindTranslate(i18n, namespace);
}

/**
 * Translation when the key exists, otherwise `fallback`. Use it where a key may legitimately be missing and a
 * server-provided name is the better fallback than the raw key, e.g. menu labels:
 * `tOr(tNav, 'resource.' + resourceCode, displayName)`.
 */
export function tOr(t: Translate, key: string, fallback: string, params?: Params): string {
  return t.has(key) ? t(key, params) : fallback;
}

/**
 * Display name of an enum code: `t(prefix + '.' + value)` when that key exists, otherwise the raw code (unknown
 * values stay visible instead of showing a key). Example: `enumLabel(t, 'enum.status', row.status)` with keys
 * `currency.enum.status.ACTIVE`… under `useT('currency')`. Empty values return ''.
 */
export function enumLabel(t: Translate, prefix: string, value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '';
  return tOr(t, prefix + '.' + value, value);
}

/** Date in the current locale; empty or invalid values show a dash. */
export function formatDate(value: string | number | Date | null | undefined, options?: Intl.DateTimeFormatOptions): string {
  if (value === null || value === undefined || value === '') return '—';
  const date = value instanceof Date ? value : new Date(value);
  if (!Number.isFinite(date.getTime())) return '—';
  return new Intl.DateTimeFormat(i18n.locale(), options).format(date);
}
/** Compact local time used by the console tables: MM-DD HH:mm (YYYY-MM-DD HH:mm outside the current year). */
export function formatShortDateTime(value: string | number | Date | null | undefined, now = new Date()): string {
  if (value === null || value === undefined || value === '') return '—';
  const date = value instanceof Date ? value : new Date(value);
  if (!Number.isFinite(date.getTime())) return '—';
  const pad = (number: number) => String(number).padStart(2, '0');
  const day = `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
  return date.getFullYear() === now.getFullYear() ? day : `${date.getFullYear()}-${day}`;
}
/**
 * Date and time in the current locale, 24-hour clock: the locale-aware replacement for
 * `new Date(value).toLocaleString('zh-CN', { hour12: false })` (zh-CN output is identical). Empty or invalid → '—'.
 */
export function formatDateTime(value: string | number | Date | null | undefined, options?: Intl.DateTimeFormatOptions): string {
  if (value === null || value === undefined || value === '') return '—';
  const date = value instanceof Date ? value : new Date(value);
  if (!Number.isFinite(date.getTime())) return '—';
  return date.toLocaleString(i18n.locale(), { hour12: false, ...options });
}
export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(i18n.locale(), options).format(value);
}
