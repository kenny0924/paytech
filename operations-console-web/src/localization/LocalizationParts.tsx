import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, ConfigProvider, Spin, Typography } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import { ApiError, session } from '../api/session';
import { I18nGate, useLocale, useT } from '../i18n/react';
import { BASE_LOCALE } from './localizationApi';
import type { Locale } from './localizationApi';
import type { IcuIssue } from './icu';
import './localization.css';

/** 本需求页面的命名空间：页面文案与枚举展示名。 */
export const ADMIN_NS = 'i18n-admin';
export const ENUM_NS = 'enum';
export const useAdminT = () => useT(ADMIN_NS);
export const useEnumT = () => useT(ENUM_NS);

/**
 * 多语言页面外框：等运行时与语言包就绪再渲染（避免中文页面先闪英文），并只在本页面范围内按当前语言切换 antd 组件文案；
 * 全局 antd / dayjs 仍是中文，既有页面不受影响。
 */
export function LocalizationFrame({ children }: { children: ReactNode }) {
  return <I18nGate namespaces={[ADMIN_NS, ENUM_NS]} fallback={<div className="content-loading"><Spin /></div>}>
    <ScopedLocale>{children}</ScopedLocale>
  </I18nGate>;
}
function ScopedLocale({ children }: { children: ReactNode }) {
  const { locale } = useLocale();
  return <ConfigProvider locale={locale === BASE_LOCALE ? enUS : zhCN} button={{ autoInsertSpace: false }}>
    <div className="l10n-page" lang={locale}>{children}</div>
  </ConfigProvider>;
}

