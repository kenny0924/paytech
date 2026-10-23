import { useCallback, useMemo, useState } from 'react';
import { Alert, App, Button, Drawer, Empty, Form, Input, Modal, Segmented, Switch, Tabs, Tag } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { displayValue } from './formModel';
import { fileAddressesOf, fileIndex, outputFileAddresses } from './fileModel';
import { AttachmentList } from './WorkflowFiles';
import { nodeOf } from './graphModel';
import { actionLabel, currentHandlers, frozenTemplate, hadRevision, isCompleted, isPendingAction, latestRuns, progressOf, traversedEdges, visualState, waitingText } from './runtimeModel';
import { useWorkflowCommand } from './useWorkflowCommand';
import { WorkflowCanvas } from './WorkflowCanvas';
import type { CanvasSelection } from './WorkflowCanvas';
import { decodeActivities, decodeInstanceDetail, decodeMutation, workflowPaths } from './workflowApi';
import {
  actionRunStatusColors, actionRunStatusLabel, actionTypeLabel, anyStatusLabel, approvalStatusColors, approvalStatusLabel, callbackStatusColors,
  callbackStatusLabel, channelLabel, instanceStatusColors, instanceStatusLabel, nodeRunStatusLabel, recipientLabel, triggerLabel,
} from './workflowLabels';
import { enumLabel, translator } from '../i18n/index';
import { useT } from '../i18n/react';
import type { Activity, FormDefinition, GraphNode, InstanceDetail } from './workflowTypes';
import { LoadFailed, OutputView, Spinner, hasButton, isMe, personName, shortTime, useLoad, workflowButtons, workflowRoutes } from './WorkflowParts';

const tw = translator('workflow');

