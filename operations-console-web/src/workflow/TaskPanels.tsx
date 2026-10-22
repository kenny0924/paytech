import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Form, Input, Segmented, Select, Space, Tag } from 'antd';
import type { TextAreaRef } from 'antd/es/input/TextArea';
import { Link } from 'react-router-dom';
import { changeSummary, diffOutputs, displayValue, outputOf, valueProblems } from './formModel';
import { nodeOf } from './graphModel';
import {
  actionLabel, batchSubmission, batchSummary, decisionError, decisionOptions, frozenTemplate, nodeActionOf, nodeApproverIds, pageTaskLink, previousRoundAction, revisionOf, routeChips, runtimeVariables,
} from './runtimeModel';
import type { BatchResult, BatchStatus } from './runtimeModel';
import { decodeMutation, decodeSubmission, workflowPaths } from './workflowApi';
import {
  actionRunStatusColors, actionRunStatusLabels, actionTypeLabels, approvalStatusLabels, nodeRunStatusLabels,
} from './workflowLabels';
import type {
  ActionRun, ApprovalDecision, FileUploadConfig, FormDefinition, InstanceDetail, ManualConfirmConfig, PageTaskConfig, Task,
} from './workflowTypes';
import { DynamicFields, OutputView, TransferPopover, personName, shortTime, workflowRoutes } from './WorkflowParts';
import type { useWorkflowCommand } from './useWorkflowCommand';
import { countProblem, fileAddressesOf, fileIndex, hasFileInput, outputFileAddresses, uploadRules } from './fileModel';
import { AttachmentList, FilePreview, FileUploadField } from './WorkflowFiles';

type Command = ReturnType<typeof useWorkflowCommand>;
/** 页面向处理区注册的快捷操作（Ctrl+Enter / A / R / T）。 */
export interface PanelHandles { submit?: () => void; approve?: () => void; giveBack?: () => void; transfer?: () => void }
interface PanelProps {
  detail: InstanceDetail; task: Task; actorId: string; command: Command; handles: React.RefObject<PanelHandles>;
  onChanged: (finished: boolean) => void;
}

const decisionLabels: Record<ApprovalDecision, string> = { APPROVE: '同意', RETURN: '退回修改', REJECT: '驳回' };
const decisionHints: Record<ApprovalDecision, string> = { APPROVE: '本节点放行', RETURN: '本节点重开新一轮，执行人修改后重新提交、重新审批', REJECT: '退回实际走过的上一个任务节点' };

/** 处理区顶部：节点名、状态、实例信息与路线条，右侧转交和查看完整流程。 */
export function TaskHeader({ detail, task, actorId, command, handles, onChanged }: PanelProps) {
  const { message } = App.useApp();
  const node = nodeOf(detail.graph, task.nodeKey);
  const nodeRun = task.taskType === 'ACTION'
    ? detail.nodeRuns.find(run => run.nodeRunId === detail.actionRuns.find(item => item.actionRunId === task.taskId)?.nodeRunId)
    : detail.nodeRuns.find(run => run.nodeRunId === detail.approvals.find(item => item.approvalId === task.taskId)?.nodeRunId);
  const [transferOpen, setTransferOpen] = useState(false);
  handles.current.transfer = () => setTransferOpen(true);
  const approvers = detail.approvals.filter(item => item.nodeRunId === nodeRun?.nodeRunId);
  const blocked = task.taskType === 'APPROVAL'
    ? Object.fromEntries(approvers.filter(item => item.approvalId !== task.taskId).map(item => [item.approverId, '已是本节点审批人'])) : {};
  async function transfer(toUserId: string, reason: string) {
    const path = task.taskType === 'ACTION' ? workflowPaths.actionTransfer(task.taskId) : workflowPaths.approvalTransfer(task.taskId);
    const result = await command.run(path, 'POST', { toUserId, ...(reason ? { reason } : {}) }, decodeMutation, '转交失败');
    if (result) { setTransferOpen(false); void message.success(`已转交给 ${personName(toUserId, detail.people)}`); onChanged(true); }
  }
  const executors = [...new Set(detail.actionRuns.filter(item => item.nodeRunId === nodeRun?.nodeRunId).map(item => personName(item.assigneeId, detail.people)))];
  const approverIds = nodeApproverIds(detail, task.nodeKey, nodeRun?.nodeRunId);
  const mode = node?.approval?.mode === 'ALL' ? '全部同意' : '任一人同意';
  return <section className="wf-card wf-task-head">
    <div className="wf-task-head-row">
      <h2>{node?.name || task.nodeKey}
        {nodeRun && <Tag color={nodeRun.status === 'APPROVING' ? 'orange' : 'blue'}>{nodeRunStatusLabels[nodeRun.status]}{nodeRun.roundNo > 1 ? ` · 第 ${nodeRun.roundNo} 轮` : ''}</Tag>}</h2>
      <Space>
        <TransferPopover title={`转交“${node?.name || task.nodeKey}”`} open={transferOpen} onOpenChange={setTransferOpen} actorId={actorId}
          people={detail.people} blocked={blocked} saving={command.saving} error={command.formError} onConfirm={(to, reason) => void transfer(to, reason)}>
          <Button>转交</Button></TransferPopover>
        <Link to={`${workflowRoutes.instances}/${detail.instance.instanceId}`}><Button>查看完整流程</Button></Link>
      </Space>
    </div>
    <div className="wf-meta">{[detail.instance.title, `${detail.definitionName} v${detail.versionNo}`, detail.instance.businessKey,
      `发起人 ${personName(detail.instance.startedBy, detail.people)}`,
      executors.length ? `执行人 ${executors.join('、')}` : '无动作',
      approverIds.length ? `审批人 ${approverIds.map(id => personName(id, detail.people)).join('、')}（${mode}）` : '无审批人，必选动作完成即放行',
    ].filter(Boolean).join(' · ')}</div>
    <div className="wf-route">{routeChips(detail.graph, detail.nodeRuns).map(chip =>
      <span key={chip.key} className={`wf-route-chip is-${chip.state}${chip.key === task.nodeKey ? ' is-current' : ''}`}>{chip.name}</span>)}</div>
  </section>;
}

