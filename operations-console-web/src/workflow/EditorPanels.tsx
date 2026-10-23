import { useState } from 'react';
import { Alert, Button, Dropdown, Empty, Input, Modal, Radio, Segmented, Select, Space, Switch, Tabs, Tag } from 'antd';
import type { ReactNode } from 'react';
import { ArrowRightOutlined, CloseOutlined, DownOutlined, EllipsisOutlined, ExportOutlined, FileTextOutlined, PlusOutlined, RightOutlined } from '@ant-design/icons';
import { instructionLine } from './definitionModel';
import {
  actionOutputs, changeOperator, changeVariable, clausesText, gatewayExits, keyPattern, nextKey, nodeOf, operatorsByType, renameNode, setDefaultExit,
  setEdgeClauses, setExitPriority, updateNode, usedKeys, valueShape, variableCatalog,
} from './graphModel';
import type { VariableInfo } from './graphModel';
import { actionTypeIcons, nodeTypeIcons } from './workflowIcons';
import { actionTypeLabels, channelLabels, nodeTypeLabels, operatorLabels, recipientLabels, triggerLabels } from './workflowLabels';
import type {
  ActionTemplate, ActionType, ConditionClause, FormDefinition, GraphNode, NodeAction, NotificationChannel, NotificationRecipient, NotificationRule,
  NotificationTrigger, PageTaskConfig, Problem, WorkflowGraph,
} from './workflowTypes';
import { Field, PersonSelect, workflowRoutes } from './WorkflowParts';

export interface PanelContext {
  graph: WorkflowGraph; startForm: FormDefinition; templates: Map<string, ActionTemplate>; templateList: ActionTemplate[];
  readOnly: boolean; problems: Problem[]; people: Record<string, string>; onChange: (graph: WorkflowGraph) => void; onDelete: () => void;
  /** 关闭属性面板（取消选中）。 */
  onClose: () => void;
  /** 就地新建动作（需求 D4）：宿主弹出类型选择与动作抽屉，创建成功后加入该节点；没有「维护动作」权限时不提供。 */
  onRequestNewAction?: (nodeKey: string) => void;
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
  return problems.length ? <Alert type="warning" showIcon className="wf-panel-problems"
    title={<ul className="wf-plain-list">{problems.map((problem, index) => <li key={index}>{problem.message}</li>)}</ul>} /> : null;
}

/** 面板页头：图标、名称、类型说明、关闭。 */
function PanelHead({ icon, title, subtitle, onClose }: { icon: ReactNode; title: string; subtitle: string; onClose: () => void }) {
  return <div className="wf-panel-head"><span className="wf-panel-icon" aria-hidden>{icon}</span>
    <div className="wf-grow"><h3>{title}</h3><div className="muted">{subtitle}</div></div>
    <Button type="text" aria-label="关闭属性面板" icon={<CloseOutlined />} onClick={onClose} /></div>;
}

function DeleteLink({ label, context }: { label: string; context: PanelContext }) {
  return context.readOnly ? null : <div className="wf-panel-foot"><Button type="link" danger size="small" onClick={context.onDelete}>{label}</Button>
    <span className="muted">也可选中后按 Delete</span></div>;
}

