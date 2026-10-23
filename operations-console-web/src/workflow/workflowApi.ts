import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import type {
  ActionConfig, ActionSnapshot, ActionTemplate, Activity, AssigneeCandidate, DefinitionDetail, DefinitionSummary, DraftResponse,
  FormDefinition, HandledTask, InstanceDetail, InstanceSummary, LaunchDefinition, Mutation, Problem, SubmissionResult, Task, TemplateReferences, VersionBody,
  WorkflowGraph,
} from './workflowTypes.ts';
import { decodeFiles } from './fileModel.ts';

/** BFF 同路径转发（technique 第 7 节）；session 统一加 /api/operations 前缀。 */
export const adminBase = '/workflow-admin/v1';
export const runtimeBase = '/workflow/v1';
/** 只取 total 的计数请求用的每页条数：Platform 列表只接受 10 / 20 / 50 / 100（size=1 返回 400）。 */
export const COUNT_PAGE_SIZE = 10;
export const workflowPaths = {
  templates: `${adminBase}/action-templates`,
  template: (id: string) => `${adminBase}/action-templates/${encodeURIComponent(id)}`,
  templateReferences: (id: string) => `${adminBase}/action-templates/${encodeURIComponent(id)}/references`,
  templateStatus: (id: string) => `${adminBase}/action-templates/${encodeURIComponent(id)}/status`,
  definitions: `${adminBase}/definitions`,
  definition: (id: string) => `${adminBase}/definitions/${encodeURIComponent(id)}`,
  draft: (id: string) => `${adminBase}/definitions/${encodeURIComponent(id)}/draft`,
  publication: (id: string) => `${adminBase}/definitions/${encodeURIComponent(id)}/publication`,
  definitionStatus: (id: string) => `${adminBase}/definitions/${encodeURIComponent(id)}/status`,
  version: (id: string, versionNo: number) => `${adminBase}/definitions/${encodeURIComponent(id)}/versions/${versionNo}`,
  launchable: `${runtimeBase}/launchable-definitions`,
  launchableOne: (id: string) => `${runtimeBase}/launchable-definitions/${encodeURIComponent(id)}`,
  instances: `${runtimeBase}/instances`,
  instance: (id: string) => `${runtimeBase}/instances/${encodeURIComponent(id)}`,
  activities: (id: string) => `${runtimeBase}/instances/${encodeURIComponent(id)}/activities`,
  termination: (id: string) => `${runtimeBase}/instances/${encodeURIComponent(id)}/termination`,
  /** 完成动作（接口 #19，原提交接口，路径与请求体不变）。 */
  submission: (id: string) => `${runtimeBase}/action-runs/${encodeURIComponent(id)}/submission`,
  actionTransfer: (id: string) => `${runtimeBase}/action-runs/${encodeURIComponent(id)}/transfer`,
  decision: (id: string) => `${runtimeBase}/approvals/${encodeURIComponent(id)}/decision`,
  approvalTransfer: (id: string) => `${runtimeBase}/approvals/${encodeURIComponent(id)}/transfer`,
  myTasks: `${runtimeBase}/my-tasks`,
  handledTasks: `${runtimeBase}/my-tasks/handled`,
  candidates: `${runtimeBase}/assignee-candidates`,
  callbackRetry: (id: string) => `${runtimeBase}/callbacks/${encodeURIComponent(id)}/retry`,
  /** 文件接口（26—28）：开始上传、完成上传、按实例签发下载授权。 */
  uploads: `${runtimeBase}/attachments/uploads`,
  uploadCompletion: (uploadId: string) => `${runtimeBase}/attachments/uploads/${encodeURIComponent(uploadId)}/completion`,
  /** 下载授权；disposition 可选（INLINE 浏览器内打开），缺省按附件。 */
  download: (instanceId: string, fileId: string, disposition?: 'INLINE' | 'ATTACHMENT') =>
    `${runtimeBase}/instances/${encodeURIComponent(instanceId)}/files/${encodeURIComponent(fileId)}/download${disposition ? `?disposition=${disposition}` : ''}`,
} as const;

