import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, App, Button, Dropdown, Empty, Form, Input, Modal, Select, Tag } from 'antd';
import { CheckOutlined, CloseOutlined, DownOutlined, InfoCircleOutlined, RightOutlined, UpOutlined, WarningOutlined } from '@ant-design/icons';
import { actionTypeIcons } from './workflowIcons';
import type { TextAreaRef } from 'antd/es/input/TextArea';
import { Link, useNavigate } from 'react-router-dom';
import { changeSummary, diffOutputs, displayValue, outputOf } from './formModel';
import { nodeOf } from './graphModel';
import {
  actionLabel, batchSubmission, batchSummary, currentHandlers, decisionError, decisionOptions, frozenTemplate, isCompleted, latestRuns, nodeActionOf,
  nodeApproverIds, pageTaskLink, previousRoundAction, revisionOf, runtimeVariables, traversedEdges, visualState, waitingText,
} from './runtimeModel';
import type { BatchResult, BatchStatus } from './runtimeModel';
import {
  actionProgress, anyScanPending, completionMode, completionProblems, defaultExpandedAction, orderNodeActions, outputFieldErrors, progressText, submissionSummary,
  taskSteps, visibleProblems,
} from './taskModel';
import type { TaskStep } from './taskModel';
import { decodeActivities, decodeMutation, decodeSubmission, workflowPaths } from './workflowApi';
import { actionRunStatusColors, actionRunStatusLabel, actionTypeLabel, approvalStatusLabel, instanceStatusColors, instanceStatusLabel } from './workflowLabels';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import type {
  ActionRun, Activity, ApprovalDecision, FileUploadConfig, FormDefinition, GraphNode, HandledTask, InstanceDetail, NodeRun, PageTaskConfig, Task,
} from './workflowTypes';
import { DynamicFields, OutputView, Spinner, TransferPopover, personName, shortTime, useLoad, workflowRoutes } from './WorkflowParts';
import type { useWorkflowCommand } from './useWorkflowCommand';
import {
  blockingFiles, fileAddressesOf, fileIndex, outputFileAddresses, scanFailedAddresses, startFileAddresses, uploadRequired, withoutInlineFileErrors,
} from './fileModel';
import { AttachmentList, FileCards, FileUploadField } from './WorkflowFiles';
import { WorkflowCanvas } from './WorkflowCanvas';
import { activityText, isDelivery } from './InstanceDetailView';

type Command = ReturnType<typeof useWorkflowCommand>;
/** 页面向处理区注册的快捷操作（Ctrl+Enter / A / R / T）。 */
export interface PanelHandles { submit?: () => void; approve?: () => void; giveBack?: () => void; transfer?: () => void }
interface PanelProps {
  detail: InstanceDetail; task: Task; actorId: string; command: Command; handles: React.RefObject<PanelHandles>;
  onChanged: (finished: boolean) => void;
  /** 本人有「流程实例」菜单：完整流程弹窗里另给「在流程实例中打开」。 */
  canOpenInstance?: boolean;
}

const nodeRunOf = (detail: InstanceDetail, task: Pick<Task, 'taskType' | 'taskId'>): NodeRun | undefined => {
  const nodeRunId = task.taskType === 'ACTION' ? detail.actionRuns.find(item => item.actionRunId === task.taskId)?.nodeRunId
    : detail.approvals.find(item => item.approvalId === task.taskId)?.nodeRunId;
  return detail.nodeRuns.find(run => run.nodeRunId === nodeRunId);
};

// ---------- 头部：标题、发起信息、横向进度条 ----------
/** 横向进度条：已完成 / 当前 / 未到；只沿主路径与已走过的分支（technique 第 2 节）。 */
export function TaskStepper({ steps, currentKey }: { steps: TaskStep[]; currentKey?: string }) {
  const t = useT('workflow');
  return <ol className="wf-stepper">{steps.map(step => <li key={step.key} className={`is-${step.state}`}>
    <span className="wf-step-dot">{step.state === 'done' ? <CheckOutlined /> : null}</span>
    <span className="wf-step-name">{step.name}</span>
    {step.state === 'current' && step.key === currentKey && <Tag color="blue" className="wf-step-tag">{t('task.current')}</Tag>}
  </li>)}</ol>;
}

/**
 * 只读完整流程弹窗（「查看流程图」「查看完整流程」「完整详情」）：节点按最新一轮着色、走过的连线实线，下方为活动记录。
 * 用已加载的实例详情，不跳转「流程实例」页（普通执行人与审批人可能没有该菜单）；有该菜单时另给「在流程实例中打开」。
 */
export function FullFlowModal({ detail, canOpenInstance, onClose }: { detail: InstanceDetail; canOpenInstance?: boolean; onClose: () => void }) {
  const t = useT('workflow');
  const runs = useMemo(() => latestRuns(detail.nodeRuns), [detail.nodeRuns]);
  const edges = useMemo(() => traversedEdges(detail.graph, detail.nodeRuns), [detail]);
  const decorate = useMemo(() => (node: GraphNode) => ({
    state: node.type === 'START' && detail.nodeRuns.length ? 'done' as const : visualState(runs.get(node.key)) }), [runs, detail.nodeRuns.length]);
  const edgeState = useMemo(() => (key: string) => edges.has(key) ? 'done' as const : 'pending' as const, [edges]);
  return <Modal open width={1080} title={`${detail.instance.title} · ${detail.definitionName} v${detail.versionNo}`} onCancel={onClose} destroyOnHidden
    footer={canOpenInstance ? <Link to={`${workflowRoutes.instances}/${detail.instance.instanceId}`} onClick={onClose}>{t('task.openInInstances')}</Link> : null}>
    <WorkflowCanvas graph={detail.graph} editable={false} selection={null} onSelect={() => {}} decorate={decorate} edgeState={edgeState} height={440}
      footer={<div className="wf-legend"><span className="is-done">{t('instance.legend.done')}</span><span className="is-active">{t('instance.legend.active')}</span><span className="is-pending">{t('instance.legend.pending')}</span>
        <span className="is-rejected">{t('instance.legend.rejected')}</span></div>} />
    <section className="wf-section wf-flow-activities"><div className="wf-section-title">{t('task.activity')}</div><ActivityTimeline detail={detail} /></section>
  </Modal>;
}

