import { useCallback, useMemo, useState } from 'react';
import { Alert, App, Button, Drawer, Empty, Form, Input, Modal, Segmented, Switch, Tabs, Tag } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { displayValue } from './formModel';
import { fileAddressesOf, fileIndex, outputFileAddresses } from './fileModel';
import { AttachmentList } from './WorkflowFiles';
import { nodeOf } from './graphModel';
import { actionLabel, currentHandlers, frozenTemplate, hadRevision, latestRuns, progressOf, traversedEdges, visualState, waitingText } from './runtimeModel';
import { useWorkflowCommand } from './useWorkflowCommand';
import { WorkflowCanvas } from './WorkflowCanvas';
import type { CanvasSelection } from './WorkflowCanvas';
import { decodeActivities, decodeInstanceDetail, decodeMutation, workflowPaths } from './workflowApi';
import {
  actionRunStatusColors, actionRunStatusLabels, actionTypeLabels, approvalStatusColors, approvalStatusLabels, callbackStatusColors,
  callbackStatusLabels, channelLabels, instanceStatusColors, instanceStatusLabels, nodeRunStatusLabels, recipientLabels, triggerLabels,
} from './workflowLabels';
import type { Activity, FormDefinition, GraphNode, InstanceDetail } from './workflowTypes';
import { LoadFailed, OutputView, Spinner, hasButton, isMe, personName, shortTime, useLoad, workflowButtons, workflowRoutes } from './WorkflowParts';

interface Props { instanceId: string; resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

/** 实例详情：流程图铺满、点节点滑出抽屉；活动记录与启动信息（原型 05、06）。 */
export function InstanceDetailView({ instanceId, resources, actorId, onAuthorizationChanged }: Props) {
  const detail = useLoad<InstanceDetail>(workflowPaths.instance(instanceId), decodeInstanceDetail);
  const [tab, setTab] = useState('graph');
  const [terminating, setTerminating] = useState(false);
  if (detail.loading && !detail.data) return <Spinner />;
  if (!detail.data) return <LoadFailed failure={detail.failure} retry={detail.refresh} title="实例加载失败" />;
  const value = detail.data;
  const progress = progressOf(value); const handlers = currentHandlers(value);
  const canTerminate = value.instance.status === 'RUNNING' && (isMe(value.instance.startedBy, actorId) || hasButton(resources, workflowButtons.instanceManage));
  const ranFor = waitingText(value.instance.startTime, value.instance.endTime ? new Date(value.instance.endTime).getTime() : Date.now());
  return <div className="wf-page">
    <nav className="wf-crumbs"><Link to={workflowRoutes.instances}>流程实例</Link> / {value.instance.title}</nav>
    <div className="wf-page-head"><h1>{value.instance.title}</h1>
      <Tag color={instanceStatusColors[value.instance.status]}>{instanceStatusLabels[value.instance.status]}</Tag>
      <span className="muted">{value.definitionName} v{value.versionNo}{value.instance.businessKey ? ` · ${value.instance.businessKey}` : ''} · {personName(value.instance.startedBy, value.people)} 发起于 {shortTime(value.instance.startTime)} · {value.instance.endTime ? '历时' : '已运行'} {ranFor}</span>
      <span className="wf-grow" />
      {canTerminate && <Button danger onClick={() => setTerminating(true)}>终止实例</Button>}
    </div>
    {value.instance.status === 'TERMINATED' && <Alert type="info" showIcon title={`已由 ${personName(value.terminatedBy, value.people)} 终止`} description={value.terminationReason ?? undefined} />}
    <div className="wf-stats">
      <span>已完成 <strong className="wf-ok-text">{progress.completed} 个节点</strong></span>
      <span>进行中 <strong className="wf-info-text">{progress.active} 个节点</strong></span>
      <span>未到达 <strong>{progress.pending} 个节点</strong></span>
      {handlers.length > 0 && <span>待处理人 <strong>{[...new Set(handlers.flatMap(item => item.people))].join(' · ')}</strong></span>}
      <span className="wf-grow" />
      <Segmented value={tab} onChange={next => setTab(String(next))} options={[{ value: 'graph', label: '流程图' }, { value: 'activity', label: '活动记录' }, { value: 'start', label: '启动信息' }]} />
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
      subtitle = !run ? '未到达'
        : run.status === 'ACTIVE' ? `待提交 · ${[...new Set(actions.filter(item => item.status !== 'VERIFIED').map(item => personName(item.assigneeId, detail.people)))].join('、')} · ${waitingText(run.activatedTime)}`
        : run.status === 'APPROVING' ? `${actions.filter(item => item.status === 'VERIFIED').length}/${actions.length} 已验证 · 待审批 ${pendingApprovers.map(item => personName(item.approverId, detail.people)).join('、')}`
        : nodeRunStatusLabels[run.status];
    }
    return { state, subtitle, badge: run && run.roundNo > 1 ? `第 ${run.roundNo} 轮` : hadRevision(detail.nodeRuns, node.key) ? '有退回' : undefined,
      dim: mine && node.type === 'TASK' && !involvesMe(detail, node.key, actorId) };
  }, [runs, detail, mine, actorId]);
  const edgeState = useCallback((key: string) => edges.has(key) ? 'done' as const : 'pending' as const, [edges]);
  const node = selection?.kind === 'node' ? nodeOf(detail.graph, selection.key) : undefined;
  return <>
    <WorkflowCanvas graph={detail.graph} editable={false} selection={selection} onSelect={setSelection} decorate={decorate} edgeState={edgeState} height="calc(100vh - 300px)"
      toolbar={<label className="wf-inline"><Switch size="small" checked={mine} onChange={setMine} /> 只看我的节点</label>}
      footer={<div className="wf-legend"><span className="is-done">已完成</span><span className="is-active">进行中</span><span className="is-pending">未到达</span><span className="is-rejected">被驳回 / 退回</span>
        <span className="muted">点击节点查看动作、审批与记录</span></div>} />
    <Drawer open={!!node && node.type === 'TASK'} size={520} onClose={() => setSelection(null)} title={node?.name} destroyOnHidden>
      {node && <NodeDrawer detail={detail} node={node} actorId={actorId} instanceId={instanceId} />}
    </Drawer>
  </>;
}