function check(valid: unknown): asserts valid {
  if (!valid) throw new ApiError(502, 'INVALID_RESPONSE');
}
const object = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
const list = (value: unknown): unknown[] => Array.isArray(value) ? value : [];
const text = (value: unknown): string | null => typeof value === 'string' ? value : null;

/** graph_json 防御性解析：缺失字段按空处理，非法节点丢弃（编辑器仍可显示并修复）。 */
export function parseGraph(value: unknown): WorkflowGraph {
  const root = object(value);
  const nodes = list(root.nodes).map(object).filter(node => typeof node.key === 'string' && typeof node.type === 'string')
    .map(node => {
      const position = object(node.position);
      return {
        ...(node as object),
        key: node.key as string, type: node.type as WorkflowGraph['nodes'][number]['type'], name: text(node.name) ?? '',
        position: { x: Number(position.x) || 0, y: Number(position.y) || 0 },
      } as WorkflowGraph['nodes'][number];
    });
  const edges = list(root.edges).map(object)
    .filter(edge => typeof edge.key === 'string' && typeof edge.from === 'string' && typeof edge.to === 'string')
    .map(edge => ({ key: edge.key as string, from: edge.from as string, to: edge.to as string }));
  const settings = object(root.settings);
  return { nodes, edges, ...(Array.isArray(settings.notifications) ? { settings: { notifications: settings.notifications as never } } : {}) };
}

export function parseForm(value: unknown): FormDefinition {
  return { fields: list(object(value).fields).map(object).filter(field => typeof field.key === 'string' && typeof field.type === 'string') as never };
}

export function parseSnapshot(value: unknown): ActionSnapshot | null {
  if (value === null || value === undefined) return null;
  return { templates: list(object(value).templates).map(object).map(item => ({
    templateId: String(item.templateId ?? ''), templateVersion: Number(item.templateVersion) || 0,
    templateCode: text(item.templateCode), templateName: text(item.templateName),
    actionType: item.actionType as never, config: object(item.config) as ActionConfig })) };
}

export function decodePage<T>(decodeItem: (value: unknown) => T) {
  return (value: unknown): PagingResponseBody<T> => {
    const page = object(value) as unknown as PagingResponseBody<unknown>;
    check(Array.isArray(page.items));
    return { ...page, items: page.items.map(decodeItem) };
  };
}

export function decodeTemplate(value: unknown): ActionTemplate {
  const item = object(value);
  check(typeof item.templateId === 'string' && typeof item.templateCode === 'string' && typeof item.actionType === 'string');
  return { ...(item as unknown as ActionTemplate), description: text(item.description),
    config: item.config === null || item.config === undefined ? null : object(item.config) as ActionConfig,
    version: Number(item.version),
    referencedDefinitionCount: Number.isInteger(item.referencedDefinitionCount) ? item.referencedDefinitionCount as number : null };
}

export function decodeMutation(value: unknown): Mutation {
  const item = object(value);
  check(typeof item.objectId === 'string' && Number.isInteger(item.version));
  return item as unknown as Mutation;
}

const decodeProblems = (value: unknown): Problem[] => list(value).map(object).map(item => ({
  code: text(item.code) ?? '', nodeKey: text(item.nodeKey), edgeKey: text(item.edgeKey), itemKey: text(item.itemKey),
  message: text(item.message) ?? '' }));

export function decodeDefinitionSummary(value: unknown): DefinitionSummary {
  const item = object(value);
  check(typeof item.definitionId === 'string' && typeof item.definitionCode === 'string');
  return item as unknown as DefinitionSummary;
}

export function decodeVersion(value: unknown): VersionBody {
  const item = object(value);
  check(Number.isInteger(item.versionNo));
  return { ...(item as unknown as VersionBody), graph: parseGraph(item.graph), startForm: parseForm(item.startForm),
    actionSnapshot: parseSnapshot(item.actionSnapshot) };
}

export function decodeDefinitionDetail(value: unknown): DefinitionDetail {
  const item = object(value);
  return { definition: decodeDefinitionSummary(item.definition), draft: item.draft ? decodeVersion(item.draft) : null,
    problems: decodeProblems(item.problems), versions: list(item.versions).map(decodeVersion), people: object(item.people) as Record<string, string> };
}

