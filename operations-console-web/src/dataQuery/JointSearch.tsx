import { useMemo, useRef, useState } from 'react';
import { Button, Input, Popover } from 'antd';
import { useT } from '../i18n/react';
import type { EntryCheck, EntryKeyType } from './jointModel';

const recentKey = 'dataQuery.jointQuery.recentKeyTypes';
const recentLimit = 5;

/** 最近使用的入口类型（每个浏览器自己保存，只用于“常用”分组；读写失败时忽略）。 */
export function readRecent(): string[] {
  try {
    const value: unknown = JSON.parse(window.localStorage.getItem(recentKey) ?? '[]');
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string').slice(0, recentLimit) : [];
  } catch { return []; }
}
export function rememberRecent(code: string) {
  try { window.localStorage.setItem(recentKey, JSON.stringify([code, ...readRecent().filter(item => item !== code)].slice(0, recentLimit))); } catch { /* 忽略 */ }
}

/** “常用”：最近使用过的类型；没有记录时取排序靠前的前 5 个。其余为“更多”。 */
export function splitKeyTypes(keyTypes: readonly EntryKeyType[], recent: readonly string[]): { common: EntryKeyType[]; more: EntryKeyType[] } {
  const used = recent.map(code => keyTypes.find(item => item.keyTypeCode === code)).filter((item): item is EntryKeyType => !!item);
  const common = used.length ? used : keyTypes.slice(0, recentLimit);
  return { common, more: keyTypes.filter(item => !common.includes(item)) };
}

/**
 * 组合搜索框（原型 01、06、08）：单号类型 ▾ / 多行输入 / 查询。类型菜单分“常用 / 更多”、带输入提示、可搜索；
 * 格式不对的行在输入框内就地标出。Enter 查询，Shift+Enter 换行；执行中按钮变为“停止”。
 */
export function JointSearch({ keyTypes, keyType, onKeyType, text, onText, check, running, onSubmit, onStop, compact, serverLines }: {
  keyTypes: readonly EntryKeyType[]; keyType: EntryKeyType | undefined; onKeyType: (code: string) => void; text: string; onText: (text: string) => void;
  check: EntryCheck; running: boolean; onSubmit: () => void; onStop: () => void; compact: boolean; serverLines: ReadonlySet<number>;
}) {
  const t = useT('data-query');
  const [open, setOpen] = useState(false);
  const lines = text.split('\n');
  const invalid = new Set([...check.invalid, ...serverLines]);
  const hasError = invalid.size > 0;
  return <div className={`jq-search${compact ? ' is-compact' : ''}${hasError ? ' has-error' : ''}`}>
    <Popover open={open} onOpenChange={setOpen} trigger="click" placement="bottomLeft" arrow={false} classNames={{ root: 'jq-type-popover' }}
      content={<KeyTypeMenu keyTypes={keyTypes} selected={keyType?.keyTypeCode} onSelect={code => { onKeyType(code); setOpen(false); }} onClose={() => setOpen(false)} />}>
      <button type="button" className="jq-type" aria-haspopup="listbox" aria-expanded={open}>
        <span>{keyType?.keyTypeName ?? t('joint.search.chooseType')}</span><span className="jq-caret" aria-hidden>▾</span></button>
    </Popover>
    <div className="jq-input-row">
      <div className="jq-input">
        <div className="jq-input-backdrop" aria-hidden>{lines.map((line, index) => <div key={index} className={invalid.has(index + 1) ? 'is-invalid' : undefined}>
          <span className="jq-ghost">{line || '​'}</span>{invalid.has(index + 1) && <span className="jq-line-error">{t('joint.search.badLine')}</span>}</div>)}</div>
        <Input.TextArea variant="borderless" value={text} autoSize={{ minRows: 1, maxRows: 24 }} spellCheck={false} aria-invalid={hasError}
          aria-label={t('joint.search.inputLabel')} placeholder={t('joint.search.placeholder')} onChange={event => onText(event.target.value)}
          onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); if (!running) onSubmit(); } }} />
      </div>
      {running ? <Button className="jq-submit" danger onClick={onStop}>{t('joint.search.stop')}</Button>
        : <Button className="jq-submit" type="primary" onClick={onSubmit}>{t('joint.search.submit')}</Button>}
    </div>
  </div>;
}

function KeyTypeMenu({ keyTypes, selected, onSelect, onClose }: {
  keyTypes: readonly EntryKeyType[]; selected: string | undefined; onSelect: (code: string) => void; onClose: () => void;
}) {
  const t = useT('data-query');
  const [keyword, setKeyword] = useState('');
  const [active, setActive] = useState(0);
  const recent = useMemo(() => readRecent(), []);
  const needle = keyword.trim().toLowerCase();
  const matched = keyTypes.filter(item => !needle || item.keyTypeName.toLowerCase().includes(needle) || (item.inputHint ?? '').toLowerCase().includes(needle)
    || item.keyTypeCode.toLowerCase().includes(needle));
  const sections = needle ? [{ title: null, items: matched }] : (() => {
    const split = splitKeyTypes(keyTypes, recent);
    return [{ title: t('joint.search.common'), items: split.common }, { title: t('joint.search.more'), items: split.more }].filter(section => section.items.length);
  })();
  const flat = sections.flatMap(section => section.items);
  const list = useRef<HTMLDivElement | null>(null);
  return <div className="jq-type-menu" onKeyDown={event => {
    if (event.key === 'ArrowDown') { event.preventDefault(); setActive(index => Math.min(flat.length - 1, index + 1)); }
    else if (event.key === 'ArrowUp') { event.preventDefault(); setActive(index => Math.max(0, index - 1)); }
    else if (event.key === 'Enter' && flat[active]) { event.preventDefault(); onSelect(flat[active].keyTypeCode); }
    else if (event.key === 'Escape') { event.preventDefault(); onClose(); }
  }}>
    <Input autoFocus allowClear size="small" value={keyword} placeholder={t('joint.search.typeSearch')} onChange={event => { setKeyword(event.target.value); setActive(0); }} />
    <div className="jq-type-list" role="listbox" ref={list}>
      {flat.length === 0 && <div className="muted jq-type-empty">{t('joint.search.noType')}</div>}
      {sections.map((section, sectionIndex) => <div key={sectionIndex}>
        {section.title && <div className="jq-type-section">{section.title}</div>}
        {section.items.map(item => {
          const index = flat.indexOf(item);
          return <button type="button" role="option" key={item.keyTypeCode} aria-selected={item.keyTypeCode === selected}
            className={`jq-type-item${item.keyTypeCode === selected ? ' is-selected' : ''}${index === active ? ' is-active' : ''}`}
            onMouseEnter={() => setActive(index)} onClick={() => onSelect(item.keyTypeCode)}>
            <span>{item.keyTypeName}</span><span className="muted">{item.inputHint ?? ''}{item.keyTypeCode === selected ? ' ✓' : ''}</span>
          </button>;
        })}
      </div>)}
    </div>
  </div>;
}
