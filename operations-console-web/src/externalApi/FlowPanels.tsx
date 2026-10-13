import { useEffect, useState } from 'react';
import { AutoComplete, Button, Form, Input, Select, Space, Switch, Table, Tag, Tooltip } from 'antd';
import { BindingInput } from './EditorParts';
import type { RegisteredFunction } from './editorModel';
import {
  allIds, normalizeRequestKey, outgoing, renameVertex, removeVertex, reorderOutgoing, requestCoverage, statusCondition, variableOptions,
} from './flowModel';
import type { ApiContract, ExecuteStatus, Flow, FlowEditorContext, FlowEdge, FlowReturn, Initializer } from './flowModel';
import type { Binding } from './integrationApiModel';
import type { FlowSelection } from './FlowCanvas';

const idPattern = /^[a-z][A-Za-z0-9]{0,63}$/;

interface PanelProps {
  flow: Flow; context: FlowEditorContext; functions: RegisteredFunction[]; readOnly: boolean;
  onChange: (flow: Flow) => void; onSelect: (selection: FlowSelection) => void;
}

/** 标识输入：失焦或回车时生效；格式不符或与其他标识重复时不提交并提示。 */
function IdInput({ flow, value, readOnly, onRename }: { flow: Flow; value: string; readOnly: boolean; onRename: (next: string) => void }) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  const taken = draft !== value && allIds(flow).has(draft);
  const invalid = !idPattern.test(draft) || taken;
  const commit = () => { if (draft !== value && !invalid) onRename(draft); else setDraft(value); };
  return <Form.Item label="标识" validateStatus={invalid ? 'error' : undefined}
    help={invalid ? (taken ? '标识已被使用' : '小写字母开头，只含字母和数字') : '初始化项、节点与出口的标识全局唯一'}>
    <Input value={draft} disabled={readOnly} maxLength={64} onChange={event => setDraft(event.target.value)} onBlur={commit} onPressEnter={commit} />
  </Form.Item>;
}

export function StartPanel({ flow }: { flow: Flow }) {
  return <aside className="flow-properties"><div className="flow-panel-title"><h3>开始</h3></div>
    <p>入口节点：{flow.entry || <span className="muted">未指定</span>}</p>
    <p className="cell-meta">从“开始”拖出连线到原子 API 节点即可设为入口，也可在节点属性中点击“设为入口”。</p></aside>;
}

/** 初始化项侧栏：只列出与新增，选择后在属性面板编辑；初始化项不上画布。 */
export function InitializerList({ flow, functions, readOnly, selection, onChange, onSelect }: PanelProps & { selection: FlowSelection }) {
  const initializerFunctions = functions.filter(item => item.kind === 'INITIALIZER');
  function add() {
    const fn = initializerFunctions[0];
    if (!fn) return;
    const base = 'resource'; const used = allIds(flow);
    let id = base; for (let index = 2; used.has(id); index++) id = `${base}${index}`;
    onChange({ ...flow, initializers: [...flow.initializers, { id, function: fn.functionCode, args: {}, required: true }] });
    onSelect({ kind: 'initializer', id });
  }
  return <aside className="external-flow-initializers"><h3>初始化</h3>
    {flow.initializers.length === 0 && <p className="muted">暂无初始化项</p>}
    {flow.initializers.map(item => <button type="button" key={item.id}
      className={`external-flow-initializer${selection?.kind === 'initializer' && selection.id === item.id ? ' is-selected' : ''}`}
      onClick={() => onSelect({ kind: 'initializer', id: item.id })}>
      <strong>{item.id}</strong><span>{item.function}{item.required ? ' · 必需' : ''}</span></button>)}
    {!readOnly && <Button block onClick={add} disabled={!initializerFunctions.length}>添加初始化项</Button>}
  </aside>;
}

