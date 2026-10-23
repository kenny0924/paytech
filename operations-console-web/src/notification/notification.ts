import { ApiError, session } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';
import { resolveTimeRange } from '../components/timeRange.ts';
import type { TimeRangeValue } from '../components/timeRange.ts';
import { configurationCommand } from '../configuration/configurationModel.ts';
import type { ConfigurationCommand } from '../configuration/configurationModel.ts';
import { enumLabel, i18n, translator } from '../i18n/index.ts';

const t = translator('notification');

/** 通知管理接口前缀（BFF）。 */
export const notificationAdminPath = '/notification-admin/v1';
export const templateRoute = '/notification-templates';
export const manageTemplates = 'OPERATIONS_NOTIFICATION_TEMPLATE_MANAGE';
export const manageProviders = 'OPERATIONS_NOTIFICATION_PROVIDER_MANAGE';
/** 新建、编辑发送账号的按钮资源。 */
export const editProviders = 'OPERATIONS_NOTIFICATION_PROVIDER_EDIT';
/** 录入密钥与测试发送的按钮资源。 */
export const credentialProviders = 'OPERATIONS_NOTIFICATION_PROVIDER_CREDENTIAL';
/** 本期可新建的服务商类型；显示名为 `notification.enum.providerType.<值>`。 */
export const providerTypes = ['AMAZON_SES'] as const;

/** 按当前语言连接并列项：zh-CN 用顿号（区域、密钥），en 用逗号（region, credentials）。 */
export function joinList(items: readonly string[]): string {
  return new Intl.ListFormat(i18n.locale(), { style: 'narrow', type: 'conjunction' }).format(items);
}

/** 缺失配置字段的显示名（`missingConfiguration.<字段>`，未知字段原样显示），按当前语言连接。 */
export function missingConfigurationText(fields: readonly string[]): string {
  return joinList(fields.map(field => enumLabel(t, 'missingConfiguration', field)));
}
/** 区域格式，与 Platform 一致，如 me-central-1。 */
export const regionPattern = /^[a-z]{2}(-[a-z]+)+-[0-9]{1,2}$/;
/** Configuration Set 格式，可空。 */
export const configurationSetPattern = /^[A-Za-z0-9_-]{1,64}$/;
/** 事件队列 URL 格式，与 Platform 一致：https://sqs.{区域}.amazonaws.com/{12 位账号}/{队列名}。 */
export const eventQueuePattern = /^https:\/\/sqs\.[a-z]{2}(-[a-z]+)+-[0-9]\.amazonaws\.com\/[0-9]{12}\/[A-Za-z0-9_-]{1,80}$/;
/** Access Key ID 格式。 */
export const accessKeyPattern = /^[A-Z0-9]{16,128}$/;
/** Secret Access Key 格式：可见 ASCII，无空白。 */
export const secretKeyPattern = /^[\x21-\x7E]{1,128}$/;
/** 发送记录单次查询的最长跨度（天），与 Platform 一致。 */
export const maxRecordDays = 31;

export type ConfigStatus = 'ACTIVE' | 'DISABLED';
export interface TemplateSummary {
  templateCode: string; medium: string; status: ConfigStatus; locales: string[]; transientTemplate: boolean;
  routeStatus: ConfigStatus | null; version: number; updateTime: string;
}
export interface TemplateContent { locale: string; subjectTemplate: string; bodyTemplate: string; version: number; updateTime: string }
export interface TemplateRoute { primaryProviderCode: string; fallbackProviderCode: string | null; status: ConfigStatus; version: number; updateTime: string }
export interface TemplateDetail {
  templateCode: string; medium: string; status: ConfigStatus; transientTemplate: boolean; requiredParameters: string[];
  version: number; updateTime: string; contents: TemplateContent[]; route: TemplateRoute | null;
}
export interface Mutation { code: string; version: number }
export interface Preview { subject: string; body: string; missingParameters: string[] }
export interface ProviderReference { templateCode: string; role: 'PRIMARY' | 'FALLBACK'; routeStatus: ConfigStatus }
export interface ProviderSummary {
  providerCode: string; medium: string; providerType: string; status: ConfigStatus; region: string | null; fromAddress: string | null;
  configurationSet: string | null; credentialConfigured: boolean; credentialUpdateTime: string | null; missingConfiguration: string[];
  adapterConfigured: boolean; eventQueueUrl: string | null; eventPollTime: string | null; eventPollError: string | null;
  eventReceiveTime: string | null; version: number; updateTime: string; references: ProviderReference[];
}
export interface RequestSummary {
  requestId: string; sourceSystem: string; sourceEventKey: string; templateCode: string | null; tenantId: string | null;
  defaultLocale: string; deliveryCount: number; statusCounts: Record<string, number>; createTime: string;
}
export interface Attempt {
  attemptId: string; providerCode: string | null; status: string; responseCode: string | null; resolutionSource: string | null;
  resolutionCode: string | null; startedTime: string; finishedTime: string | null; resolvedTime: string | null;
  eventOutcome: string | null; eventOutcomeCode: string | null; eventOutcomeTime: string | null;
  complaintCode: string | null; complaintTime: string | null;
}
export interface Delivery {
  deliveryId: string; locale: string | null; parameterKeys: string[]; status: string; attemptCount: number;
  nextRetryTime: string | null; updateTime: string; attempts: Attempt[];
}
export interface RequestDetail {
  requestId: string; sourceSystem: string; sourceEventKey: string; templateCode: string | null; tenantId: string | null;
  defaultLocale: string; parameterKeys: string[]; routeId: string; createTime: string; deliveries: Delivery[];
}

