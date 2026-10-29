import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { RequestFailure } from "../auth.ts";
import { SCHEMA_NAMESPACE, schemaI18nKey } from "./schema.ts";
import { requestErrorText } from "./draft.ts";
import { useLocale, useT } from "../i18n/react.tsx";

/** 7px 圆点 + 灰色文字；语义色只在圆点（需要处理 / 逾期时文字同色）。 */
export function StatusDot({ tone, children }: { tone: "done" | "active" | "attention" | "error" | "todo"; children: ReactNode }) {
  return <span className={"status-dot " + tone}>{children}</span>;
}

/**
 * Schema 文案：键来自国家包 DML（`onboardingSchema.*`，经 schemaI18nKey 映射到 `onboarding-schema.*`）或本应用（`onboarding.*`）；解析失败时显示由代码推导的可读名称，不显示空白或原始键
 */
export function useSchemaText() {
  const lookup = useSchemaLookup();
  return (labelKey: string | null | undefined, fallback: string): string => lookup(labelKey) ?? humanize(fallback);
}
/** 只在有文案时返回（帮助文字等可省略的文案）。 */
export function useSchemaLookup() {
  const to = useT("onboarding"), ts = useT(SCHEMA_NAMESPACE);
  return (raw: string | null | undefined): string | null => {
    if (!raw) return null;
    const key = schemaI18nKey(raw);
    const [namespace, ...rest] = key.split(".");
    const translate = namespace === SCHEMA_NAMESPACE ? ts : namespace === "onboarding" ? to : null;
    if (!translate || !rest.length) return null;
    const value = translate(rest.join("."));
    return value === key ? null : value;
  };
}
/** `SA_CR_NUMBER` / `legal_name` / `profile.organization.legal_name` → "Legal name"。 */
export function humanize(code: string): string {
  const last = code.split(/[.:]/).pop() ?? code;
  const words = last.replace(/([a-z])([A-Z])/g, "$1 $2").replace(/_/g, " ").trim().toLowerCase();
  return words ? words[0].toUpperCase() + words.slice(1) : code;
}

/**
 * 字段外框：标签在上，"可选"灰字跟在标签后；帮助文字在标签下；错误在控件下（红框 + 红字）
 * <li>optional 为文字时显示该文字代替"可选"（如批准前补充的字段显示"批准前提供"）。</li>
 */
export function FieldShell({ id, label, optional, help, error, children, wide, mono }: {
  id: string; label: string; optional?: boolean | string; help?: string | null; error?: string | null; children: ReactNode; wide?: boolean; mono?: boolean;
}) {
  const t = useT("onboarding");
  return <div className={"ob-field" + (wide ? " wide" : "") + (mono ? " mono-field" : "")} data-field={id}>
    <label htmlFor={id}>{label}{optional && <span className="ob-optional">{typeof optional === "string" ? optional : t("form.optional")}</span>}</label>
    {help && <p className="ob-help" id={id + "-help"}>{help}</p>}
    {children}
    {error && <p className="ob-error" id={id + "-error"} role="alert">{error}</p>}
  </div>;
}

/** 滚动到字段并聚焦，外框高亮 1200ms 衰减；减少动效时只聚焦。 */
export function focusAnchor(id: string) {
  const node = document.getElementById(id) ?? document.querySelector<HTMLElement>("[data-field='" + CSS.escape(id) + "']");
  if (!node) return false;
  const wrapper = node.closest<HTMLElement>(".ob-field, .ob-row, .ob-slot, .ob-declaration") ?? node;
  wrapper.scrollIntoView({ block: "center", behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
  const target = node.matches("input,select,textarea,button,a") ? node : node.querySelector<HTMLElement>("input,select,textarea,button,a");
  target?.focus({ preventScroll: true });
  wrapper.classList.remove("ob-highlight");
  void wrapper.offsetWidth;
  wrapper.classList.add("ob-highlight");
  return true;
}

/**
 * 错误汇总：左侧 3px 危险色竖线 + 黑色标题 + 逐条链接；出现时取焦点并在页面标题前加"错误："
 * <li>不用红色底框；点链接滚动并聚焦对应字段。</li>
 */
export function ErrorSummary({ items }: { items: { id: string; text: string; onOpen?: () => void }[] }) {
  const ref = useRef<HTMLDivElement>(null), t = useT("onboarding");
  const count = items.length;
  useEffect(() => {
    if (!count) return;
    ref.current?.focus();
    const original = document.title, prefix = t("form.error_title_prefix");
    if (!original.startsWith(prefix)) document.title = prefix + original;
    return () => { document.title = original; };
  }, [count]);
  if (!count) return null;
  return <div className="ob-summary" role="alert" tabIndex={-1} ref={ref}>
    <strong>{t("form.error_summary", { count })}</strong>
    <ul>{items.map((item) => <li key={item.id + item.text}>
      <a href={"#" + item.id} onClick={(event) => { event.preventDefault(); if (item.onOpen) item.onOpen(); else focusAnchor(item.id); }}>{item.text}</a>
    </li>)}</ul>
  </div>;
}

/** 页面级失败：一句话 + 请求编号；不用彩色底框。 */
export function Failure({ error }: { error: Error | null }) {
  const ref = useRef<HTMLDivElement>(null), tc = useT("common");
  useEffect(() => { if (error) ref.current?.focus(); }, [error]);
  if (!error) return null;
  return <div className="notice error" role="alert" tabIndex={-1} ref={ref}>
    {requestErrorText(error)}
    {error instanceof RequestFailure && error.requestId && <small>{tc("request_id", { requestId: error.requestId })}</small>}
  </div>;
}

/** 自动保存提示：「已保存 · 刚刚」/「已保存 · 2 分钟前」/「正在保存…」，aria-live=polite。 */
export function SavedIndicator({ savedAt, saving }: { savedAt: string | null; saving: boolean }) {
  const t = useT("onboarding"), { locale } = useLocale();
  const [, tick] = useState(0);
  useEffect(() => { const timer = window.setInterval(() => tick((n) => n + 1), 30_000); return () => clearInterval(timer); }, []);
  let label = "";
  if (saving) label = t("save.saving");
  else if (savedAt && Number.isFinite(Date.parse(savedAt))) {
    const minutes = Math.max(0, Math.round((Date.now() - Date.parse(savedAt)) / 60_000));
    const relative = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });
    label = t("save.saved", { when: minutes < 1 ? t("save.just_now") : minutes < 60 ? relative.format(-minutes, "minute")
      : minutes < 1440 ? relative.format(-Math.round(minutes / 60), "hour") : relative.format(-Math.round(minutes / 1440), "day") });
  }
  return <span className="ob-saved" aria-live="polite">{label}</span>;
}

