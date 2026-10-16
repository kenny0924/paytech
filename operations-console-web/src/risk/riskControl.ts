import { session } from '../api/session.ts';
import type { RequestClient } from '../api/session.ts';
import { requestAllPages, requestPage } from '../api/paging.ts';
import type { PagingRequestBody } from '../api/paging.ts';
import type { ConsoleResource, PagingResponseBody } from '../api/types.ts';
import { decodePage, exact, id, invariant } from './riskDictionary.ts';
import { commandFailureSettlement, randomCommand } from './riskCommands.ts';
import type { PendingCommand } from './riskCommands.ts';

/** 风控名单；用途只用于展示，命中后的处置由规则决定。 */
export interface RiskList { listId: string; listCode: string; listName: string; subjectType: string; purpose: 'BLOCK' | 'ALLOW'; active: boolean }
/** 名单成员；只有 Risk 返回的脱敏主体键，没有原值。 */
export interface RiskListEntry { entryId: string; listId: string; subjectType: string; maskedSubjectKey: string; sourceType: string; status: string; effectiveFrom: string; effectiveTo: string | null; reason: string; createdBy: string | null; deactivatedBy: string | null; deactivatedReason: string | null; createTime: string }
export interface CreateRiskList { listCode: string; listName: string; subjectType: string; purpose: 'BLOCK' | 'ALLOW' }
export interface AddRiskListEntry { subjectValue: string; effectiveFrom: string | null; effectiveTo: string | null; reason: string }
export interface ExecutionReleaseItem { releaseId: string; releaseNo: string; pointCode: string; environment: string; status: string; active: boolean; flowId: string; flowCode: string; flowVersionId: string; flowVersionNo: number; contentHash: string; createdBy: string; createTime: string; activatedBy: string | null; activatedTime: string | null; supersededTime: string | null }
export interface StaticCheck { checkCode: string; checkStatus: string; errorCode: string | null; errorSummary: string | null }
export interface ShadowRun { shadowRunId: string; baselineReleaseId: string; status: string; sampleTarget: number; sampleCount: number; allowToReviewCount: number | null; allowToDenyCount: number | null; denyToAllowCount: number | null; actionChangeCount: number | null; featureMissingCount: number | null; failedCount: number | null; timedOutCount: number | null; p95Millis: number | null; p99Millis: number | null; checkStatus: string | null }
export interface ExecutionRelease { releaseId: string; releaseNo: string; pointCode: string; flowVersionId: string; environment: string; status: string; version: number; contentHash: string; activatedTime: string | null; supersededTime: string | null; checks: StaticCheck[]; shadowRun: ShadowRun | null }

export const listPurposeLabels: Record<RiskList['purpose'], string> = { BLOCK: '拦截', ALLOW: '放行' };
export const entryStatusLabels: Record<string, string> = { ACTIVE: '生效中', EXPIRED: '已过期', RELEASED: '已失效', SUPERSEDED: '已替代' };
export const releaseStatusLabels: Record<string, string> = { DRAFT: '草稿', VALIDATING: '校验中', SHADOWING: '待激活', REJECTED: '校验未通过', ACTIVE: '生效中', SUPERSEDED: '已替代' };
export const checkLabels: Record<string, string> = { REFERENCE_INTEGRITY: '引用完整性', AST_SHAPE: '条件结构', AST_TYPES: '条件类型', FEATURE_DEPENDENCY: '特征依赖', HANDLER_HASH: '处理器摘要', ACTION_PARAMETERS: '动作参数', TERMINAL_ACTION: '终态动作（含认证要求输出）', SYSTEM_OBLIGATION: '系统义务' };
/** 支持执行发布的风控点；Risk 目录未下发运行期能力标记，由此常量控制入口，未列出的点由 Risk 返回 400。支付排第一，作为默认点。 */
export const EXECUTION_POINTS = ['PAYMENT_ATTEMPT_RISK', 'CHECKOUT_PREPARE'];
export const SHADOW_SAMPLE_DEFAULT = 200;
export const SHADOW_SAMPLE_MAX = 2000;

