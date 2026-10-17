import { ApiError, session } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { resolveTimeRange } from '../components/timeRange.ts';
import type { TimeRangeValue } from '../components/timeRange.ts';
import { configurationCommand } from '../configuration/configurationModel.ts';
import type { ConfigurationCommand } from '../configuration/configurationModel.ts';

/** Webhook 管理接口前缀（BFF）。 */
export const webhookAdminPath = '/webhook-admin/v1';
/** 新建、修改、启停、轮换、测试与 Replay 的按钮资源。 */
export const manageWebhooks = 'OPERATIONS_WEBHOOK_MANAGE';
/** 每个 Client 的端点上限（含已停用），与 Platform 一致。 */
export const endpointLimit = 5;
/** 投递记录单次查询的最长跨度（天），与 Platform 一致。 */
export const maxDeliveryDays = 31;
/** 地址与原因长度上限，与 Platform 一致。 */
export const maxUrlLength = 1000;
export const maxReasonLength = 500;
/** 防抖等待时间（毫秒）：Client 搜索输入停顿后才请求。 */
export const clientSearchDelay = 300;
/** 签名密钥格式：whsec_ 加 43 位 Base64URL。 */
export const secretPattern = /^whsec_[A-Za-z0-9_-]{43}$/;
/** V1 可订阅事件类型，按页面展示顺序。 */
export const eventTypes = [
  'order.paid', 'order.cancelled', 'order.expired', 'order.funds_available',
  'refund.created', 'refund.succeeded', 'refund.failed', 'refund.cancelled',
] as const;

export type EventType = typeof eventTypes[number];
export type EndpointStatus = 'ACTIVE' | 'DISABLED';
export type DeliveryStatus = 'CREATED' | 'PROCESSING' | 'RETRY_WAIT' | 'SUCCEEDED' | 'DEAD' | 'CANCELLED';
export type AttemptStatus = 'PROCESSING' | 'SUCCEEDED' | 'FAILED';
export type FailureCode = 'HTTP_STATUS' | 'TIMEOUT' | 'CONNECT_FAILED' | 'TLS_FAILED' | 'DNS_FAILED' | 'BLOCKED_ADDRESS' | 'INTERRUPTED';
export type OwnerStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED';
export type SecurityStatus = 'ACTIVE' | 'SUSPENDED';
export type WebhookTab = 'endpoints' | 'deliveries';

export interface WebhookClient { clientId: string; clientName: string; ownerStatus: OwnerStatus; securityStatus: SecurityStatus; endpointCount: number }
export interface SigningSecret { rotationNo: number; createTime: string; previousValidUntil: string | null }
export interface Endpoint {
  endpointId: string; url: string; eventTypes: EventType[]; status: EndpointStatus; signingSecret: SigningSecret;
  version: number; createTime: string; updateTime: string;
}
export interface EndpointCreated { endpointId: string; version: number; signingSecret: string }
export interface EndpointMutation { endpointId: string; version: number }
export interface SecretRotation { rotationNo: number; signingSecret: string; previousValidUntil: string | null }
export interface TestResult { result: 'SUCCEEDED' | 'FAILED'; httpStatus: number | null; failureCode: FailureCode | null; durationMs: number | null }
export interface DeliverySummary {
  deliveryId: string; eventId: string; eventType: EventType; endpointId: string; status: DeliveryStatus; attemptCount: number;
  lastAttemptTime: string | null; replayOfDeliveryId: string | null; createTime: string;
}
export interface DeliveryEvent { eventId: string; eventType: EventType; schemaVersion: number; sourceSystem: string; envelope: Record<string, unknown> }
export interface DeliveryAttempt {
  attemptNo: number; requestUrl: string; rotationNo: number; graceRotationNo: number | null; status: AttemptStatus;
  httpStatus: number | null; failureCode: FailureCode | null; durationMs: number | null; startedTime: string; finishedTime: string | null;
}
export interface DeliveryDetail extends DeliverySummary { requestedBy: string | null; event: DeliveryEvent; attempts: DeliveryAttempt[] }
export interface ReplayResult { deliveryId: string }

