import type {
  ActionRunStatus, ActionType, ApprovalStatus, ConditionOperator, ConfigStatus, FieldType, InstanceStatus, NodeRunStatus, NodeType,
  NotificationChannel, NotificationRecipient, NotificationTrigger,
} from './workflowTypes.ts';

export const nodeTypeLabels: Record<NodeType, string> = {
  START: '开始', END: '结束', TASK: '任务节点', EXCLUSIVE: '排他网关', PARALLEL_SPLIT: '并行分叉', PARALLEL_JOIN: '并行汇合',
};
export const actionTypeLabels: Record<ActionType, string> = {
  FORM: '表单', FILE_UPLOAD: '上传文件', PAGE_TASK: '页面任务', MANUAL_CONFIRM: '人工确认', DATA_QUERY: '数据查询',
};
export const fieldTypeLabels: Record<FieldType, string> = {
  TEXT: '单行文本', LONG_TEXT: '多行文本', NUMBER: '数字', BOOLEAN: '布尔', ENUM: '枚举（单选）', DATE: '日期', FILE: '文件',
};
export const operatorLabels: Record<ConditionOperator, string> = {
  EQ: '等于', NE: '不等于', IN: '属于', CONTAINS: '包含', GT: '大于', GTE: '大于等于', LT: '小于', LTE: '小于等于',
  IS_EMPTY: '为空', IS_NOT_EMPTY: '不为空',
};
export const triggerLabels: Record<NotificationTrigger, string> = {
  ACTIVATED: '节点激活', APPROVAL_OPENED: '进入待审批', PASSED: '节点通过', RETURNED_FOR_REVISION: '退回修改', REJECTED: '被驳回',
  VERIFICATION_FAILED: '动作验证未通过', TRANSFERRED: '转交', STARTED: '实例发起', COMPLETED: '实例完成', TERMINATED: '实例终止',
};
/** 可选的节点触发；「动作验证未通过」已停用，标签只用于显示历史。 */
export const nodeTriggers: NotificationTrigger[] = ['ACTIVATED', 'APPROVAL_OPENED', 'PASSED', 'RETURNED_FOR_REVISION', 'REJECTED', 'TRANSFERRED'];
export const instanceTriggers: NotificationTrigger[] = ['STARTED', 'COMPLETED', 'TERMINATED'];
export const recipientLabels: Record<NotificationRecipient, string> = {
  NODE_EXECUTORS: '本节点执行人', NODE_APPROVERS: '本节点审批人', STARTER: '发起人', SPECIFIED_USERS: '指定人员',
};
export const channelLabels: Record<NotificationChannel, string> = { INBOX: '我的待办', EMAIL: '邮件' };
export const configStatusLabels: Record<ConfigStatus, string> = { ACTIVE: '启用', INACTIVE: '停用' };
export const instanceStatusLabels: Record<InstanceStatus, string> = { RUNNING: '运行中', COMPLETED: '已完成', TERMINATED: '已终止' };
export const instanceStatusColors: Record<InstanceStatus, string> = { RUNNING: 'processing', COMPLETED: 'success', TERMINATED: 'default' };
export const nodeRunStatusLabels: Record<NodeRunStatus, string> = {
  ACTIVE: '待处理', APPROVING: '待审批', WAITING: '等待汇合', PASSED: '已通过', RETURNED: '退回修改', REJECTED: '被驳回', CANCELLED: '已取消',
};
/** 旧编码按新语义显示：VERIFIED → 已完成，UNVERIFIED → 待处理。 */
export const actionRunStatusLabels: Record<ActionRunStatus, string> = {
  PENDING: '待处理', COMPLETED: '已完成', CANCELLED: '已取消', VERIFIED: '已完成', UNVERIFIED: '待处理',
};
export const actionRunStatusColors: Record<ActionRunStatus, string> = {
  PENDING: 'processing', COMPLETED: 'success', CANCELLED: 'default', VERIFIED: 'success', UNVERIFIED: 'processing',
};
export const approvalStatusLabels: Record<ApprovalStatus, string> = {
  PENDING: '待审批', APPROVED: '已同意', RETURNED: '退回修改', REJECTED: '已驳回', WITHDRAWN: '已撤回',
};
export const approvalStatusColors: Record<ApprovalStatus, string> = {
  PENDING: 'processing', APPROVED: 'success', RETURNED: 'warning', REJECTED: 'error', WITHDRAWN: 'default',
};
export const callbackStatusLabels: Record<string, string> = { PENDING: '重试中', SENT: '已送达', FAILED: '失败' };
export const callbackStatusColors: Record<string, string> = { PENDING: 'warning', SENT: 'success', FAILED: 'error' };

/** 动作类型说明（新建动作右侧说明，原型 13）。 */
export const actionTypeGuides: Record<ActionType, { summary: string; sees: string; submits: string; verifies: string; output: string }> = {
  FORM: { summary: '执行人按字段填写后点「完成」；只检查必填字段', sees: '按字段渲染的表单', submits: '各字段的值',
    verifies: '只检查必填', output: '<动作键>.<字段>' },
  FILE_UPLOAD: { summary: '上传一到多个文件后点「完成」；可设「至少上传一个」，安全检查在后台进行、不挡完成', sees: '拖放上传区、已上传文件与安全检查状态、执行说明',
    submits: '文件地址列表（本实例上传的文件）', verifies: '开启「至少上传一个」时检查有文件', output: '<动作键>.files' },
  PAGE_TASK: { summary: '带参数跳到控制台内部页面完成操作，回来点「完成」，说明选填', sees: '执行说明、目标页面与“打开页面”按钮、说明',
    submits: '说明（选填）', verifies: '执行人点「完成」', output: '<动作键>.note' },
  MANUAL_CONFIRM: { summary: '按执行说明在系统外完成，点「完成」，说明选填', sees: '执行说明与说明输入框', submits: '说明（选填）',
    verifies: '记录完成人与时间', output: '<动作键>.note' },
  DATA_QUERY: { summary: '以后由 data-service 执行它维护的 SQL 自动判断。本期可以先建，流程发布时不能引用', sees: '多次验证记录与证据列',
    submits: '无（由系统查询）', verifies: '查询结果满足通过规则', output: '指定的证据列' },
};
