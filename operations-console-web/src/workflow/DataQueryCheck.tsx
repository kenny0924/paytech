import { useState } from 'react';
import type { ReactNode } from 'react';
import { App, Button, Tag } from 'antd';
import { CheckOutlined, ClockCircleOutlined, DownOutlined, InfoCircleOutlined, RightOutlined, SyncOutlined, WarningOutlined } from '@ant-design/icons';
import { actionTypeIcons } from './workflowIcons';
import { canCheckNow, checkFeedback, checkState, checkStateTag, criteriaText, failureCodeText, missingText, outputsInline } from './dataQueryModel';
import type { CheckState } from './dataQueryModel';
import { actionLabel, frozenTemplate } from './runtimeModel';
import type { useWorkflowCommand } from './useWorkflowCommand';
import { decodeDataQueryCheckResponse, workflowPaths } from './workflowApi';
import { actionTypeLabel } from './workflowLabels';
import { useT } from '../i18n/react';
import type { ActionRun, DataQueryCheck, DataQueryConfig, InstanceDetail, NodeRun } from './workflowTypes';
import { OutputView, personName, shortTime } from './WorkflowParts';

type Command = ReturnType<typeof useWorkflowCommand>;

/**
 * 数据查询动作（原型 19）：没有「完成」，跟进人可「立即检查」；展示完成标准、检查结果（已满足 / 尚未满足及原因 / 检查失败及连续次数）
 * 与最近检查时间。已满足的默认收起，其余展开。实例详情的节点抽屉共用（onChecked 刷新详情，参数为本次是否已自动完成）。
 */
export function DataQueryRow({ detail, run, nodeRun, actorId, command, onChecked }: {
  detail: InstanceDetail; run: ActionRun; nodeRun: NodeRun | undefined; actorId: string; command: Command; onChecked: (completed: boolean) => void;
}) {
  const t = useT('workflow');
  const { message } = App.useApp();
  const state = checkState(run);
  const [open, setOpen] = useState(state !== 'SATISFIED' && state !== 'CANCELLED');
  const [checking, setChecking] = useState(false);
  /** text 为 null 时为失败，说明取自命令的最新状态（409 说明、400 原因）。 */
  const [feedback, setFeedback] = useState<{ text: string | null; tone: 'info' | 'warn' | 'error' } | null>(null);
  const feedbackText = feedback && (feedback.text ?? command.conflictMessage ?? command.formError
    ?? (command.conflict ? t('task.nodeChanged') : t('dataQuery.check.failed')));
  const check = run.dataQueryCheck;
  const template = frozenTemplate(detail, run.actionKey);
  const criteria = criteriaText(check ?? (template?.config as DataQueryConfig | undefined));
  const tag = checkStateTag(state);
  const allowed = canCheckNow(run, nodeRun, detail.instance.status === 'RUNNING');
  const follower = run.assigneeId === actorId ? t('task.me') : personName(run.assigneeId, detail.people);
  const sub = state === 'SATISFIED' ? (run.submittedTime ? t('dataQuery.check.completedBySystem', { time: shortTime(run.submittedTime) }) : tag.label)
    : state === 'UNSATISFIED' ? t('dataQuery.check.missingCount', { count: check?.missing.length ?? 0 }) : tag.label;
  async function checkNow() {
    if (checking) return;
    setChecking(true); setFeedback(null);
    try {
      const response = await command.run(workflowPaths.dataQueryCheck(run.actionRunId), 'POST', {}, decodeDataQueryCheckResponse, t('dataQuery.check.failed'));
      if (!response) {
        // 409（落库时实例正被他人处理）或 400：说明在下次渲染时取命令的最新状态；随后刷新，下一轮重查会再执行。
        setFeedback({ text: null, tone: 'error' });
        onChecked(false);
        return;
      }
      const result = checkFeedback(response, actionLabel(detail, run));
      if (result.kind === 'completed') void message.success(result.text);
      else setFeedback({ text: result.text, tone: result.kind === 'inProgress' ? 'info' : result.warn ? 'warn' : 'info' });
      onChecked(result.kind === 'completed');
    } finally { setChecking(false); }
  }
  return <div className={`wf-action-row wf-dq-row is-${state.toLowerCase()}${open ? ' is-open' : ''}`}>
    <div className="wf-action-row-head">
      <button type="button" className="wf-chevron" aria-label={open ? t('task.collapse') : t('task.expand')} onClick={() => setOpen(!open)}>{open ? <DownOutlined /> : <RightOutlined />}</button>
      <span className="wf-action-icon">{actionTypeIcons.DATA_QUERY}</span>
      <div className="wf-grow" role="button" tabIndex={0} onClick={() => setOpen(!open)} onKeyDown={event => { if (event.key === 'Enter') setOpen(!open); }}>
        <div><strong>{actionLabel(detail, run)}</strong> <Tag color={run.required ? 'blue' : 'default'}>{run.required ? t('actions.required') : t('actions.optional')}</Tag></div>
        <div className="muted wf-ellipsis">{actionTypeLabel(run.actionType)} · {sub}</div>
      </div>
      {state === 'SATISFIED' && outputsInline(run.output) && <span className="muted wf-ellipsis wf-dq-outputs">{outputsInline(run.output)}</span>}
      <span className="wf-assignee"><span className="wf-avatar">{follower.slice(0, 1)}</span>{t('dataQuery.check.followedBy', { name: follower })}</span>
      <Tag color={tag.color} icon={state === 'CHECKING' ? <SyncOutlined spin /> : undefined}>{tag.label}</Tag>
    </div>
    {open && <div className="wf-action-row-body">
      <DataQueryCheckView run={run} state={state} check={check ?? null} criteria={criteria} />
      {allowed && <div className="wf-action-foot">
        <span className="wf-inline">
          <Button icon={<SyncOutlined spin={checking} />} disabled={checking} onClick={() => void checkNow()}>{checking ? t('dataQuery.check.checking') : t('dataQuery.check.now')}</Button>
          {feedback && <span className={feedback.tone === 'error' ? 'wf-danger-text' : feedback.tone === 'warn' ? 'wf-warn-text' : 'wf-info-text'} role="status">{feedbackText}</span>}
        </span>
        {!feedback && <span className="muted"><InfoCircleOutlined /> {t('task.dataQueryNoComplete')}</span>}
      </div>}
    </div>}
  </div>;
}