/** 未知状态原样显示，避免 Risk 新增或短暂状态让页面失败。 */
export function statusLabel(labels: Record<string, string>, status: string): string { return labels[status] ?? status; }
export function hasResource(resources: readonly ConsoleResource[], code: string): boolean { return resources.some(r => r.resourceCode === code); }

function text(v: unknown, max = 512): string { invariant(typeof v === 'string' && v.length > 0 && [...v].length <= max); return v; }
function code(v: unknown): string { const s = text(v, 64); invariant(/^[A-Z][A-Z0-9_]{0,63}$/.test(s)); return s; }
function nil<T>(v: unknown, decode: (v: unknown) => T): T | null { return v === null ? null : decode(v); }
function count(v: unknown): number { invariant(typeof v === 'number' && Number.isSafeInteger(v) && v >= 0); return v; }
function time(v: unknown): string { const s = text(v, 40); invariant(Number.isFinite(Date.parse(s))); return s; }

function purpose(v: unknown): RiskList['purpose'] { invariant(v === 'BLOCK' || v === 'ALLOW'); return v; }
export function decodeRiskList(v: unknown): RiskList {
  const r = exact(v, ['listId', 'listCode', 'listName', 'subjectType', 'purpose', 'active']);
  invariant(typeof r.active === 'boolean');
  return { listId: id(r.listId), listCode: code(r.listCode), listName: text(r.listName, 255), subjectType: code(r.subjectType), purpose: purpose(r.purpose), active: r.active };
}
export function decodeRiskListEntry(v: unknown): RiskListEntry {
  const r = exact(v, ['entryId', 'listId', 'subjectType', 'maskedSubjectKey', 'sourceType', 'status', 'effectiveFrom', 'effectiveTo', 'reason', 'createdBy', 'deactivatedBy', 'deactivatedReason', 'createTime']);
  return { entryId: id(r.entryId), listId: id(r.listId), subjectType: code(r.subjectType), maskedSubjectKey: text(r.maskedSubjectKey, 256), sourceType: code(r.sourceType), status: code(r.status), effectiveFrom: time(r.effectiveFrom), effectiveTo: nil(r.effectiveTo, time), reason: text(r.reason, 500), createdBy: nil(r.createdBy, id), deactivatedBy: nil(r.deactivatedBy, id), deactivatedReason: nil(r.deactivatedReason, v => text(v, 500)), createTime: time(r.createTime) };
}
export function decodeExecutionItem(v: unknown): ExecutionReleaseItem {
  const r = exact(v, ['releaseId', 'releaseNo', 'pointCode', 'environment', 'status', 'active', 'flowId', 'flowCode', 'flowVersionId', 'flowVersionNo', 'contentHash', 'createdBy', 'createTime', 'activatedBy', 'activatedTime', 'supersededTime']);
  invariant(typeof r.active === 'boolean');
  return { releaseId: id(r.releaseId), releaseNo: text(r.releaseNo, 32), pointCode: code(r.pointCode), environment: code(r.environment), status: code(r.status), active: r.active, flowId: id(r.flowId), flowCode: code(r.flowCode), flowVersionId: id(r.flowVersionId), flowVersionNo: count(r.flowVersionNo), contentHash: text(r.contentHash, 128), createdBy: id(r.createdBy), createTime: time(r.createTime), activatedBy: nil(r.activatedBy, id), activatedTime: nil(r.activatedTime, time), supersededTime: nil(r.supersededTime, time) };
}
function decodeCheck(v: unknown): StaticCheck {
  const r = exact(v, ['checkCode', 'checkStatus', 'errorCode', 'errorSummary']);
  return { checkCode: code(r.checkCode), checkStatus: code(r.checkStatus), errorCode: nil(r.errorCode, code), errorSummary: nil(r.errorSummary, v => text(v, 1000)) };
}
function decodeShadowRun(v: unknown): ShadowRun {
  const r = exact(v, ['shadowRunId', 'baselineReleaseId', 'status', 'sampleTarget', 'sampleCount', 'allowToReviewCount', 'allowToDenyCount', 'denyToAllowCount', 'actionChangeCount', 'featureMissingCount', 'failedCount', 'timedOutCount', 'p95Millis', 'p99Millis', 'checkStatus']);
  return { shadowRunId: id(r.shadowRunId), baselineReleaseId: id(r.baselineReleaseId), status: code(r.status), sampleTarget: count(r.sampleTarget), sampleCount: count(r.sampleCount),
    allowToReviewCount: nil(r.allowToReviewCount, count), allowToDenyCount: nil(r.allowToDenyCount, count), denyToAllowCount: nil(r.denyToAllowCount, count), actionChangeCount: nil(r.actionChangeCount, count),
    featureMissingCount: nil(r.featureMissingCount, count), failedCount: nil(r.failedCount, count), timedOutCount: nil(r.timedOutCount, count), p95Millis: nil(r.p95Millis, count), p99Millis: nil(r.p99Millis, count), checkStatus: nil(r.checkStatus, code) };
}
export function decodeExecutionRelease(v: unknown): ExecutionRelease {
  const r = exact(v, ['releaseId', 'releaseNo', 'pointCode', 'flowVersionId', 'environment', 'status', 'version', 'contentHash', 'activatedTime', 'supersededTime', 'checks', 'shadowRun']);
  invariant(Array.isArray(r.checks) && r.checks.length <= 32);
  return { releaseId: id(r.releaseId), releaseNo: text(r.releaseNo, 32), pointCode: code(r.pointCode), flowVersionId: id(r.flowVersionId), environment: code(r.environment), status: code(r.status), version: count(r.version), contentHash: text(r.contentHash, 128),
    activatedTime: nil(r.activatedTime, time), supersededTime: nil(r.supersededTime, time), checks: r.checks.map(decodeCheck), shadowRun: nil(r.shadowRun, decodeShadowRun) };
}