/** 按钮内的延迟转圈：250ms 后才出现，避免闪烁。 */
export function Busy({ active, label, children }: { active: boolean; label: string; children: ReactNode }) {
  return active ? <><span className="ob-spinner" aria-hidden="true" />{label}</> : <>{children}</>;
}

/** 侧边抽屉：240ms 滑入；Esc 关闭；焦点进入抽屉并在关闭后回到触发行。 */
export function Drawer({ title, onClose, children, footer }: { title: string; onClose: () => void; children: ReactNode; footer: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null), t = useT("onboarding");
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    (ref.current?.querySelector<HTMLElement>(".ob-drawer-body input,.ob-drawer-body select,.ob-drawer-body textarea") ?? ref.current?.querySelector<HTMLElement>("button"))?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
      if (event.key !== "Tab" || !ref.current) return;
      const nodes = [...ref.current.querySelectorAll<HTMLElement>("input,select,textarea,button,a[href]")].filter((node) => !node.hasAttribute("disabled"));
      if (!nodes.length) return;
      const first = nodes[0], last = nodes[nodes.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", onKey);
    document.body.classList.add("ob-locked");
    return () => { document.removeEventListener("keydown", onKey); document.body.classList.remove("ob-locked"); previous?.focus(); };
  }, []);
  return <div className="ob-drawer-layer">
    <div className="ob-scrim" onClick={onClose} />
    <aside className="ob-drawer" role="dialog" aria-modal="true" aria-labelledby="ob-drawer-title" ref={ref}>
      <header><h2 id="ob-drawer-title">{title}</h2>
        <button className="ob-icon-button" type="button" onClick={onClose} aria-label={t("drawer.close")}>×</button></header>
      <div className="ob-drawer-body">{children}</div>
      <footer>{footer}</footer>
    </aside>
  </div>;
}

/** 名字首字母（头像）；无名字时为空。 */
export function initials(name: string): string {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]!.toUpperCase()).join("");
}

/**
 * 行内确认：点击触发后在原位展开一句说明 + 取消 / 确认，不弹浏览器对话框、不铺遮罩
 * <li>展开后焦点落在确认按钮；Esc 或取消收起并把焦点还给触发按钮。</li>
 */
export function InlineConfirm({ trigger, body, confirm, working, onConfirm, className }: {
  trigger: string; body: string; confirm: string; working: string; onConfirm: () => Promise<void>; className?: string;
}) {
  const t = useT("onboarding");
  const [open, setOpen] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState<Error | null>(null);
  const confirmRef = useRef<HTMLButtonElement>(null), triggerRef = useRef<HTMLButtonElement>(null);
  useEffect(() => { if (open) confirmRef.current?.focus(); }, [open]);
  const close = () => { setOpen(false); setError(null); window.setTimeout(() => triggerRef.current?.focus(), 0); };
  async function run() {
    setBusy(true); setError(null);
    try { await onConfirm(); setOpen(false); } catch (failure) { setError(failure as Error); } finally { setBusy(false); }
  }
  if (!open) return <button type="button" ref={triggerRef} className={"text-button danger-text" + (className ? " " + className : "")} onClick={() => setOpen(true)}>{trigger}</button>;
  return <div className="ob-confirm ob-enter" role="group" aria-label={trigger} onKeyDown={(event) => { if (event.key === "Escape" && !busy) close(); }}>
    <p>{body}</p>
    <Failure error={error} />
    <div className="ob-confirm-actions">
      <button type="button" className="secondary" disabled={busy} onClick={close}>{t("action.cancel")}</button>
      <button type="button" className="danger" ref={confirmRef} disabled={busy} onClick={() => void run()}><Busy active={busy} label={working}>{confirm}</Busy></button>
    </div>
  </div>;
}
