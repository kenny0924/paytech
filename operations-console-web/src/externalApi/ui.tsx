import type { ReactNode } from 'react';
import { ConfigProvider } from 'antd';
import { Link } from 'react-router-dom';
import type { ExecuteStatus } from './flowModel';
import './externalApi.css';

/** 面包屑一项；没有 to 的是当前位置。 */
export interface Crumb { label: string; to?: string }

/** 外部 API 页面外框：统一字号与按钮文字（两个汉字不插空格），只作用于本组页面。 */
export function ExternalApiFrame({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <ConfigProvider button={{ autoInsertSpace: false }}>
    <div className={`xapi ${className}`}>{children}</div>
  </ConfigProvider>;
}

/** 页头：灰色面包屑、常规字重标题（可带编码标签）、右上操作；只读时显示无权限提示。 */
export function PageHeader({ crumbs, title, code, actions, readOnlyHint }: {
  crumbs: Crumb[]; title: ReactNode; code?: string; actions?: ReactNode; readOnlyHint?: string;
}) {
  return <header className="xapi-header">
    <nav className="xapi-crumbs" aria-label="面包屑">{crumbs.map((crumb, index) => <span key={`${crumb.label}:${index}`}>
      {index > 0 && <span className="xapi-crumb-sep">/</span>}
      {crumb.to ? <Link to={crumb.to}>{crumb.label}</Link> : <span>{crumb.label}</span>}</span>)}</nav>
    <div className="xapi-title-row">
      <h1 className="xapi-title">{title}{code && <code className="xapi-code-tag">{code}</code>}</h1>
      <div className="xapi-actions">{readOnlyHint && <span className="xapi-readonly">{readOnlyHint}</span>}{actions}</div>
    </div>
  </header>;
}

/** 内容分区：浅边框白底，可带标题与右侧操作。 */
export function Panel({ title, extra, children, className = '' }: {
  title?: ReactNode; extra?: ReactNode; children?: ReactNode; className?: string;
}) {
  return <section className={`xapi-panel ${className}`}>
    {(title || extra) && <div className="xapi-panel-head">{title && <h2>{title}</h2>}{extra && <div>{extra}</div>}</div>}
    {children}
  </section>;
}

/** 灰底说明框，逐条列出规则或限制。 */
export function Note({ items, className = '' }: { items: ReactNode[]; className?: string }) {
  return <ul className={`xapi-note ${className}`}>{items.map((item, index) => <li key={index}>{item}</li>)}</ul>;
}

/** 页签或分组标题后的数量：“请求字段 · 6”。 */
export const counted = (label: string, count: number) => `${label} · ${count}`;

/** 设计统一的时间格式 YYYY-MM-DD HH:mm（本地时区）。 */
export function formatTime(value: string): string {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return value;
  const pad = (number: number) => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 等宽文本：编码、路径、表达式、类型代码。 */
export function Mono({ children, tone }: { children: ReactNode; tone?: 'ref' | 'muted' }) {
  return <code className={`xapi-mono${tone ? ` xapi-mono-${tone}` : ''}`}>{children}</code>;
}

/** 执行状态彩色标签：SUCCESS 绿、FAIL 红、ERROR 橙。 */
export function StatusTag({ status }: { status: ExecuteStatus }) {
  return <span className={`xapi-status xapi-status-${status.toLowerCase()}`}>{status}</span>;
}

/** 受控句柄标记：橙色锁。 */
export function HandleMark({ label = 'PROTECTED_HANDLE' }: { label?: string }) {
  return <span className="xapi-handle">🔒 {label}</span>;
}

/** 只读字段：浅灰底深色正文，替代禁用输入框的低对比显示。 */
export function ReadOnlyValue({ value, mono }: { value: ReactNode; mono?: boolean }) {
  return <div className={`xapi-readonly-value${mono ? ' xapi-mono-block' : ''}`}>{value === '' || value === null || value === undefined ? '—' : value}</div>;
}

/** 字段标签 + 内容，标签在上。 */
export function Field({ label, hint, children, className = '' }: {
  label: ReactNode; hint?: ReactNode; children: ReactNode; className?: string;
}) {
  return <div className={`xapi-field ${className}`}><div className="xapi-field-label">{label}</div>{children}
    {hint && <div className="xapi-field-hint">{hint}</div>}</div>;
}