function NodeDrawer({ detail, node, actorId, instanceId }: { detail: InstanceDetail; node: GraphNode; actorId: string; instanceId: string }) {
  const nodeRuns = detail.nodeRuns.filter(run => run.nodeKey === node.key).sort((left, right) => right.roundNo - left.roundNo);
  const latest = nodeRuns[0];
  const runIds = new Set(nodeRuns.map(run => run.nodeRunId));
  const actions = latest ? detail.actionRuns.filter(item => item.nodeRunId === latest.nodeRunId) : [];
  const approvals = detail.approvals.filter(item => runIds.has(item.nodeRunId));
  const transfers = detail.transfers.filter(item => detail.actionRuns.some(run => run.actionRunId === item.targetId && runIds.has(run.nodeRunId))
    || approvals.some(row => row.approvalId === item.targetId));
  const roundOf = (nodeRunId: string) => detail.nodeRuns.find(run => run.nodeRunId === nodeRunId)?.roundNo;
  const myAction = actions.find(item => item.assigneeId === actorId && (item.status === 'PENDING' || item.status === 'UNVERIFIED') && latest?.status === 'ACTIVE');
  const myApproval = approvals.find(item => item.approverId === actorId && item.status === 'PENDING' && item.nodeRunId === latest?.nodeRunId);
  const myTask = myApproval?.approvalId ?? myAction?.actionRunId;
  const activities = useLoad<Activity[]>(workflowPaths.activities(instanceId), decodeActivities);
  const deliveries = (activities.data ?? []).filter(item => isDelivery(item) && item.nodeKey === node.key);
  return <div className="wf-drawer">
    <div className="muted">{[latest ? `第 ${latest.roundNo} 轮` : '未到达', node.approval?.approverIds.length ? `审批 ${node.approval.mode === 'ALL' ? '全部同意' : '任一人同意'}` : '无审批人'].join(' · ')}</div>
    <div className="wf-round-tags">{nodeRuns.slice().reverse().map(run => <Tag key={run.nodeRunId} color={run.status === 'PASSED' ? 'green' : run.status === 'RETURNED' || run.status === 'REJECTED' ? 'red' : 'orange'}>
      第 {run.roundNo} 轮 · {nodeRunStatusLabels[run.status]}</Tag>)}</div>
    <Tabs items={[
      { key: 'actions', label: `动作 ${actions.length}`, children: actions.length ? actions.map(run => {
        const template = frozenTemplate(detail, run.actionKey);
        const verifications = detail.verifications.filter(item => detail.actionRuns.some(row => row.actionRunId === item.actionRunId && row.actionKey === run.actionKey && runIds.has(row.nodeRunId)))
          .sort((left, right) => right.verifiedTime.localeCompare(left.verifiedTime));
        return <div key={run.actionRunId} className="wf-action is-expanded">
          <div className="wf-action-head"><strong>{actionLabel(detail, run)}</strong><Tag color="blue">{actionTypeLabels[run.actionType]}</Tag>
            {run.required ? <Tag color="red">必选</Tag> : <Tag>可选</Tag>}<span className="wf-grow" /><Tag color={actionRunStatusColors[run.status]}>{actionRunStatusLabels[run.status]}</Tag></div>
          <div className="muted">执行人 {personName(run.assigneeId, detail.people)}</div>
          <OutputView output={run.output} form={run.actionType === 'FORM' ? template?.config as FormDefinition : undefined} />
          {outputFileAddresses(run, template).length > 0 && <AttachmentList instanceId={detail.instance.instanceId} addresses={outputFileAddresses(run, template)} known={fileIndex(detail)} />}
          <ul className="wf-verifications">{verifications.map(item => <li key={item.verificationId}>
            <span className={item.result === 'PASSED' ? 'wf-ok-text' : 'wf-danger-text'}>{item.result === 'PASSED' ? '✓' : '✕'}</span>
            {shortTime(item.verifiedTime)} {personName(item.verifiedBy, detail.people)} {item.result === 'PASSED' ? '验证通过' : `未通过：${item.failureReason ?? ''}`}
            {' '}（第 {roundOf(detail.actionRuns.find(row => row.actionRunId === item.actionRunId)?.nodeRunId ?? '')} 轮）</li>)}</ul>
        </div>;
      }) : <Empty description="本节点没有动作或尚未到达" /> },
      { key: 'approvals', label: `审批 ${approvals.length}`, children: approvals.length ? <ul className="wf-records">{approvals.map(item => <li key={item.approvalId}>
        <Tag color={approvalStatusColors[item.status]}>{approvalStatusLabels[item.status]}</Tag> {personName(item.approverId, detail.people)} · 第 {roundOf(item.nodeRunId)} 轮
        {item.decidedTime && ` · ${shortTime(item.decidedTime)}`}{item.comment && <div className="wf-quote">{item.comment}</div>}
        {item.rejectTargetNodeKey && <div className="muted">驳回到分支 {nodeOf(detail.graph, item.rejectTargetNodeKey)?.name ?? item.rejectTargetNodeKey}</div>}</li>)}</ul>
        : <Empty description="没有审批记录" /> },
      { key: 'records', label: `记录 ${transfers.length}`, children: transfers.length ? <ul className="wf-records">{transfers.map(item => <li key={item.transferId}>
        {shortTime(item.createTime)} {personName(item.transferredBy, detail.people)} 把{item.targetType === 'APPROVAL' ? '审批' : '动作'}从 {personName(item.fromUserId, detail.people)} 转交给 {personName(item.toUserId, detail.people)}
        {item.reason && `：${item.reason}`}</li>)}</ul> : <Empty description="没有转交记录" /> },
    ]} />
    <section className="wf-subtle"><div className="wf-card-title">本节点通知与回调</div>
      {(node.notifications ?? []).length ? (node.notifications ?? []).map((rule, index) => <div key={index} className="wf-rule-line">
        {triggerLabels[rule.trigger]} → {rule.type === 'CALLBACK' ? `回调 ${rule.callbackCode}` : `${(rule.channels ?? []).map(channel => channelLabels[channel]).join(' + ')}通知 ${(rule.recipients ?? []).map(item => recipientLabels[item]).join('、')}`}
      </div>) : <span className="muted">本节点没有配置通知或回调</span>}
      {deliveries.map((item, index) => <div key={index} className="wf-rule-line"><span>{shortTime(item.time)} {item.detail}</span>
        <Tag color={callbackStatusColors[item.status ?? ''] ?? 'default'}>{callbackStatusLabels[item.status ?? ''] ?? item.status}</Tag></div>)}
    </section>
    {myTask && <div className="wf-drawer-foot"><span>轮到你{myApproval ? '审批' : '执行'}</span>
      <Link to={`${workflowRoutes.myTasks}?task=${myTask}`}><Button type="primary">去{myApproval ? '审批' : '处理'}</Button></Link></div>}
  </div>;
}