function DetailHeader({ detail, task, extra, status, canOpenInstance }: {
  detail: InstanceDetail; task?: Pick<Task, 'nodeKey' | 'waitingSince'>; extra?: ReactNode; status?: ReactNode; canOpenInstance?: boolean;
}) {
  const t = useT('workflow');
  const [flow, setFlow] = useState(false);
  const steps = useMemo(() => taskSteps(detail.graph, detail.nodeRuns), [detail.graph, detail.nodeRuns]);
  return <header className="wf-detail-head">
    <div className="wf-detail-title"><h2>{detail.instance.title}</h2>{status}<span className="wf-grow" />{extra}</div>
    <div className="wf-meta">{[`${detail.definitionName} v${detail.versionNo}`, t('task.meta.initiator', { name: personName(detail.instance.startedBy, detail.people) }),
      t('task.meta.startedAt', { time: shortTime(detail.instance.startTime) }), task?.waitingSince ? t('task.meta.waiting', { duration: waitingText(task.waitingSince) }) : ''].filter(Boolean).join(' · ')}</div>
    <div className="wf-stepper-row"><TaskStepper steps={steps} currentKey={task?.nodeKey} />
      <Button type="link" size="small" onClick={() => setFlow(true)}>{t('task.viewDiagram')}</Button></div>
    {flow && <FullFlowModal detail={detail} canOpenInstance={canOpenInstance} onClose={() => setFlow(false)} />}
  </header>;
}

// ---------- 背景与前序产出 ----------
/** 背景：启动信息键值与附件，默认展开，可收起为一行摘要。 */
function Background({ detail, defaultOpen = true }: { detail: InstanceDetail; defaultOpen?: boolean }) {
  const t = useT('workflow');
  const [open, setOpen] = useState(defaultOpen);
  const fields = detail.startForm.fields.filter(field => field.type !== 'FILE');
  const files = startFileAddresses(detail);
  const filled = fields.filter(field => displayValue(detail.startValues[field.key], field) !== '—');
  if (!fields.length && !files.length) return null;
  return <section className="wf-section">
    <div className="wf-section-title">{t('task.background')}<span className="muted">{t('task.backgroundCount', { count: fields.length })}{files.length ? ` · ${t('task.attachments', { count: files.length })}` : ''}</span>
      <span className="wf-grow" /><Button type="link" size="small" onClick={() => setOpen(!open)}>{open ? t('task.collapse') : t('task.expand')}</Button></div>
    {open ? <>
      <dl className="wf-kv">{fields.map(field => <div key={field.key}><dt>{field.label || field.key}</dt><dd>{displayValue(detail.startValues[field.key], field)}</dd></div>)}</dl>
      {files.length > 0 && <AttachmentList instanceId={detail.instance.instanceId} addresses={files} known={fileIndex(detail)} />}
    </> : <div className="muted wf-ellipsis">{filled.map(field => displayValue(detail.startValues[field.key], field)).join(' · ') || '—'}</div>}
  </section>;
}

/** 上一个任务节点（跳过网关）。 */
function previousTaskRun(detail: InstanceDetail, nodeRun: NodeRun | undefined): NodeRun | undefined {
  let previous = detail.nodeRuns.find(run => run.nodeRunId === nodeRun?.previousNodeRunId);
  const visited = new Set<string>();
  while (previous && previous.nodeType !== 'TASK' && !visited.has(previous.nodeRunId)) {
    visited.add(previous.nodeRunId);
    previous = detail.nodeRuns.find(run => run.nodeRunId === previous!.previousNodeRunId);
  }
  return previous;
}

/** 前序产出：上一个任务节点已完成动作的内容（审批时看）。 */
function PriorOutputs({ detail, nodeRun }: { detail: InstanceDetail; nodeRun: NodeRun | undefined }) {
  const t = useT('workflow');
  const previous = previousTaskRun(detail, nodeRun);
  if (!previous) return null;
  const outputs = detail.actionRuns.filter(item => item.nodeRunId === previous.nodeRunId && item.output && isCompleted(item));
  if (!outputs.length) return null;
  const submitters = [...new Set(outputs.map(item => personName(item.submittedBy, detail.people)))].join(t('list.comma'));
  const last = outputs.map(item => item.submittedTime ?? '').sort().at(-1);
  return <section className="wf-section">
    <div className="wf-section-title">{t('task.prior')}<span className="muted">{t('task.priorMeta', { node: nodeOf(detail.graph, previous.nodeKey)?.name ?? previous.nodeKey, people: submitters, time: shortTime(last) })}</span></div>
    {outputs.map(run => {
      const template = frozenTemplate(detail, run.actionKey);
      return <div key={run.actionRunId} className="wf-prior">
        {outputs.length > 1 && <div className="wf-field-label">{actionLabel(detail, run)}</div>}
        <OutputView output={run.output} form={run.actionType === 'FORM' ? template?.config as FormDefinition : undefined} />
        {outputFileAddresses(run, template).length > 0 && <AttachmentList instanceId={detail.instance.instanceId} addresses={outputFileAddresses(run, template)} known={fileIndex(detail)} />}
      </div>;
    })}
  </section>;
}

/** 退回修改 / 驳回后的新一轮：显示轮次与意见。 */
function RevisionBanner({ detail, nodeRun, viewer }: { detail: InstanceDetail; nodeRun: NodeRun; viewer: 'executor' | 'approver' }) {
  const t = useT('workflow');
  const revision = revisionOf(detail, nodeRun);
  if (!revision) return null;
  const who = personName(revision.approval.approverId, detail.people);
  return <Alert type="warning" showIcon className="wf-revision"
    title={t(revision.kind === 'RETURNED' ? 'revision.returned' : 'revision.rejected', { round: nodeRun.roundNo, comment: revision.approval.comment || t('revision.noComment'),
      who: viewer === 'approver' ? t('people.you') : who, time: shortTime(revision.approval.decidedTime) })}
    description={viewer === 'executor' ? t('revision.executorHint') : undefined} />;
}

// ---------- 操作栏 ----------
function ActionBar({ status, warn, more, children }: { status: ReactNode; warn?: boolean; more: ReactNode; children?: ReactNode }) {
  return <footer className="wf-actionbar"><span className={warn ? 'wf-warn-text' : 'muted'}>{warn ? <><WarningOutlined /> </> : null}{status}</span>
    <span className="wf-actionbar-buttons">{more}{children}</span></footer>;
}

