import { forwardRef, useMemo, useRef } from 'react';
import type { KeyboardEvent } from 'react';
import { highlight } from './icu';
import type { IcuIssue } from './icu';

/** 只读高亮（英文基准）：参数蓝、类型与括号灰、分支名橙（原型 01）。 */
export function IcuText({ text, className }: { text: string; className?: string }) {
  const segments = useMemo(() => highlight(text), [text]);
  return <code className={`l10n-icu${className ? ' ' + className : ''}`}>
    {segments.map((segment, index) => <span key={index} className={`icu-${segment.kind}`}>{segment.text}</span>)}
  </code>;
}

/**
 * 带高亮的译文编辑器：透明 textarea 叠在高亮层上，字体与内边距一致；问题范围加红色波浪线（原型 02）。
 * ⌘↵ / Ctrl+↵ 由调用方处理（onKeyDown）。
 */
export const IcuEditor = forwardRef<HTMLTextAreaElement, {
  value: string; onChange: (value: string) => void; issues: IcuIssue[]; invalid: boolean; readOnly?: boolean;
  placeholder?: string; label: string; onKeyDown?: (event: KeyboardEvent<HTMLTextAreaElement>) => void; lang?: string;
}>(function IcuEditor({ value, onChange, issues, invalid, readOnly, placeholder, label, onKeyDown, lang }, ref) {
  const layer = useRef<HTMLPreElement>(null);
  const segments = useMemo(() => highlight(value, issues), [value, issues]);
  return <div className={`l10n-editor${invalid ? ' is-invalid' : ''}${readOnly ? ' is-readonly' : ''}`}>
    <pre ref={layer} className="l10n-editor-layer l10n-icu" aria-hidden="true">
      {segments.map((segment, index) => <span key={index} className={`icu-${segment.kind}${segment.error ? ' icu-error' : ''}`}>{segment.text}</span>)}
      {/* 末尾换行需要占位，否则高亮层比 textarea 少一行。 */}{'\n'}
    </pre>
    <textarea ref={ref} className="l10n-editor-input" value={value} spellCheck={false} readOnly={readOnly} placeholder={placeholder}
      aria-label={label} aria-invalid={invalid} lang={lang}
      onChange={event => onChange(event.target.value)} onKeyDown={onKeyDown}
      onScroll={event => { if (layer.current) { layer.current.scrollTop = event.currentTarget.scrollTop; layer.current.scrollLeft = event.currentTarget.scrollLeft; } }} />
  </div>;
});
