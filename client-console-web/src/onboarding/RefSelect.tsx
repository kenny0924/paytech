import { useEffect, useId, useMemo, useRef, useState, useSyncExternalStore } from "react";
import type { KeyboardEvent as ReactKeyboardEvent } from "react";
import { useT } from "../i18n/react.tsx";
import { filterOptions } from "./refdata.ts";
import type { RefOption } from "./refdata.ts";

/**
 * 参考数据下拉（国家、币种、行政区）：触发按钮 + 可搜索列表；每项显示图标、本地化名称与等宽编码
 * <li>桌面在字段下方弹出；窄屏（≤640px）改为底部面板，搜索框固定在顶部，列表在其下滚动。</li>
 * <li>键盘：↑↓ 移动、Enter 选择、Esc 关闭并把焦点还给触发按钮；Home / End 跳到首尾。</li>
 * <li>当前值不在选项中（历史值或已停用）时按编码显示，不清空用户已保存的值。</li>
 */
export type RefSelectProps = {
  id: string; value: string; options: RefOption[]; disabled: boolean; invalid: boolean; describedBy?: string;
  /** 面板标题（窄屏底部面板）与列表的可访问名称。 */
  title: string;
  placeholder: string;
  /** 有图标的数据类别：country 无图标时显示中性国旗占位，currency 无图标时不显示，none 不留图标位。 */
  icon: "country" | "currency" | "none";
  /** 列表底部的说明（如"匹配本地化名称、英文名或编码"）。 */
  hint?: string;
  onChange: (code: string) => void;
  onBlur: () => void;
};

const narrowQuery = "(max-width: 640px)";
function subscribeNarrow(listener: () => void) {
  const query = matchMedia(narrowQuery);
  query.addEventListener("change", listener);
  return () => query.removeEventListener("change", listener);
}
const isNarrow = () => matchMedia(narrowQuery).matches;