/** 退回修改 / 驳回后的新一轮：顶部显示意见。 */
function RevisionBanner({ detail, nodeRunId, viewer }: { detail: InstanceDetail; nodeRunId: string; viewer: 'executor' | 'approver' }) {
  const nodeRun = detail.nodeRuns.find(run => run.nodeRunId === nodeRunId);
  const revision = nodeRun ? revisionOf(detail, nodeRun) : null;
  if (!revision) return null;
  const who = personName(revision.approval.approverId, detail.people);
  return <Alert type="warning" showIcon className="wf-revision"
    title={`${viewer === 'approver' ? '你' : who}在第 ${revision.roundNo} 轮${revision.kind === 'RETURNED' ? '退回修改' : '驳回'} · ${shortTime(revision.approval.decidedTime)}`}
    description={<><div className="wf-quote">{revision.approval.comment || '（未填写意见）'}</div>
      {viewer === 'executor' && <div className="muted">上一轮的内容已经带出来，只需改要改的地方；提交后重新验证，再重新审批。</div>}</>} />;
}

const pageOpenedKey = (actionRunId: string) => `workflow.page-opened.${actionRunId}`;
function readOpened(actionRunId: string): string | null {
  try { return window.localStorage.getItem(pageOpenedKey(actionRunId)); } catch { return null; }
}
function writeOpened(actionRunId: string) {
  try { window.localStorage.setItem(pageOpenedKey(actionRunId), new Date().toISOString()); } catch { /* 只作提示 */ }
}