export function decodeDraft(value: unknown): DraftResponse {
  const item = object(value);
  check(Number.isInteger(item.version));
  return { definitionId: String(item.definitionId), version: item.version as number, draftVersionNo: Number(item.draftVersionNo),
    problems: decodeProblems(item.problems) };
}

export function decodeLaunch(value: unknown): LaunchDefinition {
  const item = object(value);
  check(Number.isInteger(item.versionNo));
  return { definition: decodeDefinitionSummary(item.definition), versionNo: item.versionNo as number, graph: parseGraph(item.graph),
    startForm: parseForm(item.startForm), actionSnapshot: parseSnapshot(item.actionSnapshot), people: object(item.people) as Record<string, string> };
}

export function decodeInstanceSummary(value: unknown): InstanceSummary {
  const item = object(value);
  check(typeof item.instanceId === 'string' && typeof item.status === 'string');
  return item as unknown as InstanceSummary;
}

export function decodeInstanceDetail(value: unknown): InstanceDetail {
  const item = object(value);
  return { ...(item as unknown as InstanceDetail), instance: decodeInstanceSummary(item.instance), graph: parseGraph(item.graph),
    startForm: parseForm(item.startForm), startValues: object(item.startValues), actionSnapshot: parseSnapshot(item.actionSnapshot),
    nodeRuns: list(item.nodeRuns) as never, actionRuns: list(item.actionRuns).map(object).map(run => ({ ...run,
      output: run.output === null || run.output === undefined ? null : object(run.output) })) as never,
    verifications: list(item.verifications) as never, approvals: list(item.approvals) as never, transfers: list(item.transfers) as never,
    nodeApprovers: Object.fromEntries(Object.entries(object(item.nodeApprovers)).map(([key, ids]) => [key, list(ids).filter((id): id is string => typeof id === 'string')])),
    people: object(item.people) as Record<string, string>, files: decodeFiles(item.files) };
}

export function decodeActivities(value: unknown): Activity[] {
  const items = object(value).items;
  check(Array.isArray(items));
  return items as Activity[];
}

export function decodeTask(value: unknown): Task {
  const item = object(value);
  check(typeof item.taskId === 'string' && (item.taskType === 'ACTION' || item.taskType === 'APPROVAL'));
  return item as unknown as Task;
}

export function decodeSubmission(value: unknown): SubmissionResult {
  const item = object(value);
  check(typeof item.actionRunId === 'string' && typeof item.status === 'string' && typeof item.instanceStatus === 'string');
  return { actionRunId: item.actionRunId, status: item.status as SubmissionResult['status'],
    instanceStatus: item.instanceStatus as SubmissionResult['instanceStatus'] };
}

export function decodeCandidates(value: unknown): AssigneeCandidate[] {
  return list(object(value).items).map(object).filter(item => typeof item.accountId === 'string')
    .map(item => ({ accountId: item.accountId as string, loginName: text(item.loginName), emailMasked: text(item.emailMasked),
      displayName: text(item.displayName) ?? text(item.loginName) ?? text(item.emailMasked) ?? (item.accountId as string) }));
}

export function decodeHandledTask(value: unknown): HandledTask {
  const item = object(value);
  check(typeof item.taskId === 'string' && typeof item.result === 'string');
  return item as unknown as HandledTask;
}

export function decodeReferences(value: unknown): TemplateReferences {
  const item = object(value);
  check(Array.isArray(item.items));
  return { templateId: String(item.templateId ?? ''), currentTemplateVersion: Number(item.currentTemplateVersion) || 0,
    items: (item.items as unknown[]).map(object).map(row => ({ definitionId: String(row.definitionId), definitionCode: text(row.definitionCode) ?? '',
      definitionName: text(row.definitionName) ?? '', versionNo: Number(row.versionNo), currentVersion: row.currentVersion === true,
      frozenTemplateVersion: Number(row.frozenTemplateVersion), sameAsCurrent: row.sameAsCurrent === true, runningInstanceCount: Number(row.runningInstanceCount) || 0 })) };
}