export const endpointStatusLabels: Record<EndpointStatus, string> = { ACTIVE: '启用', DISABLED: '已停用' };
export const deliveryStatusLabels: Record<DeliveryStatus, string> = {
  CREATED: '待投递', PROCESSING: '处理中', RETRY_WAIT: '等待重试', SUCCEEDED: '已送达', DEAD: '已放弃', CANCELLED: '已取消',
};
export const deliveryStatusColors: Record<DeliveryStatus, string> = {
  CREATED: 'blue', PROCESSING: 'blue', RETRY_WAIT: 'geekblue', SUCCEEDED: 'green', DEAD: 'red', CANCELLED: 'default',
};
export const attemptStatusLabels: Record<AttemptStatus, string> = { PROCESSING: '发送中', SUCCEEDED: '成功', FAILED: '失败' };
export const failureCodeLabels: Record<FailureCode, string> = {
  HTTP_STATUS: '对方返回 HTTP {status}', TIMEOUT: '超时', CONNECT_FAILED: '连接失败', TLS_FAILED: 'TLS 握手失败',
  DNS_FAILED: '域名无法解析', BLOCKED_ADDRESS: '地址解析到内网，已拦截', INTERRUPTED: '发送中断（结果未知）',
};
/** 可 Replay 的已结束投递状态；进行中的投递不能 Replay。 */
export const replayableStatuses: readonly DeliveryStatus[] = ['SUCCEEDED', 'DEAD', 'CANCELLED'];

const idPattern = /^[1-9][0-9]{0,18}$/;
const endpointStatuses: readonly string[] = ['ACTIVE', 'DISABLED'];
const ownerStatuses: readonly string[] = ['PENDING', 'ACTIVE', 'SUSPENDED', 'TERMINATED'];
const securityStatuses: readonly string[] = ['ACTIVE', 'SUSPENDED'];
const attemptStatuses: readonly string[] = ['PROCESSING', 'SUCCEEDED', 'FAILED'];
const id = (v: unknown) => typeof v === 'string' && idPattern.test(v);
const text = (v: unknown) => typeof v === 'string' && v.length > 0;
const instant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
const optionalInstant = (v: unknown) => v === null || instant(v);
const count = (v: unknown, min = 0) => Number.isSafeInteger(v) && Number(v) >= min;
const optionalCount = (v: unknown) => v === null || count(v);
const version = (v: unknown) => Number.isInteger(v) && Number(v) >= 1 && Number(v) <= 2147483647;
const eventType = (v: unknown) => (eventTypes as readonly unknown[]).includes(v);
const deliveryStatus = (v: unknown) => typeof v === 'string' && Object.hasOwn(deliveryStatusLabels, v);
const failureCode = (v: unknown) => typeof v === 'string' && Object.hasOwn(failureCodeLabels, v);
const optionalFailure = (v: unknown) => v === null || failureCode(v);
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
/** 解码失败只给通用错误，不回显响应内容（可能含密钥明文）。 */
const require = (valid: boolean) => { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); };

/** Client 搜索结果项。 */
export function decodeClient(value: unknown): WebhookClient {
  const v = value as WebhookClient;
  require(object(v) && id(v.clientId) && typeof v.clientName === 'string' && ownerStatuses.includes(v.ownerStatus)
    && securityStatuses.includes(v.securityStatus) && count(v.endpointCount));
  return v;
}