export function InitializerPanel({ flow, functions, readOnly, onChange, onSelect, id, context }: PanelProps & { id: string }) {
  const index = flow.initializers.findIndex(item => item.id === id);
  const item = flow.initializers[index];
  if (!item) return null;
  const declared = functions.find(fn => fn.functionCode === item.function);
  const others = variableOptions({ ...flow, initializers: flow.initializers.filter(other => other.id !== id) }, context, functions, null);
  const set = (patch: Partial<Initializer>) => onChange({ ...flow, initializers: flow.initializers.map((other, i) => i === index ? { ...other, ...patch } : other) });
  return <aside className="flow-properties"><div className="flow-panel-title"><h3>初始化项</h3></div>
    <Form layout="vertical" disabled={readOnly}>
      <IdInput flow={flow} value={item.id} readOnly={readOnly} onRename={next => { set({ id: next }); onSelect({ kind: 'initializer', id: next }); }} />
      <Form.Item label="注册函数"><Select value={item.function} options={functions.filter(fn => fn.kind === 'INITIALIZER')
        .map(fn => ({ value: fn.functionCode, label: fn.functionCode }))} onChange={fn => set({ function: fn, args: {} })} /></Form.Item>
      {declared?.params.map(param => <Form.Item key={param.name} label={`${param.name}${param.required ? ' *' : ''}`}>
        <BindingInput value={item.args[param.name]} kinds={['PATH', 'EXPRESSION']} disabled={readOnly}
          paths={others.paths.filter(path => !path.startsWith('steps.'))}
          onChange={binding => set({ args: { ...item.args, [param.name]: binding } })} /></Form.Item>)}
      <Form.Item label="必需"><Switch checked={item.required} onChange={required => set({ required })} /></Form.Item>
      <p className="cell-meta">必需项失败时不发送渠道请求。返回：{Object.entries(declared?.returnRoots ?? {})
        .map(([root, type]) => `${root}${type === 'PROTECTED_HANDLE' ? '（受控句柄）' : ''}`).join('、') || '—'}</p>
    </Form>
    {!readOnly && <Button className="flow-remove" danger type="text" onClick={() => {
      onChange({ ...flow, initializers: flow.initializers.filter(other => other.id !== id) }); onSelect(null);
    }}>删除初始化项</Button>}
  </aside>;
}

export function NodePanel({ flow, context, functions, readOnly, onChange, onSelect, id }: PanelProps & { id: string }) {
  const index = flow.nodes.findIndex(item => item.id === id);
  const node = flow.nodes[index];
  if (!node) return null;
  const api = context.apis.find(item => item.apiCode === node.integrationApi);
  const variables = variableOptions(flow, context, functions, id);
  const conditionVariables = variableOptions(flow, context, functions, id, true);
  const coverage = requestCoverage(node, api);
  const setNode = (patch: Partial<typeof node>) => onChange({ ...flow, nodes: flow.nodes.map((item, i) => i === index ? { ...item, ...patch } : item) });
  function setRequest(key: string, binding: Binding | undefined) {
    const request = { ...node.request };
    for (const existing of Object.keys(request)) if (normalizeRequestKey(existing) === normalizeRequestKey(key)) delete request[existing];
    if (binding) request[key] = binding;
    setNode({ request });
  }
  const requestValue = (key: string) => Object.entries(node.request).find(([existing]) => normalizeRequestKey(existing) === normalizeRequestKey(key))?.[1];
  return <aside className="flow-properties"><div className="flow-panel-title"><h3>原子 API 节点</h3>
    {flow.entry === id ? <Tag color="blue">入口</Tag> : !readOnly && <Button size="small" onClick={() => onChange({ ...flow, entry: id })}>设为入口</Button>}</div>
    <Form layout="vertical" disabled={readOnly}>
      <IdInput flow={flow} value={node.id} readOnly={readOnly} onRename={next => { onChange(renameVertex(flow, id, next)); onSelect({ kind: 'node', id: next }); }} />
      <Form.Item label="原子 API"><Input value={api ? `${node.integrationApi} · ${api.apiName}` : `${node.integrationApi}（不存在）`} disabled /></Form.Item>
    </Form>
    <h4>请求字段 <Tag color={coverage.covered < coverage.required ? 'red' : 'green'}>必填 {coverage.covered}/{coverage.required}</Tag></h4>
    {variables.unavailableSteps.length > 0 && <p className="cell-meta">不在所有路径上的节点不可读取：{variables.unavailableSteps.join('、')}</p>}
    {api?.requestFields.map(field => {
      const key = `${field.fieldLocation}:${field.fieldPath}`;
      const missing = coverage.missing.includes(key);
      return <div key={key} className={`external-flow-row${missing ? ' external-api-row-error' : ''}`}>
        <div className="external-flow-row-label"><code>{key}</code>{field.required && <span className="external-api-field-hint"> *</span>}</div>
        {field.fixed ? <Tag>固定值</Tag> : <Space.Compact block>
          <BindingInput value={requestValue(key)} kinds={['PATH', 'EXPRESSION', 'FUNCTION']} paths={variables.paths} functions={functions}
            disabled={readOnly} onChange={binding => setRequest(key, binding)} />
          {!readOnly && requestValue(key) && <Button onClick={() => setRequest(key, undefined)}>清除</Button>}</Space.Compact>}
      </div>;
    })}
    {!!api?.params.length && <><h4>调用参数</h4>{api.params.map(param => {
      const binding = node.params?.[param.name];
      const setParam = (value: Binding | undefined) => {
        const params = { ...(node.params ?? {}) };
        if (value) params[param.name] = value; else delete params[param.name];
        setNode({ params });
      };
      return <div key={param.name} className="external-flow-row">
        <div className="external-flow-row-label"><code>{param.name}</code>{param.required && <span className="external-api-field-hint"> *</span>}
          {param.dataType === 'PROTECTED_HANDLE' && <Tooltip title="受控句柄只能以路径绑定 resources.<初始化项>.credentials.<名称>"><Tag>🔒 受控句柄</Tag></Tooltip>}</div>
        {param.dataType === 'PROTECTED_HANDLE'
          ? <AutoComplete style={{ width: '100%' }} disabled={readOnly} value={binding?.kind === 'PATH' ? binding.path : ''}
            placeholder="resources.<初始化项>.credentials.<名称>" options={variables.handlePaths.map(path => ({ value: path }))}
            onChange={path => setParam(path ? { kind: 'PATH', path } : undefined)} />
          : <BindingInput value={binding} kinds={['PATH', 'EXPRESSION', 'FUNCTION']} paths={variables.paths} functions={functions}
            disabled={readOnly} onChange={setParam} />}
      </div>;
    })}</>}
    <OutgoingEditor flow={flow} nodeId={id} readOnly={readOnly} paths={conditionVariables.paths} onChange={onChange} />
    {!readOnly && <Button className="flow-remove" danger type="text" onClick={() => { onChange(removeVertex(flow, id)); onSelect(null); }}>删除节点及关联连线</Button>}
  </aside>;
}

