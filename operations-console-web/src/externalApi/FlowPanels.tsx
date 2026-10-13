import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { AutoComplete, Button, Input, Modal, Segmented, Select, Switch } from 'antd';
import { BindingInput, BindingView, emptyBinding } from './EditorParts';
import type { RegisteredFunction } from './editorModel';
import {
  allIds, normalizeRequestKey, outgoing, renameVertex, removeVertex, reorderOutgoing, statusCondition, variableOptions,
} from './flowModel';
import type { ApiContract, ExecuteStatus, Flow, FlowEditorContext, FlowEdge, FlowReturn, Initializer, VariableOptions } from './flowModel';
import type { Binding, BindingKind } from './integrationApiModel';
import type { FlowSelection } from './FlowCanvas';
import { Mono, Note, StatusTag } from './ui';
import { MappingDrawer, MappingList } from './FieldMapping';
import type { MappingTarget } from './mappingModel';

const idPattern = /^[a-z][A-Za-z0-9]{0,63}$/;
const statuses: ExecuteStatus[] = ['SUCCESS', 'FAIL', 'ERROR'];
const kindLabels: Record<BindingKind, string> = { PATH: '路径', EXPRESSION: '表达式', FUNCTION: '函数' };
/** 注册函数的中文名，用于下拉与标题。 */
const functionNames: Record<string, string> = { CHANNEL_ACCOUNT_CONFIG: '读取渠道账号配置', MONEY_MINOR_UNITS: '金额换算为最小单位' };

export interface PanelProps {
  flow: Flow; context: FlowEditorContext; functions: RegisteredFunction[]; readOnly: boolean; standardApiCode: string;
  onChange: (flow: Flow) => void; onSelect: (selection: FlowSelection) => void;
}

/** 标识：失焦或回车时生效；格式不符或与其他标识重复时不提交并提示。 */
function IdField({ flow, value, readOnly, onRename }: { flow: Flow; value: string; readOnly: boolean; onRename: (next: string) => void }) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  const taken = draft !== value && allIds(flow).has(draft);
  const invalid = !idPattern.test(draft) || taken;
  const commit = () => { if (draft !== value && !invalid) onRename(draft); else setDraft(value); };
  if (readOnly) return null;
  return <div className="xflow-field"><span className="xapi-field-label">标识</span>
    <Input size="small" value={draft} maxLength={64} className="xapi-mono-input" status={invalid ? 'error' : undefined}
      onChange={event => setDraft(event.target.value)} onBlur={commit} onPressEnter={commit} />
    <span className="xapi-field-hint">{invalid ? (taken ? '标识已被使用' : '小写字母开头，只含字母和数字') : '初始化项、节点与出口的标识全局唯一'}</span></div>;
}

