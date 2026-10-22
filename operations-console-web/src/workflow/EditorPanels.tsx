import { useState } from 'react';
import { Alert, Button, Empty, Input, Radio, Select, Space, Switch, Tabs, Tag } from 'antd';
import {
  actionOutputs, changeOperator, changeVariable, clausesText, gatewayExits, nextKey, nodeOf, operatorsByType, renameNode, setDefaultExit, setEdgeClauses,
  setExitPriority, updateNode, valueShape, variableCatalog,
} from './graphModel';
import type { VariableInfo } from './graphModel';
import { actionTypeLabels, channelLabels, nodeTypeLabels, operatorLabels, recipientLabels, triggerLabels } from './workflowLabels';
import type {
  ActionTemplate, ConditionClause, FormDefinition, GraphNode, NodeAction, NotificationChannel, NotificationRecipient, NotificationRule,
  NotificationTrigger, PageTaskConfig, Problem, WorkflowGraph,
} from './workflowTypes';
import { Field, PersonSelect } from './WorkflowParts';

export interface PanelContext {
  graph: WorkflowGraph; startForm: FormDefinition; templates: Map<string, ActionTemplate>; templateList: ActionTemplate[];
  readOnly: boolean; problems: Problem[]; people: Record<string, string>; onChange: (graph: WorkflowGraph) => void; onDelete: () => void;
}

function NameAndKey({ node, context }: { node: GraphNode; context: PanelContext }) {
  const [key, setKey] = useState(node.key); const [error, setError] = useState('');
  function commitKey() {
    const result = renameNode(context.graph, node.key, key.trim());
    if ('error' in result) { setError(result.error); return; }
    setError(''); context.onChange(result.graph);
  }
  return <>
    <Field label="名称"><Input value={node.name} maxLength={64} disabled={context.readOnly}
      onChange={event => context.onChange(updateNode(context.graph, node.key, { name: event.target.value }))} /></Field>
    <Field label="节点 key" hint={error || '版本内全局唯一；条件、驳回目标与回调事件都引用它'}>
      <Input value={key} maxLength={64} disabled={context.readOnly} status={error ? 'error' : undefined}
        onChange={event => setKey(event.target.value)} onBlur={commitKey} onPressEnter={commitKey} /></Field>
  </>;
}

function ProblemList({ problems }: { problems: Problem[] }) {
  return problems.length ? <Alert type="error" showIcon className="wf-panel-problems"
    title={<ul className="wf-plain-list">{problems.map((problem, index) => <li key={index}>{problem.message}</li>)}</ul>} /> : null;
}

/** 选中节点后的属性面板：任务节点分基本 / 审批 / 动作 / 通知与回调四个页签（原型 08、10）。 */
export function NodePanel({ node, context }: { node: GraphNode; context: PanelContext }) {
  const problems = context.problems.filter(problem => problem.nodeKey === node.key && !problem.edgeKey);
  const header = <div className="wf-panel-head"><h3>{node.name || node.key}</h3><Tag>{nodeTypeLabels[node.type]}</Tag>
    <span className="wf-grow" />{!context.readOnly && node.type !== 'START' && <Button type="text" danger onClick={context.onDelete}>删除</Button>}</div>;
  if (node.type !== 'TASK') {
    return <div className="wf-panel">{header}<ProblemList problems={problems} /><NameAndKey key={node.key} node={node} context={context} />
      {node.type === 'EXCLUSIVE' && <GatewayExits node={node} context={context} />}
      {node.type === 'START' && <p className="muted">开始节点固定一个；发起时填写的启动表单在“启动表单”页签配置。</p>}
      {(node.type === 'PARALLEL_SPLIT' || node.type === 'PARALLEL_JOIN') && <p className="muted">并行分叉与汇合成对使用、一一对应；分支内部不能跨分支连线，不允许嵌套并行，结束节点不能在并行块内部。</p>}
    </div>;
  }
  const actions = node.actions ?? [];
  return <div className="wf-panel">{header}<ProblemList problems={problems} />
    <Tabs size="small" items={[
      { key: 'basic', label: '基本', children: <><NameAndKey key={node.key} node={node} context={context} />
        <p className="muted">必选动作全部验证通过后，节点进入审批；未配置审批人则自动放行。</p></> },
      { key: 'approval', label: '审批', children: <ApprovalEditor node={node} context={context} /> },
      { key: 'actions', label: `动作 ${actions.length}`, children: <ActionsEditor node={node} context={context} /> },
      { key: 'notify', label: `通知与回调 ${(node.notifications ?? []).length}`, children: <RulesEditor rules={node.notifications ?? []} triggers={nodeTriggerList}
        readOnly={context.readOnly} people={context.people} onChange={notifications => context.onChange(updateNode(context.graph, node.key, { notifications }))}
        note="节点状态变化时执行。通知人员走 Platform 邮件与我的待办；业务回调以可靠事件发布到 topic_platform_workflow_event，订阅系统按回调编码处理。" /> },
    ]} />
  </div>;
}

