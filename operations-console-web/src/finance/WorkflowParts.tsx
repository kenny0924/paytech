import { Alert, Button, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import type { ReactNode } from 'react';
import { session } from '../api/session';
import { localTime } from './finance';
import { approvalDecisionLabels, approvalRoleLabels, workflowStatusColors } from './financeWorkflow';
import type { Approval } from './financeWorkflow';
import { ConflictBanner } from './FinanceParts';
import type { useFinanceCommand } from './useFinanceCommand';
import { useFinanceList } from './useFinanceData';

type Command = ReturnType<typeof useFinanceCommand>;

/** 工作流状态标签：颜色取 workflowStatusColors，文案取中文映射。 */
export function WorkflowTag({ status, labels }: { status: string; labels?: Readonly<Record<string, string>> }) {
  return <Tag color={workflowStatusColors[status] ?? 'default'}>{labels?.[status] ?? status}</Tag>;
}

/** 操作人：当前登录账号标注「本人」。 */
export function ActorText({ value, actorId }: { value: string | null; actorId: string }) {
  if (!value) return <span className="muted">—</span>;
  return <span className="mono">{value}{value === actorId && <Tag className="finance-self-tag">本人</Tag>}</span>;
}

/** 摘要：显示前 16 位，可复制完整值。 */
export function HashText({ value }: { value: string | null }) {
  if (!value) return <span className="muted">—</span>;
  return <Typography.Text className="mono" copyable={{ text: value, tooltips: ['复制完整摘要', '已复制'] }} title={value}>{value.slice(0, 16)}…</Typography.Text>;
}

/** 审批记录：制单与复核两类，复核确认的摘要可复制。 */
export function ApprovalTable({ approvals, actorId }: { approvals: readonly Approval[]; actorId: string }) {
  return <section className="table-panel">
    <Table<Approval> rowKey={row => `${row.approvalRole}:${row.actorId}:${row.decisionTime}`} size="small" tableLayout="fixed" dataSource={[...approvals]}
      pagination={false} locale={{ emptyText: '还没有审批记录' }} columns={[
        { title: '角色', width: 80, render: (_, row) => approvalRoleLabels[row.approvalRole] },
        { title: '操作人', width: 220, render: (_, row) => <ActorText value={row.actorId} actorId={actorId} /> },
        { title: '决定', width: 80, render: (_, row) => <Tag color={row.decision === 'APPROVED' ? 'green' : 'red'}>{approvalDecisionLabels[row.decision]}</Tag> },
        { title: '确认摘要', render: (_, row) => <HashText value={row.approvedHash} /> },
        { title: '时间', width: 150, render: (_, row) => localTime(row.decisionTime) },
      ]} />
  </section>;
}

/** 单个对象详情：path 为空不请求；reloadToken 变化时重新加载。 */
export function useDetail<T>(path: string | null, decode: (value: unknown) => T, onAuthorizationChanged: () => void, failureTitle: string) {
  const state = useFinanceList<T>(path, async (url, signal) => [await session.request(url, { signal }, decode)], onAuthorizationChanged, failureTitle);
  return { item: state.items[0] as T | undefined, loading: state.loading, error: state.error, reload: state.reload };
}

/** 命令反馈：409 冲突横幅与已映射错误码的就地提示（含 Code），放在抽屉内容顶部。 */
export function CommandFeedback({ command, onReload }: { command: Command; onReload: () => void }) {
  return <>
    {command.conflict && <ConflictBanner onReload={onReload} />}
    {command.formError && <Alert type="error" showIcon className="finance-banner" title={command.formError} />}
  </>;
}

export interface ReviewAction { key: string; label: string; danger?: boolean; primary?: boolean; confirm: ReactNode; onConfirm: () => void }

/** 复核 / 处理按钮组：每个动作二次确认；执行中全部禁用，避免重复提交。 */
export function ReviewActions({ actions, saving, note }: { actions: readonly ReviewAction[]; saving: boolean; note?: ReactNode }) {
  if (!actions.length) return note ? <div className="finance-review-note">{note}</div> : null;
  return <div className="finance-review-bar">
    {note && <span className="finance-review-note">{note}</span>}
    <span className="finance-spacer" />
    <Space>{actions.map(action => <Popconfirm key={action.key} title={action.confirm} okText="确认" cancelText="取消" disabled={saving}
      okButtonProps={{ danger: action.danger }} onConfirm={action.onConfirm}>
      <Button type={action.primary ? 'primary' : 'default'} danger={action.danger} loading={saving}>{action.label}</Button>
    </Popconfirm>)}</Space>
  </div>;
}
