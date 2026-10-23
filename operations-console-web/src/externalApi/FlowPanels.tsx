import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { AutoComplete, Button, Input, Modal, Segmented, Select, Switch } from 'antd';
import { BindingInput, BindingView, emptyBinding, kindLabel } from './EditorParts';
import type { RegisteredFunction } from './editorModel';
import {
  allIds, normalizeRequestKey, outgoing, renameVertex, removeVertex, reorderOutgoing, statusCondition, variableOptions,
} from './flowModel';
import type { ApiContract, ExecuteStatus, Flow, FlowEditorContext, FlowEdge, FlowReturn, Initializer, VariableOptions } from './flowModel';
import type { Binding, BindingKind } from './integrationApiModel';
import type { FlowSelection } from './FlowCanvas';
import { Mono, Note, StatusTag, lockMark } from './ui';
import { MappingDrawer, MappingList } from './FieldMapping';
import type { MappingTarget } from './mappingModel';
import { tOr, translator } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { richText } from '../i18n/richText';

const idPattern = /^[a-z][A-Za-z0-9]{0,63}$/;
const statuses: ExecuteStatus[] = ['SUCCESS', 'FAIL', 'ERROR'];
const tPanels = translator('channel');
/** 注册函数的显示名（channel.flow.function.<编码>），用于下拉与标题；未登记的函数用回退值。 */
const functionName = (t: Translate, code: string, fallback: string) => tOr(t, `flow.function.${code}`, fallback);
const requiredWord = (t: Translate, required: boolean) => (required ? t('form.required') : t('script.optional'));

export interface PanelProps {
  flow: Flow; context: FlowEditorContext; functions: RegisteredFunction[]; readOnly: boolean; standardApiCode: string;
  onChange: (flow: Flow) => void; onSelect: (selection: FlowSelection) => void;
}

/** 标识：失焦或回车时生效；格式不符或与其他标识重复时不提交并提示。 */
function IdField({ flow, value, readOnly, onRename }: { flow: Flow; value: string; readOnly: boolean; onRename: (next: string) => void }) {
  const t = useT('channel');
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  const taken = draft !== value && allIds(flow).has(draft);
  const invalid = !idPattern.test(draft) || taken;
  const commit = () => { if (draft !== value && !invalid) onRename(draft); else setDraft(value); };
  if (readOnly) return null;
  return <div className="xflow-field"><span className="xapi-field-label">{t('flow.id.label')}</span>
    <Input size="small" value={draft} maxLength={64} className="xapi-mono-input" status={invalid ? 'error' : undefined}
      onChange={event => setDraft(event.target.value)} onBlur={commit} onPressEnter={commit} />
    <span className="xapi-field-hint">{invalid ? (taken ? t('flow.id.taken') : t('script.paramNamePattern')) : t('flow.id.hint')}</span></div>;
}