type ActivityFilter = 'ALL' | 'MANUAL' | 'STATE' | 'NOTIFY' | 'CALLBACK';
const isDelivery = (item: Activity) => item.activityType === 'NOTIFY' || item.activityType === 'CALLBACK';
const activityGroups: Record<ActivityFilter, (item: Activity) => boolean> = {
  ALL: () => true,
  MANUAL: item => item.activityType === 'SUBMISSION' || item.activityType === 'APPROVAL' || item.activityType === 'TRANSFER',
  STATE: item => item.activityType === 'NODE' || item.activityType === 'INSTANCE',
  NOTIFY: item => item.activityType === 'NOTIFY',
  CALLBACK: item => item.activityType === 'CALLBACK',
};

/** 活动记录：人工操作、状态变化、通知与回调投递；失败或重试中的回调可手动重试（需管理实例权限）。 */
function ActivityLog({ detail, instanceId, canRetry, onAuthorizationChanged }: { detail: InstanceDetail; instanceId: string; canRetry: boolean; onAuthorizationChanged: () => void }) {
  const { message } = App.useApp();
  const activities = useLoad<Activity[]>(workflowPaths.activities(instanceId), decodeActivities);
  const [filter, setFilter] = useState<ActivityFilter>('ALL');
  const command = useWorkflowCommand(onAuthorizationChanged);
  const items = (activities.data ?? []).slice().sort((left, right) => right.time.localeCompare(left.time));
  const callbacks = items.filter(isDelivery);
  const callbackCodes = detail.graph.nodes.flatMap(node => (node.notifications ?? []).filter(rule => rule.type === 'CALLBACK').map(rule => ({ code: rule.callbackCode, node: node.name, trigger: rule.trigger })))
    .concat((detail.graph.settings?.notifications ?? []).filter(rule => rule.type === 'CALLBACK').map(rule => ({ code: rule.callbackCode, node: '实例', trigger: rule.trigger })));
  async function retry(callbackId: string) {
    const result = await command.run(workflowPaths.callbackRetry(callbackId), 'POST', undefined, decodeMutation, '重试失败');
    if (result) { void message.success('已重新排队投递'); activities.refresh(); }
  }
  if (activities.failure) return <LoadFailed failure={activities.failure} retry={activities.refresh} title="活动记录加载失败" />;
  return <div className="wf-activity">
    <section className="wf-card">
      <div className="wf-chips">{(['ALL', 'MANUAL', 'STATE', 'NOTIFY', 'CALLBACK'] as ActivityFilter[]).map(value => <button type="button" key={value}
        className={`wf-chip${filter === value ? ' is-active' : ''}`} onClick={() => setFilter(value)}>
        {{ ALL: '全部', MANUAL: '人工操作', STATE: '状态变化', NOTIFY: '通知', CALLBACK: '业务回调' }[value]} {items.filter(activityGroups[value]).length}</button>)}</div>
      {activities.loading && !activities.data ? <Spinner /> : <ul className="wf-timeline">{items.filter(activityGroups[filter]).map((item, index) => <li key={`${item.time}:${index}`}>
        <span className="wf-time">{shortTime(item.time)}</span>
        <span className={`wf-activity-icon is-${item.activityType.toLowerCase()}`} />
        <span className="wf-grow">{activityText(item, detail)}</span>
        {isDelivery(item) && item.status && <Tag color={callbackStatusColors[item.status] ?? 'default'}>{callbackStatusLabels[item.status] ?? item.status}</Tag>}
        {isDelivery(item) && canRetry && item.referenceId && item.status !== 'SENT' &&
          <Button size="small" loading={command.saving} onClick={() => void retry(item.referenceId!)}>立即重试</Button>}
      </li>)}</ul>}
      {command.formError && <Alert type="error" showIcon title={command.formError} />}
    </section>
    <aside>
      <section className="wf-card"><div className="wf-card-title">通知与回调投递</div>
        <div className="wf-counters"><div><strong className="wf-ok-text">{callbacks.filter(item => item.status === 'SENT').length}</strong>已送达</div>
          <div><strong className="wf-warn-text">{callbacks.filter(item => item.status === 'PENDING').length}</strong>重试中</div>
          <div><strong className="wf-danger-text">{callbacks.filter(item => item.status === 'FAILED').length}</strong>失败</div></div>
        <p className="muted">业务回调经 topic_platform_workflow_event 可靠投递（本地事务登记、提交后发送、失败退避重试，超过 5 次标记失败并告警）。订阅方按回调编码自行处理，执行前须自行校验业务事实。</p>
      </section>
      <section className="wf-card"><div className="wf-card-title">本流程的回调编码</div>
        {callbackCodes.length ? callbackCodes.map((item, index) => <div key={index} className="wf-code-line"><code>{item.code}</code>
          <div className="muted">{item.node} · {triggerLabels[item.trigger]}</div></div>) : <span className="muted">未配置业务回调</span>}
      </section>
    </aside>
  </div>;
}