/** 执行动作：列出本节点分配给我的动作，逐个提交并同步验证（原型 01、19）。 */
export function ActionTaskPanel({ detail, task, actorId, command, handles, onChanged }: PanelProps) {
  const current = detail.actionRuns.find(item => item.actionRunId === task.taskId);
  const nodeRun = detail.nodeRuns.find(run => run.nodeRunId === current?.nodeRunId);
  const runs = detail.actionRuns.filter(item => item.nodeRunId === current?.nodeRunId);
  const mine = runs.filter(item => item.assigneeId === actorId);
  const others = runs.filter(item => item.assigneeId !== actorId);
  const [expanded, setExpanded] = useState(task.taskId);
  useEffect(() => setExpanded(task.taskId), [task.taskId]);
  if (!current || !nodeRun) return <Alert type="warning" showIcon title="这条待办已不在当前实例中，可能已被处理或转交" />;
  const requiredOpen = runs.filter(item => item.required && item.status !== 'VERIFIED').length;
  const running = detail.instance.status === 'RUNNING' && nodeRun.status === 'ACTIVE';
  return <>
    <RevisionBanner detail={detail} nodeRunId={nodeRun.nodeRunId} viewer="executor" />
    <section className="wf-card">
      <div className="wf-card-title">要做的事{nodeRun.roundNo > 1 ? `（第 ${nodeRun.roundNo} 轮）` : ''}
        <span className={requiredOpen ? 'wf-danger-text' : 'wf-ok-text'}>{requiredOpen ? `${requiredOpen} / ${runs.filter(item => item.required).length} 项必选未完成` : '必选动作均已验证'}</span></div>
      {!running && <Alert type="info" showIcon title={detail.instance.status !== 'RUNNING' ? '实例已结束，只读' : `节点${nodeRunStatusLabels[nodeRun.status]}，不能再提交`} />}
      {mine.map(run => <ActionCard key={run.actionRunId} detail={detail} run={run} expanded={expanded === run.actionRunId} editable={running}
        onExpand={() => setExpanded(run.actionRunId)} command={command} handles={handles} focused={run.actionRunId === task.taskId}
        onSubmitted={finished => onChanged(finished && !mine.some(item => item.actionRunId !== run.actionRunId && item.required && item.status !== 'VERIFIED'))} />)}
      {others.map(run => <div key={run.actionRunId} className="wf-action wf-action-other">
        <span>{actionLabel(detail, run)}</span><Tag>{actionTypeLabels[run.actionType]}</Tag><Tag color={actionRunStatusColors[run.status]}>{actionRunStatusLabels[run.status]}</Tag>
        <span className="muted">执行人 {personName(run.assigneeId, detail.people)}</span></div>)}
    </section>
    <ReferenceInfo detail={detail} nodeRunId={nodeRun.nodeRunId} />
  </>;
}

