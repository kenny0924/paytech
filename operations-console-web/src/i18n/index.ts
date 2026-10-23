import { createI18n } from './runtime.ts';
import type { LocaleInfo, Params } from './runtime.ts';
import { builtin } from './builtin.ts';

export type { Params, LocaleInfo } from './runtime.ts';

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
 * Application-wide instance for the Operations Console. Only the localization admin pages use it this phase:
 * the default is zh-CN, there is no language switcher, and the runtime never touches <html lang> or the global
 * antd / dayjs locale (existing pages stay Chinese). `?lang=en` still works for previewing the English base.
 */
export const i18n = createI18n({
  builtin,
  locales: appLocales,
  defaultLocale: 'zh-CN',
  manifestUrl: '/api/operations/i18n/v1/manifest',
  storage: browserStore('localStorage'),
  session: browserStore('sessionStorage'),
  warn: import.meta.env?.DEV ? (message: string) => console.warn(message) : undefined,
  document: null,
});

let started: Promise<void> | null = null;
/**
 * Starts the runtime once, on the first localization page. Browser languages are deliberately ignored so the
 * pages follow the console default (zh-CN) instead of switching to English for an English-language browser.
 */
export function ensureStarted(): Promise<void> {
  started ??= i18n.start({ search: browser ? window.location.search : '', navigatorLanguages: [] });
  return started;
}

export type Translate = (key: string, params?: Params) => string;
/** Translator bound to one namespace, for code outside React components. */
export function translator(namespace: string): Translate {
  return (key, params) => i18n.t(namespace, key, params);
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
export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(i18n.locale(), options).format(value);
}
