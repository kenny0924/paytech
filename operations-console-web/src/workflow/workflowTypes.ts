/**
 * 工作流前端类型集中定义（technique 第 3、6、7 节，并对齐 paytech-platform-support-api 的 workflow DTO 草稿）。
 * 后端 DTO 定稿后只需在本文件对齐；标注“前端假设”的字段尚未在后端 DTO 中出现。
 */

// ---------- 枚举 ----------
export type NodeType = 'START' | 'END' | 'TASK' | 'EXCLUSIVE' | 'PARALLEL_SPLIT' | 'PARALLEL_JOIN';

export type ConditionOperator = 'EQ' | 'NE' | 'IN' | 'CONTAINS' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'IS_EMPTY' | 'IS_NOT_EMPTY';

export type FieldType = 'TEXT' | 'LONG_TEXT' | 'NUMBER' | 'BOOLEAN' | 'ENUM' | 'DATE' | 'FILE';

export type ActionType = 'FORM' | 'FILE_UPLOAD' | 'PAGE_TASK' | 'MANUAL_CONFIRM' | 'DATA_QUERY';

export type ApprovalMode = 'ANY' | 'ALL';

export type InputSource = 'VARIABLE' | 'CONSTANT';

export type NotificationTrigger = 'ACTIVATED' | 'APPROVAL_OPENED' | 'PASSED' | 'RETURNED_FOR_REVISION' | 'REJECTED'
  | 'VERIFICATION_FAILED' | 'TRANSFERRED' | 'STARTED' | 'COMPLETED' | 'TERMINATED';

export type NotificationRuleType = 'NOTIFY' | 'CALLBACK';

export type NotificationRecipient = 'NODE_EXECUTORS' | 'NODE_APPROVERS' | 'STARTER' | 'SPECIFIED_USERS';

export type NotificationChannel = 'INBOX' | 'EMAIL';

export type ConfigStatus = 'ACTIVE' | 'INACTIVE';

export type InstanceStatus = 'RUNNING' | 'COMPLETED' | 'TERMINATED';

export type NodeRunStatus = 'ACTIVE' | 'APPROVING' | 'WAITING' | 'PASSED' | 'RETURNED' | 'REJECTED' | 'CANCELLED';

export type ActionRunStatus = 'PENDING' | 'VERIFIED' | 'UNVERIFIED' | 'CANCELLED';

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'RETURNED' | 'REJECTED' | 'WITHDRAWN';

export type ApprovalDecision = 'APPROVE' | 'RETURN' | 'REJECT';

/** 驳回方式：仅待审批的任务节点由后端给出。 */
export type RejectKind = 'PREVIOUS_TASK' | 'CHOOSE_BRANCH' | 'FIRST_TASK' | 'BRANCH_FIRST_NODE';

export type CallbackStatus = 'PENDING' | 'SENT' | 'FAILED';

/** NOTIFY 为通知投递，CALLBACK 为业务回调；INSTANCE 保留兼容。 */
export type ActivityType = 'NODE' | 'SUBMISSION' | 'APPROVAL' | 'TRANSFER' | 'NOTIFY' | 'CALLBACK' | 'INSTANCE';

export type TaskType = 'ACTION' | 'APPROVAL';

// ---------- graph_json（3.1） ----------
export interface ConditionClause { variable: string; operator: ConditionOperator; value?: string | string[] }
export interface EdgeCondition { edgeKey: string; all: ConditionClause[] }
export interface InputMapping { source: InputSource; value: string }
export interface NodeAction {
  key: string; templateId: string; required: boolean; executorId?: string; inputs?: Record<string, InputMapping>;
}
export interface NodeApproval { mode: ApprovalMode; approverIds: string[] }
export interface NotificationRule {
  trigger: NotificationTrigger; type: NotificationRuleType; recipients?: NotificationRecipient[]; userIds?: string[];
  channels?: NotificationChannel[]; callbackCode?: string;
}
export interface GraphNode {
  key: string; type: NodeType; name: string; position: { x: number; y: number };
  approval?: NodeApproval; actions?: NodeAction[]; conditions?: EdgeCondition[]; defaultEdgeKey?: string;
  notifications?: NotificationRule[];
}
export interface GraphEdge { key: string; from: string; to: string }
export interface WorkflowGraph { nodes: GraphNode[]; edges: GraphEdge[]; settings?: { notifications: NotificationRule[] } }

// ---------- 字段定义（3.2） ----------
export interface EnumOption { value: string; label: string }
export interface FieldRules {
  maxLength?: number; min?: string; max?: string; scale?: number; options?: EnumOption[];
  minFiles?: number; maxFiles?: number; mimeTypes?: string[];
}
export interface FormField { key: string; label: string; type: FieldType; required: boolean; rules?: FieldRules }
export interface FormDefinition { fields: FormField[] }