/** 影子运行仍在进行（页面轮询直到离开该状态）。 */
export function shadowRunning(release: ExecutionRelease | null): boolean { return release?.shadowRun?.status === 'RUNNING' || release?.shadowRun?.status === 'CREATED'; }
/** 已完成且技术门槛通过的影子批次。 */
export function shadowPassed(release: ExecutionRelease): boolean { const run = release.shadowRun; return !!run && run.status === 'COMPLETED' && run.checkStatus === 'PASSED'; }
/**
 * 页面按状态提供的下一步操作；最终是否允许仍以 Risk 为准（不满足时返回 400 并展示原因）。
 * @param hasActive 该风控点是否已有生效发布（不含本发布）
 */
export function releaseActions(release: ExecutionRelease, hasActive: boolean): { validate: boolean; shadow: boolean; activate: boolean } {
  const ready = release.status === 'SHADOWING';
  return { validate: release.status === 'DRAFT', shadow: ready && hasActive && !shadowRunning(release), activate: ready && !shadowRunning(release) && (!hasActive || shadowPassed(release)) };
}
/** 激活确认必须勾选：首次发布（无影子对比）或“拒绝→放行”“认证要求变化”任一大于 0。 */
export function activationNeedsAcknowledgement(release: ExecutionRelease, hasActive: boolean): boolean {
  if (!hasActive) return true;
  const run = release.shadowRun;
  return !run || (run.denyToAllowCount ?? 0) > 0 || (run.actionChangeCount ?? 0) > 0;
}
/** 主体值查询只走请求参数，不写入页面地址；空白不查询。 */
export function entryFilters(subjectValue: string, status: string): Record<string, string> {
  const filters: Record<string, string> = {};
  if (subjectValue.trim()) filters.subjectValue = subjectValue.trim();
  if (status) filters.status = status;
  return filters;
}
/** 后端时间转为页面输入的反向：datetime-local 字符串转 UTC ISO，空为 null。 */
export function localToUtc(value: string): string | null { if (!value) return null; const t = new Date(value); invariant(!Number.isNaN(t.getTime())); return t.toISOString(); }