/**
 * 显示名在 `notification` 命名空间，用 enumLabel 取：`enum.status`、`enum.deliveryStatus`、`enum.attemptStatus`、
 * `enum.resolutionSource`、`enum.eventOutcome`；这里只保留代码、顺序与颜色。
 */
export const configStatuses: readonly ConfigStatus[] = ['ACTIVE', 'DISABLED'];
export const deliveryStatuses = ['PENDING', 'PROCESSING', 'RETRY_WAIT', 'SENT', 'DELIVERED', 'FAILED', 'UNKNOWN'] as const;
export const deliveryStatusColors: Record<string, string> = {
  PENDING: 'blue', PROCESSING: 'blue', RETRY_WAIT: 'blue', SENT: 'green', DELIVERED: 'green', FAILED: 'red', UNKNOWN: 'orange',
};
export const attemptStatusColors: Record<string, string> = { PROCESSING: 'blue', ACCEPTED: 'green', FAILED: 'red', UNKNOWN: 'orange' };
export const eventOutcomeColors: Record<string, string> = { DELIVERED: 'green', SOFT_BOUNCE: 'orange', HARD_BOUNCE: 'red', REJECTED: 'red' };

/** 发送账号的投递事件接收状态，供列表显示。 */
export type EventReceiving =
  | { kind: 'none' } | { kind: 'waiting' } | { kind: 'failed'; error: string; lastPoll: string | null }
  | { kind: 'ok'; lastPoll: string; lastEvent: string | null };

/** 由账号的队列配置与拉取运行状态得出接收状态；从未拉取过（含本环境未开启拉取）为 waiting。 */
export function eventReceiving(p: Pick<ProviderSummary, 'eventQueueUrl' | 'eventPollTime' | 'eventPollError' | 'eventReceiveTime'>): EventReceiving {
  if (!p.eventQueueUrl) return { kind: 'none' };
  if (p.eventPollError) return { kind: 'failed', error: p.eventPollError, lastPoll: p.eventPollTime };
  if (!p.eventPollTime) return { kind: 'waiting' };
  return { kind: 'ok', lastPoll: p.eventPollTime, lastEvent: p.eventReceiveTime };
}
/** 列表中多个收件人状态不同时，按此顺序显示最需要关注的一个。 */
export const deliveryAttentionOrder = ['FAILED', 'UNKNOWN', 'RETRY_WAIT', 'PROCESSING', 'PENDING', 'SENT', 'DELIVERED'];

export const codePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
const slotPattern = /\$\{([A-Za-z][A-Za-z0-9_]{0,63})\}/g;
const configStatus = (v: unknown) => v === 'ACTIVE' || v === 'DISABLED';
const version = (v: unknown, allowZero = false) => Number.isInteger(v) && Number(v) >= (allowZero ? 0 : 1) && Number(v) <= 2147483647;
const text = (v: unknown) => typeof v === 'string';
const optionalText = (v: unknown) => v === null || typeof v === 'string';
const strings = (v: unknown) => Array.isArray(v) && v.every(text);
const invalid = () => new ApiError(502, 'INVALID_RESPONSE');
const require = (valid: boolean) => { if (!valid) throw invalid(); };