/** 读取一次；path 为 null 时不请求。失败由页面就地展示。 */
export function useLoad<T>(path: string | null, decode: (value: unknown) => T) {
  const [data, setData] = useState<T>(); const [loading, setLoading] = useState(path !== null);
  const [failure, setFailure] = useState<unknown>(); const [reload, setReload] = useState(0);
  const decoder = useRef(decode); decoder.current = decode;
  useEffect(() => {
    if (path === null) { setLoading(false); setData(undefined); return; }
    const controller = new AbortController(); setLoading(true); setFailure(undefined);
    session.request<T>(path, { signal: controller.signal }, value => decoder.current(value))
      .then(value => { if (!controller.signal.aborted) setData(value); })
      .catch(error => { if (!controller.signal.aborted) setFailure(error); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [path, reload]);
  return { data, loading, failure, refresh: () => setReload(value => value + 1), setData };
}

/** 加载失败：404 / 403 有专门说明；带请求编号与重试。服务端 detail 本期仍是中文（technique 3.10 已知限制）。 */
export function LoadFailed({ failure, retry, title }: { failure: unknown; retry?: () => void; title?: string }) {
  const t = useAdminT();
  const notFound = failure instanceof ApiError && failure.status === 404;
  const forbidden = failure instanceof ApiError && failure.status === 403;
  const requestId = failure instanceof ApiError ? failure.requestId : '';
  return <Alert type={forbidden ? 'info' : 'warning'} showIcon className="l10n-load-failed"
    title={notFound ? t('common.not_found') : forbidden ? t('common.no_permission') : title ?? t('common.load_failed')}
    description={failure instanceof ApiError && failure.detail ? failure.detail
      : requestId ? <>{t('common.request_id')} <Typography.Text copyable>{requestId}</Typography.Text></> : t('common.try_later')}
    action={retry && !notFound && !forbidden ? <Button size="small" onClick={retry}>{t('common.retry')}</Button> : undefined} />;
}

/** 带位置的语法 / 结构问题；参数类问题已点名参数，不再附位置。 */
const POSITIONED = new Set(['unclosed_brace', 'unmatched_close', 'bad_argument', 'missing_fragment', 'unsupported_type', 'choice_unsupported', 'missing_other']);
/** 问题说明：code → 界面文案，参数原样代入；语法类问题附"第 n 个字符"。 */
export function issueText(t: (key: string, params?: Record<string, string | number>) => string, issue: IcuIssue): string {
  const text = t(`icu.${issue.code}`, issue.params);
  return POSITIONED.has(issue.code) && issue.start >= 0 ? t('icu.with_position', { message: text, position: issue.start + 1 }) : text;
}

/** 本人持有的按钮资源。 */
export const hasButton = (resources: { resourceType: string; resourceCode: string }[], code: string) =>
  resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === code);

/** 语言显示名：目录里的自称，找不到时显示代码。 */
export function localeName(locales: Locale[] | undefined, code: string): string {
  return locales?.find(locale => locale.localeCode === code)?.nativeName ?? code;
}

/** 账号 ID 的简短显示（本期 BFF 不返回姓名）：末 6 位。 */
export const shortAccount = (id: string | null | undefined) => id ? `#${id.slice(-6)}` : '—';

/** 分段选择（原型 07、08 的默认语言与发布要求）。 */
export function Segmented<T extends string | number>({ value, options, onChange, disabled, label }: {
  value: T; options: { value: T; label: ReactNode }[]; onChange: (value: T) => void; disabled?: boolean; label?: string;
}) {
  return <div className="l10n-segmented" role="radiogroup" aria-label={label}>
    {options.map(option => <button type="button" key={String(option.value)} role="radio" aria-checked={option.value === value}
      className={option.value === value ? 'is-active' : ''} disabled={disabled} onClick={() => onChange(option.value)}>{option.label}</button>)}
  </div>;
}

/** 摘要行图标的色块（原型 03—06）。 */
export type Tone = 'green' | 'orange' | 'red' | 'blue' | 'gray';
export function ToneIcon({ tone, children }: { tone: Tone; children: ReactNode }) {
  return <span className={`l10n-tone-icon is-${tone}`} aria-hidden="true">{children}</span>;
}

// ---------- 图标（内联 SVG，currentColor） ----------
const svg = (path: ReactNode, size = 14) => <svg width={size} height={size} viewBox="0 0 16 16" fill="none" stroke="currentColor"
  strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{path}</svg>;
export const Icons = {
  check: (size?: number) => svg(<path d="M3.5 8.5 6.5 11.5 12.5 4.5" />, size),
  checkCircle: (size?: number) => svg(<><circle cx="8" cy="8" r="6.2" /><path d="M5.4 8.2 7.2 10l3.4-3.8" /></>, size),
  pencil: (size?: number) => svg(<><path d="M10.8 2.8 13.2 5.2 6 12.4 3 13l.6-3z" /></>, size),
  translate: (size?: number) => svg(<><path d="M2.5 4h6M5.5 2.5V4M3.8 4c.6 2.4 2 4 4 5M7.4 4c-.5 2.3-2 4.2-4.4 5.2" /><path d="M8.5 14l2.5-6 2.5 6M9.3 12.2h3.4" /></>, size),
  warning: (size?: number) => svg(<><path d="M8 2.5 14 13H2z" /><path d="M8 6.5v3M8 11.3v.2" /></>, size),
  error: (size?: number) => svg(<><circle cx="8" cy="8" r="6.2" /><path d="M6 6l4 4M10 6l-4 4" /></>, size),
  info: (size?: number) => svg(<><circle cx="8" cy="8" r="6.2" /><path d="M8 7.3v3.6M8 5.2v.2" /></>, size),
  upload: (size?: number) => svg(<><path d="M8 10.5V3M5 6l3-3 3 3" /><path d="M3 10.5V13h10v-2.5" /></>, size),
  send: (size?: number) => svg(<><path d="M14 2 7 9" /><path d="M14 2 9.5 14 7 9 2 6.5z" /></>, size),
  search: (size?: number) => svg(<><circle cx="7" cy="7" r="4.3" /><path d="m10.2 10.2 3.3 3.3" /></>, size),
  copy: (size?: number) => svg(<><rect x="5.5" y="5.5" width="8" height="8" rx="1.5" /><path d="M10.5 5.5V3.8c0-.7-.6-1.3-1.3-1.3H3.8c-.7 0-1.3.6-1.3 1.3v5.4c0 .7.6 1.3 1.3 1.3h1.7" /></>, size),
  up: (size?: number) => svg(<path d="M4 10l4-4 4 4" />, size),
  down: (size?: number) => svg(<path d="M4 6l4 4 4-4" />, size),
  right: (size?: number) => svg(<path d="M6 4l4 4-4 4" />, size),
  close: (size?: number) => svg(<path d="M4 4l8 8M12 4l-8 8" />, size),
  plus: (size?: number) => svg(<path d="M8 3v10M3 8h10" />, size),
  minus: (size?: number) => svg(<path d="M3.5 8h9" />, size),
  undo: (size?: number) => svg(<><path d="M5 4 2.5 6.5 5 9" /><path d="M2.5 6.5h7a3.5 3.5 0 0 1 0 7H7" /></>, size),
  restore: (size?: number) => svg(<><path d="M2.8 8a5.2 5.2 0 1 0 1.5-3.7" /><path d="M2.5 2.5v2.5H5" /></>, size),
  box: (size?: number) => svg(<><path d="M8 2 13.5 5v6L8 14 2.5 11V5z" /><path d="M2.5 5 8 8l5.5-3M8 8v6" /></>, size),
  folder: (size?: number) => svg(<><path d="M2.5 4.5c0-.6.4-1 1-1h3l1.5 1.5h4.5c.6 0 1 .4 1 1v6c0 .6-.4 1-1 1h-9c-.6 0-1-.4-1-1z" /><path d="M8 7.5v3M6.5 9h3" /></>, size),
  file: (size?: number) => svg(<><path d="M4 2.5h5.5L12.5 5.5v8H4z" /><path d="M9.5 2.5v3h3M6.3 9 5.5 10l.8 1M9.7 9l.8 1-.8 1" /></>, size),
  wand: (size?: number) => svg(<><path d="m3 13 7-7M9 3.5v1.5M12.5 7H11M11.8 4.2l-1 1" /></>, size),
  spinner: (size?: number) => svg(<><path d="M8 2v2M8 12v2M2 8h2M12 8h2M3.8 3.8l1.4 1.4M10.8 10.8l1.4 1.4M3.8 12.2l1.4-1.4M10.8 5.2l1.4-1.4" /></>, size),
  circle: (size?: number) => svg(<circle cx="8" cy="8" r="5.5" />, size),
  locate: (size?: number) => svg(<><circle cx="8" cy="8" r="4" /><path d="M8 1.5v2.5M8 12v2.5M1.5 8H4M12 8h2.5" /></>, size),
  more: (size?: number) => svg(<><circle cx="4" cy="8" r=".6" fill="currentColor" /><circle cx="8" cy="8" r=".6" fill="currentColor" /><circle cx="12" cy="8" r=".6" fill="currentColor" /></>, size),
  dot: () => <span className="l10n-dot" aria-hidden="true" />,
};
