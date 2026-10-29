import { useState } from 'react';
import { Alert, Button, Dropdown, Empty, Input, Modal, Radio, Segmented, Select, Space, Switch, Tabs, Tag } from 'antd';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRightOutlined, CloseOutlined, DownOutlined, EllipsisOutlined, FileTextOutlined, PlusOutlined, RightOutlined, SafetyCertificateOutlined,
} from '@ant-design/icons';
import { criteriaText } from './dataQueryModel';
import { dataQueryParameters } from './templateModel';
import { enumLabel } from '../i18n/index';
import { instructionLine } from './definitionModel';
import {
  actionOutputs, changeOperator, changeVariable, clausesText, gatewayExits, keyPattern, nextKey, nodeOf, operatorsByType, renameNode, setDefaultExit,
  setEdgeClauses, setExitPriority, updateNode, usedKeys, valueShape, variableCatalog,
} from './graphModel';
import type { VariableInfo } from './graphModel';
import { actionTypeIcons, nodeTypeIcons } from './workflowIcons';
import { actionTypeLabel, channelLabel, nodeTypeLabel, operatorLabel, operators as allOperators, recipientLabel, triggerLabel } from './workflowLabels';
import { useT } from '../i18n/react';
import type {
  ActionTemplate, ActionType, ConditionClause, DataQueryConfig, FormDefinition, GraphNode, NodeAction, NotificationChannel, NotificationRecipient, NotificationRule,
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
  const t = useT('workflow');
  const [key, setKey] = useState(node.key); const [error, setError] = useState('');
  function commitKey() {
    const result = renameNode(context.graph, node.key, key.trim());
    if ('error' in result) { setError(result.error); return; }
    setError(''); context.onChange(result.graph);
  }
  return <>
    <Field label={t('panel.name')}><Input value={node.name} maxLength={64} disabled={context.readOnly}
      onChange={event => context.onChange(updateNode(context.graph, node.key, { name: event.target.value }))} /></Field>
    <Field label={t('panel.nodeKey')} hint={error || t('panel.nodeKeyHint')}>
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
  const t = useT('workflow');
  return <div className="wf-panel-head"><span className="wf-panel-icon" aria-hidden>{icon}</span>
    <div className="wf-grow"><h3>{title}</h3><div className="muted">{subtitle}</div></div>
    <Button type="text" aria-label={t('panel.close')} icon={<CloseOutlined />} onClick={onClose} /></div>;
}

function DeleteLink({ label, context }: { label: string; context: PanelContext }) {
  const t = useT('workflow');
  return context.readOnly ? null : <div className="wf-panel-foot"><Button type="link" danger size="small" onClick={context.onDelete}>{label}</Button>
    <span className="muted">{t('panel.deleteKeyHint')}</span></div>;
}