function ActionCard({ detail, run, expanded, editable, onExpand, command, handles, focused, onSubmitted }: {
  detail: InstanceDetail; run: ActionRun; expanded: boolean; editable: boolean; onExpand: () => void; command: Command;
  handles: React.RefObject<PanelHandles>; focused: boolean; onSubmitted: (verified: boolean) => void;
}) {
  const { message } = App.useApp();
  const template = frozenTemplate(detail, run.actionKey);
  const located = nodeActionOf(detail.graph, run.actionKey);
  const previous = previousRoundAction(detail, run);
  const baseline = run.output ?? previous?.output ?? {};
  const [values, setValues] = useState<Record<string, unknown>>(baseline);
  const [form] = Form.useForm();
  const [result, setResult] = useState<{ status: string; reason: string | null } | null>(null);
  const [opened, setOpened] = useState(() => readOpened(run.actionRunId));
  const canSubmit = editable && run.status !== 'VERIFIED' && run.status !== 'CANCELLED';
  const formDefinition: FormDefinition | undefined = run.actionType === 'FORM' ? template?.config as FormDefinition : undefined;
  const changes = previous ? diffOutputs(previous.output, values) : [];
  const changed = new Set(changes.map(change => change.key));
  const verifications = detail.verifications.filter(item => item.actionRunId === run.actionRunId)
    .sort((left, right) => right.verifiedTime.localeCompare(left.verifiedTime));
  const config = template?.config;
  const noteRequired = run.actionType === 'MANUAL_CONFIRM' && !!(config as ManualConfirmConfig | undefined)?.noteRequired;
  const problems = formDefinition ? valueProblems(formDefinition, values) : {};
  const files = fileIndex(detail);
  const uploadConfig = run.actionType === 'FILE_UPLOAD' ? config as FileUploadConfig | undefined : undefined;
  const fileProblem = run.actionType === 'FILE_UPLOAD' ? countProblem(uploadRules(uploadConfig), fileAddressesOf(values.files).length, true) : null;
  const reverifiable = editable && run.status === 'UNVERIFIED' && !!run.output && hasFileInput(run, template);
  const localError = fileProblem ? `上传文件：${fileProblem}`
    : formDefinition && Object.keys(problems).length ? `${Object.keys(problems).length} 个字段未通过`
    : noteRequired && !String(values.note ?? '').trim() ? '请填写确认说明' : null;
  async function submit() {
    if (!canSubmit || localError) return;
    const output = formDefinition ? outputOf(formDefinition, values)
      : run.actionType === 'FILE_UPLOAD' ? { files: fileAddressesOf(values.files) } : { note: String(values.note ?? '').trim() };
    const submitted = await command.run(workflowPaths.submission(run.actionRunId), 'POST', { output }, decodeSubmission, '提交失败');
    if (!submitted) return;
    setResult({ status: submitted.status, reason: submitted.failureReason });
    if (submitted.status === 'VERIFIED') void message.success('已提交，验证通过');
    else void message.warning(`已提交，验证未通过：${submitted.failureReason ?? ''}`);
    onSubmitted(submitted.status === 'VERIFIED');
  }
  /** 重新验证已提交的文件（扫描完成后）；不修改提交内容。 */
  async function reverify() {
    const verified = await command.run(workflowPaths.verification(run.actionRunId), 'POST', undefined, decodeSubmission, '重新验证失败');
    if (!verified) return;
    setResult({ status: verified.status, reason: verified.failureReason });
    if (verified.status === 'VERIFIED') void message.success('重新验证通过');
    else void message.warning(`仍未通过：${verified.failureReason ?? ''}`);
    onSubmitted(verified.status === 'VERIFIED');
  }
  const submitRef = useRef(submit); submitRef.current = submit;
  useEffect(() => {
    if (!focused || !expanded) return;
    handles.current.submit = () => void submitRef.current();
    return () => { handles.current.submit = undefined; };
  }, [focused, expanded, handles]);
  const header = <div className="wf-action-head" onClick={onExpand}>
    <strong>{actionLabel(detail, run)}</strong><Tag color="blue">{actionTypeLabels[run.actionType]}</Tag>
    {run.required ? <Tag color="red">必选</Tag> : <Tag>可选</Tag>}
    <span className="wf-grow" />
    {previous && changes.length > 0 && <Tag color="gold">已修改</Tag>}
    <Tag color={actionRunStatusColors[run.status]}>{actionRunStatusLabels[run.status]}</Tag>
    {!expanded && canSubmit && <Button size="small">{run.required ? '去处理' : '填写'}</Button>}
  </div>;
  if (!expanded) return <div className="wf-action is-collapsed">{header}
    {!run.required && run.status === 'PENDING' && <div className="muted">未填写，不阻塞放行</div>}</div>;
  const instruction = (config as { instruction?: string } | undefined)?.instruction;
  const link = run.actionType === 'PAGE_TASK' && config ? pageTaskLink(config as PageTaskConfig, located?.action.inputs, runtimeVariables(detail)) : null;
  return <div className={`wf-action is-expanded${run.status === 'UNVERIFIED' ? ' is-failed' : ''}`}>
    {header}
    {!template && <Alert type="warning" showIcon title="发布快照里找不到该动作的模板配置" />}
    {instruction && <p className="wf-instruction">{instruction}</p>}
    {run.actionType === 'PAGE_TASK' && <div className="wf-page-link">
      <div><strong>{(config as PageTaskConfig | undefined)?.pageRoute}</strong>
        <div className="muted">{link ?? '目标页面不是站内路由，已拒绝跳转'}（参数来自实例变量）</div></div>
      {opened && <span className="wf-ok-text">{shortTime(opened)} 已打开过</span>}
      <Button type="primary" disabled={!link} onClick={() => { if (!link) return; window.open(link, '_blank', 'noopener'); writeOpened(run.actionRunId); setOpened(new Date().toISOString()); }}>打开页面</Button>
    </div>}
    {run.actionType === 'FILE_UPLOAD' && canSubmit && <FileUploadField value={values.files} rules={uploadRules(uploadConfig)} known={files}
      onChange={ids => setValues({ files: ids })} />}
    {formDefinition && <Form form={form} layout="vertical" disabled={!canSubmit} initialValues={baseline}
      onValuesChange={(_, all) => setValues(all)}>
      <DynamicFields form={formDefinition} files={files} changed={previous ? changed : undefined} previous={previous?.output} problems={canSubmit ? problems : {}} />
    </Form>}
    {(run.actionType === 'PAGE_TASK' || run.actionType === 'MANUAL_CONFIRM') && <div className="wf-field">
      <div className="wf-field-label">确认说明{noteRequired ? ' *' : '（选填）'}{changed.has('note') && <Tag color="gold" className="wf-changed-tag">已修改</Tag>}</div>
      <Input.TextArea rows={3} maxLength={4000} disabled={!canSubmit} value={String(values.note ?? '')}
        onChange={event => setValues({ note: event.target.value })} />
      {previous && changed.has('note') && <div className="wf-diff-line">上一轮：<del>{displayValue(previous.output?.note)}</del> → <ins>{displayValue(values.note)}</ins></div>}
    </div>}
    {!canSubmit && run.output && <OutputView output={run.output} form={formDefinition} />}
    {!canSubmit && outputFileAddresses(run, template).length > 0 && <AttachmentList instanceId={detail.instance.instanceId}
      addresses={outputFileAddresses(run, template)} known={files} />}
    {(result?.status === 'UNVERIFIED' || (!result && run.status === 'UNVERIFIED')) && <Alert type="error" showIcon
      title="验证未通过" description={result?.reason ?? verifications[0]?.failureReason ?? '请按提示修改后重新提交'} />}
    {verifications.length > 0 && <ul className="wf-verifications">{verifications.map(item => <li key={item.verificationId}>
      <span className={item.result === 'PASSED' ? 'wf-ok-text' : 'wf-danger-text'}>{item.result === 'PASSED' ? '✓' : '✕'}</span>
      {shortTime(item.verifiedTime)} {personName(item.verifiedBy, detail.people)} {item.result === 'PASSED' ? '验证通过' : `未通过：${item.failureReason ?? ''}`}</li>)}</ul>}
    {canSubmit && <div className="wf-action-foot">
      <span className="muted">{previous ? `与上一轮相比：${changeSummary(changes, { note: '确认说明', files: '文件' })}` : '提交后立即验证；验证通过即完成，不能再修改'}</span>
      <Space>
        {previous && changes.length > 0 && <Button onClick={() => { setValues(previous.output ?? {}); form.setFieldsValue(previous.output ?? {}); }}>撤销本轮修改</Button>}
        {reverifiable && <Button loading={command.saving} title="文件扫描完成后，按已提交的文件重新验证" onClick={() => void reverify()}>重新验证</Button>}
        <Button type="primary" loading={command.saving} disabled={!!localError} title={localError ?? undefined} onClick={() => void submit()}>
          {run.actionType === 'FORM' ? '提交' : run.actionType === 'FILE_UPLOAD' ? '提交' : '确认完成'}</Button>
      </Space>
    </div>}
    {canSubmit && localError && <div className="wf-danger-text wf-right">{localError}</div>}
    {command.formError && focused && <Alert type="error" showIcon title={command.formError} />}
  </div>;
}

