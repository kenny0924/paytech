import { callerServicePattern, channelRoutes, channelTypes, idPattern } from './channel.ts';
import type { CallGrant, ChannelEndpoint, ChannelType } from './channel.ts';
import { translator } from '../i18n/index.ts';

const t = translator('channel'), tNav = translator('nav');

/**
 * 渠道工作区（第二段画板 01–12）的纯逻辑：类型分段与地址栏、详情页签、协议 / 沙箱子页路由、旧接入方地址映射、
 * 调用授权矩阵组装与出站地址行错误映射。页面组件只负责展示与请求。
 */

// ---------- 类型分段（画板 01） ----------

/** 地址栏 ?type= 取值；缺失或非法时为「全部」。 */
export function channelTypeFilter(search: string): ChannelType | null {
  const value = new URLSearchParams(search).get('type');
  return channelTypes.find(type => type === value) ?? null;
}

/** 切换类型分段：写入或移除 ?type=，保留其他筛选并回到第 1 页。 */
export function typeSegmentSearch(search: string, type: ChannelType | null): string {
  const next = new URLSearchParams(search);
  if (type) next.set('type', type); else next.delete('type');
  next.delete('page');
  const text = next.toString();
  return text ? `?${text}` : '';
}

/**
 * 类型分段计数：全部、支付、服务依次查询总数，不并发。列表路由的 Gateway 并发上限为每人 2，列表本身已占 1 个，
 * 并发发出会被 429 拒绝。查询失败的分段不给计数。
 */
export async function typeCountsSequentially(total: (channelType: ChannelType | null) => Promise<number | undefined>):
  Promise<Partial<Record<'ALL' | ChannelType, number>>> {
  const counts: Partial<Record<'ALL' | ChannelType, number>> = {};
  for (const channelType of [null, ...channelTypes]) {
    const value = await total(channelType);
    if (value !== undefined) counts[channelType ?? 'ALL'] = value;
  }
  return counts;
}

/** 列表 Query → 分页筛选（只取非空且合法的条件）。 */
export function channelListFilters(search: string): Record<string, string> {
  const query = new URLSearchParams(search);
  const filters: Record<string, string> = {};
  const keyword = query.get('keyword')?.trim();
  if (keyword) filters.keyword = keyword;
  const status = query.get('status');
  if (status && ['DRAFT', 'ACTIVE', 'SUSPENDED', 'CLOSED'].includes(status)) filters.status = status;
  const type = channelTypeFilter(search);
  if (type) filters.channelType = type;
  return filters;
}

// ---------- 详情页签（画板 03 / 04） ----------

export type DetailTab = 'overview' | 'accounts' | 'credential' | 'mids' | 'protocol' | 'verify' | 'grants' | 'changes';
/** 页签按类型不同：支付渠道有技术账号与 MID；服务渠道有凭据与调用授权。 */
export const detailTabs: Record<ChannelType, readonly DetailTab[]> = {
  PAYMENT: ['overview', 'accounts', 'mids', 'protocol', 'verify', 'changes'],
  SERVICE: ['overview', 'credential', 'protocol', 'verify', 'grants', 'changes'],
};
/** 页签文案（channel.workspace.tab.<页签>），调用时翻译。 */
export const detailTabLabel = (tab: DetailTab) => t(`workspace.tab.${tab}`);
/** 地址栏 ?tab= 取值；不属于该类型的页签回到概览。 */
export function detailTab(type: ChannelType, value: string | null): DetailTab {
  return detailTabs[type].find(tab => tab === value) ?? 'overview';
}

export type ProtocolSection = 'implementations' | 'apis' | 'scripts';
export const protocolSections: readonly ProtocolSection[] = ['implementations', 'apis', 'scripts'];
export const protocolSectionLabel = (section: ProtocolSection) => t(`workspace.section.${section}`);
export function protocolSection(value: string | null): ProtocolSection {
  return protocolSections.find(section => section === value) ?? 'implementations';
}

export type GrantView = 'matrix' | 'list';
export function grantView(value: string | null): GrantView {
  return value === 'list' ? 'list' : 'matrix';
}

// ---------- 路由 ----------

type Query = Readonly<Record<string, string | null | undefined>>;
const withQuery = (path: string, query: Query) => {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) if (value) params.set(key, value);
  const text = params.toString();
  return text ? `${path}?${text}` : path;
};