// ---------- 动作模板 config（3.3） ----------
export interface FileUploadConfig { minFiles?: number; maxFiles?: number; mimeTypes?: string[]; instruction?: string }
export interface PageParameter { key: string; label: string }
export interface PageTaskConfig { pageRoute: string; parameters?: PageParameter[]; instruction?: string }
export interface ManualConfirmConfig { instruction?: string; noteRequired?: boolean }
/** DATA_QUERY 预留：内部结构为前端假设，后端只限制顶层四个键。 */
export interface DataQueryConfig {
  queryCode: string; parameters?: { key: string; source: InputSource; value: string }[];
  passRule?: { column: string; operator: ConditionOperator; value: string }; evidenceColumns?: string[];
}
export type ActionConfig = FormDefinition | FileUploadConfig | PageTaskConfig | ManualConfirmConfig | DataQueryConfig;

/** action_snapshot_json：发布时冻结的模板。 */
/** 早期快照没有 templateCode / templateName（为 null）。 */
export interface FrozenTemplate {
  templateId: string; templateVersion: number; templateCode: string | null; templateName: string | null; actionType: ActionType; config: ActionConfig;
}
export interface ActionSnapshot { templates: FrozenTemplate[] }

// ---------- 管理类响应 ----------
export interface ActionTemplate {
  templateId: string; templateCode: string; templateName: string; actionType: ActionType; description: string | null;
  /** 列表接口不含配置（为 null），详情接口才有。 */
  config: ActionConfig | null; status: ConfigStatus; version: number; updatedBy: string | null;
  /** 最后修改人显示名；账号不存在时为空。 */
  updatedByName?: string | null; updateTime: string;
}
export interface Mutation { objectType: string; objectId: string; status: string | null; version: number }
export interface Problem { code: string; nodeKey: string | null; edgeKey: string | null; itemKey: string | null; message: string }
export interface DefinitionSummary {
  definitionId: string; definitionCode: string; definitionName: string; description: string | null; status: ConfigStatus;
  currentVersionNo: number | null; draftVersionNo: number | null; version: number; updateTime: string;
}
export interface VersionBody {
  versionNo: number; status: 'DRAFT' | 'PUBLISHED'; graph: WorkflowGraph; startForm: FormDefinition;
  actionSnapshot: ActionSnapshot | null; publishedBy: string | null; publishedTime: string | null; updateTime: string;
}
export interface DefinitionDetail {
  definition: DefinitionSummary; draft: VersionBody | null; problems: Problem[]; versions: VersionBody[];
  /** 草稿与各版本中出现的人员：账号 ID → 显示名。 */
  people: Record<string, string>;
}
/** 接口 6：引用该模板的已发布流程版本。 */
export interface TemplateReferenceItem {
  definitionId: string; definitionCode: string; definitionName: string; versionNo: number; currentVersion: boolean;
  frozenTemplateVersion: number; sameAsCurrent: boolean; runningInstanceCount: number;
}
export interface TemplateReferences { templateId: string; currentTemplateVersion: number; items: TemplateReferenceItem[] }
export interface DraftResponse { definitionId: string; version: number; draftVersionNo: number; problems: Problem[] }