function activityText(item: Activity, detail: InstanceDetail): string {
  const node = item.nodeKey ? nodeOf(detail.graph, item.nodeKey)?.name ?? item.nodeKey : '';
  const actor = item.actorName ?? (item.actorId ? personName(item.actorId, detail.people) : '');
  const round = item.roundNo && item.roundNo > 1 ? `第 ${item.roundNo} 轮 ` : '';
  const status = item.status && !isDelivery(item)
    ? (approvalStatusLabels as Record<string, string>)[item.status] ?? (nodeRunStatusLabels as Record<string, string>)[item.status]
      ?? (actionRunStatusLabels as Record<string, string>)[item.status] ?? (instanceStatusLabels as Record<string, string>)[item.status] ?? item.status : '';
  const text = [actor, node ? `「${node}」` : '', round, status, item.detail ?? ''].filter(Boolean).join(' ');
  return `${{ NODE: '节点', SUBMISSION: '提交', APPROVAL: '审批', TRANSFER: '转交', NOTIFY: '通知', CALLBACK: '回调', INSTANCE: '实例' }[item.activityType]} · ${text}`;
}

function StartInfo({ detail }: { detail: InstanceDetail }) {
  return <section className="wf-card">
    <div className="wf-card-title">启动信息<span className="muted">发起后不可修改；更正走后续节点动作</span></div>
    <dl className="wf-output">
      <div><dt>流程</dt><dd>{detail.definitionName}（{detail.definitionCode}）v{detail.versionNo}</dd></div>
      <div><dt>发起人</dt><dd>{personName(detail.instance.startedBy, detail.people)} · {shortTime(detail.instance.startTime)}</dd></div>
      <div><dt>业务关联键</dt><dd>{detail.instance.businessKey ?? '—'}</dd></div>
      {detail.startForm.fields.map(field => <div key={field.key}><dt>{field.label}</dt>
        <dd>{field.type === 'FILE' ? <AttachmentList instanceId={detail.instance.instanceId} addresses={fileAddressesOf(detail.startValues[field.key])} known={fileIndex(detail)} /> : displayValue(detail.startValues[field.key], field)}</dd></div>)}
    </dl>
  </section>;
}