function ApprovalEditor({ node, context }: { node: GraphNode; context: PanelContext }) {
  const approval = node.approval ?? { mode: 'ANY' as const, approverIds: [] };
  const set = (patch: Partial<typeof approval>) => context.onChange(updateNode(context.graph, node.key, { approval: { ...approval, ...patch } }));
  return <>
    <Field label="审批方式"><Radio.Group value={approval.mode} disabled={context.readOnly} onChange={event => set({ mode: event.target.value })}
      options={[{ value: 'ANY', label: '任一人同意（ANY）' }, { value: 'ALL', label: '全部同意（ALL）' }]} /></Field>
    <Field label="默认审批人" hint="只能指定具体的 Operations 用户；发起时可修改，运行中只能转交">
      <PersonSelect multiple value={approval.approverIds} people={context.people} disabled={context.readOnly} onChange={value => set({ approverIds: value as string[] })} /></Field>
    {!approval.approverIds.length && <p className="muted">不设审批人：必选动作验证通过后自动放行。</p>}
    <p className="muted">同一人不能既提交本节点动作又审批本节点（审批时校验）。</p>
  </>;
}

function ActionsEditor({ node, context }: { node: GraphNode; context: PanelContext }) {
  const actions = node.actions ?? [];
  const variables = variableCatalog(context.graph, context.startForm, context.templates, node.key);
  const setActions = (next: NodeAction[]) => context.onChange(updateNode(context.graph, node.key, { actions: next }));
  const patch = (index: number, value: Partial<NodeAction>) => setActions(actions.map((action, current) => current === index ? { ...action, ...value } : action));
  const outputs = actions.flatMap(action => actionOutputs(action.key, context.templates.get(action.templateId)));
  return <>
    {actions.map((action, index) => {
      const template = context.templates.get(action.templateId);
      const problems = context.problems.filter(problem => problem.nodeKey === node.key && problem.itemKey === action.key);
      const parameters = template?.actionType === 'PAGE_TASK' ? (template.config as PageTaskConfig | null)?.parameters ?? [] : [];
      return <div key={index} className={`wf-action is-expanded${problems.length || template?.actionType === 'DATA_QUERY' ? ' is-failed' : ''}`}>
        <div className="wf-action-head"><strong>{template?.templateName ?? action.key}</strong>{template && <code className="muted">{action.key}</code>}
          {template && <Tag color={template.actionType === 'DATA_QUERY' ? 'orange' : 'blue'}>{actionTypeLabels[template.actionType]}</Tag>}
          <span className="wf-grow" /><label className="wf-inline">必选 <Switch size="small" checked={action.required} disabled={context.readOnly}
            onChange={required => patch(index, { required })} /></label>
          {!context.readOnly && <Button size="small" type="text" danger onClick={() => setActions(actions.filter((_, current) => current !== index))}>移除</Button>}</div>
        <div className="wf-two-columns">
          <Field label="动作模板"><Select showSearch={{ optionFilterProp: 'label' }} value={action.templateId || undefined} disabled={context.readOnly} placeholder="从动作目录选择"
            options={context.templateList.map(item => ({ value: item.templateId, label: `${item.templateCode} · ${item.templateName}`,
              disabled: item.status !== 'ACTIVE' && item.templateId !== action.templateId }))}
            onChange={(templateId: string) => patch(index, { templateId, inputs: {} })} /></Field>
          <Field label="执行人（默认）"><PersonSelect value={action.executorId} people={context.people} disabled={context.readOnly} onChange={value => patch(index, { executorId: (value as string | undefined) || undefined })} /></Field>
        </div>
        <Field label="动作 key" hint={`输出变量以 ${action.key}. 开头`}><Input value={action.key} disabled={context.readOnly} maxLength={64}
          onChange={event => patch(index, { key: event.target.value.trim() })} /></Field>
        {template?.actionType === 'DATA_QUERY' && <div className="wf-danger-text">数据查询本期未开放，请删除或替换为页面任务</div>}
        {template && template.status !== 'ACTIVE' && <div className="wf-danger-text">该模板已停用，发布时会被拒绝</div>}
        {parameters.length > 0 && <Field label="跳转参数 ← 实例变量 / 常量">{parameters.map(parameter => {
          const mapping = action.inputs?.[parameter.key];
          return <div key={parameter.key} className="wf-mapping-row"><span>{parameter.key}<span className="muted"> {parameter.label}</span></span>←
            <Select value={mapping?.source ?? 'VARIABLE'} disabled={context.readOnly} style={{ width: 90 }}
              options={[{ value: 'VARIABLE', label: '变量' }, { value: 'CONSTANT', label: '常量' }]}
              onChange={source => patch(index, { inputs: { ...action.inputs, [parameter.key]: { source, value: '' } } })} />
            {(mapping?.source ?? 'VARIABLE') === 'VARIABLE'
              ? <Select allowClear value={mapping?.value || undefined} disabled={context.readOnly} style={{ flex: 1 }} placeholder="选择变量"
                options={variables.map(item => ({ value: item.name, label: `${item.name}（${item.label}）` }))}
                onChange={(value?: string) => patch(index, { inputs: { ...action.inputs, [parameter.key]: { source: 'VARIABLE', value: value ?? '' } } })} />
              : <Input value={mapping?.value} disabled={context.readOnly} style={{ flex: 1 }}
                onChange={event => patch(index, { inputs: { ...action.inputs, [parameter.key]: { source: 'CONSTANT', value: event.target.value } } })} />}
          </div>;
        })}</Field>}
        {problems.map((problem, current) => <div key={current} className="wf-danger-text">{problem.message}</div>)}
      </div>;
    })}
    {!context.readOnly && <Button block type="dashed" disabled={actions.length >= 20}
      onClick={() => setActions([...actions, { key: nextKey(context.graph, 'action'), templateId: '', required: true }])}>＋ 从动作目录添加</Button>}
    {outputs.length > 0 && <section className="wf-subtle"><div className="wf-field-label">本节点产出的变量（下游节点与条件可引用）</div>
      {outputs.map(item => <div key={item.name}><code>{item.name}</code> · {item.label}</div>)}</section>}
  </>;
}