/** 检查块正文：状态行、完成标准、未满足原因或失败说明；已满足时列出写入的输出。 */
export function DataQueryCheckView({ run, state, check, criteria }: {
  run: Pick<ActionRun, 'output'>; state: CheckState; check: DataQueryCheck | null; criteria: string;
}) {
  const t = useT('workflow');
  const checkedAt = check?.lastCheckedAt ? t('dataQuery.check.lastChecked', { time: shortTime(check.lastCheckedAt) }) : '';
  const head = (icon: ReactNode, label: string, extra: string[]) => <div className="wf-dq-state">
    <span className="wf-dq-state-label">{icon} {label}</span>{extra.filter(Boolean).map((item, index) => <span key={index} className="muted">{item}</span>)}</div>;
  const criteriaLine = criteria ? <div>{t('dataQuery.check.criteria', { criteria })}</div> : null;
  if (state === 'SATISFIED') return <div className="wf-dq-panel is-satisfied">
    {head(<CheckOutlined />, t('dataQuery.check.SATISFIED'), [checkedAt])}{criteriaLine}
    {run.output && Object.keys(run.output).length > 0 && <OutputView output={run.output} />}</div>;
  if (state === 'CANCELLED') return <div className="wf-dq-panel">{criteriaLine}</div>;
  if (state === 'CHECKING') return <div className="wf-dq-panel is-checking">
    {head(<SyncOutlined spin />, t('dataQuery.check.checking'), [checkedAt])}{criteriaLine}
    <div className="muted">{t('dataQuery.check.autoComplete')}</div></div>;
  if (state === 'UNSATISFIED') return <div className="wf-dq-panel is-unsatisfied">
    {head(<ClockCircleOutlined />, t('dataQuery.check.UNSATISFIED'), [checkedAt, t('dataQuery.check.recheckEvery')])}{criteriaLine}
    {(check?.missing.length ?? 0) > 0 && <div className="wf-dq-missing"><div className="wf-field-label">{t('dataQuery.check.missing')}</div>
      <ul>{check!.missing.map(code => <li key={code}>{missingText(code)}{missingText(code) !== code && <code className="muted"> {code}</code>}</li>)}</ul></div>}
    <div className="muted">{t('dataQuery.check.autoComplete')}</div></div>;
  return <div className="wf-dq-panel is-failed">
    {head(<WarningOutlined />, t('dataQuery.check.FAILED'), [checkedAt, check?.consecutiveFailures ? t('dataQuery.check.failures', { count: check.consecutiveFailures }) : ''])}
    {criteriaLine}
    <div>{failureCodeText(check?.failureCode)}{check?.failureCode && <code className="muted"> {check.failureCode}</code>}</div>
    <div className="muted">{check?.lastSucceededAt ? t('dataQuery.check.lastSucceeded', { time: shortTime(check.lastSucceededAt) }) : t('dataQuery.check.neverSucceeded')}
      {' · '}{t('dataQuery.check.failedKeepsWaiting')}</div></div>;
}