/** 某个渠道详情下的页签、协议与沙箱子页地址。 */
export function workspaceRoutes(channelId: string) {
  const root = channelRoutes.channel(channelId);
  return {
    root,
    tab: (tab: DetailTab, query: Query = {}) => withQuery(root, { tab: tab === 'overview' ? null : tab, ...query }),
    protocol: (section: ProtocolSection) => withQuery(root, { tab: 'protocol', section: section === 'implementations' ? null : section }),
    changes: (targetType: string, targetId: string) => withQuery(root, { tab: 'changes', targetType, targetId }),
    api: (apiCode: string, mode?: 'edit') => withQuery(`${root}/protocol/apis/${apiCode}`, { mode }),
    script: (scriptCode: string, mode?: 'edit') => withQuery(`${root}/protocol/scripts/${scriptCode}`, { mode }),
    implementation: (standardApiCode: string, mode?: 'edit' | 'create') => withQuery(`${root}/protocol/implementations/${standardApiCode}`, { mode }),
    verifyAccount: (accountCode: string, mode?: 'edit') => withQuery(`${root}/verify/accounts/${accountCode}`, { mode }),
    verifyCase: (caseCode: string, mode?: 'edit') => withQuery(`${root}/verify/cases/${caseCode}`, { mode }),
    verifyRun: (runCode: string) => `${root}/verify/runs/${runCode}`,
  };
}
export type WorkspaceRoutes = ReturnType<typeof workspaceRoutes>;

/** 协议与沙箱子页的面包屑前缀：渠道 / {编码} / {页签或子导航}。 */
export function workspaceCrumbs(channelId: string, channelCode: string, section: ProtocolSection | 'verify'): { label: string; to: string }[] {
  const routes = workspaceRoutes(channelId);
  return [{ label: tNav('resource.OPERATIONS_CHANNEL_CHANNELS'), to: channelRoutes.channels }, { label: channelCode, to: routes.root },
    section === 'verify' ? { label: detailTabLabel('verify'), to: routes.tab('verify') } : { label: protocolSectionLabel(section), to: routes.protocol(section) }];
}

/**
 * 按编码进入渠道（标准 API 页等只知道编码的入口）：解析后 replace 到详情；suffix 为详情下的子路径，例如 /protocol/implementations/PAY。
 */
export const channelByCodeRoute = (channelCode: string, query: Query = {}, suffix = '') =>
  withQuery(`${channelRoutes.channels}/code/${channelCode}${suffix}`, query);

const codePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
const childCode = (value: string | undefined, allowNew: boolean) =>
  typeof value === 'string' && (codePattern.test(value) || (allowNew && value === 'new'));

export type ChannelPage =
  | { kind: 'list' }
  | { kind: 'detail'; channelId: string }
  | { kind: 'byCode'; channelCode: string; suffix: string }
  | { kind: 'protocol'; channelId: string; section: ProtocolSection; code: string | null }
  | { kind: 'verify'; channelId: string; section: 'accounts' | 'cases' | 'runs'; code: string | null };