/** 「更多」：转交、查看完整流程。转交浮层挂在按钮旁的锚点上。 */
function MoreMenu({ detail, task, actorId, command, handles, onChanged, canOpenInstance }: PanelProps) {
  const t = useT('workflow');
  const { message } = App.useApp();
  const [transferOpen, setTransferOpen] = useState(false);
  const [flow, setFlow] = useState(false);
  handles.current.transfer = () => setTransferOpen(true);
  const nodeRun = nodeRunOf(detail, task);
  const node = nodeOf(detail.graph, task.nodeKey);
  const actionRun = task.taskType === 'ACTION' ? detail.actionRuns.find(item => item.actionRunId === task.taskId) : undefined;
  const transferLabel = actionRun ? actionLabel(detail, actionRun) : node?.name || task.nodeKey;
  const blocked = task.taskType === 'APPROVAL' ? Object.fromEntries(detail.approvals.filter(item => item.nodeRunId === nodeRun?.nodeRunId && item.approvalId !== task.taskId)
    .map(item => [item.approverId, t('task.alreadyApprover')])) : {};
  async function transfer(toUserId: string, reason: string) {
    const path = task.taskType === 'ACTION' ? workflowPaths.actionTransfer(task.taskId) : workflowPaths.approvalTransfer(task.taskId);
    const result = await command.run(path, 'POST', { toUserId, ...(reason ? { reason } : {}) }, decodeMutation, t('task.transferFailed'));
    if (result) { setTransferOpen(false); void message.success(t('task.transferred', { name: personName(toUserId, detail.people) })); onChanged(true); }
  }
  return <>
    <TransferPopover title={t('task.transferTitle', { name: transferLabel })} open={transferOpen} onOpenChange={setTransferOpen} actorId={actorId}
      people={detail.people} blocked={blocked} saving={command.saving} error={command.formError} onConfirm={(to, reason) => void transfer(to, reason)}>
      <span className="wf-anchor" /></TransferPopover>
    <Dropdown trigger={['click']} menu={{ items: [{ key: 'transfer', label: t('transfer.submit') }, { key: 'flow', label: t('task.viewFullFlow') }],
      onClick: ({ key }) => { if (key === 'transfer') setTransferOpen(true); else setFlow(true); } }}>
      <Button>{t('actions.more')} <DownOutlined /></Button></Dropdown>
    {flow && <FullFlowModal detail={detail} canOpenInstance={canOpenInstance} onClose={() => setFlow(false)} />}
  </>;
}

// ---------- 待办详情 ----------
/**
 * 待办详情（原型 01、02、14、15）：头部（标题、发起信息、进度条）→ 背景 → 前序产出（审批）→ 我的任务 → 固定底部操作栏。
 */
export function TaskDetail(props: PanelProps) {
  const t = useT('workflow');
  const { detail, task } = props;
  const nodeRun = nodeRunOf(detail, task);
  if (!nodeRun) return <div className="wf-detail"><div className="wf-detail-scroll">
    <Alert type="warning" showIcon title={t('task.taskGone')} /></div></div>;
  return task.taskType === 'ACTION' ? <ActionTaskDetail {...props} nodeRun={nodeRun} /> : <ApprovalTaskDetail {...props} nodeRun={nodeRun} />;
}

/**
 * 填写待办：列表一项对应本节点运行上我的全部动作（stage-5 合并），这里列出本节点全部动作，必选在前；
 * 默认展开我的第一个待完成必选动作。「转交」作用于当前展开的我的动作（没有展开时为列表项的第一个动作）。
 */
function ActionTaskDetail(props: PanelProps & { nodeRun: NodeRun }) {
  const t = useT('workflow');
  const { detail, task, actorId, nodeRun, onChanged } = props;
  const runs = orderNodeActions(detail.actionRuns.filter(item => item.nodeRunId === nodeRun.nodeRunId));
  const running = detail.instance.status === 'RUNNING';
  const mine = runs.filter(item => item.assigneeId === actorId);
  const [expanded, setExpanded] = useState<string | null>(() => defaultExpandedAction(mine, nodeRun, running, actorId));
  const progress = actionProgress(runs);
  const node = nodeOf(detail.graph, task.nodeKey);
  const hasApprovers = nodeApproverIds(detail, task.nodeKey, nodeRun.nodeRunId).length > 0;
  const status = progressText(progress, null, hasApprovers);
  const focused = mine.find(item => item.actionRunId === expanded && completionMode(item, nodeRun, running, actorId) !== 'LOCKED');
  const transferTask: Task = focused ? { ...task, taskId: focused.actionRunId, actionKey: focused.actionKey } : task;
  function completed(run: ActionRun) {
    const others = mine.filter(item => item.actionRunId !== run.actionRunId);
    const next = others.find(item => completionMode(item, nodeRun, running, actorId) === 'COMPLETE');
    if (next) setExpanded(next.actionRunId);
    onChanged(!next);
  }
  return <div className="wf-detail">
    <div className="wf-detail-scroll">
      <DetailHeader detail={detail} task={task} canOpenInstance={props.canOpenInstance} />
      <Background detail={detail} />
      <section className="wf-section">
        <div className="wf-section-title">{t('task.mine')}<span className="muted">{node?.name ?? task.nodeKey}{runs.length > 1 ? ` · ${t('task.actionsOneByOne', { count: runs.length })}` : ''}
          {hasApprovers ? ` · ${t('task.thenApproval')}` : ''}</span></div>
        <RevisionBanner detail={detail} nodeRun={nodeRun} viewer="executor" />
        {!running && <Alert type="info" showIcon title={t('decision.blocked.instanceEnded')} />}
        {running && nodeRun.status === 'APPROVING' && <Alert type="info" showIcon title={t('task.approvingLocked')} />}
        {progress.required > 0 && <div className="wf-required">
          <span><strong>{t('progress.done', { required: progress.required, done: progress.requiredDone })}</strong>{progress.optional ? <span className="muted"> {t('task.optionalNotBlocking', { count: progress.optional })}</span> : null}</span>
          <div className="wf-required-bar"><span style={{ width: `${Math.round(progress.requiredDone / progress.required * 100)}%` }} /></div></div>}
        {runs.map(run => <ActionRow key={run.actionRunId} {...props} run={run} nodeRun={nodeRun} open={expanded === run.actionRunId}
          onToggle={() => setExpanded(expanded === run.actionRunId ? null : run.actionRunId)} onCompleted={completed} />)}
      </section>
    </div>
    <ActionBar status={status.text} warn={status.warn} more={<MoreMenu {...props} task={transferTask} />} />
  </div>;
}

