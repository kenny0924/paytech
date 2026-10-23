import { enumLabel, translator } from '../i18n/index.ts';
import type {
  ActionRunStatus, ActionType, ApprovalStatus, ConditionOperator, ConfigStatus, FieldType, InstanceStatus, NodeRunStatus, NodeType,
  NotificationChannel, NotificationRecipient, NotificationTrigger,
} from './workflowTypes.ts';

const t = translator('workflow');

/** 枚举显示名在调用时按当前语言取（workflow.enum.<类型>.<编码>）；未知编码原样显示。 */
export const nodeTypeLabel = (value: NodeType) => enumLabel(t, 'enum.nodeType', value);
export const actionTypeLabel = (value: ActionType) => enumLabel(t, 'enum.actionType', value);
export const fieldTypeLabel = (value: FieldType) => enumLabel(t, 'enum.fieldType', value);
export const operatorLabel = (value: ConditionOperator) => enumLabel(t, 'enum.operator', value);
export const triggerLabel = (value: NotificationTrigger) => enumLabel(t, 'enum.trigger', value);
/** 可选的节点触发；「动作验证未通过」已停用，标签只用于显示历史。 */
export const nodeTriggers: NotificationTrigger[] = ['ACTIVATED', 'APPROVAL_OPENED', 'PASSED', 'RETURNED_FOR_REVISION', 'REJECTED', 'TRANSFERRED'];
export const instanceTriggers: NotificationTrigger[] = ['STARTED', 'COMPLETED', 'TERMINATED'];
export const operators: ConditionOperator[] = ['EQ', 'NE', 'IN', 'CONTAINS', 'GT', 'GTE', 'LT', 'LTE', 'IS_EMPTY', 'IS_NOT_EMPTY'];
export const recipientLabel = (value: NotificationRecipient) => enumLabel(t, 'enum.recipient', value);
export const channelLabel = (value: NotificationChannel) => enumLabel(t, 'enum.channel', value);
export const configStatusLabel = (value: ConfigStatus) => enumLabel(t, 'enum.configStatus', value);
export const instanceStatusLabel = (value: InstanceStatus | string) => enumLabel(t, 'enum.instanceStatus', value);
export const instanceStatusColors: Record<InstanceStatus, string> = { RUNNING: 'processing', COMPLETED: 'success', TERMINATED: 'default' };
export const nodeRunStatusLabel = (value: NodeRunStatus | string) => enumLabel(t, 'enum.nodeRunStatus', value);
/** 旧编码按新语义显示：VERIFIED → 已完成，UNVERIFIED → 待处理。 */
export const actionRunStatusLabel = (value: ActionRunStatus | string) => enumLabel(t, 'enum.actionRunStatus', value);
export const actionRunStatusColors: Record<ActionRunStatus, string> = {
  PENDING: 'processing', COMPLETED: 'success', CANCELLED: 'default', VERIFIED: 'success', UNVERIFIED: 'processing',
};
export const approvalStatusLabel = (value: ApprovalStatus | string) => enumLabel(t, 'enum.approvalStatus', value);
export const approvalStatusColors: Record<ApprovalStatus, string> = {
  PENDING: 'processing', APPROVED: 'success', RETURNED: 'warning', REJECTED: 'error', WITHDRAWN: 'default',
};
export const callbackStatusLabel = (value: string) => enumLabel(t, 'enum.callbackStatus', value);
export const callbackStatusColors: Record<string, string> = { PENDING: 'warning', SENT: 'success', FAILED: 'error' };

/** 活动记录里的状态编码：依次按审批、节点、动作、实例状态解释，都没有时原样显示。 */
export function anyStatusLabel(value: string): string {
  for (const prefix of ['enum.approvalStatus', 'enum.nodeRunStatus', 'enum.actionRunStatus', 'enum.instanceStatus']) {
    if (t.has(prefix + '.' + value)) return t(prefix + '.' + value);
  }
  return value;
}