/** 选中节点后的属性面板：任务节点按「基本 / 动作 / 审批 / 通知与回调」分组（原型 11、16）。 */
export function NodePanel({ node, context }: { node: GraphNode; context: PanelContext }) {
  const t = useT('workflow');
  // 动作上的问题显示在对应动作卡里，其余（含审批人、通知规则等条目）显示在面板顶部。
  const actionKeys = new Set((node.actions ?? []).map(action => action.key));
  const problems = context.problems.filter(problem => problem.nodeKey === node.key && !problem.edgeKey && !(problem.itemKey && actionKeys.has(problem.itemKey)));
  if (node.type !== 'TASK') {
    return <div className="wf-panel"><PanelHead icon={nodeTypeIcons[node.type]} title={node.name || node.key} subtitle={nodeTypeLabel(node.type)} onClose={context.onClose} />
      <ProblemList problems={problems} /><NameAndKey key={node.key} node={node} context={context} />
      {node.type === 'EXCLUSIVE' && <GatewayExits node={node} context={context} />}
      {node.type === 'START' && <p className="muted">{t('panel.startHint')}</p>}
      {(node.type === 'PARALLEL_SPLIT' || node.type === 'PARALLEL_JOIN') && <p className="muted">{t('panel.parallelHint')}</p>}
      {node.type !== 'START' && <DeleteLink label={t('panel.deleteNode')} context={context} />}
    </div>;
  }
  const actions = node.actions ?? [];
  const approvers = node.approval?.approverIds ?? [];
  const subtitle = [nodeTypeLabel('TASK'), [actions.length ? t('panel.tab.actions') : '', approvers.length ? t('panel.tab.approval') : ''].filter(Boolean).join(' + ')
    || t('panel.notConfigured')].join(' · ');
  return <div className="wf-panel"><PanelHead icon={nodeTypeIcons.TASK} title={node.name || node.key} subtitle={subtitle} onClose={context.onClose} />
    <ProblemList problems={problems} />
    <Tabs size="small" defaultActiveKey={actions.length || !approvers.length ? 'actions' : 'approval'} items={[
      { key: 'basic', label: t('panel.tab.basic'), children: <><NameAndKey key={node.key} node={node} context={context} />
        <p className="muted">{t('panel.taskHint')}</p><DeleteLink label={t('panel.deleteNode')} context={context} /></> },
      { key: 'actions', label: <span>{t('panel.tab.actions')} <span className="wf-tab-count">{actions.length}</span></span>, children: <ActionsEditor node={node} context={context} /> },
      { key: 'approval', label: t('panel.tab.approval'), children: <ApprovalEditor node={node} context={context} /> },
      { key: 'notify', label: `${t('panel.tab.notify')}${(node.notifications ?? []).length ? ` ${(node.notifications ?? []).length}` : ''}`, children: <RulesEditor rules={node.notifications ?? []} triggers={nodeTriggerList}
        readOnly={context.readOnly} people={context.people} onChange={notifications => context.onChange(updateNode(context.graph, node.key, { notifications }))}
        note={t('panel.nodeRulesNote')} /> },
    ]} />
  </div>;
}

function ApprovalEditor({ node, context }: { node: GraphNode; context: PanelContext }) {
  const t = useT('workflow');
  const approval = node.approval ?? { mode: 'ANY' as const, approverIds: [] };
  const set = (patch: Partial<typeof approval>) => context.onChange(updateNode(context.graph, node.key, { approval: { ...approval, ...patch } }));
  return <>
    <Field label={t('approval.mode')}><Radio.Group value={approval.mode} disabled={context.readOnly} onChange={event => set({ mode: event.target.value })}
      options={[{ value: 'ANY', label: t('approval.modeAny') }, { value: 'ALL', label: t('approval.modeAll') }]} /></Field>
    <Field label={t('approval.approvers')} hint={t('approval.approversHint')}>
      <PersonSelect multiple value={approval.approverIds} people={context.people} disabled={context.readOnly} onChange={value => set({ approverIds: value as string[] })} /></Field>
    {!approval.approverIds.length && <p className="muted">{t('approval.noApprovers')}</p>}
    <p className="muted">{t('approval.sameUser')}</p>
  </>;
}

const NEW_ACTION = '__new_action__';