/** 左栏：初始化项列表与“添加节点”组件面板。 */
export function FlowSidebar({ flow, context, functions, readOnly, selection, onChange, onSelect, onAddNode, onAddReturn }: PanelProps & {
  selection: FlowSelection; onAddNode: (apiCode: string) => void; onAddReturn: (status: ExecuteStatus) => void;
}) {
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
    { key: 'api', title: '原子 API 节点', desc: context.apis.length ? '从本接入方原子 API 选择' : '本接入方还没有原子 API',
      onClick: () => setPicking(true), disabled: !context.apis.length },
    { key: 'SUCCESS', title: <><StatusTag status="SUCCESS" /> 出口</>, desc: '返回成功与输出', onClick: () => onAddReturn('SUCCESS') },
    { key: 'FAIL', title: <><StatusTag status="FAIL" /> 出口</>, desc: '返回明确失败与输出', onClick: () => onAddReturn('FAIL') },
    { key: 'ERROR', title: <><StatusTag status="ERROR" /> 出口</>, desc: '结果未知，输出为空', onClick: () => onAddReturn('ERROR') },
  ];
  return <aside className="xflow-sidebar">
    <h3>初始化</h3>
    <p className="xapi-field-hint">流程节点前按依赖顺序执行；必需项失败时不发送任何渠道请求。</p>
    {flow.initializers.length === 0 && <span className="xapi-field-hint">暂无初始化项</span>}
    {flow.initializers.map(item => <button type="button" key={item.id}
      className={`xflow-init${selection?.kind === 'initializer' && selection.id === item.id ? ' is-selected' : ''}`}
      onClick={() => onSelect({ kind: 'initializer', id: item.id })}>
      <span className="xflow-init-head"><code>{item.id}</code>{item.required && <span className="xflow-required">必需</span>}</span>
      <code className="xapi-mono-muted">{item.function}</code>
      <code className="xapi-mono-muted">→ resources.{item.id}</code></button>)}
    {!readOnly && <button type="button" className="xapi-link xapi-link-add" disabled={!initializerFunctions.length} onClick={addInitializer}>添加初始化项</button>}
    {!readOnly && <>
      <div className="xflow-divider" />
      <h3>添加节点</h3>
      {palette.map(item => <button type="button" key={item.key} className="xflow-palette" disabled={item.disabled} onClick={item.onClick}>
        <span>{item.title}</span><span className="xapi-field-hint">{item.desc}</span></button>)}
    </>}
    {picking && <Modal open title="添加原子 API 节点" footer={null} onCancel={() => setPicking(false)}>
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
  return <Side title="属性"><p className="xapi-field-hint">点击节点、出口、初始化项查看与编辑配置；点击连线编辑该节点的出口顺序。</p></Side>;
}

