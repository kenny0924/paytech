import { createI18n } from './runtime.ts';
import type { LocaleInfo, Params } from './runtime.ts';
import { builtin } from './builtin.ts';

export type { Params, LocaleInfo } from './runtime.ts';

/**
 * Locales this build knows before a manifest loads. Native names are data, written as escapes so a CJK scan of
 * src/i18n stays meaningful; the manifest's nativeName wins once it loads.
 */
export const appLocales: LocaleInfo[] = [
  { code: 'en', nativeName: 'English', direction: 'LTR' },
  { code: 'zh-CN', nativeName: '\u7b80\u4f53\u4e2d\u6587', direction: 'LTR' },
];

const browser = typeof window !== 'undefined';
function browserStore(name: 'localStorage' | 'sessionStorage'): Storage | null {
  try { return browser ? window[name] : null; } catch { return null; }
}

/** Application-wide instance; reading it never touches browser globals until `start()`. */
export const i18n = createI18n({
  builtin,
  locales: appLocales,
  defaultLocale: 'en',
  manifestUrl: '/api/client/i18n/v1/manifest',
  storage: browserStore('localStorage'),
  session: browserStore('sessionStorage'),
  warn: import.meta.env?.DEV ? (message: string) => console.warn(message) : undefined,
  document: browser ? document : null,
  // Client Console: the switcher lists only published locales; without a release everything stays English.
  publishedOnly: true,
});

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
export function formatDateTime(value: string | number | Date | null | undefined): string {
  return formatDate(value, { dateStyle: 'medium', timeStyle: 'short' });
}
export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(i18n.locale(), options).format(value);
}
