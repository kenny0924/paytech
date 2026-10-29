import { useEffect, useId, useRef, useState } from "react";
import type { KeyboardEvent as ReactKeyboardEvent } from "react";
import { useT } from "../i18n/react.tsx";
import { useNarrow } from "./RefSelect.tsx";
import { joinCodeList, splitCodeList } from "./schema.ts";

/**
 * 目录多选（MULTI_SELECT）：已选项为可移除的标签，点击控件弹出带勾选框的列表；每项显示本地化名称与等宽编码
 * <li>值是规范化逗号串（joinCodeList）；标签按目录顺序显示，不在目录中的已存代码（如已停用）按编码显示，可移除，不自动清空。</li>
 * <li>桌面在字段下方弹出，勾选不关闭；窄屏（≤640px）改为底部面板，带已选数量与"完成"。</li>
 * <li>键盘：↑↓ 移动、Space / Enter 勾选、Home / End 跳到首尾、Esc 关闭并把焦点还给触发按钮；触发按钮上 Backspace 移除最后一个标签。</li>
 */
export type MultiOption = { code: string; label: string };
export type MultiSelectProps = {
  id: string; value: string; options: MultiOption[]; disabled: boolean; invalid: boolean; describedBy?: string;
  /** 面板标题（窄屏底部面板）与列表的可访问名称。 */
  title: string;
  placeholder: string;
  onChange: (value: string) => void;
  onBlur: () => void;
};

export function MultiSelect(props: MultiSelectProps) {
  const t = useT("onboarding"), listId = useId(), narrow = useNarrow();
  const [open, setOpen] = useState(false), [active, setActive] = useState(0);
  const root = useRef<HTMLDivElement>(null), trigger = useRef<HTMLButtonElement>(null), list = useRef<HTMLUListElement>(null);
  const selected = new Set(splitCodeList(props.value));
  const chips: MultiOption[] = [...props.options.filter((option) => selected.has(option.code)),
    ...[...selected].filter((code) => !props.options.some((option) => option.code === code)).map((code) => ({ code, label: code }))];

  function show() {
    if (props.disabled) return;
    setActive(Math.max(0, props.options.findIndex((option) => selected.has(option.code))));
    setOpen(true);
  }
  function close(returnFocus: boolean) {
    setOpen(false);
    props.onBlur();
    if (returnFocus) trigger.current?.focus();
  }
  function toggle(code: string) {
    const next = new Set(selected);
    if (!next.delete(code)) next.add(code);
    props.onChange(joinCodeList(next));
  }
  function remove(code: string) {
    selected.delete(code);
    props.onChange(joinCodeList(selected));
    props.onBlur();
    trigger.current?.focus();
  }

  // 打开后焦点进入列表；窄屏锁定页面滚动；点击外部关闭。
  useEffect(() => {
    if (!open) return;
    list.current?.focus({ preventScroll: true });
    if (narrow) document.body.classList.add("ob-locked");
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) close(false); };
    document.addEventListener("pointerdown", outside);
    return () => { document.removeEventListener("pointerdown", outside); document.body.classList.remove("ob-locked"); };
  }, [open, narrow]);
  useEffect(() => {
    if (open) list.current?.querySelector<HTMLElement>("[data-index='" + active + "']")?.scrollIntoView({ block: "nearest" });
  }, [active, open]);

  function onListKey(event: ReactKeyboardEvent) {
    const last = props.options.length - 1;
    if (event.key === "ArrowDown") setActive((index) => Math.min(last, index + 1));
    else if (event.key === "ArrowUp") setActive((index) => Math.max(0, index - 1));
    else if (event.key === "Home") setActive(0);
    else if (event.key === "End") setActive(Math.max(0, last));
    else if ((event.key === "Enter" || event.key === " ") && props.options[active]) toggle(props.options[active].code);
    else if (event.key === "Escape") close(true);
    else if (event.key === "Tab") { close(false); return; }
    else return;
    event.preventDefault();
    event.stopPropagation();
  }
  function onTriggerKey(event: ReactKeyboardEvent) {
    if (["ArrowDown", "ArrowUp", "Enter", " "].includes(event.key)) { event.preventDefault(); show(); }
    else if (event.key === "Backspace" && chips.length && !props.disabled) { event.preventDefault(); remove(chips[chips.length - 1].code); }
  }

  const optionId = (index: number) => listId + "-o" + index;
  const count = t("form.multi_select_count", { count: selected.size });
  const panel = <div className={"ob-ref-panel ob-multi-panel" + (narrow ? " sheet" : "")} role={narrow ? "dialog" : undefined}
    aria-modal={narrow ? true : undefined} aria-label={narrow ? props.title : undefined}>
    {narrow && <div className="ob-ref-sheet-head"><span aria-hidden="true" className="ob-ref-grip" />
      <strong>{props.title}</strong><span className="ob-multi-count" aria-live="polite">{count}</span></div>}
    <ul id={listId} ref={list} role="listbox" aria-multiselectable="true" aria-label={props.title} tabIndex={0}
      aria-activedescendant={props.options[active] ? optionId(active) : undefined} onKeyDown={onListKey}>
      {props.options.map((option, index) => <li key={option.code} id={optionId(index)} data-index={index} role="option"
        aria-selected={selected.has(option.code)} className={(index === active ? "active" : "") + (selected.has(option.code) ? " checked" : "")}
        onPointerMove={() => setActive(index)} onClick={() => { setActive(index); toggle(option.code); }}>
        <span className="ob-multi-box" aria-hidden="true" />
        <span className="ob-ref-label">{option.label}</span>
        <span className="ob-ref-code">{option.code}</span>
      </li>)}
    </ul>
    {narrow && <div className="ob-multi-done"><button type="button" className="primary full" onClick={() => close(true)}>{t("form.multi_select_done")}</button></div>}
  </div>;

  return <div className={"ob-ref ob-multi" + (open ? " open" : "")} ref={root}>
    <div className={"ob-multi-control" + (props.disabled ? " disabled" : "") + (props.invalid ? " invalid" : "")}
      onClick={(event) => { if (event.target === event.currentTarget) { if (open) close(false); else show(); } }}>
      {chips.map((chip) => <span className="ob-chip" key={chip.code}>
        <span className="ob-chip-label">{chip.label}</span>
        {!props.disabled && <button type="button" className="ob-chip-remove" aria-label={t("form.remove_option", { label: chip.label })}
          onClick={(event) => { event.stopPropagation(); remove(chip.code); }}>×</button>}
      </span>)}
      <button type="button" id={props.id} ref={trigger} className="ob-multi-trigger" disabled={props.disabled}
        role="combobox" aria-haspopup="listbox" aria-expanded={open} aria-controls={open ? listId : undefined}
        aria-invalid={props.invalid} aria-describedby={props.describedBy}
        onClick={() => (open ? close(false) : show())} onKeyDown={onTriggerKey}>
        {chips.length ? <span className="sr-only">{count}</span> : <span className="ob-ref-placeholder">{props.placeholder}</span>}
      </button>
    </div>
    {open && (narrow ? <div className="ob-ref-layer"><div className="ob-scrim" onClick={() => close(true)} />{panel}</div> : panel)}
  </div>;
}