/** 左栏：初始化项列表与“添加节点”组件面板。 */
export function FlowSidebar({ flow, context, functions, readOnly, selection, onChange, onSelect, onAddNode, onAddReturn }: PanelProps & {
  selection: FlowSelection; onAddNode: (apiCode: string) => void; onAddReturn: (status: ExecuteStatus) => void;
}) {
  const t = useT('channel');
  const [picking, setPicking] = useState(false);
  const initializerFunctions = functions.filter(item => item.kind === 'INITIALIZER');
  function addInitializer() {
    const fn = initializerFunctions[0];
    if (!fn) return;
    const used = allIds(flow); let id = 'channelAccount';
    for (let index = 2; used.has(id); index++) id = `channelAccount${index}`;
    onChange({ ...flow, initializers: [...flow.initializers, { id, function: fn.functionCode, args: {}, required: true }] });
    onSelect({ kind: 'initializer', id });
  }
  const palette: { key: string; title: ReactNode; desc: string; onClick: () => void; disabled?: boolean }[] = [
    { key: 'api', title: t('flow.palette.api'), desc: context.apis.length ? t('flow.palette.apiDesc') : t('flow.palette.noApis'),
      onClick: () => setPicking(true), disabled: !context.apis.length },
    { key: 'SUCCESS', title: <><StatusTag status="SUCCESS" /> {t('flow.kind.return')}</>, desc: t('flow.palette.success'), onClick: () => onAddReturn('SUCCESS') },
    { key: 'FAIL', title: <><StatusTag status="FAIL" /> {t('flow.kind.return')}</>, desc: t('flow.palette.fail'), onClick: () => onAddReturn('FAIL') },
    { key: 'ERROR', title: <><StatusTag status="ERROR" /> {t('flow.kind.return')}</>, desc: t('flow.errorExit'), onClick: () => onAddReturn('ERROR') },
  ];
  return <aside className="xflow-sidebar">
    <h3>{t('flow.sidebar.initializers')}</h3>
    <p className="xapi-field-hint">{t('flow.sidebar.initializersHint')}</p>
    {flow.initializers.length === 0 && <span className="xapi-field-hint">{t('flow.sidebar.noInitializers')}</span>}
    {flow.initializers.map(item => <button type="button" key={item.id}
      className={`xflow-init${selection?.kind === 'initializer' && selection.id === item.id ? ' is-selected' : ''}`}
      onClick={() => onSelect({ kind: 'initializer', id: item.id })}>
      <span className="xflow-init-head"><code>{item.id}</code>{item.required && <span className="xflow-required">{t('flow.initializer.required')}</span>}</span>
      <code className="xapi-mono-muted">{item.function}</code>
      <code className="xapi-mono-muted">→ resources.{item.id}</code></button>)}
    {!readOnly && <button type="button" className="xapi-link xapi-link-add" disabled={!initializerFunctions.length} onClick={addInitializer}>{t('flow.sidebar.addInitializer')}</button>}
    {!readOnly && <>
      <div className="xflow-divider" />
      <h3>{t('flow.sidebar.addNode')}</h3>
      {palette.map(item => <button type="button" key={item.key} className="xflow-palette" disabled={item.disabled} onClick={item.onClick}>
        <span>{item.title}</span><span className="xapi-field-hint">{item.desc}</span></button>)}
    </>}
    {picking && <Modal open title={t('flow.sidebar.addApiNode')} footer={null} onCancel={() => setPicking(false)}>
      <div className="xapi-choice-list">{context.apis.map(api => <button type="button" key={api.apiCode} className="xapi-choice"
        onClick={() => { setPicking(false); onAddNode(api.apiCode); }}><Mono>{api.apiCode}</Mono><span>{api.apiName}</span></button>)}</div>
    </Modal>}
  </aside>;
}

/** 右栏外框：标题、内容与底部危险操作。 */
function Side({ title, extra, children, footer }: { title: ReactNode; extra?: ReactNode; children: ReactNode; footer?: ReactNode }) {
  return <aside className="xflow-side"><div className="xflow-side-head"><h3>{title}</h3>{extra}</div>
    <div className="xflow-side-body">{children}</div>{footer && <div className="xflow-side-foot">{footer}</div>}</aside>;
}

export function EmptySide() {
  const t = useT('channel');
  return <Side title={t('flow.side.properties')}><p className="xapi-field-hint">{t('flow.side.emptyHint')}</p></Side>;
}

export function StartSide({ flow, standardApiCode }: { flow: Flow; standardApiCode: string }) {
  const t = useT('channel');
  return <Side title={t('flow.start', { api: standardApiCode })}>
    <p>{t('flow.side.entryNode')}{flow.entry ? <Mono>{flow.entry}</Mono> : <span className="xapi-field-hint">{t('flow.side.notSet')}</span>}</p>
    <p className="xapi-field-hint">{t('flow.side.entryHint')}</p></Side>;
}

/** 取值浮层的打开参数。 */
export interface ValueTarget {
  title: string; binding?: Binding; kinds: BindingKind[]; required: boolean; variables: VariableOptions; handleOnly?: boolean;
  onApply: (binding: Binding | undefined) => void;
}

/**
 * 取值浮层：路径 / 表达式 / 函数分段编辑，右侧“可用变量”树；点击变量写入路径或追加到表达式。
 * 受控句柄参数只能以路径选择 credentials；不在所有路径上的节点单独列出，不能读取。
 */