export function StartSide({ flow, standardApiCode }: { flow: Flow; standardApiCode: string }) {
  return <Side title={`开始 · ${standardApiCode} 输入`}>
    <p>入口节点：{flow.entry ? <Mono>{flow.entry}</Mono> : <span className="xapi-field-hint">未指定</span>}</p>
    <p className="xapi-field-hint">从“开始”拖出连线到原子 API 节点即可设为入口，也可以在节点属性中设为入口。</p></Side>;
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
  const [binding, setBinding] = useState<Binding>(target.binding ?? emptyBinding(target.kinds[0]));
  const pure = functions.filter(item => item.kind === 'PURE');
  const groups = [
    { root: 'input', title: '标准 API 输入', items: target.variables.paths.filter(path => path.startsWith('input.')) },
    { root: 'resources', title: '初始化结果', items: target.variables.paths.filter(path => path.startsWith('resources.')) },
    { root: 'steps', title: '必经节点结果', items: target.variables.paths.filter(path => path.startsWith('steps.')) },
  ];
  function pick(path: string) {
    if (binding.kind === 'EXPRESSION') setBinding({ kind: 'EXPRESSION', expression: `${binding.expression}${binding.expression ? ' ' : ''}${path}` });
    else setBinding({ kind: 'PATH', path });
  }
  const declared = binding.kind === 'FUNCTION' ? pure.find(item => item.functionCode === binding.function) : undefined;
  const filled = binding.kind === 'PATH' ? !!binding.path.trim() : binding.kind === 'EXPRESSION' ? !!binding.expression.trim() : !!binding.function;
  return <div className="xflow-popover" role="dialog" aria-label={target.title}>
    <div className="xflow-popover-head"><h3>{target.title}</h3>
      {target.kinds.length > 1 && <Segmented size="small" value={binding.kind} options={target.kinds.map(kind => ({ value: kind, label: kindLabels[kind] }))}
        onChange={kind => setBinding(emptyBinding(kind as BindingKind))} />}</div>
    <div className="xflow-popover-body">
      <div className="xflow-popover-editor">
        {binding.kind === 'PATH' && <>
          {target.handleOnly ? <AutoComplete value={binding.path} className="xapi-mono-input" placeholder="resources.<初始化项>.credentials.<名称>"
            options={(target.variables.handlePaths ?? []).map(path => ({ value: path }))} onChange={path => setBinding({ kind: 'PATH', path })} />
            : <Input value={binding.path} className="xapi-mono-input" placeholder="从右侧选择，或输入 input.… / resources.… / steps.…"
              onChange={event => setBinding({ kind: 'PATH', path: event.target.value })} />}
          <span className="xapi-field-hint">只读点分路径，不能用下标；数组元素请改用表达式明确选择。</span></>}
        {binding.kind === 'EXPRESSION' && <>
          <Input.TextArea value={binding.expression} autoSize={{ minRows: 4 }} className="xapi-mono-input" maxLength={4096}
            placeholder="'NI-' + input.paymentContext.attemptId" onChange={event => setBinding({ kind: 'EXPRESSION', expression: event.target.value })} />
          <span className="xapi-field-hint">Aviator 只读表达式；常量写成 'text' 或 100，带下标读取写成 #a.b[0].c。</span></>}
        {binding.kind === 'FUNCTION' && <>
          <Select value={binding.function || undefined} placeholder="选择纯函数" className="xapi-mono-input"
            options={pure.map(item => ({ value: item.functionCode, label: `${item.functionCode} · ${functionNames[item.functionCode] ?? item.returns}` }))}
            onChange={(code: string) => setBinding({ kind: 'FUNCTION', function: code, args: {}, result: 'result' })} />
          {declared?.params.map(param => <div key={param.name} className="xflow-field"><span className="xapi-field-label"><Mono>{param.name}</Mono>
            <span className="xapi-field-hint"> {param.dataType} · {param.required ? '必填' : '可选'}</span></span>
            <BindingInput value={binding.args[param.name]} kinds={['PATH', 'EXPRESSION']} paths={target.variables.paths}
              onChange={next => setBinding({ ...binding, args: { ...binding.args, [param.name]: next } })} /></div>)}
          {declared && <div className="xflow-field"><span className="xapi-field-label">结果表达式</span>
            <Input value={binding.result} className="xapi-mono-input" onChange={event => setBinding({ ...binding, result: event.target.value })} /></div>}
        </>}
      </div>
      <div className="xflow-popover-vars"><span className="xapi-field-label">可用变量</span>
        {target.handleOnly ? <>{(target.variables.handlePaths ?? []).map(path => <button type="button" key={path} className="xflow-var"
          onClick={() => setBinding({ kind: 'PATH', path })}><span className="xapi-handle">🔒 {path}…</span></button>)}
          <span className="xapi-field-hint">受控句柄参数只能以路径绑定初始化结果中的 credentials。</span></>
          : groups.map(group => <div key={group.root} className="xflow-var-group"><span className="xapi-field-hint">{group.title}</span>
            {group.items.length ? group.items.map(path => <button type="button" key={path} className="xflow-var" onClick={() => pick(path)}>
              <code className="xapi-mono xapi-mono-ref">{path}</code></button>) : <span className="xapi-field-hint">—</span>}</div>)}
        {!target.handleOnly && (target.variables.handlePaths?.length ?? 0) > 0 && <span className="xapi-field-hint">🔒 credentials.* 是受控句柄，只能给 PROTECTED_HANDLE 参数。</span>}
        {target.variables.unavailableSteps.length > 0 && <span className="xapi-field-hint">不在所有路径上、不能读取：{target.variables.unavailableSteps.join('、')}</span>}
      </div>
    </div>
    <div className="xflow-popover-foot">
      {!target.required && target.binding && <button type="button" className="xapi-link" onClick={() => { target.onApply(undefined); onClose(); }}>清除取值（省略）</button>}
      <span style={{ flex: 1 }} />
      <Button onClick={onClose}>取消</Button>
      <Button type="primary" disabled={!filled} onClick={() => { target.onApply(binding); onClose(); }}>应用</Button>
    </div>
  </div>;
}

/** 一行可点击的取值：标签、类型与必填，下方显示当前取值；只读时不可点击。 */
function ValueRow({ label, meta, value, fixed, required, readOnly, error, onClick }: {
  label: ReactNode; meta?: ReactNode; value?: Binding; fixed?: string; required: boolean; readOnly: boolean; error?: boolean; onClick: () => void;
}) {
  const content = <><span className="xflow-row-label">{label}{required && <span className="xapi-inline-hint"> *</span>}
    {meta && <span className="xapi-field-hint"> {meta}</span>}</span>
    {fixed !== undefined ? <span className="xapi-source-fixed">固定值 {fixed}</span>
      : value ? <BindingView binding={value} /> : <span className={required ? 'xapi-inline-hint' : 'xapi-field-hint'}>
        {required ? '未赋值（必填）' : '未赋值（非必填，省略）'}</span>}</>;
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
    ...api.params.map(param => ({ key: `PARAM:${param.name}`, group: '调用参数', path: param.name, nested: false, dataType: param.dataType,
      required: param.required, fixed: false, handle: param.dataType === 'PROTECTED_HANDLE' })),
  ];
}