const nodeTriggerList: NotificationTrigger[] = ['ACTIVATED', 'APPROVAL_OPENED', 'PASSED', 'RETURNED_FOR_REVISION', 'REJECTED', 'VERIFICATION_FAILED', 'TRANSFERRED'];
const recipients: NotificationRecipient[] = ['NODE_EXECUTORS', 'NODE_APPROVERS', 'STARTER', 'SPECIFIED_USERS'];
const channels: NotificationChannel[] = ['INBOX', 'EMAIL'];

/** 通知与回调规则（节点级或实例级）。 */
export function RulesEditor({ rules, triggers, readOnly, onChange, note, people }: {
  rules: NotificationRule[]; triggers: NotificationTrigger[]; readOnly: boolean; onChange: (rules: NotificationRule[]) => void; note: string;
  people?: Record<string, string>;
}) {
  const patch = (index: number, value: Partial<NotificationRule>) => onChange(rules.map((rule, current) => current === index ? { ...rule, ...value } : rule));
  return <>
    <p className="muted">{note}</p>
    {rules.map((rule, index) => <div key={index} className="wf-rule">
      <div className="wf-rule-row">当 <Select value={rule.trigger} disabled={readOnly} style={{ width: 150 }} options={triggers.map(value => ({ value, label: triggerLabels[value] }))}
        onChange={trigger => patch(index, { trigger })} /> 时
        <Radio.Group value={rule.type} disabled={readOnly} optionType="button" size="small" options={[{ value: 'NOTIFY', label: '通知人员' }, { value: 'CALLBACK', label: '业务回调' }]}
          onChange={event => patch(index, event.target.value === 'NOTIFY' ? { type: 'NOTIFY', callbackCode: undefined, recipients: ['NODE_EXECUTORS'], channels: ['INBOX', 'EMAIL'] }
            : { type: 'CALLBACK', recipients: undefined, channels: undefined, userIds: undefined, callbackCode: '' })} />
        <span className="wf-grow" />{!readOnly && <Button size="small" type="text" danger onClick={() => onChange(rules.filter((_, current) => current !== index))}>删除</Button>}</div>
      {rule.type === 'NOTIFY' ? <>
        <Field label="通知"><Select mode="multiple" value={rule.recipients ?? []} disabled={readOnly} style={{ width: '100%' }}
          options={recipients.map(value => ({ value, label: recipientLabels[value] }))} onChange={value => patch(index, { recipients: value })} /></Field>
        {rule.recipients?.includes('SPECIFIED_USERS') && <Field label="指定人员"><PersonSelect multiple value={rule.userIds ?? []} people={people} disabled={readOnly}
          onChange={value => patch(index, { userIds: value as string[] })} /></Field>}
        <Field label="渠道"><Select mode="multiple" value={rule.channels ?? []} disabled={readOnly} style={{ width: '100%' }}
          options={channels.map(value => ({ value, label: channelLabels[value] }))} onChange={value => patch(index, { channels: value })} /></Field>
      </> : <Field label="回调编码（订阅方据此识别）" hint="事件只携带实例 / 节点 / 轮次、业务关联键、状态变化与意见，不带表单值或文件；回调只是通知，订阅方执行前须自行校验">
        <Input value={rule.callbackCode} disabled={readOnly} maxLength={64} placeholder="例如 RISK_RULES_RETURNED"
          onChange={event => patch(index, { callbackCode: event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '') })} /></Field>}
    </div>)}
    {!readOnly && <Button block type="dashed" onClick={() => onChange([...rules, { trigger: triggers[0], type: 'NOTIFY', recipients: ['NODE_EXECUTORS'], channels: ['INBOX', 'EMAIL'] }])}>＋ 添加规则</Button>}
  </>;
}