/** 端点列表项：事件类型必须是 V1 八种之一，未知状态失败关闭。 */
export function decodeEndpoint(value: unknown): Endpoint {
  const v = value as Endpoint;
  require(object(v) && id(v.endpointId) && text(v.url) && Array.isArray(v.eventTypes) && v.eventTypes.length > 0
    && v.eventTypes.every(eventType) && endpointStatuses.includes(v.status) && object(v.signingSecret)
    && count(v.signingSecret.rotationNo, 1) && instant(v.signingSecret.createTime) && optionalInstant(v.signingSecret.previousValidUntil)
    && version(v.version) && instant(v.createTime) && instant(v.updateTime));
  return v;
}

/** 端点列表（不分页，按创建时间升序）。 */
export function decodeEndpoints(value: unknown): Endpoint[] {
  require(Array.isArray(value));
  return (value as unknown[]).map(decodeEndpoint);
}

/** 新建结果：密钥必须符合 whsec_ 格式。 */
export function decodeEndpointCreated(value: unknown): EndpointCreated {
  const v = value as EndpointCreated;
  require(object(v) && id(v.endpointId) && version(v.version) && typeof v.signingSecret === 'string' && secretPattern.test(v.signingSecret));
  return v;
}

/** 修改与启停结果，端点 ID 必须与请求一致。 */
export function decodeEndpointMutation(value: unknown, endpointId: string): EndpointMutation {
  const v = value as EndpointMutation;
  require(object(v) && v.endpointId === endpointId && version(v.version));
  return v;
}

/** 轮换结果：立即作废时宽限截止为空。 */
export function decodeSecretRotation(value: unknown): SecretRotation {
  const v = value as SecretRotation;
  require(object(v) && count(v.rotationNo, 1) && typeof v.signingSecret === 'string' && secretPattern.test(v.signingSecret)
    && optionalInstant(v.previousValidUntil));
  return v;
}

/** 测试事件结果：失败必须带失败代码，HTTP_STATUS 必须带状态码。 */
export function decodeTestResult(value: unknown): TestResult {
  const v = value as TestResult;
  require(object(v) && (v.result === 'SUCCEEDED' || v.result === 'FAILED') && optionalCount(v.httpStatus)
    && optionalFailure(v.failureCode) && optionalCount(v.durationMs)
    && (v.result === 'SUCCEEDED' ? v.failureCode === null : v.failureCode !== null)
    && (v.failureCode !== 'HTTP_STATUS' || v.httpStatus !== null));
  return v;
}

/** 投递列表项。 */
export function decodeDeliverySummary(value: unknown): DeliverySummary {
  const v = value as DeliverySummary;
  require(object(v) && id(v.deliveryId) && text(v.eventId) && eventType(v.eventType) && id(v.endpointId) && deliveryStatus(v.status)
    && count(v.attemptCount) && optionalInstant(v.lastAttemptTime) && (v.replayOfDeliveryId === null || id(v.replayOfDeliveryId))
    && instant(v.createTime));
  return v;
}

function decodeAttempt(value: unknown): DeliveryAttempt {
  const v = value as DeliveryAttempt;
  require(object(v) && count(v.attemptNo, 1) && text(v.requestUrl) && count(v.rotationNo, 1)
    && (v.graceRotationNo === null || count(v.graceRotationNo, 1)) && attemptStatuses.includes(v.status)
    && optionalCount(v.httpStatus) && optionalFailure(v.failureCode) && optionalCount(v.durationMs)
    && instant(v.startedTime) && optionalInstant(v.finishedTime));
  return v;
}

/** 投递详情，投递 ID 必须与请求一致，事件与 Envelope 必须完整。 */
export function decodeDeliveryDetail(value: unknown, deliveryId: string): DeliveryDetail {
  const v = value as DeliveryDetail;
  decodeDeliverySummary(v);
  require(v.deliveryId === deliveryId && (v.requestedBy === null || text(v.requestedBy)) && object(v.event)
    && v.event.eventId === v.eventId && v.event.eventType === v.eventType && count(v.event.schemaVersion, 1)
    && text(v.event.sourceSystem) && object(v.event.envelope) && Array.isArray(v.attempts));
  return { ...v, attempts: v.attempts.map(decodeAttempt) };
}