/** 选中节点后的属性面板：任务节点按「基本 / 动作 / 审批 / 通知与回调」分组（原型 11、16）。 */
export function NodePanel({ node, context }: { node: GraphNode; context: PanelContext }) {
  // 动作上的问题显示在对应动作卡里，其余（含审批人、通知规则等条目）显示在面板顶部。
  const actionKeys = new Set((node.actions ?? []).map(action => action.key));
  const problems = context.problems.filter(problem => problem.nodeKey === node.key && !problem.edgeKey && !(problem.itemKey && actionKeys.has(problem.itemKey)));
  if (node.type !== 'TASK') {
    return <div className="wf-panel"><PanelHead icon={nodeTypeIcons[node.type]} title={node.name || node.key} subtitle={nodeTypeLabels[node.type]} onClose={context.onClose} />
      <ProblemList problems={problems} /><NameAndKey key={node.key} node={node} context={context} />
      {node.type === 'EXCLUSIVE' && <GatewayExits node={node} context={context} />}
      {node.type === 'START' && <p className="muted">开始节点固定一个；发起时填写的启动表单在“启动表单”页签配置。</p>}
      {(node.type === 'PARALLEL_SPLIT' || node.type === 'PARALLEL_JOIN') && <p className="muted">并行分叉与汇合成对使用、一一对应；分支内部不能跨分支连线，不允许嵌套并行，结束节点不能在并行块内部。</p>}
      {node.type !== 'START' && <DeleteLink label="删除节点" context={context} />}
    </div>;
  }
  const actions = node.actions ?? [];
  const approvers = node.approval?.approverIds ?? [];
  const subtitle = ['任务节点', [actions.length ? '动作' : '', approvers.length ? '审批' : ''].filter(Boolean).join(' + ') || '未配置'].join(' · ');
  return <div className="wf-panel"><PanelHead icon={nodeTypeIcons.TASK} title={node.name || node.key} subtitle={subtitle} onClose={context.onClose} />
    <ProblemList problems={problems} />
    <Tabs size="small" defaultActiveKey={actions.length || !approvers.length ? 'actions' : 'approval'} items={[
      { key: 'basic', label: '基本', children: <><NameAndKey key={node.key} node={node} context={context} />
        <p className="muted">必选动作全部完成后，节点进入审批；未配置审批人则自动放行。</p><DeleteLink label="删除节点" context={context} /></> },
      { key: 'actions', label: <span>动作 <span className="wf-tab-count">{actions.length}</span></span>, children: <ActionsEditor node={node} context={context} /> },
      { key: 'approval', label: '审批', children: <ApprovalEditor node={node} context={context} /> },
      { key: 'notify', label: `通知与回调${(node.notifications ?? []).length ? ` ${(node.notifications ?? []).length}` : ''}`, children: <RulesEditor rules={node.notifications ?? []} triggers={nodeTriggerList}
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
    {!approval.approverIds.length && <p className="muted">不设审批人：必选动作全部完成后自动放行。</p>}
    <p className="muted">同一人不能既执行本节点动作又审批本节点（审批时校验）。</p>
  </>;
}

const NEW_ACTION = '__new_action__';

/** 节点动作（原型 16）：每个动作一张卡（必选 / 可选、默认执行人、输入参数、说明首行），底部搜索添加或就地新建。 */
function ActionsEditor({ node, context }: { node: GraphNode; context: PanelContext }) {
  const actions = node.actions ?? [];
  const [dragging, setDragging] = useState<number | null>(null);
  const [renaming, setRenaming] = useState<number | null>(null);
  const setActions = (next: NodeAction[]) => context.onChange(updateNode(context.graph, node.key, { actions: next }));
  const patch = (index: number, value: Partial<NodeAction>) => setActions(actions.map((action, current) => current === index ? { ...action, ...value } : action));
  const outputs = actions.flatMap(action => actionOutputs(action.key, context.templates.get(action.templateId)));
  const used = new Set(actions.map(action => action.templateId));
  const options = [
    ...context.templateList.filter(item => item.status === 'ACTIVE' && item.actionType !== 'DATA_QUERY').map(item => ({
      value: item.templateId, disabled: used.has(item.templateId), search: `${item.templateName} ${item.templateCode}`,
      label: <span className="wf-option-line"><span>{item.templateName}</span><span className="muted">{used.has(item.templateId) ? '已添加' : actionTypeLabels[item.actionType]}</span></span> })),
    ...(context.onRequestNewAction ? [{ value: NEW_ACTION, disabled: false, search: '',
      label: <span className="wf-option-line is-new"><span><PlusOutlined /> 新建动作…</span><span className="muted">不离开编辑器</span></span> }] : []),
  ];
  function add(templateId: string) {
    if (templateId === NEW_ACTION) { context.onRequestNewAction?.(node.key); return; }
    setActions([...actions, { key: nextKey(context.graph, 'action'), templateId, required: true }]);
  }
  function drop(target: number) {
    if (dragging === null || dragging === target) return;
    const next = [...actions]; const [moved] = next.splice(dragging, 1); next.splice(target, 0, moved);
    setDragging(null); setActions(next);
  }
  return <div className="wf-node-actions">
    <p className="muted">处理人在本节点需要完成的动作，执行人逐个完成。</p>
    {actions.map((action, index) => <ActionCard key={`${action.key}:${index}`} node={node} action={action} context={context}
      dragging={dragging === index} onDragStart={() => setDragging(index)} onDragEnd={() => setDragging(null)} onDrop={() => drop(index)}
      onPatch={value => patch(index, value)} onRemove={() => setActions(actions.filter((_, current) => current !== index))}
      onRename={() => setRenaming(index)} />)}
    {!context.readOnly && actions.length < 20 && <Select className="wf-add-action" showSearch={{ filterOption: (input, option) =>
        option?.value === NEW_ACTION || String(option?.search ?? '').toLowerCase().includes(input.trim().toLowerCase()) }}
      value={null} placeholder={<><PlusOutlined /> 添加动作</>} options={options} onSelect={(value: string | null) => { if (value) add(value); }}
      notFoundContent={<span className="muted">没有可用的动作</span>} />}
    <div className="wf-hint-box">
      <div>必选动作全部完成后，节点才进入审批；可选动作不阻塞放行</div>
      <div>同一人不能既执行本节点动作又审批本节点</div>
    </div>
    {outputs.length > 0 && <section className="wf-subtle"><div className="wf-field-label">本节点产出的变量（下游节点与条件可引用）</div>
      {outputs.map(item => <div key={item.name}><code>{item.name}</code> · {item.label}</div>)}</section>}
    {renaming !== null && actions[renaming] && <ActionKeyModal graph={context.graph} actionKey={actions[renaming].key}
      onCancel={() => setRenaming(null)} onSave={key => { patch(renaming, { key }); setRenaming(null); }} />}
  </div>;
}

function ActionCard({ node, action, context, dragging, onDragStart, onDragEnd, onDrop, onPatch, onRemove, onRename }: {
  node: GraphNode; action: NodeAction; context: PanelContext; dragging: boolean; onDragStart: () => void; onDragEnd: () => void; onDrop: () => void;
  onPatch: (value: Partial<NodeAction>) => void; onRemove: () => void; onRename: () => void;
}) {
  const [inputsOpen, setInputsOpen] = useState(false);
  const template = context.templates.get(action.templateId);
  const problems = context.problems.filter(problem => problem.nodeKey === node.key && problem.itemKey === action.key);
  const parameters = template?.actionType === 'PAGE_TASK' ? (template.config as PageTaskConfig | null)?.parameters ?? [] : [];
  const mapped = parameters.filter(parameter => action.inputs?.[parameter.key]?.value).length;
  const variables = variableCatalog(context.graph, context.startForm, context.templates, node.key);
  const instruction = template ? instructionLine(template) : '';
  const setInput = (key: string, mapping: { source: 'VARIABLE' | 'CONSTANT'; value: string }) => onPatch({ inputs: { ...action.inputs, [key]: mapping } });
  return <div className={`wf-node-action${dragging ? ' is-dragging' : ''}${problems.length ? ' has-problem' : ''}`}
    onDragOver={event => { if (!context.readOnly) event.preventDefault(); }} onDrop={event => { event.preventDefault(); onDrop(); }}>
    <div className="wf-node-action-head">
      {!context.readOnly && <span className="wf-drag-handle" draggable title="拖动排序" onDragStart={onDragStart} onDragEnd={onDragEnd}>⠿</span>}
      {template && <ActionTypeIcon type={template.actionType} />}
      <strong className="wf-grow" title={action.key}>{template?.templateName ?? (action.templateId ? '读取中…' : '未选择动作')}
        {template && <span className="muted"> · {actionTypeLabels[template.actionType]}</span>}</strong>
      {action.templateId && <a className="wf-link-small" href={`${workflowRoutes.templates}/${action.templateId}`} target="_blank" rel="noreferrer">在动作目录中编辑 <ExportOutlined /></a>}
      {!context.readOnly && <Dropdown trigger={['click']} menu={{ items: [{ key: 'key', label: `修改动作 key（${action.key}）` }, { type: 'divider' }, { key: 'remove', label: '移除', danger: true }],
        onClick: ({ key }) => key === 'remove' ? onRemove() : onRename() }}><Button type="text" size="small" aria-label="更多" icon={<EllipsisOutlined />} /></Dropdown>}
    </div>
    <div className="wf-node-action-row">
      <Segmented size="small" value={action.required ? 'required' : 'optional'} disabled={context.readOnly}
        options={[{ value: 'required', label: '必选' }, { value: 'optional', label: '可选' }]} onChange={value => onPatch({ required: value === 'required' })} />
      <span className="muted">执行人</span>
      <PersonSelect value={action.executorId} people={context.people} disabled={context.readOnly} placeholder="默认执行人 · 发起时可改"
        style={{ flex: 1, minWidth: 0 }} onChange={value => onPatch({ executorId: (value as string | undefined) || undefined })} />
    </div>
    <button type="button" className="wf-collapse-toggle" onClick={() => setInputsOpen(value => !value)} disabled={!parameters.length}>
      <span aria-hidden>{inputsOpen && parameters.length ? <DownOutlined /> : <RightOutlined />}</span> 输入参数 <span className="muted">{parameters.length ? `${parameters.length} 项${mapped ? ` · 已映射 ${mapped}` : ''}` : '无'}</span></button>
    {inputsOpen && parameters.length > 0 && <div className="wf-subtle wf-node-action-inputs">{parameters.map(parameter => {
      const mapping = action.inputs?.[parameter.key];
      return <div key={parameter.key} className="wf-mapping-row"><span className="wf-mapping-label">{parameter.label || parameter.key}</span>←
        <Select value={mapping?.source ?? 'VARIABLE'} disabled={context.readOnly} style={{ width: 80 }} size="small"
          options={[{ value: 'VARIABLE', label: '变量' }, { value: 'CONSTANT', label: '常量' }]}
          onChange={source => setInput(parameter.key, { source, value: '' })} />
        {(mapping?.source ?? 'VARIABLE') === 'VARIABLE'
          ? <Select allowClear size="small" value={mapping?.value || undefined} disabled={context.readOnly} style={{ flex: 1, minWidth: 120 }} placeholder="选择变量"
            options={variables.map(item => ({ value: item.name, label: `${item.source}.${item.label}` }))}
            onChange={(value?: string) => setInput(parameter.key, { source: 'VARIABLE', value: value ?? '' })} />
          : <Input size="small" value={mapping?.value} disabled={context.readOnly} style={{ flex: 1, minWidth: 120 }}
            onChange={event => setInput(parameter.key, { source: 'CONSTANT', value: event.target.value })} />}
      </div>;
    })}</div>}
    {instruction && <div className="wf-node-action-instruction muted" title={instruction}><FileTextOutlined /> {instruction}</div>}
    {template?.actionType === 'DATA_QUERY' && <div className="wf-danger-text">数据查询本期未开放，请移除或替换</div>}
    {template && template.status !== 'ACTIVE' && <div className="wf-danger-text">该动作已停用，发布时会被拒绝</div>}
    {problems.map((problem, current) => <div key={current} className="wf-warn-text">{problem.message}</div>)}
  </div>;
}

function ActionTypeIcon({ type }: { type: ActionType }) {
  return <span className={`wf-action-type-icon is-${type.toLowerCase()}`} aria-hidden>{actionTypeIcons[type]}</span>;
}

/** 修改动作 key：格式与唯一性同节点 key；条件中已引用的旧变量名需自行调整。 */
function ActionKeyModal({ graph, actionKey, onCancel, onSave }: { graph: WorkflowGraph; actionKey: string; onCancel: () => void; onSave: (key: string) => void }) {
  const [value, setValue] = useState(actionKey);
  const next = value.trim();
  const error = next === actionKey ? '' : !keyPattern.test(next) ? 'key 以字母开头，只能包含字母、数字和下划线，最长 64 位'
    : next === 'start' ? '动作 key 不能是 start，它是启动变量的前缀' : usedKeys(graph).has(next) ? `key ${next} 已被使用` : '';
  return <Modal open title="修改动作 key" okText="保存" okButtonProps={{ disabled: !!error || next === actionKey }} onCancel={onCancel} onOk={() => onSave(next)}>
    <Field label="动作 key" hint={error || `输出变量以 ${next || actionKey}. 开头；已在条件或输入参数中引用的旧变量需要同步修改`}>
      <Input value={value} maxLength={64} status={error ? 'error' : undefined} onChange={event => setValue(event.target.value)} /></Field>
  </Modal>;
}

/** 「动作验证未通过」已停用（需求 E5），不再作为可选触发。 */
const nodeTriggerList: NotificationTrigger[] = ['ACTIVATED', 'APPROVAL_OPENED', 'PASSED', 'RETURNED_FOR_REVISION', 'REJECTED', 'TRANSFERRED'];
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
      <div className="wf-rule-row">当 <Select value={rule.trigger} disabled={readOnly} style={{ width: 150 }} options={[...triggers.map(value => ({ value, label: triggerLabels[value] })),
          // 历史配置里已停用的触发只显示、不可再选，需改为其他触发。
          ...(triggers.includes(rule.trigger) ? [] : [{ value: rule.trigger, label: `${triggerLabels[rule.trigger] ?? rule.trigger}（已停用）`, disabled: true }])]}
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
    {!readOnly && <Button block type="dashed" onClick={() => onChange([...rules, { trigger: triggers[0], type: 'NOTIFY', recipients: ['NODE_EXECUTORS'], channels: ['INBOX', 'EMAIL'] }])} icon={<PlusOutlined />}>添加规则</Button>}
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
  const header = <PanelHead icon={<ArrowRightOutlined />} title={`${source?.name || edge.from} → ${target?.name || edge.to}`} subtitle="连线" onClose={context.onClose} />;
  if (source?.type !== 'EXCLUSIVE') return <div className="wf-panel">{header}<p className="muted">只有排他网关的出边可以配置条件；并行分叉的出边同时激活。也可以把鼠标移到连线上，点「+」在这条线上插入节点。</p>
    <DeleteLink label="删除连线" context={context} /></div>;
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
    <DeleteLink label="删除连线" context={context} />
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
        {!readOnly && <Button type="text" danger icon={<CloseOutlined />} aria-label="删除条件" onClick={() => onChange(clauses.filter((_, current) => current !== index))} />}
      </div>;
    })}
    {clauses.some(clause => clause.variable && !variables.some(item => item.name === clause.variable)) && <div className="wf-danger-text">有条件引用的变量不在可用范围内（来自下游、另一并行分支或已被删除）</div>}
    {!readOnly && <Space><Button type="link" disabled={!variables.length}
      onClick={() => onChange([...clauses, changeVariable({ variable: '', operator: 'EQ', value: '' }, variables[0])])} icon={<PlusOutlined />}>添加条件</Button>
      {!variables.length && <span className="muted">先在启动表单或上游动作中声明变量</span>}</Space>}
  </section>;
}