function GatewayExits({ node, context }: { node: GraphNode; context: PanelContext }) {
  const exits = gatewayExits(context.graph, node.key);
  const variables = variableCatalog(context.graph, context.startForm, context.templates, node.key);
  return <section className="wf-subtle"><div className="wf-field-label">该网关的出口（按优先级依次判断，第一个满足的生效；都不满足走默认出口）</div>
    {exits.length ? exits.map(exit => <div key={exit.edge.key} className="wf-exit-line">
      <Tag>{exit.isDefault ? '默认' : exit.priority ?? '—'}</Tag><strong>{nodeOf(context.graph, exit.edge.to)?.name ?? exit.edge.to}</strong>
      <span className="muted">{exit.isDefault ? '以上都不满足' : clausesText(exit.clauses, variables)}</span></div>) : <Empty description="从网关右侧拖出连线添加出口" />}
    <p className="muted">点击连线配置条件、优先级与默认出口。</p></section>;
}

/** 选中连线：排他网关出边可配条件组（且关系）、优先级与默认出口（原型 09）。 */
export function EdgePanel({ edgeKey, context }: { edgeKey: string; context: PanelContext }) {
  const edge = context.graph.edges.find(item => item.key === edgeKey);
  if (!edge) return <Empty />;
  const source = nodeOf(context.graph, edge.from); const target = nodeOf(context.graph, edge.to);
  const header = <div className="wf-panel-head"><h3>{source?.name ?? edge.from} → {target?.name ?? edge.to}</h3><Tag>连线</Tag><span className="wf-grow" />
    {!context.readOnly && <Button type="text" danger onClick={context.onDelete}>删除</Button>}</div>;
  if (source?.type !== 'EXCLUSIVE') return <div className="wf-panel">{header}<p className="muted">只有排他网关的出边可以配置条件；并行分叉的出边同时激活。</p></div>;
  const exits = gatewayExits(context.graph, source.key);
  const exit = exits.find(item => item.edge.key === edgeKey)!;
  const conditional = exits.filter(item => !item.isDefault && item.priority !== null);
  const variables = variableCatalog(context.graph, context.startForm, context.templates, source.key);
  const problems = context.problems.filter(problem => problem.edgeKey === edgeKey);
  return <div className="wf-panel">{header}<ProblemList problems={problems} />
    <p className="muted">来源是排他网关：按优先级依次判断条件，第一个满足的出口生效；都不满足走默认出口。</p>
    <div className="wf-rule-row">
      {!exit.isDefault && exit.priority !== null && <>优先级 <Select value={exit.priority} disabled={context.readOnly} style={{ width: 140 }}
        options={conditional.map((_, index) => ({ value: index + 1, label: `${index + 1}（共 ${conditional.length} 个出口）` }))}
        onChange={priority => context.onChange(setExitPriority(context.graph, source.key, edgeKey, priority))} /></>}
      <span className="wf-grow" /><label className="wf-inline">设为默认出口 <Switch size="small" checked={exit.isDefault} disabled={context.readOnly || exit.isDefault}
        onChange={() => context.onChange(setDefaultExit(context.graph, source.key, edgeKey))} /></label>
    </div>
    {exit.isDefault ? <Alert type="info" showIcon title="默认出口不带条件：以上条件都不满足时走这条线" />
      : <ConditionGroup clauses={exit.clauses} variables={variables} readOnly={context.readOnly}
        onChange={clauses => context.onChange(setEdgeClauses(context.graph, source.key, edgeKey, clauses))} />}
    <section className="wf-subtle"><div className="wf-field-label">可引用的变量（启动表单 + 上游节点输出）</div>
      <div className="wf-variable-chips">{variables.map(item => <Tag key={item.name} color="purple">{item.name}</Tag>)}</div>
      <p className="muted">并行分支内部的输出在汇合之后才可引用。</p></section>
    <GatewayExits node={source} context={context} />
  </div>;
}