export function ValuePopover({ target, functions, onClose }: { target: ValueTarget; functions: RegisteredFunction[]; onClose: () => void }) {
  const t = useT('channel'), tCommon = useT('common');
  const [binding, setBinding] = useState<Binding>(target.binding ?? emptyBinding(target.kinds[0]));
  const pure = functions.filter(item => item.kind === 'PURE');
  const groups = [
    { root: 'input', title: t('mapping.varGroup.input'), items: target.variables.paths.filter(path => path.startsWith('input.')) },
    { root: 'resources', title: t('mapping.varGroup.resources'), items: target.variables.paths.filter(path => path.startsWith('resources.')) },
    { root: 'steps', title: t('mapping.varGroup.steps'), items: target.variables.paths.filter(path => path.startsWith('steps.')) },
  ];
  function pick(path: string) {
    if (binding.kind === 'EXPRESSION') setBinding({ kind: 'EXPRESSION', expression: `${binding.expression}${binding.expression ? ' ' : ''}${path}` });
    else setBinding({ kind: 'PATH', path });
  }
  const declared = binding.kind === 'FUNCTION' ? pure.find(item => item.functionCode === binding.function) : undefined;
  const filled = binding.kind === 'PATH' ? !!binding.path.trim() : binding.kind === 'EXPRESSION' ? !!binding.expression.trim() : !!binding.function;
  return <div className="xflow-popover" role="dialog" aria-label={target.title}>
    <div className="xflow-popover-head"><h3>{target.title}</h3>
      {target.kinds.length > 1 && <Segmented size="small" value={binding.kind} options={target.kinds.map(kind => ({ value: kind, label: kindLabel(t, kind) }))}
        onChange={kind => setBinding(emptyBinding(kind as BindingKind))} />}</div>
    <div className="xflow-popover-body">
      <div className="xflow-popover-editor">
        {binding.kind === 'PATH' && <>
          {target.handleOnly ? <AutoComplete value={binding.path} className="xapi-mono-input" placeholder={t('mapping.handlePlaceholder')}
            options={(target.variables.handlePaths ?? []).map(path => ({ value: path }))} onChange={path => setBinding({ kind: 'PATH', path })} />
            : <Input value={binding.path} className="xapi-mono-input" placeholder={t('flow.value.pathPlaceholder')}
              onChange={event => setBinding({ kind: 'PATH', path: event.target.value })} />}
          <span className="xapi-field-hint">{t('flow.value.pathHint')}</span></>}
        {binding.kind === 'EXPRESSION' && <>
          <Input.TextArea value={binding.expression} autoSize={{ minRows: 4 }} className="xapi-mono-input" maxLength={4096}
            placeholder="'NI-' + input.paymentContext.attemptId" onChange={event => setBinding({ kind: 'EXPRESSION', expression: event.target.value })} />
          <span className="xapi-field-hint">{t('flow.value.expressionHint')}</span></>}
        {binding.kind === 'FUNCTION' && <>
          <Select value={binding.function || undefined} placeholder={t('flow.value.pickFunction')} className="xapi-mono-input"
            options={pure.map(item => ({ value: item.functionCode, label: `${item.functionCode} · ${functionName(t, item.functionCode, item.returns)}` }))}
            onChange={(code: string) => setBinding({ kind: 'FUNCTION', function: code, args: {}, result: 'result' })} />
          {declared?.params.map(param => <div key={param.name} className="xflow-field"><span className="xapi-field-label"><Mono>{param.name}</Mono>
            <span className="xapi-field-hint"> {param.dataType} · {requiredWord(t, param.required)}</span></span>
            <BindingInput value={binding.args[param.name]} kinds={['PATH', 'EXPRESSION']} paths={target.variables.paths}
              onChange={next => setBinding({ ...binding, args: { ...binding.args, [param.name]: next } })} /></div>)}
          {declared && <div className="xflow-field"><span className="xapi-field-label">{t('flow.value.resultExpression')}</span>
            <Input value={binding.result} className="xapi-mono-input" onChange={event => setBinding({ ...binding, result: event.target.value })} /></div>}
        </>}
      </div>
      <div className="xflow-popover-vars"><span className="xapi-field-label">{t('mapping.variables')}</span>
        {target.handleOnly ? <>{(target.variables.handlePaths ?? []).map(path => <button type="button" key={path} className="xflow-var"
          onClick={() => setBinding({ kind: 'PATH', path })}><span className="xapi-handle">{lockMark} {path}…</span></button>)}
          <span className="xapi-field-hint">{t('flow.value.handleHint')}</span></>
          : groups.map(group => <div key={group.root} className="xflow-var-group"><span className="xapi-field-hint">{group.title}</span>
            {group.items.length ? group.items.map(path => <button type="button" key={path} className="xflow-var" onClick={() => pick(path)}>
              <code className="xapi-mono xapi-mono-ref">{path}</code></button>) : <span className="xapi-field-hint">—</span>}</div>)}
        {!target.handleOnly && (target.variables.handlePaths?.length ?? 0) > 0 && <span className="xapi-field-hint">{t('flow.value.credentialsHandle')}</span>}
        {target.variables.unavailableSteps.length > 0 && <span className="xapi-field-hint">{t('mapping.unavailableSteps', { steps: target.variables.unavailableSteps.join(t('list.separator')) })}</span>}
      </div>
    </div>
    <div className="xflow-popover-foot">
      {!target.required && target.binding && <button type="button" className="xapi-link" onClick={() => { target.onApply(undefined); onClose(); }}>{t('flow.value.clear')}</button>}
      <span style={{ flex: 1 }} />
      <Button onClick={onClose}>{tCommon('action.cancel')}</Button>
      <Button type="primary" disabled={!filled} onClick={() => { target.onApply(binding); onClose(); }}>{t('field.apply')}</Button>
    </div>
  </div>;
}