/** 节点动作（原型 16）：每个动作一张卡（必选 / 可选、默认执行人、输入参数、说明首行），底部搜索添加或就地新建。 */
function ActionsEditor({ node, context }: { node: GraphNode; context: PanelContext }) {
  const t = useT('workflow');
  const actions = node.actions ?? [];
  const [dragging, setDragging] = useState<number | null>(null);
  const [renaming, setRenaming] = useState<number | null>(null);
  const setActions = (next: NodeAction[]) => context.onChange(updateNode(context.graph, node.key, { actions: next }));
  const patch = (index: number, value: Partial<NodeAction>) => setActions(actions.map((action, current) => current === index ? { ...action, ...value } : action));
  const outputs = actions.flatMap(action => actionOutputs(action.key, context.templates.get(action.templateId)));
  const used = new Set(actions.map(action => action.templateId));
  const options = [
    ...context.templateList.filter(item => item.status === 'ACTIVE').map(item => ({
      value: item.templateId, disabled: used.has(item.templateId), search: `${item.templateName} ${item.templateCode}`,
      label: <span className="wf-option-line"><span>{item.templateName}</span><span className="muted">{used.has(item.templateId) ? t('actions.added') : actionTypeLabel(item.actionType)}</span></span> })),
    ...(context.onRequestNewAction ? [{ value: NEW_ACTION, disabled: false, search: '',
      label: <span className="wf-option-line is-new"><span><PlusOutlined /> {t('actions.new')}</span><span className="muted">{t('actions.newHint')}</span></span> }] : []),
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
    <p className="muted">{t('actions.hint')}</p>
    {actions.map((action, index) => <ActionCard key={`${action.key}:${index}`} node={node} action={action} context={context}
      dragging={dragging === index} onDragStart={() => setDragging(index)} onDragEnd={() => setDragging(null)} onDrop={() => drop(index)}
      onPatch={value => patch(index, value)} onRemove={() => setActions(actions.filter((_, current) => current !== index))}
      onRename={() => setRenaming(index)} />)}
    {!context.readOnly && actions.length < 20 && <Select className="wf-add-action" showSearch={{ filterOption: (input, option) =>
        option?.value === NEW_ACTION || String(option?.search ?? '').toLowerCase().includes(input.trim().toLowerCase()) }}
      value={null} placeholder={<><PlusOutlined /> {t('actions.add')}</>} options={options} onSelect={(value: string | null) => { if (value) add(value); }}
      notFoundContent={<span className="muted">{t('actions.none')}</span>} />}
    <div className="wf-hint-box">
      <div>{t('actions.requiredHint')}</div>
      <div>{t('actions.sameUser')}</div>
      {actions.some(action => context.templates.get(action.templateId)?.actionType === 'DATA_QUERY') && <>
        <div>{t('actions.dataQueryHint')}</div><div>{t('actions.dataQueryOutputsHint', { actionKey: t('config.actionKey'), outputName: t('dataQuery.outputNameArg') })}</div></>}
    </div>
    {outputs.length > 0 && <section className="wf-subtle"><div className="wf-field-label">{t('actions.outputs')}</div>
      {outputs.map(item => <div key={item.name}><code>{item.name}</code> · {item.label}</div>)}</section>}
    {renaming !== null && actions[renaming] && <ActionKeyModal graph={context.graph} actionKey={actions[renaming].key}
      onCancel={() => setRenaming(null)} onSave={key => { patch(renaming, { key }); setRenaming(null); }} />}
  </div>;
}

function ActionCard({ node, action, context, dragging, onDragStart, onDragEnd, onDrop, onPatch, onRemove, onRename }: {
  node: GraphNode; action: NodeAction; context: PanelContext; dragging: boolean; onDragStart: () => void; onDragEnd: () => void; onDrop: () => void;
  onPatch: (value: Partial<NodeAction>) => void; onRemove: () => void; onRename: () => void;
}) {
  const t = useT('workflow');
  const template = context.templates.get(action.templateId);
  const dataQuery = template?.actionType === 'DATA_QUERY';
  // 数据查询的参数与输出默认展开（原型 18）。
  const [inputsToggle, setInputsOpen] = useState<boolean | null>(null);
  const inputsOpen = inputsToggle ?? dataQuery;
  const problems = context.problems.filter(problem => problem.nodeKey === node.key && problem.itemKey === action.key);
  const parameters = template?.actionType === 'PAGE_TASK' ? (template.config as PageTaskConfig | null)?.parameters ?? []
    : dataQuery ? dataQueryParameters(template.config as DataQueryConfig | null).map(key => ({ key, label: key })) : [];
  const outputs = dataQuery ? actionOutputs(action.key, template) : [];
  const criteria = dataQuery ? criteriaText(template.config as DataQueryConfig | null) : '';
  const mapped = parameters.filter(parameter => action.inputs?.[parameter.key]?.value).length;
  const variables = variableCatalog(context.graph, context.startForm, context.templates, node.key);
  const instruction = template ? instructionLine(template) : '';
  const setInput = (key: string, mapping: { source: 'VARIABLE' | 'CONSTANT'; value: string }) => onPatch({ inputs: { ...action.inputs, [key]: mapping } });
  return <div className={`wf-node-action${dragging ? ' is-dragging' : ''}${problems.length ? ' has-problem' : ''}`}
    onDragOver={event => { if (!context.readOnly) event.preventDefault(); }} onDrop={event => { event.preventDefault(); onDrop(); }}>
    <div className="wf-node-action-head">
      {!context.readOnly && <span className="wf-drag-handle" draggable title={t('designer.dragToSort')} onDragStart={onDragStart} onDragEnd={onDragEnd}>⠿</span>}
      {template && <ActionTypeIcon type={template.actionType} />}
      <strong className="wf-grow" title={action.key}>{template?.templateName ?? (action.templateId ? t('actions.loading') : t('actions.notSelected'))}
        {template && <span className="muted"> · {actionTypeLabel(template.actionType)}</span>}</strong>
      {action.templateId && <Link className="wf-link-small" to={`${workflowRoutes.templates}/${action.templateId}`}>{t('actions.editInCatalog')} <ArrowRightOutlined /></Link>}
      {!context.readOnly && <Dropdown trigger={['click']} menu={{ items: [{ key: 'key', label: t('actions.renameKey', { key: action.key }) }, { type: 'divider' }, { key: 'remove', label: t('files.remove'), danger: true }],
        onClick: ({ key }) => key === 'remove' ? onRemove() : onRename() }}><Button type="text" size="small" aria-label={t('actions.more')} icon={<EllipsisOutlined />} /></Dropdown>}
    </div>
    <div className="wf-node-action-row">
      <Segmented size="small" value={action.required ? 'required' : 'optional'} disabled={context.readOnly}
        options={[{ value: 'required', label: t('actions.required') }, { value: 'optional', label: t('actions.optional') }]} onChange={value => onPatch({ required: value === 'required' })} />
      <span className="muted">{dataQuery ? t('actions.follower') : t('actions.executor')}</span>
      <PersonSelect value={action.executorId} people={context.people} disabled={context.readOnly} placeholder={dataQuery ? t('actions.followerPlaceholder') : t('actions.executorPlaceholder')}
        style={{ flex: 1, minWidth: 0 }} onChange={value => onPatch({ executorId: (value as string | undefined) || undefined })} />
    </div>
    <button type="button" className="wf-collapse-toggle" onClick={() => setInputsOpen(!inputsOpen)} disabled={!parameters.length}>
      <span aria-hidden>{inputsOpen && parameters.length ? <DownOutlined /> : <RightOutlined />}</span> {t('actions.inputs')} <span className="muted">{parameters.length ? (mapped ? t('actions.inputCountMapped', { count: parameters.length, mapped }) : t('actions.inputCount', { count: parameters.length })) : t('actions.noInputs')}</span></button>
    {inputsOpen && parameters.length > 0 && <div className="wf-subtle wf-node-action-inputs">{parameters.map(parameter => {
      const mapping = action.inputs?.[parameter.key];
      return <div key={parameter.key} className="wf-mapping-row"><span className="wf-mapping-label">{parameter.label || parameter.key}</span>←
        <Select value={mapping?.source ?? 'VARIABLE'} disabled={context.readOnly} style={{ width: 80 }} size="small"
          options={[{ value: 'VARIABLE', label: t('actions.source.VARIABLE') }, { value: 'CONSTANT', label: t('actions.source.CONSTANT') }]}
          onChange={source => setInput(parameter.key, { source, value: '' })} />
        {(mapping?.source ?? 'VARIABLE') === 'VARIABLE'
          ? <Select allowClear size="small" value={mapping?.value || undefined} disabled={context.readOnly} style={{ flex: 1, minWidth: 120 }} placeholder={t('actions.selectVariable')}
            options={variables.map(item => ({ value: item.name, label: `${item.source}.${item.label}` }))}
            onChange={(value?: string) => setInput(parameter.key, { source: 'VARIABLE', value: value ?? '' })} />
          : <Input size="small" value={mapping?.value} disabled={context.readOnly} style={{ flex: 1, minWidth: 120 }}
            onChange={event => setInput(parameter.key, { source: 'CONSTANT', value: event.target.value })} />}
      </div>;
    })}</div>}
    {dataQuery && <div className="wf-subtle wf-node-action-inputs">
      <div className="wf-field-label">{t('actions.outputVariables')} <span className="muted">{outputs.length ? t('actions.outputVariableCount', { count: outputs.length }) : t('actions.noOutputVariables')}</span></div>
      {outputs.map(item => <div key={item.name} className="wf-mapping-row"><code className="wf-mapping-label">{item.name}</code>←
        <span className="wf-variable-chip"><code>out_{item.label}</code> · {enumLabel(t, 'dataQuery.outputTypeName', item.type)}</span></div>)}</div>}
    {dataQuery ? <div className="wf-node-action-instruction muted" title={criteria}><SafetyCertificateOutlined /> {t('actions.systemCheckLine', { criteria })}</div>
      : instruction && <div className="wf-node-action-instruction muted" title={instruction}><FileTextOutlined /> {instruction}</div>}
    {template && template.status !== 'ACTIVE' && <div className="wf-danger-text">{t('actions.inactive')}</div>}
    {problems.map((problem, current) => <div key={current} className="wf-warn-text">{problem.message}</div>)}
  </div>;
}

function ActionTypeIcon({ type }: { type: ActionType }) {
  return <span className={`wf-action-type-icon is-${type.toLowerCase()}`} aria-hidden>{actionTypeIcons[type]}</span>;
}

/** 修改动作 key：格式与唯一性同节点 key；条件中已引用的旧变量名需自行调整。 */
function ActionKeyModal({ graph, actionKey, onCancel, onSave }: { graph: WorkflowGraph; actionKey: string; onCancel: () => void; onSave: (key: string) => void }) {
  const t = useT('workflow');
  const [value, setValue] = useState(actionKey);
  const next = value.trim();
  const error = next === actionKey ? '' : !keyPattern.test(next) ? t('graph.error.keyPattern')
    : next === 'start' ? t('actionKey.notStart') : usedKeys(graph).has(next) ? t('graph.error.keyUsed', { key: next }) : '';
  return <Modal open title={t('actionKey.title')} okText={t('templateEditor.save')} cancelText={t('action.cancel')} okButtonProps={{ disabled: !!error || next === actionKey }} onCancel={onCancel} onOk={() => onSave(next)}>
    <Field label={t('actionKey.label')} hint={error || t('actionKey.hint', { key: next || actionKey })}>
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
  const t = useT('workflow');
  const patch = (index: number, value: Partial<NotificationRule>) => onChange(rules.map((rule, current) => current === index ? { ...rule, ...value } : rule));
  return <>
    <p className="muted">{note}</p>
    {rules.map((rule, index) => <div key={index} className="wf-rule">
      <div className="wf-rule-row">{t('rules.when')} <Select value={rule.trigger} disabled={readOnly} style={{ width: 150 }} options={[...triggers.map(value => ({ value, label: triggerLabel(value) })),
          // 历史配置里已停用的触发只显示、不可再选，需改为其他触发。
          ...(triggers.includes(rule.trigger) ? [] : [{ value: rule.trigger, label: t('rules.retiredTrigger', { trigger: triggerLabel(rule.trigger) }), disabled: true }])]}
        onChange={trigger => patch(index, { trigger })} /> {t('rules.then')}
        <Radio.Group value={rule.type} disabled={readOnly} optionType="button" size="small" options={[{ value: 'NOTIFY', label: t('rules.type.NOTIFY') }, { value: 'CALLBACK', label: t('rules.type.CALLBACK') }]}
          onChange={event => patch(index, event.target.value === 'NOTIFY' ? { type: 'NOTIFY', callbackCode: undefined, recipients: ['NODE_EXECUTORS'], channels: ['INBOX', 'EMAIL'] }
            : { type: 'CALLBACK', recipients: undefined, channels: undefined, userIds: undefined, callbackCode: '' })} />
        <span className="wf-grow" />{!readOnly && <Button size="small" type="text" danger onClick={() => onChange(rules.filter((_, current) => current !== index))}>{t('designer.delete')}</Button>}</div>
      {rule.type === 'NOTIFY' ? <>
        <Field label={t('rules.notify')}><Select mode="multiple" value={rule.recipients ?? []} disabled={readOnly} style={{ width: '100%' }}
          options={recipients.map(value => ({ value, label: recipientLabel(value) }))} onChange={value => patch(index, { recipients: value })} /></Field>
        {rule.recipients?.includes('SPECIFIED_USERS') && <Field label={t('enum.recipient.SPECIFIED_USERS')}><PersonSelect multiple value={rule.userIds ?? []} people={people} disabled={readOnly}
          onChange={value => patch(index, { userIds: value as string[] })} /></Field>}
        <Field label={t('rules.channels')}><Select mode="multiple" value={rule.channels ?? []} disabled={readOnly} style={{ width: '100%' }}
          options={channels.map(value => ({ value, label: channelLabel(value) }))} onChange={value => patch(index, { channels: value })} /></Field>
      </> : <Field label={t('rules.callbackCode')} hint={t('rules.callbackHint')}>
        <Input value={rule.callbackCode} disabled={readOnly} maxLength={64} placeholder={t('rules.callbackPlaceholder')}
          onChange={event => patch(index, { callbackCode: event.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '') })} /></Field>}
    </div>)}
    {!readOnly && <Button block type="dashed" onClick={() => onChange([...rules, { trigger: triggers[0], type: 'NOTIFY', recipients: ['NODE_EXECUTORS'], channels: ['INBOX', 'EMAIL'] }])} icon={<PlusOutlined />}>{t('rules.add')}</Button>}
  </>;
}