/** 条件组：每行变量 / 比较符 / 值，行与行之间是“且”；“或”用多条出边表达。 */
export function ConditionGroup({ clauses, variables, readOnly, onChange }: {
  clauses: ConditionClause[]; variables: VariableInfo[]; readOnly: boolean; onChange: (clauses: ConditionClause[]) => void;
}) {
  const patch = (index: number, clause: ConditionClause) => onChange(clauses.map((item, current) => current === index ? clause : item));
  return <section className="wf-condition">
    <div className="wf-field-label">满足以下全部条件时走这条线</div>
    {clauses.map((clause, index) => {
      const variable = variables.find(item => item.name === clause.variable);
      const operators = variable ? operatorsByType[variable.type] : Object.keys(operatorLabels) as ConditionClause['operator'][];
      const shape = valueShape(clause.operator);
      const enumOptions = variable?.options?.map(option => ({ value: option.value, label: `${option.value} · ${option.label}` }));
      return <div key={index} className="wf-condition-row">
        <Select showSearch={{ optionFilterProp: 'label' }} value={clause.variable || undefined} disabled={readOnly} placeholder="变量"
          status={clause.variable && !variable ? 'error' : undefined}
          options={variables.map(item => ({ value: item.name, label: `${item.name}（${item.label}）` }))}
          onChange={(name: string) => { const info = variables.find(item => item.name === name); if (info) patch(index, changeVariable(clause, info)); }} />
        <Select value={clause.operator} disabled={readOnly} style={{ width: 110 }} options={operators.map(value => ({ value, label: operatorLabels[value] }))}
          onChange={operator => patch(index, changeOperator(clause, operator))} />
        {shape === 'NONE' ? <span className="wf-grow" /> : enumOptions?.length
          ? <Select mode={shape === 'LIST' ? 'multiple' : undefined} value={clause.value as never} disabled={readOnly} style={{ flex: 1 }} options={enumOptions}
            onChange={value => patch(index, { ...clause, value })} />
          : shape === 'LIST' ? <Select mode="tags" value={(clause.value as string[]) ?? []} disabled={readOnly} style={{ flex: 1 }} placeholder="输入后回车"
            onChange={value => patch(index, { ...clause, value })} />
          : <Input value={clause.value as string} disabled={readOnly} style={{ flex: 1 }} placeholder={variable?.type === 'DATE' ? 'YYYY-MM-DD' : variable?.type === 'BOOLEAN' ? 'true / false' : '值'}
            onChange={event => patch(index, { ...clause, value: event.target.value })} />}
        {!readOnly && <Button type="text" danger onClick={() => onChange(clauses.filter((_, current) => current !== index))}>✕</Button>}
      </div>;
    })}
    {clauses.some(clause => clause.variable && !variables.some(item => item.name === clause.variable)) && <div className="wf-danger-text">有条件引用的变量不在可用范围内（来自下游、另一并行分支或已被删除）</div>}
    {!readOnly && <Space><Button type="link" disabled={!variables.length}
      onClick={() => onChange([...clauses, changeVariable({ variable: '', operator: 'EQ', value: '' }, variables[0])])}>＋ 添加条件</Button>
      {!variables.length && <span className="muted">先在启动表单或上游动作中声明变量</span>}</Space>}
  </section>;
}