/** 一行可点击的取值：标签、类型与必填，下方显示当前取值；只读时不可点击。 */
function ValueRow({ label, meta, value, fixed, required, readOnly, error, onClick }: {
  label: ReactNode; meta?: ReactNode; value?: Binding; fixed?: string; required: boolean; readOnly: boolean; error?: boolean; onClick: () => void;
}) {
  const t = useT('channel');
  const content = <><span className="xflow-row-label">{label}{required && <span className="xapi-inline-hint"> *</span>}
    {meta && <span className="xapi-field-hint"> {meta}</span>}</span>
    {fixed !== undefined ? <span className="xapi-source-fixed">{t('flow.value.fixed', { value: fixed })}</span>
      : value ? <BindingView binding={value} /> : <span className={required ? 'xapi-inline-hint' : 'xapi-field-hint'}>
        {required ? t('flow.value.missingRequired') : t('flow.value.missingOptional')}</span>}</>;
  return readOnly || fixed !== undefined ? <div className={`xflow-row${error ? ' is-error' : ''}`}>{content}</div>
    : <button type="button" className={`xflow-row xflow-row-button${error ? ' is-error' : ''}`} onClick={onClick}>{content}</button>;
}

/** 原子 API 的请求字段（不含数组元素）与调用参数，作为映射目标；请求字段的键为规范化后的 LOCATION:path。 */
function nodeTargets(api: ApiContract | undefined): MappingTarget[] {
  if (!api) return [];
  return [
    ...api.requestFields.filter(field => !field.fieldPath.includes('[]')).map(field => ({
      key: normalizeRequestKey(`${field.fieldLocation}:${field.fieldPath}`), group: field.fieldLocation, path: field.fieldPath,
      nested: field.fieldLocation === 'BODY', dataType: field.dataType, required: field.required, fixed: field.fixed, handle: false })),
    ...api.params.map(param => ({ key: `PARAM:${param.name}`, group: 'PARAMS', path: param.name, nested: false, dataType: param.dataType,
      required: param.required, fixed: false, handle: param.dataType === 'PROTECTED_HANDLE' })),
  ];
}

/** 取值浮层的标题：分组、路径、类型与是否必填。 */
const targetTitle = (target: MappingTarget) => {
  const params = { path: target.path, type: target.dataType, required: requiredWord(tPanels, target.required) };
  if (target.group === 'PARAMS') return tPanels('flow.value.paramTitle', params);
  if (target.group === 'RESPONSE') return tPanels('flow.value.responseTitle', params);
  return tPanels('flow.value.fieldTitle', { ...params, group: target.group });
};

/** 映射目标对应的取值浮层参数。 */
function valueTarget(target: MappingTarget, binding: Binding | undefined, variables: VariableOptions,
  onApply: (binding: Binding | undefined) => void): ValueTarget {
  return { title: targetTitle(target), binding, kinds: target.handle ? ['PATH'] : ['PATH', 'EXPRESSION', 'FUNCTION'],
    required: target.required, variables, handleOnly: target.handle, onApply };
}

