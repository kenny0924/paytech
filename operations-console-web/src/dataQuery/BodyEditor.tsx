import { useEffect, useRef } from 'react';
import { autocompletion, completionKeymap } from '@codemirror/autocomplete';
import type { CompletionContext, CompletionResult } from '@codemirror/autocomplete';
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands';
import { MySQL, sql, SQLDialect } from '@codemirror/lang-sql';
import { defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { lintGutter, setDiagnostics } from '@codemirror/lint';
import { Compartment, EditorSelection, EditorState, RangeSetBuilder } from '@codemirror/state';
import { Decoration, EditorView, highlightActiveLine, keymap, lineNumbers, ViewPlugin } from '@codemirror/view';
import type { DecorationSet, ViewUpdate } from '@codemirror/view';
import type { CompletionWord, EditorDiagnostic } from './templateModel';

/** MySQL 方言，但关闭 # 行注释：模板值标记 #{…} 以 # 开头，不能被当成注释。 */
const templateSql = SQLDialect.define({ ...MySQL.spec, hashComments: false });

const marks = {
  comment: Decoration.mark({ class: 'dq-tpl-comment' }),
  tag: Decoration.mark({ class: 'dq-tpl-tag' }),
  value: Decoration.mark({ class: 'dq-tpl-value' }),
  fragment: Decoration.mark({ class: 'dq-tpl-fragment' }),
};
/** 模板标记：注释 {{!-- --}}、标签 {{…}}、值 #{…}、片段 @{…}；反斜杠转义的标记原样输出，不着色。 */
const markPattern = /\\(?:#\{|@\{|\{\{)|\{\{!--[\s\S]*?--\}\}|\{\{[^}]*\}\}|#\{[^}\n]*\}|@\{[^}\n]*\}/g;

function templateMarks(view: EditorView): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>();
  const text = view.state.doc.toString();
  for (const match of text.matchAll(markPattern)) {
    const token = match[0]; const from = match.index;
    if (token.startsWith('\\')) continue;
    const mark = token.startsWith('{{!--') ? marks.comment : token.startsWith('{{') ? marks.tag : token.startsWith('#') ? marks.value : marks.fragment;
    builder.add(from, from + token.length, mark);
  }
  return builder.finish();
}

const templateHighlighter = ViewPlugin.fromClass(class {
  decorations: DecorationSet;
  constructor(view: EditorView) { this.decorations = templateMarks(view); }
  update(update: ViewUpdate) { if (update.docChanged) this.decorations = templateMarks(update.view); }
}, { decorations: plugin => plugin.decorations });

/** 补全：#{ 后补值参数与系统参数，@{ 后补片段名，{{ 后补标签。 */
function templateCompletion(words: () => readonly CompletionWord[]) {
  return (context: CompletionContext): CompletionResult | null => {
    const before = context.matchBefore(/(#\{|@\{|\{\{)[A-Za-z0-9_.#/]*$/);
    if (!before) return null;
    const opener = before.text.slice(0, 2);
    const kinds = opener === '#{' ? ['value', 'system'] : opener === '@{' ? ['fragment'] : ['tag'];
    return {
      from: before.from + 2,
      options: words().filter(word => kinds.includes(word.kind)).map(word => ({
        label: word.label, detail: word.detail, type: word.kind === 'tag' ? 'keyword' : word.kind === 'fragment' ? 'constant' : 'variable',
        apply: opener === '{{' ? `${word.label}}}` : `${word.label}}`,
      })),
      validFor: /^[A-Za-z0-9_.#/]*$/,
    };
  };
}

export interface BodyEditorHandle { focus: (line: number, column: number) => void }

/**
 * 模板正文编辑器（CodeMirror 6）：SQL 高亮叠加模板标记分色；服务端问题显示为下划线、行号旁标记与悬浮提示；
 * 参数名 / 片段名 / 标签自动补全。只做展示与输入，语法校验以服务端结果为准。
 */
export function BodyEditor({ value, onChange, diagnostics, completions, readOnly, onReady }: {
  value: string; onChange: (value: string) => void; diagnostics: readonly EditorDiagnostic[]; completions: readonly CompletionWord[];
  readOnly: boolean; onReady?: (handle: BodyEditorHandle) => void;
}) {
  const host = useRef<HTMLDivElement>(null);
  const view = useRef<EditorView | null>(null);
  const readOnlyCompartment = useRef(new Compartment());
  const words = useRef(completions); words.current = completions;
  const change = useRef(onChange); change.current = onChange;

  useEffect(() => {
    const editor = new EditorView({
      parent: host.current!,
      state: EditorState.create({
        doc: value,
        extensions: [
          lineNumbers(), lintGutter(), highlightActiveLine(), history(), EditorView.lineWrapping,
          keymap.of([...defaultKeymap, ...historyKeymap, ...completionKeymap]),
          sql({ dialect: templateSql, upperCaseKeywords: true }), syntaxHighlighting(defaultHighlightStyle), templateHighlighter,
          autocompletion({ override: [templateCompletion(() => words.current)], activateOnTyping: true }),
          readOnlyCompartment.current.of(EditorState.readOnly.of(readOnly)),
          EditorView.updateListener.of(update => { if (update.docChanged) change.current(update.state.doc.toString()); }),
        ],
      }),
    });
    view.current = editor;
    onReady?.({ focus: (line, column) => {
      const doc = editor.state.doc;
      const target = doc.line(Math.min(Math.max(line, 1), doc.lines));
      const position = Math.min(target.from + Math.max(column - 1, 0), target.to);
      editor.dispatch({ selection: EditorSelection.cursor(position), effects: EditorView.scrollIntoView(position, { y: 'center' }) });
      editor.focus();
    } });
    return () => { editor.destroy(); view.current = null; };
    // 编辑器只创建一次；外部值、只读与诊断通过下面的 effect 同步。
  }, []);

  useEffect(() => {
    const editor = view.current;
    if (!editor || editor.state.doc.toString() === value) return;
    editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: value } });
  }, [value]);

  useEffect(() => {
    view.current?.dispatch({ effects: readOnlyCompartment.current.reconfigure(EditorState.readOnly.of(readOnly)) });
  }, [readOnly]);

  useEffect(() => {
    const editor = view.current;
    if (!editor) return;
    const length = editor.state.doc.length;
    editor.dispatch(setDiagnostics(editor.state, diagnostics.map(item => {
      const from = Math.min(item.from, length);
      return { from, to: Math.min(Math.max(item.to, from), length), severity: item.severity, message: item.message };
    })));
  }, [diagnostics]);

  return <div ref={host} className="dq-code" aria-label="模板正文" />;
}