export function RefSelect(props: RefSelectProps) {
  const t = useT("onboarding"), listId = useId(), narrow = useSyncExternalStore(subscribeNarrow, isNarrow, () => false);
  const [open, setOpen] = useState(false), [query, setQuery] = useState(""), [active, setActive] = useState(0);
  const root = useRef<HTMLDivElement>(null), trigger = useRef<HTMLButtonElement>(null), search = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLUListElement>(null);
  const shown = useMemo(() => filterOptions(props.options, query), [props.options, query]);
  const selected = props.options.find((option) => option.code === props.value);
  const searchable = props.options.length > 7;

  function show() {
    if (props.disabled) return;
    setQuery("");
    setActive(Math.max(0, props.options.findIndex((option) => option.code === props.value)));
    setOpen(true);
  }
  function close(returnFocus: boolean) {
    setOpen(false);
    props.onBlur();
    if (returnFocus) trigger.current?.focus();
  }
  function choose(option: RefOption) {
    if (option.code !== props.value) props.onChange(option.code);
    close(true);
  }

  // 打开后焦点进入搜索框（无搜索时进入列表）；窄屏锁定页面滚动。
  useEffect(() => {
    if (!open) return;
    (search.current ?? list.current)?.focus({ preventScroll: true });
    if (narrow) document.body.classList.add("ob-locked");
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) close(false); };
    document.addEventListener("pointerdown", outside);
    return () => { document.removeEventListener("pointerdown", outside); document.body.classList.remove("ob-locked"); };
  }, [open, narrow]);
  // 活动项保持在可见范围内。
  useEffect(() => {
    if (open) list.current?.querySelector<HTMLElement>("[data-index='" + active + "']")?.scrollIntoView({ block: "nearest" });
  }, [active, open, shown]);

  function onListKey(event: ReactKeyboardEvent) {
    const last = shown.length - 1;
    if (event.key === "ArrowDown") setActive((index) => Math.min(last, index + 1));
    else if (event.key === "ArrowUp") setActive((index) => Math.max(0, index - 1));
    else if (event.key === "Home") setActive(0);
    else if (event.key === "End") setActive(Math.max(0, last));
    else if (event.key === "Enter" && shown[active]) choose(shown[active]);
    else if (event.key === "Escape") close(true);
    else if (event.key === "Tab") { close(false); return; }
    else return;
    event.preventDefault();
    event.stopPropagation();
  }
  function onTriggerKey(event: ReactKeyboardEvent) {
    if (["ArrowDown", "ArrowUp", "Enter", " "].includes(event.key)) { event.preventDefault(); show(); }
  }

  const optionId = (index: number) => listId + "-o" + index;
  const panel = <div className={"ob-ref-panel" + (narrow ? " sheet" : "")} role={narrow ? "dialog" : undefined}
    aria-modal={narrow ? true : undefined} aria-label={narrow ? props.title : undefined}>
    {narrow && <div className="ob-ref-sheet-head"><span aria-hidden="true" className="ob-ref-grip" />
      <strong>{props.title}</strong>
      <button type="button" className="ob-icon-button" aria-label={t("drawer.close")} onClick={() => close(true)}>×</button></div>}
    {(searchable || narrow) && <div className="ob-ref-search">
      <input ref={search} type="search" value={query} placeholder={t("refdata.search_placeholder")} autoComplete="off" spellCheck={false}
        role="combobox" aria-expanded="true" aria-controls={listId} aria-autocomplete="list"
        aria-activedescendant={shown[active] ? optionId(active) : undefined} aria-label={props.title}
        onChange={(event) => { setQuery(event.target.value); setActive(0); }} onKeyDown={onListKey} />
    </div>}
    <ul id={listId} ref={list} role="listbox" aria-label={props.title} tabIndex={searchable || narrow ? -1 : 0}
      aria-activedescendant={!(searchable || narrow) && shown[active] ? optionId(active) : undefined}
      onKeyDown={searchable || narrow ? undefined : onListKey}>
      {shown.map((option, index) => <li key={option.code} id={optionId(index)} data-index={index} role="option"
        aria-selected={option.code === props.value} className={(index === active ? "active" : "") + (option.code === props.value ? " selected" : "")}
        onPointerMove={() => setActive(index)} onClick={() => choose(option)}>
        <RefIcon kind={props.icon} logo={option.logo} />
        <span className="ob-ref-label">{option.label}</span>
        <span className="ob-ref-code">{option.code}</span>
        <span className="ob-ref-check" aria-hidden="true">{option.code === props.value ? "✓" : ""}</span>
      </li>)}
      {!shown.length && <li className="ob-ref-empty" role="presentation">{t("refdata.no_match")}</li>}
    </ul>
    {props.hint && <p className="ob-ref-hint">{props.hint}</p>}
  </div>;

  return <div className={"ob-ref" + (open ? " open" : "")} ref={root}>
    <button type="button" id={props.id} ref={trigger} className="ob-ref-trigger" disabled={props.disabled}
      role="combobox" aria-haspopup="listbox" aria-expanded={open} aria-controls={open ? listId : undefined}
      aria-invalid={props.invalid} aria-describedby={props.describedBy}
      onClick={() => (open ? close(false) : show())} onKeyDown={onTriggerKey}>
      {props.value ? <>
        <RefIcon kind={props.icon} logo={selected?.logo ?? null} />
        <span className="ob-ref-value">{selected ? selected.display ?? selected.label : props.value}</span>
      </> : <span className="ob-ref-placeholder">{props.placeholder}</span>}
    </button>
    {open && (narrow ? <div className="ob-ref-layer"><div className="ob-scrim" onClick={() => close(true)} />{panel}</div> : panel)}
  </div>;
}

/** 国旗 / 币种图标；没有公开地址或加载失败时显示中性占位，不使用彩色底块。 */
function RefIcon({ kind, logo }: { kind: RefSelectProps["icon"]; logo: string | null }) {
  const [broken, setBroken] = useState(false);
  useEffect(() => setBroken(false), [logo]);
  if (kind === "none") return null;
  const className = "ob-ref-icon " + kind;
  if ((!logo || broken) && kind === "currency") return null;
  if (!logo || broken) return <span className={className + " placeholder"} aria-hidden="true" />;
  return <img className={className} src={logo} alt="" aria-hidden="true" loading="lazy" decoding="async" onError={() => setBroken(true)} />;
}

/** 选项无法加载：字段内一行说明 + 重试，不回退到任何内置列表。 */
export function OptionsUnavailable({ id, onRetry }: { id: string; onRetry?: () => void }) {
  const t = useT("onboarding");
  return <p className="ob-ref-unavailable" id={id} role="status">
    {t("refdata.unavailable")}
    {onRetry && <>{" · "}<button type="button" className="text-button" onClick={onRetry}>{t("refdata.retry")}</button></>}
  </p>;
}