export function NodeSide({ flow, context, functions, readOnly, onChange, onSelect, id, onEdit }: PanelProps & { id: string; onEdit: (target: ValueTarget) => void }) {
  const t = useT('channel');
  const [editingBasics, setEditingBasics] = useState(false);
  const [mapping, setMapping] = useState(false);
  const index = flow.nodes.findIndex(item => item.id === id);
  const node = flow.nodes[index];
  const api: ApiContract | undefined = context.apis.find(item => item.apiCode === node?.integrationApi);
  const targets = useMemo(() => nodeTargets(api), [api]);
  const bindings = useMemo(() => {
    const map = new Map<string, Binding>();
    for (const [key, binding] of Object.entries(node?.request ?? {})) map.set(normalizeRequestKey(key), binding);
    for (const [name, binding] of Object.entries(node?.params ?? {})) map.set(`PARAM:${name}`, binding);
    return map;
  }, [node]);
  if (!node) return null;
  const variables = variableOptions(flow, context, functions, id);
  const exits = outgoing(flow, id).length;
  /** 把一组目标的取值写回节点：请求字段按规范化键替换，调用参数按名称替换；undefined 表示清除。 */
  function apply(changes: Map<string, Binding | undefined>) {
    const request = { ...node.request }; const params = { ...(node.params ?? {}) };
    for (const [key, binding] of changes) {
      const target = targets.find(item => item.key === key);
      if (!target) continue;
      if (target.group === 'PARAMS') { if (binding) params[target.path] = binding; else delete params[target.path]; continue; }
      for (const existing of Object.keys(request)) if (normalizeRequestKey(existing) === key) delete request[existing];
      if (binding) request[`${target.group}:${target.path}`] = binding;
    }
    onChange({ ...flow, nodes: flow.nodes.map((item, i) => i === index ? { ...item, request, params } : item) });
  }
  return <Side title={<>{richText(t, 'flow.node.title', { id: <Mono>{node.id}</Mono> })}</>} extra={<span className="xmap-head-extra">
    {flow.entry === id ? <span className="xflow-required">{t('flow.node.entry')}</span>
      : !readOnly && <button type="button" className="xapi-link" onClick={() => onChange({ ...flow, entry: id })}>{t('flow.node.setEntry')}</button>}
    {!readOnly && <button type="button" className={`xmap-edit${editingBasics ? ' is-on' : ''}`} title={t('flow.node.renameId')} onClick={() => setEditingBasics(!editingBasics)}>✎</button>}</span>}
    footer={!readOnly && <><button type="button" className="xapi-link" onClick={() => onSelect({ kind: 'exits', id })}>{t('flow.node.editExits')}</button>
      <button type="button" className="xapi-link xapi-danger" onClick={() => { onChange(removeVertex(flow, id)); onSelect(null); }}>{t('flow.node.delete')}</button></>}>
    <p className="xmap-sub"><Mono>{node.integrationApi}</Mono>{api ? ` · ${api.apiName}` : t('flow.node.apiMissing')}{t('flow.node.exitCount', { count: exits })}</p>
    {editingBasics && <IdField flow={flow} value={node.id} readOnly={readOnly}
      onRename={next => { onChange(renameVertex(flow, id, next)); onSelect({ kind: 'node', id: next }); }} />}
    <MappingList title={t('api.tab.request')} targets={targets} bindings={bindings} readOnly={readOnly} onOpenMapping={() => setMapping(true)}
      onEdit={target => onEdit(valueTarget(target, bindings.get(target.key), variables, binding => apply(new Map([[target.key, binding]]))))} />
    <p className="xapi-field-hint">{t('flow.node.mappingHint')}{variables.unavailableSteps.length > 0
      && t('flow.node.unavailableSteps', { steps: variables.unavailableSteps.join(t('list.separator')) })}</p>
    <MappingDrawer open={mapping} heading={<Mono>{node.id}</Mono>} targets={targets} bindings={bindings} variables={variables} functions={functions}
      subtitle={`${node.integrationApi}${api ? ` · ${api.apiName}` : ''}${t('flow.node.mappingSubtitle', { fields: targets.filter(item => item.group !== 'PARAMS').length, params: api?.params.length ?? 0 })}`}
      onClose={() => setMapping(false)} onApply={apply} />
  </Side>;
}