/** 正文中出现的占位符名称，按出现顺序去重；用于生成预览参数输入框。 */
export function placeholders(...templates: string[]): string[] {
  const names = new Set<string>();
  for (const template of templates) for (const match of template.matchAll(slotPattern)) names.add(match[1]);
  return [...names];
}

/** 正文缺少的必需占位符，如认证模板的 link。 */
export function missingRequired(body: string, required: string[]): string[] {
  const present = placeholders(body);
  return required.filter(name => !present.includes(name));
}

/** 列表状态标签：多个收件人时取最需要关注的状态。 */
export function attentionStatus(counts: Record<string, number>): string | null {
  return deliveryAttentionOrder.find(status => (counts[status] ?? 0) > 0) ?? null;
}

/** 校验模板列表项。 */
export function decodeTemplateSummary(value: unknown): TemplateSummary {
  const v = value as TemplateSummary;
  require(!!v && codePattern.test(v.templateCode) && configStatus(v.status) && strings(v.locales)
    && typeof v.transientTemplate === 'boolean' && (v.routeStatus === null || configStatus(v.routeStatus)) && version(v.version)
    && text(v.updateTime));
  return v;
}

/** 校验模板详情，编码必须与请求一致。 */
export function decodeTemplateDetail(value: unknown, templateCode: string): TemplateDetail {
  const v = value as TemplateDetail;
  require(!!v && v.templateCode === templateCode && configStatus(v.status) && typeof v.transientTemplate === 'boolean'
    && strings(v.requiredParameters) && version(v.version) && Array.isArray(v.contents)
    && v.contents.every(c => text(c.locale) && text(c.subjectTemplate) && text(c.bodyTemplate) && version(c.version))
    && (v.route === null || (text(v.route.primaryProviderCode) && optionalText(v.route.fallbackProviderCode)
      && configStatus(v.route.status) && version(v.route.version))));
  return v;
}

/** 校验写入结果；code 为空时不比对（语言由 Platform 规范化）。 */
export function decodeMutation(value: unknown, code?: string): Mutation {
  const v = value as Mutation;
  require(!!v && text(v.code) && (code === undefined || v.code === code) && version(v.version));
  return v;
}

/** 校验预览结果。 */
export function decodePreview(value: unknown): Preview {
  const v = value as Preview;
  require(!!v && text(v.subject) && text(v.body) && strings(v.missingParameters));
  return v;
}

/** 测试发送结果；ACCEPTED 只表示服务商受理。 */
export interface TestSendResult { outcome: 'ACCEPTED' | 'REJECTED' | 'UNKNOWN'; resultCode: string }

/** 有专门处理建议（`testSendHint.<结果码>`）的测试发送结果码；未列出的结果码按结果类型给通用建议（`testSendHint.outcome.<类型>`）。 */
const testSendHintCodes: readonly string[] = [
  'SES_CREDENTIAL_REJECTED', 'SES_SENDER_NOT_VERIFIED', 'SES_MESSAGE_REJECTED', 'SES_CONFIGURATION_NOT_FOUND', 'SES_QUOTA_EXCEEDED',
  'SES_THROTTLED', 'SES_ACCOUNT_PAUSED', 'SES_ACCOUNT_SUSPENDED', 'SES_BAD_REQUEST',
];

/** 测试发送结果的处理建议。 */
export function testSendHint(result: TestSendResult): string {
  if (result.outcome !== 'ACCEPTED' && testSendHintCodes.includes(result.resultCode)) return t('testSendHint.' + result.resultCode);
  return t('testSendHint.outcome.' + result.outcome);
}

/** 校验发送账号列表项。 */
export function decodeProvider(value: unknown): ProviderSummary {
  const v = value as ProviderSummary;
  require(!!v && codePattern.test(v.providerCode) && text(v.providerType) && configStatus(v.status) && optionalText(v.region)
    && optionalText(v.fromAddress) && optionalText(v.configurationSet) && typeof v.credentialConfigured === 'boolean'
    && optionalText(v.credentialUpdateTime) && strings(v.missingConfiguration) && typeof v.adapterConfigured === 'boolean'
    && optionalText(v.eventQueueUrl) && optionalText(v.eventPollTime) && optionalText(v.eventPollError) && optionalText(v.eventReceiveTime)
    && version(v.version) && Array.isArray(v.references)
    && v.references.every(r => text(r.templateCode) && (r.role === 'PRIMARY' || r.role === 'FALLBACK') && configStatus(r.routeStatus)));
  return v;
}