/** 取值浮层的标题：分组、路径、类型与是否必填。 */
const targetTitle = (target: MappingTarget) => `${target.group === '调用参数' ? '调用参数 ·' : `取值 · ${target.group === '响应字段' ? '' : `${target.group} `}`}${
  target.path}（${target.dataType}，${target.required ? '必填' : '可选'}）`;

/** 映射目标对应的取值浮层参数。 */
function valueTarget(target: MappingTarget, binding: Binding | undefined, variables: VariableOptions,
  onApply: (binding: Binding | undefined) => void): ValueTarget {
  return { title: targetTitle(target), binding, kinds: target.handle ? ['PATH'] : ['PATH', 'EXPRESSION', 'FUNCTION'],
    required: target.required, variables, handleOnly: target.handle, onApply };
}

export function NodeSide({ flow, context, functions, readOnly, onChange, onSelect, id, onEdit }: PanelProps & { id: string; onEdit: (target: ValueTarget) => void }) {
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
      if (target.group === '调用参数') { if (binding) params[target.path] = binding; else delete params[target.path]; continue; }
      for (const existing of Object.keys(request)) if (normalizeRequestKey(existing) === key) delete request[existing];
      if (binding) request[`${target.group}:${target.path}`] = binding;
    }
    onChange({ ...flow, nodes: flow.nodes.map((item, i) => i === index ? { ...item, request, params } : item) });
  }
  return <Side title={<>节点 <Mono>{node.id}</Mono></>} extra={<span className="xmap-head-extra">
    {flow.entry === id ? <span className="xflow-required">入口</span>
      : !readOnly && <button type="button" className="xapi-link" onClick={() => onChange({ ...flow, entry: id })}>设为入口</button>}
    {!readOnly && <button type="button" className={`xmap-edit${editingBasics ? ' is-on' : ''}`} title="修改标识" onClick={() => setEditingBasics(!editingBasics)}>✎</button>}</span>}
    footer={!readOnly && <><button type="button" className="xapi-link" onClick={() => onSelect({ kind: 'exits', id })}>编辑出口顺序</button>
      <button type="button" className="xapi-link xapi-danger" onClick={() => { onChange(removeVertex(flow, id)); onSelect(null); }}>删除节点</button></>}>
    <p className="xmap-sub"><Mono>{node.integrationApi}</Mono>{api ? ` · ${api.apiName}` : '（原子 API 不存在）'} · 出口 {exits} 条</p>
    {editingBasics && <IdField flow={flow} value={node.id} readOnly={readOnly}
      onRename={next => { onChange(renameVertex(flow, id, next)); onSelect({ kind: 'node', id: next }); }} />}
    <MappingList title="请求字段" targets={targets} bindings={bindings} readOnly={readOnly} onOpenMapping={() => setMapping(true)}
      onEdit={target => onEdit(valueTarget(target, bindings.get(target.key), variables, binding => apply(new Map([[target.key, binding]]))))} />
    <p className="xapi-field-hint">点击一行用取值浮层修改；多个字段一起改用“字段映射”。{variables.unavailableSteps.length > 0
      && `不在所有路径上的节点不可读取：${variables.unavailableSteps.join('、')}。`}</p>
    <MappingDrawer open={mapping} heading={<Mono>{node.id}</Mono>} targets={targets} bindings={bindings} variables={variables} functions={functions}
      subtitle={`${node.integrationApi}${api ? ` · ${api.apiName}` : ''} · 请求字段 ${targets.filter(item => item.group !== '调用参数').length}、调用参数 ${api?.params.length ?? 0}`}
      onClose={() => setMapping(false)} onApply={apply} />
  </Side>;
}