/** 某节点的出口顺序：条件出口有序（↑↓ 调整优先级），“其他情况”固定最后；选中一条在下方编辑条件与目标。 */
export function ExitsSide({ flow, context, functions, readOnly, onChange, id }: PanelProps & { id: string }) {
  const t = useT('channel');
  const edges = outgoing(flow, id);
  const conditional = edges.filter(edge => !edge.default);
  const fallback = edges.find(edge => edge.default);
  const [editing, setEditing] = useState<number | null>(conditional.length ? 0 : null);
  const targets = [...flow.nodes.filter(node => node.id !== id).map(node => ({ value: node.id, label: t('flow.target.node', { id: node.id }) })),
    ...flow.returns.map(item => ({ value: item.id, label: t('flow.target.return', { id: item.id }) }))];
  const kindOf = (to: string) => flow.nodes.some(node => node.id === to) ? t('flow.kind.node') : t('flow.kind.return');
  const replace = (edge: FlowEdge, patch: Partial<FlowEdge>) => onChange({ ...flow, edges: flow.edges.map(item => item === edge ? { ...item, ...patch } : item) });
  const move = (index: number, offset: number) => {
    const next = [...conditional]; const [item] = next.splice(index, 1); next.splice(index + offset, 0, item);
    onChange(reorderOutgoing(flow, id, next)); setEditing(index + offset);
  };
  const statusOf = (edge: FlowEdge) => statuses.find(status => edge.condition === statusCondition(id, status));
  const current = editing === null ? undefined : conditional[editing];
  const conditionPaths = variableOptions(flow, context, functions, id, true).paths;
  return <Side title={<>{richText(t, 'flow.exits.title', { id: <Mono>{id}</Mono> })}</>}>
    <p className="xapi-field-hint">{t('flow.exits.hint')}</p>
    {conditional.map((edge, index) => <button type="button" key={`${edge.to}:${index}`}
      className={`xflow-exit${editing === index ? ' is-selected' : ''}`} onClick={() => setEditing(index)}>
      <span className="xflow-exit-seq">{index + 1}</span>
      <span className="xflow-exit-main"><span>{statusOf(edge) ? t('flow.exits.statusIs', { status: statusOf(edge) ?? '' }) : edge.condition || t('flow.edge.conditionPending')}</span>
        <code className="xapi-mono-muted">→ {kindOf(edge.to)} {edge.to}</code></span>
      {!readOnly && <span className="xapi-ops" onClick={event => event.stopPropagation()}>
        <button type="button" className="xapi-link" disabled={index === 0} onClick={() => move(index, -1)}>↑</button>
        <button type="button" className="xapi-link" disabled={index === conditional.length - 1} onClick={() => move(index, 1)}>↓</button></span>}
    </button>)}
    <div className="xflow-exit is-fallback"><span className="xflow-exit-seq">—</span><span className="xflow-exit-main"><span>{t('flow.edge.otherwise')}</span>
      {readOnly || !targets.length ? <code className="xapi-mono-muted">{fallback ? `→ ${kindOf(fallback.to)} ${fallback.to}` : t('account.notConfigured')}</code>
        : <Select size="small" value={fallback?.to} placeholder={t('flow.exits.fallbackPlaceholder')} options={targets}
          onChange={to => fallback ? replace(fallback, { to }) : onChange({ ...flow, edges: [...flow.edges, { from: id, to, default: true }] })} />}</span></div>
    {!readOnly && <button type="button" className="xapi-link xapi-link-add" disabled={!targets.length} onClick={() => {
      onChange({ ...flow, edges: [...flow.edges, { from: id, to: targets[0].value, condition: statusCondition(id, 'SUCCESS'), priority: conditional.length + 1 }] });
      setEditing(conditional.length);
    }}>{t('flow.exits.add')}</button>}
    {current && !readOnly && <div className="xflow-exit-editor">
      <h4>{t('flow.exits.editTitle', { index: editing! + 1 })}</h4>
      <Segmented size="small" value={statusOf(current) ?? 'CUSTOM'}
        options={[...statuses.map(status => ({ value: status, label: status })), { value: 'CUSTOM', label: t('flow.exits.custom') }]}
        onChange={value => replace(current, { condition: value === 'CUSTOM' ? (statusOf(current) ? '' : current.condition) : statusCondition(id, value as ExecuteStatus) })} />
      <AutoComplete value={current.condition ?? ''} className="xapi-mono-input"
        options={conditionPaths.filter(path => (current.condition ?? '') && path.includes(current.condition ?? '')).slice(0, 20).map(path => ({ value: path }))}
        onChange={condition => replace(current, { condition })}>
        <Input.TextArea autoSize={{ minRows: 2 }} className="xapi-mono-input" placeholder={t('flow.exits.conditionPlaceholder')} /></AutoComplete>
      <div className="xflow-field"><span className="xapi-field-label">{t('flow.exits.target')}</span>
        <Select value={current.to} options={targets} onChange={to => replace(current, { to })} /></div>
      <div className="xapi-ops"><button type="button" className="xapi-link xapi-danger" onClick={() => {
        onChange(reorderOutgoing({ ...flow, edges: flow.edges.filter(item => item !== current) }, id, conditional.filter(item => item !== current)));
        setEditing(null);
      }}>{t('flow.exits.delete')}</button></div>
      <p className="xapi-field-hint">{t('flow.exits.note')}</p>
    </div>}
  </Side>;
}

