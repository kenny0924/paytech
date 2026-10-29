import type { ActionRun, DataQueryCheckResponse, DataQueryConfig, NodeRun } from './workflowTypes.ts';
import { actionState, isCompleted } from './runtimeModel.ts';
import { tOr, translator } from '../i18n/index.ts';

const t = translator('workflow');

/**
 * 数据查询动作（REQ-05）运行侧的纯逻辑：完成标准文字、未满足原因与失败码的文案、任务卡「系统检查」块的状态、
 * 「立即检查」结果的反馈与实例详情的检查计数。
 */

/** 完成标准：文案键（workflow 命名空间）有译文时用译文，否则用说明，都没有时用查询模板编码。 */
export function criteriaText(source: Partial<Pick<DataQueryConfig, 'descriptionKey' | 'instruction' | 'queryCode'>> | null | undefined): string {
  const key = source?.descriptionKey?.trim() ?? '';
  if (key.startsWith('workflow.') && t.has(key.slice('workflow.'.length))) return t(key.slice('workflow.'.length));
  return source?.instruction?.trim() || source?.queryCode || '';
}

/** 未满足原因码的文案；没有译文时显示原码（S-16）。 */
export const missingText = (code: string) => tOr(t, `dataQuery.missing.${code}`, code);

/** 检查失败码的文案；没有译文时用通用的「查询失败」并附原码。 */
export function failureCodeText(code: string | null | undefined): string {
  if (!code) return t('dataQuery.failure.QUERY_FAILED');
  return tOr(t, `dataQuery.failure.${code}`, `${t('dataQuery.failure.QUERY_FAILED')} (${code})`);
}

export type CheckState = 'SATISFIED' | 'UNSATISFIED' | 'FAILED' | 'CHECKING' | 'CANCELLED';

/** 任务卡上的检查状态：动作已完成即已满足；已取消；首查未完成为检查中；其余取最近一次检查结果（从未检查按检查中）。 */
export function checkState(run: Pick<ActionRun, 'status' | 'dataQueryCheck'>): CheckState {
  if (isCompleted(run)) return 'SATISFIED';
  if (actionState(run.status) === 'CANCELLED') return 'CANCELLED';
  const check = run.dataQueryCheck;
  if (!check || check.checking || !check.result) return 'CHECKING';
  return check.result;
}

/** 状态标签的文字与颜色。 */
export function checkStateTag(state: CheckState): { label: string; color: string } {
  switch (state) {
    case 'SATISFIED': return { label: t('dataQuery.check.SATISFIED'), color: 'success' };
    case 'UNSATISFIED': return { label: t('dataQuery.check.UNSATISFIED'), color: 'warning' };
    case 'FAILED': return { label: t('dataQuery.check.FAILED'), color: 'error' };
    case 'CANCELLED': return { label: t('enum.actionRunStatus.CANCELLED'), color: 'default' };
    case 'CHECKING': return { label: t('dataQuery.check.checking'), color: 'processing' };
  }
}

/** 可以「立即检查」：动作待处理、实例运行中，且节点仍开放（填写中或审批中）。 */
export function canCheckNow(run: Pick<ActionRun, 'status' | 'actionType'>, nodeRun: Pick<NodeRun, 'status'> | undefined, instanceRunning: boolean): boolean {
  return run.actionType === 'DATA_QUERY' && instanceRunning && actionState(run.status) === 'PENDING'
    && (nodeRun?.status === 'ACTIVE' || nodeRun?.status === 'APPROVING');
}

/** 已满足时的输出摘要：“crr = MEDIUM · mcc = 5732”；没有输出为空。 */
export function outputsInline(output: Record<string, unknown> | null | undefined): string {
  return Object.entries(output ?? {}).filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => `${key} = ${Array.isArray(value) ? value.join(', ') : String(value)}`).join(' · ');
}

export type CheckFeedback =
  | { kind: 'inProgress'; text: string }
  | { kind: 'completed'; text: string }
  | { kind: 'checked'; text: string; warn: boolean };

/** 「立即检查」的反馈：已有检查在进行 / 已满足并自动完成 / 本次检查的结果。 */
export function checkFeedback(response: Pick<DataQueryCheckResponse, 'inProgress' | 'actionStatus' | 'check'>, name: string): CheckFeedback {
  if (response.inProgress) return { kind: 'inProgress', text: t('dataQuery.check.inProgress') };
  if (actionState(response.actionStatus) === 'COMPLETED') return { kind: 'completed', text: t('dataQuery.check.completed', { name }) };
  const result = response.check?.result;
  if (result === 'FAILED') return { kind: 'checked', text: `${t('dataQuery.check.FAILED')}: ${failureCodeText(response.check?.failureCode)}`, warn: true };
  if (result === 'UNSATISFIED') return { kind: 'checked', text: t('dataQuery.check.stillMissing', { count: response.check?.missing.length ?? 0 }), warn: true };
  return { kind: 'checked', text: t('dataQuery.check.checked'), warn: false };
}

export interface CheckCounts { satisfied: number; unsatisfied: number; failed: number }

/** 实例详情「系统检查」计数：数据查询动作按检查状态计（已取消与检查中不计）。 */
export function checkCounts(runs: Pick<ActionRun, 'actionType' | 'status' | 'dataQueryCheck'>[]): CheckCounts {
  const states = runs.filter(run => run.actionType === 'DATA_QUERY').map(checkState);
  return { satisfied: states.filter(state => state === 'SATISFIED').length, unsatisfied: states.filter(state => state === 'UNSATISFIED').length,
    failed: states.filter(state => state === 'FAILED').length };
}

/** 节点上的动作是否全部为数据查询（任务区标题改为「我跟进的检查」、底部提示改为自动推进）。 */
export const allDataQuery = (runs: Pick<ActionRun, 'actionType'>[]) => runs.length > 0 && runs.every(run => run.actionType === 'DATA_QUERY');

/** 系统完成的动作：数据查询且已完成、没有提交人。 */
export const completedBySystem = (run: Pick<ActionRun, 'actionType' | 'status' | 'submittedBy'>) =>
  run.actionType === 'DATA_QUERY' && isCompleted(run) && !run.submittedBy;