const pageOpenedKey = (actionRunId: string) => `workflow.page-opened.${actionRunId}`;
function readOpened(actionRunId: string): string | null {
  try { return window.localStorage.getItem(pageOpenedKey(actionRunId)); } catch { return null; }
}
function writeOpened(actionRunId: string) {
  try { window.localStorage.setItem(pageOpenedKey(actionRunId), new Date().toISOString()); } catch { /* 只作提示 */ }
}

/**
 * 一个动作一行（原型 14）：点「完成」只检查必填；扫描中的文件不挡完成，已确认未通过安全检查的文件须删除后才能完成；
 * 已完成的必选动作在节点进入审批前可「修改」再完成，可选动作到放行前可完成；422 字段错误就地标到字段。
 */
function ActionRow({ detail, run, nodeRun, actorId, command, handles, open, onToggle, onCompleted }: PanelProps & {
  run: ActionRun; nodeRun: NodeRun; open: boolean; onToggle: () => void; onCompleted: (run: ActionRun) => void;
}) {
  const t = useT('workflow');
  const { message } = App.useApp();
  const navigate = useNavigate();
  const template = frozenTemplate(detail, run.actionKey);
  const config = template?.config;
  const located = nodeActionOf(detail.graph, run.actionKey);
  const previous = previousRoundAction(detail, run);
  const baseline = run.output ?? previous?.output ?? {};
  const mode = completionMode(run, nodeRun, detail.instance.status === 'RUNNING', actorId);
  const [editing, setEditing] = useState(mode === 'COMPLETE');
  useEffect(() => { setEditing(mode === 'COMPLETE'); }, [mode, run.actionRunId]);
  const [values, setValues] = useState<Record<string, unknown>>(baseline);
  const [touched, setTouched] = useState<ReadonlySet<string>>(new Set());
  const [attempted, setAttempted] = useState(false);
  const [server, setServer] = useState<Record<string, string>>({});
  /** 服务端 422 报告未通过安全检查的文件地址；删除前「完成」不可用。 */
  const [reportedFailed, setReportedFailed] = useState<ReadonlySet<string>>(new Set());
  const submitting = useRef(false);
  const lastOutput = useRef<Record<string, unknown>>({});
  const [form] = Form.useForm();
  const [opened] = useState(() => readOpened(run.actionRunId));
  const formDefinition = run.actionType === 'FORM' ? config as FormDefinition | undefined : undefined;
  const problems = completionProblems(run, template, values);
  const shown = { ...visibleProblems(problems, touched, attempted), ...server };
  const changes = previous ? diffOutputs(previous.output, values) : [];
  const changed = new Set(changes.map(change => change.key));
  const files = fileIndex(detail);
  const chosenFiles = run.actionType === 'FILE_UPLOAD' ? fileAddressesOf(values.files)
    : formDefinition ? formDefinition.fields.filter(field => field.type === 'FILE').flatMap(field => fileAddressesOf(values[field.key])) : [];
  const blocking = blockingFiles(chosenFiles, files, reportedFailed);
  const touch = (key: string) => setTouched(current => current.has(key) ? current : new Set(current).add(key));
  /** 内容变化后该字段的服务端错误作废（例如删除未通过安全检查的文件后）。 */
  const clearServer = (keys: string[]) => setServer(current => keys.some(key => key in current)
    ? Object.fromEntries(Object.entries(current).filter(([key]) => !keys.includes(key))) : current);
  const formPrefix = `wf${run.actionRunId}_`;
  // 422 字段错误只属于本行最近一次「完成」。
  useEffect(() => {
    if (!submitting.current) return;
    // 落在文件元素上的错误已在该文件上标红（未通过安全检查），不再在字段下重复一行。
    setServer(outputFieldErrors(withoutInlineFileErrors(command.fieldErrors, 'output', lastOutput.current)));
    setReportedFailed(new Set(scanFailedAddresses(command.fieldErrors, 'output', lastOutput.current)));
  }, [command.fieldErrors]);
  async function complete() {
    if (!editing || mode === 'LOCKED') return;
    setAttempted(true);
    const count = Object.keys(problems).length;
    if (count) { void message.warning(t('task.requiredLeft', { count })); return; }
    if (blocking.length) { void message.warning(t('launch.blockingFiles', { count: blocking.length })); return; }
    const output = formDefinition ? outputOf(formDefinition, values)
      : run.actionType === 'FILE_UPLOAD' ? { files: fileAddressesOf(values.files) } : { note: String(values.note ?? '').trim() };
    submitting.current = true; setServer({}); lastOutput.current = output;
    const result = await command.run(workflowPaths.submission(run.actionRunId), 'POST', { output }, decodeSubmission, t('task.completeFailed'));
    if (!result) return;
    submitting.current = false;
    void message.success(t('task.completed', { name: actionLabel(detail, run) }));
    setEditing(false); setAttempted(false); setTouched(new Set());
    onCompleted(run);
  }
  const completeRef = useRef(complete); completeRef.current = complete;
  useEffect(() => {
    if (!open || !editing) return;
    handles.current.submit = () => void completeRef.current();
    return () => { handles.current.submit = undefined; };
  }, [open, editing, handles]);
  const instruction = (config as { instruction?: string } | undefined)?.instruction;
  const link = run.actionType === 'PAGE_TASK' && config ? pageTaskLink(config as PageTaskConfig, located?.action.inputs, runtimeVariables(detail)) : null;
  const completed = isCompleted(run);
  const sub = completed && run.submittedTime ? t('task.completedAt', { time: shortTime(run.submittedTime) })
    : run.actionType === 'FILE_UPLOAD' ? (uploadRequired(config as FileUploadConfig | undefined) ? t('config.atLeastOne') : t('task.uploadOptional'))
    : instruction?.split('\n')[0] ?? '';
  const assignee = run.assigneeId === actorId ? t('task.me') : personName(run.assigneeId, detail.people);
  // 已就地标到字段的 422 不再重复显示汇总文案（其中含字段路径等技术信息）。
  const generalError = submitting.current ? (server._ ?? (Object.keys(server).length || reportedFailed.size ? null : command.formError)) : null;
  return <div className={`wf-action-row${open ? ' is-open' : ''}${mode === 'LOCKED' && !completed ? ' is-muted' : ''}`}>
    <div className="wf-action-row-head">
      <button type="button" className="wf-chevron" aria-label={open ? t('task.collapse') : t('task.expand')} onClick={onToggle}>{open ? <DownOutlined /> : <RightOutlined />}</button>
      <span className="wf-action-icon">{actionTypeIcons[run.actionType]}</span>
      <div className="wf-grow" role="button" tabIndex={0} onClick={onToggle} onKeyDown={event => { if (event.key === 'Enter') onToggle(); }}>
        <div><strong>{actionLabel(detail, run)}</strong> <Tag color={run.required ? 'blue' : 'default'}>{run.required ? t('actions.required') : t('actions.optional')}</Tag>
          {previous && changes.length > 0 && editing && <Tag color="gold">{t('parts.changed')}</Tag>}</div>
        <div className="muted wf-ellipsis">{actionTypeLabel(run.actionType)}{sub ? ` · ${sub}` : ''}</div>
      </div>
      {mode === 'EDIT' && !editing && <span className="wf-row-links">
        <Button type="link" size="small" onClick={() => { if (!open) onToggle(); }}>{t('task.viewContent')}</Button>
        <Button type="link" size="small" onClick={() => { setEditing(true); setValues(run.output ?? {}); form.setFieldsValue(run.output ?? {}); if (!open) onToggle(); }}>{t('designer.editKey')}</Button>
        {run.required && <span className="muted">{t('task.editBeforeApproval')}</span>}</span>}
      {!run.required && !completed && <span className="muted">{t('task.notBlocking')}</span>}
      <span className="wf-assignee"><span className="wf-avatar">{assignee.slice(0, 1)}</span>{assignee}</span>
      <Tag color={actionRunStatusColors[run.status]}>{actionRunStatusLabel(run.status)}</Tag>
    </div>
    {open && <div className="wf-action-row-body" onBlur={event => {
      const id = (event.target as HTMLElement).id;
      if (id.startsWith(formPrefix)) touch(id.slice(formPrefix.length));
    }}>
      {!template && <Alert type="warning" showIcon title={t('task.noSnapshot')} />}
      {instruction && <p className="wf-instruction">{instruction}</p>}
      {run.actionType === 'PAGE_TASK' && <div className="wf-page-link">
        <div><strong>{(config as PageTaskConfig | undefined)?.pageRoute}</strong>
          <div className="muted">{link ?? t('task.linkRejected')}{t('preview.parametersFromVariables')}</div>
          {link && <div className="muted">{t('task.linkHint')}</div>}</div>
        {opened && <span className="wf-ok-text">{t('task.openedAt', { time: shortTime(opened) })}</span>}
        {/* 会话只存在本标签页（refresh token 轮换不跨标签页共享），新标签页会落到登录页，所以站内跳转。 */}
        <Button disabled={!link} onClick={() => { if (!link) return; writeOpened(run.actionRunId); navigate(link); }}>{t('preview.openPage')}</Button>
      </div>}
      {editing ? <>
        {run.actionType === 'FILE_UPLOAD' && <div className="wf-field">
          <div className="wf-field-label">{fileAddressesOf(values.files).length ? t('task.uploadedCount', { count: fileAddressesOf(values.files).length }) : t('task.uploadFiles')}
            {uploadRequired(config as FileUploadConfig | undefined) ? t('task.atLeastOneSuffix') : t('task.optionalSuffix')}</div>
          <FileUploadField value={values.files} known={files} failed={reportedFailed}
            onChange={addresses => { setValues({ files: addresses }); touch('files'); clearServer(['files']); }} />
          {shown.files && <div className="wf-danger-text">{shown.files}</div>}</div>}
        {formDefinition && <Form form={form} name={`wf${run.actionRunId}`} layout="vertical" initialValues={baseline} onValuesChange={(changedValues, all) => { setValues(all); clearServer(Object.keys(changedValues)); }}>
          <div className="wf-form-grid"><DynamicFields form={formDefinition} files={files} failedFiles={reportedFailed} changed={previous ? changed : undefined} previous={previous?.output} problems={shown} /></div>
        </Form>}
        {(run.actionType === 'PAGE_TASK' || run.actionType === 'MANUAL_CONFIRM') && <div className="wf-field">
          <div className="wf-field-label">{t('preview.noteOptional')}{changed.has('note') && <Tag color="gold" className="wf-changed-tag">{t('parts.changed')}</Tag>}</div>
          <Input.TextArea rows={3} maxLength={4000} value={String(values.note ?? '')} onChange={event => setValues({ note: event.target.value })} />
          {shown.note && <div className="wf-danger-text">{shown.note}</div>}
          {previous && changed.has('note') && <div className="wf-diff-line">{t('task.previousRound')}<del>{displayValue(previous.output?.note)}</del> → <ins>{displayValue(values.note)}</ins></div>}
        </div>}
        {generalError && <Alert type="error" showIcon title={generalError} />}
        {submitting.current && command.conflict && <Alert type="warning" showIcon title={command.conflictMessage ?? t('task.nodeChanged')} />}
        <div className="wf-action-foot">
          <span className="wf-inline">
            <Button type="primary" loading={command.saving} disabled={blocking.length > 0}
              title={blocking.length ? t('task.blockingTitle') : undefined} onClick={() => void complete()}>{t('preview.complete')}</Button>
            {mode === 'EDIT' && <Button onClick={() => { setEditing(false); setValues(baseline); setAttempted(false); setServer({}); }}>{t('task.cancelEdit')}</Button>}
            {previous && changes.length > 0 && <Button onClick={() => { setValues(previous.output ?? {}); form.setFieldsValue(previous.output ?? {}); }}>{t('task.undoRound')}</Button>}
          </span>
          {/* 未通过安全检查的文件已在文件上标红、「完成」悬停有说明，这里不再重复一行汇总。 */}
          {blocking.length > 0 ? null
            : <span className="muted">{anyScanPending(chosenFiles, files) ? <><InfoCircleOutlined /> {t('task.scanInBackground')}</>
            : previous ? t('task.comparedPrevious', { summary: changeSummary(changes, { note: t('task.noteLabel'), files: t('parts.files') }) }) : ''}</span>}
        </div>
      </> : <>
        <OutputView output={run.output} form={formDefinition} />
        {outputFileAddresses(run, template).length > 0 && <AttachmentList instanceId={detail.instance.instanceId} addresses={outputFileAddresses(run, template)} known={files} />}
      </>}
    </div>}
  </div>;
}