/** Replay 结果：新投递 ID。 */
export function decodeReplay(value: unknown): ReplayResult {
  const v = value as ReplayResult;
  require(object(v) && id(v.deliveryId));
  return v;
}

/** 分页结果逐项解码。 */
export function pageOf<T>(decode: (value: unknown) => T) {
  return (value: unknown): PagingResponseBody<T> => {
    const v = value as PagingResponseBody<T>;
    require(object(v) && Array.isArray(v.items) && Number.isInteger(v.total));
    return { ...v, items: v.items.map(decode) };
  };
}

/** Client 平台访问状态的中文说明；两项都 ACTIVE 才算正常。 */
export function clientAccessText(client: Pick<WebhookClient, 'ownerStatus' | 'securityStatus'>): string {
  if (client.ownerStatus === 'PENDING') return '平台访问未开通';
  if (client.ownerStatus === 'TERMINATED') return '平台访问已终止';
  if (client.ownerStatus !== 'ACTIVE' || client.securityStatus !== 'ACTIVE') return '平台访问已暂停';
  return '正常';
}

/** 尝试或测试的失败说明；HTTP_STATUS 带对方状态码。 */
export function failureText(code: FailureCode | null, httpStatus: number | null): string {
  if (!code) return httpStatus === null ? '—' : `HTTP ${httpStatus}`;
  return failureCodeLabels[code].replace('{status}', httpStatus === null ? '—' : String(httpStatus));
}

/** 测试事件结果的一行摘要。 */
export function testResultText(result: TestResult): string {
  const duration = result.durationMs === null ? null : `${result.durationMs} ms`;
  if (result.result === 'SUCCEEDED') return ['成功', result.httpStatus === null ? null : `HTTP ${result.httpStatus}`, duration].filter(Boolean).join(' · ');
  return ['失败', failureText(result.failureCode, result.httpStatus), duration ? `耗时 ${duration}` : null].filter(Boolean).join(' · ');
}

export function isReplayable(status: DeliveryStatus): boolean {
  return replayableStatuses.includes(status);
}

/** 尝试记录最新在前。 */
export function newestAttemptsFirst(attempts: readonly DeliveryAttempt[]): DeliveryAttempt[] {
  return [...attempts].sort((a, b) => b.attemptNo - a.attemptNo);
}

/** 列表展示的地址：主机名加路径，解析失败时原样返回。 */
export function hostAndPath(url: string): string {
  try { const parsed = new URL(url); return `${parsed.host}${parsed.pathname === '/' ? '' : parsed.pathname}`; } catch { return url; }
}

/** 从 URL 查询参数恢复所选 Client 与标签页；非法 Client ID 忽略。 */
export function readWebhookQuery(params: URLSearchParams): { clientId: string | null; tab: WebhookTab } {
  const clientId = params.get('clientId');
  return { clientId: clientId && idPattern.test(clientId) ? clientId : null, tab: params.get('tab') === 'deliveries' ? 'deliveries' : 'endpoints' };
}

export interface EndpointFormValues { url: string; eventTypes: string[]; reason: string }

/** 新建与修改共用的请求体；事件按固定顺序去重，新建版本号为 0。 */
export function endpointBody(values: EndpointFormValues, expectedVersion = 0) {
  return { url: values.url.trim(), eventTypes: eventTypes.filter(type => values.eventTypes.includes(type)),
    expectedVersion, reason: values.reason.trim() };
}

export function statusBody(status: EndpointStatus, expectedVersion: number, reason: string) {
  return { status, expectedVersion, reason: reason.trim() };
}

export function rotateBody(immediate: boolean, reason: string) {
  return { immediate, reason: reason.trim() };
}

export function replayBody(reason: string) {
  return { reason: reason.trim() };
}