/** 某节点的出口顺序：条件出口有序（↑↓ 调整优先级），“其他情况”固定最后；选中一条在下方编辑条件与目标。 */
export function ExitsSide({ flow, context, functions, readOnly, onChange, id }: PanelProps & { id: string }) {
  const edges = outgoing(flow, id);
  const conditional = edges.filter(edge => !edge.default);
  const fallback = edges.find(edge => edge.default);
  const [editing, setEditing] = useState<number | null>(conditional.length ? 0 : null);
  const targets = [...flow.nodes.filter(node => node.id !== id).map(node => ({ value: node.id, label: `节点 ${node.id}` })),
    ...flow.returns.map(item => ({ value: item.id, label: `出口 ${item.id}` }))];
  const kindOf = (to: string) => flow.nodes.some(node => node.id === to) ? '节点' : '出口';
  const replace = (edge: FlowEdge, patch: Partial<FlowEdge>) => onChange({ ...flow, edges: flow.edges.map(item => item === edge ? { ...item, ...patch } : item) });
  const move = (index: number, offset: number) => {
    const next = [...conditional]; const [item] = next.splice(index, 1); next.splice(index + offset, 0, item);
    onChange(reorderOutgoing(flow, id, next)); setEditing(index + offset);
  };
  const statusOf = (edge: FlowEdge) => statuses.find(status => edge.condition === statusCondition(id, status));
  const current = editing === null ? undefined : conditional[editing];
  const conditionPaths = variableOptions(flow, context, functions, id, true).paths;
  return <Side title={<><Mono>{id}</Mono> 的出口</>}>
    <p className="xapi-field-hint">按顺序判断，第一条为真即流转；“其他情况”固定在最后。</p>
    {conditional.map((edge, index) => <button type="button" key={`${edge.to}:${index}`}
      className={`xflow-exit${editing === index ? ' is-selected' : ''}`} onClick={() => setEditing(index)}>
      <span className="xflow-exit-seq">{index + 1}</span>
      <span className="xflow-exit-main"><span>{statusOf(edge) ? `执行状态为 ${statusOf(edge)}` : edge.condition || '条件待填'}</span>
        <code className="xapi-mono-muted">→ {kindOf(edge.to)} {edge.to}</code></span>
      {!readOnly && <span className="xapi-ops" onClick={event => event.stopPropagation()}>
        <button type="button" className="xapi-link" disabled={index === 0} onClick={() => move(index, -1)}>↑</button>
        <button type="button" className="xapi-link" disabled={index === conditional.length - 1} onClick={() => move(index, 1)}>↓</button></span>}
    </button>)}
    <div className="xflow-exit is-fallback"><span className="xflow-exit-seq">—</span><span className="xflow-exit-main"><span>其他情况</span>
      {readOnly || !targets.length ? <code className="xapi-mono-muted">{fallback ? `→ ${kindOf(fallback.to)} ${fallback.to}` : '未配置'}</code>
        : <Select size="small" value={fallback?.to} placeholder="选择兜底目标（必需）" options={targets}
          onChange={to => fallback ? replace(fallback, { to }) : onChange({ ...flow, edges: [...flow.edges, { from: id, to, default: true }] })} />}</span></div>
    {!readOnly && <button type="button" className="xapi-link xapi-link-add" disabled={!targets.length} onClick={() => {
      onChange({ ...flow, edges: [...flow.edges, { from: id, to: targets[0].value, condition: statusCondition(id, 'SUCCESS'), priority: conditional.length + 1 }] });
      setEditing(conditional.length);
    }}>添加条件出口</button>}
    {current && !readOnly && <div className="xflow-exit-editor">
      <h4>编辑出口 {editing! + 1}</h4>
      <Segmented size="small" value={statusOf(current) ?? 'CUSTOM'}
        options={[...statuses.map(status => ({ value: status, label: status })), { value: 'CUSTOM', label: '自定义表达式' }]}
        onChange={value => replace(current, { condition: value === 'CUSTOM' ? (statusOf(current) ? '' : current.condition) : statusCondition(id, value as ExecuteStatus) })} />
      <AutoComplete value={current.condition ?? ''} className="xapi-mono-input"
        options={conditionPaths.filter(path => (current.condition ?? '') && path.includes(current.condition ?? '')).slice(0, 20).map(path => ({ value: path }))}
        onChange={condition => replace(current, { condition })}>
        <Input.TextArea autoSize={{ minRows: 2 }} className="xapi-mono-input" placeholder="条件表达式" /></AutoComplete>
      <div className="xflow-field"><span className="xapi-field-label">目标</span>
        <Select value={current.to} options={targets} onChange={to => replace(current, { to })} /></div>
      <div className="xapi-ops"><button type="button" className="xapi-link xapi-danger" onClick={() => {
        onChange(reorderOutgoing({ ...flow, edges: flow.edges.filter(item => item !== current) }, id, conditional.filter(item => item !== current)));
        setEditing(null);
      }}>删除这条出口</button></div>
      <p className="xapi-field-hint">条件只读取上下文，不在页面执行；表达式异常时本次调用返回 ERROR。查询只在渠道合同允许时配置，流程不编排冲正。</p>
    </div>}
  </Side>;
}