/** 参考信息：启动表单与上一节点的已验证输出。 */
function ReferenceInfo({ detail, nodeRunId }: { detail: InstanceDetail; nodeRunId: string }) {
  const nodeRun = detail.nodeRuns.find(run => run.nodeRunId === nodeRunId);
  let previous = detail.nodeRuns.find(run => run.nodeRunId === nodeRun?.previousNodeRunId);
  const visited = new Set<string>();
  while (previous && previous.nodeType !== 'TASK' && !visited.has(previous.nodeRunId)) {
    visited.add(previous.nodeRunId);
    previous = detail.nodeRuns.find(run => run.nodeRunId === previous!.previousNodeRunId);
  }
  const previousOutputs = previous ? detail.actionRuns.filter(item => item.nodeRunId === previous!.nodeRunId && item.output) : [];
  const startText = detail.startForm.fields.filter(field => field.type !== 'FILE')
    .map(field => `${field.label} ${displayValue(detail.startValues[field.key], field)}`).join(' · ');
  const files = detail.startForm.fields.filter(field => field.type === 'FILE');
  return <section className="wf-card">
    <div className="wf-card-title">参考信息</div>
    <div className="wf-reference">
      <div><div className="wf-field-label">启动表单</div>{startText || '—'}</div>
      {files.map(field => <div key={field.key}><div className="wf-field-label">{field.label}</div>
        <AttachmentList instanceId={detail.instance.instanceId} addresses={fileAddressesOf(detail.startValues[field.key])} known={fileIndex(detail)} /></div>)}
      {previous && <div><div className="wf-field-label">上一节点 · {nodeOf(detail.graph, previous.nodeKey)?.name ?? previous.nodeKey}</div>
        {previousOutputs.map(item => `${item.actionKey}：${Object.values(item.output ?? {}).map(value => displayValue(value)).join('，')}`).join('；') || '纯审批'}
        <span className="muted">（{nodeRunStatusLabels[previous.status]} · {shortTime(previous.completedTime)}）</span></div>}
    </div>
  </section>;
}