/** 本节点出口：条件连线有序（上移/下移即重排优先级），“其他情况”固定最后且恰好一条。 */
function OutgoingEditor({ flow, nodeId, readOnly, paths, onChange }: {
  flow: Flow; nodeId: string; readOnly: boolean; paths: string[]; onChange: (flow: Flow) => void;
}) {
  const edges = outgoing(flow, nodeId);
  const conditional = edges.filter(edge => !edge.default);
  const fallback = edges.find(edge => edge.default);
  const targets = [...flow.nodes.filter(node => node.id !== nodeId).map(node => node.id), ...flow.returns.map(item => item.id)];
  const replace = (edge: FlowEdge, patch: Partial<FlowEdge>) => onChange({ ...flow, edges: flow.edges.map(item => item === edge ? { ...item, ...patch } : item) });
  const move = (index: number, offset: number) => {
    const next = [...conditional]; const [item] = next.splice(index, 1); next.splice(index + offset, 0, item);
    onChange(reorderOutgoing(flow, nodeId, next));
  };
  const statuses: ExecuteStatus[] = ['SUCCESS', 'FAIL', 'ERROR'];
  return <div className="flow-exit-list external-flow-exits"><strong>出口顺序</strong>
    {conditional.map((edge, index) => <div key={`${edge.to}:${index}`} className="external-flow-exit">
      <div className="external-flow-exit-head"><span>{index + 1}</span>
        <Select size="small" value={edge.to} disabled={readOnly} style={{ flex: 1 }} options={targets.map(target => ({ value: target, label: `→ ${target}` }))}
          onChange={to => replace(edge, { to })} />
        {!readOnly && <Space size={0}>
          <Button type="text" size="small" disabled={index === 0} onClick={() => move(index, -1)}>↑</Button>
          <Button type="text" size="small" disabled={index === conditional.length - 1} onClick={() => move(index, 1)}>↓</Button>
          <Button type="text" size="small" danger onClick={() => onChange(reorderOutgoing({ ...flow, edges: flow.edges.filter(item => item !== edge) },
            nodeId, conditional.filter(item => item !== edge)))}>×</Button></Space>}
      </div>
      <AutoComplete style={{ width: '100%' }} disabled={readOnly} value={edge.condition ?? ''} placeholder="条件表达式"
        options={[...statuses.map(status => ({ value: statusCondition(nodeId, status), label: `执行状态为 ${status}` })),
          ...paths.filter(path => (edge.condition ?? '') && path.includes(edge.condition ?? '')).slice(0, 20).map(path => ({ value: path }))]}
        onChange={condition => replace(edge, { condition })} />
    </div>)}
    <div className="external-flow-exit external-flow-exit-default"><div className="external-flow-exit-head"><span>其他情况</span>
      {fallback ? <Select size="small" value={fallback.to} disabled={readOnly} style={{ flex: 1 }}
        options={targets.map(target => ({ value: target, label: `→ ${target}` }))} onChange={to => replace(fallback, { to })} />
        : readOnly ? <span className="external-api-field-hint">未配置</span> : <Select size="small" style={{ flex: 1 }} placeholder="选择兜底目标（必需）"
          options={targets.map(target => ({ value: target, label: `→ ${target}` }))}
          onChange={to => onChange({ ...flow, edges: [...flow.edges, { from: nodeId, to, default: true }] })} />}
    </div></div>
    {!readOnly && <Button size="small" type="dashed" block disabled={!targets.length} onClick={() => onChange({ ...flow, edges: [...flow.edges,
      { from: nodeId, to: targets[0], condition: '', priority: conditional.length + 1 }] })}>添加条件出口</Button>}
  </div>;
}