/**
 * 写命令发送器：JSON 内容类型（无请求体也声明）+ Gateway 请求幂等键。
 * 结果未知（网络中断、5xx）后以相同路径和请求体重试沿用原键；成功或确定的 4xx 后下一次命令换新键。
 */
function commandSender(client: RequestClient) {
  let pending: PendingCommand | null = null;
  return async <T>(path: string, body: unknown, decode: (v: unknown) => T): Promise<T> => {
    const payload = body === undefined ? '' : JSON.stringify(body);
    const command = randomCommand(path, payload, pending);
    pending = command;
    try {
      const result = await client.request(path, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key },
        ...(body === undefined ? {} : { body: payload }) }, decode);
      pending = null;
      return result;
    } catch (error) {
      if (commandFailureSettlement(error) === 'DETERMINISTIC_HTTP_FAILURE') pending = null;
      throw error;
    }
  };
}
const enc = encodeURIComponent;
export function createRiskListApi(client: RequestClient = session) {
  const root = '/risk-lists/v1/lists';
  const send = commandSender(client);
  return {
    list: (paging: PagingRequestBody, signal?: AbortSignal) => requestPage(root, paging, signal, client, {}, v => decodePage(v, decodeRiskList)),
    all: (signal: AbortSignal) => requestAllPages(root, signal, client, {}, v => decodePage(v, decodeRiskList)),
    create: (body: CreateRiskList) => send(root, body, v => { const r = decodeRiskList(v); invariant(r.listCode === body.listCode); return r; }),
    entries: (listCode: string, paging: PagingRequestBody, filters: Record<string, string>, signal?: AbortSignal): Promise<PagingResponseBody<RiskListEntry>> =>
      requestPage(`${root}/${enc(listCode)}/entries`, paging, signal, client, filters, v => decodePage(v, decodeRiskListEntry)),
    add: (listCode: string, body: AddRiskListEntry) => send(`${root}/${enc(listCode)}/entries`, body, decodeRiskListEntry),
    deactivate: (listCode: string, entryId: string, reason: string) => send(`${root}/${enc(listCode)}/entries/${enc(entryId)}/deactivate`, { reason }, v => { const r = decodeRiskListEntry(v); invariant(r.entryId === entryId); return r; }),
  };
}
export function createExecutionReleaseApi(client: RequestClient = session) {
  const root = '/decision-releases/v1/executions';
  const send = commandSender(client);
  const same = (releaseId: string) => (v: unknown) => { const r = decodeExecutionRelease(v); invariant(r.releaseId === releaseId); return r; };
  return {
    list: (pointCode: string, paging: PagingRequestBody, status: string, signal?: AbortSignal) => requestPage(root, paging, signal, client, { pointCode, ...(status ? { status } : {}) },
      v => { const page = decodePage(v, decodeExecutionItem); for (const item of page.items) invariant(item.pointCode === pointCode && (!status || item.status === status)); return page; }),
    active: async (pointCode: string, signal?: AbortSignal) => (await requestPage(root, { page: 1, size: 10 }, signal, client, { pointCode, status: 'ACTIVE' }, v => decodePage(v, decodeExecutionItem))).items[0] ?? null,
    create: (pointCode: string, flowVersionId: string) => send(root, { pointCode, flowVersionId }, v => { const r = decodeExecutionRelease(v); invariant(r.pointCode === pointCode && r.flowVersionId === flowVersionId); return r; }),
    detail: (releaseId: string, signal?: AbortSignal) => client.request(`${root}/${enc(releaseId)}`, { signal }, same(releaseId)),
    validate: (releaseId: string) => send(`${root}/${enc(releaseId)}/validate`, undefined, same(releaseId)),
    shadow: (releaseId: string, sampleTarget: number) => send(`${root}/${enc(releaseId)}/shadow-runs`, { sampleTarget }, same(releaseId)),
    activate: (releaseId: string) => send(`${root}/${enc(releaseId)}/activate`, undefined, same(releaseId)),
  };
}
export const riskListApi = createRiskListApi();
export const executionReleaseApi = createExecutionReleaseApi();