export function ReturnSide({ flow, context, functions, readOnly, onChange, onSelect, id, standardApiCode, onEdit }: PanelProps & {
  id: string; onEdit: (target: ValueTarget) => void;
}) {
  const t = useT('channel');
  const [mapping, setMapping] = useState(false);
  const index = flow.returns.findIndex(item => item.id === id);
  const item = flow.returns[index];
  const targets = useMemo<MappingTarget[]>(() => context.standardApi.responseFields
    .filter(field => field.fieldLocation === 'BODY' && !field.fieldPath.includes('[]'))
    .map(field => ({ key: field.fieldPath, group: 'RESPONSE', path: field.fieldPath, nested: true, dataType: field.dataType,
      required: field.required, fixed: false, handle: false })), [context]);
  const bindings = useMemo(() => new Map(Object.entries(item?.outputs ?? {})), [item]);
  if (!item) return null;
  const variables = variableOptions(flow, context, functions, id);
  const set = (patch: Partial<FlowReturn>) => onChange({ ...flow, returns: flow.returns.map((other, i) => i === index ? { ...other, ...patch } : other) });
  function apply(changes: Map<string, Binding | undefined>) {
    const outputs = { ...(item.outputs ?? {}) };
    for (const [key, binding] of changes) { if (binding) outputs[key] = binding; else delete outputs[key]; }
    set({ outputs });
  }
  const readable = variables.paths.filter(path => path.startsWith('steps.')).map(path => path.split('.')[1]);
  return <Side title={<>{richText(t, 'flow.target.return', { id: <Mono>{item.id}</Mono> })}</>}
    footer={!readOnly && <button type="button" className="xapi-link xapi-danger" onClick={() => { onChange(removeVertex(flow, id)); onSelect(null); }}>{t('flow.return.delete')}</button>}>
    <IdField flow={flow} value={item.id} readOnly={readOnly} onRename={next => { onChange(renameVertex(flow, id, next)); onSelect({ kind: 'return', id: next }); }} />
    <div className="xflow-field"><span className="xapi-field-label">{t('api.rules.executeStatus')}</span>
      <Segmented value={item.executeStatus} disabled={readOnly} options={statuses}
        onChange={value => set(value === 'ERROR' ? { executeStatus: 'ERROR', outputs: undefined } : { executeStatus: value as ExecuteStatus, outputs: item.outputs ?? {} })} /></div>
    {item.executeStatus === 'ERROR' ? <p className="xapi-field-hint">{t('flow.return.errorHint')}</p> : <>
      <MappingList title={t('flow.return.responseFields', { api: standardApiCode })} targets={targets} bindings={bindings} readOnly={readOnly} onOpenMapping={() => setMapping(true)}
        onEdit={target => onEdit(valueTarget(target, bindings.get(target.key), variables, binding => apply(new Map([[target.key, binding]]))))} />
      <p className="xapi-field-hint">{t('flow.return.readable', { nodes: [...new Set(readable)].join(t('list.separator')) || t('accountConfig.wizard.none') })}</p>
      <MappingDrawer open={mapping} heading={<>{richText(t, 'flow.target.return', { id: <Mono>{item.id}</Mono> })}</>} targets={targets} bindings={bindings} variables={variables}
        functions={functions} subtitle={t('flow.return.mappingSubtitle', { api: standardApiCode, count: targets.length, status: item.executeStatus })}
        onClose={() => setMapping(false)} onApply={apply} /></>}
  </Side>;
}