function GatewayExits({ node, context }: { node: GraphNode; context: PanelContext }) {
  const t = useT('workflow');
  const exits = gatewayExits(context.graph, node.key);
  const variables = variableCatalog(context.graph, context.startForm, context.templates, node.key);
  return <section className="wf-subtle"><div className="wf-field-label">{t('gateway.exits')}</div>
    {exits.length ? exits.map(exit => <div key={exit.edge.key} className="wf-exit-line">
      <Tag>{exit.isDefault ? t('graph.defaultEdge') : exit.priority ?? '—'}</Tag><strong>{nodeOf(context.graph, exit.edge.to)?.name ?? exit.edge.to}</strong>
      <span className="muted">{exit.isDefault ? t('gateway.noneMatched') : clausesText(exit.clauses, variables)}</span></div>) : <Empty description={t('gateway.noExits')} />}
    <p className="muted">{t('gateway.clickEdge')}</p></section>;
}

/** 选中连线：排他网关出边可配条件组（且关系）、优先级与默认出口（原型 09）。 */
export function EdgePanel({ edgeKey, context }: { edgeKey: string; context: PanelContext }) {
  const t = useT('workflow');
  const edge = context.graph.edges.find(item => item.key === edgeKey);
  if (!edge) return <Empty />;
  const source = nodeOf(context.graph, edge.from); const target = nodeOf(context.graph, edge.to);
  const header = <PanelHead icon={<ArrowRightOutlined />} title={`${source?.name || edge.from} → ${target?.name || edge.to}`} subtitle={t('edge.subtitle')} onClose={context.onClose} />;
  if (source?.type !== 'EXCLUSIVE') return <div className="wf-panel">{header}<p className="muted">{t('edge.plainHint')}</p>
    <DeleteLink label={t('edge.delete')} context={context} /></div>;
  const exits = gatewayExits(context.graph, source.key);
  const exit = exits.find(item => item.edge.key === edgeKey)!;
  const conditional = exits.filter(item => !item.isDefault && item.priority !== null);
  const variables = variableCatalog(context.graph, context.startForm, context.templates, source.key);
  const problems = context.problems.filter(problem => problem.edgeKey === edgeKey);
  return <div className="wf-panel">{header}<ProblemList problems={problems} />
    <p className="muted">{t('edge.gatewayHint')}</p>
    <div className="wf-rule-row">
      {!exit.isDefault && exit.priority !== null && <>{t('edge.priority')} <Select value={exit.priority} disabled={context.readOnly} style={{ width: 140 }}
        options={conditional.map((_, index) => ({ value: index + 1, label: t('edge.priorityOption', { priority: index + 1, count: conditional.length }) }))}
        onChange={priority => context.onChange(setExitPriority(context.graph, source.key, edgeKey, priority))} /></>}
      <span className="wf-grow" /><label className="wf-inline">{t('edge.setDefault')} <Switch size="small" checked={exit.isDefault} disabled={context.readOnly || exit.isDefault}
        onChange={() => context.onChange(setDefaultExit(context.graph, source.key, edgeKey))} /></label>
    </div>
    {exit.isDefault ? <Alert type="info" showIcon title={t('edge.defaultHint')} />
      : <ConditionGroup clauses={exit.clauses} variables={variables} readOnly={context.readOnly}
        onChange={clauses => context.onChange(setEdgeClauses(context.graph, source.key, edgeKey, clauses))} />}
    <section className="wf-subtle"><div className="wf-field-label">{t('edge.variables')}</div>
      <div className="wf-variable-chips">{variables.map(item => <Tag key={item.name} color="purple">{item.name}</Tag>)}</div>
      <p className="muted">{t('edge.parallelVariables')}</p></section>
    <GatewayExits node={source} context={context} />
    <DeleteLink label={t('edge.delete')} context={context} />
  </div>;
}