/** 测试事件请求：BFF 拒绝任何请求体（含 {}），但写方法仍要求声明 JSON，因此只带 Content-Type 不带 Body；每次发送使用新的 Idempotency-Key。 */
export function testEndpointRequest(key: string = crypto.randomUUID()): RequestInit {
  return { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key } };
}

/** Platform 端点地址错误码（WebhookEndpointErrorCode）对应的字段提示，与 Owner 安全说明一致。 */
const urlErrorMessages: Readonly<Record<string, string>> = {
  WEBHOOK_URL_NOT_ALLOWED: '地址格式不正确或协议不允许',
  WEBHOOK_URL_UNRESOLVABLE: '端点主机名无法解析',
  WEBHOOK_URL_NOT_PUBLIC: '端点地址指向内网或保留地址',
};

/** 保存端点时按错误码把地址拒绝原因回填到地址字段；其他错误（含 BUSINESS_CHECK_FAILED）交给通用提示。 */
export function urlErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.status !== 400) return null;
  return urlErrorMessages[error.code] ?? null;
}

const twoDigits = (value: number) => String(value).padStart(2, '0');
function localDateTime(date: Date): string {
  return `${date.getFullYear()}-${twoDigits(date.getMonth() + 1)}-${twoDigits(date.getDate())}T${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}:${twoDigits(date.getSeconds())}`;
}

/** 投递记录默认最近 24 小时：结束取下一整分钟（不含），以免遗漏刚创建的投递。 */
export function defaultDeliveryRange(now = new Date()): TimeRangeValue {
  const end = new Date(now); end.setSeconds(0, 0); end.setMinutes(end.getMinutes() + 1);
  const start = new Date(end.getTime() - 86_400_000);
  return { preset: 'CUSTOM', start: localDateTime(start), end: localDateTime(end) };
}

export interface DeliveryFilterValues { timeRange?: TimeRangeValue; endpointId?: string; eventType?: string; status?: string; eventId?: string }

/** 投递筛选：Client 必填，时间范围转 UTC，跨度不超过 31 天；空的可选条件不发送。 */
export function deliveryFilters(clientId: string, values: DeliveryFilterValues, now = new Date()): Record<string, string> {
  const range = resolveTimeRange(values.timeRange ?? defaultDeliveryRange(now), now);
  if (!range.start || !range.end) throw new Error('请选择完整的开始和结束时间');
  const from = new Date(range.start); const to = new Date(range.end);
  if (!Number.isFinite(from.getTime()) || !Number.isFinite(to.getTime())) throw new Error('请输入有效时间');
  if (from >= to) throw new Error('结束时间必须晚于开始时间');
  if (to.getTime() - from.getTime() > maxDeliveryDays * 86_400_000) throw new Error(`时间跨度不能超过 ${maxDeliveryDays} 天`);
  const filters: Record<string, string> = { clientId, createdFrom: from.toISOString(), createdTo: to.toISOString() };
  for (const key of ['endpointId', 'eventType', 'status', 'eventId'] as const) {
    const value = values[key]?.trim();
    if (value) filters[key] = value;
  }
  return filters;
}

/** 构造写命令：同一目标与正文的重试复用 Idempotency-Key，改了内容才换新 Key。 */
export function webhookCommand(path: string, method: ConfigurationCommand['method'], body: unknown,
  previous?: ConfigurationCommand): ConfigurationCommand {
  return configurationCommand(path, method, body, previous);
}

/** 发出写命令；请求幂等由 Gateway 依据 Idempotency-Key 处理。 */
export function sendCommand<T>(command: ConfigurationCommand, decode: (value: unknown) => T): Promise<T> {
  return session.request<T>(command.path, { method: command.method, body: command.body,
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key } }, decode);
}

/** 发送测试事件，无请求体。 */
export function sendTestEvent(endpointId: string, signal?: AbortSignal): Promise<TestResult> {
  return session.request<TestResult>(`${webhookAdminPath}/endpoints/${endpointId}/tests`, { ...testEndpointRequest(), signal }, decodeTestResult);
}