/** /channel/channels 之下的路径 → 页面；不认识的路径返回 null（显示 404）。 */
export function parseChannelPath(pathname: string): ChannelPage | null {
  const rest = pathname.replace(/\/$/, '').slice(channelRoutes.channels.length);
  if (rest === '') return { kind: 'list' };
  const parts = rest.replace(/^\//, '').split('/');
  if (parts[0] === 'code') {
    if (parts.length < 2 || !codePattern.test(parts[1])) return null;
    const suffix = parts.slice(2).map(part => `/${part}`).join('');
    // 子路径须是详情下的合法页面（以占位 ID 校验）
    const target = suffix ? parseChannelPath(`${channelRoutes.channels}/1${suffix}`) : null;
    return !suffix || (target && target.kind !== 'detail') ? { kind: 'byCode', channelCode: parts[1], suffix } : null;
  }
  if (!idPattern.test(parts[0])) return null;
  const channelId = parts[0];
  if (parts.length === 1) return { kind: 'detail', channelId };
  if (parts.length !== 4) return null;
  if (parts[1] === 'protocol' && protocolSections.includes(parts[2] as ProtocolSection)) {
    const section = parts[2] as ProtocolSection;
    if (!childCode(parts[3], section !== 'implementations')) return null;
    return { kind: 'protocol', channelId, section, code: parts[3] === 'new' ? null : parts[3] };
  }
  if (parts[1] === 'verify' && (parts[2] === 'accounts' || parts[2] === 'cases')) {
    if (!childCode(parts[3], true)) return null;
    return { kind: 'verify', channelId, section: parts[2], code: parts[3] === 'new' ? null : parts[3] };
  }
  if (parts[1] === 'verify' && parts[2] === 'runs' && idPattern.test(parts[3])) return { kind: 'verify', channelId, section: 'runs', code: parts[3] };
  return null;
}

// ---------- 旧接入方地址（/external-api/integrations/:code/...） ----------

export const legacyIntegrationRoute = '/external-api/integrations';

/** 旧地址 → 渠道编码与剩余路径；列表根地址 code 为 null；不认识的地址返回 null。 */
export function parseLegacyIntegrationPath(pathname: string): { code: string | null; rest: string[] } | null {
  if (pathname !== legacyIntegrationRoute && !pathname.startsWith(`${legacyIntegrationRoute}/`)) return null;
  const parts = pathname.slice(legacyIntegrationRoute.length).split('/').filter(Boolean);
  if (parts.length === 0 || (parts.length === 1 && parts[0] === 'new')) return { code: null, rest: [] };
  if (!codePattern.test(parts[0])) return null;
  return { code: parts[0], rest: parts.slice(1) };
}

const legacyTabs: Readonly<Record<string, (routes: WorkspaceRoutes) => string>> = {
  basic: routes => routes.root,
  apis: routes => routes.protocol('apis'),
  scripts: routes => routes.protocol('scripts'),
  implementations: routes => routes.protocol('implementations'),
  verify: routes => routes.tab('verify'),
};

/**
 * 旧接入方地址的同名页签 → 渠道详情地址：基本信息 → 概览，原子 API / 脚本 / 实现 → 协议配置子导航，验证 → 沙箱验证；
 * 子页保留 mode 参数。不认识的子路径返回 null。
 */
export function legacyIntegrationTarget(channelId: string, rest: readonly string[], search: string): string | null {
  const routes = workspaceRoutes(channelId);
  const query = new URLSearchParams(search);
  if (rest.length === 0) return (legacyTabs[query.get('tab') ?? 'basic'] ?? legacyTabs.basic)(routes);
  if (rest.length !== 2) return null;
  const [section, code] = rest;
  const mode = query.get('mode');
  const edit = mode === 'edit' ? 'edit' : undefined;
  switch (section) {
    case 'apis': return childCode(code, true) ? routes.api(code, edit) : null;
    case 'scripts': return childCode(code, true) ? routes.script(code, edit) : null;
    case 'implementations': return childCode(code, false) ? routes.implementation(code, mode === 'create' ? 'create' : edit) : null;
    case 'verify-accounts': return childCode(code, true) ? routes.verifyAccount(code, edit) : null;
    case 'verify-cases': return childCode(code, true) ? routes.verifyCase(code, edit) : null;
    case 'verify-runs': return idPattern.test(code) ? routes.verifyRun(code) : null;
    default: return null;
  }
}

// ---------- 调用授权（画板 07–09） ----------

/** 新增授权的预置调用方：与内部认证服务名一致；显示名（channel.caller.<服务名>）只用于展示。 */
export const presetCallers: readonly string[] = [
  'counterparty-service', 'commerce-service', 'risk-service', 'payment-service', 'acquiring-service', 'client-master-service',
];
export function callerLabel(callerService: string): string | null {
  return presetCallers.includes(callerService) ? t(`caller.${callerService}`) : null;
}
export function isCallerService(value: string): boolean {
  return callerServicePattern.test(value);
}

/** 调用方候选：已有授权中的调用方加预置名单，去重后按服务名排序；带已有授权条数。 */
export function callerOptions(grants: readonly Pick<CallGrant, 'callerService'>[]): { callerService: string; label: string | null; grantCount: number }[] {
  const counts = new Map<string, number>();
  for (const grant of grants) counts.set(grant.callerService, (counts.get(grant.callerService) ?? 0) + 1);
  const names = new Set([...counts.keys(), ...presetCallers]);
  return [...names].toSorted().map(callerService => ({ callerService, label: callerLabel(callerService), grantCount: counts.get(callerService) ?? 0 }));
}

export interface ImplementedApi { standardApiCode: string; standardApiName: string }
export interface GrantMatrix {
  /** 列：本渠道已实现的标准 API，按编码排序。 */
  columns: ImplementedApi[];
  /** 行：出现在授权中的调用方，按服务名排序；cells 以标准 API 编码为键，空格无授权。 */
  rows: { callerService: string; cells: Readonly<Record<string, CallGrant | undefined>> }[];
  /** 指向已不在实现列表中的标准 API 的授权（实现被删除前已停用），只在列表视图出现。 */
  orphans: CallGrant[];
  activeCount: number; disabledCount: number;
}

/** 矩阵数据组装：行 = 调用方，列 = 已实现的标准 API，单元格为该组合的唯一授权。 */
export function grantMatrix(grants: readonly CallGrant[], implemented: readonly ImplementedApi[]): GrantMatrix {
  const columns = implemented.toSorted((left, right) => left.standardApiCode.localeCompare(right.standardApiCode));
  const columnCodes = new Set(columns.map(column => column.standardApiCode));
  const byCaller = new Map<string, Record<string, CallGrant>>();
  const orphans: CallGrant[] = [];
  for (const grant of grants) {
    if (!columnCodes.has(grant.standardApiCode)) { orphans.push(grant); continue; }
    const cells = byCaller.get(grant.callerService) ?? {};
    cells[grant.standardApiCode] = grant;
    byCaller.set(grant.callerService, cells);
  }
  const rows = [...byCaller.keys()].toSorted().map(callerService => ({ callerService, cells: byCaller.get(callerService)! }));
  return { columns, rows, orphans, activeCount: grants.filter(grant => grant.grantStatus === 'ACTIVE').length,
    disabledCount: grants.filter(grant => grant.grantStatus === 'DISABLED').length };
}

/** 暂停服务渠道时受影响的 ACTIVE 授权，按调用方归组（画板 11）。 */
export function activeGrantsByCaller(grants: readonly CallGrant[]): { callerService: string; standardApiCodes: string[] }[] {
  const groups = new Map<string, string[]>();
  for (const grant of grants) {
    if (grant.grantStatus !== 'ACTIVE') continue;
    groups.set(grant.callerService, [...(groups.get(grant.callerService) ?? []), grant.standardApiCode]);
  }
  return [...groups.entries()].toSorted(([left], [right]) => left.localeCompare(right))
    .map(([callerService, codes]) => ({ callerService, standardApiCodes: codes.toSorted() }));
}

// ---------- 出站地址（画板 12） ----------

export const maxEndpoints = 16;

/** 出站地址被哪些原子 API 引用：由原子 API 摘要的 endpointCode 汇总。 */
export function endpointReferences(apis: readonly { apiCode: string; endpointCode: string }[]): Map<string, string[]> {
  const result = new Map<string, string[]>();
  for (const api of apis) result.set(api.endpointCode, [...(result.get(api.endpointCode) ?? []), api.apiCode].toSorted());
  return result;
}

/** 输入校验（只做格式与唯一性，规则仍以 Owner 为准）：返回每行的问题。 */
export function endpointDraftIssues(rows: readonly Partial<ChannelEndpoint>[]): Map<number, string> {
  const issues = new Map<number, string>();
  const seen = new Set<string>();
  rows.forEach((row, index) => {
    const code = row.endpointCode?.trim() ?? '';
    if (!code) issues.set(index, t('endpoint.codeRequired'));
    else if (seen.has(code)) issues.set(index, t('endpoint.codeDuplicate', { code }));
    else if (!(row.baseUrl?.trim())) issues.set(index, t('endpoint.baseUrlRequired'));
    seen.add(code);
  });
  return issues;
}

/**
 * Owner 拒绝原因 → 出站地址行：422 字段路径 endpoints[i].x 直接定位；400 / 409 的原因文本含该行编码或主机名时归到该行
 * （例如私网、localhost 被拦截）。无法定位的原因放在 general，由弹窗顶部显示。
 */
export function endpointRowErrors(rows: readonly Partial<ChannelEndpoint>[], fieldErrors: readonly { field: string; message: string }[],
  detail?: string | null): { rows: Map<number, string[]>; general: string[] } {
  const result = new Map<number, string[]>();
  const general: string[] = [];
  const add = (index: number, message: string) => result.set(index, [...(result.get(index) ?? []), message]);
  for (const error of fieldErrors) {
    const match = /^endpoints\[(\d+)]/.exec(error.field);
    if (match && Number(match[1]) < rows.length) add(Number(match[1]), error.message);
    else general.push(error.message);
  }
  if (detail) {
    // 编码按整词匹配（NI 不命中 NI_API），多行命中时取最长的编码或主机名
    let best = -1; let bestLength = 0;
    rows.forEach((row, index) => {
      const code = row.endpointCode?.trim();
      const host = hostOf(row.baseUrl);
      const matches = [code && wholeCode(detail, code) ? code : null, host && detail.includes(host) ? host : null];
      for (const token of matches) if (token && token.length > bestLength) { best = index; bestLength = token.length; }
    });
    if (best >= 0) add(best, detail); else general.push(detail);
  }
  return { rows: result, general };
}

function wholeCode(text: string, code: string): boolean {
  return new RegExp(`(^|[^A-Za-z0-9_])${code.replace(/[^A-Za-z0-9_]/g, '\\$&')}($|[^A-Za-z0-9_])`).test(text);
}

function hostOf(url: string | undefined): string | null {
  if (!url) return null;
  try { return new URL(url.trim()).hostname || null; } catch { return null; }
}

/** 保存请求体：去首尾空白，sandbox 只在为 true 时提交。 */
export function endpointsBody(expectedVersion: number, rows: readonly Partial<ChannelEndpoint>[]) {
  return { expectedVersion, endpoints: rows.map(row => ({ endpointCode: (row.endpointCode ?? '').trim(), baseUrl: (row.baseUrl ?? '').trim(),
    sandbox: row.sandbox === true })) };
}
