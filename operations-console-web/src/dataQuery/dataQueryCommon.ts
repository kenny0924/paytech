import { ApiError } from '../api/session.ts';
import type { PagingResponseBody } from '../api/types.ts';

// ---------- 路径、路由与按钮 ----------

const admin = '/data-query-admin/v1';

/** Operations BFF 数据查询管理路径（经 Gateway /api/operations 前缀，同路径转发到 Platform）。 */
export const dataQueryPaths = {
  dataSources: `${admin}/data-sources`,
  dataSource: (dataSourceId: string) => `${admin}/data-sources/${dataSourceId}`,
  dataSourceStatus: (dataSourceId: string) => `${admin}/data-sources/${dataSourceId}/status`,
  dataSourceDeletion: (dataSourceId: string, expectedVersion: number) => `${admin}/data-sources/${dataSourceId}?expectedVersion=${expectedVersion}`,
  connectionTests: `${admin}/data-source-connection-tests`,
  templates: `${admin}/templates`,
  template: (templateId: string) => `${admin}/templates/${templateId}`,
  templateStatus: (templateId: string) => `${admin}/templates/${templateId}/status`,
  versions: (templateId: string) => `${admin}/templates/${templateId}/versions`,
  version: (templateId: string, versionNo: number) => `${admin}/templates/${templateId}/versions/${versionNo}`,
  restoration: (templateId: string) => `${admin}/templates/${templateId}/restoration`,
  validations: `${admin}/template-validations`,
  trials: `${admin}/template-trials`,
} as const;

/** 页面路由（与 Console Resource frontend_route 及 pageRoutePaths 一致）。 */
export const dataQueryRoutes = {
  dataSources: '/data-query/data-sources',
  templates: '/data-query/templates',
  newTemplate: '/data-query/templates/new',
  template: (templateId: string) => `/data-query/templates/${templateId}`,
} as const;

/** 按钮资源（块 131）：数据源维护、模板维护与试运行。没有按钮时页面只读。 */
export const dataQueryButtons = {
  dataSourceManage: 'OPERATIONS_DATA_SOURCE_MANAGE',
  templateManage: 'OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE',
} as const;

export function hasButton(resources: readonly { resourceType: string; resourceCode: string }[], code: string): boolean {
  return resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === code);
}

// ---------- 解码工具 ----------

const idPattern = /^[1-9][0-9]{0,18}$/;
export const isRecord = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object' && !Array.isArray(v);
/** 解码失败只给通用错误，不回显响应内容。 */
export function require(valid: boolean): asserts valid { if (!valid) throw new ApiError(502, 'INVALID_RESPONSE'); }
export const isId = (v: unknown): v is string => typeof v === 'string' && idPattern.test(v);
export const isText = (v: unknown): v is string => typeof v === 'string';
export const isOptionalText = (v: unknown) => v === undefined || v === null || typeof v === 'string';
export const isInteger = (v: unknown, min = 0, max = Number.MAX_SAFE_INTEGER) => Number.isInteger(v) && Number(v) >= min && Number(v) <= max;
export const isOptionalInteger = (v: unknown, min = 0) => v === undefined || v === null || isInteger(v, min);
export const isInstant = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v));
export const isOneOf = <T extends string>(values: readonly T[], v: unknown): v is T => typeof v === 'string' && (values as readonly string[]).includes(v);
export const isTextList = (v: unknown): v is string[] => Array.isArray(v) && v.every(isText);
/** 可空字段统一为 null，页面不区分缺省与 null。 */
export const orNull = <T>(v: T | undefined | null): T | null => v ?? null;

/** 分页响应：items 逐项解码，page/size/total 交给公共分页加载器复核。 */
export function pageDecoder<T>(decodeItem: (value: unknown) => T) {
  return (value: unknown): PagingResponseBody<T> => {
    require(isRecord(value) && Array.isArray(value.items) && isInteger(value.page, 1) && isInteger(value.size, 1) && isInteger(value.total));
    const page = value as unknown as PagingResponseBody<unknown>;
    return { items: page.items.map(decodeItem), page: page.page, size: page.size, total: page.total };
  };
}

export interface DataQueryMutation { objectType: 'DATA_SOURCE' | 'QUERY_TEMPLATE'; objectId: string; objectCode: string; status: string; version: number }

/** 写回执：对象类型、编号与版本必须合法；传入 objectId 时必须是本次对象。 */
export function decodeMutation(value: unknown, objectType: DataQueryMutation['objectType'], objectId?: string): DataQueryMutation {
  require(isRecord(value) && value.objectType === objectType && isId(value.objectId) && isText(value.objectCode)
    && isText(value.status) && isInteger(value.version, 1) && (objectId === undefined || value.objectId === objectId));
  return value as unknown as DataQueryMutation;
}

// ---------- 展示 ----------

/** 统一时间格式 YYYY-MM-DD HH:mm（本地时区）；缺省显示 —。 */
export function localTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return value;
  const two = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())} ${two(date.getHours())}:${two(date.getMinutes())}`;
}

/** 只把非空筛选值放进 Query。 */
export function listFilters(values: Readonly<Record<string, string | undefined>>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).map(([k, v]) => [k, v?.trim() ?? '']).filter(([, v]) => v !== ''));
}

export const codePattern = /^[A-Z][A-Z0-9_]{1,63}$/;
export const activeStatuses = ['ACTIVE', 'INACTIVE'] as const;
export type ActiveStatus = typeof activeStatuses[number];
export const activeStatusLabels: Readonly<Record<string, string>> = { ACTIVE: '启用', INACTIVE: '停用', DELETED: '已删除' };
export const activeStatusColors: Readonly<Record<string, string>> = { ACTIVE: 'green', INACTIVE: 'default', DELETED: 'red' };