/** 审批决定：待审内容、上一轮对比、文件内嵌预览、同意 / 退回修改 / 驳回（原型 02、20）。 */
export function ApprovalPanel({ detail, task, actorId, command, handles, onChanged }: PanelProps) {
  const { message } = App.useApp();
  const approval = detail.approvals.find(item => item.approvalId === task.taskId);
  const nodeRun = detail.nodeRuns.find(run => run.nodeRunId === approval?.nodeRunId);
  const runs = detail.actionRuns.filter(item => item.nodeRunId === nodeRun?.nodeRunId);
  const [decision, setDecision] = useState<ApprovalDecision>('APPROVE');
  const [comment, setComment] = useState(''); const [target, setTarget] = useState<string>();
  const [onlyChanges, setOnlyChanges] = useState(true);
  const [previewId, setPreviewId] = useState<string>();
  const known = fileIndex(detail);
  const reviewFiles = [...new Set(runs.flatMap(item => outputFileAddresses(item, frozenTemplate(detail, item.actionKey))))];
  const commentRef = useRef<TextAreaRef>(null);
  useEffect(() => { setDecision('APPROVE'); setComment(''); setTarget(undefined); }, [task.taskId]);
  const options = useMemo(() => approval && nodeRun ? decisionOptions({ approval, nodeRun, hasActions: runs.length > 0,
    submittedByMe: runs.some(item => item.submittedBy === actorId), instanceRunning: detail.instance.status === 'RUNNING' }) : null,
  [approval, nodeRun, runs, actorId, detail.instance.status]);
  const submitRef = useRef<() => void>(() => {});
  useEffect(() => {
    handles.current.submit = () => submitRef.current();
    handles.current.approve = () => setDecision('APPROVE');
    handles.current.giveBack = () => { if (options?.options[1].enabled) { setDecision('RETURN'); setTimeout(() => commentRef.current?.focus()); } };
    return () => { handles.current.submit = undefined; handles.current.approve = undefined; handles.current.giveBack = undefined; };
  }, [handles, options]);
  if (!approval || !nodeRun || !options) return <Alert type="warning" showIcon title="这条审批已不在当前实例中，可能已被处理或转交" />;
  const error = decisionError(options, decision, comment, target);
  async function submit() {
    if (error) { void message.warning(error); return; }
    const body = { decision, ...(comment.trim() ? { comment: comment.trim() } : {}), ...(decision === 'REJECT' && target ? { rejectTargetNodeKey: target } : {}) };
    const result = await command.run(workflowPaths.decision(task.taskId), 'POST', body, decodeMutation, '审批提交失败');
    if (result) { void message.success(`已${decisionLabels[decision]}`); onChanged(true); }
  }
  submitRef.current = () => void submit();
  const node = nodeOf(detail.graph, nodeRun.nodeKey);
  const decided = detail.approvals.filter(item => item.nodeRunId === nodeRun.nodeRunId && item.approvalId !== approval.approvalId && item.status !== 'PENDING');
  return <>
    <RevisionBanner detail={detail} nodeRunId={nodeRun.nodeRunId} viewer="approver" />
    <div className={reviewFiles.length ? 'wf-approval-grid' : ''}>
      <section className="wf-card">
        <div className="wf-card-title">待审内容
          {runs.length ? <Tag>已锁定</Tag> : <span className="muted">本节点没有动作，只需审批</span>}
          {nodeRun.roundNo > 1 && runs.length > 0 && <Segmented size="small" value={onlyChanges ? 'changes' : 'all'}
            onChange={value => setOnlyChanges(value === 'changes')} options={[{ value: 'changes', label: '只看变化' }, { value: 'all', label: '全部内容' }]} />}
        </div>
        {runs.map(run => <ReviewItem key={run.actionRunId} detail={detail} run={run} onlyChanges={nodeRun.roundNo > 1 && onlyChanges} />)}
        {decided.length > 0 && <div className="muted">{decided.map(item => `${personName(item.approverId, detail.people)} ${approvalStatusLabels[item.status]}`).join('；')}</div>}
      </section>
      {reviewFiles.length > 0 && <section className="wf-card wf-preview">
        <div className="wf-card-title">文件预览</div>
        <AttachmentList instanceId={detail.instance.instanceId} addresses={reviewFiles} known={known} selected={previewId}
          onPreview={file => setPreviewId(file.fileAddress)} />
        <FilePreview instanceId={detail.instance.instanceId} file={previewId ? known.get(previewId) : undefined} />
      </section>}
    </div>
    <section className="wf-card">
      <div className="wf-card-title">审批决定<span className="muted">{node?.approval?.mode === 'ALL' ? '全部同意才放行' : '任一人同意即放行'}</span></div>
      {options.blocked && <Alert type="warning" showIcon title={options.blocked} />}
      <div className="wf-decisions">{options.options.map(option => <button type="button" key={option.decision} disabled={!option.enabled}
        className={`wf-decision wf-decision-${option.decision.toLowerCase()}${decision === option.decision ? ' is-selected' : ''}`}
        onClick={() => setDecision(option.decision)}>
        <strong>{decisionLabels[option.decision]}{option.decision !== 'REJECT' && <kbd>{option.decision === 'APPROVE' ? 'A' : 'R'}</kbd>}</strong>
        <span>{option.enabled ? decisionHints[option.decision] : `不可用：${option.reason}`}</span>
      </button>)}</div>
      {decision === 'REJECT' && options.rejectTargets.length > 0 && <div className="wf-field">
        <div className="wf-field-label">驳回到哪一条分支 *</div>
        <Select value={target} onChange={setTarget} placeholder="选择分支的最后一个节点" style={{ width: 320 }}
          options={options.rejectTargets.map(key => ({ value: key, label: nodeOf(detail.graph, key)?.name ?? key }))} />
        <div className="wf-field-hint">其他分支保持已完成，汇合点重新等待这条分支完成。</div></div>}
      <div className="wf-field"><div className="wf-field-label">意见{decision === 'APPROVE' ? '（选填）' : ' *（退回修改和驳回必填，执行人可见）'}</div>
        <Input.TextArea ref={commentRef} rows={3} maxLength={512} showCount value={comment} onChange={event => setComment(event.target.value)} /></div>
      {command.formError && <Alert type="error" showIcon title={command.formError} />}
      {command.conflict && <Alert type="warning" showIcon title="这条审批已被他人处理或实例已变化，请刷新" />}
      <div className="wf-action-foot"><span className="muted">提交后按节点配置通知相关人员（邮件 + 我的待办）。</span>
        <Button type="primary" loading={command.saving} disabled={!!options.blocked} onClick={() => void submit()}>提交决定</Button></div>
    </section>
  </>;
}