/** 校验测试发送结果。 */
export function decodeTestSend(value: unknown): TestSendResult {
  const v = value as TestSendResult;
  require(!!v && (v.outcome === 'ACCEPTED' || v.outcome === 'REJECTED' || v.outcome === 'UNKNOWN') && codePattern.test(v.resultCode));
  return v;
}

/** 校验发送记录列表项。 */
export function decodeRequestSummary(value: unknown): RequestSummary {
  const v = value as RequestSummary;
  require(!!v && /^[1-9][0-9]*$/.test(v.requestId) && text(v.sourceSystem) && text(v.sourceEventKey)
    && optionalText(v.templateCode) && Number.isInteger(v.deliveryCount) && !!v.statusCounts && typeof v.statusCounts === 'object'
    && Object.values(v.statusCounts).every(n => Number.isInteger(n) && n >= 0) && text(v.createTime));
  return v;
}

/** 校验发送记录详情，请求 ID 必须与请求一致。 */
export function decodeRequestDetail(value: unknown, requestId: string): RequestDetail {
  const v = value as RequestDetail;
  require(!!v && v.requestId === requestId && strings(v.parameterKeys) && Array.isArray(v.deliveries)
    && v.deliveries.every(d => text(d.deliveryId) && optionalText(d.locale) && strings(d.parameterKeys) && text(d.status)
      && Number.isInteger(d.attemptCount) && Array.isArray(d.attempts)
      && d.attempts.every(a => text(a.attemptId) && text(a.status) && text(a.startedTime) && optionalText(a.eventOutcome)
        && optionalText(a.eventOutcomeCode) && optionalText(a.eventOutcomeTime) && optionalText(a.complaintCode)
        && optionalText(a.complaintTime))));
  return v;
}

/** 分页结果逐项解码。 */
export function pageOf<T>(decode: (value: unknown) => T) {
  return (value: unknown): PagingResponseBody<T> => {
    const v = value as PagingResponseBody<T>;
    require(!!v && Array.isArray(v.items) && Number.isInteger(v.total));
    return { ...v, items: v.items.map(decode) };
  };
}

export interface RecordFilterValues {
  timeRange?: TimeRangeValue; sourceSystem?: string; sourceEventKey?: string; templateCode?: string; deliveryStatus?: string;
}

/** 发送记录筛选：时间范围必填（默认最近 7 天），转为 UTC，跨度不超过 31 天。 */
export function recordFilters(values: RecordFilterValues, now = new Date()): Record<string, string> {
  const range = resolveTimeRange(values.timeRange ?? { preset: 'LAST_7_DAYS' }, now);
  if (!range.start || !range.end) throw new Error(t('filters.incompleteRange'));
  const from = new Date(range.start); const to = new Date(range.end);
  if (!Number.isFinite(from.getTime()) || !Number.isFinite(to.getTime())) throw new Error(t('filters.invalidTime'));
  if (from >= to) throw new Error(t('filters.endBeforeStart'));
  if (to.getTime() - from.getTime() > maxRecordDays * 86_400_000) throw new Error(t('filters.rangeTooLong', { days: maxRecordDays }));
  const filters: Record<string, string> = { createdFrom: from.toISOString(), createdTo: to.toISOString() };
  for (const key of ['sourceSystem', 'sourceEventKey', 'templateCode', 'deliveryStatus'] as const) {
    const value = values[key]?.trim();
    if (value) filters[key] = value;
  }
  return filters;
}

/** 构造写命令：同一目标与正文的重试复用 Idempotency-Key，改了草稿才换新 Key。 */
export function notificationCommand(path: string, method: ConfigurationCommand['method'], body: unknown,
  previous?: ConfigurationCommand): ConfigurationCommand {
  return configurationCommand(path, method, body, previous);
}

/** 发出写命令；请求幂等由 Gateway 依据 Idempotency-Key 处理。 */
export function sendCommand<T>(command: ConfigurationCommand, decode: (value: unknown) => T): Promise<T> {
  return session.request<T>(command.path, { method: command.method, body: command.body,
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key } }, decode);
}