// ---------- 审批 ----------
function ApprovalTaskDetail(props: PanelProps & { nodeRun: NodeRun }) {
  const t = useT('workflow');
  const { detail, task, actorId, command, handles, onChanged, nodeRun } = props;
  const { message } = App.useApp();
  const approval = detail.approvals.find(item => item.approvalId === task.taskId);
  const runs = detail.actionRuns.filter(item => item.nodeRunId === nodeRun.nodeRunId);
  const [comment, setComment] = useState(''); const [target, setTarget] = useState<string>();
  const [armed, setArmed] = useState<ApprovalDecision>('APPROVE');
  const [error, setError] = useState<string | null>(null);
  const commentRef = useRef<TextAreaRef>(null);
  useEffect(() => { setComment(''); setTarget(undefined); setArmed('APPROVE'); setError(null); }, [task.taskId]);
  const options = useMemo(() => approval ? decisionOptions({ approval, nodeRun, hasActions: runs.length > 0,
    submittedByMe: runs.some(item => item.submittedBy === actorId), instanceRunning: detail.instance.status === 'RUNNING' }) : null,
  [approval, nodeRun, runs, actorId, detail.instance.status]);
  async function decide(decision: ApprovalDecision) {
    if (!options) return;
    setArmed(decision);
    const problem = decisionError(options, decision, comment, target);
    if (problem) { setError(problem); if (decision !== 'APPROVE') commentRef.current?.focus(); return; }
    setError(null);
    const body = { decision, ...(comment.trim() ? { comment: comment.trim() } : {}), ...(decision === 'REJECT' && target ? { rejectTargetNodeKey: target } : {}) };
    const result = await command.run(workflowPaths.decision(task.taskId), 'POST', body, decodeMutation, t('task.decisionFailed'));
    if (result) { void message.success(enumLabel(t, 'decision.done', decision)); onChanged(true); }
  }
  const decideRef = useRef(decide); decideRef.current = decide;
  const armedRef = useRef(armed); armedRef.current = armed;
  useEffect(() => {
    handles.current.submit = () => void decideRef.current(armedRef.current);
    handles.current.approve = () => setArmed('APPROVE');
    handles.current.giveBack = () => { setArmed('RETURN'); setTimeout(() => commentRef.current?.focus()); };
    return () => { handles.current.submit = undefined; handles.current.approve = undefined; handles.current.giveBack = undefined; };
  }, [handles]);
  if (!approval || !options) return <div className="wf-detail"><div className="wf-detail-scroll">
    <Alert type="warning" showIcon title={t('task.approvalGone')} /></div></div>;
  const node = nodeOf(detail.graph, nodeRun.nodeKey);
  const approvers = nodeApproverIds(detail, nodeRun.nodeKey, nodeRun.nodeRunId);
  const progress = actionProgress(runs);
  const rule = node?.approval?.mode === 'ALL' ? t('task.ruleAll') : t('task.ruleAny');
  const decided = detail.approvals.filter(item => item.nodeRunId === nodeRun.nodeRunId && item.approvalId !== approval.approvalId && item.status !== 'PENDING');
  const status = options.blocked ?? [runs.length ? (progress.requiredDone === progress.required ? t('task.requiredAllDone')
    : t('task.requiredOpen', { count: progress.required - progress.requiredDone })) : '', t('task.approverCount', { count: approvers.length }), rule].filter(Boolean).join(' · ');
  const button = (decision: ApprovalDecision, type: 'primary' | 'default' | 'text') => {
    const option = options.options.find(item => item.decision === decision)!;
    return <Button key={decision} type={type} danger={decision === 'REJECT'} disabled={!option.enabled} title={option.enabled ? undefined : option.reason}
      className={armed === decision ? 'is-armed' : undefined} loading={command.saving && armed === decision} onClick={() => void decide(decision)}>{enumLabel(t, 'enum.decision', decision)}</Button>;
  };
  return <div className="wf-detail">
    <div className="wf-detail-scroll">
      <DetailHeader detail={detail} task={task} canOpenInstance={props.canOpenInstance} />
      <Background detail={detail} defaultOpen={false} />
      <PriorOutputs detail={detail} nodeRun={nodeRun} />
      <section className="wf-section">
        <RevisionBanner detail={detail} nodeRun={nodeRun} viewer="approver" />
        {runs.length > 0 ? <SubmissionTable detail={detail} runs={runs} />
          : <div className="wf-section-title">{t('task.submissions')}<span className="muted">{t('task.approvalOnly')}</span></div>}
        {decided.length > 0 && <div className="muted">{decided.map(item => `${personName(item.approverId, detail.people)} ${approvalStatusLabel(item.status)}`).join(t('list.semicolon'))}</div>}
      </section>
      <section className="wf-section">
        <div className="wf-section-title">{t('task.comment')}<span className="muted">{t('task.commentHint')}</span></div>
        {options.blocked && <Alert type="warning" showIcon title={options.blocked} />}
        <Input.TextArea ref={commentRef} rows={3} maxLength={512} showCount value={comment} placeholder={t('task.commentPlaceholder')}
          onChange={event => { setComment(event.target.value); setError(null); }} />
        {options.rejectTargets.length > 0 && <div className="wf-field wf-reject-target">
          <div className="wf-field-label">{t('task.rejectTarget')}</div>
          <Select value={target} onChange={setTarget} placeholder={t('task.rejectTargetPlaceholder')} style={{ width: 320 }}
            options={options.rejectTargets.map(key => ({ value: key, label: nodeOf(detail.graph, key)?.name ?? key }))} /></div>}
        {error && <div className="wf-danger-text">{error}</div>}
        {command.formError && <Alert type="error" showIcon title={command.formError} />}
        {command.conflict && <Alert type="warning" showIcon title={command.conflictMessage ?? t('task.approvalConflict')} />}
      </section>
    </div>
    <ActionBar status={status} warn={!!options.blocked} more={<MoreMenu {...props} />}>
      {button('REJECT', 'text')}{button('RETURN', 'default')}{button('APPROVE', 'primary')}
    </ActionBar>
  </div>;
}