export function ReturnSide({ flow, context, functions, readOnly, onChange, onSelect, id, standardApiCode, onEdit }: PanelProps & {
  id: string; onEdit: (target: ValueTarget) => void;
}) {
  const [mapping, setMapping] = useState(false);
  const index = flow.returns.findIndex(item => item.id === id);
  const item = flow.returns[index];
  const targets = useMemo<MappingTarget[]>(() => context.standardApi.responseFields
    .filter(field => field.fieldLocation === 'BODY' && !field.fieldPath.includes('[]'))
    .map(field => ({ key: field.fieldPath, group: '响应字段', path: field.fieldPath, nested: true, dataType: field.dataType,
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
  return <Side title={<>出口 <Mono>{item.id}</Mono></>}
    footer={!readOnly && <button type="button" className="xapi-link xapi-danger" onClick={() => { onChange(removeVertex(flow, id)); onSelect(null); }}>删除出口</button>}>
    <IdField flow={flow} value={item.id} readOnly={readOnly} onRename={next => { onChange(renameVertex(flow, id, next)); onSelect({ kind: 'return', id: next }); }} />
    <div className="xflow-field"><span className="xapi-field-label">执行状态</span>
      <Segmented value={item.executeStatus} disabled={readOnly} options={statuses}
        onChange={value => set(value === 'ERROR' ? { executeStatus: 'ERROR', outputs: undefined } : { executeStatus: value as ExecuteStatus, outputs: item.outputs ?? {} })} /></div>
    {item.executeStatus === 'ERROR' ? <p className="xapi-field-hint">ERROR 出口不写输出，返回空对象；Payment 按原操作身份处理。</p> : <>
      <MappingList title={`${standardApiCode} 响应字段`} targets={targets} bindings={bindings} readOnly={readOnly} onOpenMapping={() => setMapping(true)}
        onEdit={target => onEdit(valueTarget(target, bindings.get(target.key), variables, binding => apply(new Map([[target.key, binding]]))))} />
      <p className="xapi-field-hint">只能引用必经节点的结果：{[...new Set(readable)].join('、') || '无'}。</p>
      <MappingDrawer open={mapping} heading={<>出口 <Mono>{item.id}</Mono></>} targets={targets} bindings={bindings} variables={variables}
        functions={functions} subtitle={`标准 API ${standardApiCode} 响应字段 ${targets.length} · 执行状态 ${item.executeStatus}`}
        onClose={() => setMapping(false)} onApply={apply} /></>}
  </Side>;
}

export function InitializerSide({ flow, context, functions, readOnly, onChange, onSelect, id, onEdit }: PanelProps & {
  id: string; onEdit: (target: ValueTarget) => void;
}) {
  const index = flow.initializers.findIndex(item => item.id === id);
  const item = flow.initializers[index];
  if (!item) return null;
  const declared = functions.find(fn => fn.functionCode === item.function);
  const others = variableOptions({ ...flow, initializers: flow.initializers.filter(other => other.id !== id) }, context, functions, null);
  const variables = { ...others, paths: others.paths.filter(path => !path.startsWith('steps.')), handlePaths: [] };
  const set = (patch: Partial<Initializer>) => onChange({ ...flow, initializers: flow.initializers.map((other, i) => i === index ? { ...other, ...patch } : other) });
  return <Side title={<>初始化项 <Mono>{item.id}</Mono></>}
    footer={!readOnly && <button type="button" className="xapi-link xapi-danger" onClick={() => {
      onChange({ ...flow, initializers: flow.initializers.filter(other => other.id !== id) }); onSelect(null); }}>删除初始化项</button>}>
    <IdField flow={flow} value={item.id} readOnly={readOnly} onRename={next => { set({ id: next }); onSelect({ kind: 'initializer', id: next }); }} />
    <div className="xflow-field"><span className="xapi-field-label">注册函数</span>
      <Select value={item.function} disabled={readOnly} className="xapi-mono-input"
        options={functions.filter(fn => fn.kind === 'INITIALIZER').map(fn => ({ value: fn.functionCode,
          label: `${fn.functionCode} · ${functionNames[fn.functionCode] ?? ''}` }))} onChange={fn => set({ function: fn, args: {} })} /></div>
    <h4>参数</h4>
    {declared?.params.map(param => <ValueRow key={param.name} label={<Mono>{param.name}</Mono>} meta={`${param.dataType} · ${param.required ? '必填' : '可选'}`}
      required={param.required} readOnly={readOnly} value={item.args[param.name]} error={param.required && !item.args[param.name]}
      onClick={() => onEdit({ title: `参数 · ${param.name}（${param.dataType}，${param.required ? '必填' : '可选'}）`, binding: item.args[param.name],
        kinds: ['PATH', 'EXPRESSION'], required: param.required, variables,
        onApply: binding => { const args = { ...item.args }; if (binding) args[param.name] = binding; else delete args[param.name]; set({ args }); } })} />)}
    <div className="xflow-switch"><Switch size="small" checked={item.required} disabled={readOnly} onChange={required => set({ required })} />
      <span><span>必需</span><span className="xapi-field-hint"> 失败时结束调用，返回 503，不发送渠道请求</span></span></div>
    <h4>返回（resources.{item.id}）</h4>
    <div className="xflow-returns">{Object.entries(declared?.returnRoots ?? {}).map(([root, type]) => <div key={root}>
      {type === 'PROTECTED_HANDLE' ? <><span className="xapi-handle">🔒 {root}.*</span><span className="xapi-field-hint">受控句柄，只能给 PROTECTED_HANDLE 参数</span></>
        : <><Mono>{root}.*</Mono><span className="xapi-field-hint">普通字符串，可用于请求字段</span></>}</div>)}</div>
    {item.function === 'CHANNEL_ACCOUNT_CONFIG' && <Note items={['以 platform-support-service 身份、CHANNEL_EXECUTION 用途读取 Vault，并核对渠道、账号与经营主体与传入一致。',
      '在数据库事务外执行；结果只在本次调用内存中，不缓存。']} />}
  </Side>;
}