export function InitializerSide({ flow, context, functions, readOnly, onChange, onSelect, id, onEdit }: PanelProps & {
  id: string; onEdit: (target: ValueTarget) => void;
}) {
  const t = useT('channel');
  const index = flow.initializers.findIndex(item => item.id === id);
  const item = flow.initializers[index];
  if (!item) return null;
  const declared = functions.find(fn => fn.functionCode === item.function);
  const others = variableOptions({ ...flow, initializers: flow.initializers.filter(other => other.id !== id) }, context, functions, null);
  const variables = { ...others, paths: others.paths.filter(path => !path.startsWith('steps.')), handlePaths: [] };
  const set = (patch: Partial<Initializer>) => onChange({ ...flow, initializers: flow.initializers.map((other, i) => i === index ? { ...other, ...patch } : other) });
  return <Side title={<>{richText(t, 'flow.initializer.title', { id: <Mono>{item.id}</Mono> })}</>}
    footer={!readOnly && <button type="button" className="xapi-link xapi-danger" onClick={() => {
      onChange({ ...flow, initializers: flow.initializers.filter(other => other.id !== id) }); onSelect(null); }}>{t('flow.initializer.delete')}</button>}>
    <IdField flow={flow} value={item.id} readOnly={readOnly} onRename={next => { set({ id: next }); onSelect({ kind: 'initializer', id: next }); }} />
    <div className="xflow-field"><span className="xapi-field-label">{t('script.registeredFunctions')}</span>
      <Select value={item.function} disabled={readOnly} className="xapi-mono-input"
        options={functions.filter(fn => fn.kind === 'INITIALIZER').map(fn => ({ value: fn.functionCode,
          label: `${fn.functionCode} · ${functionName(t, fn.functionCode, '')}` }))} onChange={fn => set({ function: fn, args: {} })} /></div>
    <h4>{t('credential.parameters')}</h4>
    {declared?.params.map(param => <ValueRow key={param.name} label={<Mono>{param.name}</Mono>} meta={`${param.dataType} · ${requiredWord(t, param.required)}`}
      required={param.required} readOnly={readOnly} value={item.args[param.name]} error={param.required && !item.args[param.name]}
      onClick={() => onEdit({ title: t('flow.initializer.paramTitle', { name: param.name, type: param.dataType, required: requiredWord(t, param.required) }), binding: item.args[param.name],
        kinds: ['PATH', 'EXPRESSION'], required: param.required, variables,
        onApply: binding => { const args = { ...item.args }; if (binding) args[param.name] = binding; else delete args[param.name]; set({ args }); } })} />)}
    <div className="xflow-switch"><Switch size="small" checked={item.required} disabled={readOnly} onChange={required => set({ required })} />
      <span><span>{t('flow.initializer.required')}</span><span className="xapi-field-hint"> {t('flow.initializer.requiredHint')}</span></span></div>
    <h4>{t('flow.initializer.returns', { id: item.id })}</h4>
    <div className="xflow-returns">{Object.entries(declared?.returnRoots ?? {}).map(([root, type]) => <div key={root}>
      {type === 'PROTECTED_HANDLE' ? <><span className="xapi-handle">{lockMark} {root}.*</span><span className="xapi-field-hint">{t('flow.initializer.handleHint')}</span></>
        : <><Mono>{root}.*</Mono><span className="xapi-field-hint">{t('flow.initializer.plainHint')}</span></>}</div>)}</div>
    {item.function === 'CHANNEL_ACCOUNT_CONFIG' && <Note items={[t('flow.initializer.note.vault'), t('flow.initializer.note.transaction')]} />}
  </Side>;
}