/** 动作与提交内容（原型 15）：执行人、状态、完成时间与内容摘要；「查看」展开完整内容，可与上一轮对比，文件以卡片列出、弹窗预览（未通过安全检查的不可预览 / 下载）。 */
function SubmissionTable({ detail, runs }: { detail: InstanceDetail; runs: ActionRun[] }) {
  const t = useT('workflow');
  const [open, setOpen] = useState<string | null>(null);
  const known = fileIndex(detail);
  const progress = actionProgress(runs);
  return <>
    <div className="wf-section-title">{t('task.submissions')}<span className="muted">{t('task.nodeActions', { count: runs.length })}{progress.required ? ` · ${progress.requiredDone === progress.required
      ? t('task.requiredAllDoneCount', { count: progress.required }) : t('task.requiredDoneCount', { count: progress.required, done: progress.requiredDone })}` : ''}</span></div>
    <table className="wf-table wf-submissions"><thead><tr><th>{t('task.column.action')}</th><th>{t('actions.executor')}</th><th>{t('templateEditor.status')}</th><th>{t('task.column.completedAt')}</th><th>{t('task.column.content')}</th><th /></tr></thead>
      <tbody>{runs.flatMap(run => {
        const template = frozenTemplate(detail, run.actionKey);
        const expanded = open === run.actionRunId;
        const rows = [<tr key={run.actionRunId} className={expanded ? 'is-open' : undefined}>
          <td><span className="wf-action-icon is-small">{actionTypeIcons[run.actionType]}</span> {actionLabel(detail, run)} <Tag color={run.required ? 'blue' : 'default'}>{run.required ? t('actions.required') : t('actions.optional')}</Tag></td>
          <td>{personName(run.submittedBy ?? run.assigneeId, detail.people)}</td>
          <td><Tag color={actionRunStatusColors[run.status]}>{actionRunStatusLabel(run.status)}</Tag></td>
          <td>{isCompleted(run) ? shortTime(run.submittedTime) : '—'}</td>
          <td>{submissionSummary(run, template, known)}</td>
          <td className="wf-right">{run.output && <Button type="link" size="small" onClick={() => setOpen(expanded ? null : run.actionRunId)}>{expanded ? <>{t('task.collapse')} <UpOutlined /></> : t('files.view')}</Button>}</td>
        </tr>];
        if (expanded) rows.push(<tr key={`${run.actionRunId}:detail`} className="wf-submission-detail"><td colSpan={6}>
          <ReviewContent detail={detail} run={run} />
          {outputFileAddresses(run, template).length > 0 && <FileCards instanceId={detail.instance.instanceId} addresses={outputFileAddresses(run, template)} known={known} />}
          {previousRoundAction(detail, run) && <div className="muted">{t('task.latestShown')}</div>}
        </td></tr>);
        return rows;
      })}</tbody></table>
  </>;
}