/** 审批里的一项动作：第 2 轮起左右对比上一轮。 */
function ReviewItem({ detail, run, onlyChanges }: { detail: InstanceDetail; run: ActionRun; onlyChanges: boolean }) {
  const template = frozenTemplate(detail, run.actionKey);
  const form = run.actionType === 'FORM' ? template?.config as FormDefinition : undefined;
  const previous = previousRoundAction(detail, run);
  const changes = previous ? diffOutputs(previous.output, run.output) : [];
  if (onlyChanges && previous && !changes.length) return <div className="wf-review is-same"><strong>{actionLabel(detail, run)}</strong><span className="muted">与上一轮相同</span></div>;
  return <div className="wf-review">
    <div className="wf-action-head"><span className={run.status === 'VERIFIED' ? 'wf-ok-text' : 'wf-danger-text'}>{run.status === 'VERIFIED' ? '✓' : '•'}</span>
      <strong>{actionLabel(detail, run)}</strong><Tag color="blue">{actionTypeLabels[run.actionType]}</Tag>{!run.required && <Tag>可选</Tag>}
      <span className="wf-grow" />{changes.length > 0 && <Tag color="gold">已修改</Tag>}
      <span className="muted">{personName(run.submittedBy, detail.people)} · {shortTime(run.submittedTime)}</span></div>
    {previous && changes.length ? <div className="wf-compare">
      <div className="wf-compare-before"><div className="wf-field-label">上一轮</div><OutputView output={previous.output} form={form} highlight={new Set(changes.map(item => item.key))} /></div>
      <div className="wf-compare-after"><div className="wf-field-label">本轮</div><OutputView output={run.output} form={form} highlight={new Set(changes.map(item => item.key))} /></div>
    </div> : <OutputView output={run.output} form={form} />}
  </div>;
}

