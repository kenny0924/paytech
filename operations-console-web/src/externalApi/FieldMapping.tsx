import { useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { App, AutoComplete, Button, Drawer, Input, Segmented, Select } from 'antd';
import { BindingInput, bindingText, emptyBinding } from './EditorParts';
import type { RegisteredFunction } from './editorModel';
import type { VariableOptions } from './flowModel';
import type { Binding, BindingKind } from './integrationApiModel';
import {
  isUnassigned, mappingRows, mappingStates, nextUnassigned, suggestMatches, typeHint, variableTree,
} from './mappingModel';
import type { MappingRow, MappingState, MappingTarget, VariableNode } from './mappingModel';
import { Mono } from './ui';

const kindLabels: Record<BindingKind, string> = { PATH: '路径', EXPRESSION: '表达式', FUNCTION: '函数' };
const groupLabels: Record<VariableNode['group'], string> = { input: '标准 API 输入', resources: '初始化结果', steps: '必经节点结果', handle: '受控句柄' };
/** 字段多于这个数量时显示搜索框。 */
const SEARCH_THRESHOLD = 8;

type ListFilter = 'ALL' | 'UNASSIGNED' | 'ATTENTION';

/** 一行的取值摘要：方式标签 + 等宽取值（超长截断、悬停看全文），或未赋值说明。 */
function ValueSummary({ target, state, binding }: { target: MappingTarget; state: MappingState; binding?: Binding }) {
  if (state === 'MISSING') return <span className="xmap-missing">未赋值</span>;
  if (state === 'OPTIONAL') return <span className="xapi-field-hint">未赋值 · 省略</span>;
  if (state === 'INHERITED') return <span className="xapi-field-hint">随上级对象赋值</span>;
  if (!binding) return null;
  const text = bindingText(binding);
  return <span className="xmap-value" title={text}>
    {binding.kind !== 'PATH' && <span className={`xmap-kind is-${binding.kind.toLowerCase()}`}>{kindLabels[binding.kind]}</span>}
    <code className={target.handle ? 'xmap-handle' : 'xapi-mono-ref'}>{target.handle ? `🔒 ${text}` : text}</code></span>;
}

/**
 * 右侧属性面板的紧凑字段列表：按分组与层级一行一个字段，可筛选未赋值、有错误，字段多时可搜索；固定值折叠在最后。
 * 点击一行交给调用方打开取值浮层；“字段映射”打开宽面板批量编辑。
 */
export function MappingList({ title, targets, bindings, attention = new Set(), readOnly, onEdit, onOpenMapping }: {
  title: string; targets: MappingTarget[]; bindings: Map<string, Binding>; attention?: Set<string>; readOnly: boolean;
  onEdit: (target: MappingTarget) => void; onOpenMapping?: () => void;
}) {
  const [filter, setFilter] = useState<ListFilter>('ALL');
  const [query, setQuery] = useState('');
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const [showFixed, setShowFixed] = useState(false);
  const states = useMemo(() => mappingStates(targets, bindings), [targets, bindings]);
  const needsAttention = (target: MappingTarget, state: MappingState) => state === 'MISSING' || attention.has(target.key);
  const count = (predicate: (target: MappingTarget, state: MappingState) => boolean) =>
    targets.filter(target => states.get(target.key) !== 'FIXED' && predicate(target, states.get(target.key)!)).length;
  const missing = count((_, state) => state === 'MISSING');
  const include = filter === 'UNASSIGNED' ? (_: MappingTarget, state: MappingState) => isUnassigned(state)
    : filter === 'ATTENTION' ? needsAttention : () => true;
  const rows = mappingRows(targets, states, include, query);
  const fixed = targets.filter(target => target.fixed);
  const toggle = (group: string) => setCollapsed(previous => {
    const next = new Set(previous); if (next.has(group)) next.delete(group); else next.add(group); return next;
  });
  return <div className="xmap-block">
    <div className="xmap-summary"><span>{title} {targets.length - fixed.length}</span>
      {missing > 0 && <span className="xmap-badge">必填未赋值 {missing}</span>}
      <span className="xmap-spacer" />
      {onOpenMapping && !readOnly && <Button size="small" className="xmap-open" onClick={onOpenMapping}>⤢ 字段映射</Button>}</div>
    <div className="xmap-tools">
      <Segmented size="small" value={filter} onChange={value => setFilter(value as ListFilter)} options={[
        { value: 'ALL', label: `全部 ${count(() => true)}` }, { value: 'UNASSIGNED', label: `未赋值 ${count((_, state) => isUnassigned(state))}` },
        { value: 'ATTENTION', label: `有错误 ${count(needsAttention)}` }]} />
      {targets.length > SEARCH_THRESHOLD && <Input size="small" allowClear placeholder="搜索字段" value={query} onChange={event => setQuery(event.target.value)} />}
    </div>
    <div className="xmap-list">
      {rows.length === 0 && <div className="xmap-empty">没有符合条件的字段</div>}
      {visibleRows(rows, collapsed).map(row => row.kind === 'group'
        ? <button type="button" key={`g:${row.group}`} className="xmap-group" onClick={() => toggle(row.group)}>
          <span className="xmap-chevron">{collapsed.has(row.group) ? '▸' : '▾'}</span>{row.group}<span className="xmap-count">{row.count}</span></button>
        : row.kind === 'object'
          ? <div key={`o:${row.group}:${row.path}`} className="xmap-row is-object" style={{ paddingLeft: 10 + row.depth * 14 }}>
            <span className="xmap-chevron">▾</span><Mono tone="muted">{row.name}</Mono><span className="xmap-type">OBJECT</span></div>
          : <FieldLine key={row.target.key} row={row} binding={bindings.get(row.target.key)} readOnly={readOnly}
            error={row.state === 'MISSING' || attention.has(row.target.key)} onClick={() => onEdit(row.target)} />)}
      {fixed.length > 0 && <>
        <button type="button" className="xmap-group is-fixed" onClick={() => setShowFixed(!showFixed)}>
          <span className="xmap-chevron">{showFixed ? '▾' : '▸'}</span>固定值 {fixed.length} 个
          {!showFixed && <span className="xmap-fixed-names">{fixed.map(target => `${target.group} ${target.path}`).join('、')}</span>}</button>
        {showFixed && fixed.map(target => <div key={target.key} className="xmap-row is-object">
          <Mono>{target.path}</Mono><span className="xmap-type">{target.dataType}</span><span className="xmap-spacer" />
          <span className="xapi-field-hint">协议固定值，在原子 API 中维护</span></div>)}
      </>}
    </div>
  </div>;
}

/** 折叠的分组隐藏其下的行（直到下一个分组）。 */
function visibleRows(rows: MappingRow[], collapsed: Set<string>): MappingRow[] {
  let hidden = false;
  return rows.filter(row => {
    if (row.kind === 'group') { hidden = collapsed.has(row.group); return true; }
    return !hidden;
  });
}

function FieldLine({ row, binding, readOnly, error, onClick }: {
  row: Extract<MappingRow, { kind: 'field' }>; binding?: Binding; readOnly: boolean; error: boolean; onClick: () => void;
}) {
  const content = <>
    <span className="xmap-name" style={{ paddingLeft: row.depth * 14 }}><Mono>{row.name}</Mono>
      {row.target.required && <span className="xmap-required">*</span>}<span className="xmap-type">{row.target.handle ? 'HANDLE' : row.target.dataType}</span></span>
    <ValueSummary target={row.target} state={row.state} binding={binding} /></>;
  const className = `xmap-row${error ? ' is-error' : ''}`;
  return readOnly ? <div className={className}>{content}</div>
    : <button type="button" className={`${className} is-button`} onClick={onClick}>{content}</button>;
}

type Draft = Map<string, Binding | undefined>;

/** 空路径或空表达式视为未赋值。 */
function normalized(binding: Binding | undefined): Binding | undefined {
  if (!binding) return undefined;
  if (binding.kind === 'PATH') return binding.path.trim() ? { kind: 'PATH', path: binding.path.trim() } : undefined;
  if (binding.kind === 'EXPRESSION') return binding.expression.trim() ? binding : undefined;
  return binding.function ? binding : undefined;
}

/**
 * 字段映射宽面板：左侧逐行就地编辑（路径自动补全带类型、表达式、函数），右侧可用变量树写入当前行；
 * Enter 跳到下一个未赋值字段；“按同名自动匹配”只生成建议，采纳后才写入。完成时把修改交给调用方写入页面草稿。
 */
export function MappingDrawer({ open, heading, subtitle, targets, bindings, variables, functions, onClose, onApply }: {
  open: boolean; heading: ReactNode; subtitle: string; targets: MappingTarget[]; bindings: Map<string, Binding>;
  variables: VariableOptions; functions: RegisteredFunction[];
  onClose: () => void; onApply: (changes: Map<string, Binding | undefined>) => void;
}) {
  return <Drawer open={open} width={1000} placement="right" destroyOnHidden closable={false} rootClassName="xmap-drawer"
    styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column' } }} onClose={onClose} keyboard={false}>
    {open && <MappingEditor heading={heading} subtitle={subtitle} targets={targets} bindings={bindings} variables={variables}
      functions={functions} onClose={onClose} onApply={onApply} />}
  </Drawer>;
}

function MappingEditor({ heading, subtitle, targets, bindings, variables, functions, onClose, onApply }: Omit<Parameters<typeof MappingDrawer>[0], 'open'>) {
  const { message, modal } = App.useApp();
  const [draft, setDraft] = useState<Draft>(() => new Map(targets.map(target => [target.key, bindings.get(target.key)])));
  const [active, setActive] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<Map<string, string>>(new Map());
  const [filter, setFilter] = useState<ListFilter>('ALL');
  const [query, setQuery] = useState('');
  const [varQuery, setVarQuery] = useState('');
  const inputs = useRef(new Map<string, { focus: () => void }>());
  const current = useMemo(() => {
    const map = new Map<string, Binding>();
    for (const [key, binding] of draft) { const value = normalized(binding); if (value) map.set(key, value); }
    return map;
  }, [draft]);
  const states = useMemo(() => mappingStates(targets, current), [targets, current]);
  const hintOf = (target: MappingTarget) => typeHint(target, current.get(target.key), variables);
  const attention = (target: MappingTarget) => !!hintOf(target) || suggestions.has(target.key);
  const include = filter === 'UNASSIGNED' ? (_: MappingTarget, state: MappingState) => isUnassigned(state)
    : filter === 'ATTENTION' ? attention : () => true;
  const rows = mappingRows(targets, states, include, query);
  const allRows = mappingRows(targets, states, () => true);
  const editable = targets.filter(target => !target.fixed);
  const changed = targets.filter(target => bindingText(normalized(draft.get(target.key))) !== bindingText(bindings.get(target.key))
    || normalized(draft.get(target.key))?.kind !== bindings.get(target.key)?.kind);
  const activeTarget = targets.find(target => target.key === active);
  const set = (key: string, binding: Binding | undefined) => setDraft(previous => new Map(previous).set(key, binding));
  function focus(key: string | null) {
    setActive(key);
    if (key) setTimeout(() => inputs.current.get(key)?.focus(), 0);
  }
  const advance = (key: string) => focus(nextUnassigned(allRows, key));
  function suggest() {
    const found = suggestMatches(targets, states, variables);
    setSuggestions(found);
    if (!found.size) void message.info('没有可匹配的同名变量');
    else void message.success(`找到 ${found.size} 个同名建议，逐条采纳后才会写入`);
  }
  function pickVariable(node: VariableNode) {
    if (!activeTarget || !node.insert) { void message.info('先点击左侧要赋值的字段'); return; }
    if ((node.group === 'handle') !== activeTarget.handle) {
      void message.warning(activeTarget.handle ? '受控句柄参数只能选择 credentials' : 'credentials 是受控句柄，只能给 PROTECTED_HANDLE 参数');
      return;
    }
    const existing = draft.get(activeTarget.key);
    if (existing?.kind === 'EXPRESSION') set(activeTarget.key, { kind: 'EXPRESSION', expression: `${existing.expression}${existing.expression ? ' ' : ''}${node.insert}` });
    else if (existing?.kind === 'FUNCTION') { void message.info('函数取值请在参数中选择变量'); return; }
    else set(activeTarget.key, { kind: 'PATH', path: node.insert });
    setTimeout(() => inputs.current.get(activeTarget.key)?.focus(), 0);
  }
  function close() {
    if (!changed.length) { onClose(); return; }
    modal.confirm({ title: '放弃本次映射修改？', content: `有 ${changed.length} 个字段的取值修改尚未应用到草稿。`, okText: '放弃', okButtonProps: { danger: true },
      cancelText: '继续编辑', onOk: onClose });
  }
  function finish() {
    if (changed.length) onApply(new Map(changed.map(target => [target.key, normalized(draft.get(target.key))])));
    onClose();
  }
  const missing = editable.filter(target => states.get(target.key) === 'MISSING').length;
  return <div className="xmap-editor" onKeyDown={event => { if (event.key === 'Escape' && !event.defaultPrevented) close(); }}>
    <header className="xmap-head"><div><h3>字段映射 · {heading}</h3><p>{subtitle}{missing ? ` · 必填未赋值 ${missing}` : ''}</p></div>
      <Button onClick={suggest}>按同名自动匹配</Button>
      <button type="button" className="xmap-close" aria-label="关闭" onClick={close}>✕</button></header>
    <div className="xmap-toolbar">
      <Segmented value={filter} onChange={value => setFilter(value as ListFilter)} options={[
        { value: 'ALL', label: `全部 ${editable.length}` },
        { value: 'UNASSIGNED', label: `未赋值 ${editable.filter(target => isUnassigned(states.get(target.key)!)).length}` },
        { value: 'ATTENTION', label: `有提示 ${editable.filter(attention).length}` }]} />
      <Input allowClear placeholder="搜索目标字段" value={query} onChange={event => setQuery(event.target.value)} style={{ width: 240 }} />
      <span className="xmap-spacer" /><span className="xapi-field-hint">Enter 跳到下一个未赋值字段 · Esc 关闭</span></div>
    <div className="xmap-body">
      <div className="xmap-table">
        <div className="xmap-thead"><span>目标字段</span><span>类型</span><span>取值</span></div>
        {rows.length === 0 && <div className="xmap-empty">没有符合条件的字段</div>}
        {rows.map(row => {
          if (row.kind === 'group') return <div key={`g:${row.group}`} className="xmap-tgroup">{row.group}</div>;
          if (row.kind === 'object') return <div key={`o:${row.group}:${row.path}`} className="xmap-trow is-object" style={{ paddingLeft: 12 + row.depth * 18 }}>
            <span className="xmap-chevron">▾</span><Mono tone="muted">{row.name}</Mono></div>;
          const target = row.target; const hint = hintOf(target); const suggestion = suggestions.get(target.key);
          return <div key={target.key} className={`xmap-trow${active === target.key ? ' is-active' : ''}`} onClick={() => setActive(target.key)}>
            <div className="xmap-tmain">
              <span className="xmap-tname" style={{ paddingLeft: row.depth * 18 }}><Mono>{row.name}</Mono>{target.required && <span className="xmap-required">*</span>}</span>
              <span className="xmap-ttype">{target.handle ? 'HANDLE' : target.dataType}</span>
              {suggestion && row.state !== 'ASSIGNED'
                ? <div className="xmap-suggest"><span className="xmap-suggest-tag">建议</span><code>{suggestion}</code><span className="xmap-spacer" />
                  <button type="button" className="xapi-link" onClick={() => { set(target.key, { kind: 'PATH', path: suggestion });
                    setSuggestions(previous => { const next = new Map(previous); next.delete(target.key); return next; }); }}>采纳</button>
                  <button type="button" className="xapi-link xapi-muted-link" onClick={() => setSuggestions(previous => {
                    const next = new Map(previous); next.delete(target.key); return next; })}>忽略</button></div>
                : row.state === 'INHERITED' && !draft.get(target.key)
                  ? <span className="xapi-field-hint xmap-inherit">随上级对象赋值<button type="button" className="xapi-link" onClick={() => { set(target.key, emptyBinding('PATH')); focus(target.key); }}>单独赋值</button></span>
                  : <MappingCell target={target} binding={draft.get(target.key)} variables={variables} functions={functions}
                    register={handle => { if (handle) inputs.current.set(target.key, handle); else inputs.current.delete(target.key); }}
                    onFocus={() => setActive(target.key)} onChange={binding => set(target.key, binding)} onEnter={() => advance(target.key)} />}
            </div>
            {hint && <div className="xmap-hint">⚠ {hint}，可改用函数转换</div>}
            {suggestion && row.state !== 'ASSIGNED' && <div className="xmap-note">按同名匹配得到，采纳后才会写入草稿</div>}
          </div>;
        })}
        {targets.some(target => target.fixed) && <div className="xmap-tgroup is-fixed">固定值 {targets.filter(target => target.fixed).length} 个（不可映射）
          <span>{targets.filter(target => target.fixed).map(target => `${target.group} ${target.path}`).join(' · ')}</span></div>}
      </div>
      <VariablePanel variables={variables} query={varQuery} onQuery={setVarQuery} active={activeTarget} onPick={pickVariable} />
    </div>
    <footer className="xmap-foot"><span className="xapi-field-hint">映射只修改页面草稿；关闭后在页面顶部“保存”统一提交。</span><span className="xmap-spacer" />
      <Button onClick={close}>放弃本次修改</Button><Button type="primary" onClick={finish}>完成{changed.length ? `（${changed.length}）` : ''}</Button></footer>
  </div>;
}

/** 一格取值：方式切换 + 路径自动补全（候选带分组与类型）/ 表达式 / 函数；受控句柄只能选 credentials 路径。 */
function MappingCell({ target, binding, variables, functions, register, onFocus, onChange, onEnter }: {
  target: MappingTarget; binding?: Binding; variables: VariableOptions; functions: RegisteredFunction[];
  register: (handle: { focus: () => void } | null) => void; onFocus: () => void; onChange: (binding: Binding | undefined) => void; onEnter: () => void;
}) {
  const kinds: BindingKind[] = target.handle ? ['PATH'] : ['PATH', 'EXPRESSION', 'FUNCTION'];
  const current = binding ?? emptyBinding('PATH');
  const [open, setOpen] = useState(false);
  const options = useMemo(() => {
    if (current.kind !== 'PATH') return [];
    const typed = current.path.trim().toLowerCase();
    const nodes = target.handle ? (variables.handlePaths ?? []).map(path => ({ insert: path, group: 'handle' as const, type: 'PROTECTED_HANDLE', label: `${path}*` }))
      : variableTree(variables, typed).filter(node => node.group !== 'handle' && node.insert)
        .map(node => ({ insert: node.insert!, group: node.group, type: node.type, label: node.label }));
    const groups = [...new Set(nodes.map(node => node.group))];
    return groups.map(group => ({ label: groupLabels[group], options: nodes.filter(node => node.group === group).slice(0, 30).map(node => ({
      value: node.insert, label: <span className="xmap-option"><code>{node.label}</code><span>{node.type ?? ''}</span></span> })) }));
  }, [current, target.handle, variables]);
  return <div className="xmap-cell">
    <Select className="xmap-mode" value={current.kind} disabled={kinds.length === 1} popupMatchSelectWidth={false}
      options={kinds.map(kind => ({ value: kind, label: kindLabels[kind] }))} onChange={(kind: BindingKind) => onChange(emptyBinding(kind))} />
    {current.kind === 'PATH' && <AutoComplete className="xmap-input xapi-mono-input" value={current.path} options={options} open={open}
      onOpenChange={setOpen} ref={ref => register(ref)} onFocus={onFocus} popupMatchSelectWidth={420}
      placeholder={target.handle ? 'resources.<初始化项>.credentials.<名称>' : '输入或选择 input. / resources. / steps.'}
      onChange={path => onChange({ kind: 'PATH', path })}
      onSelect={(path: string) => { onChange({ kind: 'PATH', path }); if (!path.endsWith('.')) { setOpen(false); onEnter(); } }}
      onKeyDown={event => { if (event.key === 'Enter' && !open) { event.preventDefault(); onEnter(); } if (event.key === 'Escape' && open) event.preventDefault(); }} />}
    {current.kind === 'EXPRESSION' && <Input className="xmap-input xapi-mono-input" value={current.expression} ref={ref => register(ref)} onFocus={onFocus}
      placeholder="'NI-' + input.paymentContext.attemptId" maxLength={4096}
      onChange={event => onChange({ kind: 'EXPRESSION', expression: event.target.value })} onPressEnter={onEnter} />}
    {current.kind === 'FUNCTION' && <div className="xmap-function" onFocus={onFocus}>
      <BindingInput value={current} kinds={['FUNCTION']} paths={variables.paths} functions={functions} onChange={onChange} /></div>}
    {binding && <button type="button" className="xmap-clear" title="清除取值" onClick={() => onChange(undefined)}>✕</button>}
  </div>;
}

/** 右侧可用变量：分组树（带类型）、搜索；点击写入当前行。 */
function VariablePanel({ variables, query, onQuery, active, onPick }: {
  variables: VariableOptions; query: string; onQuery: (query: string) => void; active?: MappingTarget; onPick: (node: VariableNode) => void;
}) {
  const nodes = variableTree(variables, query);
  let lastGroup: string | null = null;
  return <aside className="xmap-vars">
    <div className="xmap-vars-head"><span>可用变量</span>
      <span className="xmap-target">{active ? <>点击写入 <code>{active.path}</code></> : '先点击左侧字段'}</span></div>
    <Input size="small" allowClear placeholder="搜索变量" value={query} onChange={event => onQuery(event.target.value)} />
    <div className="xmap-vars-list">
      {nodes.length === 0 && <span className="xapi-field-hint">没有匹配的变量</span>}
      {nodes.map(node => {
        const header = node.group !== lastGroup ? <div className="xmap-vars-group">{groupLabels[node.group]}</div> : null;
        lastGroup = node.group;
        const disabled = !node.insert || (active ? (node.group === 'handle') !== active.handle : false);
        return <div key={node.key}>{header}
          <button type="button" className={`xmap-var${node.folder ? ' is-folder' : ''}${node.group === 'handle' ? ' is-handle' : ''}`}
            style={{ paddingLeft: 6 + node.depth * 14 }} disabled={disabled} onClick={() => onPick(node)}>
            <code>{node.folder ? '▾ ' : ''}{node.group === 'handle' ? '🔒 ' : ''}{node.label}</code>
            <span className={node.runtime ? 'xmap-runtime' : ''}>{node.runtime ? '运行时补全' : node.type ?? ''}</span></button></div>;
      })}
      {nodes.some(node => node.runtime) && <span className="xapi-field-hint">初始化结果的参数名在运行时从渠道账号读取，选中后补全名称。</span>}
      {!query && !variables.paths.some(path => path.startsWith('steps.')) && <><div className="xmap-vars-group">必经节点结果</div><span className="xapi-field-hint">无</span></>}
      {variables.unavailableSteps.length > 0 && <span className="xapi-field-hint">不在所有路径上、不能读取：{variables.unavailableSteps.join('、')}</span>}
    </div>
  </aside>;
}