/** 一项动作的完整内容：第 2 轮起与上一轮左右对比。 */
function ReviewContent({ detail, run }: { detail: InstanceDetail; run: ActionRun }) {
  const t = useT('workflow');
  const template = frozenTemplate(detail, run.actionKey);
  const form = run.actionType === 'FORM' ? template?.config as FormDefinition : undefined;
  const previous = previousRoundAction(detail, run);
  const changes = previous ? diffOutputs(previous.output, run.output) : [];
  if (run.actionType === 'FILE_UPLOAD') return null;
  return previous && changes.length ? <div className="wf-compare">
    <div className="wf-compare-before"><div className="wf-field-label">{t('task.previousRoundLabel')}</div><OutputView output={previous.output} form={form} highlight={new Set(changes.map(item => item.key))} /></div>
    <div className="wf-compare-after"><div className="wf-field-label">{t('task.thisRound')}</div><OutputView output={run.output} form={form} highlight={new Set(changes.map(item => item.key))} /></div>
  </div> : <OutputView output={run.output} form={form} />;
}

// ---------- 批量同意 ----------
/** 批量同意确认（原型 03）：确认清单 + 统一意见 + 同意 N 项；逐项提交、逐项显示结果。 */
export function BatchPanel({ tasks, details, statuses, results, running, comment, onComment, onRemove, onRun, onCancel, onFinish, untouched }: {
  tasks: Task[]; details: Map<string, InstanceDetail>; statuses: ReadonlyMap<string, BatchStatus>; results: Map<string, BatchResult>; running: boolean;
  comment: string; onComment: (value: string) => void; onRemove: (taskId: string) => void; onRun: () => void; onCancel: () => void; onFinish: () => void;
  /** 未选的待办数（仍留在待办中）。 */
  untouched: number;
}) {
  const t = useT('workflow');
  const plan = batchSubmission(tasks.map(task => task.taskId), statuses);
  const submitted = results.size > 0;
  const done = submitted && !running;
  const summary = batchSummary(tasks.map(task => task.taskId), results);
  const status = done ? [t('batchPanel.doneApproved', { count: summary.approved }), summary.failed ? t('batchPanel.doneFailed', { count: summary.failed }) : '',
    summary.skipped ? t('batchPanel.doneSkipped', { count: summary.skipped }) : ''].filter(Boolean).join(t('batchPanel.separator'))
    : !tasks.length ? t('batchPanel.selectHint')
    : plan.checking.length ? t('batchPanel.checking', { count: plan.checking.length })
    : t('batchPanel.selected', { count: tasks.length, untouched });
  return <div className="wf-detail">
    <div className="wf-detail-scroll">
      <header className="wf-detail-head"><div className="wf-detail-title"><h2>{t('batchPanel.title', { count: tasks.length })}</h2></div>
        <div className="wf-meta">{t('batchPanel.hint')}</div></header>
      {tasks.length ? <table className="wf-table wf-batch-table"><thead><tr><th>{t('enum.activityType.INSTANCE')}</th><th>{t('enum.activityType.NODE')}</th><th>{t('batchPanel.column.key')}</th><th>{t('batchPanel.column.rule')}</th><th>{t('batchPanel.column.result')}</th><th /></tr></thead>
        <tbody>{tasks.map(task => {
          const detail = details.get(task.instanceId); const node = detail ? nodeOf(detail.graph, task.nodeKey) : undefined;
          const result = results.get(task.taskId); const state = statuses.get(task.taskId);
          const nodeRunId = detail?.approvals.find(row => row.approvalId === task.taskId)?.nodeRunId;
          const rows = detail?.approvals.filter(item => item.nodeRunId === nodeRunId) ?? [];
          const approved = rows.filter(item => item.status === 'APPROVED').length;
          return <tr key={task.taskId}><td><strong>{task.instanceTitle}</strong></td>
            <td><Tag>{task.nodeName || node?.name || task.nodeKey}</Tag></td>
            <td>{detail ? detail.startForm.fields.filter(field => field.type !== 'FILE').slice(0, 3)
              .map(field => displayValue(detail.startValues[field.key], field)).filter(value => value !== '—').join(' · ') || '—' : t('batch.loading')}</td>
            <td>{node?.approval?.mode === 'ALL' ? t('batchPanel.ruleAll', { count: Math.max(0, rows.length - approved) }) : t('batchPanel.ruleAny')}</td>
            <td>{result ? <Tag color={result.ok ? 'success' : 'error'}>{result.ok ? t('batch.approved') : t('batchPanel.notSubmitted')}</Tag>
              : state?.state === 'CHECKING' ? <Tag color="processing">{t('batch.checking')}</Tag>
              : state?.state === 'UNCHECKABLE' || state?.state === 'INELIGIBLE' ? <><Tag color="warning">{t('batchPanel.excluded')}</Tag><div className="wf-warn-text">{state.reason}</div></>
              : running ? <Tag>{t('batchPanel.waiting')}</Tag> : <Tag color="blue">{t('batchPanel.toApprove')}</Tag>}
              {result && !result.ok && <div className="wf-danger-text">{result.message}</div>}</td>
            <td>{!submitted && <Button type="text" size="small" aria-label={t('batchPanel.remove')} icon={<CloseOutlined />} onClick={() => onRemove(task.taskId)} />}</td></tr>;
        })}</tbody></table> : <Empty description={t('batchPanel.empty')} />}
      <section className="wf-section"><div className="wf-section-title">{t('batchPanel.comment')}<span className="muted">{t('batchPanel.commentHint')}</span></div>
        <Input.TextArea rows={3} maxLength={512} value={comment} placeholder={t('batchPanel.commentPlaceholder')} disabled={running || done} onChange={event => onComment(event.target.value)} /></section>
    </div>
    <footer className="wf-actionbar"><span className="muted">{status}</span><span className="wf-actionbar-buttons">
      {done ? <Button type="primary" onClick={onFinish}>{t('preview.complete')}</Button> : <>
        <Button onClick={onCancel} disabled={running}>{t('action.cancel')}</Button>
        <Button type="primary" loading={running} disabled={!plan.ready} onClick={onRun}>{t('batchPanel.approveCount', { count: plan.submit.length })}</Button></>}
    </span></footer>
  </div>;
}