function TerminateModal({ instanceId, onClose, onDone, onAuthorizationChanged }: { instanceId: string; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }) {
  const { message } = App.useApp();
  const [form] = Form.useForm<{ reason: string }>();
  const command = useWorkflowCommand(onAuthorizationChanged);
  async function submit({ reason }: { reason: string }) {
    const result = await command.run(workflowPaths.termination(instanceId), 'POST', { reason: reason.trim() }, decodeMutation, '终止失败');
    if (result) { void message.success('实例已终止'); onDone(); }
  }
  return <Modal open title="终止实例" okText="确认终止" okButtonProps={{ danger: true, loading: command.saving }} onCancel={onClose} onOk={() => form.submit()}>
    <p className="muted">终止后实例只读：进行中的节点与未完成动作取消，待审批撤回。终止不回退任何业务数据。</p>
    {command.formError && <Alert type="error" showIcon title={command.formError} />}
    {command.conflict && <Alert type="warning" showIcon title="实例状态已变化，请刷新后重试" />}
    <Form form={form} layout="vertical" onFinish={values => void submit(values)}>
      <Form.Item name="reason" label="终止原因" rules={[{ required: true, whitespace: true, message: '请填写终止原因' }]}>
        <Input.TextArea rows={3} maxLength={512} showCount /></Form.Item>
    </Form>
  </Modal>;
}