export function ReturnPanel({ flow, context, functions, readOnly, onChange, onSelect, id }: PanelProps & { id: string }) {
  const index = flow.returns.findIndex(item => item.id === id);
  const item = flow.returns[index];
  if (!item) return null;
  const variables = variableOptions(flow, context, functions, id);
  const fields = context.standardApi.responseFields.filter(field => field.fieldLocation === 'BODY' && !field.fieldPath.includes('[]'));
  const set = (patch: Partial<FlowReturn>) => onChange({ ...flow, returns: flow.returns.map((other, i) => i === index ? { ...other, ...patch } : other) });
  const setOutput = (name: string, binding: Binding | undefined) => {
    const outputs = { ...(item.outputs ?? {}) };
    if (binding) outputs[name] = binding; else delete outputs[name];
    set({ outputs });
  };
  return <aside className="flow-properties"><div className="flow-panel-title"><h3>返回出口</h3></div>
    <Form layout="vertical" disabled={readOnly}>
      <IdInput flow={flow} value={item.id} readOnly={readOnly} onRename={next => { onChange(renameVertex(flow, id, next)); onSelect({ kind: 'return', id: next }); }} />
      <Form.Item label="执行状态"><Select value={item.executeStatus} options={(['SUCCESS', 'FAIL', 'ERROR'] as ExecuteStatus[]).map(status => ({ value: status, label: status }))}
        onChange={(executeStatus: ExecuteStatus) => set(executeStatus === 'ERROR' ? { executeStatus, outputs: undefined } : { executeStatus, outputs: item.outputs ?? {} })} /></Form.Item>
    </Form>
    {item.executeStatus === 'ERROR' ? <p className="cell-meta">ERROR 出口不写输出。</p> : <>
      <h4>标准 API 响应字段</h4>
      {variables.unavailableSteps.length > 0 && <p className="cell-meta">不在所有路径上的节点不可读取：{variables.unavailableSteps.join('、')}</p>}
      <Table rowKey="fieldPath" size="small" pagination={false} dataSource={fields} columns={[
        { title: '字段', width: 140, render: (_, field) => <span><code>{field.fieldPath}</code>{field.required && <span className="external-api-field-hint"> *</span>}</span> },
        { title: '取值', render: (_, field) => <Space.Compact block>
          <BindingInput value={item.outputs?.[field.fieldPath]} kinds={['PATH', 'EXPRESSION', 'FUNCTION']} paths={variables.paths}
            functions={functions} disabled={readOnly} onChange={binding => setOutput(field.fieldPath, binding)} />
          {!readOnly && item.outputs?.[field.fieldPath] && <Button onClick={() => setOutput(field.fieldPath, undefined)}>清除</Button>}</Space.Compact> },
      ]} /></>}
    {!readOnly && <Button className="flow-remove" danger type="text" onClick={() => { onChange(removeVertex(flow, id)); onSelect(null); }}>删除出口及关联连线</Button>}
  </aside>;
}

export type { ApiContract };