// ---------- 活动时间线（已办、我发起的） ----------
/** 竖向活动时间线：运行中先列当前等待的节点，再按时间倒序列人工操作与状态变化（不含通知与回调投递）。 */
export function ActivityTimeline({ detail, actorId }: { detail: InstanceDetail; actorId?: string }) {
  const t = useT('workflow');
  const activities = useLoad<Activity[]>(workflowPaths.activities(detail.instance.instanceId), decodeActivities);
  const items = (activities.data ?? []).filter(item => !isDelivery(item)).slice().sort((left, right) => right.time.localeCompare(left.time));
  const waiting = currentHandlers(detail, actorId);
  if (activities.loading && !activities.data) return <Spinner />;
  return <ul className="wf-vtimeline">
    {waiting.map(item => <li key={`wait:${item.nodeKey}`} className="is-waiting"><span className="wf-vdot" />
      <div><strong>{item.nodeName}</strong> <span className="wf-info-text">{t(item.stage === 'APPROVAL' ? 'timeline.waitingApproval' : 'timeline.waitingAction', { people: item.people.join(t('list.comma')) || t('timeline.handler'), duration: waitingText(item.waitingSince) })}</span></div>
      <span className="muted">{t('timeline.since', { time: shortTime(item.waitingSince) })}</span></li>)}
    {items.map((item, index) => <li key={`${item.time}:${index}`} className={`is-${item.activityType.toLowerCase()}${item.status === 'RETURNED' || item.status === 'REJECTED' ? ' is-warn' : ''}`}>
      <span className="wf-vdot" /><div>{activityText(item, detail, actorId)}</div><span className="muted">{shortTime(item.time)}</span></li>)}
    {!waiting.length && !items.length && <li className="muted">{t('timeline.empty')}</li>}
    {activities.failure ? <li className="wf-warn-text">{t('activity.loadFailed')}</li> : null}
  </ul>;
}

/** 已办只读详情（原型 04）：我的处理、背景、后续进展。 */
export function HandledDetail({ detail, handled, canOpenInstance }: { detail: InstanceDetail; handled: HandledTask; canOpenInstance?: boolean }) {
  const t = useT('workflow');
  const approval = handled.taskType === 'APPROVAL' ? detail.approvals.find(item => item.approvalId === handled.taskId) : undefined;
  const run = handled.taskType === 'ACTION' ? detail.actionRuns.find(item => item.actionRunId === handled.taskId) : undefined;
  const template = run ? frozenTemplate(detail, run.actionKey) : undefined;
  return <div className="wf-detail"><div className="wf-detail-scroll">
    <DetailHeader detail={detail} canOpenInstance={canOpenInstance} status={<Tag color={instanceStatusColors[detail.instance.status]}>{instanceStatusLabel(detail.instance.status)}</Tag>} />
    <section className="wf-section">
      <div className="wf-section-title">{t('handledDetail.title')}<span className="muted">{handled.nodeName ?? handled.nodeKey}{handled.roundNo > 1 ? ` · ${t('instance.round', { round: handled.roundNo })}` : ''}</span></div>
      <div className="wf-handled-box">
        <div className="muted">{approval ? t('handledDetail.decided', { time: shortTime(handled.handledTime), result: approvalStatusLabel(approval.status) })
          : run ? t('handledDetail.completed', { time: shortTime(handled.handledTime), name: actionLabel(detail, run) }) : t('handledDetail.handled', { time: shortTime(handled.handledTime) })}</div>
        {approval && (approval.comment ? <div className="wf-quote">{approval.comment}</div> : <span className="muted">{t('revision.noComment')}</span>)}
        {run && <><OutputView output={run.output} form={run.actionType === 'FORM' ? template?.config as FormDefinition : undefined} />
          {outputFileAddresses(run, template).length > 0 && <AttachmentList instanceId={detail.instance.instanceId} addresses={outputFileAddresses(run, template)} known={fileIndex(detail)} />}</>}
        {!approval && !run && <span className="muted">{t('handledDetail.gone')}</span>}
      </div>
    </section>
    <Background detail={detail} defaultOpen={false} />
    <section className="wf-section"><div className="wf-section-title">{t('handledDetail.progress')}</div><ActivityTimeline detail={detail} /></section>
  </div></div>;
}