// ---------- 运行类响应 ----------
export interface LaunchDefinition {
  definition: DefinitionSummary; versionNo: number; graph: WorkflowGraph; startForm: FormDefinition;
  actionSnapshot: ActionSnapshot | null; people: Record<string, string>;
}
export interface InstanceSummary {
  instanceId: string; definitionId: string; title: string; businessKey: string | null; status: InstanceStatus;
  startedBy: string; startedByName: string | null; startTime: string; endTime: string | null;
  /** 列表接口给出；详情里的 instance 可能不带以下字段。 */
  definitionName?: string; versionNo?: number; completedTaskCount?: number; totalTaskCount?: number; currentNodes?: CurrentNode[];
}
export interface Person { accountId: string; displayName: string }
export interface CurrentNode { nodeKey: string; nodeName: string; status: NodeRunStatus; handlers: Person[] }
export interface NodeRun {
  nodeRunId: string; nodeKey: string; nodeType: NodeType; roundNo: number; branchKey: string | null;
  previousNodeRunId: string | null; status: NodeRunStatus; selectedEdgeKey: string | null; activatedTime: string;
  completedTime: string | null; rejectKind: RejectKind | null; rejectTargets: string[] | null;
}
export interface ActionRun {
  actionRunId: string; nodeRunId: string; actionKey: string; actionType: ActionType; required: boolean; assigneeId: string;
  status: ActionRunStatus; output: Record<string, unknown> | null; submittedBy: string | null; submittedTime: string | null;
  /** 发布时冻结的模板名称；早期快照为空。 */
  templateName?: string | null;
}
export interface Verification {
  verificationId: string; actionRunId: string; result: 'PASSED' | 'FAILED'; failureReason: string | null; evidence: unknown;
  verifiedBy: string; verifiedTime: string;
}
export interface Approval {
  approvalId: string; nodeRunId: string; approverId: string; status: ApprovalStatus; comment: string | null;
  rejectTargetNodeKey: string | null; decidedTime: string | null;
}
export interface Transfer {
  transferId: string; targetType: 'ACTION_RUN' | 'APPROVAL'; targetId: string; fromUserId: string; toUserId: string;
  reason: string | null; transferredBy: string; createTime: string;
}
export interface InstanceDetail {
  instance: InstanceSummary; definitionCode: string; definitionName: string; versionNo: number; version: number;
  terminatedBy: string | null; terminationReason: string | null; graph: WorkflowGraph; startForm: FormDefinition;
  startValues: Record<string, unknown>; actionSnapshot: ActionSnapshot | null; nodeRuns: NodeRun[]; actionRuns: ActionRun[];
  verifications: Verification[]; approvals: Approval[]; transfers: Transfer[];
  /** 有审批的任务节点当前生效的审批人（发起分配并保留转交）：节点 key → 账号 ID。 */
  nodeApprovers: Record<string, string[]>; people: Record<string, string>;
  /** 启动值与动作输出引用的文件元数据（接口 17），按 fileAddress 匹配；旧响应没有时为空。 */
  files: WorkflowFile[];
}
/** 文件元数据；只有 AVAILABLE 可下载与预览。 */
export interface WorkflowFile {
  fileId: string;
  /** 业务数据里保存的文件地址 /files/v1/{fileId}。 */
  fileAddress: string; displayName: string | null; mimeType: string | null; sizeBytes: string | null;
  scanStatus: 'PENDING_SCAN' | 'SCANNING' | 'AVAILABLE' | 'QUARANTINED' | 'REJECTED' | null; uploaderId: string | null;
}
export interface Activity {
  time: string; activityType: ActivityType; nodeKey: string | null; roundNo: number | null; status: string | null;
  actorId: string | null; actorName: string | null; detail: string | null; referenceId: string | null;
}
export interface Task {
  taskType: TaskType; taskId: string; instanceId: string; instanceTitle: string; nodeKey: string; roundNo: number;
  actionKey: string | null; waitingSince: string; definitionName?: string | null; nodeName?: string | null;
}
/** 接口 26：已处理。result 为动作运行状态或审批决定。 */
export interface HandledTask {
  taskType: TaskType; taskId: string; instanceId: string; instanceTitle: string; instanceStatus: InstanceStatus; definitionName: string | null;
  nodeKey: string; nodeName: string | null; roundNo: number; actionKey: string | null; result: string; handledTime: string;
}
export interface SubmissionResult {
  actionRunId: string; status: ActionRunStatus; verificationId: string | null; failureReason: string | null; instanceStatus: InstanceStatus;
}
/** 接口 28：显示名为登录名，没有登录名时为脱敏邮箱（Q11）。 */
export interface AssigneeCandidate { accountId: string; loginName: string | null; emailMasked: string | null; displayName: string }

// ---------- 请求 ----------
export interface CreateTemplateRequest {
  templateCode: string; templateName: string; actionType: ActionType; description?: string; config: ActionConfig;
}
export interface UpdateTemplateRequest { templateName: string; description?: string; config: ActionConfig; expectedVersion: number }
export interface ChangeStatusRequest { status: ConfigStatus; expectedVersion: number }
export interface CreateDefinitionRequest { definitionCode: string; definitionName: string; description?: string }
export interface SaveDraftRequest {
  definitionName: string; description?: string; graph: WorkflowGraph; startForm: FormDefinition; expectedVersion: number;
}
export interface PublishRequest { expectedVersion: number }
export interface StartInstanceRequest {
  definitionId: string; versionNo: number; title: string; businessKey?: string; startValues: Record<string, unknown>;
  approvers?: { nodeKey: string; approverIds: string[] }[]; executors?: { actionKey: string; executorId: string }[];
}
export interface TerminateRequest { reason: string }
export interface SubmitActionRequest { output: Record<string, unknown> }
export interface DecisionRequest { decision: ApprovalDecision; comment?: string; rejectTargetNodeKey?: string }
export interface TransferRequest { toUserId: string; reason?: string }