interface Props { instanceId: string; resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

/** 实例详情：流程图铺满、点节点滑出抽屉；活动记录与启动信息（原型 05、06）。 */
export function InstanceDetailView({ instanceId, resources, actorId, onAuthorizationChanged }: Props) {
  const t = useT('workflow');
  const detail = useLoad<InstanceDetail>(workflowPaths.instance(instanceId), decodeInstanceDetail);
  const [tab, setTab] = useState('graph');
  const [terminating, setTerminating] = useState(false);
  if (detail.loading && !detail.data) return <Spinner />;
  if (!detail.data) return <LoadFailed failure={detail.failure} retry={detail.refresh} title={t('instance.loadFailed')} />;
  const value = detail.data;
  const progress = progressOf(value); const handlers = currentHandlers(value);
  const canTerminate = value.instance.status === 'RUNNING' && (isMe(value.instance.startedBy, actorId) || hasButton(resources, workflowButtons.instanceManage));
  const ranFor = waitingText(value.instance.startTime, value.instance.endTime ? new Date(value.instance.endTime).getTime() : Date.now());
  return <div className="wf-page">
    <nav className="wf-crumbs"><Link to={workflowRoutes.instances}>{t('instance.instances')}</Link> / {value.instance.title}</nav>
    <div className="wf-page-head"><h1>{value.instance.title}</h1>
      <Tag color={instanceStatusColors[value.instance.status]}>{instanceStatusLabel(value.instance.status)}</Tag>
      <span className="muted">{value.definitionName} v{value.versionNo}{value.instance.businessKey ? ` · ${value.instance.businessKey}` : ''} · {t('instance.startedBy', { name: personName(value.instance.startedBy, value.people), time: shortTime(value.instance.startTime) })} · {value.instance.endTime ? t('instance.took', { duration: ranFor }) : t('instance.runningFor', { duration: ranFor })}</span>
      <span className="wf-grow" />
      {canTerminate && <Button danger onClick={() => setTerminating(true)}>{t('terminate.title')}</Button>}
    </div>
    {value.instance.status === 'TERMINATED' && <Alert type="info" showIcon title={t('instance.terminatedBy', { name: personName(value.terminatedBy, value.people) })} description={value.terminationReason ?? undefined} />}
    <div className="wf-stats">
      <span>{t('instance.stats.completed')} <strong className="wf-ok-text">{t('instance.stats.nodes', { count: progress.completed })}</strong></span>
      <span>{t('instance.stats.active')} <strong className="wf-info-text">{t('instance.stats.nodes', { count: progress.active })}</strong></span>
      <span>{t('instance.stats.pending')} <strong>{t('instance.stats.nodes', { count: progress.pending })}</strong></span>
      {handlers.length > 0 && <span>{t('instance.stats.handlers')} <strong>{[...new Set(handlers.flatMap(item => item.people))].join(' · ')}</strong></span>}
      <span className="wf-grow" />
      <Segmented value={tab} onChange={next => setTab(String(next))} options={[{ value: 'graph', label: t('publish.area.graph') }, { value: 'activity', label: t('instance.tab.activity') }, { value: 'start', label: t('instance.tab.start') }]} />
    </div>
    {tab === 'graph' && <InstanceGraph detail={value} actorId={actorId} instanceId={instanceId} />}
    {tab === 'activity' && <ActivityLog detail={value} instanceId={instanceId} canRetry={hasButton(resources, workflowButtons.instanceManage)} onAuthorizationChanged={onAuthorizationChanged} />}
    {tab === 'start' && <StartInfo detail={value} />}
    {terminating && <TerminateModal instanceId={instanceId} onClose={() => setTerminating(false)} onDone={() => { setTerminating(false); detail.refresh(); }}
      onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 节点是否与我有关（执行人或审批人）。 */
function involvesMe(detail: InstanceDetail, nodeKey: string, actorId: string) {
  const runIds = new Set(detail.nodeRuns.filter(run => run.nodeKey === nodeKey).map(run => run.nodeRunId));
  return detail.actionRuns.some(run => runIds.has(run.nodeRunId) && run.assigneeId === actorId)
    || detail.approvals.some(item => runIds.has(item.nodeRunId) && item.approverId === actorId);
}

function InstanceGraph({ detail, actorId, instanceId }: { detail: InstanceDetail; actorId: string; instanceId: string }) {
  const t = useT('workflow');
  const [selection, setSelection] = useState<CanvasSelection>(null);
  const [mine, setMine] = useState(false);
  const runs = useMemo(() => latestRuns(detail.nodeRuns), [detail.nodeRuns]);
  const edges = useMemo(() => traversedEdges(detail.graph, detail.nodeRuns), [detail]);
  const decorate = useCallback((node: GraphNode) => {
    const run = runs.get(node.key);
    const state = node.type === 'START' && detail.nodeRuns.length ? 'done' as const : visualState(run);
    let subtitle: string | undefined;
    if (node.type === 'TASK') {
      const actions = run ? detail.actionRuns.filter(item => item.nodeRunId === run.nodeRunId) : [];
      const pendingApprovers = run ? detail.approvals.filter(item => item.nodeRunId === run.nodeRunId && item.status === 'PENDING') : [];
      subtitle = !run ? t('instance.node.notReached')
        : run.status === 'ACTIVE' ? t('instance.node.active', { people: [...new Set(actions.filter(item => !isCompleted(item)).map(item => personName(item.assigneeId, detail.people)))].join(t('list.comma')),
          waiting: waitingText(run.activatedTime) })
        : run.status === 'APPROVING' ? t('instance.node.approving', { done: actions.filter(isCompleted).length, total: actions.length,
          people: pendingApprovers.map(item => personName(item.approverId, detail.people)).join(t('list.comma')) })
        : nodeRunStatusLabel(run.status);
    }
    return { state, subtitle, badge: run && run.roundNo > 1 ? t('instance.round', { round: run.roundNo }) : hadRevision(detail.nodeRuns, node.key) ? t('instance.node.hadReturn') : undefined,
      dim: mine && node.type === 'TASK' && !involvesMe(detail, node.key, actorId) };
  }, [runs, detail, mine, actorId, t]);
  const edgeState = useCallback((key: string) => edges.has(key) ? 'done' as const : 'pending' as const, [edges]);
  const node = selection?.kind === 'node' ? nodeOf(detail.graph, selection.key) : undefined;
  return <>
    <WorkflowCanvas graph={detail.graph} editable={false} selection={selection} onSelect={setSelection} decorate={decorate} edgeState={edgeState} height="calc(100vh - 300px)"
      toolbar={<label className="wf-inline"><Switch size="small" checked={mine} onChange={setMine} /> {t('instance.onlyMine')}</label>}
      footer={<div className="wf-legend"><span className="is-done">{t('instance.legend.done')}</span><span className="is-active">{t('instance.legend.active')}</span><span className="is-pending">{t('instance.legend.pending')}</span><span className="is-rejected">{t('instance.legend.rejected')}</span>
        <span className="muted">{t('instance.legend.hint')}</span></div>} />
    <Drawer open={!!node && node.type === 'TASK'} size={520} onClose={() => setSelection(null)} title={node?.name} destroyOnHidden>
      {node && <NodeDrawer detail={detail} node={node} actorId={actorId} instanceId={instanceId} />}
    </Drawer>
  </>;
}

function NodeDrawer({ detail, node, actorId, instanceId }: { detail: InstanceDetail; node: GraphNode; actorId: string; instanceId: string }) {
  const t = useT('workflow');
  const nodeRuns = detail.nodeRuns.filter(run => run.nodeKey === node.key).sort((left, right) => right.roundNo - left.roundNo);
  const latest = nodeRuns[0];
  const runIds = new Set(nodeRuns.map(run => run.nodeRunId));
  const actions = latest ? detail.actionRuns.filter(item => item.nodeRunId === latest.nodeRunId) : [];
  const approvals = detail.approvals.filter(item => runIds.has(item.nodeRunId));
  const transfers = detail.transfers.filter(item => detail.actionRuns.some(run => run.actionRunId === item.targetId && runIds.has(run.nodeRunId))
    || approvals.some(row => row.approvalId === item.targetId));
  const roundOf = (nodeRunId: string) => detail.nodeRuns.find(run => run.nodeRunId === nodeRunId)?.roundNo;
  const myAction = actions.find(item => item.assigneeId === actorId && isPendingAction(item) && latest?.status === 'ACTIVE');
  const myApproval = approvals.find(item => item.approverId === actorId && item.status === 'PENDING' && item.nodeRunId === latest?.nodeRunId);
  const myTask = myApproval?.approvalId ?? myAction?.actionRunId;
  const activities = useLoad<Activity[]>(workflowPaths.activities(instanceId), decodeActivities);
  const deliveries = (activities.data ?? []).filter(item => isDelivery(item) && item.nodeKey === node.key);
  return <div className="wf-drawer">
    <div className="muted">{[latest ? t('instance.round', { round: latest.roundNo }) : t('instance.node.notReached'),
      node.approval?.approverIds.length ? (node.approval.mode === 'ALL' ? t('instance.drawer.approvalAll') : t('instance.drawer.approvalAny')) : t('instance.drawer.noApprovers')].join(' · ')}</div>
    <div className="wf-round-tags">{nodeRuns.slice().reverse().map(run => <Tag key={run.nodeRunId} color={run.status === 'PASSED' ? 'green' : run.status === 'RETURNED' || run.status === 'REJECTED' ? 'red' : 'orange'}>
      {t('instance.round', { round: run.roundNo })} · {nodeRunStatusLabel(run.status)}</Tag>)}</div>
    <Tabs items={[
      { key: 'actions', label: `${t('panel.tab.actions')} ${actions.length}`, children: actions.length ? actions.map(run => {
        const template = frozenTemplate(detail, run.actionKey);
        return <div key={run.actionRunId} className="wf-action is-expanded">
          <div className="wf-action-head"><strong>{actionLabel(detail, run)}</strong><Tag color="blue">{actionTypeLabel(run.actionType)}</Tag>
            {run.required ? <Tag color="red">{t('actions.required')}</Tag> : <Tag>{t('actions.optional')}</Tag>}<span className="wf-grow" /><Tag color={actionRunStatusColors[run.status]}>{actionRunStatusLabel(run.status)}</Tag></div>
          <div className="muted">{t('instance.drawer.executor', { name: personName(run.assigneeId, detail.people) })}</div>
          <OutputView output={run.output} form={run.actionType === 'FORM' ? template?.config as FormDefinition : undefined} />
          {outputFileAddresses(run, template).length > 0 && <AttachmentList instanceId={detail.instance.instanceId} addresses={outputFileAddresses(run, template)} known={fileIndex(detail)} />}
        </div>;
      }) : <Empty description={t('instance.drawer.noActions')} /> },
      { key: 'approvals', label: `${t('panel.tab.approval')} ${approvals.length}`, children: approvals.length ? <ul className="wf-records">{approvals.map(item => <li key={item.approvalId}>
        <Tag color={approvalStatusColors[item.status]}>{approvalStatusLabel(item.status)}</Tag> {personName(item.approverId, detail.people)} · {t('instance.round', { round: roundOf(item.nodeRunId) ?? '—' })}
        {item.decidedTime && ` · ${shortTime(item.decidedTime)}`}{item.comment && <div className="wf-quote">{item.comment}</div>}
        {item.rejectTargetNodeKey && <div className="muted">{t('instance.drawer.rejectedTo', { branch: nodeOf(detail.graph, item.rejectTargetNodeKey)?.name ?? item.rejectTargetNodeKey })}</div>}</li>)}</ul>
        : <Empty description={t('instance.drawer.noApprovals')} /> },
      { key: 'records', label: `${t('instance.drawer.records')} ${transfers.length}`, children: transfers.length ? <ul className="wf-records">{transfers.map(item => <li key={item.transferId}>
        {shortTime(item.createTime)} {t(item.targetType === 'APPROVAL' ? 'instance.drawer.transferApproval' : 'instance.drawer.transferAction', { by: personName(item.transferredBy, detail.people),
          from: personName(item.fromUserId, detail.people), to: personName(item.toUserId, detail.people) })}
        {item.reason && t('instance.drawer.transferReason', { reason: item.reason })}</li>)}</ul> : <Empty description={t('instance.drawer.noTransfers')} /> },
    ]} />
    <section className="wf-subtle"><div className="wf-card-title">{t('instance.drawer.rules')}</div>
      {(node.notifications ?? []).length ? (node.notifications ?? []).map((rule, index) => <div key={index} className="wf-rule-line">
        {triggerLabel(rule.trigger)} → {rule.type === 'CALLBACK' ? t('rules.summary.callback', { code: rule.callbackCode ?? '' })
          : t('rules.summary.notify', { channels: (rule.channels ?? []).map(channelLabel).join(' + '), recipients: (rule.recipients ?? []).map(recipientLabel).join(t('list.comma')) })}
      </div>) : <span className="muted">{t('instance.drawer.noRules')}</span>}
      {deliveries.map((item, index) => <div key={index} className="wf-rule-line"><span>{shortTime(item.time)} {item.detail}</span>
        <Tag color={callbackStatusColors[item.status ?? ''] ?? 'default'}>{callbackStatusLabel(item.status ?? '')}</Tag></div>)}
    </section>
    {myTask && <div className="wf-drawer-foot"><span>{myApproval ? t('instance.drawer.yourApproval') : t('instance.drawer.yourAction')}</span>
      <Link to={`${workflowRoutes.myTasks}?task=${myTask}`}><Button type="primary">{myApproval ? t('instance.drawer.goApprove') : t('instance.drawer.goHandle')}</Button></Link></div>}
  </div>;
}

type ActivityFilter = 'ALL' | 'MANUAL' | 'STATE' | 'NOTIFY' | 'CALLBACK';
export const isDelivery = (item: Activity) => item.activityType === 'NOTIFY' || item.activityType === 'CALLBACK';
const activityGroups: Record<ActivityFilter, (item: Activity) => boolean> = {
  ALL: () => true,
  MANUAL: item => item.activityType === 'SUBMISSION' || item.activityType === 'APPROVAL' || item.activityType === 'TRANSFER',
  STATE: item => item.activityType === 'NODE' || item.activityType === 'INSTANCE',
  NOTIFY: item => item.activityType === 'NOTIFY',
  CALLBACK: item => item.activityType === 'CALLBACK',
};

/** 活动记录：人工操作、状态变化、通知与回调投递；失败或重试中的回调可手动重试（需管理实例权限）。 */
function ActivityLog({ detail, instanceId, canRetry, onAuthorizationChanged }: { detail: InstanceDetail; instanceId: string; canRetry: boolean; onAuthorizationChanged: () => void }) {
  const t = useT('workflow');
  const { message } = App.useApp();
  const activities = useLoad<Activity[]>(workflowPaths.activities(instanceId), decodeActivities);
  const [filter, setFilter] = useState<ActivityFilter>('ALL');
  const command = useWorkflowCommand(onAuthorizationChanged);
  const items = (activities.data ?? []).slice().sort((left, right) => right.time.localeCompare(left.time));
  const callbacks = items.filter(isDelivery);
  const callbackCodes = detail.graph.nodes.flatMap(node => (node.notifications ?? []).filter(rule => rule.type === 'CALLBACK').map(rule => ({ code: rule.callbackCode, node: node.name, trigger: rule.trigger })))
    .concat((detail.graph.settings?.notifications ?? []).filter(rule => rule.type === 'CALLBACK').map(rule => ({ code: rule.callbackCode, node: t('enum.activityType.INSTANCE'), trigger: rule.trigger })));
  async function retry(callbackId: string) {
    const result = await command.run(workflowPaths.callbackRetry(callbackId), 'POST', undefined, decodeMutation, t('activity.retryFailed'));
    if (result) { void message.success(t('activity.requeued')); activities.refresh(); }
  }
  if (activities.failure) return <LoadFailed failure={activities.failure} retry={activities.refresh} title={t('activity.loadFailed')} />;
  return <div className="wf-activity">
    <section className="wf-card">
      <div className="wf-chips">{(['ALL', 'MANUAL', 'STATE', 'NOTIFY', 'CALLBACK'] as ActivityFilter[]).map(value => <button type="button" key={value}
        className={`wf-chip${filter === value ? ' is-active' : ''}`} onClick={() => setFilter(value)}>
        {enumLabel(t, 'activity.filter', value)} {items.filter(activityGroups[value]).length}</button>)}</div>
      {activities.loading && !activities.data ? <Spinner /> : <ul className="wf-timeline">{items.filter(activityGroups[filter]).map((item, index) => <li key={`${item.time}:${index}`}>
        <span className="wf-time">{shortTime(item.time)}</span>
        <span className={`wf-activity-icon is-${item.activityType.toLowerCase()}`} />
        <span className="wf-grow">{activityText(item, detail)}</span>
        {isDelivery(item) && item.status && <Tag color={callbackStatusColors[item.status] ?? 'default'}>{callbackStatusLabel(item.status)}</Tag>}
        {isDelivery(item) && canRetry && item.referenceId && item.status !== 'SENT' &&
          <Button size="small" loading={command.saving} onClick={() => void retry(item.referenceId!)}>{t('activity.retryNow')}</Button>}
      </li>)}</ul>}
      {command.formError && <Alert type="error" showIcon title={command.formError} />}
    </section>
    <aside>
      <section className="wf-card"><div className="wf-card-title">{t('activity.deliveries')}</div>
        <div className="wf-counters"><div><strong className="wf-ok-text">{callbacks.filter(item => item.status === 'SENT').length}</strong>{callbackStatusLabel('SENT')}</div>
          <div><strong className="wf-warn-text">{callbacks.filter(item => item.status === 'PENDING').length}</strong>{callbackStatusLabel('PENDING')}</div>
          <div><strong className="wf-danger-text">{callbacks.filter(item => item.status === 'FAILED').length}</strong>{callbackStatusLabel('FAILED')}</div></div>
        <p className="muted">{t('activity.deliveryNote')}</p>
      </section>
      <section className="wf-card"><div className="wf-card-title">{t('activity.callbackCodes')}</div>
        {callbackCodes.length ? callbackCodes.map((item, index) => <div key={index} className="wf-code-line"><code>{item.code}</code>
          <div className="muted">{item.node} · {triggerLabel(item.trigger)}</div></div>) : <span className="muted">{t('activity.noCallbacks')}</span>}
      </section>
    </aside>
  </div>;
}

/** 活动的一行文字（实例详情、我发起的、已办共用）。 */
/** actorId 给出时本人显示为“你”。 */
export function activityText(item: Activity, detail: InstanceDetail, actorId?: string): string {
  const node = item.nodeKey ? nodeOf(detail.graph, item.nodeKey)?.name ?? item.nodeKey : '';
  const actor = actorId && item.actorId === actorId ? tw('people.youLead') : item.actorName ?? (item.actorId ? personName(item.actorId, detail.people) : '');
  const round = item.roundNo && item.roundNo > 1 ? tw('instance.round', { round: item.roundNo }) : '';
  const status = item.status && !isDelivery(item) ? anyStatusLabel(item.status) : '';
  const text = [actor, node ? tw('publish.subject', { subject: node }) : '', round, status, item.detail ?? ''].filter(Boolean).join(' ');
  return `${enumLabel(tw, 'enum.activityType', item.activityType)} · ${text}`;
}

function StartInfo({ detail }: { detail: InstanceDetail }) {
  const t = useT('workflow');
  return <section className="wf-card">
    <div className="wf-card-title">{t('instance.tab.start')}<span className="muted">{t('instance.start.hint')}</span></div>
    <dl className="wf-output">
      <div><dt>{t('instance.start.process')}</dt><dd>{t('instance.start.processValue', { name: detail.definitionName, code: detail.definitionCode, version: detail.versionNo })}</dd></div>
      <div><dt>{t('enum.recipient.STARTER')}</dt><dd>{personName(detail.instance.startedBy, detail.people)} · {shortTime(detail.instance.startTime)}</dd></div>
      <div><dt>{t('launch.businessKey')}</dt><dd>{detail.instance.businessKey ?? '—'}</dd></div>
      {detail.startForm.fields.map(field => <div key={field.key}><dt>{field.label}</dt>
        <dd>{field.type === 'FILE' ? <AttachmentList instanceId={detail.instance.instanceId} addresses={fileAddressesOf(detail.startValues[field.key])} known={fileIndex(detail)} /> : displayValue(detail.startValues[field.key], field)}</dd></div>)}
    </dl>
  </section>;
}

/** 终止实例（发起人或有管理实例权限的人）；title 可改为「撤回（终止实例）」。 */
export function TerminateModal({ instanceId, onClose, onDone, onAuthorizationChanged, title }: {
  instanceId: string; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void; title?: string;
}) {
  const t = useT('workflow');
  const { message } = App.useApp();
  const [form] = Form.useForm<{ reason: string }>();
  const command = useWorkflowCommand(onAuthorizationChanged);
  async function submit({ reason }: { reason: string }) {
    const result = await command.run(workflowPaths.termination(instanceId), 'POST', { reason: reason.trim() }, decodeMutation, t('terminate.failed'));
    if (result) { void message.success(t('terminate.done')); onDone(); }
  }
  return <Modal open title={title ?? t('terminate.title')} okText={t('terminate.confirm')} cancelText={t('action.cancel')} okButtonProps={{ danger: true, loading: command.saving }} onCancel={onClose} onOk={() => form.submit()}>
    <p className="muted">{t('terminate.hint')}</p>
    {command.formError && <Alert type="error" showIcon title={command.formError} />}
    {command.conflict && <Alert type="warning" showIcon title={t('terminate.conflict')} />}
    <Form form={form} layout="vertical" onFinish={values => void submit(values)}>
      <Form.Item name="reason" label={t('terminate.reason')} rules={[{ required: true, whitespace: true, message: t('terminate.reasonRequired') }]}>
        <Input.TextArea rows={3} maxLength={512} showCount /></Form.Item>
    </Form>
  </Modal>;
}