/** 条件组：每行变量 / 比较符 / 值，行与行之间是“且”；“或”用多条出边表达。 */
export function ConditionGroup({ clauses, variables, readOnly, onChange }: {
  clauses: ConditionClause[]; variables: VariableInfo[]; readOnly: boolean; onChange: (clauses: ConditionClause[]) => void;
}) {
  const t = useT('workflow');
  const patch = (index: number, clause: ConditionClause) => onChange(clauses.map((item, current) => current === index ? clause : item));
  return <section className="wf-condition">
    <div className="wf-field-label">{t('condition.title')}</div>
    {clauses.map((clause, index) => {
      const variable = variables.find(item => item.name === clause.variable);
      const operators = variable ? operatorsByType[variable.type] : allOperators;
      const shape = valueShape(clause.operator);
      const enumOptions = variable?.options?.map(option => ({ value: option.value, label: `${option.value} · ${option.label}` }));
      return <div key={index} className="wf-condition-row">
        <Select showSearch={{ optionFilterProp: 'label' }} value={clause.variable || undefined} disabled={readOnly} placeholder={t('condition.variable')}
          status={clause.variable && !variable ? 'error' : undefined}
          options={variables.map(item => ({ value: item.name, label: t('condition.variableOption', { name: item.name, label: item.label }) }))}
          onChange={(name: string) => { const info = variables.find(item => item.name === name); if (info) patch(index, changeVariable(clause, info)); }} />
        <Select value={clause.operator} disabled={readOnly} style={{ width: 110 }} options={operators.map(value => ({ value, label: operatorLabel(value) }))}
          onChange={operator => patch(index, changeOperator(clause, operator))} />
        {shape === 'NONE' ? <span className="wf-grow" /> : enumOptions?.length
          ? <Select mode={shape === 'LIST' ? 'multiple' : undefined} value={clause.value as never} disabled={readOnly} style={{ flex: 1 }} options={enumOptions}
            onChange={value => patch(index, { ...clause, value })} />
          : shape === 'LIST' ? <Select mode="tags" value={(clause.value as string[]) ?? []} disabled={readOnly} style={{ flex: 1 }} placeholder={t('condition.typeEnter')}
            onChange={value => patch(index, { ...clause, value })} />
          : <Input value={clause.value as string} disabled={readOnly} style={{ flex: 1 }} placeholder={variable?.type === 'DATE' ? 'YYYY-MM-DD' : variable?.type === 'BOOLEAN' ? 'true / false' : t('designer.optionValue')}
            onChange={event => patch(index, { ...clause, value: event.target.value })} />}
        {!readOnly && <Button type="text" danger icon={<CloseOutlined />} aria-label={t('condition.delete')} onClick={() => onChange(clauses.filter((_, current) => current !== index))} />}
      </div>;
    })}
    {clauses.some(clause => clause.variable && !variables.some(item => item.name === clause.variable)) && <div className="wf-danger-text">{t('condition.unavailable')}</div>}
    {!readOnly && <Space><Button type="link" disabled={!variables.length}
      onClick={() => onChange([...clauses, changeVariable({ variable: '', operator: 'EQ', value: '' }, variables[0])])} icon={<PlusOutlined />}>{t('condition.add')}</Button>
      {!variables.length && <span className="muted">{t('condition.noVariables')}</span>}</Space>}
  </section>;
}