/** 批量同意：只含无动作的审批，逐项提交、逐项显示结果（原型 21）。 */
export function BatchPanel({ tasks, details, statuses, results, running, comment, onComment, onRun, onClear, onFinish }: {
  tasks: Task[]; details: Map<string, InstanceDetail>; statuses: ReadonlyMap<string, BatchStatus>; results: Map<string, BatchResult>; running: boolean;
  comment: string; onComment: (value: string) => void; onRun: () => void; onClear: () => void; onFinish: () => void;
}) {
  // 整体等待：任一项检查中时不能提交；检查完成后只提交可批量的项，其余列为排除。
  const plan = batchSubmission(tasks.map(task => task.taskId), statuses);
  const submitted = results.size > 0;
  const done = submitted && !running;
  const summary = batchSummary(tasks.map(task => task.taskId), results);
  return <>
    <section className="wf-card wf-task-head"><div className="wf-task-head-row"><h2>批量同意 {tasks.length} 项审批</h2>
      <Button onClick={onClear} disabled={running}>逐条处理</Button></div>
      <div className="wf-meta">每项单独提交、单独记录；任一项失败不影响其他项。退回修改和驳回需要逐条处理。</div></section>
    <section className="wf-card"><div className="wf-card-title">确认内容</div>
      <table className="wf-table"><thead><tr><th>实例 / 节点</th><th>关键信息</th><th>规则</th><th>结果</th></tr></thead>
        <tbody>{tasks.map(task => {
          const detail = details.get(task.instanceId); const node = detail ? nodeOf(detail.graph, task.nodeKey) : undefined;
          const result = results.get(task.taskId); const status = statuses.get(task.taskId);
          const approved = detail?.approvals.filter(item => item.nodeRunId === detail.approvals.find(row => row.approvalId === task.taskId)?.nodeRunId && item.status === 'APPROVED').length ?? 0;
          const total = detail?.approvals.filter(item => item.nodeRunId === detail.approvals.find(row => row.approvalId === task.taskId)?.nodeRunId).length ?? 0;
          return <tr key={task.taskId}><td><strong>{task.instanceTitle}</strong><div className="muted">{task.nodeName || node?.name || task.nodeKey}</div></td>
            <td>{detail ? detail.startForm.fields.filter(field => field.type !== 'FILE').slice(0, 3)
              .map(field => `${field.label} ${displayValue(detail.startValues[field.key], field)}`).join(' · ') || '—' : '加载中'}</td>
            <td>{node?.approval?.mode ?? '—'}{node?.approval?.mode === 'ALL' ? ` · 已同意 ${approved}/${total}` : ''}</td>
            <td>{result ? <Tag color={result.ok ? 'success' : 'error'}>{result.ok ? '已同意' : '未提交'}</Tag>
              : status?.state === 'CHECKING' ? <Tag color="processing">检查中</Tag>
              : status?.state === 'UNCHECKABLE' || status?.state === 'INELIGIBLE' ? <><Tag color="warning">不批量提交</Tag><div className="wf-warn-text">{status.reason}</div></>
              : running ? <Tag>等待中</Tag> : <Tag color="blue">可批量</Tag>}
              {result && !result.ok && <div className="wf-danger-text">{result.message}</div>}</td></tr>;
        })}</tbody></table></section>
    <section className="wf-card"><div className="wf-field-label">统一意见（选填，写入每一项）</div>
      <Input.TextArea rows={3} maxLength={512} value={comment} disabled={running || done} onChange={event => onComment(event.target.value)} /></section>
    <section className="wf-card wf-action-foot">
      <span>{done ? `完成：${summary.approved} 项已同意${summary.failed ? `，${summary.failed} 项未成功` : ''}${summary.skipped ? `，${summary.skipped} 项未提交（需单独处理）` : ''}`
        : plan.checking.length ? `正在检查 ${plan.checking.length} 项，检查完成后才能提交`
        : `将逐项提交 ${plan.submit.length} 项${plan.excluded.length ? `，${plan.excluded.length} 项不能批量、需单独处理` : ''}`}</span>
      {done ? <Button type="primary" onClick={onFinish}>完成</Button>
        : <Button type="primary" loading={running} disabled={!plan.ready} onClick={onRun}>批量同意</Button>}
    </section>
  </>;
}
